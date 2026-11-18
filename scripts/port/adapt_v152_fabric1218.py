"""Patch-version port within the 1.21.6-8 deferred GUI API family."""
from pathlib import Path
import json
import re
import shutil

JAVA = 'src/main/java/net/wurstclient/'


def conflicts(rel, text, theirs):
    if rel.removeprefix(JAVA) in {'gui/title/WurstTitleButton.java',
        'altmanager/screens/AltManagerScreen.java', 'mixin/LevelRendererMixin.java'}:
        return theirs
    if rel == JAVA+'WurstClient.java':
        return re.sub(r'<<<<<<<[^\n]*\n(.*?)=======\n(.*?)>>>>>>>[^\n]*\n?',lambda m:m[2],text,flags=re.S).encode()
    return None


def configure(project: Path, donor: Path):
    from adapt_v152_fabric1216 import snapshot_layers
    snapshot_layers(project)
    import sync_v152_fabric1204 as build
    previous = build.TARGET
    try:
        build.TARGET = project
        build.configure_build()
    finally:
        build.TARGET = previous
    from host_netty import configure as netty
    netty(project)
    path = project/'build.gradle'
    text = path.read_text('utf-8-sig')
    text = re.sub(r'seedcrackerX:[^"\s]+', 'seedcrackerX:2.15.4', text)
    text = text.replace('include: ["*.jar"]', 'include: ["cloth-config*.jar"]')
    if 'include: ["mc_*.jar", "latticg-*.jar"]' not in text:
        text += '\ndependencies { runtimeOnly fileTree(dir: "libs/seedcrackerx-deps", include: ["mc_*.jar", "latticg-*.jar"]) }\n'
    path.write_text(text, encoding='utf-8')
    for source in (donor/'libs').glob('seedcracker*.jar'):
        dest = project/'libs'/source.name
        dest.parent.mkdir(parents=True, exist_ok=True)
        shutil.copy2(source, dest)
    shutil.copytree(donor/'libs/seedcrackerx-deps', project/'libs/seedcrackerx-deps', dirs_exist_ok=True)
    path = project/'gradle.properties'
    path.write_text(re.sub(r'^mod_version=.*$', 'mod_version=1.5.2-dev-Fabric-'+project.name,
        path.read_text('utf-8-sig'), flags=re.M), encoding='utf-8')
    path = project/'src/main/resources/wurstpenguin.accesswidener'
    lines = path.read_text('utf-8-sig').splitlines()
    for line in (donor/'src/main/resources/wurstpenguin.accesswidener').read_text().splitlines():
        if line.startswith('accessible ') and line not in lines: lines.append(line)
    path.write_text('\n'.join(lines)+'\n', encoding='utf-8')
    config_path = project/'src/main/resources/wurstpenguin.mixins.json'
    config = json.loads(config_path.read_text('utf-8-sig'))
    other = json.loads((donor/'src/main/resources/wurstpenguin.mixins.json').read_text())
    aliases = {'KeyboardHandlerMixin','RenderLayersMixin','FluidRendererMixin'}
    config['client'] = list(dict.fromkeys(n for n in config['client']+other['client'] if n not in aliases))
    for name in aliases: (project/(JAVA+'mixin/'+name+'.java')).unlink(missing_ok=True)
    for name in config['client']:
        path = project/(JAVA+'mixin/'+name.replace('.', '/')+'.java')
        if not path.exists():
            source = donor/path.relative_to(project)
            if not source.exists(): raise ValueError('Missing mixin '+name)
            path.parent.mkdir(parents=True, exist_ok=True)
            path.write_bytes(source.read_bytes())
    config_path.write_text(json.dumps(config,indent=2)+'\n', encoding='utf-8')
    from sync_v152_1201 import REPLACED_SCAFFOLD
    for rel in REPLACED_SCAFFOLD: (project/rel).unlink(missing_ok=True)
    path = project/(JAVA+'WurstInitializer.java')
    text = path.read_text('utf-8-sig')
    if 'SeedCrackerBridge.initialize()' not in text:
        text = text.replace('WurstClient.INSTANCE.initialize();', 'WurstClient.INSTANCE.initialize();\n        net.wurstclient.seed.SeedCrackerBridge.initialize();')
    path.write_text(text, encoding='utf-8')
    path = project/(JAVA+'WurstClient.java')
    path.write_text(re.sub(r'MC_VERSION = "[^"]+"', 'MC_VERSION = "'+project.name+'"', path.read_text('utf-8-sig')),encoding='utf-8')
