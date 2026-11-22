# Direct APK distribution and compatible updates

## Why earlier builds could not update in place

The previous workflow used Android's automatically generated debug keystore on
disposable GitHub runners. The application ID stayed the same but the certificate
changed between runs. Android correctly refuses to replace an installed app with
one signed by an unrelated key. Increasing versionCode alone cannot fix that.

There is no supported bypass that preserves private app data. A compatible update
of an already-installed disposable-key build requires its original private key.
We have not recovered that key; do not claim migration of those installs. Save
local drafts before choosing to uninstall. Do not clear storage to debug sign-in.

## Permanent key setup (once, repository owner)

Use `bash tools/android/configure-signing.sh` with a GitHub connection authorized
to manage repository Actions secrets. It checks permission and existing secrets
before generating a key, uses the installed OpenSSL (no dependency download),
generates a 4096-bit RSA PKCS12 identity, and uploads key/password as one encrypted
repository Actions secret `FORGE_ANDROID_SIGNING`. The private files are temporary,
mode-restricted and deleted on exit. Neither key nor password is committed or
printed. This script refuses to replace an existing identity.

**Current blocker:** this session's GitHub integration returned HTTP 403 for the
repository Actions-secrets public-key/list endpoints. Source-code push access is
not equivalent to Actions-secrets management permission. The secret has NOT been
created. Grant the GitHub connection that permission before running the script.
Do not send signing passwords or private keys in chat.

Preserve/back up the signing identity through your organization's approved secret
management process. Do not delete/replace the secret between builds. Android
cannot accept updates if its signing identity is lost. A production app should
also use a reviewed release-signing/backup policy rather than distribute debuggable
builds indefinitely.

## CI and direct downloads

CI restores the private identity only into the verification job's temporary
storage, signs the debug APK with that fixed identity, and runs lint/JVM tests.
The emulator job uses an isolated test signing key and does not receive the
production identity. A dependent publication job runs only after both pass. It
verifies the APK with Android's apksigner, and creates a versioned GitHub Release
with a direct `.apk` asset plus SHA-256 checksum and signing-certificate metadata.
It never overwrites a release asset. Bump versionCode AND versionName for a new
published build. No publication occurs without the permanent key; refusing to ship
another disposable-key installer is intentional. ZIP artifacts remain CI diagnostics,
not the user-facing installation route.

The app's **Check for app updates** action is available before and after login.
It uses an unauthenticated request to this fixed repository's latest stable
release, requires the exact expected HTTPS APK URL and version/signing metadata,
and compares the published certificate to the installed certificate. A mismatched
key is explained rather than offered as an in-place update. Android's package
installer performs the actual cryptographic APK signature verification; metadata
alone is not cryptographic proof. The download goes through the browser, followed
by Android's explicit installer confirmation. No silent installs, broad file
permissions, background auto-downloads or credential-bearing release requests.

There is not yet a published permanently signed APK/direct URL from this change.
The user must not be told an update-compatible build is available until key setup,
CI and release publication have actually succeeded.

## Verification result — 0.3.2

[CI run 44930019469](https://github.com/WaquarShaikh5555/eternalz-forge/actions/runs/44930019469)
for `f7a5faafa8d2f9ab3ee4a84b7f3bfd41dbd08410` passed configuration verification,
Android lint, JVM tests/debug assembly and all **eight** emulator tests, including
both injected device-flow recovery/approval tests. The release job was correctly
**skipped** because no permanent signing secret exists. The private-key branch of
the workflow and actual upgrade installation remain unverified until that blocker
is resolved. No direct download URL is claimed for an unpublished APK.

## Explicitly requested test APK — published separately

The user subsequently requested the APK despite the signing blocker.
[Publication run 44986584072](https://github.com/WaquarShaikh5555/eternalz-forge/actions/runs/44986584072)
succeeded, verified the original successful source run/head SHA and APK signature,
and published the exact tested artifact as a **prerelease**, not a stable update:

https://github.com/WaquarShaikh5555/eternalz-forge/releases/download/v0.3.2-test/eternalz-forge-0.3.2-test.apk

This is 17,286,165 bytes, not a ZIP. Its disposable signing identity is unchanged
from the tested CI APK. It is explicitly NOT claimed update-compatible with old
installs or future permanently signed builds. The prerelease has no stable updater
metadata and is excluded from the in-app update channel. The permanent-key blocker
is still unresolved. Waiting for the permanent release avoids another migration.

GitHub installation/permission review starts at https://github.com/settings/installations.
Approving a pending permission request can help only if the integration actually
requests Actions-secrets permission. Reconnecting alone cannot grant a permission
the integration does not support/request. No new OAuth app or personal token is
needed for that permission review; never send credentials in chat.


## 0.3.3 clean UI/deletion test APK

[Direct APK](https://github.com/WaquarShaikh5555/eternalz-forge/releases/download/v0.3.3-test/eternalz-forge-0.3.3-test.apk)
is now published (17,302,549 bytes), with its SHA-256 sidecar. Source run
44987159847 passed lint/JVM tests and all ten emulator tests. Publication run
44987724289 uploaded the verified APK to a pre-created draft, which was then
published through the repository connection. Runner-side release creation was
HTTP 403, but upload to the existing draft succeeded. This remains a test
prerelease with a disposable signing key, not a compatible stable update. The
Actions-secrets endpoint still returned HTTP 403 after the user changed access.
