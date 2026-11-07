"""Merge Leaves behavior while retaining version-specific inventory and render APIs."""
import argparse,hashlib,json,shutil,sys
from pathlib import Path
sys.path.insert(0,str(Path(__file__).resolve().parent))
from sync_four_client_batch import ROOT,PLAN,read,write
from sync_four_client_batch2 import merge
STAGE=PLAN/'batch5/src/net/wurstclient'
MERGED=['hacks/AutoPlaceBlockHack.java','hacks/AutoTorchHack.java','hacks/KillFxHack.java','hacks/AutoSprintHack.java','hacks/treebot/SaplingFarm.java']

def main():
    parser=argparse.ArgumentParser();parser.add_argument('--target',action='append');args=parser.parse_args()
    manifest=PLAN/'batch5-outputs.json';previous=json.loads(read(manifest)) if manifest.exists() else {}
    for tree in json.loads(read(PLAN/'target-inventory.json')):
        if args.target and tree['root'] not in args.target:continue
        java=ROOT/tree['root']/'src/main/java/net/wurstclient';planned={}
        modern_slots='inventory.setSelectedSlot(' in read(java/'util/InventoryUtils.java')
        def adapt(text):
            if modern_slots:
                import re
                text=re.sub(r'MC\.player\.getInventory\(\)\.selected = (.*?);',r'MC.player.getInventory().setSelectedSlot(\1);',text)
                text=text.replace('MC.player.getInventory().selected','MC.player.getInventory().getSelectedSlot()')
            if tree['mc'] in ['26.2','26.3']:text=text.replace('MC.screen','MC.gui.screen()')
            return text
        for name in MERGED:
            file=java/name;backup=PLAN/'batch5/before'/file.relative_to(ROOT)
            if not backup.exists():backup.parent.mkdir(parents=True,exist_ok=True);shutil.copyfile(file,backup)
            old=adapt(read(PLAN/'batch5/base'/name));new=adapt(read(STAGE/name))
            for slot_name in ('oldSlot','slot'):
                assignment=f'MC.player.getInventory().selected = {slot_name};'
                wrapped=f'MC.player.getInventory().selected = ({slot_name});'
                if wrapped in read(backup):old=old.replace(assignment,wrapped)
            if name.endswith('AutoSprintHack.java') and 'import net.wurstclient.util.MovementPlanner;' in read(backup):
                anchor='import net.wurstclient.settings.CheckboxSetting;'
                extra='\nimport net.wurstclient.util.MovementPlanner;'
                old=old.replace(anchor,anchor+extra);new=new.replace(anchor,anchor+extra)
            if name.endswith('AutoSprintHack.java') and 'player.isInWaterOrBubble()' not in read(backup):
                old=old.replace('player.isInWaterOrBubble()', 'player.isInWater()')
                new=new.replace('player.isInWaterOrBubble()', 'player.isInWater()')
            if name.endswith('AutoSprintHack.java') and 'player.isInFluidType()' in read(backup):
                old=old.replace('|| player.isInWater() || player.isUnderWater()', '|| player.isInWater() || player.isInFluidType()\n\t\t\t|| player.isUnderWater()')
                new=new.replace('player.isInWater() || player.isUnderWater()', 'player.isInWater() || player.isInFluidType() || player.isUnderWater()')
            planned[file]=merge(read(backup),old,new,file)
        for src in (STAGE/'hacks/autoplace').glob('*.java'):planned[java/'hacks/autoplace'/src.name]=adapt(read(src))
        for file,text in planned.items():
            rel=file.relative_to(ROOT).as_posix()
            if rel in previous:assert hashlib.sha256(file.read_bytes()).hexdigest()==previous[rel],f'Concurrent edit: {file}'
            write(file,text);previous[rel]=hashlib.sha256(file.read_bytes()).hexdigest()
        write(manifest,json.dumps(previous,indent=2)+'\n');print('Batch 5 synchronized:',tree['root'])

if __name__=='__main__':main()
