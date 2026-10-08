# Firebase Cloud Messaging 接入（Android）

`androidApp/google-services.json` **不入库**，与 keystore 同理（见 `Docs/release-signing.md`）。本文件说明如何生成，以及哪些值必须与本项目一致。

## 一次性注册

在 <https://console.firebase.google.com> 新建项目后：

1. **添加 Android 应用**
   - 包名：`com.clarklevis.dsh.android`
     （来源：`androidApp/build.gradle.kts:51` 的 `applicationId`）
2. **注册签名证书 SHA-1 / SHA-256**
   - Debug 证书（每个开发机都不同）：

     ```sh
     keytool -list -v -keystore ~/.android/debug.keystore -alias androiddebugkey \
       -storepass android -keypass android
     ```

   - Release 证书：用你的 keystore，取 `SHA-1` 与 `SHA-256`

   两个都要注册，否则对应构建收不到消息。
3. 在项目设置 → 服务账号 → 生成新的私钥 JSON（用于网关侧 `fcmAccessToken`）。

## 放入位置

```sh
cp /path/to/downloaded/google-services.json androidApp/google-services.json
```

`androidApp/build.gradle.kts` 只在文件存在时应用 `com.google.gms.google-services` 插件，因此缺少它不会让构建失败，只是收不到推送。

## 验证

```sh
./gradlew :androidApp:assembleDebug
adb install -r androidApp/build/outputs/apk/debug/androidApp-debug.apk
adb logcat -s GatewayMessaging
```

看到 `push delivered: kind=... session=...` 说明链路通了。

## 网关侧配置

```yaml
pushEnabled: true
fcmProjectId: <google-services.json 里的 project_id>
fcmAccessToken: <service account 换取的 OAuth2 access token>
```

`fcmAccessToken` 是**短期**令牌（默认 1 小时）。生产部署应改为用 service account 私钥动态换取，而不是把一个会过期的字符串写进配置——当前实现只读配置值，过期后投递会失败并在日志里体现（`push delivery partially failed`）。

## 安全说明

`google-services.json` 不含密钥，但含 `project_id`、`api_key` 与已注册证书指纹，泄露等于把推送入口暴露给第三方。项目约定敏感材料走 CI secrets（`release.yml` 已有 `DSH_KEYSTORE_PROPERTIES` 先例），本地构建则放到仓库外再拷贝。