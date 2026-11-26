#!/bin/sh
# Build Claude Pill on the phone itself (Debian arm64). Output: pill/out/claudepill.apk
set -e
cd "$(dirname "$0")"
mkdir -p sdk

fetch() {  # <file> <url> <sha256>
  [ -f "$1" ] || curl -sSfL -o "$1" "$2"
  echo "$3  $1" | sha256sum -c --quiet - || { echo "checksum mismatch: $1" >&2; rm -f "$1"; exit 1; }
}
fetch sdk/platform.zip https://dl.google.com/android/repository/platform-34-ext7_r03.zip \
  16fdb74c55e59ae3ef52def135aec713508467bd56d7dabcd8c9be31fa8b20f3
fetch sdk/r8.jar https://dl.google.com/dl/android/maven2/com/android/tools/r8/8.5.35/r8-8.5.35.jar \
  4733945987ee0a840fafc34080b135259e01678412e07212b23f706334290294
[ -f sdk/android.jar ] || unzip -q -o -j sdk/platform.zip '*/android.jar' -d sdk

rm -rf out; mkdir -p out/classes
aapt2 link -o out/base.apk -I sdk/android.jar --manifest AndroidManifest.xml
javac -nowarn -encoding utf-8 --release 8 -cp sdk/android.jar -d out/classes src/com/local/claudepill/*.java
java -cp sdk/r8.jar com.android.tools.r8.D8 --min-api 26 --lib sdk/android.jar --output out $(find out/classes -name '*.class')
(cd out && zip -q base.apk classes.dex)
zipalign -f 4 out/base.apk out/aligned.apk
# a local throw-away signing key, generated once and never committed
[ -f debug.keystore ] || keytool -genkeypair -keystore debug.keystore -storepass android -keypass android \
  -alias k -keyalg RSA -keysize 2048 -validity 10000 -dname CN=local 2>/dev/null
apksigner sign --ks debug.keystore --ks-pass pass:android --out out/claudepill.apk out/aligned.apk
ls -l out/claudepill.apk
