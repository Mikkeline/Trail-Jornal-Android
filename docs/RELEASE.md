# Release validation — 0.9.1

The current implementation is ready for release validation; it is not a verified production release. Version code/name are 12/0.9.1, compile/target SDK 36, Room schema 4, backup format 2. The application ID remains dev.michelle.trail to preserve installed data and Health Connect ownership.

## Build and test

Use JDK 17, Android Platform 36 and Build Tools 36.0.0. Open this project folder and run:

```sh
./gradlew :app:testDebugUnitTest :app:lintRelease :app:assembleDebug :app:assembleDebugAndroidTest :app:assembleRelease :app:bundleRelease --no-daemon
python3 scripts/check_architecture.py
python3 scripts/check_legacy_migration.py
python3 scripts/check_health_sync_storage.py
python3 scripts/check_native_alignment.py app/build/outputs/apk/release/app-release-unsigned.apk --require-native
```

Check APK ZIP alignment using the SDK's Build Tools executable:

```sh
"$ANDROID_HOME/build-tools/36.0.0/zipalign" -c -P 16 4 app/build/outputs/apk/release/app-release-unsigned.apk
```

ANDROID_HOME must point to the installed SDK for this command. The ELF check covers arm64-v8a/x86_64 LOAD-segment alignment; it does not replace ZIP alignment, app-bundle configuration or actual 16-KB device testing. The cached MapLibre 11.8.0 AAR passes for its two 64-bit libraries. Actual release packaging still needs a successful Gradle build.

CI runs the same build/lint/storage/native checks and retains reports, schemas and unsigned release artifacts. The manual device workflow runs instrumentation on API 36. These workflow changes have not run on GitHub in this session.

## Device acceptance

With a compatible emulator/device connected:

```sh
./gradlew :app:connectedDebugAndroidTest --no-daemon
```

Complete MANUAL_TESTS.md and ACCESSIBILITY.md. Include an in-place schema-3→4 upgrade, old backup recovery, permission revocation/regrant, delayed background jobs/reboot/force-stop, sync deletion/Undo, and Samsung Health refresh. Run the release build on Android 16, a supported older device and a 16-KB device/emulator. Check edge-to-edge insets, predictive back, large-screen layouts and delayed background work after the target-SDK change.

Use the existing application ID and signing key for in-place upgrade tests. Export the journal before switching from a debug-signed installation to a separately signed release. Add release signing and complete the public privacy policy, Health Apps and Data safety declarations before publishing. Generated CI artifacts are unsigned review artifacts, not Play-ready uploads.

## Current blocker

The local Gradle build attempt stops before project tasks because starting its daemon socket fails with java.net.SocketException: Operation not permitted. Direct Kotlin/Compose compilation and JVM/storage checks pass, but KSP generation, release lint, APK/AAB packaging and device execution cannot be claimed from those checks.

Primary documentation:

- [Google Play target API requirements](https://support.google.com/googleplay/android-developer/answer/11926878) — reviewed 2026-11-25; new app/update submissions require API 36.
- [Android 16 behavior changes](https://developer.android.com/about/versions/16/behavior-changes-16)
- [16-KB page-size support](https://developer.android.com/guide/practices/page-sizes)
- [Build for release](https://developer.android.com/build/build-for-release)
- [Publish a Health Connect app](https://developer.android.com/health-and-fitness/health-connect/publish)
