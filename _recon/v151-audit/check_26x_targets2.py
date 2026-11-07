"""Verify 26.x mixin injection targets against Loom-cached Minecraft jars.

Handles both `Lcls;name(desc)` and bare `name(desc)` method strings, and uses
the mixin's real @Mixin target class.
"""
import os
import re
import subprocess

ROOT = r"C:\Users\ui863\Documents\trae_projects\VAP"
JAVAP = r"C:\Users\ui863\.gradle\jdks\eclipse_adoptium-21-amd64-windows.2\bin\javap.exe"
VERSIONS = ["26.1", "26.1.1", "26.1.2", "26.2", "26.3"]
MIXINS = [
    "GuiMixin.java",
    "IngameHudMixin.java",
    "LevelRendererMixin.java",
    "freecam/CameraMixin.java",
    "freecam/LocalPlayerMixin.java",
    "freecam/ItemInHandRendererMixin.java",
]
METHOD_RE = re.compile(r'method\s*=\s*"([^"]+)"')
TARGET_RE = re.compile(r"@Mixin\(([A-Za-z0-9_.$]+)\.class\)")


def find_jar(version):
    base = os.path.join(ROOT, "fabric", "versions", version,
                        ".gradle", "loom-cache", "minecraftMaven")
    jars = []
    for dp, _dn, fs in os.walk(base):
        for f in fs:
            if f.startswith("minecraft-merged") and f.endswith(".jar"):
                jars.append(os.path.join(dp, f))
    return sorted(jars)[-1] if jars else None


def javap_members(jar, cls):
    out = subprocess.run([JAVAP, "-p", "-s", "-classpath", jar, cls],
                         capture_output=True, text=True, errors="replace")
    if out.returncode != 0 and "class not found" in (out.stderr or ""):
        return None
    members = {}
    pending = None
    for line in out.stdout.splitlines():
        stripped = line.strip()
        if stripped.startswith("descriptor:"):
            if pending:
                members.setdefault(pending, set()).add(
                    stripped.split("descriptor: ")[1].strip())
                pending = None
            continue
        if not line.startswith("  ") or "(" not in line:
            continue
        if stripped.startswith(("public ", "private ", "protected ", "static ",
                                "final ", "abstract ", "native ", "synchronized ")):
            name = stripped.split("(")[0].split()[-1]
            # method references print as "  public void foo(...)" or
            # "  net.minecraft.X foo(...)" without modifiers
            pending = name
        else:
            name = stripped.split("(")[0].split()[-1]
            pending = name
    return members


def parse_target(method_str):
    if method_str.startswith("L"):
        m = re.match(r"L([^;]+);(\S+?)(\(.*)", method_str)
        if m:
            return m.group(1).replace("/", "."), m.group(2), m.group(3)
        return None
    m = re.match(r"([\w$<>]+)(\(.*)", method_str)
    if m:
        return None, m.group(1), m.group(2)
    return None, method_str, None


def resolve_members(jar, target_cls):
    if "." in target_cls:
        return javap_members(jar, target_cls)
    for pkg in ("net.minecraft.client.gui.", "net.minecraft.client.",
                "net.minecraft.client.renderer.", "net.minecraft.client.player.",
                "net.minecraft.client.multiplayer.", "net.minecraft.world.entity."):
        members = javap_members(jar, pkg + target_cls)
        if members:
            return members
    return None


def check(version, mixin_rel, jar):
    path = os.path.join(ROOT, "fabric", "versions", version, "src", "main",
                        "java", "net", "wurstclient", "mixin", mixin_rel)
    if not os.path.isfile(path):
        return "SOURCE MISSING"
    text = open(path, encoding="utf-8", errors="replace").read()
    target = TARGET_RE.search(text)
    if not target:
        return "NO @Mixin TARGET"
    target_cls = target.group(1)
    members = resolve_members(jar, target_cls)
    if members is None:
        return f"TARGET CLASS MISSING ({target_cls})"

    problems = []
    for raw in METHOD_RE.findall(text):
        if raw in ("<init>", "<clinit>"):
            continue
        cls, name, desc = parse_target(raw)
        if name is None:
            problems.append(f"unparsable {raw}")
        elif name not in members:
            problems.append(f"no method {name}")
        elif desc is not None and desc not in members[name]:
            problems.append(f"desc drift {name}{desc} have {sorted(members[name])[:2]}")
    return "OK" if not problems else "; ".join(problems)


def main():
    for version in VERSIONS:
        jar = find_jar(version)
        print(f"\n== {version}")
        for mixin in MIXINS:
            print(f"  {mixin:38s} {check(version, mixin, jar)}")


if __name__ == "__main__":
    main()
