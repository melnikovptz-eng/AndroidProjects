"""Unit tests for the static PMCA passport validator."""
import importlib.util
import unittest
from pathlib import Path

SCRIPT = Path(__file__).with_name("pmca_module_test.py")
SPEC = importlib.util.spec_from_file_location("pmca_module_test", SCRIPT)
MODULE = importlib.util.module_from_spec(SPEC)
assert SPEC and SPEC.loader
SPEC.loader.exec_module(MODULE)


class ValidatorHelperTests(unittest.TestCase):
    def test_entrypoints_accept_single_and_multiple(self):
        self.assertEqual(MODULE.entrypoints_for({"entrypoint": "a.b.C"}), ["a.b.C"])
        self.assertEqual(MODULE.entrypoints_for({"entrypoints": ["a.B", "c.D"]}), ["a.B", "c.D"])

    def test_dependency_ids_support_structured_and_legacy_canonical_ids(self):
        data = {
            "dependency_ids": ["pmca.alpha", "pmca.beta"],
            "dependencies": [
                {"module_id": "pmca.gamma"},
                {"id": "pmca.delta"},
                "pmca.epsilon",
                "Android SDK 35",
            ],
        }
        self.assertEqual(
            MODULE.dependency_ids(data),
            ["pmca.alpha", "pmca.beta", "pmca.gamma", "pmca.delta", "pmca.epsilon"],
        )

    def test_noncanonical_dependency_prose_is_not_misread_as_module_id(self):
        self.assertEqual(MODULE.dependency_ids({"dependencies": ["Android SDK 35"]}), [])

    def test_semver(self):
        self.assertTrue(MODULE.SEMVER.fullmatch("1.2.3"))
        self.assertFalse(MODULE.SEMVER.fullmatch("1.2"))


if __name__ == "__main__":
    unittest.main()
