"""Rebuild only a verified incomplete NeoGradle game artifact, retaining all source files."""
from pathlib import Path
import argparse,json,os,subprocess,zipfile
ROOT=Path(__file__).resolve().parents[2]
PLAN=ROOT/'.planning/2026-11-07-four-client-v151-port'

def main():
    parser=argparse.ArgumentParser();parser.add_argument('--target',required=True);args=parser.parse_args()
    tree=next(r for r in json.loads((PLAN/'target-inventory.json').read_text(encoding='utf-8')) if r['root']==args.target)
    assert tree['root'].startswith('neoforge/versions/1.20.')
    root=ROOT/tree['root'];jars=[p for p in (root/'.gradle/repositories/ng_dummy_ng/net/neoforged/neoforge').glob('*/*.jar') if not p.name.endswith('-sources.jar')]
    assert len(jars)==1,jars
    with zipfile.ZipFile(jars[0]) as z: complete='net/minecraft/client/Minecraft.class' in z.namelist()
    if complete:
        print('Game classes already present:',args.target);return
    env=os.environ.copy();jdk=Path('C:/Program Files/Eclipse Adoptium/jdk-17.0.20.101-hotspot') if tree['mc'] in ['1.20.2','1.20.3','1.20.4'] else Path.home()/'.jdks/jdk21.0.12_12'
    env['JAVA_HOME']=str(jdk)
    log=PLAN/(tree['root'].replace('/','-')+'-repair.log')
    command=[str(root/'gradlew.bat'),'--no-build-cache','--no-daemon','--console=plain','--max-workers=2',
             '-Pnet.neoforged.gradle.caching.enabled=false','-I',str(PLAN/'rebuild-neoform.gradle'),'compileJava']
    print('Rebuilding missing game classes:',args.target,flush=True)
    with log.open('wb') as out:run=subprocess.run(command,cwd=root,env=env,stdout=out,stderr=subprocess.STDOUT)
    with zipfile.ZipFile(jars[0]) as z: complete='net/minecraft/client/Minecraft.class' in z.namelist()
    print('exit=',run.returncode,'game_classes=',complete,'log=',log)
    assert run.returncode==0 and complete

if __name__=='__main__':main()
