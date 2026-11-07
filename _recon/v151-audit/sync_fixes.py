"""Sync patched files from fabric/src to every project tree that still holds
the pre-patch version.

Only overwrites a target when its normalized content is byte-identical to the
pre-patch original (taken from git HEAD). Divergent copies are reported and
left alone for manual review.
"""
import difflib
import os
import subprocess
import sys

ROOT = r"C:\Users\ui863\Documents\trae_projects\VAP"
CANON_ROOT = os.path.join(ROOT, "fabric", "src", "main", "java", "net", "wurstclient")
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


def project_src_roots():
    roots = []
    for base in ("src", os.path.join("fabric", "src"), os.path.join("neoforge", "src")):
        roots.append(os.path.join(ROOT, base))
    for sub in ("versions", os.path.join("fabric", "versions"), os.path.join("neoforge", "versions")):
        base = os.path.join(ROOT, sub)
        if not os.path.isdir(base):
            continue
        for name in sorted(os.listdir(base)):
            roots.append(os.path.join(base, name, "src"))
    return [r for r in roots if os.path.isdir(os.path.join(r, PACKAGE))]


def git_original(rel):
    path = "fabric/src/main/java/net/wurstclient/" + rel
    out = subprocess.run(["git", "show", "HEAD:" + path], cwd=ROOT,
                         capture_output=True)
    if out.returncode != 0:
        raise RuntimeError(f"git show failed for {path}: {out.stderr[:200]}")
    return out.stdout


def normalize(data: bytes):
    bom = data.startswith(b"\xef\xbb\xbf")
    if bom:
        data = data[3:]
    eol = b"\r\n" if data.count(b"\r\n") > data.count(b"\n") / 2 else b"\n"
    return data.replace(b"\r\n", b"\n"), eol, bom


def apply_eol(data: bytes, eol: bytes):
    if eol == b"\r\n":
        return data.replace(b"\n", b"\r\n")
    return data


def main():
    dry = "--apply" not in sys.argv
    roots = project_src_roots()
    canon = {rel: normalize(open(os.path.join(CANON_ROOT, rel), "rb").read())
             for rel in FILES}
    orig = {rel: normalize(git_original(rel))[0] for rel in FILES}

    print(f"targets: {len(roots)} project src roots; files: {len(FILES)}; mode: "
          f"{'DRY RUN' if dry else 'APPLY'}\n")

    total_synced = total_mismatch = total_missing = 0
    mismatches = []
    for rel in FILES:
        patched_norm = canon[rel][0]
        synced = mismatch = missing = 0
        for root in roots:
            target = os.path.join(root, PACKAGE, rel.replace("/", os.sep))
            if not os.path.isfile(target):
                missing += 1
                continue

            data = open(target, "rb").read()
            norm, eol, bom = normalize(data)
            if norm == patched_norm:
                continue
            if norm != orig[rel]:
                mismatch += 1
                ratio = difflib.SequenceMatcher(
                    None, norm.decode("utf-8", "replace").splitlines(),
                    orig[rel].decode("utf-8", "replace").splitlines(),
                    autojunk=False).ratio()
                mismatches.append((rel, os.path.relpath(target, ROOT), round(ratio, 3)))
                continue

            if not dry:
                out = apply_eol(patched_norm, eol)
                if bom:
                    out = b"\xef\xbb\xbf" + out
                with open(target, "wb") as f:
                    f.write(out)
            synced += 1

        total_synced += synced
        total_mismatch += mismatch
        total_missing += missing
        print(f"{rel:55s} synced={synced:2d} skipped(differ)={mismatch:2d} missing={missing:2d}")

    print(f"\nTOTAL synced={total_synced} skipped(differ)={total_mismatch} missing={total_missing}")
    if mismatches:
        print("\nDivergent copies (left untouched, need manual review):")
        for rel, path, ratio in mismatches:
            print(f"  {ratio:5.3f}  {path}")


if __name__ == "__main__":
    main()
