"""Find EVENTS.add/remove (and subscribe variants) imbalance per file in v1.5.1 fabric tree."""
import os
import re
import sys

BASE = r"C:\Users\ui863\Documents\trae_projects\VAP\fabric\src\main\java\net\wurstclient"

pat_add = re.compile(r"EVENTS\.(?:add|subscribe)\(([^)]*)\)")
pat_remove = re.compile(r"EVENTS\.(?:remove|unsubscribe)\(([^)]*)\)")


def norm(arg: str) -> str:
    # keep only the listener class part (before first comma), strip whitespace
    return arg.split(",")[0].strip()


def main():
    rows = []
    for dirpath, _d, files in os.walk(BASE):
        for fn in files:
            if not fn.endswith(".java"):
                continue
            full = os.path.join(dirpath, fn)
            rel = os.path.relpath(full, BASE).replace("\\", "/")
            with open(full, encoding="utf-8", errors="replace") as f:
                lines = f.readlines()
            adds, removes = {}, {}
            for i, line in enumerate(lines, 1):
                for m in pat_add.finditer(line):
                    adds.setdefault(norm(m.group(1)), []).append(i)
                for m in pat_remove.finditer(line):
                    removes.setdefault(norm(m.group(1)), []).append(i)
            for k, v in adds.items():
                if len(v) != len(removes.get(k, [])):
                    rows.append((rel, k, v, removes.get(k, [])))
            for k, v in removes.items():
                if k not in adds:
                    rows.append((rel, k + " [REMOVE-ONLY]", [], v))

    if not rows:
        print("balanced")
        return
    for rel, k, a, r in sorted(rows):
        print(f"{rel}: {k}\n    add@{a}  remove@{r}")


if __name__ == "__main__":
    main()
