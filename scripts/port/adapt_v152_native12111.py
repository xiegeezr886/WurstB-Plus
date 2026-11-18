"""Native 1.21.11 sampler/RenderSetup port from its matching Fabric donor."""
from pathlib import Path
import re

JAVA='src/main/java/net/wurstclient/'


def conflicts(rel,text,theirs):
    if rel.removeprefix(JAVA) in {'hud2/nanovg/NanoVGContext.java',
        'mixin/xray/LiquidBlockRendererMixin.java'}:
        return theirs
    from adapt_v152_native1219 import conflicts as common
    return common(rel,text,theirs)


def configure(project:Path,donor:Path):
    from adapt_v152_native1216 import configure as common
    common(project,donor)
    path=project/'src/main/resources/META-INF/accesstransformer.cfg'
    text=path.read_text('utf-8')
    text='\n'.join(row for row in text.splitlines() if not any(name in row for name in
        ['net.minecraft.client.renderer.RenderType$', 'net.minecraft.client.renderer.RenderStateShard$']))+'\n'
    path.write_text(text,encoding='utf-8')
    path=project/'build.gradle'
    text=re.sub(r'io.netty:(netty-codec-socks|netty-handler-proxy):[^"\n]+',
        lambda m:'io.netty:'+m[1]+':4.2.7.Final',path.read_text('utf-8'))
    text=re.sub(r'archiveFileName = "WurstB\+ Plus-v1\.5\.1-[^"]+"',
        'archiveFileName = "WurstB+ Plus-${project.mod_version}.jar"',text)
    path.write_text(text,encoding='utf-8')
    for name in ['NeoRavenReceivingLevelMixin','RenderTypeAccessor']:
        (project/(JAVA+'mixin/'+name+'.java')).unlink(missing_ok=True)
