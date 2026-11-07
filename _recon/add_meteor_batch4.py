# -*- coding: utf-8 -*-
"""Replicate FO/IMG/Leaves batch-4 hacks to all 66 v1.5 projects (source only)."""
import re
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).parent))
from add_upstream_hacks import REPO, project_dirs

SRC = {
    'fabric/versions/1.21.11': [
        'AutoTrashHack', 'ElytraCollectorHack', 'AutoAnchorHack',
        'AutoArmorPlusHack', 'AutoLoginHack', 'AutoOffHandHack',
        'AutoPlaceBlockHack'],
    'versions/1.21.11': [
        'AttackRangeIndicatorHack', 'EntityTagsHack', 'HitboxESPHack',
        'ItemDespawnTimerHack', 'LavaESPHack', 'OreVeinESPHack',
        'AutoRefreshTradeHack', 'DeathRenderHack',
        'ElytraGrimAccelerateHack', 'FireworkElytraFlyHack',
        'GlassFillerHack'],
    'neoforge/versions/1.21.11': [
        'AutoDeoxidizerHack', 'AutoSmithingHack', 'AutoVaultHack',
        'ChatHighlightHack', 'ShieldEspHack', 'CrystalAuraPlusHack',
        'PlaceRenderHack', 'PrinterHack', 'PushCrystalHack',
        'ReplenishHack'],
    'fabric/versions/1.21.10': ['AutoTorchHack', 'FastCrossbowHack'],
}

FIELDS = {
    'AutoTrashHack': 'autoTrashHack',
    'ElytraCollectorHack': 'elytraCollectorHack',
    'AutoAnchorHack': 'autoAnchorHack',
    'AutoArmorPlusHack': 'autoArmorPlusHack',
    'AutoLoginHack': 'autoLoginHack',
    'AutoOffHandHack': 'autoOffHandHack',
    'AutoPlaceBlockHack': 'autoPlaceBlockHack',
    'AttackRangeIndicatorHack': 'attackRangeIndicatorHack',
    'EntityTagsHack': 'entityTagsHack',
    'HitboxESPHack': 'hitboxEspHack',
    'ItemDespawnTimerHack': 'itemDespawnTimerHack',
    'LavaESPHack': 'lavaEspHack',
    'OreVeinESPHack': 'oreVeinEspHack',
    'AutoRefreshTradeHack': 'autoRefreshTradeHack',
    'DeathRenderHack': 'deathRenderHack',
    'ElytraGrimAccelerateHack': 'elytraGrimAccelerateHack',
    'FireworkElytraFlyHack': 'fireworkElytraFlyHack',
    'GlassFillerHack': 'glassFillerHack',
    'AutoDeoxidizerHack': 'autoDeoxidizerHack',
    'AutoSmithingHack': 'autoSmithingHack',
    'AutoVaultHack': 'autoVaultHack',
    'ChatHighlightHack': 'chatHighlightHack',
    'ShieldEspHack': 'shieldEspHack',
    'CrystalAuraPlusHack': 'crystalAuraPlusHack',
    'PlaceRenderHack': 'placeRenderHack',
    'PrinterHack': 'printerHack',
    'PushCrystalHack': 'pushCrystalHack',
    'ReplenishHack': 'replenishHack',
    'AutoTorchHack': 'autoTorchHack',
    'FastCrossbowHack': 'fastCrossbowHack',
}


def main():
    root = REPO.resolve()
    templates = {}
    for rel, names in SRC.items():
        d = REPO / rel / 'src/main/java/net/wurstclient/hacks'
        for n in names:
            p = d / (n + '.java')
            if not p.exists():
                print('MISSING TEMPLATE', p)
                continue
            templates[n] = p.read_text(encoding='utf-8')

    stats = {}
    for p in project_dirs():
        if p.resolve() == root:
            continue
        hack_dir = p / 'src/main/java/net/wurstclient/hacks'
        list_path = p / 'src/main/java/net/wurstclient/hack/HackList.java'
        text = list_path.read_text(encoding='utf-8')
        for cls, field in FIELDS.items():
            if cls not in templates:
                continue
            (hack_dir / (cls + '.java')).write_text(
                templates[cls], encoding='utf-8', newline='\n')
            if field in text:
                stats['exists'] = stats.get('exists', 0) + 1
                continue
            m = re.search(
                r'^([ \t]*)public final \w+ notifierHack\s*=',
                text, re.M)
            if not m:
                stats['NO ANCHOR'] = stats.get('NO ANCHOR', 0) + 1
                continue
            end = text.find(';', m.end())
            eol = text.find('\n', end) if end != -1 else -1
            if end == -1 or eol == -1:
                stats['NO ANCHOR'] = stats.get('NO ANCHOR', 0) + 1
                continue
            line = '\tpublic final %s %s = new %s();' % (cls, field, cls)
            text = text[:eol + 1] + line + '\n' + text[eol + 1:]
            stats['added'] = stats.get('added', 0) + 1
        list_path.write_text(text, encoding='utf-8', newline='\n')
    print('batch4', stats)


if __name__ == '__main__':
    main()
