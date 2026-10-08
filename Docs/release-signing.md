# Release signing (Android)

The release APK must be signed with the project's release key. A debug-signed APK uses a
random per-machine key, so devices cannot upgrade it and Play would reject it. That is why
`androidApp/build.gradle.kts` no longer falls back to the `debug` signing config, and why
`.github/workflows/release.yml` refuses to publish an APK that is not signed with
`CN=DeepSeek Harness Mobile`.

## Keystore

The keystore lives **outside the repository** and must never be committed:

```
~/dsh-release/keystore.jks          # the key material (RSA 4096, PKCS12, alias dsh-release)
~/dsh-release/keystore.properties   # points at it + passwords
```

`androidApp/build.gradle.kts` looks for the properties file in this order:

1. `$DSH_KEYSTORE_PROPERTIES` (absolute path — used by CI)
2. `<repo root>/keystore.properties` (gitignored; convenient for a checkout on a build machine)
3. `~/dsh-release/keystore.properties`

If none is present the `release` signing config is not registered, `assembleRelease`
produces `androidApp-release-unsigned.apk`, and the release workflow fails its signature
check instead of publishing it.

> **Back the keystore up.** If it is lost, existing installs can never be upgraded —
> the app must be uninstalled (losing local data) before a new build can be installed.

## GitHub Actions secrets

`release.yml` reads three repository secrets:

| Secret | Value |
| --- | --- |
| `DSH_KEYSTORE_BASE64` | `base64 -i ~/dsh-release/keystore.jks` (Linux: `base64 -w0`) |
| `DSH_KEYSTORE_PASSWORD` | the `storePassword` from `keystore.properties` |
| `DSH_KEY_PASSWORD` | the `keyPassword` from `keystore.properties` |

Set them with:

```bash
gh secret set DSH_KEYSTORE_BASE64 --body "$(base64 -i ~/dsh-release/keystore.jks)"
gh secret set DSH_KEYSTORE_PASSWORD --body "$(grep '^storePassword=' ~/dsh-release/keystore.properties | cut -d= -f2-)"
gh secret set DSH_KEY_PASSWORD     --body "$(grep '^keyPassword=' ~/dsh-release/keystore.properties | cut -d= -f2-)"
```

## Cutting a release

```bash
# 1. bump versionCode/versionName in androidApp/build.gradle.kts
./gradlew :shared:testAndroidHostTest :androidApp:testDebugUnitTest :androidApp:lintDebug :androidApp:assembleDebug
git commit -am "Bump app version to X.Y.Z"
git tag -a vX.Y.Z -m "DeepSeek Harness Mobile X.Y.Z"
git push origin main vX.Y.Z        # the tag push triggers release.yml
```
