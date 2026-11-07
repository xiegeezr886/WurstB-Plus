"""Enumerate the NoSlowdown fine-grained-setting gap and the shared-file
identity for the HOLD/command fixes."""
import os
import re

ROOT = r"C:\Users\ui863\Documents\trae_projects\VAP"
PKG = os.path.join("main", "java", "net", "wurstclient")


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
    affected = []
    no_policy = []
    for root in roots():
        mixin = os.path.join(root, PKG, "mixin")
        target = None
        for name in ("LocalPlayerMixin.java", "ClientPlayerEntityMixin.java"):
            p = os.path.join(mixin, name)
            if os.path.isfile(p):
                target = p
                break
        if not target:
            continue
        text = read(target)
        rel = os.path.relpath(target, ROOT)
        if "shouldBypassUsingItem" in text:
            continue
        # only count files that actually wrap the item-use slowdown
        matches = [line for line in text.splitlines()
                   if "isSlowDueToUsingItem" in line or "isUsingItem()Z" in line]
        if not matches:
            continue
        # find the wrapper bodies that check only isEnabled()
        bare_enabled = re.findall(
            r"if\(WurstClient\.INSTANCE\.getHax\(\)\.noSlowdownHack\.isEnabled\(\)\)",
            text)
        affected.append((rel, len(bare_enabled), len(matches)))
        if not bare_enabled:
            no_policy.append(rel)

    print(f"files without fine-grained policy but with item-use wrappers: {len(affected)}")
    for rel, bare, wraps in affected:
        print(f"  {rel}  bare-isEnabled={bare} wrappers={wraps}")
    print(f"\nitem-use wrappers w/o either policy: {len(no_policy)}")
    for rel in no_policy:
        print("  ", rel)


if __name__ == "__main__":
    main()
