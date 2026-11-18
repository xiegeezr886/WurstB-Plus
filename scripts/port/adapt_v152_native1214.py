"""Same-version native ports; retain Forge 7 / NeoGradle and loader hooks."""
from pathlib import Path
import json
import re

JAVA = 'src/main/java/net/wurstclient/'


def conflicts(rel, text, theirs):
    if rel == JAVA+'hack/HackList.java':
        # Donor contains the complete combat replacements. Preserve any unique
        # target registrations while avoiding duplicate declarations.
        merged = text
        text = theirs.decode('utf-8-sig')
        pattern = r'public final\s+\w+\s+(\w+)\s*=\s*new\s+\w+\([^;]*?;'
        known = set(re.findall(r'public final\s+\w+\s+(\w+)\s*=', text))
        extra = {m[1]:m[0] for m in re.finditer(pattern, merged, re.S) if m[1] not in known}
        return text.replace('public HackList()', '\n'.join('\t'+n for n in extra.values())+'\n\n\tpublic HackList()').encode()
    if rel == 'src/test/java/net/wurstclient/util/render/PostEffectResourcesTest.java':
        return theirs
    if rel in {JAVA+n for n in ('gui/title/WurstTitleMenu.java',
            'hacks/AutoArmorHack.java', 'hacks/NukerHack.java', 'hacks/TreeBotHack.java')}:
        return theirs
    if rel in {JAVA+n for n in ('altmanager/screens/AltEditorScreen.java',
            'hacks/InvWalkHack.java', 'options/EnterProfileNameScreen.java',
            'options/PressAKeyScreen.java', 'serverfinder/ServerFinderScreen.java')}:
        # Persist editor input; modern vanilla's creative tab is static.
        # Inherited screen input is handled through explicit overrides.
        return re.sub(r'<<<<<<<[^\n]*\n(.*?)=======\n(.*?)>>>>>>>[^\n]*\n?',
                      lambda m:m[2], text, flags=re.S).encode()
    return None


def configure(project: Path, donor: Path):
    from native_test_bootstrap import configure as bootstrap
    neo = 'neoforge' in project.parts
    bootstrap(project, neo)
    config_path = project/'src/main/resources/wurst.mixins.json'
    config = json.loads(config_path.read_text('utf-8-sig'))
    config['client'] = list(dict.fromkeys('ToastComponentAccessor' if name == 'ToastManagerAccessor' else name for name in config['client']))
    config_path.write_text(json.dumps(config, indent=2)+'\n', encoding='utf-8')
    for name in config['client']:
        relative = JAVA+'mixin/'+name.replace('.', '/')+'.java'
        dest = project/relative
        if not dest.exists():
            source = donor/relative
            if not source.exists():
                raise ValueError('Missing registered native mixin '+name)
            dest.parent.mkdir(parents=True, exist_ok=True)
            dest.write_bytes(source.read_bytes())
    path = project/'build.gradle'
    build = path.read_text('utf-8-sig')
    # Keep the proxy codecs on the exact Minecraft buffer/core release.
    version = {'1.21.4':'4.1.115.Final', '1.21.5':'4.1.118.Final'}.get(project.name, '4.1.97.Final')
    build = re.sub(r'io.netty:(netty-codec-socks|netty-handler-proxy):[^"\n]+',
                   lambda m:'io.netty:'+m[1]+':'+version, build)
    build = re.sub(r'implementation "(io.netty:[^"\n]+)"',
                   r'implementation("\1") { transitive = false }', build)
    if 'exclude "net/wurstclient/clickgui2/screens.disabled/**"' not in build:
        build += '\nsourceSets.main.java { exclude "net/wurstclient/clickgui2/screens.disabled/**" }\n'
    path.write_text(build, encoding='utf-8')
    initializer = project/(JAVA+'WurstForgeInitializer.java')
    text = initializer.read_text('utf-8-sig')
    if 'SeedCrackerBridge.disconnect()' not in text:
        bus = 'NeoForge' if neo else 'MinecraftForge'
        package = 'net.neoforged.neoforge' if neo else 'net.minecraftforge'
        anchor = bus+'.EVENT_BUS.addListener(this::onRegisterClientCommands);'
        if anchor not in text:
            raise ValueError('Unreviewed native initializer '+str(initializer))
        text = text.replace(anchor, anchor+'\n        '+bus+'.EVENT_BUS.addListener(('
            +package+'.client.event.ClientPlayerNetworkEvent.LoggingOut event) -> '
            +'net.wurstclient.seed.SeedCrackerBridge.disconnect());')
    initializer.write_text(text, encoding='utf-8')
    test = project/'src/test/java/net/wurstclient/mixin/HudRenderHookTest.java'
    if test.exists():
        text = test.read_text('utf-8-sig')
        text = text.replace('旧架构：钩子在 {@code IngameHudMixin}，事件由 initializer 派发',
            'IngameHudMixin：事件只能由 initializer 或 mixin 其中一处派发')
        text = text.replace('assertTrue(initializer.contains("new GUIRenderEvent"),\n'
            '\t\t\t\t"旧架构下 GUIRenderEvent 应由 initializer 派发");',
            'assertTrue(initializer.contains("new GUIRenderEvent")\n'
            '\t\t\t\t^ mixin.contains("new GUIRenderEvent("),\n'
            '\t\t\t\t"GUIRenderEvent 应派发一次，不能遗漏或重复");')
        test.write_text(text, encoding='utf-8')
    if not neo:
        at = project/'src/main/resources/META-INF/accesstransformer.cfg'
        text = at.read_text('utf-8-sig')
        if project.name == '1.21.5':
            text = text.replace('net.minecraft.client.gui.components.toasts.ToastComponent queued',
                'net.minecraft.client.gui.components.toasts.ToastManager queued')
        entry = 'public net.minecraft.client.renderer.FogRenderer$MobEffectFogFunction'
        if entry not in text:
            text = text.rstrip()+'\n'+entry+'\n'
        at.write_text(text, encoding='utf-8')
        # Forge 54 has no RenderLevelStageEvent. Its existing renderLevel hook
        # already supplies a camera view, then drains both bounded effect queues.
        path = project/(JAVA+'mixin/LevelRendererMixin.java')
        text = path.read_text('utf-8-sig')
        anchor = 'EventManager.fire(new RenderEvent(matrixStack, tickProgress));'
        if 'getPostEffectQueue().flush(' not in text:
            if anchor not in text:
                raise ValueError('Unreviewed Forge render hook')
            text = text.replace(anchor, anchor+'\n        net.wurstclient.WurstClient.INSTANCE.getPostEffectQueue().flush(matrixStack, tickProgress);\n'
                +'        net.wurstclient.util.render.MoonEntityGlow.INSTANCE.flush(matrixStack, tickProgress);')
        path.write_text(text, encoding='utf-8')
