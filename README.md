# Eternalz Forge

**Your GitHub workspace. Reimagined for Android.**

A native Kotlin / Jetpack Compose GitHub client with a light-first neutral-and-evergreen
interface, encrypted multi-account storage, real GitHub API operations, a mobile
code editor and optimistic, non-force multi-file commits. No WebView. No mock data.

> **Verified development build, not a production release.** Android lint, JVM
> tests and debug APK assembly passed in GitHub Actions. Six Android emulator UI/security tests also pass. Authenticated
> end-to-end behavior still needs testing. Read the
> [implementation status](docs/implementation-status.md) for exact coverage,
> missing flows and verification results.

## GitHub sign-in — 0.3.1

Token creation is no longer the onboarding path. **Continue with GitHub** now
uses device authorization with a build-configured public client ID. No client
secret is embedded. The owner registration is now configured, and GitHub has
confirmed Device Flow is enabled. Users tap Continue with GitHub, approve a
short-lived code on GitHub and return to Forge. See [sign-in details](docs/github-sign-in.md).

## Visual revision — 0.2.0

Light mode is now the default. The sign-in/root background mismatch is fixed,
and navigation, repository surfaces and onboarding have been redesigned.
[View the actual emulator screenshots](docs/design-system.md) (test-only sample data).
Dark mode remains optional; existing explicit appearance preferences are respected.

## Install the development build

[Download the debug APK artifact](https://github.com/WaquarShaikh5555/eternalz-forge/actions/runs/44927124185/artifacts/14716322420)
from the [successful CI run](https://github.com/WaquarShaikh5555/eternalz-forge/actions/runs/44927124185).
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

The default is now **Continue with GitHub**: approve a short-lived device code in
GitHub's browser page and return to Forge. Users no longer need to create/paste
personal access tokens or enter client IDs. Manual-token connection is an advanced
fallback, not the onboarding path.

**Product registration configured:** the public OAuth client ID is in
`gradle.properties`; GitHub has confirmed Device Flow is enabled. Both CI and
local builds include it. The optional `GITHUB_OAUTH_CLIENT_ID` repository variable
is only needed if the checked-in property is removed.
No confidential client secret belongs in this Android application.

See **[GitHub sign-in setup](docs/github-sign-in.md)** for the owner-only steps,
permission choices and testing boundary. Account authorization requires your
approval on GitHub; successful end-to-end account sign-in has not yet been tested
by this session. Release builds reject missing sign-in configuration.

Private-repository and workflow-file permissions are explicit opt-ins. Access
still requires the user's approval and respects GitHub permissions, organization
policies and branch protection. Disconnect is local; revoke access in GitHub settings.

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
