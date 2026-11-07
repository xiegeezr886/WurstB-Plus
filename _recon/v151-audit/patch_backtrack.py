"""Apply the Backtrack fix (S4) to every tree with narrow anchors.

The generic hunk replay fails on hunk 0 because the import block differs per
Minecraft version; all remaining hunks are applied directly instead.
"""
import os
import subprocess

ROOT = r"C:\Users\ui863\Documents\trae_projects\VAP"
PKG = os.path.join("main", "java", "net", "wurstclient")
REL = "hacks/BacktrackHack.java"
CANON = "fabric/src/main/java/net/wurstclient/" + REL


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


def read(path):
    data = open(path, "rb").read()
    bom = data.startswith(b"\xef\xbb\xbf")
    if bom:
        data = data[3:]
    eol = b"\r\n" if data.count(b"\r\n") > data.count(b"\n") / 2 else b"\n"
    return data.replace(b"\r\n", b"\n").decode("utf-8"), eol, bom


def write(path, text, eol, bom):
    out = text.replace("\n", "\r\n").encode("utf-8") if eol == b"\r\n" \
        else text.encode("utf-8")
    if bom:
        out = b"\xef\xbb\xbf" + out
    open(path, "wb").write(out)


def hunks():
    diff = subprocess.run(
        ["git", "diff", "--no-color", "-U3", "HEAD", "--", CANON],
        cwd=ROOT, capture_output=True, check=True).stdout.decode("utf-8", "replace")
    out, cur = [], None
    for line in diff.splitlines():
        if line.startswith("@@ "):
            cur = {"old": [], "new": []}
            out.append(cur)
            continue
        if cur is None or line.startswith(("diff --git", "index ", "\\")):
            continue
        if line.startswith("+"):
            cur["new"].append(line[1:])
        elif line.startswith("-"):
            cur["old"].append(line[1:])
        elif line.startswith(" "):
            cur["old"].append(line[1:])
            cur["new"].append(line[1:])
    return out


IMPORT_OPS = [
    ("import net.minecraft.client.multiplayer.ClientPacketListener;\n",
     "import net.minecraft.client.multiplayer.ClientLevel;\n"
     "import net.minecraft.client.multiplayer.ClientPacketListener;\n"),
    ("import net.wurstclient.events.UpdateListener;\n",
     "import net.wurstclient.events.UpdateListener;\n"
     "import net.wurstclient.events.WorldChangeListener;\n"),
]


def main():
    hs = hunks()[2:]  # imports are handled by IMPORT_OPS
    ok = bad = 0
    for root in roots():
        path = os.path.join(root, PKG, REL.replace("/", os.sep))
        if not os.path.isfile(path):
            continue
        text, eol, bom = read(path)
        if "onWorldChange" in text and "isCurrentWorld" in text:
            ok += 1
            continue

        failures = []
        for old, new in IMPORT_OPS:
            if text.count(old) == 1:
                text = text.replace(old, new, 1)
            else:
                failures.append("import")

        for index, hunk in enumerate(hs):
            old_block = "\n".join(hunk["old"])
            new_block = "\n".join(hunk["new"])
            if not old_block:
                failures.append(f"hunk{index}")
                continue
            if text.count(old_block) == 1:
                text = text.replace(old_block, new_block, 1)
            else:
                failures.append(f"hunk{index}")

        if failures:
            bad += 1
            print(f"FAIL {os.path.relpath(path, ROOT)}: {failures}")
            continue

        write(path, text, eol, bom)
        ok += 1
    print(f"backtrack patched={ok} failed={bad}")


if __name__ == "__main__":
    main()
