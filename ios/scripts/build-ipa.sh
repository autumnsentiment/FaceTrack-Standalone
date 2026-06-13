#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
SCHEME="${SCHEME:-FaceTrackStandalone}"
CONFIGURATION="${CONFIGURATION:-Release}"
BUILD_DIR="${BUILD_DIR:-$ROOT_DIR/build}"
ARCHIVE_PATH="${ARCHIVE_PATH:-$BUILD_DIR/$SCHEME.xcarchive}"
EXPORT_PATH="${EXPORT_PATH:-$BUILD_DIR/export}"
EXPORT_METHOD="${EXPORT_METHOD:-development}"
SIGNING_STYLE="${SIGNING_STYLE:-automatic}"
EXPORT_OPTIONS="${EXPORT_OPTIONS:-$BUILD_DIR/ExportOptions.generated.plist}"

cd "$ROOT_DIR"

if ! command -v pod >/dev/null 2>&1; then
  echo "CocoaPods is required. Install it with: sudo gem install cocoapods" >&2
  exit 1
fi

pod install

rm -rf "$ARCHIVE_PATH" "$EXPORT_PATH"
mkdir -p "$BUILD_DIR" "$EXPORT_PATH"

cat > "$EXPORT_OPTIONS" <<PLIST
<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE plist PUBLIC "-//Apple//DTD PLIST 1.0//EN" "http://www.apple.com/DTDs/PropertyList-1.0.dtd">
<plist version="1.0">
<dict>
	<key>method</key>
	<string>$EXPORT_METHOD</string>
	<key>signingStyle</key>
	<string>$SIGNING_STYLE</string>
	<key>stripSwiftSymbols</key>
	<true/>
	<key>compileBitcode</key>
	<false/>
	<key>destination</key>
	<string>export</string>
PLIST

if [[ -n "${TEAM_ID:-}" ]]; then
cat >> "$EXPORT_OPTIONS" <<PLIST
	<key>teamID</key>
	<string>$TEAM_ID</string>
PLIST
fi

cat >> "$EXPORT_OPTIONS" <<PLIST
</dict>
</plist>
PLIST

xcodebuild archive \
  -workspace "$ROOT_DIR/FaceTrackStandalone.xcworkspace" \
  -scheme "$SCHEME" \
  -configuration "$CONFIGURATION" \
  -destination "generic/platform=iOS" \
  -archivePath "$ARCHIVE_PATH" \
  -allowProvisioningUpdates

xcodebuild -exportArchive \
  -archivePath "$ARCHIVE_PATH" \
  -exportPath "$EXPORT_PATH" \
  -exportOptionsPlist "$EXPORT_OPTIONS" \
  -allowProvisioningUpdates

echo "IPA export complete:"
find "$EXPORT_PATH" -name "*.ipa" -maxdepth 1 -print
