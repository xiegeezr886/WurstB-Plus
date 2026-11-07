"""Inventory source module inheritance/registration and native port candidates.

Name matches are candidates, never evidence of behavioral equivalence.
This tool only writes audit artifacts under the selected task directory.
"""
from pathlib import Path
import hashlib
import json
import re

ROOT = Path(__file__).resolve().parents[2]
OUT = ROOT / '.planning/2026-11-07-four-client-v151-port'
SOURCES = {
    'FO': 'reference/tools/FO-src/fo/src/main/java',
    'IMG': 'reference/tools/InvincibleMachineGun-src/src/main/java',
    'Leaves': 'reference/tools/LeavesHack-src/src/main/java',
    'Meteor': 'reference/upstream-sources/meteor_client_master_src/meteor-client-master/src/main/java',
}
ALIASES = {
    'FO:AutoLog': ['AutoLeave'], 'FO:AutoMining': ['BaritoneMine'],
    'FO:AutoTree': ['TreeBot', 'BonemealAura'],
    'FO:FOAncientCitySearch': ['AncientCitySearch'],
    'Leaves:Aura': ['Killaura'], 'Leaves:AutoTree': ['TreeBot', 'BonemealAura'],
    'Leaves:Follower': ['Follow'], 'Leaves:ScaffoldPlus': ['ScaffoldWalk'],
    'Leaves:SprintPlus': ['AutoSprint'], 'Leaves:NukerPlus': ['Nuker'],
    'IMG:InfiniteChat': ['InfiniChat'], 'IMG:AutoMessage': ['AutoText'],
    'IMG:CameraClip': ['CameraNoClip'], 'IMG:AutoCrystal': ['CrystalAura'],
    'IMG:Follower': ['Follow'], 'IMG:MusicPlayer': [],
    'IMG:ScaffoldPlus': ['ScaffoldWalk'], 'IMG:ScaffoldPlusLeaves': ['ScaffoldWalk'],
    'IMG:PrinterLeaves': ['Printer'], 'IMG:AdvancedCriticals': ['Criticals'],
    'IMG:GrimCriticals': ['Criticals'], 'IMG:AdvancedFakePlayer': ['FakePlayer'],
    'IMG:IMGFakePlayer': ['FakePlayer'], 'IMG:IMGPopChams': ['PopChams'],
    'IMG:adaAttributeSwap': ['AutoWeapon'], 'IMG:AlienSprint': ['AutoSprint'],
    'IMG:BMWSprint': ['AutoSprint'], 'IMG:NoFallimg': ['NoFall'],
    'IMG:LegitNoFallLeaves': ['LegitNoFall'], 'IMG:VelocityAlien': ['AntiKnockback'],
    'IMG:ElytraFlyPlus': ['ElytraFly'], 'IMG:PacketMinePlus': ['PacketMine'],
    'IMG:AdaPacketMine': ['PacketMine'], 'IMG:AlienV4PacketMine': ['PacketMine'],
    'IMG:xhPacketMinePlus': ['PacketMine'], 'IMG:XTpaura': ['TpAura'],
    'IMG:RoundRotation': ['Rotation'], 'IMG:CrosshairHud': ['Crosshair'],
    'Meteor:AutoLog': ['AutoLeave'], 'Meteor:AttributeSwap': ['AutoWeapon'],
    'Meteor:ServerSpoof': ['ClientSpoofer'], 'Meteor:GUIMove': ['InvWalk'],
    'Meteor:NoSlow': ['NoSlowdown'], 'Meteor:Scaffold': ['ScaffoldWalk'],
    'Meteor:Sprint': ['AutoSprint'], 'Meteor:Velocity': ['AntiKnockback'],
    'Meteor:BreakDelay': ['DelayRemover'], 'Meteor:LiquidInteract': ['Liquids'],
    'Meteor:Portals': ['PortalGui'], 'Meteor:CameraTweaks': ['CameraDistance', 'CameraNoClip'],
    'Meteor:ESP': ['PlayerEsp', 'MobEsp'], 'Meteor:StorageESP': ['ChestEsp'],
    'Meteor:Speed': ['SpeedHack'], 'Meteor:HandView': ['CustomHeldItems'],
    'Meteor:SoundBlocker': ['SoundModifier'], 'Meteor:AutoWasp': ['ElytraFollower'],
}

def without_comments(text):
    # Keep quoted strings so URLs and regexes do not become fake comments.
    return re.sub(r'"(?:\\.|[^"\\])*"|\'(?:\\.|[^\'\\])*\'|/\*[\s\S]*?\*/|//[^\n]*',
                  lambda m: ' ' if m[0].startswith(('/', '/*')) else m[0], text)

def main():
    modules = []
    native = {p.stem.removesuffix('Hack').lower(): p
              for p in (ROOT / 'fabric/src/main/java/net/wurstclient/hacks').glob('*.java')}
    for client, relative in SOURCES.items():
        classes = {}
        registrations = []
        for path in (ROOT / relative).rglob('*.java'):
            text = path.read_text(encoding='utf-8-sig')
            code = without_comments(text)
            package = re.search(r'\bpackage\s+([\w.]+)\s*;', code)
            declaration = re.search(r'\bpublic\s+((?:(?:abstract|final|sealed)\s+)*)class\s+(\w+)(?:\s+extends\s+([\w.]+))?', code)
            if not package or not declaration:
                continue
            name = package[1] + '.' + declaration[2]
            imports = re.findall(r'\bimport\s+([\w.*]+)\s*;', code)
            classes[name] = {'client': client, 'class': declaration[2], 'qualified_class': name,
                'parent': declaration[3], 'abstract': 'abstract' in declaration[1],
                'source': path.relative_to(ROOT).as_posix(), 'imports': imports,
                'package': package[1], 'code': code, 'lines': len(text.splitlines()),
                'sha256': hashlib.sha256(path.read_bytes()).hexdigest()}
            for match in re.finditer(r'\badd\(new\s+([\w.]+)\s*\(', code):
                registrations.append((name, match[1]))

        def resolve(owner, name):
            if name in classes:
                return name
            local = owner['package'] + '.' + name
            if local in classes:
                return local
            for imp in owner['imports']:
                candidate = imp[:-1] + name if imp.endswith('.*') else imp
                if candidate.rsplit('.', 1)[-1] == name and candidate in classes:
                    return candidate
            return None

        def is_module(name, seen=None):
            seen = set() if seen is None else seen
            if name in seen:
                return False
            seen.add(name)
            row = classes[name]
            parent = row['parent']
            if parent == 'meteordevelopment.meteorclient.systems.modules.Module':
                return True
            if parent == 'Module' and ('meteordevelopment.meteorclient.systems.modules.Module' in row['imports']
                                      or row['package'] == 'meteordevelopment.meteorclient.systems.modules'):
                return True
            resolved = resolve(row, parent) if parent else None
            return resolved is not None and is_module(resolved, seen)

        registered = {}
        for owner, name in registrations:
            resolved = resolve(classes[owner], name)
            if resolved:
                registered.setdefault(resolved, []).append(classes[owner]['source'])
        for name, row in classes.items():
            if row['abstract'] or not is_module(name):
                continue
            row = dict(row)
            key = client + ':' + row['class']
            candidates = ALIASES.get(key, [row['class']])
            matched = [native[c.lower()].relative_to(ROOT).as_posix()
                       for c in candidates if c.lower() in native]
            row['registered_in'] = sorted(set(registered.get(name, [])))
            row['native_candidates'] = matched
            row['status'] = 'candidate-needs-behavior-review' if matched else 'needs-mapping-or-implementation'
            row['settings'] = re.findall(r'\.name\("([^"]+)"\)', row['code'])
            row['event_types'] = re.findall(r'@EventHandler(?:\([^)]*\))?\s+(?:private|public|protected)?\s*void\s+\w+\(([^)]*)\)', row['code'])
            for internal in ['code', 'imports', 'package', 'abstract']:
                row.pop(internal)
            modules.append(row)
        count = sum(m['client'] == client for m in modules)
        active = sum(m['client'] == client and bool(m['registered_in']) for m in modules)
        print(f'{client}: {count} concrete module classes, {active} with add(new ...) registration')
    modules.sort(key=lambda m: (m['client'], m['qualified_class']))
    (OUT / 'module-matrix.json').write_text(json.dumps(modules, ensure_ascii=False, indent=2), encoding='utf-8')
    print(f'Total {len(modules)}; none marked behavior-complete by name matching.')

if __name__ == '__main__':
    main()
