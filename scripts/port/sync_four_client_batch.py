"""Synchronize the reviewed native batch, preserving unrelated local edits."""
from pathlib import Path
import argparse
import hashlib
import json
import shutil

ROOT = Path(__file__).resolve().parents[2]
PLAN = ROOT / '.planning/2026-11-07-four-client-v151-port'
BASE = ROOT / 'fabric/src/main/java/net/wurstclient'
NEW = ['hacks/SlippyHack.java', 'hacks/EndermanLookHack.java',
       'hacks/BuildHeightHack.java', 'util/SoundIdFilter.java', 'mixin/SoundEngineMixin.java']

def read(path):
    return path.read_text(encoding='utf-8')

def write(path, content):
    raw = content.encode('utf-8')
    if path.exists() and read(path) == content:
        return
    if path.exists():
        backup = PLAN / 'before' / path.relative_to(ROOT)
        if not backup.exists():
            backup.parent.mkdir(parents=True, exist_ok=True)
            shutil.copy2(path, backup)
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_bytes(raw)

def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--target', action='append', help='Default: all 66 v1.5.1 projects')
    args = parser.parse_args()
    targets = json.loads(read(PLAN / 'target-inventory.json'))
    api = {r['mc']: r for r in json.loads(read(PLAN / 'minecraft-api.json'))}
    registry_addition = ''.join(f'\tpublic final {name}Hack {name[0].lower() + name[1:]}Hack = new {name}Hack();\n'
                               for name in ['Slippy', 'EndermanLook', 'BuildHeight'])
    block_addition = read(BASE / 'mixin/BlockMixin.java').split('{\n', 1)[1].split('\n\t/**', 1)[0]
    original_sound = read(PLAN / 'before/fabric/src/main/java/net/wurstclient/hacks/SoundModifierHack.java')
    sound = read(BASE / 'hacks/SoundModifierHack.java')
    assert 'wurstSlippy' in block_addition and 'shouldBlock' in sound
    outputs = {}
    for tree in targets:
        if args.target and tree['root'] not in args.target:
            continue
        target = ROOT / tree['root']
        java = target / 'src/main/java/net/wurstclient'
        version = api[tree['mc']]
        for name in NEW:
            content = read(BASE / name)
            if name.endswith('EndermanLookHack.java'):
                if tree['mc'] == '26.3':
                    content = content.replace('EnderMan', 'Enderman')
                if version['gaze_tag']:
                    content = content.replace('import net.minecraft.world.item.Items;', 'import net.minecraft.tags.ItemTags;')
                    content = content.replace('Items.CARVED_PUMPKIN', 'ItemTags.GAZE_DISGUISE_EQUIPMENT')
            elif name.endswith('BuildHeightHack.java'):
                content = content.replace('getMinBuildHeight()', version['min_y'])
                api_text = read(PLAN / f"api-{tree['mc']}.txt")
                if ' getHitResult();' not in api_text:
                    for old, new in [('getHitResult()', 'hitResult()'), ('getHand()', 'hand()'), ('getSequence()', 'sequence()')]:
                        content = content.replace(old, new)
            elif name.endswith('SoundEngineMixin.java'):
                content = content.replace('getLocation()', version['sound_id'])
                if 'PlayResult' in version['sound_play'][0]:
                    content = content.replace('callback.CallbackInfo;', 'callback.CallbackInfoReturnable;')
                    content = content.replace('SoundInstance;)V', 'SoundInstance;)Lnet/minecraft/client/sounds/SoundEngine$PlayResult;')
                    content = content.replace('CallbackInfo ci', 'CallbackInfoReturnable<SoundEngine.PlayResult> ci')
                    content = content.replace('ci.cancel();', 'ci.setReturnValue(SoundEngine.PlayResult.NOT_STARTED);')
            write(java / name, content)
        registry = java / 'hack/HackList.java'
        text = read(registry)
        if 'public final SlippyHack ' not in text:
            marker = 'public final class HackList implements UpdateListener\n{\n'
            assert text.count(marker) == 1, registry
            write(registry, text.replace(marker, marker + registry_addition))
        mixin = java / 'mixin/BlockMixin.java'
        text = read(mixin)
        if 'wurstSlippy' not in text:
            marker = 'public abstract class BlockMixin implements ItemLike\n{\n'
            assert text.count(marker) == 1, mixin
            write(mixin, text.replace(marker, marker + block_addition + '\n'))
        sound_file = java / 'hacks/SoundModifierHack.java'
        assert read(sound_file) in [original_sound, sound], sound_file
        write(sound_file, sound)
        configs = []
        for config in (target / 'src/main/resources').glob('*.mixins.json'):
            config_text = read(config)
            data = json.loads(config_text.lstrip('\ufeff'))
            if data.get('package') != 'net.wurstclient.mixin':
                continue
            configs.append(config)
            if 'SoundEngineMixin' not in data.get('client', []):
                assert config_text.count('"client": [') == 1, config
                write(config, config_text.replace('"client": [', '"client": [\n    "SoundEngineMixin",'))
        assert configs, tree
        translations = target / 'src/main/resources/assets/wurst/translations/zh_cn.json'
        text = read(translations)
        additions = {
            'hack.name.slippy': '方块滑行',
            'description.wurst.hack.slippy': '设置方块摩擦系数，支持生效名单和排除名单。',
            'hack.name.endermanlook': '末影人视线',
            'description.wurst.hack.endermanlook': '避开末影人视线，或注视末影人使其停下。',
            'hack.name.buildheight': '高度上限交互',
            'description.wurst.hack.buildheight': '调整世界高度上限处的交互面，不会提高服务器建筑高度限制。',
        }
        existing = json.loads(text.lstrip('\ufeff'))
        additions = {k: v for k, v in additions.items() if k not in existing}
        if additions:
            prefix = ''.join('  ' + json.dumps(k) + ': ' + json.dumps(v, ensure_ascii=False) + ',\n'
                             for k, v in additions.items())
            write(translations, text.replace('{', '{\n' + prefix, 1))
        files = [java / n for n in NEW] + [registry, mixin, sound_file, translations, *configs]
        outputs.update({f.relative_to(ROOT).as_posix(): hashlib.sha256(f.read_bytes()).hexdigest() for f in files})
    manifest = PLAN / 'batch1-outputs.json'
    old = json.loads(read(manifest)) if manifest.exists() else {}
    old.update(outputs)
    write(manifest, json.dumps(old, indent=2) + '\n')
    print(f'Batch 1 synchronized; {len(outputs)} files checked, original files backed up before modification.')

if __name__ == '__main__':
    main()
