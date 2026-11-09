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
OFILE_LEAFNAME=app-release.apk
OFILE=app/build/outputs/apk/release/${OFILE_LEAFNAME}

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
find . -name ${OFILE_LEAFNAME}
ls -lh $OFILE
echo '-------------------'
echo 'Verifying Signature'
echo '-------------------'
$ANDROID_HOME/build-tools/34.0.0/apksigner verify --verbose $OFILE

echo '======='
echo 'Hashing'
echo '-------'
mkdir -p dist
cp $OFILE ./dist
pushd ./dist
shasum -a 256 ${OFILE_LEAFNAME} > ${OFILE_LEAFNAME}.sha256
popd

echo '===='
echo 'Done'
echo '----'
ls -lh ./dist
cat dist/${OFILE_LEAFNAME}.sha256
