"""Compare v1.5.1 (fabric 1.20.1) source tree against Wurst7 upstream 1.20.1.

Emits a CSV inventory of shared files with diff magnitudes plus package rollup.
"""
import csv
import difflib
import os
import sys
from collections import defaultdict

ROOT = r"C:\Users\ui863\Documents\trae_projects\VAP"
FORK = os.path.join(ROOT, "fabric", "src", "main", "java", "net", "wurstclient")
UP = os.path.join(ROOT, "reference", "upstream-sources", "Wurst7_1_20_1_src",
                  "Wurst7-1.20.1", "src", "main", "java", "net", "wurstclient")
OUT = os.path.join(ROOT, "_recon", "v151-audit")


def walk(base):
    out = {}
    for dirpath, _dirnames, filenames in os.walk(base):
        for fn in filenames:
            if fn.endswith(".java"):
                full = os.path.join(dirpath, fn)
                rel = os.path.relpath(full, base).replace("\\", "/")
                out[rel] = full
    return out


def main():
    fork = walk(FORK)
    up = walk(UP)
    shared = sorted(set(fork) & set(up))
    fork_only = sorted(set(fork) - set(up))
    up_only = sorted(set(up) - set(fork))

    rows = []
    for rel in shared:
        with open(fork[rel], encoding="utf-8", errors="replace") as f:
            a = f.read().splitlines()
        with open(up[rel], encoding="utf-8", errors="replace") as f:
            b = f.read().splitlines()
        sm = difflib.SequenceMatcher(None, a, b, autojunk=False)
        added = removed = 0
        for tag, i1, i2, j1, j2 in sm.get_opcodes():
            if tag in ("replace", "delete"):
                removed += i2 - i1
            if tag in ("replace", "insert"):
                added += j2 - j1
        rows.append({
            "path": rel,
            "ratio": round(sm.ratio(), 4),
            "fork_lines": len(a),
            "up_lines": len(b),
            "fork_removed_vs_up": removed,   # lines in fork not in upstream
            "up_added_vs_fork": added,       # lines upstream has that fork lacks
            "churn": removed + added,
        })

    os.makedirs(OUT, exist_ok=True)
    with open(os.path.join(OUT, "shared_diff.csv"), "w", newline="", encoding="utf-8") as f:
        w = csv.DictWriter(f, fieldnames=list(rows[0].keys()))
        w.writeheader()
        w.writerows(rows)

    # rollup per top-level package
    agg = defaultdict(lambda: [0, 0, 0])  # files, churn, identical
    for r in rows:
        pkg = r["path"].split("/")[0] if "/" in r["path"] else "(root)"
        agg[pkg][0] += 1
        agg[pkg][1] += r["churn"]
        if r["churn"] == 0:
            agg[pkg][2] += 1

    with open(os.path.join(OUT, "packages.csv"), "w", newline="", encoding="utf-8") as f:
        w = csv.writer(f)
        w.writerow(["package", "shared_files", "identical", "total_churn"])
        for pkg, (n, churn, ident) in sorted(agg.items(), key=lambda kv: -kv[1][1]):
            w.writerow([pkg, n, ident, churn])

    with open(os.path.join(OUT, "fork_only.txt"), "w", encoding="utf-8") as f:
        f.write("\n".join(fork_only))
    with open(os.path.join(OUT, "upstream_only.txt"), "w", encoding="utf-8") as f:
        f.write("\n".join(up_only))

    print(f"fork files      : {len(fork)}")
    print(f"upstream files  : {len(up)}")
    print(f"shared paths    : {len(shared)}")
    print(f"  identical     : {sum(1 for r in rows if r['churn'] == 0)}")
    print(f"fork-only       : {len(fork_only)}")
    print(f"upstream-only   : {len(up_only)}")
    print("\nTop 40 most-churned shared files:")
    for r in sorted(rows, key=lambda r: -r["churn"])[:40]:
        print(f"  {r['churn']:5d}  ratio={r['ratio']:.2f}  {r['path']}")


if __name__ == "__main__":
    main()
