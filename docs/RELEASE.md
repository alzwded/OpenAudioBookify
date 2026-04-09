Release
=======

- `CHANGELOG.md` up to date, newest first
- `app/build.gradle.kts`
  + `versionCode++`
  + `versionName`.major++
- `./.scripts/make_release.sh`
- tag to `v${versionName}`
- create release:
  + latest release notes + sha256 of app-release.apk
  + attach app-release.apk
