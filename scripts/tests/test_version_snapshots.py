"""Exercise the snapshot writer with real Git objects, including quoted names."""
import os
from pathlib import Path
import subprocess
import sys
import tempfile
import unittest


SCRIPT = Path(__file__).resolve().parents[1] / "sync-version-branches.py"


class SnapshotTest(unittest.TestCase):
    def test_paths_filter_history_and_repeat_sync(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            env = dict(os.environ, GITHUB_WORKSPACE=directory, PYTHONIOENCODING="utf-8")

            def git(*args):
                return subprocess.check_output(["git", *args], cwd=root)

            git("init", "-q", "-b", "main")
            git("config", "user.name", "Test")
            git("config", "user.email", "test@example.invalid")
            git("config", "core.quotePath", "true")
            shared = "report/\u68c0\u6d4b 01.txt"
            wanted = [shared, "versions/26.3/build.gradle", "fabric/versions/26.3/build.gradle",
                      "neoforge/versions/26.3/build.gradle"]
            for path in wanted + ["versions/26.2/build.gradle", "src/Main.java"]:
                (root / path).parent.mkdir(parents=True, exist_ok=True)
                (root / path).write_bytes(b"fixture\n")
            git("add", ".")
            git("commit", "-qm", "fixture")
            git("commit", "--allow-empty", "-qm", "advance main")
            git("update-ref", "refs/remotes/origin/26.3", "main^")
            cmd = [sys.executable, str(SCRIPT), "--no-push", "--main-ref", "main", "26.3"]
            subprocess.run(cmd, cwd=root, env=env, check=True, capture_output=True)
            names = git("ls-tree", "-rz", "--name-only", "26.3").decode().split("\0")[:-1]
            self.assertCountEqual(names, wanted + ["BRANCH.md"])
            self.assertEqual(git("rev-parse", "26.3^1", "26.3^2"), git("rev-parse", "main^", "main"))
            self.assertEqual(git("rev-parse", "26.3:" + shared), git("rev-parse", "main:" + shared))
            tip = git("rev-parse", "26.3")
            result = subprocess.check_output(cmd, cwd=root, env=env)
            self.assertIn(b"unchanged=1", result)
            self.assertEqual(tip, git("rev-parse", "26.3"))
