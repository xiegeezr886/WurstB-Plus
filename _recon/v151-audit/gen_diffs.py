"""Generate unified diffs (upstream -> fork) for every shared java file."""
import os
import subprocess
import sys

ROOT = r"C:\Users\ui863\Documents\trae_projects\VAP"
FORK = os.path.join(ROOT, "fabric", "src", "main", "java", "net", "wurstclient")
UP = os.path.join(ROOT, "reference", "upstream-sources", "Wurst7_1_20_1_src",
                  "Wurst7-1.20.1", "src", "main", "java", "net", "wurstclient")
OUT = os.path.join(ROOT, "_recon", "v151-audit", "diffs")


def walk(base):
    out = set()
    for dirpath, _dirnames, filenames in os.walk(base):
        for fn in filenames:
            if fn.endswith(".java"):
                out.add(os.path.relpath(os.path.join(dirpath, fn), base).replace("\\", "/"))
    return out


def main():
    os.makedirs(OUT, exist_ok=True)
    shared = sorted(walk(FORK) & walk(UP))
    n = 0
    for rel in shared:
        out_name = rel.replace("/", "__") + ".diff"
        out_path = os.path.join(OUT, out_name)
        with open(out_path, "w", encoding="utf-8", errors="replace") as f:
            # -U2 keeps hunks readable; label sides clearly
            subprocess.run(
                ["git", "diff", "--no-index", "--no-color", "-U2",
                 "--", os.path.join(UP, rel), os.path.join(FORK, rel)],
                stdout=f, stderr=subprocess.DEVNULL, cwd=ROOT)
        n += 1
    print(f"wrote {n} diff files to {OUT}")


if __name__ == "__main__":
    main()
