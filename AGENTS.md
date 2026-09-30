# Deployment version reporting

- Before deploying a changed main-app APK, increment `app/build.gradle.kts`'s `versionCode` so different deployed builds can be distinguished. Keep the settings version label sourced from `BuildConfig`.
- After installation, verify the installed package version. Always include the deployed `versionName` and `versionCode` in the final response. If installation failed, distinguish the built version from the installed version.
