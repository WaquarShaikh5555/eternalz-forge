---
name: android-production-workflow
description: Plan and implement a production Android feature with architecture, Material 3 design, Compose motion, persistence, security and measurable quality gates. Use alongside the focused official Android skills.
---

# Production Android implementation workflow

Locally authored project workflow. This is guidance, not an installed compiler or
an upstream Google/JetBrains skill. Begin with `AGENTS.md` and the toolchain doctor.

## 1. Establish a compatible baseline

Inspect the existing version catalog and wrapper before adding dependencies.
Choose stable compatible releases, not arbitrary newest versions. Match Compose
compiler configuration to Kotlin, KSP to Kotlin, and JDK/Gradle to AGP. Record
min/compile/target SDK and why a platform API needs a compatibility fallback.
Do not add Room, Hilt, networking, Lottie or other libraries until a feature needs them.

## 2. Implement a vertical slice

- Prefer feature packages/modules with explicit API boundaries. Start simple;
  extract modules for ownership, reuse or build isolation, not a layer-per-file ritual.
- Stateless screen/composable -> lifecycle ViewModel -> repository -> data sources.
  Add domain use cases for reused or genuinely complex business logic.
- Expose immutable UI state through StateFlow, collect with lifecycle awareness,
  hoist state and handle loading, empty, content, offline and recoverable failure states.
- Own coroutines in lifecycle/application scopes with explicit cancellation;
  inject dispatchers for blocking IO and tests, propagate CancellationException,
  and model failures without catching Throwable indiscriminately.
- Room for relational/offline data, tested schema migrations and transactions;
  DataStore for small preferences. Neither provides encryption by default.
  Do not use DataStore as a relational store or store raw secrets in preferences.
- Hilt where dependency graphs warrant codegen; manual constructor injection for
  small slices. Do not install multiple DI frameworks.
- Use typed routes where the project's Navigation version supports them;
  test back, process restoration, deep links and malformed/external inputs.

## 3. Design before embellishment

Define semantic Material 3 light/dark color schemes, typography, spacing, shapes,
elevation and motion tokens. Support optional dynamic color with deliberate
fallback palettes. Build reusable components with previews for states and themes.
Adapt to window size and posture rather than device-name checks. Preserve state
through rotation, resize and process recreation. Check RTL, long strings, large
font/display scales, TalkBack order/semantics and at least 48dp touch targets.
Check contrast (4.5:1 ordinary text, 3:1 large text) in both themes; never rely
solely on color or animation to communicate state. Avoid decorative glass that
makes text unreadable or burns GPU time.

## 4. Add intentional motion and graphics

- AnimatedVisibility for entry/exit; animateContentSize for measured size changes;
  updateTransition for coordinated state; Animatable for interruptible gestures.
- Give gestures cancellation/velocity behavior, accessible non-gesture alternatives,
  stable keys and restoration semantics. Choose spring damping/stiffness deliberately.
- Shared element/bounds transitions only with compatible APIs and navigation hosts.
- Respect system animation scale/reduced motion; provide static loading alternatives.
  Do not leave infinite decorative animations running offscreen.
- Canvas/drawWithCache for custom drawing; cache paths/brushes outside hot drawing
  loops, use graphicsLayer for transforms when appropriate, avoid per-frame allocation.
- Bound particle counts and blur areas. Check API support (notably blur/RenderEffect),
  provide older-device fallbacks, inspect clipping and overdraw on a real device.
- Prefer built-in Compose motion. Add Airbnb Lottie only for a concrete vector
  animation requirement; review animation assets/licensing and measure render cost.

## 5. Apply security and privacy controls

Use HTTPS and platform TLS verification. Never add trust-all verifiers or put
backend service-role secrets in an APK. Store sensitive key material through
Android Keystore-backed designs; choose maintained encryption APIs after checking
threat model, backups, device lock and recovery requirements. Minimize collected
data, redact logs, keep release builds non-debuggable, restrict exported components,
validate external routes, and request the least permission at the point of use.
DataStore/Room are not vaults; biometrics alone are not an encryption scheme.
Run `tools/android/scan.sh <source-root>` and the two official security skills.
Treat findings as candidates needing context, not a complete OWASP audit.

## 6. Measure and test

- JVM: ViewModel/repository state and errors, coroutine virtual time (runTest),
  Flow ordering/cancellation (Turbine if useful), fake data sources and migrations.
- Compose semantics/UI: loading/error/retry, input, back/deep links, restoration,
  accessibility, and light/dark/adaptive screenshot coverage.
- Use Android Lint; add ktlint for formatting and detekt for deeper Kotlin checks
  only after checking Kotlin/Gradle support. Add Slack Compose lint rules when compatible.
- Execute wrapper tasks appropriate to the app: lint, testDebugUnitTest,
  assembleDebug; connectedDebugAndroidTest needs an authorized device/emulator.
- Profile a release/profileable build using Macrobenchmark/Baseline Profiles and
  Perfetto. Measure cold startup, frame timing/jank, allocations, memory and battery.
  Frame budgets: ~16.7ms at 60Hz, ~11.1ms at 90Hz, ~8.3ms at 120Hz. Never promise
  those rates from source inspection. Check recomposition causes before annotating
  types stable; use the official performance skill and its scope guards.
- Inspect R8 release artifacts for size and keep-rule impact. Test release behavior,
  offline operation, process death, background limits, denied permissions and token expiry.

Finish with the actual commands/results, untested paths and environmental blockers.
