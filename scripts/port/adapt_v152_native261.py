"""Same-version Forge/NeoForge 26.1 extraction and GPU backend port."""
from pathlib import Path
import re
import shutil
import json

JAVA='src/main/java/net/wurstclient/'


def conflicts(rel,text,theirs):
    if rel==JAVA+'clickgui2/ClickGui.java': return theirs
    from adapt_v152_native1219 import conflicts as common
    return common(rel,text,theirs)


def configure(project:Path,donor:Path):
    # The 26.1 fluid hook already modifies packed ARGB, so the old float
    # vertex-overload conversion in this shared adapter does not match it.
    from adapt_v152_native1216 import configure as common
    from sync_v152_siblings import adapt
    neo='neoforge' in project.parts
    target=('neoforge/versions/' if neo else 'versions/')+project.name
    for source in donor.joinpath('src/test').rglob('*'):
        if source.is_file():
            dest=project/source.relative_to(donor)
            dest.parent.mkdir(parents=True,exist_ok=True)
            dest.write_bytes(adapt(target,source.relative_to(donor).as_posix(),source.read_bytes()))
    for path in project.joinpath('src/test').rglob('*.java'):
        path.write_text(path.read_text().replace('WurstInitializer.java','WurstForgeInitializer.java')
            .replace('/wurstpenguin.mixins.json','/wurst.mixins.json'),encoding='utf-8')
    common(project,donor)
    from adapt_v152_fabric261 import archive_aliases
    archive_aliases(project,json.loads((project/'src/main/resources/wurst.mixins.json').read_text()))
    alias=project/(JAVA+'mixin/ClientPlayerEntityMixin.java')
    alias.unlink(missing_ok=True)  # consolidated LocalPlayerMixin owns these hooks
    # Indigo is Fabric's renderer. Native loaders use SectionCompiler and
    # FluidRenderer, already present and registered by the shared adapter.
    for path in project.joinpath(JAVA+'mixin/xray/indigo').glob('*.java'):
        path.unlink()
    shutil.copy2(donor/(JAVA+'util/Rotation.java'),project/(JAVA+'util/Rotation.java'))
    path=project/'src/main/resources/META-INF/accesstransformer.cfg'
    text=path.read_text('utf-8')
    text='\n'.join(row for row in text.splitlines() if not any(name in row for name in
        ['net.minecraft.client.renderer.RenderType$', 'net.minecraft.client.renderer.RenderStateShard$']))+'\n'
    text+='public '+('com.mojang.renderpearl.backend.opengl' if project.name=='26.3' else 'com.mojang.blaze3d.opengl')+'.GlCommandEncoder\n'
    path.write_text(text,encoding='utf-8')
    path=project/'build.gradle'
    text=re.sub(r'io.netty:(netty-codec-socks|netty-handler-proxy):[^"\n]+',
        lambda m:'io.netty:'+m[1]+':'+{'26.2':'4.2.15.Final','26.3':'4.2.16.Final'}.get(project.name,'4.2.7.Final'),path.read_text('utf-8'))
    text=re.sub(r'archiveFileName = "[^"\n]*NeoForge[^"\n]*"',
        'archiveFileName = "WurstB+ Plus-${project.mod_version}.jar"',text)
    if 'testImplementation "io.github.llamalad7:mixinextras-common:0.5.5"' not in text:
        text+='\ndependencies { testImplementation "io.github.llamalad7:mixinextras-common:0.5.5" }\n'
    text=re.sub(r'\n// Java 25: avoid Gradle 9.4 constant visitor crash[^\n]*\n'
        r'tasks.named\("compileTestJava", JavaCompile\) \{ options.incremental = false }\n','\n',text)
    if neo and '// Plain tests use the same patched Minecraft classpath' not in text:
        text+='\n// Plain tests use the same patched Minecraft classpath as main.\n'
        text+='sourceSets.test.compileClasspath += sourceSets.main.compileClasspath\n'
        text+='sourceSets.test.runtimeClasspath += sourceSets.main.compileClasspath\n'
    text=text.replace('sourceSets.test.runtimeClasspath += sourceSets.main.runtimeClasspath',
        'sourceSets.test.runtimeClasspath += sourceSets.main.compileClasspath')
    path.write_text(text,encoding='utf-8')
    if project.name in ('26.2','26.3'):
        for name in ['BaritoneLevelRendererMixin','BaritoneMinecraftMixin']:
            source=project/(JAVA+'mixin/'+name+'.java')
            if source.exists():
                archive=Path(__file__).resolve().parents[2]/'.dsh-tmp/v152-obsolete-aliases'/('native-'+project.name)/source.name
                archive.parent.mkdir(parents=True,exist_ok=True)
                if not archive.exists():shutil.copy2(source,archive)
                source.unlink()
    for name in ['NeoRavenReceivingLevelMixin','RenderTypeAccessor']:
        (project/(JAVA+'mixin/'+name+'.java')).unlink(missing_ok=True)
    if project.name=='26.3':
        # Moon's prepared outline pass replaces the removed target accessor.
        (project/(JAVA+'mixin/WorldRendererMixin.java')).unlink(missing_ok=True)
        from adapt_v152_fabric263 import adapt_shaders
        adapt_shaders(project)
