"""Cross-project drift inside the v1.5.1 family.

Compares the released Fabric-1.20.1 tree against the same-MC-version sibling
trees (Forge 1.20.2, Fabric 1.20.2, NeoForge 1.20.2) after normalizing the
Yarn->Mojang and loader-specific import noise, and lists core logic files whose
content differs.
"""
import difflib
import os
import re

ROOT = r"C:\Users\ui863\Documents\trae_projects\VAP"
BASE = os.path.join(ROOT, "fabric", "src", "main", "java", "net", "wurstclient")
PEERS = {
    "fabric-1.20.2": os.path.join(ROOT, "fabric", "versions", "1.20.2", "src", "main", "java", "net", "wurstclient"),
    "forge-1.20.2": os.path.join(ROOT, "versions", "1.20.2", "src", "main", "java", "net", "wurstclient"),
    "neoforge-1.20.2": os.path.join(ROOT, "neoforge", "versions", "1.20.2", "src", "main", "java", "net", "wurstclient"),
}

CORE_PREFIXES = ("hacks/", "util/", "hack/", "event/", "events/", "ai/", "settings/",
                 "keybinds/", "command/", "commands/", "mixin/")


def walk(base):
    d = {}
    for dp, _dn, fs in os.walk(base):
        for f in fs:
            if f.endswith(".java"):
                d[os.path.relpath(os.path.join(dp, f), base).replace("\\", "/")] = os.path.join(dp, f)
    return d


def norm(path):
    try:
        lines = open(path, encoding="utf-8", errors="replace").read().splitlines()
    except OSError:
        return []
    out = []
    for ln in lines:
        s = ln.strip()
        if s.startswith(("import ", "package ", "*", "/*", "//")) or s in ("", "{", "}"):
            continue
        # strip loader-specific package prefixes so Fabric/Forge/NeoForge collapse
        s = re.sub(r"net\.(fabricmc|neoforged|minecraftforge)[\w.]*", "LOADER", s)
        out.append(s)
    return out


def main():
    base = walk(BASE)
    for name, root in PEERS.items():
        if not os.path.isdir(root):
            print(f"{name}: MISSING ({root})")
            continue
        peer = walk(root)
        shared = sorted((set(base) & set(peer)))
        core = [p for p in shared if p.startswith(CORE_PREFIXES)]
        diffs = []
        for rel in core:
            a = norm(base[rel])
            b = norm(peer[rel])
            sm = difflib.SequenceMatcher(None, a, b, autojunk=False)
            if sm.ratio() < 0.999:
                diffs.append((round(1 - sm.ratio(), 4), rel, len(a), len(b)))
        diffs.sort(reverse=True)
        print(f"\n== {name}: shared core files={len(core)}, differing={len(diffs)}")
        for r, rel, la, lb in diffs[:15]:
            print(f"   {r:.3f}  {rel}  ({la} vs {lb} lines)")
        only = sorted(set(peer) - set(base))
        missing = sorted(set(base) - set(peer))
        print(f"   peer-only files: {len(only)}   base-only files: {len(missing)}")
        for p in only[:5]:
            print("      peer-only:", p)


if __name__ == "__main__":
    main()
