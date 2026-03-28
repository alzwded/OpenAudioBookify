#!/usr/bin/env bash

set -e

cd "$(dirname "$(realpath "$0")")/.."

printf "Enter store password: "
read -rs STORE_PWD
echo
printf "Enter key password: "
read -rs KEY_PWD
echo

export ANDROID_HOME=../../android/sdk
OFILE=app/build/outputs/apk/release/app-release.apk

echo '========'
echo 'Cleaning'
echo '--------'
./gradlew clean
rm -rf dist/

echo '=================='
echo 'Assembling release'
echo '------------------'
env \
      JAVA_HOME=../../android/android-studio/jbr \
      RELEASE_STORE_FILE=$(readlink -f ../../.secret/oab-keystore.jks) \
      RELEASE_STORE_PASSWORD="$STORE_PWD" \
      RELEASE_KEY_ALIAS=sign \
      RELEASE_KEY_PASSWORD="$KEY_PWD" \
      ./gradlew assembleRelease
find . -name 'app-release.apk'
$ANDROID_HOME/build-tools/34.0.0/apksigner verify --verbose $OFILE

echo '======='
echo 'Hashing'
echo '-------'
mkdir -p dist
cp $OFILE ./dist
pushd ./dist
shasum -a 256 app-release.apk > app-release.apk.sha256
popd

echo '===='
echo 'Done'
echo '----'
ls -lh ./dist
cat dist/app-release.apk.sha256
