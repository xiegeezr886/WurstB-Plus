"""U6: validate the equipment slot snapshot before a delayed AutoArmor swap."""
import os
import re
import sys

ROOT = r"C:\Users\ui863\Documents\trae_projects\VAP"
PKG = os.path.join("main", "java", "net", "wurstclient")
REL = os.path.join(PKG, "hacks", "AutoArmorHack.java")

DECL = re.compile(
    r"(?P<indent>[ \t]*)ItemStack expectedArmor\s*=\s*\n?\s*"
    r"inventory\.getItem\(inventorySlot\)\.copy\(\);")
TAIL = re.compile(
    r"\n(?P<indent>[ \t]*)&& ItemStack\.isSameItemSame(?P<mode>Tags|Components)"
    r"\(expectedArmor,\s*\n?[ \t]*MC\.player\.getInventory\(\)\.getItem"
    r"\(inventorySlot\)\),")


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


def main():
    apply = "--apply" in sys.argv
    patched = skipped = 0
    failures = []
    for root in roots():
        path = os.path.join(root, REL)
        if not os.path.isfile(path):
            continue
        text, eol, bom = read(path)
        if "expectedEquipped" in text:
            skipped += 1
            continue

        match = DECL.search(text)
        if match is None:
            failures.append((path, "decl"))
            continue
        indent = match.group("indent")
        text = text[:match.end()] + "\n" + indent \
            + "ItemStack expectedEquipped = oldArmor.copy();" + text[match.end():]

        tail = TAIL.search(text)
        if tail is None:
            failures.append((path, "tail"))
            continue

        equipped = ("MC.player.getInventory().getItem(36 + type)"
                    if "getItem(36 + type)" in text
                    else "MC.player.getInventory().getArmor(type)")
        mode = "isSameItemSame" + tail.group("mode")
        indent = tail.group("indent")
        addition = ("\n" + indent
            + "// The equipment slot can change between planning and the next"
            + "\n" + indent
            + "// queue tick (manual swap, ChestSwap, elytra). Never run a stale"
            + "\n" + indent + "// plan against a different piece."
            + "\n" + indent + "&& " + mode + "(expectedEquipped,"
            + "\n" + indent + "\t" + equipped + ")"
            + "\n" + indent + "&& !(keepElytra.isChecked()"
            + "\n" + indent + "\t&& " + equipped + ".is(Items.ELYTRA)),")
        text = text[:tail.end()] + addition + text[tail.end():]

        if apply:
            write(path, text, eol, bom)
        patched += 1

    print(f"AutoArmor {'patched' if apply else 'to patch'}: {patched}, "
          f"already: {skipped}, failures: {len(failures)}")
    for path, why in failures:
        print(f"    FAIL ({why}): {os.path.relpath(path, ROOT)}")


if __name__ == "__main__":
    main()
