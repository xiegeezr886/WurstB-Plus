"""Verify round-3 fixes across all trees."""
import os

ROOT = r"C:\Users\ui863\Documents\trae_projects\VAP"
PKG = os.path.join("main", "java", "net", "wurstclient")
CHECKS = {
    "altmanager/Encryption.java": ["legacyKeyDiffers"],
    "altmanager/credentials/CommandCredentialStore.java": ["notFoundExitCode"],
    "altmanager/credentials/CredentialStores.java": ["44, false"],
    "hacks/SurroundHack.java": ["BlockPlacer.place(pos, airPlace, false, null, false)"],
    "hacks/AutoArmorHack.java": ["expectedEquipped"],
    "hud2/GuiEditHUD.java": [],
    "util/BlockPlacer.java": ["boolean rotate)"],
}


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
    if data.startswith(b"\xef\xbb\xbf"):
        data = data[3:]
    return data.replace(b"\r\n", b"\n").decode("utf-8", "replace")


for rel, markers in CHECKS.items():
    fixed = missing = notfixed = 0
    bad = []
    for root in roots():
        path = os.path.join(root, PKG, rel.replace("/", os.sep))
        if not os.path.isfile(path):
            missing += 1
            continue
        text = read(path)
        if rel.endswith("GuiEditHUD.java"):
            ok = "canSnap = true;" not in text.split("mouseClicked", 1)[-1].split(
                "mouseReleased", 1)[0]
        else:
            ok = all(marker in text for marker in markers)
        if ok:
            fixed += 1
        else:
            notfixed += 1
            bad.append(os.path.relpath(path, ROOT))
    print(f"{rel:58s} fixed={fixed:2d} n/a={missing:2d} NOT-FIXED={notfixed:2d}")
    for path in bad:
        print("    NOT FIXED:", path)

json_hits = 0
for tree in ("fabric", "neoforge"):
    directory = os.path.join(ROOT, tree, "src", "main", "resources",
                             "assets", "wurst", "shaders", "post")
    for name in os.listdir(directory):
        if name.endswith(".json") and "wurst:target_effect" in read(
                os.path.join(directory, name)):
            json_hits += 1
print(f"target shader JSONs with wurst: namespace: {json_hits}")
