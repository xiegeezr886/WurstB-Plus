# -*- coding: utf-8 -*-
"""Replicate the loader-agnostic Meteor batch-3 hacks to all 66 v1.5 projects.

Compilation is intentionally NOT run (per user instruction). The four
NeoForge-specific modules (BetterTab/BetterTooltips/Zoom/NoStatusEffects) are
left in neoforge/versions/1.21.11 only and must get per-loader variants later.
"""
import re
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).parent))
from add_upstream_hacks import REPO, project_dirs

SRC = {
    'fabric/versions/1.21.11': ['AutoNametagHack', 'AutoShearerHack',
                                'AutoBrewerHack', 'EChestFarmerHack',
                                'FlamethrowerHack', 'LiquidFillerHack'],
    'versions/1.21.11': ['HighwayBuilderHack', 'InfinityMinerHack',
                         'NoGhostBlocksHack', 'PacketMineHack',
                         'SpawnProoferHack', 'StashFinderHack'],
    'neoforge/versions/1.21.11': ['EntityOwnerHack', 'FakePlayerHack',
                                  'NoRotateHack'],
    'fabric/versions/1.21.10': ['BetterBeaconsHack', 'BookBotHack',
                                'InventoryTweaksHack', 'MessageAuraHack',
                                'NotifierHack'],
}

FIELDS = {
    'AutoNametagHack': 'autoNametagHack',
    'AutoShearerHack': 'autoShearerHack',
    'AutoBrewerHack': 'autoBrewerHack',
    'EChestFarmerHack': 'eChestFarmerHack',
    'FlamethrowerHack': 'flamethrowerHack',
    'LiquidFillerHack': 'liquidFillerHack',
    'HighwayBuilderHack': 'highwayBuilderHack',
    'InfinityMinerHack': 'infinityMinerHack',
    'NoGhostBlocksHack': 'noGhostBlocksHack',
    'PacketMineHack': 'packetMineHack',
    'SpawnProoferHack': 'spawnProoferHack',
    'StashFinderHack': 'stashFinderHack',
    'EntityOwnerHack': 'entityOwnerHack',
    'FakePlayerHack': 'fakePlayerHack',
    'NoRotateHack': 'noRotateHack',
    'BetterBeaconsHack': 'betterBeaconsHack',
    'BookBotHack': 'bookBotHack',
    'InventoryTweaksHack': 'inventoryTweaksHack',
    'MessageAuraHack': 'messageAuraHack',
    'NotifierHack': 'notifierHack',
}


def main():
    root = REPO.resolve()
    templates = {}
    for rel, names in SRC.items():
        d = REPO / rel / 'src/main/java/net/wurstclient/hacks'
        for n in names:
            templates[n] = (d / (n + '.java')).read_text(encoding='utf-8')

    stats = {}
    for p in project_dirs():
        if p.resolve() == root:
            continue
        hack_dir = p / 'src/main/java/net/wurstclient/hacks'
        list_path = p / 'src/main/java/net/wurstclient/hack/HackList.java'
        text = list_path.read_text(encoding='utf-8')
        for cls, field in FIELDS.items():
            (hack_dir / (cls + '.java')).write_text(
                templates[cls], encoding='utf-8', newline='\n')
            if field in text:
                stats['exists'] = stats.get('exists', 0) + 1
                continue
            m = re.search(
                r'^([ \t]*)public final \w+ autoSmelterHack\s*=',
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
    print('batch3', stats)


if __name__ == '__main__':
    main()
