# -*- coding: utf-8 -*-
"""Descriptions for the FO/IMG/Leaves batch-4 hacks (66 v1.5 projects)."""
import json
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).parent))
from add_upstream_hacks import REPO, project_dirs

CLASSES = [
    'AutoTrashHack', 'ElytraCollectorHack', 'AutoAnchorHack',
    'AutoArmorPlusHack', 'AutoLoginHack', 'AutoOffHandHack',
    'AutoPlaceBlockHack', 'AttackRangeIndicatorHack', 'EntityTagsHack',
    'HitboxESPHack', 'ItemDespawnTimerHack', 'LavaESPHack',
    'OreVeinESPHack', 'AutoRefreshTradeHack', 'DeathRenderHack',
    'ElytraGrimAccelerateHack', 'FireworkElytraFlyHack', 'GlassFillerHack',
    'AutoDeoxidizerHack', 'AutoSmithingHack', 'AutoVaultHack',
    'ChatHighlightHack', 'ShieldEspHack', 'CrystalAuraPlusHack',
    'PlaceRenderHack', 'PrinterHack', 'PushCrystalHack', 'ReplenishHack',
    'AutoTorchHack', 'FastCrossbowHack',
]

EN = {
    'AutoTrashHack': 'Drops configured items from the inventory.',
    'ElytraCollectorHack': 'Collects dropped items while gliding.',
    'AutoAnchorHack': 'Places and detonates respawn anchors on targets.',
    'AutoArmorPlusHack': 'Automatically equips the best armor.',
    'AutoLoginHack': 'Sends a login command after joining a server.',
    'AutoOffHandHack': 'Keeps a configured item in the offhand.',
    'AutoPlaceBlockHack': 'Automatically places a configured block.',
    'AttackRangeIndicatorHack': 'Shows your attack range.',
    'EntityTagsHack': 'Shows names, health and distance above entities.',
    'HitboxESPHack': 'Renders entity hitboxes.',
    'ItemDespawnTimerHack': 'Shows the despawn timer of dropped items.',
    'LavaESPHack': 'Highlights lava source blocks.',
    'OreVeinESPHack': 'Highlights ore veins.',
    'AutoRefreshTradeHack': 'Refreshes villager trades automatically.',
    'DeathRenderHack': 'Marks death positions.',
    'ElytraGrimAccelerateHack': 'Grim-compatible elytra acceleration.',
    'FireworkElytraFlyHack': 'Flies with an elytra using fireworks.',
    'GlassFillerHack': 'Fills nearby space with a configured block.',
    'AutoDeoxidizerHack': 'Scrapes oxidized copper with an axe.',
    'AutoSmithingHack': 'Automates the smithing table.',
    'AutoVaultHack': 'Opens and uses nearby vaults.',
    'ChatHighlightHack': 'Highlights or hides chat messages by keyword.',
    'ShieldEspHack': 'Highlights entities holding a shield.',
    'CrystalAuraPlusHack': 'Extended crystal aura with damage checks.',
    'PlaceRenderHack': 'Renders recently placed blocks.',
    'PrinterHack': 'Places blocks in front of you while walking.',
    'PushCrystalHack': 'Pushes and detonates end crystals.',
    'ReplenishHack': 'Refills the held stack from the inventory.',
    'AutoTorchHack': 'Places torches in dark areas.',
    'FastCrossbowHack': 'Charges and shoots crossbows quickly.',
}

ZH = {
    'AutoTrashHack': '丢弃背包中指定的物品。',
    'ElytraCollectorHack': '滑翔时自动收集掉落物。',
    'AutoAnchorHack': '自动在目标处放置并引爆重生锚。',
    'AutoArmorPlusHack': '自动穿上最好的护甲。',
    'AutoLoginHack': '进服后自动发送登录命令。',
    'AutoOffHandHack': '自动在副手保持指定物品。',
    'AutoPlaceBlockHack': '自动放置指定方块。',
    'AttackRangeIndicatorHack': '显示你的攻击距离。',
    'EntityTagsHack': '在实体上方显示名字、血量和距离。',
    'HitboxESPHack': '渲染实体碰撞箱。',
    'ItemDespawnTimerHack': '显示掉落物的消失倒计时。',
    'LavaESPHack': '高亮岩浆源方块。',
    'OreVeinESPHack': '高亮矿脉。',
    'AutoRefreshTradeHack': '自动刷新村民交易。',
    'DeathRenderHack': '标记死亡位置。',
    'ElytraGrimAccelerateHack': '适配 Grim 的鞘翅加速。',
    'FireworkElytraFlyHack': '使用烟花火箭进行鞘翅飞行。',
    'GlassFillerHack': '用指定方块填充附近空间。',
    'AutoDeoxidizerHack': '自动用斧头刮除铜锈。',
    'AutoSmithingHack': '自动使用锻造台。',
    'AutoVaultHack': '自动打开并使用附近的试炼宝库。',
    'ChatHighlightHack': '按关键词高亮或隐藏聊天消息。',
    'ShieldEspHack': '高亮举盾的实体。',
    'CrystalAuraPlusHack': '带伤害判定的增强水晶光环。',
    'PlaceRenderHack': '渲染最近放置的方块。',
    'PrinterHack': '行走时自动在身前放置方块。',
    'PushCrystalHack': '推动并引爆末影水晶。',
    'ReplenishHack': '从背包补充手中物品。',
    'AutoTorchHack': '在黑暗处自动放置火把。',
    'FastCrossbowHack': '快速装填并发射弩。',
}


def add_keys(path, mapping):
    text = path.read_text(encoding='utf-8')
    missing = [(c, k) for c, k in ((c, c[:-4].lower()) for c in CLASSES)
               if '"description.wurst.hack.%s"' % k not in text]
    if not missing:
        return 0
    lines = text.splitlines(keepends=True)
    anchor = None
    for i, line in enumerate(lines):
        if '"description.wurst.hack.antiknockback"' in line:
            anchor = i
            break
    if anchor is None:
        for i, line in enumerate(lines):
            if '"description.wurst.hack.' in line:
                anchor = i
                break
    if anchor is None:
        return -1
    if not lines[anchor].rstrip().endswith(','):
        lines[anchor] = lines[anchor].rstrip('\r\n') + ',\n'
    ins = ['"description.wurst.hack.%s": "%s",\n' % (k, mapping[c])
           for c, k in missing]
    lines[anchor + 1:anchor + 1] = ins
    new = ''.join(lines)
    json.loads(new)
    path.write_text(new, encoding='utf-8', newline='\n')
    return len(missing)


def main():
    root = REPO.resolve()
    stats = {}
    for p in project_dirs():
        if p.resolve() == root:
            continue
        base = p / 'src/main/resources/assets/wurst/translations'
        for name, table in (('en_us.json', EN), ('zh_cn.json', ZH)):
            f = base / name
            if f.exists():
                n = add_keys(f, table)
                stats[n] = stats.get(n, 0) + 1
    print('translations4', stats)


if __name__ == '__main__':
    main()
