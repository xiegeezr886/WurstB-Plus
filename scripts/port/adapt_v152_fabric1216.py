"""1.21.6 deferred GUI port; keep each snapshot alive until state reset."""
from pathlib import Path
import hashlib
import json
import re
import zipfile

ROOT = Path(__file__).resolve().parents[2]
JAVA = 'src/main/java/net/wurstclient/'
WRAPPER = 'net.wurstclient.util.render.GuiGraphicsExtractor'
REPLACED = {
 'altmanager/screens/AltEditorScreen.java', 'altmanager/screens/AltManagerScreen.java',
 'clickgui2/ClickGuiScreen.java', 'clickgui2/NavigatorScreen.java',
 'clickgui2/NavigatorSettingsPanel.java', 'gui/title/WurstTitleButton.java',
 'gui/title/WurstTitleMenu.java', 'hacks/AntiBotHack.java', 'hacks/AutoArmorHack.java',
 'hacks/AutoMineHack.java', 'hacks/AutoSprintHack.java', 'hacks/AutoTotemHack.java',
 'hacks/BowAimbotHack.java', 'hacks/BreakEspHack.java', 'hacks/DeathRenderHack.java',
 'hacks/InvWalkHack.java', 'hacks/NukerHack.java', 'hacks/ScaffoldWalkHack.java',
 'hacks/TreeBotHack.java', 'hacks/TriggerBotHack.java', 'util/render/PostEffectQueue.java',
 'mixin/TitleScreenMixin.java', 'mixin/ScreenMixin.java'}
MERGED_DONOR = {'mixin/EntityMixin.java', 'options/EnterProfileNameScreen.java',
 'options/KeybindEditorScreen.java', 'options/KeybindManagerScreen.java',
 'options/KeybindProfilesScreen.java', 'options/PressAKeyScreen.java',
 'options/WurstOptionsScreen.java', 'options/ZoomManagerScreen.java',
 'other_features/NoChatReportsOtf.java', 'serverfinder/CleanUpScreen.java',
 'serverfinder/ServerFinderScreen.java'}


def choose(text, side):
    return re.sub(r'<<<<<<<[^\n]*\n(.*?)=======\n(.*?)>>>>>>>[^\n]*\n?',
        lambda m: m[side], text, flags=re.S)


def conflicts(rel, text, theirs):
    short = rel.removeprefix(JAVA)
    if short in REPLACED:
        return theirs
    if short in MERGED_DONOR:
        return choose(text, 2).encode()
    if short == 'hack/HackList.java':
        original = (ROOT/'.dsh-tmp/v152-siblings/originals/fabric/versions/1.21.6'/rel).read_text('utf-8-sig')
        donor = theirs.decode('utf-8-sig')
        pattern = r'public final\s+\w+\s+(\w+)\s*=\s*new\s+\w+\([^;]*?;'
        known = set(re.findall(r'public final\s+\w+\s+(\w+)\s*=', donor))
        extra = [m[0] for m in re.finditer(pattern, original, re.S) if m[1] not in known]
        return donor.replace('public HackList()', '\n'.join(extra)+'\n\tpublic HackList()').encode()
    if short in ('hud2/HudManager.java', 'hud2/nanovg/NanoVGUI.java'):
        # Retain the target's deferred HUD subscription/composition, while the
        # non-conflicting hunks bring in the new modules and behavior.
        return choose(text, 1).encode()
    if short == 'WurstClient.java':
        return choose(text, 2).replace('MC_VERSION = "1.21.5"', 'MC_VERSION = "1.21.6"').encode()
    if short == 'WurstInitializer.java':
        return choose(text, 1).encode()
    if short == 'mixin/BlockMixin.java':
        return choose(text, 2).encode()
    if rel == 'src/main/resources/wurstpenguin.mixins.json':
        return choose(text, 1).encode()
    return None


def adapt(rel, text):
    if rel.endswith('BlockMixin.java'):
        text = text.replace('package net.wurstclient.mixin;', 'package net.wurstclient.mixin;\n'
            'import net.minecraft.core.BlockPos;\nimport net.minecraft.core.Direction;\n'
            'import net.minecraft.world.level.block.state.BlockState;\n'
            'import net.wurstclient.event.EventManager;\n'
            'import net.wurstclient.events.ShouldDrawSideListener.ShouldDrawSideEvent;')
    if rel.endswith('EntityMixin.java') and '@WrapWithCondition' in text:
        text = text.replace('package net.wurstclient.mixin;', 'package net.wurstclient.mixin;\n'
            'import com.llamalad7.mixinextras.injector.v2.WrapWithCondition;')
    # Version utilities and events use a wrapper around the new deferred state.
    # Native overrides and injection descriptors must still receive GuiGraphics.
    if 'GuiGraphics' in text and '/mixin/' not in rel:
        # Existing target entry points already wrap the native argument. Keep
        # those signatures native rather than wrapping an extractor twice.
        native_entries = []
        def keep_native(m):
            native_entries.append(m[0])
            return f'__V152_NATIVE_ENTRY_{len(native_entries)-1}__'
        text = re.sub(r'@Override\s+(?:public|protected)\s+void\s+render\(GuiGraphics\s+graphics,[^{]+\{\s*extractContents\(new GuiGraphicsExtractor\(graphics\)[^}]+}', keep_native, text)
        text = re.sub(r'@Override\s+public\s+void\s+render\(GuiGraphics\s+graphics,\s*int index,[^{]+\{\s*extractContent\(new GuiGraphicsExtractor\(graphics\)[^}]+}', keep_native, text)
        text = text.replace('net.minecraft.client.gui.GuiGraphics', WRAPPER)
        text = re.sub(r'\bGuiGraphics\b', 'GuiGraphicsExtractor', text)
        text = text.replace(WRAPPER+' ', 'GuiGraphicsExtractor ')
        # Restore native entrypoints as small bridges, keeping custom drawing
        # methods typed to the target's shared wrapper.
        pattern = r'@Override\s+(public|protected)\s+(void|boolean)\s+(render|renderWidget|renderBackground|renderTransparentBackground|renderMenuBackground)\(GuiGraphicsExtractor\s+(\w+),\s*int\s+(\w+),\s*int\s+(\w+),\s*float\s+(\w+)\)'
        def bridge(m):
            access, result, name, g, x, y, delta = m.groups()
            ret = 'return ' if result != 'void' else ''
            custom = ('/components/' in rel or rel.endswith(('NavigatorActionComponent.java', 'NavigatorValueComponent.java'))
                or (rel.endswith('ClickGuiScreen.java') and 'abstract class TemplateWindow' in text
                    and m.start() > text.index('abstract class TemplateWindow')))
            annotation = '' if custom else '@Override '
            wrapper_annotation = '@Override ' if custom else ''
            return (f'{annotation}{access} {result} {name}(net.minecraft.client.gui.GuiGraphics {g}, int {x}, int {y}, float {delta}) {{'
                f' {ret}{name}(new GuiGraphicsExtractor({g}), {x}, {y}, {delta}); }}\n'
                f'{wrapper_annotation}{access} {result} {name}(GuiGraphicsExtractor {g}, int {x}, int {y}, float {delta})')
        text = re.sub(pattern, bridge, text)
        text = re.sub(r'super\.(render\w*)\((graphics|context|g),', r'super.\1(\2.getInner(),', text)
        text = re.sub(r'(?<![.\w])renderBackground\((context|g),', r'renderBackground(\1.getInner(),', text)
        text = re.sub(r'((?:nameOrEmailBox|passwordBox|searchBox|search|listGui))\.render\((context|graphics),', r'\1.render(\2.getInner(),', text)
        text = text.replace('.scale(viewport.scale(), viewport.scale(), 1)', '.scale(viewport.scale(), viewport.scale())')
        text = re.sub(r'new (?:net\.wurstclient\.util\.render\.)?GuiGraphicsExtractor\((graphics|context)\)', r'\1', text)
        # Restore construction in the generated native bridges only.
        text = re.sub(r'(net\.minecraft\.client\.gui\.GuiGraphics (\w+),[^{}]+\{\s*(?:return )?\w+\()\2,', r'\1new GuiGraphicsExtractor(\2),', text)
        for index, entry in enumerate(native_entries):
            entry = entry.replace('render(GuiGraphics ', 'render(net.minecraft.client.gui.GuiGraphics ')
            text = text.replace(f'__V152_NATIVE_ENTRY_{index}__', entry)
        text = text.replace('.drawCenteredString(', '.centeredText(').replace('.drawString(', '.text(')
        text = re.sub(r'(\w+)\.pose\(\)\.pushPose\(\)', r'\1.pose().pushMatrix()', text)
        text = re.sub(r'(\w+)\.pose\(\)\.popPose\(\)', r'\1.pose().popMatrix()', text)
    if '/mixin/' in rel:
        calls = ('renderContainerIsland','beginScreen','endScreen')
        for name in calls:
            text = re.sub(r'(\.'+name+r'\()(?P<arg>graphics|context)(?=[,)])', r'\1new net.wurstclient.util.render.GuiGraphicsExtractor(\g<arg>)', text)
        text = re.sub(r'((?:MainMenuBackground|NeoRavenLoadingOverlay)\.render\(\s*)(graphics|context)', r'\1new net.wurstclient.util.render.GuiGraphicsExtractor(\2)', text)
        text = re.sub(r'((?:opai\$menu|wurstPenguin\$titleMenu)\.render\()(graphics|g)\b', r'\1new net.wurstclient.util.render.GuiGraphicsExtractor(\2)', text)
        text = text.replace('g.flush();', 'new net.wurstclient.util.render.GuiGraphicsExtractor(g).flush();')
    if rel.endswith('IngameHudMixin.java'):
        text = text.replace('renderHotbar(FLnet/minecraft/client/gui/GuiGraphics;)V', 'renderItemHotbar(Lnet/minecraft/client/gui/GuiGraphics;Lnet/minecraft/client/DeltaTracker;)V')
        text = text.replace('onRenderHotbar(float partialTicks, GuiGraphics context,', 'onRenderHotbar(GuiGraphics context, net.minecraft.client.DeltaTracker tracker,')
        text = text.replace('renderEffects(Lnet/minecraft/client/gui/GuiGraphics;)V', 'renderEffects(Lnet/minecraft/client/gui/GuiGraphics;Lnet/minecraft/client/DeltaTracker;)V')
        text = text.replace('onRenderEffects(GuiGraphics context, CallbackInfo ci)', 'onRenderEffects(GuiGraphics context, net.minecraft.client.DeltaTracker tracker, CallbackInfo ci)')
    if rel.endswith('HudManager.java'):
        text = re.sub(r'public final class HudManager(?: implements GUIRenderListener)?', 'public final class HudManager implements GUIRenderListener', text)
        if 'eventManager.add(GUIRenderListener.class, this)' not in text:
            text = text.replace('started = true;', '''started = true;
        EventManager eventManager = WURST.getEventManager();
        if(eventManager != null) eventManager.add(GUIRenderListener.class, this);''')
        if 'eventManager.remove(GUIRenderListener.class, this)' not in text:
            text = text.replace('stopping = true;', '''stopping = true;
        EventManager eventManager = WURST.getEventManager();
        if(eventManager != null) eventManager.remove(GUIRenderListener.class, this);''')
    if rel.endswith('WurstTitleMenu.java'):
        text = text.replace('getCurrentVersion().getName()', 'getCurrentVersion().name()')
    if rel.endswith('WorldRendererMixin.java'):
        text = text.replace('shouldCull(entity,\n\t\t\t\tcameraX, cameraY, cameraZ, partialTicks, poseStack)', 'shouldCull(entity)')
        # The target hooks EntityRenderDispatcher.shouldRender before any model
        # work; keep that single culling path instead of the old late hook.
        text = re.sub(r'\s*@Inject\(at = @At\("HEAD"\),\s*method = "renderEntity.*?\n\t\}', '', text, flags=re.S)
    # Sneaking moved into the input packet; preserve every other input bit.
    text = re.sub(r'new ServerboundPlayerCommandPacket\(\s*(MC.player|mc.player|owner),\s*ServerboundPlayerCommandPacket.Action.(PRESS|RELEASE)_SHIFT_KEY\)',
        lambda m: 'net.wurstclient.util.render.V152Input.sneak('+m[1]+', '+str(m[2]=='PRESS').lower()+')', text)
    if rel.endswith('FireworkElytraFlyHack.java'):
        text = text.replace('IMC.getInteractionManager().sendPlayerUseItemPacket(hand)', 'MC.gameMode.useItem(MC.player, hand)')
    if rel.endswith('SkiaTextCache.java'):
        text = text.replace('net.minecraft.client.renderer.RenderType::guiTextured', 'net.minecraft.client.renderer.RenderPipelines.GUI_TEXTURED')
    if rel.endswith('TestControlServer.java'):
        text = text.replace('MC.getMainRenderTarget(), component ->', 'MC.getMainRenderTarget(), 1, component ->')
    if rel.endswith('V152BackdropBlur.java'):
        text = text.replace('GraphicsResourceAllocator.UNPOOLED, pass -> {}', 'GraphicsResourceAllocator.UNPOOLED')
    if rel.endswith('DuoGlassTransition.java'):
        text = text.replace('content.accept(layer);\n\t\t\t\tgraphics.flush();', '''try(var targetScope = net.wurstclient.util.render.V152Gpu.target(layer)) {
                    content.accept(layer);
                    graphics.flush();
                }''')
        # target() scopes restore even if content drawing throws.
        text = text.replace('catch(IOException | RuntimeException e)', 'catch(Exception e)')
    if rel.endswith('PostEffectQueue.java'):
        text = text.replace('target.postChain.process(target.renderTarget, com.mojang.blaze3d.resource.GraphicsResourceAllocator.UNPOOLED, pass -> pass.setUniform("Time", (System.nanoTime() % 20_000_000_000L) / 20_000_000_000F));',
            'V152PostUniforms.time(target.postChain, effect.ordinal(), (System.nanoTime() % 20_000_000_000L) / 20_000_000_000F);\n'
            '        target.postChain.process(target.renderTarget, com.mojang.blaze3d.resource.GraphicsResourceAllocator.UNPOOLED);')
        text = text.replace('RenderSystem.backupProjectionMatrix();', 'var previousProjection = V152Gpu.projection();')
        text = text.replace('RenderSystem.setProjectionMatrix(new Matrix4f().setOrtho(0, width,\n\t\t\t\theight, 0, 1000, 3000), ProjectionType.ORTHOGRAPHIC);',
            'V152Gpu.setProjection(new Matrix4f().setOrtho(0, width, height, 0, 1000, 3000), true);')
        text = text.replace('RenderSystem.restoreProjectionMatrix();', 'V152Gpu.restoreProjection(previousProjection);')
    text = text.replace('RenderSystem.disableScissor()', 'com.mojang.blaze3d.opengl.GlStateManager._disableScissorTest()')
    text = text.replace('RenderSystem.setShaderColor(', 'V152Gpu.setShaderColor(') if rel.endswith(('PostEffectQueue.java','MoonEntityGlow.java')) else text
    if rel.endswith('MoonEntityGlow.java'):
        text = text.replace('new org.joml.Matrix4f(RenderSystem.getModelViewMatrix()).mul(matrices.last().pose()), RenderSystem.getProjectionMatrix()',
            'new org.joml.Matrix4f(matrices.last().pose()), net.wurstclient.events.RenderListener.RenderEvent.getLastProjection()')
    if rel.endswith('NoChatReportsOtf.java'):
        text = text.replace('Icon.CHAT_MODIFIED', 'GuiMessageTag.Icon.CHAT_MODIFIED').replace('GuiMessageTag.GuiMessageTag.', 'GuiMessageTag.')
    return text


def snapshot_layers(project):
    path = project/'src/main/java/net/wurstclient/hud2/nanovg/NanoVgBlitter.java'
    text = path.read_text('utf-8-sig')
    if 'state.nextStratum();' not in text:
        text = text.replace('state.submitBlitToCurrentLayer(new BlitRenderState(',
            'state.nextStratum();\n\t\tstate.submitBlitToCurrentLayer(new BlitRenderState(')
        path.write_text(text, encoding='utf-8')


def configure(project):
    snapshot_layers(project)
    import sync_v152_fabric1204 as old
    previous = old.TARGET
    try:
        old.TARGET = project
        old.configure_build()
    finally:
        old.TARGET = previous
    from host_netty import configure as netty
    netty(project)
    props = project/'gradle.properties'
    props.write_text(re.sub(r'^mod_version=.*$', 'mod_version=1.5.2-dev-Fabric-'+project.name,
        props.read_text('utf-8-sig'), flags=re.M), encoding='utf-8')
    seed = ROOT/'.dsh-tmp/v152-upstream-seed/seedcrackerX-2.15.4.jar'
    digest = '17773800aa5366838aadb2d8b98eaa9a8e03c8ba3b2276c53085e10700e96e24'
    assert hashlib.sha256(seed.read_bytes()).hexdigest() == digest
    libs = project/'libs'
    deps = libs/'seedcrackerx-deps'
    deps.mkdir(parents=True, exist_ok=True)
    (libs/seed.name).write_bytes(seed.read_bytes())
    with zipfile.ZipFile(seed) as z:
        meta = json.loads(z.read('fabric.mod.json'))
        assert meta['depends']['minecraft'] == '>=1.21.6 <1.22'
        for row in meta['jars']:
            (deps/Path(row['file']).name).write_bytes(z.read(row['file']))
    p = project/'build.gradle'
    text = re.sub(r'seedcrackerX:[^"\s]+', 'seedcrackerX:2.15.4', p.read_text('utf-8-sig'))
    text = text.replace('include: ["*.jar"]', 'include: ["cloth-config*.jar"]')
    if 'include: ["mc_*.jar", "latticg-*.jar"]' not in text:
        text += '\ndependencies { runtimeOnly fileTree(dir: "libs/seedcrackerx-deps", include: ["mc_*.jar", "latticg-*.jar"]) }\n'
    p.write_text(text, encoding='utf-8')
    notice = project/'src/main/resources/META-INF/licenses/seedcrackerx/NOTICE.txt'
    notice.write_text('SeedCrackerX 2.15.4, MIT, unmodified upstream release for Minecraft 1.21.6-1.21.8.\n'
        'https://github.com/19MisterX98/SeedcrackerX/releases/tag/2.15.4\nSHA-256: '+digest+'\n', encoding='utf-8')
    from sync_v152_1201 import REPLACED_SCAFFOLD
    for rel in REPLACED_SCAFFOLD:
        (project/rel).unlink(missing_ok=True)
    config_path = project/'src/main/resources/wurstpenguin.mixins.json'
    config = json.loads(config_path.read_text('utf-8-sig'))
    config['client'] = list(dict.fromkeys('ToastComponentAccessor' if n=='ToastManagerAccessor' else n for n in config['client']))
    donor = ROOT/'fabric/versions/1.21.5'
    donor_config = json.loads((donor/'src/main/resources/wurstpenguin.mixins.json').read_text('utf-8-sig'))
    # These old classes were removed by Mojang. Their behavior belongs to the
    # target's fog-environment hooks and shared DisconnectedScreen respectively.
    replaced_mixins = {'BackgroundRendererMixin', 'DisconnectedRealmsScreenMixin', 'KeyboardHandlerMixin',
        'ClientPlayerEntityMixin', 'MouseMixin', 'ChatHudMixin', 'InGameOverlayRendererMixin',
        'MobEntityRendererMixin', 'RenderLayersMixin', 'FluidRendererMixin'}
    config['client'] = list(dict.fromkeys(n for n in config['client']+donor_config['client'] if n not in replaced_mixins))
    if 'EntityCullingMixin' not in config['client']:
        config['client'].append('EntityCullingMixin')
    for name in replaced_mixins:
        (project/(JAVA+'mixin/'+name+'.java')).unlink(missing_ok=True)
    for name in config['client']:
        relative = JAVA+'mixin/'+name.replace('.', '/')+'.java'
        dest = project/relative
        if not dest.exists():
            source = donor/relative
            if not source.exists():
                raise ValueError('Registered mixin has no implementation: '+name)
            dest.parent.mkdir(parents=True, exist_ok=True)
            dest.write_text(adapt(relative, source.read_text('utf-8-sig')), encoding='utf-8')
    config_path.write_text(json.dumps(config, indent=2)+'\n', encoding='utf-8')
    initializer = project/(JAVA+'WurstInitializer.java')
    text = initializer.read_text('utf-8-sig')
    if 'SeedCrackerBridge.initialize()' not in text:
        text = text.replace('WurstClient.INSTANCE.initialize();', 'WurstClient.INSTANCE.initialize();\n        net.wurstclient.seed.SeedCrackerBridge.initialize();')
    initializer.write_text(text, encoding='utf-8')
    mixin = project/(JAVA+'mixin/LevelRendererMixin.java')
    text = mixin.read_text('utf-8-sig')
    if 'MoonEntityGlow.INSTANCE.flush' not in text:
        text = text.replace('EventManager.fire(event);', 'EventManager.fire(event);\n'
            '        net.wurstclient.WurstClient.INSTANCE.getPostEffectQueue().flush(matrixStack, tickProgress);\n'
            '        net.wurstclient.util.render.MoonEntityGlow.INSTANCE.flush(matrixStack, tickProgress);')
    mixin.write_text(text, encoding='utf-8')
    consolidate_mixins(project)
    configure_rendering(project)


def method_block(text, name):
    match = re.search(r'\b'+re.escape(name)+r'\([^;{]*\)\s*\{', text)
    if not match: raise ValueError('Missing method '+name)
    start = text.rfind('\n\t@', 0, match.start())
    opening = text.index('{', match.start())
    depth = 1
    end = opening+1
    while depth:
        depth += (text[end]=='{')-(text[end]=='}')
        end += 1
    return text[start:end]


def consolidate_mixins(project):
    donor = ROOT/'fabric/versions/1.21.5'
    path = project/(JAVA+'mixin/LocalPlayerMixin.java')
    text = path.read_text('utf-8-sig')
    incoming = (donor/(JAVA+'mixin/ClientPlayerEntityMixin.java')).read_text('utf-8-sig')
    text = text.replace('implements ILocalPlayer\n', 'implements ILocalPlayer, net.wurstclient.mixinterface.IClientPlayerEntity\n')
    for name in ('epsilon$preventBlockPush','useSilentYaw','useSilentPitch','isInLava','isSpectator'):
        if re.search(r'\b'+re.escape(name)+r'\(',text): continue
        text = text.rstrip()[:-1] + method_block(incoming, name) + '\n}\n'
    text = text.replace(method_block(text,'isStayingOnGroundSurface'), method_block(incoming,'isStayingOnGroundSurface'))
    text = text.replace('import net.wurstclient.util.MovementPlanner;', '''import net.wurstclient.util.MovementPlanner;
import net.wurstclient.util.Rotation;
import net.wurstclient.events.IsPlayerInLavaListener.IsPlayerInLavaEvent;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;''') if 'import net.wurstclient.util.Rotation;' not in text else text
    path.write_text(text, encoding='utf-8')
    path = project/(JAVA+'mixin/MouseHandlerMixin.java')
    text = path.read_text('utf-8-sig')
    if 'new net.wurstclient.events.MouseButtonListener.MouseButtonEvent' not in text:
        text = text.replace('EventManager.fire(new MouseButtonPressEvent(button, action));', '''EventManager.fire(new MouseButtonPressEvent(button, action));
        EventManager.fire(new net.wurstclient.events.MouseButtonListener.MouseButtonEvent(button, action));''')
    path.write_text(text, encoding='utf-8')
    path = project/(JAVA+'mixin/ScreenEffectRendererMixin.java')
    text = path.read_text('utf-8-sig').replace(', require = 0', '')
    path.write_text(text, encoding='utf-8')
    path = project/(JAVA+'mixin/xray/FluidRendererMixin.java')
    text = path.read_text('utf-8-sig')
    text = text.replace('Boolean shouldDrawSide = xray.shouldDrawSide(blockState, null);', '''var event = new net.wurstclient.events.ShouldDrawSideListener.ShouldDrawSideEvent(blockState, null);
        net.wurstclient.event.EventManager.fire(event);
        Boolean shouldDrawSide = event.isRendered();''')
    text = text.replace(', require = 0', '')
    path.write_text(text, encoding='utf-8')


def configure_rendering(project):
    templates = ROOT/'scripts/port/templates/fabric1216'
    for template in templates.glob('*.java'):
        dest = project/(JAVA+'util/render/'+template.name)
        dest.write_bytes(template.read_bytes())
    for template in (templates/'tests').glob('*.java'):
        dest = project/'src/test/java/net/wurstclient/util/render'/template.name
        dest.parent.mkdir(parents=True, exist_ok=True)
        dest.write_bytes(template.read_bytes())
    aw = project/'src/main/resources/wurstpenguin.accesswidener'
    text = aw.read_text('utf-8-sig')
    for row in [
        'accessible field net/minecraft/client/renderer/GameRenderer guiRenderer Lnet/minecraft/client/gui/render/GuiRenderer;',
        'accessible field net/minecraft/client/renderer/GameRenderer fogRenderer Lnet/minecraft/client/renderer/fog/FogRenderer;',
        'accessible field net/minecraft/client/gui/render/GuiRenderer renderState Lnet/minecraft/client/gui/render/state/GuiRenderState;',
        'accessible field com/mojang/blaze3d/opengl/GlCommandEncoder lastProgram Lcom/mojang/blaze3d/opengl/GlProgram;',
        'accessible field com/mojang/blaze3d/opengl/GlCommandEncoder lastPipeline Lcom/mojang/blaze3d/pipeline/RenderPipeline;',
        'accessible field net/minecraft/client/renderer/PostChain passes Ljava/util/List;',
        'accessible field net/minecraft/client/renderer/PostPass customUniforms Ljava/util/Map;']:
        if row not in text: text += '\n'+row+'\n'
    aw.write_text(text, encoding='utf-8')
    path = project/(JAVA+'util/render/GuiGraphicsExtractor.java')
    text = path.read_text('utf-8-sig')
    if 'public void flush()' not in text:
        text = text.replace('private final GuiGraphics inner;', '''private final GuiGraphics inner;
    public void flush() { V152GuiBridge.flush(this); }
    public void renderTooltip(Font font, ItemStack stack, int x, int y) {
        inner.setTooltipForNextFrame(font, stack, x, y);
    }''')
    path.write_text(text, encoding='utf-8')
    path = project/(JAVA+'hud2/nanovg/GlStateGuard.java')
    text = path.read_text('utf-8-sig')
    anchor = 'GL11.glScissor(scissorX, scissorY, scissorWidth, scissorHeight);'
    if 'encoder.lastProgram = null' not in text:
        text = text.replace(anchor, '''// Immediate rendering restores GL directly; synchronize the cached
        // state too before Minecraft submits another GPU render pass.
        if(blend) GlStateManager._enableBlend(); else GlStateManager._disableBlend();
        GlStateManager._blendFuncSeparate(blendSrcRgb, blendDstRgb, blendSrcAlpha, blendDstAlpha);
        if(depthTest) GlStateManager._enableDepthTest(); else GlStateManager._disableDepthTest();
        GlStateManager._depthMask(depthMask);
        GlStateManager._depthFunc(depthFunc);
        if(cull) GlStateManager._enableCull(); else GlStateManager._disableCull();
        GlStateManager._colorMask(colorMaskR, colorMaskG, colorMaskB, colorMaskA);
        if(scissor) GlStateManager._enableScissorTest(); else GlStateManager._disableScissorTest();
        var encoder = (com.mojang.blaze3d.opengl.GlCommandEncoder)RenderSystem.getDevice().createCommandEncoder();
        encoder.lastProgram = null;
        encoder.lastPipeline = null;
        '''+anchor)
    path.write_text(text, encoding='utf-8')
    # Keep the donor's full state restoration, including framebuffer/viewport.
    path = project/(JAVA+'util/render/RenderScope.java')
    text = (ROOT/'fabric/versions/1.21.5'/(JAVA+'util/render/RenderScope.java')).read_text('utf-8-sig')
    text = text.replace('RenderSystem.getShaderColor()', 'V152Gpu.getShaderColor()').replace('RenderSystem.setShaderColor(', 'V152Gpu.setShaderColor(')
    text = text.replace('RenderSystem.lineWidth(', 'GL11.glLineWidth(')
    path.write_text(text, encoding='utf-8')
    path = project/(JAVA+'util/render/V152Gpu.java')
    text = path.read_text('utf-8-sig')
    text = text.replace('private static RenderTarget override;', '''private static RenderTarget override;
  private static Matrix4f immediateProjection;
  private static boolean combineModelView;
  private static float[] tint = {1, 1, 1, 1};
  public record Projection(Matrix4f matrix, boolean combine) {}
  public static Projection projection() { return new Projection(immediateProjection, combineModelView); }
  public static void restoreProjection(Projection previous) {
    immediateProjection = previous.matrix(); combineModelView = previous.combine();
  }
  public static void setProjection(Matrix4f matrix, boolean combine) {
    immediateProjection = new Matrix4f(matrix); combineModelView = combine;
  }
  public static float[] getShaderColor() { return tint; }
  public static void setShaderColor(float r, float g, float b, float a) { tint = new float[]{r,g,b,a}; }
  private static Matrix4f transform() {
    var window = Minecraft.getInstance().getWindow();
    Matrix4f matrix = immediateProjection == null
        ? new Matrix4f().setOrtho(0, window.getGuiScaledWidth(), window.getGuiScaledHeight(), 0, -1, 1)
        : new Matrix4f(immediateProjection);
    return combineModelView ? matrix.mul(RenderSystem.getModelViewMatrix()) : matrix;
  }''')
    text = text.replace('new Matrix4f(RenderSystem.getProjectionMatrix()).mul(RenderSystem.getModelViewMatrix())', 'transform()')
    text = text.replace('RenderSystem.getShaderColor()', 'getShaderColor()')
    path.write_text(text, encoding='utf-8')
    path = project/(JAVA+'mixin/LevelRendererMixin.java')
    path.write_bytes((templates/'mixin/LevelRendererMixin.java').read_bytes())
    # A deferred snapshot directed at a transition layer must be consumed while
    # that layer is active, before the native glass shader samples its texture.
    path = project/(JAVA+'hud2/nanovg/NanoVGUI.java')
    text = path.read_text('utf-8-sig')
    text = text.replace('NanoVgBlitter.queueBlit(graphics, NVG);', '''if(target != mc.getMainRenderTarget()) graphics.flush();
            NanoVgBlitter.queueBlit(graphics, NVG);
            if(target != mc.getMainRenderTarget())
                try(var targetScope = net.wurstclient.util.render.V152Gpu.target(target)) { graphics.flush(); }''')
    path.write_text(text, encoding='utf-8')
    path = project/(JAVA+'hud2/compat/HudItemIcons.java')
    text = (ROOT/'fabric/versions/1.21.5'/(JAVA+'hud2/compat/HudItemIcons.java')).read_text('utf-8-sig')
    text = re.sub(r'\s*Matrix4f oldProjection =.*?try\n\s*\{\n\s*ensureTarget\(\);', '\n            {\n                ensureTarget();', text, flags=re.S)
    text = re.sub(r'\}finally\n\s*\{\n\s*RenderSystem.getModelViewStack\(\).popMatrix\(\);.*?\n\s*\}', '}', text, count=1, flags=re.S)
    text = text.replace('RenderSystem.disableScissor()', 'com.mojang.blaze3d.opengl.GlStateManager._disableScissorTest()')
    text = text.replace('nvgHandle = nvg.getHandle();', 'nvgHandle = nvg.getHandle();\n        net.wurstclient.util.render.V152GuiBridge.flushMain();')
    text = re.sub(r'\s*RenderSystem.setProjectionMatrix\(new Matrix4f\(\).setOrtho.*?ProjectionType.ORTHOGRAPHIC\);', '', text, flags=re.S)
    text = re.sub(r'GuiGraphics graphics = new GuiGraphics\(.*?graphics.flush\(\);',
        'net.wurstclient.util.render.V152GuiBridge.item(stack, target);', text, flags=re.S)
    path.write_text(text, encoding='utf-8')
    path = project/(JAVA+'mixin/MinecraftAccessor.java')
    text = path.read_text('utf-8-sig')
    text = text.replace('int getRightClickDelay()', 'int wurst$getRightClickDelay()')
    text = text.replace('void setRightClickDelay(', 'void wurst$setRightClickDelay(')
    path.write_text(text, encoding='utf-8')
    path = project/(JAVA+'mixin/MultiPlayerGameModeAccessor.java')
    text = path.read_text('utf-8-sig').replace('float getDestroyProgress()', 'float wurst$getDestroyProgress()')
    path.write_text(text, encoding='utf-8')
    for relative in ('mixin/ClientPlayerInteractionManagerMixin.java', 'util/TestControlServer.java'):
        path = project/(JAVA+relative)
        text = path.read_text('utf-8-sig')
        text = text.replace('access.getDestroyProgress()', 'access.wurst$getDestroyProgress()')
        text = text.replace('WurstClient.MC.gameMode).getDestroyProgress()', 'WurstClient.MC.gameMode).wurst$getDestroyProgress()')
        if relative.endswith('TestControlServer.java') and 'case "disconnect":' not in text:
            text = text.replace('case "seed-create":', '''case "disconnect":
                if(WurstClient.MC.level != null) WurstClient.MC.level.disconnect(net.minecraft.network.chat.Component.translatable("menu.disconnect"));
                WurstClient.MC.disconnectWithSavingScreen();
                WurstClient.MC.setScreen(new net.minecraft.client.gui.screens.TitleScreen());
                break;
                case "seed-create":''')
        path.write_text(text, encoding='utf-8')
    assets = project/'src/main/resources/assets'
    for path in assets.glob('*/post_effect/*.json'):
        data = json.loads(path.read_text('utf-8-sig'))
        for render_pass in data['passes']:
            shader = render_pass['fragment_shader']
            rows = render_pass.get('uniforms', [])
            if isinstance(rows, dict): continue
            block = 'WurstEffect' if shader == 'wurst:program/target_effect' else ('BlurConfig' if shader.endswith('box_blur') else 'BlitConfig')
            if block == 'BlitConfig': rows = [{'name':'ColorModulate','type':'vec4','values':[1,1,1,1]}]
            for row in rows:
                values = row.pop('values', [0])
                row['value'] = values[0] if row['type'] in ('float','int') else values
            render_pass['uniforms'] = {block: rows} if rows else {}
        path.write_text(json.dumps(data, indent=2)+'\n', encoding='utf-8')
    path = assets/'wurst/shaders/program/target_effect.fsh'
    text = path.read_text('utf-8-sig')
    text = text.replace('uniform vec2 InSize;\nuniform float Time;\nuniform int Mode;', '''layout(std140) uniform SamplerInfo { vec2 OutSize; vec2 InSize; };
layout(std140) uniform WurstEffect { int Mode; float Time; };''')
    path.write_text(text, encoding='utf-8')
