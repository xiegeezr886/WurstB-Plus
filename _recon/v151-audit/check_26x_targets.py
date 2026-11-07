"""Verify the 26.x render/freecam mixin injection targets against the real
Minecraft jars cached by Loom.

For every project version we read the mixin source's `method = "..."` strings,
resolve the target class, and check the descriptor/name exists in the jar via
javap -p -s.
"""
import os
import re
import subprocess

ROOT = r"C:\Users\ui863\Documents\trae_projects\VAP"
JAVAP = r"C:\Users\ui863\.gradle\jdks\eclipse_adoptium-21-amd64-windows.2\bin\javap.exe"
VERSIONS = ["26.1", "26.1.1", "26.1.2", "26.2", "26.3"]
MIXINS = {
    "GuiMixin.java": "net.minecraft.client.gui.Hud",
    "IngameHudMixin.java": "net.minecraft.client.gui.Gui",
    "LevelRendererMixin.java": "net.minecraft.client.renderer.LevelRenderer",
    "freecam/CameraMixin.java": "net.minecraft.client.Camera",
    "freecam/LocalPlayerMixin.java": "net.minecraft.client.player.LocalPlayer",
    "freecam/ItemInHandRendererMixin.java":
        "net.minecraft.client.renderer.ItemInHandRenderer",
}
METHOD_RE = re.compile(r'method\s*=\s*"([^"]+)"')


def find_jar(version):
    base = os.path.join(ROOT, "fabric", "versions", version,
                        ".gradle", "loom-cache", "minecraftMaven")
    jars = []
    for dp, _dn, fs in os.walk(base):
        for f in fs:
            if f.startswith("minecraft-merged") and f.endswith(".jar"):
                jars.append(os.path.join(dp, f))
    return sorted(jars)[-1] if jars else None


def javap(jar, cls):
    out = subprocess.run([JAVAP, "-p", "-s", "-classpath", jar, cls],
                         capture_output=True, text=True, errors="replace")
    return out.stdout


def element_of(desc):
    # "Lnet/minecraft/client/gui/Hud;extractTabList(L...;)V" -> class, name, desc
    m = re.match(r"L([^;]+);(\w+)(\(.*)", desc)
    if not m:
        return None
    return m.group(1).replace("/", "."), m.group(2), m.group(3)


def check(version, mixin_rel, target_cls, jar):
    path = os.path.join(ROOT, "fabric", "versions", version, "src", "main",
                        "java", "net", "wurstclient", "mixin", mixin_rel)
    if not os.path.isfile(path):
        return f"missing source"
    text = open(path, encoding="utf-8", errors="replace").read()
    if f"@Mixin({target_cls.split('.')[-1]}.class)" not in text:
        return "target class mismatch"

    body = javap(jar, target_cls)
    if not body.strip():
        return "target class not in jar"

    names = set()
    descs = set()
    lines = body.splitlines()
    for i, line in enumerate(lines):
        if line.startswith("  ") and "(" in line and ")" in line and not line.strip().startswith("descriptor:"):
            sig = line.strip()
            names.add(sig.split("(")[0].split()[-1])
        if line.strip().startswith("descriptor:") and i > 0:
            descs.add(line.strip().split("descriptor: ")[1])

    problems = []
    for raw in METHOD_RE.findall(text):
        if raw in ("<init>", "<clinit>"):
            continue
        if "(" in raw:
            el = element_of(raw)
            if el is None:
                problems.append(f"cannot parse {raw}")
                continue
            cls, name, desc = el
            if name not in names:
                problems.append(f"no method {name}")
            elif desc not in descs:
                problems.append(f"descriptor drift {name}{desc}")
        else:
            if raw not in names:
                problems.append(f"no method {raw}")
    return "OK" if not problems else "; ".join(problems)


def main():
    for version in VERSIONS:
        jar = find_jar(version)
        print(f"\n== {version}  jar={'yes' if jar else 'MISSING'}")
        if not jar:
            continue
        for mixin, target in MIXINS.items():
            print(f"  {mixin:40s} -> {target}: {check(version, mixin, target, jar)}")


if __name__ == "__main__":
    main()
