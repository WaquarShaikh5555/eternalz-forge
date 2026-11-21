# GitHub sign-in without personal-token creation

## User experience

1. Tap **Continue with GitHub**.
2. Copy the short-lived code, open GitHub in the browser and approve the request.
3. Return to Forge. It obtains the authorized credential and saves it encrypted
   in Android Keystore-backed storage automatically.

Users do not create personal access tokens, register their own OAuth apps, paste
client IDs, or give Forge their GitHub password. This is GitHub's supported OAuth
device authorization flow. It still creates an access token internally, as OAuth
normally does, but the user never has to create or manage one manually.

Official reference:
https://docs.github.com/en/apps/oauth-apps/building-oauth-apps/authorizing-oauth-apps#device-flow

## One-time setup — Eternalz Forge owner only

GitHub requires the product to have its own registered identity. This cannot be
replaced with a stranger's client ID or a client secret embedded in an APK.

1. Open https://github.com/settings/applications/new while signed into the account
   that should own Eternalz Forge's OAuth registration.
2. Register **Eternalz Forge**. For a development registration, the repository URL
   `https://github.com/WaquarShaikh5555/eternalz-forge` can be used as the homepage
   and callback URL. The callback is not used by the device flow. Use a controlled
   product homepage/callback when moving to a production browser-based flow.
3. Enable **Device Flow** in the registration's settings.
4. Copy the **Client ID**. This is public configuration, not a credential. Do not
   copy the client secret, private keys, passwords or access tokens into chat or code.

The current GitHub connection does not provide a supported OAuth-app registration
API; the owner must perform this one consent/registration step in GitHub's UI.
Normal users do none of this.

## Current registration — configured

The owner supplied public client ID `Ov23liwHrytkoVH3CMAd`. It is configured in
`gradle.properties` as `forge.githubClientId`, so both local and CI builds include
it automatically. This identifier is intentionally public; no client secret or
user credential was supplied or stored.

A direct request to GitHub's `/login/device/code` endpoint on 2026-11-21 confirmed
that GitHub accepted the ID and issued a device authorization challenge (Device
Flow is enabled). That diagnostic challenge was not displayed, approved, or
exchanged for a token. This verifies registration readiness, not a completed
user authorization. The owner no longer needs to configure a repository variable.

## Configure or change a registration

The checked-in public Gradle property is the configured default. A `-P` Gradle
argument can override it. If the checked-in property is removed, GitHub Actions
can instead read a repository **variable**, not a secret, called
`GITHUB_OAUTH_CLIENT_ID` containing that public Client ID. Both build/test jobs
already read this variable.

```sh
# Public app identifier only — replace the example with YOUR registered client ID.
gh variable set GITHUB_OAUTH_CLIENT_ID --body YOUR_PUBLIC_CLIENT_ID
```

For local builds, either set the environment variable or use a Gradle property:

```sh
./gradlew -Pforge.githubClientId=YOUR_PUBLIC_CLIENT_ID assembleDebug
```

The ID is compiled into `BuildConfig.GITHUB_OAUTH_CLIENT_ID`, so users never need
an app-configuration field. A release build fails configuration verification if
this value is missing. Debug builds can remain unconfigured for isolated UI tests;
**Continue with GitHub** then honestly explains that owner setup is missing rather
than pretending authentication works or asking regular users to create tokens.

**No GitHub client secret is requested, stored in this repository, or included in
the APK.** Token entry remains a clearly labeled advanced/manual fallback only.

## Permission choices and limitations

- Default: `public_repo`, `read:user`, `notifications` for public-repository
  read/write operations, profile information and notifications.
- **Include private repositories** explicitly replaces `public_repo` with `repo`.
  OAuth `repo` is broad and is not per-repository selection. GitHub displays the
  grant for the user to approve; Forge does not silently escalate it.
- **Edit Actions workflow files** explicitly adds `workflow`.
- `delete_repo` is not requested. Repository deletion may be denied with this
  sign-in method. Fine-grained/manual credentials may have different permissions.
- A fine-grained GitHub App with installations and expiring user-token management
  is the longer-term upgrade for selected repositories and fine-grained permissions;
  this change does not falsely claim to implement that separate lifecycle.
- Browser authorization-code flow would avoid entering a short code, but GitHub's
  documented OAuth code exchange requires a confidential client secret. That
  requires a trusted backend; PKCE does not justify embedding the secret in an APK.
  Device flow keeps this version backend-free and avoids new token-handling services.

Polling honors GitHub's interval and slow-down responses, supports cancellation,
expiry and denied access, and prevents duplicate starts. The device code is held
only in coroutine memory; only the short-lived user code is displayed or copied.
The approved access token is validated through `/user` before an account is saved.

## Verification boundary

Automated tests cover scope selection, client-ID validation, slow-down handling,
owner/setup error messages and the token-free default sign-in UI. A real successful
GitHub approval still requires the owner registration and an interactive test user.
Neither a public client ID nor a successful APK build proves an end-to-end login.


## Build/UI verification — 0.3.0

[CI run 44910837997](https://github.com/WaquarShaikh5555/eternalz-forge/actions/runs/44910837997)
passed Android lint, JVM tests, debug APK assembly and all six emulator UI/security
tests, including the default sign-in/owner-setup test. The short-code grant has
not been exercised against a real registered product client in this session.
That was the pre-registration 0.3.0 build. The owner has since supplied the public
ID and Device Flow is confirmed enabled for the 0.3.1 build.


## Configured APK — 0.3.1

[CI run 44927124185](https://github.com/WaquarShaikh5555/eternalz-forge/actions/runs/44927124185)
passed the non-empty sign-in configuration gate, Android lint, JVM tests, debug
APK assembly and all six emulator UI/security tests for commit
`cea75c36fa03fc9b82428f826c1ab2cd7784f8dd`.

[Download the configured APK artifact](https://github.com/WaquarShaikh5555/eternalz-forge/actions/runs/44927124185/artifacts/14716322420).
Install the extracted `app-debug.apk`, tap Continue with GitHub, copy the one-time
code, open GitHub and approve, then return to Forge. Select Include private
repositories first if private-repository access is wanted. No owner setup or
manual-token entry is required by this configured build.

A live account grant is deliberately left to the user; the successful device-code
probe and build do not claim that an account has already been authorized.
