"""Check every tree: does Freecam's camera position have an active consumer?"""
import json
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


def registered_entries(root):
    resources = os.path.join(root, "main", "resources")
    entries = set()
    if not os.path.isdir(resources):
        return entries
    for name in os.listdir(resources):
        if not name.endswith(".json"):
            continue
        try:
            cfg = json.loads(read(os.path.join(resources, name)))
        except Exception:
            continue
        if not isinstance(cfg, dict) or "package" not in cfg:
            continue
        for key in ("client", "mixins", "server"):
            for entry in cfg.get(key, []):
                entries.add(entry.replace("/", "."))
    return entries


def main():
    gaps = []
    for root in roots():
        freecam = os.path.join(root, PKG, "hacks", "FreecamHack.java")
        if not os.path.isfile(freecam):
            continue
        text = read(freecam)
        if "getCamPos(" not in text:
            continue
        mixin_root = os.path.join(root, PKG, "mixin")
        consumers = []
        for dp, _dn, fs in os.walk(mixin_root):
            for f in fs:
                if not f.endswith(".java"):
                    continue
                body = read(os.path.join(dp, f))
                if "getCamPos(" in body and "@Mixin(Camera.class)" in body:
                    rel = os.path.relpath(os.path.join(dp, f), mixin_root)
                    consumers.append(rel[:-5].replace(os.sep, "."))
        registered = registered_entries(root)
        active = [c for c in consumers if c in registered]
        label = os.path.relpath(root, ROOT)
        if not active:
            gaps.append((label, consumers, sorted(registered)[:0]))
            print(f"GAP {label}: consumers-on-disk={consumers} "
                  f"registered={'yes' if any(c in registered for c in consumers) else 'no'}")
    print(f"\ntrees with a Freecam camera consumer gap: {len(gaps)}")


if __name__ == "__main__":
    main()
