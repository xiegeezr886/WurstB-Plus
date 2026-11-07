"""Apply the fabric/src fixes to every project tree at hunk level.

For each file we take the git diff (HEAD -> working tree) of the canonical
fabric copy and replay its hunks on the other trees:
  * identical file            -> whole-file copy
  * already patched           -> skip
  * version-adapted file      -> per-hunk replacement (context block first,
                                 removed-lines-only fallback)
  * absent / no anchor match  -> report for manual review
"""
import os
import re
import subprocess
import sys

ROOT = r"C:\Users\ui863\Documents\trae_projects\VAP"
CANON_REL = "fabric/src/main/java/net/wurstclient/"
FILES = [
    "altmanager/AltsFile.java",
    "altmanager/Encryption.java",
    "hacks/AimAssistHack.java",
    "hacks/BlinkHack.java",
    "hacks/FlightHack.java",
    "hacks/KillauraHack.java",
    "hacks/KillauraLegitHack.java",
    "hacks/MultiAuraHack.java",
    "hacks/ScaffoldWalkHack.java",
    "hacks/aura/OpenAura.java",
    "hacks/scaffold/HeypixelScaffoldMode.java",
    "hacks/scaffold/PlayerSimulation.java",
    "hacks/scaffold/SilenceTellyScaffoldMode.java",
    "hud2/compat/HudItemIcons.java",
    "util/TargetTracker.java",
]
PACKAGE = os.path.join("main", "java", "net", "wurstclient")
HUNK_RE = re.compile(r"^@@ -(\d+)(?:,(\d+))? \+(\d+)(?:,(\d+))? @@")


def project_src_roots():
    roots = [os.path.join(ROOT, b) for b in
             ("src", os.path.join("fabric", "src"), os.path.join("neoforge", "src"))]
    for sub in ("versions", os.path.join("fabric", "versions"),
                os.path.join("neoforge", "versions")):
        base = os.path.join(ROOT, sub)
        if os.path.isdir(base):
            for name in sorted(os.listdir(base)):
                roots.append(os.path.join(base, name, "src"))
    return [r for r in roots if os.path.isdir(os.path.join(r, PACKAGE))]


def read_norm(path):
    data = open(path, "rb").read()
    bom = data.startswith(b"\xef\xbb\xbf")
    if bom:
        data = data[3:]
    eol = b"\r\n" if data.count(b"\r\n") > data.count(b"\n") / 2 else b"\n"
    return data.replace(b"\r\n", b"\n"), eol, bom


def git_bytes(spec):
    return subprocess.run(["git", "show", spec], cwd=ROOT, capture_output=True,
                          check=True).stdout


def parse_hunks(rel):
    diff = subprocess.run(
        ["git", "diff", "--no-color", "-U3", "HEAD", "--", CANON_REL + rel],
        cwd=ROOT, capture_output=True, check=True).stdout.decode("utf-8", "replace")
    hunks = []
    cur = None
    for line in diff.splitlines():
        if line.startswith("diff --git") or line.startswith("index "):
            continue
        if line.startswith("@@ "):
            cur = {"old": [], "new": []}
            hunks.append(cur)
            continue
        if cur is None:
            continue
        if line.startswith("\\"):
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
    """Return (patched_text, failures). Atomic: caller may discard on failure."""
    failures = []
    for index, hunk in enumerate(hunks):
        old_block = "\n".join(hunk["old"])
        new_block = "\n".join(hunk["new"])
        if old_block:
            count = text.count(old_block)
            if count == 1:
                text = text.replace(old_block, new_block, 1)
                continue
            if count > 1:
                failures.append((index, "ambiguous"))
                continue

        # Context differed (version adaptation): retry with the removed lines
        # only, which are the lines the fix actually touches.
        removed = "\n".join(line for line in hunk["old"]
                            if line not in hunk["new"])
        added = "\n".join(line for line in hunk["new"]
                          if line not in hunk["old"])
        if removed and text.count(removed) == 1:
            text = text.replace(removed, added, 1)
            continue
        failures.append((index, "anchor-missing"))
    return text, failures


def main():
    apply = "--apply" in sys.argv
    roots = [r for r in project_src_roots()
             if os.path.abspath(r) != os.path.abspath(os.path.join(ROOT, "fabric", "src"))]

    summary = []
    for rel in FILES:
        patched_path = os.path.join(ROOT, CANON_REL, rel.replace("/", os.sep))
        patched_norm, _, _ = read_norm(patched_path)
        orig_norm, _, _ = read_norm_bytes(git_bytes("HEAD:" + CANON_REL + rel))
        hunks = parse_hunks(rel)

        copied = patched_already = hunked = absent = failed = 0
        failures_detail = []
        for root in roots:
            target = os.path.join(root, PACKAGE, rel.replace("/", os.sep))
            if not os.path.isfile(target):
                absent += 1
                continue
            norm, eol, bom = read_norm(target)
            if norm == patched_norm:
                patched_already += 1
                continue
            if norm == orig_norm:
                if apply:
                    write_norm(target, patched_norm, eol, bom)
                copied += 1
                continue

            new_text, fails = apply_hunks(norm.decode("utf-8"), hunks)
            if fails:
                failed += 1
                failures_detail.append((os.path.relpath(target, ROOT), fails))
                continue
            if apply:
                write_norm(target, new_text.encode("utf-8"), eol, bom)
            hunked += 1

        summary.append((rel, copied, hunked, patched_already, failed, absent,
                        failures_detail))
        print(f"{rel:52s} copy={copied:2d} hunk={hunked:2d} "
              f"already={patched_already:2d} fail={failed:2d} absent={absent:2d}")

    total_fail = sum(s[4] for s in summary)
    print(f"\nfiles={len(FILES)} failed-trees={total_fail} mode="
          f"{'APPLY' if apply else 'DRY'}")
    for rel, *_rest, detail in summary:
        if not detail:
            continue
        print(f"\n{rel}")
        for path, fails in detail:
            print(f"  {path}  failures={fails}")


def read_norm_bytes(data):
    bom = data.startswith(b"\xef\xbb\xbf")
    if bom:
        data = data[3:]
    eol = b"\r\n" if data.count(b"\r\n") > data.count(b"\n") / 2 else b"\n"
    return data.replace(b"\r\n", b"\n"), eol, bom


def write_norm(path, norm, eol, bom):
    out = norm.replace(b"\n", b"\r\n") if eol == b"\r\n" else norm
    if bom:
        out = b"\xef\xbb\xbf" + out
    with open(path, "wb") as f:
        f.write(out)


if __name__ == "__main__":
    main()
