"""Identity/variant census for the round-2 audit targets."""
import hashlib
import os

ROOT = r"C:\Users\ui863\Documents\trae_projects\VAP"
PKG = os.path.join("main", "java", "net", "wurstclient")
FILES = [
    "hacks/BacktrackHack.java",
    "hacks/NoGhostBlocksHack.java",
    "event/WurstSubscriber.java",
    "event/EventManager.java",
    "addon/AddonManager.java",
    "hud2/HudManager.java",
    "hud2/nanovg/NanoVgBlitter.java",
    "hud2/nanovg/NanoVGUI.java",
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


def digest(path):
    data = open(path, "rb").read()
    if data.startswith(b"\xef\xbb\xbf"):
        data = data[3:]
    return hashlib.md5(data.replace(b"\r\n", b"\n")).hexdigest()


for rel in FILES:
    groups = {}
    missing = 0
    for root in roots():
        path = os.path.join(root, PKG, rel.replace("/", os.sep))
        if not os.path.isfile(path):
            missing += 1
            continue
        groups.setdefault(digest(path), []).append(os.path.relpath(root, ROOT))
    print(f"{rel}: variants={len(groups)} missing={missing}")
    for h, paths in sorted(groups.items(), key=lambda kv: -len(kv[1])):
        print(f"   {h[:8]} n={len(paths):2d} e.g. {paths[0]}")
