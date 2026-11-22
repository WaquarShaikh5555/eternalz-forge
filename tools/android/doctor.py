#!/usr/bin/env python3
"""Report capabilities without downloading tools or accepting SDK licenses."""
import argparse
import json
import os
from pathlib import Path
import shutil
import subprocess

ROOT = Path(__file__).resolve().parents[2]


def inspect(project):
    tools = {name: shutil.which(name) for name in
             ('java', 'javac', 'adb', 'sdkmanager', 'emulator', 'android')}
    sdk = os.environ.get('ANDROID_HOME') or os.environ.get('ANDROID_SDK_ROOT')
    wrapper = project / 'gradlew'
    semgrep = ROOT / '.venv/bin/semgrep'
    return {
        'project': str(project),
        'tools': tools,
        'sdk_root': sdk,
        'sdk_directory_exists': bool(sdk and Path(sdk).is_dir()),
        'gradle_wrapper': wrapper.is_file(),
        'semgrep_installed': semgrep.is_file(),
        'skill_count': len(list((ROOT / '.agents/skills').glob('*/SKILL.md'))),
        'kvm_available': os.access('/dev/kvm', os.R_OK | os.W_OK),
        'build_prerequisites_present': bool(tools['java'] and tools['javac']
                                           and sdk and Path(sdk).is_dir() and wrapper.is_file()),
        'note': 'Presence is not build validation. Check JDK/AGP/Gradle/Kotlin compatibility, SDK packages and dependency access before building.',
    }


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('project', nargs='?', type=Path, default=ROOT)
    parser.add_argument('--require-build', action='store_true')
    args = parser.parse_args()
    report = inspect(args.project.resolve())
    print(json.dumps(report, indent=2))
    return 1 if args.require_build and not report['build_prerequisites_present'] else 0


if __name__ == '__main__':
    raise SystemExit(main())
