# 阶段 7.1 暂无额外规则。后续引入序列化 DTO 时在此补充必要 keep 配置。

# Mi Push SDK：保留其通过反射实例化的接收器与命令消息类，避免 release 构建被裁剪后收不到推送。
-keep class com.xiaomi.mipush.sdk.** { *; }
-keep class com.xiaomi.push.** { *; }
-keep class * extends com.xiaomi.mipush.sdk.PushMessageReceiver { *; }

# vivo push SDK：保留其反射实例化的接收器、消息与客户端类，避免 release 构建被裁剪后收不到推送。
-keep class com.vivo.push.** { *; }
-keep class * extends com.vivo.push.sdk.OpenClientPushMessageReceiver { *; }
