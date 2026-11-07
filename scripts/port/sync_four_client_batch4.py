"""Register native TridentBoost, Multitask and NoInteract with checked version hooks."""
import argparse
import hashlib
import json
from pathlib import Path
import shutil
import sys
sys.path.insert(0,str(Path(__file__).resolve().parent))
from sync_four_client_batch import ROOT, PLAN, read, write

STAGE=PLAN/'batch4/src/net/wurstclient'
MODULES={
    'TridentBoost':('三叉戟推进','调整激流推进倍率，支持离水激流。'),
    'Multitask':('多任务交互','允许同时使用物品和挖掘，可选择同时攻击实体。'),
    'NoInteract':('交互过滤','按方块、实体、手和好友等条件阻止挖掘、攻击或交互。')
}

def original(file):
    backup=PLAN/'batch4/before'/file.relative_to(ROOT)
    if not backup.exists():
        backup.parent.mkdir(parents=True,exist_ok=True);shutil.copyfile(file,backup)
    return read(backup)

def main():
    parser=argparse.ArgumentParser();parser.add_argument('--target',action='append');args=parser.parse_args()
    manifest=PLAN/'batch4-outputs.json'
    previous=json.loads(read(manifest)) if manifest.exists() else {}
    for tree in json.loads(read(PLAN/'target-inventory.json')):
        if args.target and tree['root'] not in args.target:continue
        root=ROOT/tree['root'];java=root/'src/main/java/net/wurstclient';planned={}
        for src in STAGE.rglob('*.java'):
            text=read(src)
            if src.name=='NoInteractMixin.java':
                api=read(PLAN/('batch3-api-'+tree['mc']+'.txt'))
                if 'public net.minecraft.world.InteractionResult interactAt(' not in api:
                    start=text.index('\n\t@Inject(method = "interactAt"')
                    text=text[:start]+'\n}\n'
                    text=text.replace('wurstEntityUse(Player player, Entity target, InteractionHand hand,',
                                      'wurstEntityUse(Player player, Entity target, EntityHitResult hit, InteractionHand hand,')
            planned[java/src.relative_to(STAGE)]=text
        file=java/'hack/HackList.java';text=original(file)
        needle='public final class HackList'
        # Add beside the already-registered native batch1 modules.
        anchor='\tpublic final SlippyHack slippyHack = new SlippyHack();'
        assert text.count(anchor)==1,file
        fields='\n'.join(f'\tpublic final {name}Hack {name[0].lower()+name[1:]}Hack = new {name}Hack();' for name in MODULES)
        planned[file]=text.replace(anchor,anchor+'\n'+fields)
        for config in (root/'src/main/resources').glob('*.mixins.json'):
            text=original(config);data=json.loads(text.lstrip('\ufeff'))
            if data.get('package')=='net.wurstclient.mixin':
                for name in MODULES:
                    mixin='TridentBoostMixin' if name=='TridentBoost' else name+'Mixin'
                    if mixin not in data.get('client',[]):text=text.replace('"client": [','"client": [\n    "'+mixin+'",')
                planned[config]=text
        lang=root/'src/main/resources/assets/wurst/translations/zh_cn.json'
        text=original(lang)
        lines=[]
        for name,(label,description) in MODULES.items():
            for key,value in [('hack.name.'+name.lower(),label),('description.wurst.hack.'+name.lower(),description)]:
                if '"'+key+'"' not in text:lines.append('  '+json.dumps(key)+': '+json.dumps(value,ensure_ascii=False)+',')
        if lines:planned[lang]=text.replace('{','{\n'+'\n'.join(lines),1)
        for file,text in planned.items():
            rel=file.relative_to(ROOT).as_posix()
            if rel in previous:assert hashlib.sha256(file.read_bytes()).hexdigest()==previous[rel],f'Concurrent edit: {file}'
            write(file,text);previous[rel]=hashlib.sha256(file.read_bytes()).hexdigest()
        write(manifest,json.dumps(previous,indent=2)+'\n')
        print('Batch 4 synchronized:',tree['root'])

if __name__=='__main__':main()
