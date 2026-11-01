#!/usr/bin/env bash
# Builds Clicker for NetCast as one self-contained AppImage for x86_64 Linux, Java runtime included.
# Run from anywhere: desktop/appimage/build.sh
# appimagetool is downloaded once to ~/.cache/appimagetool unless APPIMAGETOOL points at a copy.
set -euo pipefail
cd "$(dirname "$0")/../.."

version=$(sed -n 's/.*const val VERSION = "\(.*\)".*/\1/p' core/src/main/kotlin/dev/netcastremote/model/AppInfo.kt)
./gradlew :desktop:createDistributable

work=desktop/build/appimage
appdir="$work/Clicker.AppDir"
rm -rf "$work"
mkdir -p "$work"
cp -a desktop/build/compose/binaries/main/app/clicker-for-netcast "$appdir"
ln -s bin/clicker-for-netcast "$appdir/AppRun"
cp desktop/appimage/clicker-for-netcast.desktop "$appdir/"
cp desktop/src/main/resources/clicker.png "$appdir/clicker-for-netcast.png"
ln -s clicker-for-netcast.png "$appdir/.DirIcon"

tool=${APPIMAGETOOL:-$HOME/.cache/appimagetool/appimagetool-x86_64.AppImage}
if [ ! -x "$tool" ]; then
    mkdir -p "$(dirname "$tool")"
    curl -L --progress-bar -o "$tool" https://github.com/AppImage/appimagetool/releases/download/continuous/appimagetool-x86_64.AppImage
    chmod +x "$tool"
fi

out="desktop/build/Clicker-for-NetCast-$version-x86_64.AppImage"
# Extract-and-run, so building doesn't need FUSE.
ARCH=x86_64 "$tool" --appimage-extract-and-run "$appdir" "$out"
echo "Built $out"
