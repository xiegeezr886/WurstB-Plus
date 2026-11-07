# -*- coding: utf-8 -*-
"""Replicate IMG/FO batch-5 hacks to all 66 v1.5 projects (source only)."""
import re
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).parent))
from add_upstream_hacks import REPO, project_dirs

SRC = {
    'fabric/versions/1.21.11': [
        'AttractAuraHack', 'AutoChestAuraHack', 'CrossbowAuraHack',
        'CrystalMacroHack', 'MaceAuraHack', 'MaceBreakerProHack',
        'MacroAnchorHack', 'PistonCrystalHack', 'SpearKillHack',
        'SwordGapHack'],
    'versions/1.21.11': [
        'TntBomberHack', 'TpAnchorHack', 'TpBowAuraHack', 'TpMachineGunHack',
        'PearlPhaseHack', 'BacktrackHack', 'StuckHack', 'PortalGodModeHack',
        'LegitNoFallHack', 'FreezeHack'],
    'neoforge/versions/1.21.11': [
        'AutoChorusHack', 'AutoDoubleHandHack', 'AutoFireworkHack',
        'AutoInvTotemHack', 'VillagerTraderHack', 'AdaManualCrystalHack',
        'AdaAutoHotbarHack', 'FurnaceUncloggerHack', 'SchematicProHack',
        'RTsearchHack', 'AutoMineSandHack', 'FoKillAuraHack'],
    'fabric/versions/1.21.10': [
        'ChatFilterHack', 'ChatHiderHack', 'ChatPrefixCustomHack',
        'FeedbackBlockerHack', 'GAntiExplosionHack', 'HexChatHack',
        'AutoServerHack', 'AutoTPAcceptHack', 'AutoNodHack', 'AutoKouZiHack',
        'XCarryHack'],
    'fabric/versions/1.21.9': [
        'AmbienceHack', 'AntiAntiXrayHack', 'AntiLagHack', 'BreakEspHack',
        'CustomFovHack', 'CustomItemEspHack', 'ElytraFollowerHack',
        'FillEspHack', 'ImgChamsHack', 'MineEspHack', 'MobHudHack'],
    'neoforge/versions/1.21.10': [
        'ImgTipsHack', 'ImgTotemParticleHack', 'ImgWorldStatsHack',
        'KillFxHack', 'KnockbackDirectionHack', 'SprintStatusHack',
        'SonarBypassHack', 'ScreenActionsHack', 'FlightAntiKickHack'],
}


def main():
    root = REPO.resolve()
    templates = {}
    fields = {}
    for rel, names in SRC.items():
        d = REPO / rel / 'src/main/java/net/wurstclient/hacks'
        list_path = REPO / rel / 'src/main/java/net/wurstclient/hack/HackList.java'
        text = list_path.read_text(encoding='utf-8')
        for n in names:
            p = d / (n + '.java')
            if not p.exists():
                print('MISSING TEMPLATE', p)
                continue
            templates[n] = p.read_text(encoding='utf-8')
            m = re.search(r'public final ' + n + r'\s+(\w+)\s*=', text)
            if not m:
                print('MISSING FIELD', rel, n)
                continue
            fields[n] = m.group(1)

    stats = {}
    for p in project_dirs():
        if p.resolve() == root:
            continue
        hack_dir = p / 'src/main/java/net/wurstclient/hacks'
        list_path = p / 'src/main/java/net/wurstclient/hack/HackList.java'
        text = list_path.read_text(encoding='utf-8')
        for cls, field in fields.items():
            (hack_dir / (cls + '.java')).write_text(
                templates[cls], encoding='utf-8', newline='\n')
            if field in text:
                stats['exists'] = stats.get('exists', 0) + 1
                continue
            m = re.search(
                r'^([ \t]*)public final \w+ notifierHack\s*=', text, re.M)
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
    print('batch5', stats, 'modules=', len(fields))


if __name__ == '__main__':
    main()
