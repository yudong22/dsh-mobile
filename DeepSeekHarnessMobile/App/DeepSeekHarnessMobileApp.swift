import SwiftUI
import UserNotifications

private final class AgentNotificationAppDelegate: NSObject, UIApplicationDelegate {
    func application(
        _ application: UIApplication,
        didFinishLaunchingWithOptions launchOptions: [UIApplication.LaunchOptionsKey: Any]? = nil
    ) -> Bool {
        _ = AgentUserNotificationManager.shared
        // 系统通知授权由 RootView 在界面出现后请求；远程注册必须先于授权请求，
        // 否则 token 回调会在授权对话框还在显示时到达。
        application.registerForRemoteNotifications()
        return true
    }

    /// APNs 签发的 device token。持久化后由 AppStore 在通道就绪时上报网关。
    func application(
        _ application: UIApplication,
        didRegisterForRemoteNotificationsWithDeviceToken deviceToken: Data
    ) {
        let token = deviceToken.map { String(format: "%02x", $0) }.joined()
        APNsDeviceTokenStore.store(token)
        Task { @MainActor in
            AgentPushRegistrationManager.shared.registerIfSupported(supportsPush: true)
        }
    }

    func application(
        _ application: UIApplication,
        didFailToRegisterForRemoteNotificationsWithError error: Error
    ) {
        Task { @MainActor in
            AgentPushRegistrationManager.shared.remoteRegistrationFailed(error)
        }
    }

    /// 静默推送：网关用它在 App 被唤醒时补发离线期间的事件。返回 true 表示
    /// 已处理，系统据此保留运行时间以便继续投递。
    func application(
        _ application: UIApplication,
        didReceiveRemoteNotification userInfo: [AnyHashable: Any],
        fetchCompletionHandler completionHandler: @escaping (UIBackgroundFetchResult) -> Void
    ) {
        Task {
            let handled = await AgentPushRegistrationManager.shared.handleRemoteNotification(userInfo)
            completionHandler(handled ? .newData : .noData)
        }
    }
}

@main
struct DeepSeekHarnessMobileApp: App {
    @UIApplicationDelegateAdaptor(AgentNotificationAppDelegate.self) private var notificationAppDelegate
    @StateObject private var hosts = MultiGatewayStore()

    var body: some Scene {
        WindowGroup {
            RootView()
                .id(ObjectIdentifier(hosts.activeStore))
                .environmentObject(hosts.activeStore)
                .environmentObject(hosts)
                .task {
                    await AgentUserNotificationManager.shared.requestAuthorizationIfNeeded()
                }
                .alert("主机连接", isPresented: Binding(get: { hosts.error != nil }, set: { if !$0 { hosts.error = nil } })) {
                    Button("好", role: .cancel) { hosts.error = nil; hosts.cancelPairing() }
                } message: { Text(hosts.error ?? "") }
        }
    }
}
