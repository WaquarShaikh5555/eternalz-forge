# Android development capability setup

Research and verification: **2026-11-21**. Scope: this repository and its agent
workspace, not a global installation into every future project or a running IDE.
At the tooling-preparation stage no application or cloud resource was created.
**Subsequent app work:** the repository now contains the native Eternalz Forge app.
Local SDK/JDK restrictions still apply, but a real GitHub Actions runner successfully
ran Android lint, JVM tests and APK assembly. See
[implementation status](implementation-status.md) for the verified artifact and
five passing emulator UI/security tests and remaining authenticated end-to-end work. No backend cloud resource was needed.

## Installed and working now

| Capability | Installation | Verification |
| --- | --- | --- |
| Kotlin-aware static analysis | Semgrep OSS 1.186.0 in `.venv` | Five Kotlin positive/negative fixture tests pass |
| Manifest security candidates | Two local generic Semgrep rules | Two positive/negative fixture tests pass |
| R8 keep-radius analysis | Official Python analyzer + protobuf converter | Synthetic protobuf conversion, report generation and invalid-input test pass |
| Agent skills | 10 Google Android skills + 1 JetBrains Kotlin skill under `.agents/skills` | SHA-256 integrity test passes |
| Production feature workflow | Locally authored `android-production-workflow` skill | Explicitly separate from upstream material |
| Capability diagnostics | `tools/android/doctor.py` | Missing prerequisites correctly fail `--require-build` |
| Reinstallation | `tools/android/bootstrap.sh` + pinned transitive requirements | Python 3.11 Linux environment; `pip check` passes |

These skills are readable by this agent through file tools and routed explicitly
from `AGENTS.md`; `.agents/skills` also follows the common agent-skills layout.
They are not claimed to be registered in a nonexistent IDE/plugin runtime.
The R8 analyzer consumes real build outputs when available; its smoke test uses
synthetic data and does not prove any app has been optimized.

### Commands

```sh
# Restore ignored runtime packages after a workspace reset:
tools/android/bootstrap.sh

# Inspect an app's prerequisites (does not download or accept licenses):
python3 tools/android/doctor.py /path/to/app --require-build

# Scan source without uploading it or downloading registry rules:
tools/android/scan.sh /path/to/app

# Test this capability bundle:
tools/android/test.sh

# Convert and inspect an existing R8 report:
.venv/bin/python .agents/skills/r8-analyzer/scripts/convert_pb_to_json.py input.pb .cache/r8/report.json
.venv/bin/python .agents/skills/r8-analyzer/scripts/analyze.py .cache/r8/report.json
```

Static checks cover GlobalScope.launch, blocking sleep, production WebView
debugging, a trust-all hostname-verifier pattern, world-readable file creation,
cleartext-enabled manifests and explicitly debuggable manifests. They are narrow
patterns, not interprocedural analysis or exhaustive security coverage. Thread.sleep
may be valid on a worker; debug settings may be valid in debug-only source sets.
Review context. Java variants, aliases, computed flags, merged manifests and
network-security-config exceptions need Android Lint and further review. By default
scanner findings return exit 1. Do not count an empty scan as proof of clean code.
The fixture sources are intentionally unsafe; do not include them in an app.

## Research and selection decisions

Sources were checked through GitHub repositories/API (license, archive status,
recent activity, installation instructions and skill content), plus discovery
search across agent/plugin ecosystems. Google provides Claude Code and Codex
marketplace entries in its README. No compatible IDE/agent plugin manager is
installed here, so reviewed files were installed directly rather than running an
unavailable marketplace command. Popularity alone was not a selection criterion.

| Candidate / official resource | Decision and rationale |
| --- | --- |
| [Google Android skills](https://github.com/android/skills) | Installed focused Apache-2.0 skills for adaptive layouts, Compose performance, ANR diagnosis, R8, Intent security, permission security, testing setup, Navigation 3, edge-to-edge and AGP upgrades. Includes local reference documents. Avoided unrelated Wear/TV/XR/AI/billing skills. |
| [JetBrains Kotlin agent skills](https://github.com/Kotlin/kotlin-agent-skills) | Installed Java-to-Kotlin conversion with framework-specific references. Did not add backend JPA, CocoaPods or overlapping KMP AGP migration skills to a native Android workspace. |
| [Google Android CLI](https://developer.android.com/tools/agents/android-cli) | Preferred future SDK/device/preview integration; official skill installer uses dl.google.com, which is outside this sandbox's network allowlist. Not installed. No curl-pipe-shell was run. |
| [Android Studio / SDK](https://developer.android.com/studio) | No JDK/SDK/adb/emulator installed, no /dev/kvm. SDK downloads need Google's blocked host; Maven/Gradle distribution hosts also are not allowlisted. No APK build possible here today. Do not substitute unofficial SDK mirrors. |
| [Semgrep OSS](https://github.com/semgrep/semgrep) | Installed PyPI release 1.186.0 for Python 3.11/Linux with local rules. Runs without Java/SDK; actual Kotlin parsing verified. OSS engine, no Pro login, no remote registry or MCP server required. LGPL-2.1 repository license; dependency licenses remain their own. |
| [ktlint](https://github.com/ktlint/ktlint), [detekt](https://github.com/detekt/detekt), [Slack Compose lint](https://github.com/slackhq/compose-lints) | Maintained candidates for the app's Gradle build. Deferred: no JVM or app/Kotlin version to validate against. Avoided installing redundant linters that cannot execute. Semgrep is the usable interim scanner, not a complete replacement. |
| [Now in Android](https://github.com/android/nowinandroid), [architecture samples](https://github.com/android/architecture-samples) | Official reusable implementation references for modular architecture, offline data, DI and testing. Linked, not copied wholesale or executed. Choose patterns based on app scale. |
| [Compose samples](https://github.com/android/compose-samples) | Official component, theme and interaction references; use with the installed performance/adaptive guidance. Not added as app dependencies. |
| [Coroutines](https://github.com/Kotlin/kotlinx.coroutines), [Turbine](https://github.com/cashapp/turbine) | Preferred async runtime/test candidates when implementing features. Deferred until Kotlin version and tests exist. |
| [Airbnb Lottie Android](https://github.com/airbnb/lottie-android) | Considered for asset-driven vector motion. Not installed without an animation requirement; built-in Compose animation APIs are the default. |
| [Maestro](https://github.com/mobile-dev-inc/Maestro) | Useful later for end-to-end journeys; no device/adb/JVM here, so not installed. Compose semantics tests remain the first app-level UI test choice. |
| Third-party all-in-one Android agent packs | Not installed: duplicate official guidance, broader hooks/MCP privileges, and no demonstrated executable benefit here. No unreviewed shell hooks, background servers or global prompt changes. |
| IDE index MCP / device-control MCP / design MCP | Require a running indexed IDE, authorized device or additional service/authentication. Not available just by copying a skill. Not installed or claimed active. |

## Security, provenance and compatibility review

- Upstream exact commits, selected directories and hashes are in
  `tools/android/skills.lock.json`. Google commit:
  `c72942584fff970dac86ced78b367fce0c441e52`; JetBrains commit:
  `087f5689c1934135ed58718cdf524525e452b7e9`.
- Both sources were non-archived and Apache-2.0 at review. License text is retained
  in every copied upstream skill. No upstream file was modified.
- Selected skills are Markdown/reference material except R8's three Python files.
  Reviewed imports, file access and execution/network surfaces: analyzer reads JSON
  and writes a report; converter reads protobuf and writes JSON; generated schema
  registers protobuf descriptors. No shell spawning/network behavior in these helpers.
  Treat report files as untrusted input and avoid enormous artifacts.
- Guidance contains build commands and upgrade recommendations, not permission to
  execute them. Check exact library/API versions first. Adaptive assumes Compose +
  Navigation 3 and references newer APIs; R8 AGP 9.3 instructions are conditional.
  This is version-scoped guidance, not justification to upgrade every app.
- Semgrep was installed in a local isolated venv from PyPI. Exact resolved versions
  are recorded, but this is not a comprehensive vulnerability audit or a hash-locked
  supply-chain attestation. Bootstrap prefers wheels; peewee's source distribution
  is explicitly permitted because the resolved environment used its build step.
  Dependencies include MCP libraries, but no MCP service was started or registered.
- Scanner metrics and version checks are disabled; scans use local rules and
  `--oss-only`. No tokens, production data, SDK license acceptance, device-control
  permissions or paid services are needed for the installed functionality.
- Runtime packages/caches are ignored to keep the repository small. Reinstall with
  bootstrap if this sandbox's venv disappears. The curated skill/reference bundle
  is about 1.4 MB, not a vendored SDK or dataset.

## Coverage and remaining quality gates

The local production workflow covers ViewModel/StateFlow, coroutines, Room,
DataStore, DI, feature boundaries, Material 3/You tokens, adaptive light/dark UI,
accessibility, Compose animation/gestures, Canvas/graphics, secure storage/networking,
and test/performance gates. The focused upstream skills add detailed procedures.
This is preparation for implementation, not evidence that those app libraries or
an Android renderer are installed.

When app building begins in a permitted native environment:

1. Select a verified JDK/AGP/Gradle/Kotlin/Compose/KSP matrix and SDK versions;
   use a checksum-verified project Gradle wrapper. Do not pick versions blindly.
2. Install only needed official SDK packages and review licenses. Use an authorized
   physical device or accelerated emulator; never expose unauthenticated adb remotely.
3. Add app-specific dependencies, Android Lint, compatible formatting/static-analysis
   plugins, unit/Flow tests, Room migration tests and Compose semantics/screenshot tests.
4. Run actual lint, JVM tests and APK assembly. Then run device tests, accessibility
   checks and light/dark, font-scale, RTL, offline and process-death scenarios.
5. Measure release/profileable startup, frame timings, battery/memory and APK size
   using Macrobenchmark/Baseline Profiles/Perfetto and the installed R8 workflow.
   A static source review cannot establish 60/90/120Hz performance.

**Local sandbox boundary:** code generation, reference-grounded reviews, local
Kotlin/manifest scans and R8 report analysis work here. Android compilation works
through the repository's verified GitHub Actions CI runner, not inside this sandbox.
Compose rendering and five UI/security tests now pass through an API 30 GitHub
Actions emulator; local rendering and device performance measurements remain unavailable.
