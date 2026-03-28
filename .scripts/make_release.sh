#!/usr/bin/env bash

cd "$(dirname "$(realpath "$0")")/.."

printf "Enter store password: "
read -rs STORE_PWD
echo
printf "Enter key password: "
read -rs KEY_PWD
echo

export ANDROID_HOME=../../android/sdk

echo '========'
echo 'Cleaning'
echo '--------'
./gradlew clean

echo '=================='
echo 'Assembling release'
echo '------------------'
env \
      JAVA_HOME=../../android/android-studio/jbr \
      RELEASE_STORE_FILE=../../.secret/oab-keystore.jks \
      RELEASE_STORE_PASSWORD="$STORE_PWD" \
      RELEASE_KEY_ALIAS=sign \
      RELEASE_KEY_PASSWORD="$KEY_PWD" \
      ./gradlew assembleRelease
find . -name 'app-release.apk'

echo '======='
echo 'Hashing'
echo '-------'
OFILE=app/build/outputs/apk/release/app-release.apk
shasum -a 256 $OFILE > $OFILE.sha256

echo '===='
echo 'Done'
echo '----'
$ANDROID_HOME/build-tools/34.0.0/apksigner verify --verbose $OFILE
mkdir -p dist
cp $OFILE ./dist
cp $OFILE.sha256 ./dist
ls -lh ./dist
