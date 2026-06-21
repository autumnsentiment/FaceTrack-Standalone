#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
SCHEME="${SCHEME:-FaceTrackStandalone}"
CONFIGURATION="${CONFIGURATION:-Release}"
BUILD_DIR="${BUILD_DIR:-$ROOT_DIR/build}"
ARCHIVE_PATH="${ARCHIVE_PATH:-$BUILD_DIR/$SCHEME.xcarchive}"
EXPORT_PATH="${EXPORT_PATH:-$BUILD_DIR/export}"
DERIVED_DATA_PATH="${DERIVED_DATA_PATH:-$BUILD_DIR/DerivedData}"
EXPORT_METHOD="${EXPORT_METHOD:-development}"
SIGNING_STYLE="${SIGNING_STYLE:-automatic}"
EXPORT_OPTIONS="${EXPORT_OPTIONS:-$BUILD_DIR/ExportOptions.generated.plist}"

cd "$ROOT_DIR"

if ! command -v pod >/dev/null 2>&1; then
  echo "CocoaPods is required. Install it with: sudo gem install cocoapods" >&2
  exit 1
fi

pod install

rm -rf "$ARCHIVE_PATH" "$EXPORT_PATH" "$DERIVED_DATA_PATH"
mkdir -p "$BUILD_DIR" "$EXPORT_PATH"

SIGNED_BUILD="${SIGNED_BUILD:-}"
if [[ -z "$SIGNED_BUILD" ]]; then
  if [[ -n "${TEAM_ID:-}" && -n "${IOS_CERTIFICATE_BASE64:-}" && -n "${IOS_PROVISIONING_PROFILE_BASE64:-}" ]]; then
    SIGNED_BUILD=1
  else
    SIGNED_BUILD=0
  fi
fi

if [[ "$SIGNED_BUILD" != "1" ]]; then
  echo "Signing secrets are not configured; building an unsigned IPA artifact."
  echo "This artifact is useful for CI validation and later signing, but it cannot be installed on a real iPhone until it is signed."

  xcodebuild build \
    -workspace "$ROOT_DIR/FaceTrackStandalone.xcworkspace" \
    -scheme "$SCHEME" \
    -configuration "$CONFIGURATION" \
    -sdk iphoneos \
    -destination "generic/platform=iOS" \
    -derivedDataPath "$DERIVED_DATA_PATH" \
    CODE_SIGNING_ALLOWED=NO \
    CODE_SIGNING_REQUIRED=NO \
    CODE_SIGN_IDENTITY=""

  APP_PATH="$DERIVED_DATA_PATH/Build/Products/$CONFIGURATION-iphoneos/$SCHEME.app"
  if [[ ! -d "$APP_PATH" ]]; then
    echo "Expected app bundle not found: $APP_PATH" >&2
    exit 1
  fi

  mkdir -p "$EXPORT_PATH/Payload"
  cp -R "$APP_PATH" "$EXPORT_PATH/Payload/"
  (cd "$EXPORT_PATH" && /usr/bin/zip -qry "$SCHEME-unsigned.ipa" Payload)
  rm -rf "$EXPORT_PATH/Payload"

  echo "Unsigned IPA export complete:"
  find "$EXPORT_PATH" -maxdepth 1 -name "*.ipa" -print
  exit 0
fi

echo "Signing secrets detected; building a signed archive."

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
  DEVELOPMENT_TEAM="$TEAM_ID" \
  CODE_SIGN_STYLE=Automatic \
  -allowProvisioningUpdates

xcodebuild -exportArchive \
  -archivePath "$ARCHIVE_PATH" \
  -exportPath "$EXPORT_PATH" \
  -exportOptionsPlist "$EXPORT_OPTIONS" \
  -allowProvisioningUpdates

echo "IPA export complete:"
find "$EXPORT_PATH" -name "*.ipa" -maxdepth 1 -print
