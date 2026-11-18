"""Proxy codecs must not upgrade Minecraft's core/buffer Netty runtime."""
import re
from pathlib import Path


def configure(project):
    mc = project.name
    if mc not in ('1.20.5', '1.20.6', '1.21', '1.21.1', '1.21.2', '1.21.3', '1.21.4', '1.21.5', '1.21.6', '1.21.7', '1.21.8', '1.21.9', '1.21.10', '1.21.11', '26.1', '26.1.1', '26.1.2','26.2','26.3'):
        raise ValueError('Unreviewed Minecraft Netty runtime: '+mc)
    version = {'1.21.4':'4.1.115.Final', '1.21.5':'4.1.118.Final',
        '1.21.6':'4.1.118.Final', '1.21.7':'4.1.118.Final', '1.21.8':'4.1.118.Final', '1.21.9':'4.1.118.Final', '1.21.10':'4.1.118.Final'}.get(mc, '4.1.97.Final')
    path = project / 'build.gradle'
    if mc in ('1.21.11','26.1','26.1.1','26.1.2','26.2','26.3'): version='4.2.7.Final'
    if mc in ('26.2','26.3'): version='4.2.15.Final'
    if mc=='26.3': version='4.2.16.Final'
    text = path.read_text('utf-8-sig')
    text = re.sub(r'include implementation\("io\.netty:(netty-codec-socks|netty-handler-proxy):[^"\n]+"\)(?: \{ transitive = false \})?',
        lambda m:'include implementation("io.netty:'+m[1]+':'+version+'") { transitive = false }', text)
    path.write_text(text, encoding='utf-8')
    test = project/'src/test/java/net/wurstclient/util/BrandPayloadCodecTest.java'
    test.parent.mkdir(parents=True, exist_ok=True)
    test.write_bytes((Path(__file__).parent/'templates/common/BrandPayloadCodecTest.java').read_bytes())
    if mc in ('1.21.11','26.1','26.1.1','26.1.2','26.2','26.3'):
        # Netty 4.2 split the old codec artifact; retain the same-version check
        # against the actual base codec shipped by Minecraft.
        test.write_text(test.read_text('utf-8').replace('"netty-codec",','"netty-codec-base",'),encoding='utf-8')
