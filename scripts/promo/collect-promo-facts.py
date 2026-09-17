"""Extract the real v1.5.0 feature inventory used by the promo video."""
import io
import json
import os
import re

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.dirname(os.path.dirname(HERE))          # .../WurstB-Plus-main
# The v1.5.0 inventory is read from one v1.5 project; every v1.5 project carries
# the same HackList.
SRC = os.path.join(ROOT, "fabric", "versions", "1.21.5",
                   "src", "main", "java", "net", "wurstclient")
OUT = os.path.join(HERE, "promo-facts.json")

CATEGORY_LABELS = {
    "RENDER": "渲染强化",
    "MOVEMENT": "移动增强",
    "COMBAT": "战斗辅助",
    "BLOCKS": "方块操作",
    "OTHER": "杂项工具",
    "FUN": "娱乐恶搞",
    "ITEMS": "物品管理",
    "CHAT": "聊天社交",
    "Unknown": "其他",
}

CATEGORY_BLURB = {
    "RENDER": "X-Ray、夜视、无遮挡视野、自定义准星与外观",
    "MOVEMENT": "飞行、加速、自动上台阶、防摔、爬墙",
    "COMBAT": "自动攻击、暴击、反击退、瞄准辅助、自动格挡",
    "BLOCKS": "加速挖掘、自动破坏、建筑与放置辅助",
    "OTHER": "杂项工具与日常便捷功能",
    "FUN": "恶搞视觉、按键音效、表情与彩蛋",
    "ITEMS": "自动补货、工具切换、掉落物与库存管理",
    "CHAT": "聊天翻译、反垃圾消息、本地过滤",
    "Unknown": "其他工具",
}


def read(p):
    return io.open(p, encoding="utf-8", errors="replace").read()


def camel_to_words(name):
    """KillAura -> Kill Aura, XRay -> X Ray (kept short for the marquee)."""
    s = re.sub(r"(?<=[a-z0-9])(?=[A-Z])", " ", name)
    s = re.sub(r"(?<=[A-Z])(?=[A-Z][a-z])", " ", s)
    return s


def main():
    hacklist = read(os.path.join(SRC, "hack", "HackList.java"))
    fields = re.findall(r"public final (\w+Hack) (\w+) =", hacklist)
    hacks = []
    hd = os.path.join(SRC, "hacks")
    for cls, _field in fields:
        p = os.path.join(hd, cls + ".java")
        if not os.path.isfile(p):
            hacks.append({"class": cls, "name": camel_to_words(cls[:-4]),
                          "category": None})
            continue
        t = read(p)
        m = re.search(r"Category\.(\w+)", t)
        nm = re.search(r'super\(\s*"([^"]+)"', t)
        hacks.append({
            "class": cls,
            "name": nm.group(1) if nm else camel_to_words(cls[:-4]),
            "category": m.group(1) if m else None,
        })

    by_cat = {}
    for h in hacks:
        by_cat.setdefault(h["category"] or "Unknown", []).append(h)
    by_cat = {k: v for k, v in sorted(by_cat.items(),
                                      key=lambda kv: -len(kv[1]))}

    print(f"hacks: {len(hacks)}")
    for c, v in by_cat.items():
        print(f"  {c:24s} {len(v)}")

    # commands
    cmds = []
    p = os.path.join(SRC, "command", "CmdList.java")
    if os.path.isfile(p):
        cmds = re.findall(r"public final \w+ (\w+) = new", read(p))

    # hud2 elements (declared in HudManager)
    hud = []
    p = os.path.join(SRC, "hud2", "HudManager.java")
    if os.path.isfile(p):
        t = read(p)
        hud = sorted(set(re.findall(r"new (\w+HudElement)\(", t)))

    jars = []
    rel = os.path.join(ROOT, "build", "release-v1.5")
    if os.path.isdir(rel):
        for f in sorted(os.listdir(rel)):
            if f.endswith(".jar"):
                jars.append({"file": f, "bytes": os.path.getsize(os.path.join(rel, f))})

    facts = {
        "hacks": hacks,
        "hack_names": [h["name"] for h in hacks],
        "hack_count": len(hacks),
        "by_category": {k: [h["name"] for h in v] for k, v in by_cat.items()},
        "categories": {k: len(v) for k, v in by_cat.items()},
        "category_blurb": CATEGORY_BLURB,
        "category_label": CATEGORY_LABELS,
        "commands": cmds,
        "command_count": len(cmds),
        "hud_elements": hud,
        "hud_count": len(hud),
        "jars": jars,
        "jar_count": len(jars),
        "release_url":
            "https://github.com/xiegeezr886/WurstB-Plus/releases/tag/v1.5.0",
        "repo_url": "https://github.com/xiegeezr886/WurstB-Plus",
    }
    io.open(OUT, "w", encoding="utf-8").write(
        json.dumps(facts, ensure_ascii=False, indent=1))
    print(f"\ncommands={len(cmds)} hud={len(hud)} jars={len(jars)}")
    print(f"written -> {OUT}")


if __name__ == "__main__":
    main()
