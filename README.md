# Nutrius Prime

Android app for viewing and editing a Kalorické Tabulky food diary and syncing daily nutrition and hydration to Health Connect. Built with Kotlin and Jetpack Compose.

## Build requirements

- JDK 17
- Android SDK Platform 34 and Build Tools 34.0.0
- `ANDROID_HOME` pointing to your Android SDK, or `sdk.dir` in an untracked root `local.properties`

Use the checked-in Gradle wrapper. Its Gradle 8.5 distribution is verified with a SHA-256 checksum. No account credentials or device are needed to build or run local checks. The first build downloads dependencies; subsequent builds can use `--offline` when the dependencies are cached.

## Local checks

Run the same checks as CI from the repository root:

```sh
./gradlew :app:testDebugUnitTest :app:lintDebug :app:assembleDebug
```

The default JVM tests use local fixtures and test doubles. They do not call Kalorické Tabulky or Open Food Facts. This also applies to the standard `test` and `check` tasks and to release unit tests.

Outputs:

- Unit test report: `app/build/reports/tests/testDebugUnitTest/index.html`
- Lint report: `app/build/reports/lint-results-debug.html`
- Debug APK: `app/build/outputs/apk/debug/app-debug.apk`

Lint errors fail the build. Warnings are reported without blocking it. Fragment is constrained to 1.6.2 because the barcode scanner otherwise pulls in 1.0.0, which is incompatible with the Activity Result APIs used by the app. This version fits the current SDK 34 dependency stack.

## Live API tests

Tests annotated with `@Category(LiveNetworkTest::class)` are excluded from normal unit test tasks. Run them explicitly:

```sh
./gradlew :app:liveTest
```

Kalorické Tabulky tests require `KALORICKE_COOKIE` in the environment or the untracked root `local.properties`. An environment value takes precedence. Do not commit this file or put credentials in command-line arguments. The existing Kalorické tests include account-specific diagnostics for diary entries on 25 August and 7 September 2026; they are not a portable acceptance suite. Some expired-session cases are skipped by those tests.

The Open Food Facts test requires network access but no cookie and can be run independently:

```sh
./gradlew :app:liveTest --tests com.example.kthealthsync.OpenFoodFactsLiveTest
```

Live tests always run when requested, without reusing cached results. Their reports are separate: `app/build/reports/tests/liveTest/index.html`. Keep these account-dependent tests out of CI. New external API tests must use the `LiveNetworkTest` category; deterministic regression tests belong in the ordinary suite.

## CI

[Android checks](https://github.com/oline-tramp/NutriusPrime/actions/workflows/android.yml) runs on pushes, pull requests, and manual dispatch. It installs Java 17 and the required SDK packages, validates the Gradle wrapper, then runs local tests, Lint, and the debug build. Reports and the APK are available as workflow artifacts. No Kalorické Tabulky credentials are configured in this workflow.

## Install a debug build

With an authorized Android device connected through ADB:

```sh
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb shell am start -W -n com.example.kthealthsync/.MainActivity
```

Health Connect permissions must be granted on the device. Daily records use stable client IDs so repeated syncs update the same day. Duplicates created by older app versions are not removed automatically.
