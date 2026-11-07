"""Verify U1, U2, U3, S1, S2, S3 across all project trees."""
import json
import os

ROOT = r"C:\Users\ui863\Documents\trae_projects\VAP"
PKG = os.path.join("main", "java", "net", "wurstclient")
FABRIC = os.path.join(ROOT, "fabric", "versions")


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
    print("== U1/U2 26.x mixin configs ==")
    expected = {
        "26.1": ["GuiMixin", "freecam/CameraMixin", "freecam/LocalPlayerMixin",
                 "freecam/ItemInHandRendererMixin"],
        "26.1.1": ["GuiMixin", "freecam/CameraMixin", "freecam/LocalPlayerMixin",
                   "freecam/ItemInHandRendererMixin"],
        "26.1.2": ["GuiMixin", "freecam/CameraMixin", "freecam/LocalPlayerMixin",
                   "freecam/ItemInHandRendererMixin"],
        "26.2": ["GuiMixin", "LevelRendererMixin", "freecam/CameraMixin",
                 "freecam/LocalPlayerMixin", "freecam/ItemInHandRendererMixin"],
        "26.3": ["GuiMixin", "LevelRendererMixin", "freecam/CameraMixin",
                 "freecam/LocalPlayerMixin", "freecam/ItemInHandRendererMixin"],
    }
    for version, entries in expected.items():
        path = os.path.join(FABRIC, version, "src", "main", "resources",
                            "wurstpenguin.mixins.json")
        cfg = json.loads(open(path, encoding="utf-8").read())
        client = cfg.get("client", [])
        missing = [e for e in entries if e not in client]
        classes = [e for e in entries
                   if not os.path.isfile(os.path.join(
                       FABRIC, version, "src", "main", "java", "net",
                       "wurstclient", "mixin", *e.split("/")) + ".java")]
        print(f"  {version:6s} missing-entries={missing} missing-classes={classes} "
              f"defaultRequire={cfg.get('injectors', {}).get('defaultRequire')}")

    print("\n== U3 NoSlowdown ==")
    u3 = 0
    u3_bad = []
    for root in roots():
        path = os.path.join(root, PKG, "mixin", "LocalPlayerMixin.java")
        if not os.path.isfile(path):
            continue
        text = read(path)
        if "isSlowDueToUsingItem" not in text:
            continue
        good = text.count("shouldBypassUsingItem()")
        bare = text.count("noSlowdownHack.isEnabled())\n\t\t\treturn false;")
        if good:
            u3 += 1
            if bare:
                u3_bad.append((os.path.relpath(path, ROOT), good, bare))
    print(f"  patched files={u3}  files with bare checks={len(u3_bad)}")
    for item in u3_bad[:5]:
        print("   BAD:", item)

    print("\n== S1 HOLD ==")
    s1 = 0
    for root in roots():
        path = os.path.join(root, PKG, "keybinds", "KeybindProcessor.java")
        if not os.path.isfile(path):
            continue
        text = read(path)
        if "isHeldByAnyKey" in text:
            s1 += 1
    print(f"  KeybindProcessor patched={s1}")

    print("\n== S2/S3 CmdUtils ==")
    s23 = 0
    for root in roots():
        path = os.path.join(root, PKG, "util", "CmdUtils.java")
        if not os.path.isfile(path):
            continue
        text = read(path)
        ok = all(marker in text for marker in
                 ("getAllFeatures().stream()", "collectSettingMatches",
                  "findSettingByPath", "matchesName"))
        if ok:
            s23 += 1
        else:
            print("  NOT PATCHED:", os.path.relpath(path, ROOT))
    print(f"  CmdUtils patched={s23}")

    print("\n== S2b getFeatureByName ==")
    s2b = 0
    for root in roots():
        path = os.path.join(root, PKG, "WurstClient.java")
        if not os.path.isfile(path):
            continue
        text = read(path)
        if "HUD modules and the HUD settings feature" in text:
            s2b += 1
        else:
            print("  NOT PATCHED:", os.path.relpath(path, ROOT))
    print(f"  WurstClient patched={s2b}")


if __name__ == "__main__":
    main()
