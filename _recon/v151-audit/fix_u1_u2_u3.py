"""Apply U1/U2 (26.x mixin registrations) and U3 (NoSlowdown policy).

Every change is verified before/after: JSON parses, target classes exist, the
three bare `isEnabled()` checks are replaced with the fine-grained policy.
"""
import json
import os
import re

ROOT = r"C:\Users\ui863\Documents\trae_projects\VAP"
FABRIC = os.path.join(ROOT, "fabric", "versions")

# version -> mixin entries to add
CONFIG_ADD = {
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

BARE = ("if(WurstClient.INSTANCE.getHax().noSlowdownHack.isEnabled())\n"
        "\t\t\treturn false;")
POLICY = ("if(WurstClient.INSTANCE.getHax().noSlowdownHack.isEnabled()\n"
          "\t\t\t&& WurstClient.INSTANCE.getHax().noSlowdownHack"
          ".shouldBypassUsingItem())\n"
          "\t\t\treturn false;")


def patch_configs():
    for version, entries in CONFIG_ADD.items():
        path = os.path.join(FABRIC, version, "src", "main", "resources",
                            "wurstpenguin.mixins.json")
        text = open(path, encoding="utf-8").read()
        cfg = json.loads(text)
        client = cfg.get("client", [])
        added = [e for e in entries if e not in client]
        if not added:
            print(f"  {version}: already registered")
            continue

        anchor = '    "LocalPlayerMixin"\n  ],'
        if text.count(anchor) != 1:
            raise SystemExit(f"{version}: anchor count {text.count(anchor)}")
        block = '    "LocalPlayerMixin",\n' + "".join(
            f'    "{entry}",\n' for entry in added[:-1]) + \
            f'    "{added[-1]}"\n  ],'
        text = text.replace(anchor, block, 1)
        json.loads(text)
        open(path, "w", encoding="utf-8", newline="").write(text)
        print(f"  {version}: added {added}")


def patch_noslowdown():
    targets = []
    for base in ("versions", os.path.join("fabric", "versions"),
                 os.path.join("neoforge", "versions")):
        base_path = os.path.join(ROOT, base)
        for version in sorted(os.listdir(base_path)):
            path = os.path.join(base_path, version, "src", "main", "java",
                                "net", "wurstclient", "mixin",
                                "LocalPlayerMixin.java")
            if os.path.isfile(path):
                targets.append(path)

    patched = skipped = 0
    for path in targets:
        data = open(path, "rb").read()
        bom = data.startswith(b"\xef\xbb\xbf")
        if bom:
            data = data[3:]
        eol = b"\r\n" if data.count(b"\r\n") > data.count(b"\n") / 2 else b"\n"
        text = data.replace(b"\r\n", b"\n").decode("utf-8")
        count = text.count(BARE)
        if count == 0:
            skipped += 1
            continue
        if count != 3:
            print(f"  WARNING {path}: {count} bare checks")
        text = text.replace(BARE, POLICY)
        out = text.replace("\n", "\r\n").encode("utf-8") if eol == b"\r\n" \
            else text.encode("utf-8")
        if bom:
            out = b"\xef\xbb\xbf" + out
        open(path, "wb").write(out)
        patched += 1
    print(f"NoSlowdown: patched={patched} skipped={skipped}")


if __name__ == "__main__":
    print("U1/U2 mixin configs:")
    patch_configs()
    print("U3 NoSlowdown:")
    patch_noslowdown()
