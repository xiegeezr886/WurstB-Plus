"""Sync S1 (KeybindProcessor), S2/S3 (CmdUtils, WurstClient) fixes.

Strategic files are patched in fabric/src first; this replays the same hunks on
every other project tree (whole-file copy when the target still matches the
pre-patch original).
"""
import os
import subprocess
import sys

ROOT = r"C:\Users\ui863\Documents\trae_projects\VAP"
PKG = os.path.join("main", "java", "net", "wurstclient")
CANON_REL = "fabric/src/main/java/net/wurstclient/"
FILES = [
    "util/CmdUtils.java",
    "keybinds/KeybindProcessor.java",
    "WurstClient.java",
]


def roots():
    result = [os.path.join(ROOT, b) for b in
              ("src", os.path.join("fabric", "src"), os.path.join("neoforge", "src"))]
    for sub in ("versions", os.path.join("fabric", "versions"),
                os.path.join("neoforge", "versions")):
        base = os.path.join(ROOT, sub)
        if os.path.isdir(base):
            for name in sorted(os.listdir(base)):
                result.append(os.path.join(base, name, "src"))
    return [r for r in result if os.path.isdir(os.path.join(r, PKG))]


def read_norm(path):
    data = open(path, "rb").read()
    bom = data.startswith(b"\xef\xbb\xbf")
    if bom:
        data = data[3:]
    eol = b"\r\n" if data.count(b"\r\n") > data.count(b"\n") / 2 else b"\n"
    return data.replace(b"\r\n", b"\n"), eol, bom


def write_norm(path, norm, eol, bom):
    out = norm.replace(b"\n", b"\r\n") if eol == b"\r\n" else norm
    if bom:
        out = b"\xef\xbb\xbf" + out
    open(path, "wb").write(out)


def git_bytes(spec):
    return subprocess.run(["git", "show", spec], cwd=ROOT, capture_output=True,
                          check=True).stdout


def parse_hunks(rel):
    diff = subprocess.run(
        ["git", "diff", "--no-color", "-U3", "HEAD", "--", CANON_REL + rel],
        cwd=ROOT, capture_output=True, check=True).stdout.decode("utf-8", "replace")
    hunks, cur = [], None
    for line in diff.splitlines():
        if line.startswith(("diff --git", "index ")):
            continue
        if line.startswith("@@ "):
            cur = {"old": [], "new": []}
            hunks.append(cur)
            continue
        if cur is None or line.startswith("\\"):
            continue
        if line.startswith("+"):
            cur["new"].append(line[1:])
        elif line.startswith("-"):
            cur["old"].append(line[1:])
        elif line.startswith(" "):
            cur["old"].append(line[1:])
            cur["new"].append(line[1:])
    return hunks


def apply_hunks(text, hunks):
    failures = []
    for index, hunk in enumerate(hunks):
        old_block = "\n".join(hunk["old"])
        new_block = "\n".join(hunk["new"])
        if old_block and text.count(old_block) == 1:
            text = text.replace(old_block, new_block, 1)
            continue
        removed = "\n".join(l for l in hunk["old"] if l not in hunk["new"])
        added = "\n".join(l for l in hunk["new"] if l not in hunk["old"])
        if removed and text.count(removed) == 1:
            text = text.replace(removed, added, 1)
            continue
        failures.append(index)
    return text, failures


def main():
    apply = "--apply" in sys.argv
    targets = [r for r in roots()
               if os.path.abspath(r) != os.path.abspath(os.path.join(ROOT, "fabric", "src"))]

    for rel in FILES:
        patched, _, _ = read_norm(os.path.join(ROOT, CANON_REL, rel))
        orig, _, _ = read_norm_bytes(git_bytes("HEAD:" + CANON_REL + rel))
        hunks = parse_hunks(rel)
        copied = hunked = already = missing = failed = 0
        failed_paths = []
        for root in targets:
            path = os.path.join(root, PKG, rel.replace("/", os.sep))
            if not os.path.isfile(path):
                missing += 1
                continue
            norm, eol, bom = read_norm(path)
            if norm == patched:
                already += 1
                continue
            if norm == orig:
                if apply:
                    write_norm(path, patched, eol, bom)
                copied += 1
                continue
            new_text, fails = apply_hunks(norm.decode("utf-8"), hunks)
            if fails:
                failed += 1
                failed_paths.append(os.path.relpath(path, ROOT))
                continue
            if apply:
                write_norm(path, new_text.encode("utf-8"), eol, bom)
            hunked += 1
        print(f"{rel:40s} copy={copied:2d} hunk={hunked:2d} "
              f"already={already:2d} fail={failed:2d} missing={missing:2d}")
        for p in failed_paths:
            print("    FAIL:", p)


def read_norm_bytes(data):
    bom = data.startswith(b"\xef\xbb\xbf")
    if bom:
        data = data[3:]
    eol = b"\r\n" if data.count(b"\r\n") > data.count(b"\n") / 2 else b"\n"
    return data.replace(b"\r\n", b"\n"), eol, bom


if __name__ == "__main__":
    main()
