"""Package exact upstream licenses and attribution for the implemented ports."""
import hashlib
import json
from pathlib import Path
import sys
sys.path.insert(0,str(Path(__file__).resolve().parent))
from sync_four_client_batch import ROOT, PLAN, read, write

SOURCES={
    'fo':('reference/tools/FO-src/fo','https://github.com/jialebot6666/FO','CC0-1.0',
          'AutoLog (AutoLogPlus) -> AutoLeave: height, armor durability, portal, position, health, totems, timeout/reconnect conditions. Existing Wurst health/totem gate retained.'),
    'invincible-machine-gun':('reference/tools/InvincibleMachineGun-src','https://github.com/adaxiaohu/InvincibleMachineGun','GPL-3.0',
          'AutoMessage -> AutoText/ChatSequence: UTF-8 file sequences, selection prefix, per-message delay, loop/random modes. Non-loop random runs terminate.'),
    'leaves-hack':('reference/tools/LeavesHack-src','https://github.com/MrBZBZ/LeavesHack','AGPL-3.0',
          'AutoTree -> TreeBot/SaplingFarm: selected planting sites, bone meal, delay, action cap and rendering. Native tick events replace render-driven actions; slot restored in finally; chunk/reach/world guards added.'),
    'meteor-client':('reference/upstream-sources/meteor_client_master_src/meteor-client-master','https://github.com/MeteorDevelopment/meteor-client','GPL-3.0',
          'Copyright (c) Meteor Development. Slippy, EndermanLook, BuildHeight, TridentBoost, Multitask, NoInteract -> native modules; SoundBlocker -> SoundModifier; AntiHunger -> AntiHunger; NoStatusEffects -> configurable NoLevitation; BreakDelay -> FastBreak; AutoRespawn death record -> native waypoint manager. Native Wurst events/settings/registries replace Meteor services. NoLevitation keeps its existing levitation-only default; death record stores the latest position. These are implemented portions, not a claim of complete Meteor parity.')
}

def main():
    outputs={}
    inventory=json.loads(read(PLAN/'module-matrix.json'))
    for tree in json.loads(read(PLAN/'target-inventory.json')):
        for key,(folder,url,license,detail) in SOURCES.items():
            dest=ROOT/tree['root']/'src/main/resources/META-INF/licenses'/key
            upstream=ROOT/folder/'LICENSE'
            files={dest/'UPSTREAM-LICENSE.txt':upstream.read_bytes()}
            lines=[f'Upstream: {url}',f'Local source snapshot: {folder}',f'License: {license}',
                   f'License SHA-256: {hashlib.sha256(upstream.read_bytes()).hexdigest()}',
                   'Adapted for WurstB+ Plus v1.5.1 on 2026-11-07.',detail,
                   'Original notices remain applicable. Local adaptation: WurstB+ Plus contributors.',
                   'Source snapshot module hashes:']
            classes={'fo':['AutoLog'],'invincible-machine-gun':['AutoMessage'],'leaves-hack':['AutoTree'],
                     'meteor-client':['Slippy','EndermanLook','BuildHeight','SoundBlocker','AntiHunger','NoStatusEffects','BreakDelay','AutoRespawn','TridentBoost','Multitask','NoInteract']}[key]
            lines += [f"{m['source']} SHA-256 {m['sha256']}" for m in inventory if m['source'].startswith(folder+'/') and m['class'] in classes]
            files[dest/'ATTRIBUTION.txt']=('\n'.join(lines)+'\n').encode('utf-8')
            for file,data in files.items():
                relative=file.relative_to(ROOT)
                if file.exists() and file.read_bytes()!=data:
                    backup=PLAN/'notices-before'/relative
                    if not backup.exists():backup.parent.mkdir(parents=True,exist_ok=True);backup.write_bytes(file.read_bytes())
                file.parent.mkdir(parents=True,exist_ok=True);file.write_bytes(data)
                outputs[relative.as_posix()]=hashlib.sha256(data).hexdigest()
    write(PLAN/'notice-outputs.json',json.dumps(outputs,indent=2)+'\n')
    print(f'Packaged {len(outputs)} license/attribution files across 66 v1.5.1 projects.')

if __name__=='__main__':main()
