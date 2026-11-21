# Security model

## Trust boundaries

The APK is a public client. It contains no OAuth client secret, GitHub password,
backend service credential or signing key. All GitHub REST/GraphQL calls go
directly to `api.github.com`; device authorization goes to `github.com/login/*`.
System HTTPS certificate/hostname verification is retained. Cleartext transport
is disabled. General authenticated redirects are rejected, not followed.

Job logs use a separate download path: obtain a signed redirect from the fixed
GitHub API endpoint, validate an HTTPS GitHub artifact-storage host, then make a
fresh unauthenticated bounded download. Never forward the user's bearer token to
that storage URL. Signed URLs are neither stored nor logged.

No application logs contain tokens or repository contents. No analytics, remote
crash-reporting, background notification service or content-upload proxy is present.
No cloud database is required. The app uses INTERNET and ACCESS_NETWORK_STATE;
no storage, contacts, location or notification runtime permissions are requested.

## Local data

A per-install Android Keystore AES-GCM key encrypts an app-private AtomicFile in
`noBackupFilesDir`. Every write uses a fresh platform-generated IV. Backups are
disabled. Credentials and drafts are never written as plaintext preferences/files.
Authentication state, pins, recents and staged files are keyed by immutable GitHub
user ID, not a mutable login. An account switch replaces in-memory API/session and
screen state; disconnect removes that account's persistent data.

A Keystore failure locks the app rather than silently erasing or replacing the
vault. Lost keys can make local drafts unrecoverable. Storage is not protected by
an additional biometric prompt and is not a defense against an unlocked/rooted or
fully compromised device. JVM strings holding tokens cannot be reliably zeroed.
Encryption is not claimed hardware-backed on every supported device.

FLAG_SECURE blocks app screenshots and recent-task snapshots. Explicit Copy
operations intentionally expose the selected text to the system clipboard; avoid
copying sensitive source on shared devices. Android's browser/download manager
handles explicitly confirmed file/asset links outside the vault and may persist
those downloads under its own security policy.

## Rendering and inputs

The Markdown renderer is a native TextView implementation. No WebView,
JavaScript, HTML plugin, remote image plugin or custom URI execution is enabled.
Markdown link navigation is disabled. Explicit external links are displayed and
confirmed, require HTTPS with no embedded user-info, and open the browser without
GitHub authorization headers. Repository content and Actions logs are rendered
as text, never interpreted as shell commands or agent instructions.

File paths reject absolute paths, traversal, empty segments, control characters
and backslashes. Git ref names reject dangerous/invalid patterns. URL path/query
components are separately encoded. Text files are decoded strictly as UTF-8 and
limited to 500 KB; symlinks, submodules and binary files are not editable. General
API responses are capped at 4 MB, downloaded logs at approximately 1 MB. No
unbounded polling or transparent write retries are implemented.

## Consequential writes

Remote operations require a confirmation sheet; repository deletion, branch
deletion/rename, PR merge, workflow rerun/cancel and commit/push use explicit typed
confirmation where indicated. GitHub permissions, selected repositories, SSO and
branch protection are never bypassed. Forks and Actions can complete asynchronously.
Timeouts warn the user to refresh before retrying a write with uncertain outcome.

File editing captures a branch-head SHA. Commit creates blobs/tree/commit with
that SHA as its sole parent, then requests a non-force ref update. A changed head
is rejected in preflight; an intervening ordinary push fails the non-fast-forward
update. Failed attempts can leave unreachable blobs/commits on GitHub, but the
local draft remains. This is optimistic Git ancestry protection, not a server-side
CAS transaction against adversarial concurrent force-rewinds. No force operation
exists in the UI. File modes are preserved.

Safe-base refresh only advances a draft if its affected blobs are unchanged (or
its changes were already applied). Conflicting files keep the entire original
draft; no hidden auto-resolution is attempted. Full draft contents can be copied
before manual reconciliation. Review diffs are bounded displays, not the actual
payload; full draft contents are committed.

PR merges send the fetched head SHA; submitted reviews include commit_id. Issue
edits preflight updated_at, but a race can still occur before the issue PATCH
because the implementation has no atomic issue-field CAS. Release/settings edits
also remain collaborative last-writer-wins operations after explicit confirmation.
Revert is restricted to a single-parent current-head commit and uses a new commit
plus non-force update. Root and merge commits require manual resolution.

## Authentication limits / release work

OAuth device flow is supported for a registered client with device flow enabled.
The client ID is a public identifier; this checkout cannot register one on the
product owner's behalf. The public client ID is now build configuration, never an end-user input. Default
OAuth scopes cover public repositories, profile and notifications; private-repository
and workflow-file scopes require explicit opt-in. No delete_repo scope is requested.
The scopes are explained before approval. See `github-sign-in.md`.
A fine-grained PAT offers narrower repository selection; it is validated through
/user, encrypted locally and expires according to its GitHub settings. A 401
requires reconnection. Expiring GitHub App user-token refresh and installation
management are not implemented.

Disconnect is local, not revocation. GitHub's revoke endpoints for OAuth/PAT
administration cannot be safely emulated by embedding a confidential secret in
an APK; the app links to GitHub's settings. Exact fine-grained permissions are
reviewed there rather than inferred from missing OAuth scope headers.

The debug Android build, lint and JVM commit-safety tests passed in CI. The
Keystore/account-isolation and typed-confirmation tests also pass on an API 30
emulator. Authenticated integration tests, release-build validation and independent
security review are still required before release.
The seven local static rules are a small regression net, not a security certificate.


## Design test capture boundary

The shipping MainActivity still uses FLAG_SECURE. Screenshot tests run in the
separate Compose test activity with synthetic fixtures, not a real connected
account. They save test-only images to the disposable emulator's MediaStore so
Gradle's uninstall cleanup does not erase the CI output. No production screenshot
or content-export service was added. Full-resolution images are in the CI artifact;
small JPEG previews are retained in `docs/screenshots/`.
