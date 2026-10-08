import Foundation
import UIKit
import UserNotifications

/// 远程推送注册：把 APNs device token 与当前网关配对，并在网关通告支持时上报。
///
/// 与本地通知分开：本地通知由 socket 帧驱动，后台时 socket 已断开；远程推送
/// 由网关在离线时投递，二者需要靠 `dedupeKey` 去重，避免同一事件弹两次。
@MainActor
final class AgentPushRegistrationManager: NSObject {
    static let shared = AgentPushRegistrationManager()

    /// 上报失败后重试的间隔。token 在每次冷启动都可能变化，因此重连时也会重发。
    private static let retryDelay: Duration = .seconds(30)

    private let tokenStore = APNsDeviceTokenStore()
    private var retryTask: Task<Void, Never>?

    /// 最近一次向网关上报成功的时间，用于决定是否需要重发。
    private var lastRegisteredToken: String?

    private override init() {
        super.init()
    }

    // MARK: - 生命周期

    /// 在 `hello` 帧之后调用。网关未通告 `push-notifications` 时不上报，
    /// 避免在不支持的部署上产生无意义的往返。
    func registerIfSupported(supportsPush: Bool) {
        guard supportsPush else { return }
        let token = tokenStore.loadOrNil()
        guard let token, token != lastRegisteredToken else { return }
        pendingToken = token
        scheduleDelivery()
    }

    func unregister() {
        pendingToken = nil
        lastRegisteredToken = nil
        retryTask?.cancel()
        retryTask = nil
    }

    /// 连接断开时清空，使下次 `hello` 重新上报。
    func connectionLost() {
        pendingToken = nil
    }

    // MARK: - 投递

    /// 实际发帧由 `GatewayRuntime` 在通道可用时完成，这里只持有待上报的 token。
    private var pendingToken: String?

    /// 由 `AppStore` 在通道就绪后调用，读取并消费待上报 token。
    func takePendingRegistration() -> GatewayPushRegistration? {
        guard let token = pendingToken else { return nil }
        return GatewayPushRegistration(platform: .apns, token: token)
    }

    /// 网关确认收到。上报失败时保留 token 并按固定间隔重试。
    func deliverySucceeded() {
        pendingToken = nil
        retryTask?.cancel()
        retryTask = nil
    }

    private func scheduleDelivery() {
        retryTask?.cancel()
        retryTask = Task { [weak self] in
            try? await Task.sleep(for: Self.retryDelay)
            guard !Task.isCancelled else { return }
            self?.scheduleDelivery()
        }
    }

    // MARK: - 远程通知

    /// APNs 注册失败时放弃本轮；系统会在下次启动重新尝试。
    func remoteRegistrationFailed(_ error: Error) {
        NSLog("APNs registration failed: \(error.localizedDescription)")
    }

    func handleRemoteNotification(_ userInfo: [AnyHashable: Any]) async -> Bool {
        guard let gatewayId = userInfo["dsh-gateway-id"] as? String,
              let sessionId = userInfo["dsh-session-id"] as? String else {
            return false
        }
        await MainActor.run {
            AgentUserNotificationManager.shared.recordRemoteDelivery(
                gatewayID: gatewayId,
                sessionID: sessionId,
                dedupeKey: userInfo["dsh-dedupe-key"] as? String
            )
        }
        return true
    }
}

/// APNs device token 的 Keychain 存储。
///
/// 与安装标识 `ai.dsh.mobile.ios.device-identity` 分开保存：前者可被系统轮换，
/// 后者必须跨重装保持稳定，二者生命周期不同。
enum APNsDeviceTokenStore {
    private static let service = "ai.dsh.mobile.ios.apns-token"
    private static let account = "device-token"

    static func loadOrNil() -> String? {
        let query: [String: Any] = [
            kSecClass as String: kSecClassGenericPassword,
            kSecAttrService as String: service,
            kSecAttrAccount as String: account,
            kSecReturnData as String: true,
            kSecMatchLimit as String: kSecMatchLimitOne
        ]
        var item: CFTypeRef?
        guard SecItemCopyMatching(query as CFDictionary, &item) == errSecSuccess,
              let data = item as? Data,
              let token = String(data: data, encoding: .utf8) else {
            return nil
        }
        return token
    }

    static func store(_ token: String) {
        let data = Data(token.utf8)
        let query: [String: Any] = [
            kSecClass as String: kSecClassGenericPassword,
            kSecAttrService as String: service,
            kSecAttrAccount as String: account
        ]
        let attributes: [String: Any] = [
            kSecValueData as String: data,
            // 与网关侧设备文件一致：token 属于凭据材料，不随备份迁移到新设备。
            kSecAttrAccessible as String: kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly
        ]
        let status = SecItemUpdate(query as CFDictionary, attributes as CFDictionary)
        if status == errSecItemNotFound {
            SecItemAdd(query.merging(attributes) { _, new in new } as CFDictionary, nil)
        }
    }
}