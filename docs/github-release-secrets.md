# GitHub Release Secrets

To run the release workflow end-to-end from GitHub Actions, add these repository secrets:

- `ANDROID_KEYSTORE_BASE64`
  Base64-encoded upload keystore file contents
- `ANDROID_KEYSTORE_PASSWORD`
  Upload keystore password
- `ANDROID_KEY_ALIAS`
  Upload key alias
- `ANDROID_KEY_PASSWORD`
  Upload key password

Optional secret for automatic Play upload:

- `GOOGLE_PLAY_SERVICE_ACCOUNT_JSON`
  Google Play service account JSON as plain text with permission to upload to the target app

Optional repository variable:

- `GOOGLE_PLAY_TRACK`
  Play track name to upload to
  Suggested default: `internal`

## Notes

- The intended primary release path is GitHub Actions in `.github/workflows/android-release.yml`.
- `workflow_dispatch` is suitable for dry-run signed release builds before publishing a GitHub Release.
- The release workflow builds both:
  - `app-release.aab` for Google Play
  - `app-release.apk` for manual testing/distribution
- It also uploads:
  - ProGuard/R8 `mapping.txt`
- If `GOOGLE_PLAY_SERVICE_ACCOUNT_JSON` is not set, the workflow still produces signed release artifacts and attaches them to the GitHub release.
- Example command to create `ANDROID_KEYSTORE_BASE64` on Linux:
  `base64 -w 0 upload-keystore.jks`
