# Eternalz Forge — implementation and verification status

## What this delivery is

A native Kotlin/Jetpack Compose Android application source project, application ID
`dev.eternalz.forge`, minimum Android 8 / API 26. There is no WebView, web frontend,
mock server, seeded account data, analytics SDK or hosted token proxy.

**This is not yet a production release.** The requested scope is substantial. The
API paths and user flows below are implemented in source, but Android compilation,
installation and authenticated end-to-end behavior have not been verified in this
sandbox. Do not confuse source coverage with tested production functionality.

## Native implementation coverage

| Area | Implemented source flows | Boundaries |
| --- | --- | --- |
| Authentication | GitHub OAuth device flow, cancellation/expiry/poll slow-down; validate a user-supplied token through `/user` | Requires your registered OAuth app's public client ID and enabled device flow. No embedded client secret. First-party GitHub App registration/installation and expiring-token refresh are not implemented. |
| Accounts | Keystore AES-GCM encrypted atomic vault, account switching, per-account drafts/recents/pins, disconnect; public profile/organizations, follower/following lists, read-only gists, GraphQL contribution calendar | OAuth account revocation remains in GitHub settings. Lists of people/gists/orgs show at most 100. No gist editing or organization administration. |
| Dashboard | Real repository list, recent GitHub events, local recent/pinned repositories, staged-work shortcut, assigned-issue/review-request/created-PR search shortcuts, compact/expanded dashboard | Not an aggregate analytics dashboard. Events use GitHub's activity API and can be delayed. No fabricated counters. |
| Repositories | Owned/collaborated/fork/private/starred filters, loaded-page text filtering, local sort, list/grid, pagination, create, fork, edit description/name/visibility/archive, topics, star/unstar, delete | Collaborated means returned repositories not owned by the signed-in login, including organization repositories. Filters/sort apply to loaded pages; global repository search is separate. No teams, webhooks or branch-protection editor. |
| Code explorer | Directory navigation, UTF-8 text viewing, line numbers, lightweight syntax color, inert native Markdown, repo-scoped GitHub code search, copy, browser download, file-specific commit history | Directory API limits apply. Readme shortcut assumes `README.md`. Binary files, submodules and symlinks are rejected for editing. Native blame is not implemented. |
| Editor | Smart newline indentation, in-file search/selection, create/edit/rename/delete staging, multi-file encrypted draft, review diff, full-draft clipboard export, unsaved-exit warning | Unstaged buffers survive rotation via ViewModel, not process death. Stage explicitly for durable storage. One batch per account, up to 30 files / 2 MB text, individual files up to 500 KB. Highlighting is heuristic, first 60 KB, not an IDE parser. No LSP, completion or terminal. |
| Git writes | Atomic tree/commit creation, executable-mode preservation, captured base head, preflight branch check, non-force ref update; safe-base refresh for unchanged staged paths; head-only revert | No clone/index/working-tree engine. Commit and push are one explicit remote operation. Divergent changes require manual comparison; no automatic merge/conflict resolution or force push. Revert supports only the single-parent branch-head commit. |
| Branches | Create from selected head, switch, rename, delete with typed confirmation; branch comparison and diffs | Default-branch deletion is blocked. GitHub permissions and branch rules still apply. Renaming a branch may invalidate an older draft's target. |
| Issues | List all states, loaded text/state filter, global qualified search, create, edit title/body/assignees/labels/milestone, comment, close/reopen | Assignees/labels use explicit comma-separated names; milestones use numbers. Editing preflights `updated_at` but GitHub's issue API has no atomic compare-and-swap here, so simultaneous edits remain possible. No linked-PR timeline or label/milestone creation UI. |
| Pull requests | List open/closed/merged, create draft/regular PR, description/conversation, changed-file patches, check runs, comments, review, merge with SHA precondition | Conversation/files/check-runs show up to 100 per detail. No inline line comments, separate PR commit-list tab, legacy combined-status aggregation or merge-queue management. GitHub can compute mergeability asynchronously. |
| Actions | Run list, state/conclusion, jobs/steps, bounded job-log text, rerun failed jobs, cancel with confirmation | Log download accepts only specified GitHub signed storage hosts, no bearer token forwarded. 1 MB download / 1500 displayed lines. Expired/private logs may be denied. No workflow YAML builder. |
| Notifications | Unread/all, loaded repository/type/title filtering, issue/PR/commit routing, mark one/all read | Discussions, invitations and other subject types explain that their native detail is unavailable. No push notification service or background polling. |
| Releases/tags | Browse releases, notes and assets; create draft/published release; edit notes; list/create lightweight tags | Asset/file downloads open a confirmed browser link, where GitHub may require its own login. No authenticated native download manager, asset upload, release publishing toggle or tag deletion UI. |
| Search | GitHub repository, code, issue/PR, user and commit search with qualifiers, retained query, explicit submit, pagination | PR search uses `is:pr` in Issues; file-name search uses `filename:` in Code. Search is explicit rather than issuing rate-limited calls on each keystroke. GitHub token/search restrictions apply. |
| UX | Graphite/lime and warm neutral design, light/dark/system themes, Material 3 controls, phone bottom navigation/tablet rail, native sheets, progress/empty/error states, refresh, offline banner | No screenshot, TalkBack, font scaling, RTL, tablet or frame-timing verification has run. English strings are currently inline. No persistent private-data browsing cache. |

## Validation performed in this sandbox

- Existing tooling tests: **3 passed** (skill integrity, doctor gate, R8 conversion/report).
- Local scanner fixture tests: **7 passed** (positive and negative examples).
- Semgrep OSS scanned the app's main Kotlin/manifest sources: **0 findings** for
  the seven local rules; no parser diagnostics. This is a narrow static check,
  not a compiler, whole-program security audit or proof of API behavior.
- Main manifest/resource XML parsed successfully with Python's XML parser.
- Wrapper JAR fetched unmodified from `gradle/gradle` tag `v8.11.1` through GitHub
  API. SHA-256: `2db75c40782f5e8ba1fc278a5574bab070adccb2d21ca5a6e5ed840888448046`.
- Attempted `./gradlew --version`: **blocked**, no Java/JAVA_HOME in this sandbox.

## Tests and CI supplied, but not executed here

- JVM tests for path/ref validation and bounded review diffs.
- Fake-transport commit tests for moved-head/no writes, file modes/deletion,
  non-force updates and concurrent-push rejection.
- Instrumented Compose destructive-confirmation test.
- Instrumented Keystore encryption, restore and account-isolation test. Run this
  only on a test install; it operates on the installed app's vault.
- SHA-pinned GitHub Actions workflow: lintDebug, JVM tests, debug APK assembly and
  artifact upload on push/PR/manual dispatch. This workflow has **not** been run
  by this session. No remote push, deployment, signing key or release was created.

## Release blockers / next verification gates

1. Run CI/Android Studio build and resolve any dependency, Kotlin type, lint or API
   compatibility problems. Dependency versions are selected as a conservative
   known-compatible baseline, not verified as the latest available stack.
2. Register a product-owned GitHub OAuth app and enable device flow, or evaluate a
   GitHub App architecture with fine-grained installations and secure token refresh.
3. Exercise real public/private/organization repository flows with least-privilege
   test accounts. Test permission denials, SSO, rate limits, timeouts after writes,
   branch protection, archived/empty repos and simultaneous pushes.
4. Run emulator/device tests and a complete accessibility/responsiveness pass.
   Add screenshot regression tests and release/profileable Macrobenchmarks.
5. Complete the explicitly missing flows above, improve conflict-resolution UX,
   move strings to resources, review dependencies/licensing, add signed release
   infrastructure and independently security-review authentication/storage/networking.

The full original product brief is **not** claimed complete or production-ready.
This file intentionally records remaining work instead of presenting placeholders
or browser fallbacks as fully native implementations.
