#!/usr/bin/env bash
# Đóng gói Sổ chi tiêu thành file SoChiTieu.apk, không cần Android Studio.
#
# Cần các gói (Ubuntu hoặc Debian):
#   sudo apt-get install openjdk-17-jdk-headless aapt dalvik-exchange zipalign apksigner android-sdk-platform-23
#
# Chạy:  ./build.sh
set -euo pipefail
cd "$(dirname "$0")"

SDK="${ANDROID_SDK:-/usr/lib/android-sdk}"
NEN="$SDK/platforms/android-23/android.jar"
KHOA="ky-ten/sochitieu.p12"
MAT_KHAU="${MAT_KHAU_KY:-sochitieu}"
RA="SoChiTieu.apk"

for lenh in aapt javac dalvik-exchange zipalign apksigner; do
  command -v "$lenh" >/dev/null || { echo "Thiếu công cụ: $lenh (xem đầu file build.sh)"; exit 1; }
done
[ -f "$NEN" ] || { echo "Thiếu $NEN (gói android-sdk-platform-23)"; exit 1; }

rm -rf build
mkdir -p build/lop

echo "1/5 Gói tài nguyên và giao diện"
aapt package -f -M AndroidManifest.xml -S res -A assets -I "$NEN" -F build/chua-ky.apk

echo "2/5 Biên dịch mã Java"
javac -source 8 -target 8 -Xlint:-options -encoding UTF-8 \
  -bootclasspath "$NEN" -classpath "$NEN" -d build/lop \
  $(find java -name '*.java')

echo "3/5 Chuyển sang mã chạy trên Android"
dalvik-exchange --dex --output=build/classes.dex build/lop

echo "4/5 Ghép và căn chỉnh"
(cd build && aapt add -f chua-ky.apk classes.dex >/dev/null)
zipalign -f -p 4 build/chua-ky.apk build/can-le.apk

echo "5/5 Ký app"
apksigner sign --ks "$KHOA" --ks-key-alias sochitieu \
  --ks-pass "pass:$MAT_KHAU" --key-pass "pass:$MAT_KHAU" \
  --out "$RA" build/can-le.apk
apksigner verify "$RA"
rm -f "$RA.idsig"

echo "Xong: $(pwd)/$RA"
