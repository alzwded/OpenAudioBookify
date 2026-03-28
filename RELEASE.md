Release
=======

- `app/build.gradle.kts`
  + `versionCode++`
  + `versionName`.major++
- This:
  ```sh
  ./gradlew clean
  env ANDROID_HOME=~/android/sdk \
      JAVA_HOME=~/android/android-studio/jbr \
      RELEASE_STORE_FILE=oab-keystore.jks \
      RELEASE_STORE_PASSWORD=banana \
      RELEASE_KEY_ALIAS=sign \
      RELEASE_KEY_PASSWORD=potato \
      ./gradlew assembleRelease
  shasum -a 256 app/build/outputs/apk/release/app-release.apk > app/build/outputs/apk/release/app-release.apk.sha256
  ```
- tag to `v${versionName}`
