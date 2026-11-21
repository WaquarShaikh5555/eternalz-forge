# Eternalz Forge

**Your GitHub workspace. Reimagined for Android.**

A native Kotlin / Jetpack Compose GitHub client with a light-first neutral-and-evergreen
interface, encrypted multi-account storage, real GitHub API operations, a mobile
code editor and optimistic, non-force multi-file commits. No WebView. No mock data.

> **Verified development build, not a production release.** Android lint, JVM
> tests and debug APK assembly passed in GitHub Actions. Five Android emulator UI/security tests also pass. Authenticated
> end-to-end behavior still needs testing. Read the
> [implementation status](docs/implementation-status.md) for exact coverage,
> missing flows and verification results.

## Visual revision — 0.2.0

Light mode is now the default. The sign-in/root background mismatch is fixed,
and navigation, repository surfaces and onboarding have been redesigned.
[View the actual emulator screenshots](docs/design-system.md) (test-only sample data).
Dark mode remains optional; existing explicit appearance preferences are respected.

## Install the development build

[Download the debug APK artifact](https://github.com/WaquarShaikh5555/eternalz-forge/actions/runs/44905175855/artifacts/14707936467)
from the [successful CI run](https://github.com/WaquarShaikh5555/eternalz-forge/actions/runs/44905175855).
Sign in to GitHub, download/extract `eternalz-forge-debug.zip`, then install
`app-debug.apk` on an Android 8+ device. Android may ask you to allow installation
from that browser/file manager. This is a debug-signed test build, not a Play Store
release. If Android rejects an update because a previous CI build used a different
debug signing key, copy any local draft contents before uninstalling/reinstalling:
uninstalling removes local credentials and drafts. CI artifacts expire after seven days; rerun CI to regenerate one.

## Build

Open in Android Studio with **JDK 17**, Android **SDK 35**, and SDK build tools.
The project uses AGP 8.9.2, Gradle 8.11.1, Kotlin/Compose compiler 2.1.20 and
Compose BOM 2025.04.01. Minimum Android version: 8.0 / API 26.

```sh
./gradlew lintDebug testDebugUnitTest assembleDebug
# APK: app/build/outputs/apk/debug/app-debug.apk
./gradlew connectedDebugAndroidTest   # authorized test device/emulator required
```

The SHA-pinned [Android CI workflow](.github/workflows/android.yml) runs lint,
unit tests and assembly and uploads a debug APK after successful verification.
Device tests and release signing are separate. This sandbox has no native
Android toolchain; see [tooling setup](docs/android-tooling.md).

## Connect GitHub

**Never put credentials in this repository or in chat.** There are two in-app paths:

1. **Device sign-in:** enter a public GitHub OAuth app client ID with device flow
   enabled. Approve the displayed code on `https://github.com/login/device`.
   The full-workspace flow requests `repo`, `read:user`, `notifications` and
   `workflow`. No client secret is embedded or required. This checkout does not
   contain a registered product-owned OAuth client.
2. **Fine-grained personal token:** use the password-masked in-app field. Select
   only needed repositories and permissions in GitHub. Missing permissions cause
   explicit failures; the app does not silently escalate access.

Common fine-grained repository permissions: Contents (read/write for editing),
Issues, Pull requests, Actions (read/write for rerun/cancel), Checks (read),
Administration (only if managing settings/deletion), Workflows (only when editing
workflow files), Metadata (read). Availability of user/notification/search APIs
varies by credential type. Start read-only and add permissions only as needed.
Organization SSO/policies and branch protection remain authoritative.

Disconnect removes the account and its local drafts from this device. Revoke its
credential separately through GitHub's application/token settings. The app links
there with confirmation.

## Architecture and safety

- `data/GitHubApi.kt`: bounded HTTPS REST/GraphQL transport and Git tree operations;
  no credential-forwarding redirects, no source/token logs, injectable test transport.
- `data/SecureVault.kt`: atomic AES-GCM vault with Android Keystore key, no backups;
  account-isolated credentials, drafts, pins and recent repositories.
- `domain/Models.kt`: models, Git path/ref validation and bounded review diffs.
- `ui/ForgeViewModel.kt`: lifecycle state, device authorization, repositories and
  operations. Manual constructor injection; no unnecessary DI/codegen dependencies.
- `ui/`: Material 3 phone/tablet navigation, light/dark themes, native Markdown,
  editor, staging and confirmation sheets.

Edits are staged locally and durably encrypted only when you select **Stage**.
**Commit & push** checks the captured remote head and uses a non-force ref update.
Concurrent diverging updates fail rather than overwriting newer remote commits.
Executable modes are preserved; symlinks/submodules/binary edits are rejected.
See the [security model](docs/security.md) for residual risks and limits.

## Development tools

```sh
tools/android/bootstrap.sh
python3 tools/android/doctor.py
tools/android/test.sh
tools/android/scan.sh app/src/main
```

Includes 11 pinned Google/JetBrains skills and a local production workflow.
Agent entry point: [AGENTS.md](AGENTS.md).
