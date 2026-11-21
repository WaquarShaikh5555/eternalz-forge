import hashlib
import importlib.util
import json
from pathlib import Path
import subprocess
import sys
import tempfile
import unittest

ROOT = Path(__file__).resolve().parents[2]
R8 = ROOT / '.agents/skills/r8-analyzer/scripts'


class ToolingTests(unittest.TestCase):
    def test_vendored_skill_integrity(self):
        lock = json.loads((ROOT / 'tools/android/skills.lock.json').read_text())
        for path, expected in lock['files'].items():
            with self.subTest(path=path):
                self.assertEqual(hashlib.sha256((ROOT / path).read_bytes()).hexdigest(), expected)

    def test_doctor_missing_project_cannot_pass_build_gate(self):
        with tempfile.TemporaryDirectory() as directory:
            result = subprocess.run([sys.executable, str(ROOT / 'tools/android/doctor.py'),
                                     directory, '--require-build'], capture_output=True, text=True)
            self.assertEqual(result.returncode, 1)
            self.assertFalse(json.loads(result.stdout)['gradle_wrapper'])

    def test_r8_protobuf_conversion_and_analysis(self):
        sys.path.insert(0, str(R8))
        import keep_radius_pb2
        import convert_pb_to_json
        spec = importlib.util.spec_from_file_location('r8_analyze', R8 / 'analyze.py')
        analyzer = importlib.util.module_from_spec(spec)
        spec.loader.exec_module(analyzer)
        with tempfile.TemporaryDirectory() as directory:
            base = Path(directory)
            proto = base / 'radius.pb'
            proto.write_bytes(keep_radius_pb2.BlastRadiusContainer().SerializeToString())
            output = base / 'radius.json'
            self.assertTrue(convert_pb_to_json.convert_pb_to_json(str(proto), str(output)))
            analyzer.analyze(str(output), str(base))
            self.assertTrue((base / 'analysis_result.txt').is_file())
            proto.write_bytes(b'not a protobuf')
            self.assertFalse(convert_pb_to_json.convert_pb_to_json(str(proto), str(output)))


if __name__ == '__main__':
    unittest.main()
