"""Verify round-2 fixes across all trees."""
import os

ROOT = r"C:\Users\ui863\Documents\trae_projects\VAP"
PKG = os.path.join("main", "java", "net", "wurstclient")
CHECKS = {
    "hacks/BacktrackHack.java": ["onWorldChange", "isCurrentWorld", "isCurrent()"],
    "hacks/NoGhostBlocksHack.java": ["QueuedUpdate", "onWorldChange"],
    "event/WurstSubscriber.java": ["createFastCaller", "createReflectiveCaller"],
    "event/EventManager.java": ["methodSignature", "overriddenMethods"],
    "addon/AddonManager.java": ["Skipping a broken addon provider"],
    "hud2/HudManager.java": ["loadModState", "using defaults"],
    "hud2/nanovg/NanoVgBlitter.java": ["MAX_SLOTS", "ensureSlot"],
    "hud2/nanovg/NanoVGUI.java": ["item.translateX()"],
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


def main():
    for rel, markers in CHECKS.items():
        fixed = missing = notfixed = 0
        bad = []
        for root in roots():
            path = os.path.join(root, PKG, rel.replace("/", os.sep))
            if not os.path.isfile(path):
                missing += 1
                continue
            text = read(path)
            if rel.endswith("NanoVGUI.java") and "item(GuiGraphicsExtractor" not in text:
                missing += 1
                continue
            if all(marker in text for marker in markers):
                fixed += 1
            else:
                notfixed += 1
                bad.append(os.path.relpath(path, ROOT))
        print(f"{rel:42s} fixed={fixed:2d} n/a={missing:2d} NOT-FIXED={notfixed:2d}")
        for path in bad:
            print("    NOT FIXED:", path)


if __name__ == "__main__":
    main()
