"""Same-version native port of the deferred GUI renderer and combat features."""
from pathlib import Path
import json
import re

JAVA = 'src/main/java/net/wurstclient/'


def conflicts(rel, text, theirs):
    if rel.removeprefix(JAVA) in {
        'altmanager/screens/AltManagerScreen.java', 'gui/title/WurstTitleMenu.java',
        'options/KeybindManagerScreen.java', 'options/KeybindProfilesScreen.java',
        'hacks/AutoSprintHack.java', 'hacks/CriticalsHack.java',
        'mixin/xray/FluidRendererMixin.java'}:
        # The donor has the complete new UI and Epsilon combat behavior;
        # native lifecycle and event delivery remain target-owned.
        return theirs
    return None


def configure(project: Path, donor: Path):
    from native_test_bootstrap import configure as bootstrap
    from sync_v152_siblings import adapt
    neo = 'neoforge' in project.parts
    target = ('neoforge/versions/' if neo else 'versions/') + project.name
    bootstrap(project, neo)
    config_path = project/'src/main/resources/wurst.mixins.json'
    config = json.loads(config_path.read_text('utf-8-sig'))
    donor_config = json.loads((donor/'src/main/resources/wurstpenguin.mixins.json').read_text('utf-8-sig'))
    # Native projects keep the container accessor; keyboard and render-layer
    # aliases are already provided by the donor's consolidated hooks.
    names = [n for n in donor_config['client'] if not n.startswith('xray.indigo.')]
    if (project/(JAVA+'mixin/AbstractContainerScreenAccessor.java')).exists():
        names.append('AbstractContainerScreenAccessor')
    if (project/(JAVA+'mixin/GuiGraphicsAccessor.java')).exists():
        names.append('GuiGraphicsAccessor')
    if (project/(JAVA+'mixin/SubmitNodeCollectionMixin.java')).exists():
        names.append('SubmitNodeCollectionMixin')
    config['client'] = list(dict.fromkeys(names))
    config_path.write_text(json.dumps(config, indent=2)+'\n', encoding='utf-8')
    # These aliases inject the same behavior as the consolidated donor hooks.
    for name in ('FluidRendererMixin', 'RenderLayersMixin', 'KeyboardHandlerMixin'):
        (project/(JAVA+'mixin/'+name+'.java')).unlink(missing_ok=True)
    for name in names:
        rel = JAVA+'mixin/'+name.replace('.', '/')+'.java'
        source = donor/rel
        if source.exists():
            path = project/rel
            path.parent.mkdir(parents=True, exist_ok=True)
            data = adapt(target, rel, source.read_bytes())
            if name in ('xray.FluidRendererMixin','xray.LiquidBlockRendererMixin'):
                # Native loaders expose alpha in different vertex overloads.
                text = data.decode('utf-8-sig').replace('\r\n', '\n')
                text = text.replace('import org.spongepowered.asm.mixin.injection.ModifyConstant;',
                                    'import org.spongepowered.asm.mixin.injection.ModifyVariable;')
                text = text.replace('@ModifyConstant(\n\t\tmethod = "vertex(Lcom/mojang/blaze3d/vertex/VertexConsumer;FFFFFFFFI)V",\n\t\tconstant = @Constant(floatValue = 1F, ordinal = 0))',
                    '@ModifyVariable(method = "vertex(Lcom/mojang/blaze3d/vertex/VertexConsumer;'+('FFFFFFFFFI' if neo else 'FFFFFFFFIF')+')V", at = @At("HEAD"), argsOnly = true, index = '+('8' if neo else '11')+')')
                data = text.encode()
            path.write_bytes(data)
        elif not (project/rel).exists():
            raise ValueError('Missing native mixin '+name)
    # Mirror the actual donor access declarations, including GPU buffer state.
    rows = []
    for line in (donor/'src/main/resources/wurstpenguin.accesswidener').read_text().splitlines():
        parts = line.split()
        if not parts or parts[0] != 'accessible':
            continue
        kind, owner = parts[1], parts[2].replace('/', '.')
        if kind == 'field': rows.append('public '+owner+' '+parts[3])
        elif kind == 'method': rows.append('public '+owner+' '+parts[3]+parts[4])
        elif kind == 'class': rows.append('public '+owner)
    rows += ['public net.minecraft.client.renderer.RenderType$CompositeRenderType',
             'public net.minecraft.client.renderer.RenderType$CompositeState',
             'public net.minecraft.client.renderer.RenderStateShard$LineStateShard']
    (project/'src/main/resources/META-INF/accesstransformer.cfg').write_text('\n'.join(dict.fromkeys(rows))+'\n', encoding='utf-8')
    path = project/'build.gradle'
    text = path.read_text('utf-8-sig')
    text = re.sub(r'io.netty:(netty-codec-socks|netty-handler-proxy):[^"\n]+',
                  lambda m: 'io.netty:'+m[1]+':4.1.118.Final', text)
    text = re.sub(r'implementation "(io.netty:[^"\n]+)"', r'implementation("\1") { transitive = false }', text)
    if 'exclude "net/wurstclient/clickgui2/screens.disabled/**"' not in text:
        text += '\nsourceSets.main.java { exclude "net/wurstclient/clickgui2/screens.disabled/**" }\n'
    path.write_text(text, encoding='utf-8')
    path = project/(JAVA+'WurstForgeInitializer.java')
    text = path.read_text('utf-8-sig')
    if 'SeedCrackerBridge.disconnect()' not in text:
        if neo:
            anchor = 'NeoForge.EVENT_BUS.addListener(this::onRegisterClientCommands);'
            hook = 'NeoForge.EVENT_BUS.addListener((net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent.LoggingOut event) -> net.wurstclient.seed.SeedCrackerBridge.disconnect());'
        else:
            anchor = '.addListener(this::onRegisterClientCommands);'
            hook = 'net.minecraftforge.client.event.ClientPlayerNetworkEvent.LoggingOut.BUS.addListener(event -> net.wurstclient.seed.SeedCrackerBridge.disconnect());'
        if anchor not in text: raise ValueError('Unreviewed native lifecycle')
        text = text.replace(anchor, anchor+'\n        '+hook)
    path.write_text(text, encoding='utf-8')
    path = project/'src/test/java/net/wurstclient/mixin/HudRenderHookTest.java'
    if path.exists():
        text = path.read_text('utf-8-sig').replace('assertTrue(initializer.contains("new GUIRenderEvent"),',
            'assertTrue(initializer.contains("new GUIRenderEvent") ^ mixin.contains("new GUIRenderEvent("),')
        path.write_text(text, encoding='utf-8')
