"""Run the reproducible local build/test baseline for both Gradle projects."""
from __future__ import annotations

import argparse
import os
import subprocess
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]


def run(command: list[str], *, env: dict[str, str] | None = None) -> None:
    print("$ " + " ".join(command), flush=True)
    subprocess.run(command, cwd=ROOT, env=env, check=True)


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--skip-java", action="store_true")
    parser.add_argument("--skip-python", action="store_true")
    args = parser.parse_args()
    if not args.skip_java:
        wrapper = ["gradlew.bat"] if os.name == "nt" else ["bash", "./gradlew"]
        run(wrapper + ["test", "shadowJar", "acceptanceJar", "--no-daemon", "--no-configuration-cache", "--console=plain"])
        run(wrapper + ["-p", "series", "build", "--no-daemon", "--no-configuration-cache", "--console=plain"])
    if not args.skip_python:
        run([sys.executable, "-m", "unittest", "discover", "-s", "tools", "-p", "test_*.py", "-v"])
        env = os.environ.copy()
        env["PYTHONPATH"] = str(ROOT / "series/tools")
        run([sys.executable, "-m", "unittest", "discover", "-s", "series/tools", "-p", "test_*.py", "-v"], env=env)
        run([sys.executable, "tools/feature_ledger.py"])
        run([sys.executable, "series/tools/check_independence.py"], env=env)


if __name__ == "__main__":
    main()
