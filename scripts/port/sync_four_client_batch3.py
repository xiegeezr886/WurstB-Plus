"""Merge Meteor behavior into existing modules; preserve each target's API adapters."""
import argparse
import hashlib
import json
from pathlib import Path
import re
import shutil
import sys
sys.path.insert(0,str(Path(__file__).resolve().parent))
from sync_four_client_batch import ROOT, PLAN, read, write
from sync_four_client_batch2 import merge

STAGE=PLAN/'batch3/src/net/wurstclient'
MERGED=['hacks/AntiHungerHack.java','hacks/NoLevitationHack.java','hacks/FastBreakHack.java','hacks/AutoRespawnHack.java','util/SoundIdFilter.java']
NEW=['util/RegistryIdFilter.java','mixin/FastBreakCooldownMixin.java']

def original(file):
    backup=PLAN/'batch3/before'/file.relative_to(ROOT)
    if not backup.exists():
        backup.parent.mkdir(parents=True,exist_ok=True)
        shutil.copyfile(file,backup)
    return read(backup)

def main():
    parser=argparse.ArgumentParser()
    parser.add_argument('--target',action='append')
    args=parser.parse_args()
    manifest=PLAN/'batch3-outputs.json'
    previous=json.loads(read(manifest)) if manifest.exists() else {}
    for tree in json.loads(read(PLAN/'target-inventory.json')):
        if args.target and tree['root'] not in args.target: continue
        root=ROOT/tree['root'];java=root/'src/main/java/net/wurstclient'
        planned={}
        for name in MERGED:
            file=java/name
            # Adapt known existing differences before comparing hunks.
            old=read(PLAN/'batch3/base'/name);new=read(STAGE/name)
            actual=original(file)
            if name.endswith('FastBreakHack.java') and 'MC.gameMode.setDestroyDelay(0)' in actual:
                old=old.replace('((net.wurstclient.mixin.MultiPlayerGameModeAccessor)(Object)MC.gameMode).setDestroyDelay(0)', 'MC.gameMode.setDestroyDelay(0)')
            if name.endswith('FastBreakHack.java') and 'MC.gameMode.destroyDelay = 0' in actual:
                old=old.replace('((net.wurstclient.mixin.MultiPlayerGameModeAccessor)(Object)MC.gameMode).setDestroyDelay(0)', 'MC.gameMode.destroyDelay = 0')
            text=merge(actual,old,new,file)
            if name.endswith('AutoRespawnHack.java'):
                if '.dimension().identifier()' in read(java/'commands/WaypointsCmd.java'):
                    text=text.replace('.dimension().location()', '.dimension().identifier()')
            planned[file]=text
        for name in NEW: planned[java/name]=read(STAGE/name)
        player=java/'mixin/LocalPlayerMixin.java'
        if not player.exists():player=java/'mixin/ClientPlayerEntityMixin.java'
        text=original(player)
        arg='effect.value()' if 'hasEffect(Holder<MobEffect> effect)' in text else 'effect'
        text,count=re.subn(r'(?:effect == MobEffects\.LEVITATION|effect\.is\(MobEffects\.LEVITATION\))\s*&& hax\.noLevitationHack\.isEnabled\(\)',f'hax.noLevitationHack.shouldBlock({arg})',text)
        assert count in (1,2),(player,count)
        # Older versions only override hasEffect; match getEffect too so all callers agree.
        if count==1:
            holder='Holder<MobEffect>' if arg=='effect.value()' else 'MobEffect'
            effect_class='net.minecraft.world.effect.MobEffectInstance'
            addition=f'''\n\t@Override
\tpublic {effect_class} getEffect({holder} effect)
\t{{
\t\tvar hax = WurstClient.INSTANCE.getHax();
\t\tif(hax != null && hax.noLevitationHack.shouldBlock({arg}))
\t\t\treturn null;
\t\treturn super.getEffect(effect);
\t}}
'''
            i=text.rfind('}')
            text=text[:i]+addition+text[i:]
        planned[player]=text
        for config in (root/'src/main/resources').glob('*.mixins.json'):
            text=original(config);data=json.loads(text.lstrip('\ufeff'))
            if data.get('package')=='net.wurstclient.mixin' and 'FastBreakCooldownMixin' not in data.get('client',[]):
                planned[config]=text.replace('"client": [','"client": [\n    "FastBreakCooldownMixin",')
        for file,text in planned.items():
            rel=file.relative_to(ROOT).as_posix()
            if rel in previous:
                assert hashlib.sha256(file.read_bytes()).hexdigest()==previous[rel],f'Concurrent edit: {file}'
            write(file,text)
            previous[rel]=hashlib.sha256(file.read_bytes()).hexdigest()
        write(manifest,json.dumps(previous,indent=2)+'\n')
        print('Batch 3 synchronized:',tree['root'])

if __name__=='__main__':main()
