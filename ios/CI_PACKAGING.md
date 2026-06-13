# iOS CI Packaging

The iOS app must be built on a macOS runner with Xcode and valid Apple signing credentials. This repository includes examples for GitHub Actions, Codemagic, and Bitrise.

## GitHub Actions

Workflow: `.github/workflows/ios-ipa.yml`

Add these repository secrets:

- `APPLE_TEAM_ID`: Apple Developer Team ID.
- `IOS_CERTIFICATE_BASE64`: base64 encoded `.p12` signing certificate.
- `IOS_CERTIFICATE_PASSWORD`: password for the `.p12`.
- `IOS_PROVISIONING_PROFILE_BASE64`: base64 encoded `.mobileprovision`.
- `KEYCHAIN_PASSWORD`: temporary CI keychain password.

Run the workflow manually and choose `development`, `ad-hoc`, or `app-store`.

Create base64 values on macOS:

```sh
base64 -i certificate.p12 | pbcopy
base64 -i profile.mobileprovision | pbcopy
```

## Codemagic

Config: `codemagic.yaml`

In Codemagic, add the signing certificate and provisioning profile under iOS code signing, then select the `ios-ipa` workflow. Change `EXPORT_METHOD` and `distribution_type` for Ad Hoc or App Store builds.

## Bitrise

Config: `bitrise.yml`

Upload the signing certificate and provisioning profile to Bitrise code signing, then run the `ios-ipa` workflow. Set `EXPORT_METHOD` / `BITRISE_EXPORT_METHOD` to match the desired export type.

## Local Mac

```sh
cd ios
EXPORT_METHOD=development TEAM_ID=YOUR_TEAM_ID ./scripts/build-ipa.sh
```

The IPA is exported to `ios/build/export/`.
