"""26.1 extraction API port from the verified 1.21.11 feature donor."""
from pathlib import Path
import hashlib
import json
import re
import shutil
import zipfile

ROOT=Path(__file__).resolve().parents[2]
JAVA='src/main/java/net/wurstclient/'


def conflicts(rel,text,theirs):
    original=ROOT/'.dsh-tmp/v152-siblings/originals/fabric/versions/26.1'/rel
    short=rel.removeprefix(JAVA)
    if short=='hack/HackList.java':
        source=theirs.decode('utf-8-sig')
        names=set(re.findall(r'public final \w+ (\w+)\s*=',source))
        for field in re.finditer(r'\s*public final \w+ (\w+)\s*=\s*new \w+\(\);',original.read_text('utf-8-sig')):
            if field[1] not in names:
                source=source.replace('\tprivate final TreeMap<String, Hack> hax =',field[0]+'\n\tprivate final TreeMap<String, Hack> hax =')
        return source.encode()
    if short=='mixin/LevelRendererMixin.java':
        return original.read_bytes()
    if rel=='src/main/resources/wurstpenguin.accesswidener':
        rows=original.read_text('utf-8-sig').splitlines()
        rows += [r for r in theirs.decode().splitlines() if r.startswith('accessible') and r not in rows]
        return ('\n'.join(rows)+'\n').encode()
    if rel=='src/main/resources/wurstpenguin.mixins.json':
        config=json.loads(theirs)
        old=json.loads(original.read_text('utf-8-sig'))
        config['client']=list(dict.fromkeys(config['client']+[n for n in old['client'] if n not in {
            'KeyboardHandlerMixin','OpenOpalPostMoveMixin','OpenOpalSkipTickMixin'}]))
        return (json.dumps(config,indent=2)+'\n').encode()
    # These conflicts replace the old menus/HUD/combat implementations with
    # the new feature implementation. The API translation runs afterwards.
    if short.startswith(('altmanager/','clickgui2/','gui/','hacks/','hud2/','options/','serverfinder/')) or short in {
        'mixin/CreativeModeInventoryScreenAccessor.java','mixin/IngameHudMixin.java',
        'mixin/KeyboardMixin.java','mixin/LocalPlayerMixin.java','mixin/ScreenMixin.java',
        'mixin/TitleScreenMixin.java','mixin/WorldRendererMixin.java',
        'util/TestControlServer.java','WurstClient.java','WurstInitializer.java'}:
        return theirs
    return None


def adapt(rel,text):
    if 'onRenderGUI(' in text and '/mixin/' not in rel:
        text=text.replace('import net.minecraft.client.gui.GuiGraphicsExtractor;',
            'import net.wurstclient.util.render.GuiGraphicsExtractor;')
    text=text.replace('import net.minecraft.client.gui.GuiGraphics;','')
    text=re.sub(r'net\.minecraft\.client\.gui\.GuiGraphics\b','net.minecraft.client.gui.GuiGraphicsExtractor',text)
    text=re.sub(r'(?<![./\w])GuiGraphics\b','net.minecraft.client.gui.GuiGraphicsExtractor',text)
    text=text.replace('net/minecraft/client/gui/net.minecraft.client.gui.GuiGraphicsExtractor',
        'net/minecraft/client/gui/GuiGraphicsExtractor')
    # The wrapper still presents the shared API to feature code. Native
    # entrypoints now extract state instead of rendering immediately.
    if rel.endswith('util/render/GuiGraphicsExtractor.java'):
        text=text.replace('import net.minecraft.client.gui.GuiGraphicsExtractor;','')
        text=text.replace('private final GuiGraphicsExtractor inner;', 'private final net.minecraft.client.gui.GuiGraphicsExtractor inner;')
        text=text.replace('GuiGraphicsExtractor(GuiGraphicsExtractor inner)', 'GuiGraphicsExtractor(net.minecraft.client.gui.GuiGraphicsExtractor inner)')
        text=text.replace('public GuiGraphicsExtractor getInner()', 'public net.minecraft.client.gui.GuiGraphicsExtractor getInner()')
        for old,new in {'hLine':'horizontalLine','vLine':'verticalLine','renderOutline':'outline',
            'drawString':'text','drawCenteredString':'centeredText','drawWordWrap':'textWithWordWrap',
            'drawStringWithBackdrop':'textWithBackdrop','renderItem':'item','renderFakeItem':'fakeItem',
            'renderItemDecorations':'itemDecorations','renderDeferredElements':'extractDeferredElements'}.items():
            text=text.replace('inner.'+old+'(', 'inner.'+new+'(')
        text=text.replace('inner.extractDeferredElements();','inner.extractDeferredElements(mouseX, mouseY, partialTicks);')
        for method,arg in [('requestCursor','type'),('applyCursor','window'),('nextStratum',''),('blurBeforeThisStratum','')]:
            text=re.sub(r'(public void '+method+r'\([^)]*\)\s*\{)\s*// not available in 1.21.11, no-op\s*}',
                r'\1\n\t\tinner.'+method+'('+arg+');\n\t}',text)
        # Remove unused historical wrappers with incompatible signatures;
        # they must not pretend to render an entity/map while doing nothing.
        text=re.sub(r'\tpublic void \w+\([^)]*\)\s*\{\s*//[^\n]*(?:no-op)[^\n]*\n\t}\s*','',text)
    else:
        # Only native overloads are renamed; NanoVG helper render methods keep
        # their shared names and are called by these extraction entrypoints.
        text=re.sub(r'(public|protected) void render\((net\.minecraft\.client\.gui\.GuiGraphicsExtractor) ',r'\1 void extractRenderState(\2 ',text)
        text=text.replace('super.render(', 'super.extractRenderState(')
        text=text.replace('.renderWidget(', '.extractRenderState(')
        text=text.replace('protected void renderContents(', 'protected void extractContents(')
        text=re.sub(r'((?:public|protected) void )renderWidget\((net\.minecraft\.client\.gui\.GuiGraphicsExtractor)',
            r'\1extractWidgetRenderState(\2',text)
        text=text.replace('super.extractRenderState(graphics.getInner(), scaledMouseX',
            'super.extractWidgetRenderState(graphics.getInner(), scaledMouseX')
        text=re.sub(r'\.render\((\w+\.getInner\(\))',r'.extractRenderState(\1',text)
        text=text.replace('renderContent(', 'extractContent(')
    text=text.replace('net.minecraft.client.gui.render.state.', 'net.minecraft.client.renderer.state.gui.')
    text=text.replace('net.minecraft.client.renderer.entity.state.', 'net.minecraft.client.renderer.entity.state.')
    text=text.replace('submitBlitToCurrentLayer(', 'addBlitToCurrentLayer(')
    text=text.replace('MC.isSingleplayer()', 'MC.hasSingleplayerServer()')
    text=text.replace('ClickType','ContainerInput').replace('handleInventoryMouseClick(', 'handleContainerInput(')
    text=text.replace('.getType().is(', '.getType().builtInRegistryHolder().is(')
    text=re.sub(r'new net.minecraft.client.input.CharacterEvent\(([^,()]+),[^)]+\)',r'new net.minecraft.client.input.CharacterEvent(\1)',text)
    text=re.sub(r'(CharacterEvent event[^{}]*\{[^{}]*)',lambda m:m[0].replace('event.modifiers()','0'),text)
    if rel.endswith(('AntiKnockbackHack.java','KillauraHack.java','PacketLoggerHack.java','ElytraGrimAccelerateHack.java')):
        text=text.replace('p.getId()', 'p.id()').replace('p.getMovement()', 'p.movement()')
        text=text.replace('packet.getId()', 'packet.id()').replace('packet.getMovement()', 'packet.movement()')
    if rel.endswith('PacketLoggerHack.java'):
        for name in ['ServerboundAcceptTeleportationPacket','ServerboundPongPacket']:
            text=text.replace(name+' p) return "id=" + p.id()',name+' p) return "id=" + p.getId()')
    if rel.endswith('BetterTabHack.java'): text=text.replace('graphics.drawString(', 'graphics.text(')
    if rel.endswith('BetterTooltipsHack.java'):
        text=text.replace('for(ItemStack content : contents.nonEmptyItems())',
            'for(var template : contents.nonEmptyItems())')
        if 'ItemStack content = template.create();' not in text:
            text=text.replace('for(var template : contents.nonEmptyItems())\n\t\t{',
                'for(var template : contents.nonEmptyItems())\n\t\t{\n\t\t\tItemStack content = template.create();')
    if rel.endswith('mixin/EntityMixin.java'):
        # Both vanilla and patched NeoForge 26.1 still call isPushedByFluid.
        text=re.sub(r'\s*// require=0:.*?require = 0\)', '\n        require = 1)',text,flags=re.S)
    if rel.endswith('AutoRefreshTradeHack.java'):
        text=text.replace('interact(MC.player, target, InteractionHand.MAIN_HAND)',
            'interact(MC.player, target, new net.minecraft.world.phys.EntityHitResult(target, target.getEyePosition()), InteractionHand.MAIN_HAND)')
        text=text.replace('interact(MC.player, target, null, InteractionHand.MAIN_HAND)',
            'interact(MC.player, target, new net.minecraft.world.phys.EntityHitResult(target, target.getEyePosition()), InteractionHand.MAIN_HAND)')
    if rel.endswith('mixin/LevelRendererMixin.java') and 'V152Gpu.setProjection' not in text:
        text=text.replace('EventManager.fire(event);', '''var previous = net.wurstclient.util.render.V152Gpu.projection();
        try {
            net.wurstclient.util.render.V152Gpu.setProjection(cameraState.projectionMatrix, false);
            EventManager.fire(event);
            net.wurstclient.WurstClient.INSTANCE.getPostEffectQueue().flush(matrixStack, tickProgress);
            net.wurstclient.util.render.MoonEntityGlow.INSTANCE.flush(matrixStack, tickProgress);
        } finally { net.wurstclient.util.render.V152Gpu.restoreProjection(previous); }''')
    if rel.endswith('ArmorStatusMod.java'):
        text=text.replace('state.pickParticleIcon(net.minecraft.util.RandomSource.create(0))',
            'state.pickParticleMaterial(net.minecraft.util.RandomSource.create(0)).sprite()')
    if rel.endswith('PlayerEspHack.java'):
        for method in ['fill2D','drawBorder2D']:
            text=text.replace('RenderUtils.'+method+'(context,','RenderUtils.'+method+'(context.getInner(),')
    if rel.endswith('ScaffoldIsland.java'):
        text=text.replace('NanoVGUI.item(context,','NanoVGUI.item(new net.wurstclient.util.render.GuiGraphicsExtractor(context),')
    if rel.endswith('NanoVGUI.java'):
        text=text.replace('RenderUtils.drawItem(graphics,','RenderUtils.drawItem(graphics.getInner(),')
    if rel.endswith('GuiMixin.java'):
        text=text.replace('new GUIRenderEvent(context,','new GUIRenderEvent(new net.wurstclient.util.render.GuiGraphicsExtractor(context),')
    text=text.replace('._colorMask(true, true, true, true)', '._colorMask(15)')
    text=text.replace('._colorMask(colorMaskR, colorMaskG, colorMaskB, colorMaskA)',
        '._colorMask((colorMaskR ? 1 : 0) | (colorMaskG ? 2 : 0) | (colorMaskB ? 4 : 0) | (colorMaskA ? 8 : 0))')
    text=text.replace('((GlDevice)RenderSystem.getDevice()).directStateAccess()',
        '((GlDevice)RenderSystem.getDevice().backend).directStateAccess()')
    text=re.sub(r'(\(com.mojang.blaze3d.opengl.GlCommandEncoder\)RenderSystem.getDevice\(\).createCommandEncoder\(\))(?:\.backend)*',
        r'\1.backend',text)
    if rel.endswith('MoonEntityGlow.java'):
        text=text.replace('nodes, mc.getBlockRenderer(), buffers, mc.getAtlasManager(), outlines, buffers, mc.font)',
            'nodes, mc.getModelManager(), buffers, mc.getAtlasManager(), outlines, buffers, mc.font, mc.gameRenderer.getGameRenderState())')
        text=text.replace('cameraState.entityPos = camera;', '')
    if rel.endswith('TestControlServer.java'):
        text=re.sub(r'new net.minecraft.world.level.LevelSettings\("Seed mining QA", net.minecraft.world.level.GameType.CREATIVE, false,\s*net.minecraft.world.Difficulty.PEACEFUL, true, new net.minecraft.world.level.gamerules.GameRules\(net.minecraft.world.flag.FeatureFlags.DEFAULT_FLAGS\), net.minecraft.world.level.WorldDataConfiguration.DEFAULT\)',
            'new net.minecraft.world.level.LevelSettings("Seed mining QA", net.minecraft.world.level.GameType.CREATIVE, new net.minecraft.world.level.LevelSettings.DifficultySettings(net.minecraft.world.Difficulty.PEACEFUL, false, false), true, net.minecraft.world.level.WorldDataConfiguration.DEFAULT)',text)
    text=text.replace('.getDebugOverlay().showDebugScreen()', '.debugEntries.isOverlayVisible()')
    text=text.replace('net.minecraft.client.renderer.entity.state.CameraRenderState','net.minecraft.client.renderer.state.level.CameraRenderState')
    text=text.replace('net.minecraft.client.renderer.state.CameraRenderState','net.minecraft.client.renderer.state.level.CameraRenderState')
    text=text.replace('net.minecraft.client.renderer.LightTexture','net.minecraft.client.renderer.LightmapRenderStateExtractor')
    text=text.replace('@Mixin(LightTexture.class)', '@Mixin(LightmapRenderStateExtractor.class)')
    text=text.replace('renderWithTooltipAndSubtitles','extractRenderStateWithTooltipAndSubtitles')
    text=text.replace('renderBackground(', 'extractBackground(')
    text=text.replace('renderBlurredBackground', 'extractBlurredBackground')
    text=text.replace('renderTransparentBackground', 'extractTransparentBackground')
    text=text.replace('renderPanorama(', 'extractPanorama(')
    text=text.replace('renderMenuBackground(', 'extractMenuBackground(')
    if '/mixin/' in rel:
        if 'net.minecraft.client.gui.GuiGraphicsExtractor' in text:
            text=re.sub(r'method\s*=\s*"render"','method = "extractRenderState"',text)
        for old,new in {'renderHotbar':'extractHotbar','renderEffects':'extractEffects',
            'renderPlayerList':'extractTabList','renderTextureOverlay':'extractTextureOverlay',
            'renderVignette':'extractVignette','displayScoreboardSidebar':'extractScoreboardSidebar',
            'renderWidget':'extractRenderState','renderContents':'extractContents'}.items():
            text=text.replace('"'+old, '"'+new)
        text=text.replace('method = "render(Lnet/minecraft/client/gui/', 'method = "extractRenderState(Lnet/minecraft/client/gui/')
        text=text.replace('method="render",', 'method="extractRenderState",')
        text=text.replace('"renderBackground"','"extractBackground"')
        text=text.replace('"renderItemHotbar', '"extractItemHotbar')
    if rel.endswith('IngameHudMixin.java'):
        text=text.replace('extractScoreboardSidebar(Lnet/minecraft/client/gui/GuiGraphicsExtractor;Lnet/minecraft/world/scores/Objective;)',
            'displayScoreboardSidebar(Lnet/minecraft/client/gui/GuiGraphicsExtractor;Lnet/minecraft/world/scores/Objective;)')
    if rel.endswith('LeavesFovMixin.java'):
        text=text.replace('import net.minecraft.client.renderer.GameRenderer;', 'import net.minecraft.client.Camera;')
        text=text.replace('@Mixin(GameRenderer.class)', '@Mixin(Camera.class)').replace('method = "getFov"','method = "calculateFov"')
    if rel.endswith('WorldRendererMixin.java'):
        # CameraMixin now clears entityRenderState.doesMobEffectBlockSky
        # during extraction. Keep the Moon outline target hook here.
        text=re.sub(r'\s*@Inject\(at = @At\("HEAD"\),\s*method = "doesMobEffectBlockSky.*?\n\t}', '',text,flags=re.S)
    if rel.endswith('util/render/V152GuiBridge.java'):
        text=text.replace('renderer.incrementFrameNumber();','renderer.endFrame();')
        text=text.replace('graphics.renderItem(', 'graphics.item(')
    # Native descriptors cannot use the shared wrapper.
    text=text.replace('Lnet/minecraft/client/gui/GuiGraphics;', 'Lnet/minecraft/client/gui/GuiGraphicsExtractor;')
    return text


def configure(project:Path,donor:Path):
    import sync_v152_fabric1204 as build
    previous=build.TARGET
    try:
        build.TARGET=project
        build.configure_build()
    finally:
        build.TARGET=previous
    seed_version,seed_range,digest={
        '26.2':('2.16.1','>=26.2 <26.3','657a6b998d7dc4e5e2c6b7ebd5e2268cfb79c1b6bc4e5976294c3c5b3fcdf89e'),
        '26.3':('2.16.2','>=26.3 <26.4','1767404627d633e033d73b499cb4db27597eebfedf2c1c160df2dfbc42748614'),
    }.get(project.name,('2.16.0','>=26.1 <26.2','652d0f13339e54b0b1087431e343db18b2b2ea1b4f546215d31da0abd823217d'))
    seed=ROOT/('.dsh-tmp/v152-upstream-seed/seedcrackerX-'+seed_version+'.jar')
    assert hashlib.sha256(seed.read_bytes()).hexdigest()==digest
    (project/'libs/seedcrackerx-deps').mkdir(parents=True,exist_ok=True)
    with zipfile.ZipFile(seed) as z:
        meta=json.loads(z.read('fabric.mod.json'))
        assert meta['depends']['minecraft']==seed_range
        shutil.copy2(seed,project/'libs'/seed.name)
        for row in meta['jars']:
            (project/'libs/seedcrackerx-deps'/Path(row['file']).name).write_bytes(z.read(row['file']))
    p=project/'build.gradle'
    text=re.sub(r'seedcrackerX:[^"\s]+','seedcrackerX:'+seed_version,p.read_text('utf-8'))
    text=text.replace('modImplementation ', 'implementation ')
    # These are plain behavior/resource tests. Loader/JVM integration is
    # checked by runClient; loader-junit forbids tests in a mixin package.
    text=re.sub(r'\s*testImplementation "net.fabricmc:fabric-loader-junit:[^"\n]+"',
        '\n    testImplementation "org.junit.jupiter:junit-jupiter:5.10.2"\n    testRuntimeOnly "org.junit.platform:junit-platform-launcher:1.10.2"',text)
    text=text.replace('include: ["*.jar"]','include: ["cloth-config*.jar"]')
    for row in ['dependencies { runtimeOnly fileTree(dir: "libs/seedcrackerx-deps", include: ["mc_*.jar", "latticg-*.jar"]) }',
        'sourceSets.main.java { exclude "net/wurstclient/clickgui2/component.disabled/**"; exclude "net/wurstclient/clickgui2/screens.disabled/**" }']:
        text=re.sub(r'\n'+re.escape(row)+r'\n?', '\n', text)
        text+='\n'+row+'\n'
    p.write_text(text,encoding='utf-8')
    p=project/'gradle.properties'
    p.write_text(re.sub(r'^mod_version=.*$','mod_version=1.5.2-dev-Fabric-'+project.name,p.read_text(),flags=re.M),encoding='utf-8')
    # These shared UI sources did not all change in the donor's Git diff.
    # Carry their existing native-entrypoint wrappers as one coherent API.
    for folder in ['clickgui2','hud','options','serverfinder']:
        for source in (donor/(JAVA+folder)).rglob('*.java'):
            dest=project/source.relative_to(donor)
            dest.parent.mkdir(parents=True,exist_ok=True)
            dest.write_bytes(source.read_bytes())
    for name in ['events/GUIRenderListener.java','hacks/BetterTabHack.java',
        'hacks/BetterTooltipsHack.java','hacks/NoStatusEffectsHack.java',
        'hacks/EntityCullingHack.java','util/Rotation.java']:
        shutil.copy2(donor/(JAVA+name),project/(JAVA+name))
    # Carry the complete regression suite, including donor files unchanged
    # relative to its Git baseline (the three-way delta alone misses those).
    for source in donor.joinpath('src/test').rglob('*'):
        if source.is_file():
            dest=project/source.relative_to(donor)
            dest.parent.mkdir(parents=True,exist_ok=True)
            shutil.copy2(source,dest)
    p=project/'src/test/java/net/wurstclient/WurstCnNamesTest.java'
    p.write_text(p.read_text().replace('assertEquals(196, names.size())',
        'assertEquals(197, names.size())'),encoding='utf-8')
    p=project/'src/test/java/net/wurstclient/mixin/HudRenderHookTest.java'
    p.write_text(p.read_text().replace('renderTabList(', 'extractTabList(')
        .replace('new GuiGraphicsExtractor(graphics)',
            'new net.wurstclient.util.render.GuiGraphicsExtractor(context)'),encoding='utf-8')
    p=project/(JAVA+'mixin/xray/LiquidBlockRendererMixin.java')
    p.unlink(missing_ok=True)
    p=project/(JAVA+'mixin/xray/FluidRendererMixin.java')
    text=p.read_text('utf-8-sig').replace('Boolean shouldDrawSide = xray.shouldDrawSide(blockState, null);',
        'var event = new net.wurstclient.events.ShouldDrawSideListener.ShouldDrawSideEvent(blockState, null);\n'
        '\t\t net.wurstclient.event.EventManager.fire(event);\n\t\tBoolean shouldDrawSide = event.isRendered();')
    p.write_text(text,encoding='utf-8')
    for p in project.joinpath('src').rglob('*.java'):
        p.write_text(adapt(p.as_posix(),p.read_text('utf-8-sig')),encoding='utf-8')
    from host_netty import configure as netty
    netty(project)
    p=project/'src/main/resources/wurstpenguin.accesswidener'
    text=re.sub(r'net/minecraft/client/gui/GuiGraphics\b','net/minecraft/client/gui/GuiGraphicsExtractor',p.read_text()).replace('net/minecraft/client/gui/render/state/','net/minecraft/client/renderer/state/gui/')
    text+='\naccessible class com/mojang/blaze3d/opengl/GlDevice\n'
    text+='accessible field com/mojang/blaze3d/systems/GpuDevice backend Lcom/mojang/blaze3d/systems/GpuDeviceBackend;\n'
    text+='accessible field com/mojang/blaze3d/systems/CommandEncoder backend Lcom/mojang/blaze3d/systems/CommandEncoderBackend;\n'
    p.write_text('\n'.join(dict.fromkeys(text.splitlines()))+'\n',encoding='utf-8')
    p=project/(JAVA+'WurstClient.java')
    p.write_text(re.sub(r'MC_VERSION = "[^"]+"','MC_VERSION = "'+project.name+'"',p.read_text()),encoding='utf-8')
    from sync_v152_1201 import REPLACED_SCAFFOLD
    for rel in REPLACED_SCAFFOLD: (project/rel).unlink(missing_ok=True)
    p=project/'src/main/resources/wurstpenguin.mixins.json'
    config=json.loads(p.read_text())
    config['client']=[n for n in config['client'] if n not in {'KeyboardHandlerMixin','OpenOpalPostMoveMixin','OpenOpalSkipTickMixin','RenderTypeAccessor','xray.LiquidBlockRendererMixin'}]
    # ClientClockManagerMixin owns the 26.x TimeChanger/NoWeather clock hook.
    replaced={'TimeChangerLevelMixin','xray.ItemBlockRenderTypesMixin','xray.indigo.AbstractTerrainRenderContextMixin','xray.indigo.BlockRenderInfoMixin'}
    config['client']=[n for n in config['client'] if n not in replaced]
    for name in replaced:
        (project/(JAVA+'mixin/'+name.replace('.','/')+'.java')).unlink(missing_ok=True)
    config['client']=list(dict.fromkeys(config['client']+['xray.FluidRendererMixin',
        'xray.SectionCompilerMixin','xray.BlockStateBaseMixin','xray.indigo.AltModelBlockRendererImplMixin']))
    p2=project/(JAVA+'mixin/xray/indigo/AltModelBlockRendererImplMixin.java')
    # Indigo 8.0.0 ships both methods; missing injections must fail loudly.
    p2.write_text(re.sub(r'\s*require = 0,?', '', p2.read_text('utf-8-sig')).replace('@At("RETURN"),)', '@At("RETURN"))'),encoding='utf-8')
    config['compatibilityLevel']='JAVA_25'
    archive_aliases(project,config)
    for name in config['client']:
        relative=JAVA+'mixin/'+name.replace('.','/')+'.java'
        dest=project/relative
        if not dest.exists():
            source=donor/relative
            if not source.exists(): raise ValueError('Missing registered mixin '+name)
            dest.parent.mkdir(parents=True,exist_ok=True)
            dest.write_text(adapt(relative,source.read_text('utf-8-sig')),encoding='utf-8')
    p.write_text(json.dumps(config,indent=2)+'\n',encoding='utf-8')
    (project/(JAVA+'mixin/KeyboardHandlerMixin.java')).unlink(missing_ok=True)
    (project/(JAVA+'mixin/RenderTypeAccessor.java')).unlink(missing_ok=True)
    p=project/'src/main/resources/META-INF/licenses/seedcrackerx/NOTICE.txt'
    p.write_text('SeedCrackerX '+seed_version+', MIT, unmodified upstream release for Minecraft '+seed_range+'.\nhttps://github.com/19MisterX98/SeedcrackerX/releases/tag/'+seed_version+'\nSHA-256: '+digest+'\n',encoding='utf-8')


def archive_aliases(project,config):
    # Unregistered historical Mojmap aliases duplicate the consolidated donor
    # hooks. Preserve their sources outside compilation for local review.
    obsolete='''BackgroundRendererMixin ChatHudMixin ClientCommonPacketListenerImplMixin
ClientLevelMixin ClientPacketListenerMixin ClientTelemetryManagerMixin CommandSuggestionsMixin
ConnectionMixin ContainerScreenMixin CreativeModeInventoryScreenMixin DeltaTrackerTimerMixin
DirectJoinServerScreenMixin DownloadQueueMixin EnvironmentAttributeSystemMixin
GuiGraphicsExtractorAccessor InGameOverlayRendererMixin InventoryAccessor JoinMultiplayerScreenMixin
KeyBindsListMixin KeyMappingMixin LevelMixin MobEffectInstanceMixin MobEntityRendererMixin
MouseMixin MultiPlayerGameModeMixin OptionInstanceMixin PackSelectionScreenMixin PauseScreenMixin
PlayerInventoryMixin SectionOcclusionGraphMixin ServerNameResolverMixin SkinManagerMixin
StorageMixin StringDecomposerMixin'''.split()
    for name in obsolete:
        # 26.3 still uses these target-specific hooks (account-scoped pack
        # cache and moon phase). Neither duplicates the clock manager hook.
        if project.name == '26.3' and name in {'DownloadQueueMixin', 'EnvironmentAttributeSystemMixin'}:
            continue
        if name in config['client']: raise ValueError('Registered historical alias '+name)
        source=project/(JAVA+'mixin/'+name+'.java')
        if source.exists():
            archive=ROOT/'.dsh-tmp/v152-obsolete-aliases'/project.name/source.name
            archive.parent.mkdir(parents=True,exist_ok=True)
            if not archive.exists(): shutil.copy2(source,archive)
            source.unlink()
