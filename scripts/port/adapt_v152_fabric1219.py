"""1.21.9 event-object input and submit-node renderer migration."""
from pathlib import Path
import hashlib
import json
import re
import zipfile

ROOT = Path(__file__).resolve().parents[2]
JAVA = 'src/main/java/net/wurstclient/'


def conflicts(rel, text, theirs):
    short = rel.removeprefix(JAVA)
    if short in {
        'altmanager/screens/AltEditorScreen.java', 'altmanager/screens/AltManagerScreen.java',
        'clickgui2/ClickGuiScreen.java', 'clickgui2/NavigatorScreen.java',
        'gui/title/WurstTitleButton.java', 'hacks/AutoArmorPlusHack.java',
        'hacks/ElytraGrimAccelerateHack.java', 'hacks/ReplenishHack.java',
        'mixin/LevelRendererMixin.java', 'mixin/MouseHandlerMixin.java',
        'mixin/ScreenEffectRendererMixin.java'}:
        return theirs
    if short in {'hud2/mods/MouseStrokesMod.java', 'options/KeybindManagerScreen.java',
        'options/KeybindProfilesScreen.java', 'options/WurstOptionsScreen.java',
        'serverfinder/CleanUpScreen.java', 'WurstClient.java'}:
        return re.sub(r'<<<<<<<[^\n]*\n(.*?)=======\n(.*?)>>>>>>>[^\n]*\n?',
            lambda m:m[2], text, flags=re.S).encode()
    if rel == 'src/main/resources/wurstpenguin.mixins.json':
        return theirs
    return None


def adapt(rel, text):
    # Keep primitive overloads for custom controls; native Screen entry points
    # forward the complete event and preserve their original coordinate logic.
    signatures = {
        'mouseClicked': (r'double (\w+),\s*double (\w+),\s*int (\w+)',
            'net.minecraft.client.input.MouseButtonEvent event, boolean doubleClick',
            'event.x(), event.y(), event.button()'),
        'mouseReleased': (r'double (\w+),\s*double (\w+),\s*int (\w+)',
            'net.minecraft.client.input.MouseButtonEvent event',
            'event.x(), event.y(), event.button()'),
        'mouseDragged': (r'double (\w+),\s*double (\w+),\s*int (\w+),\s*double (\w+),\s*double (\w+)',
            'net.minecraft.client.input.MouseButtonEvent event, double dragX, double dragY',
            'event.x(), event.y(), event.button(), dragX, dragY'),
        'keyPressed': (r'int (\w+),\s*int (\w+),\s*int (\w+)',
            'net.minecraft.client.input.KeyEvent event',
            'event.key(), event.scancode(), event.modifiers()'),
        'keyReleased': (r'int (\w+),\s*int (\w+),\s*int (\w+)',
            'net.minecraft.client.input.KeyEvent event',
            'event.key(), event.scancode(), event.modifiers()'),
        'charTyped': (r'char (\w+),\s*int (\w+)',
            'net.minecraft.client.input.CharacterEvent event',
            '(char)event.codepoint(), event.modifiers()'),
    }
    for name,(pattern, signature,args) in signatures.items():
        text = re.sub(r'@Override\s+public boolean '+name+r'\('+pattern+r'\)',
            lambda m: '@Override public boolean '+name+'('+signature+') { return '+name+'('+args+'); }\n'
                +'public boolean '+name+'('+m[0].split('(',1)[1], text)
    # Only native controls and super calls use the new vanilla signatures.
    for name in ('keyPressed', 'keyReleased'):
        text = re.sub(r'((?:super|nameOrEmailBox|passwordBox|searchBox|search)\.'+name+r')\((\w+),\s*(\w+),\s*(\w+)\)',
            r'\1(new net.minecraft.client.input.KeyEvent(\2, \3, \4))',text)
    text = re.sub(r'((?:super|nameOrEmailBox|passwordBox|searchBox|search)\.charTyped)\((\w+),\s*(\w+)\)',
        r'\1(new net.minecraft.client.input.CharacterEvent(\2, \3))',text)
    for name in ('mouseClicked','mouseReleased','mouseDragged'):
        tail = ', false' if name=='mouseClicked' else ''
        ending = r'(?=,)' if name=='mouseDragged' else r'(?=\))'
        text = re.sub(r'((?:super|nameOrEmailBox|passwordBox|searchBox)\.'+name+r')\((\w+),\s*(\w+),\s*(\w+)'+ending,
            r'\1(new net.minecraft.client.input.MouseButtonEvent(\2, \3, new net.minecraft.client.input.MouseButtonInfo(\4, 0))'+tail,text)
    if rel.endswith('KeyboardMixin.java'):
        text = text.replace('keyPress(JIIII)V', 'keyPress(JILnet/minecraft/client/input/KeyEvent;)V')
        text = text.replace('long windowHandle, int key, int scancode, int action,\n\t\tint modifiers, CallbackInfo ci',
            'long windowHandle, int action, net.minecraft.client.input.KeyEvent event, CallbackInfo ci')
        text = text.replace('new KeyPressEvent(key, scancode, action, modifiers)',
            'new KeyPressEvent(event.key(), event.scancode(), action, event.modifiers())')
    if rel.endswith('MouseHandlerMixin.java'):
        text=text.replace('onPress(JIII)V','onButton(JLnet/minecraft/client/input/MouseButtonInfo;I)V')
        text=text.replace('onButton(JIII)V','onButton(JLnet/minecraft/client/input/MouseButtonInfo;I)V')
        text=re.sub(r'long (window|windowHandle), int button, int action,\s*int (mods|modifiers),',
            r'long \1, net.minecraft.client.input.MouseButtonInfo info, int action,',text)
        text=text.replace('MouseButtonPressEvent(button, action)', 'MouseButtonPressEvent(info.button(), action)')
        text=text.replace('MouseButtonEvent(button, action)', 'MouseButtonEvent(info.button(), action)')
    if rel.endswith('LevelRendererMixin.java'):
        text=text.replace('@Mixin(LevelRenderer.class)', '@Mixin(LevelRenderer.class)')
        text=text.replace('public class LevelRendererMixin {', '''public class LevelRendererMixin {
  @org.spongepowered.asm.mixin.Shadow @org.spongepowered.asm.mixin.Final
  private net.minecraft.client.renderer.SubmitNodeStorage submitNodeStorage;''')
        text=text.replace('Lorg/joml/Matrix4f;Lorg/joml/Matrix4f;Lcom/mojang',
            'Lorg/joml/Matrix4f;Lorg/joml/Matrix4f;Lorg/joml/Matrix4f;Lcom/mojang')
        text=text.replace('GpuBufferSlice fogBuffer,', 'Matrix4f culling, GpuBufferSlice fogBuffer,')
        text=text.replace('PoseStack matrices = new PoseStack();',
            'net.wurstclient.util.RenderUtils.setSubmitNodeStorage(submitNodeStorage);\n    PoseStack matrices = new PoseStack();')
    if rel.endswith('LeavesPlayerRendererMixin.java'):
        text=text.replace('entity.player.PlayerRenderer','entity.player.AvatarRenderer').replace('PlayerRenderState','AvatarRenderState')
        text=text.replace('@Mixin(PlayerRenderer.class)','@Mixin(AvatarRenderer.class)')
        text=text.replace('Lnet/minecraft/client/player/AbstractClientPlayer;', 'Lnet/minecraft/world/entity/Avatar;')
        text=text.replace('leaves$standingFlight(AbstractClientPlayer player,', 'leaves$standingFlight(net.minecraft.world.entity.Avatar player,')
    if rel.endswith('NeoRavenReceivingLevelMixin.java'):
        text=text.replace('ReceivingLevelScreen.class','LevelLoadingScreen.class')
    if rel.endswith('MultiplayerScreenMixin.java'):
        text=text.replace(' @Shadow public void joinSelectedServer() {}\n @Shadow public void setSelected(ServerSelectionList.Entry entry) {}', '')
        text=text.replace('entry->setSelected((ServerSelectionList.Entry)entry),this::joinSelectedServer,',
            'entry->serverSelectionList.setSelected((ServerSelectionList.Entry)entry),\n   ()->{var selected=serverSelectionList.getSelected(); if(selected!=null)selected.join();},')
        text=text.replace('private void opai$key(int key,int scan,int mods,CallbackInfoReturnable<Boolean> cir){',
            'private void opai$key(net.minecraft.client.input.KeyEvent event,CallbackInfoReturnable<Boolean> cir){\n int key=event.key();')
    if rel.endswith(('MultiplayerScreenMixin.java','SelectWorldScreenMixin.java')):
        text=text.replace('@Inject(method="render",at=@At("HEAD"),cancellable=true)\n private void opai$render(GuiGraphics g,int x,int y,float delta,CallbackInfo ci){',
            '@Override public void render(GuiGraphics g,int x,int y,float delta){')
        text=text.replace('if(opai$menu==null)return;', 'if(opai$menu==null){super.render(g,x,y,delta);return;}')
        text=text.replace('opai$menu.render(new net.wurstclient.util.render.GuiGraphicsExtractor(g),x,y);ci.cancel();',
            'opai$menu.render(new net.wurstclient.util.render.GuiGraphicsExtractor(g),x,y);')
        if rel.endswith('SelectWorldScreenMixin.java'):
            text=text.replace('if(opai$menu!=null){opai$menu.render(',
                'if(opai$menu==null)super.render(g,x,y,delta);else {opai$menu.render(')
    if rel.endswith('ScreenMixin.java'):
        text=text.replace('method = "renderWithTooltip"', 'method = "renderWithTooltipAndSubtitles"')
    if rel.endswith('ChatScreenMixin.java'):
        text=text.replace('mouseClicked(DDI)Z', 'mouseClicked(Lnet/minecraft/client/input/MouseButtonEvent;Z)Z')
        text=re.sub(r'private void ([\w$]+)\(double (\w+), double (\w+), int button,\s*CallbackInfoReturnable<Boolean> cir\)\s*\{',
            r'private void \1(net.minecraft.client.input.MouseButtonEvent event, boolean doubleClick, CallbackInfoReturnable<Boolean> cir) {\n double \2=event.x(), \3=event.y(); int button=event.button();',text)
    if rel.endswith('IslandContainerScreenMixin.java'):
        for name,extra in [('Click',', boolean doubleClick'),('Release','')]:
            text=re.sub(r'private void wurst\$island'+name+r'\(double x, double y, int button, CallbackInfoReturnable<Boolean> cir\)\s*\{',
                'private void wurst$island'+name+'(net.minecraft.client.input.MouseButtonEvent event'+extra+', CallbackInfoReturnable<Boolean> cir) {\n double x=event.x(), y=event.y(); int button=event.button();',text)
        text=re.sub(r'private void wurst\$islandDrag\(double x, double y, int button, double dx, double dy,\s*CallbackInfoReturnable<Boolean> cir\)\s*\{',
            'private void wurst$islandDrag(net.minecraft.client.input.MouseButtonEvent event, double dx, double dy, CallbackInfoReturnable<Boolean> cir) {\n int button=event.button();',text)
    if rel.endswith('ScreenEffectRendererMixin.java'):
        text=text.replace('renderFire(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;)V',
            'renderFire(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;Lnet/minecraft/client/renderer/texture/TextureAtlasSprite;)V')
    text=text.replace('.viewWidth','.width').replace('.viewHeight','.height')
    text=text.replace('.level().isClientSide\n','.level().isClientSide()\n')
    text=text.replace('MC.getWindow().getWindow(), InputConstants.getKey(key)', 'MC.getWindow(), InputConstants.getKey(key)')
    text=text.replace('Screen.hasShiftDown()', 'net.wurstclient.util.render.V152Input.shift()')
    text=re.sub(r'(?<![.\w])hasShiftDown\(\)', 'net.wurstclient.util.render.V152Input.shift()',text)
    text=text.replace('InputConstants.getKey(keyCode, scanCode)', 'InputConstants.getKey(new net.minecraft.client.input.KeyEvent(keyCode, scanCode, modifiers))')
    if rel.endswith(('AntiKnockbackHack.java','PacketLoggerHack.java','ElytraGrimAccelerateHack.java')):
        for axis in 'XYZ':
            text=text.replace('.get'+axis+'a() / 8000.0', '.getMovement().'+axis.lower())
            text=text.replace('.get'+axis+'a()', '.getMovement().'+axis.lower())
    text=text.replace('.getSkin().texture()', '.getSkin().body().texturePath()')
    text=text.replace('.onPress();', '.onPress(new net.minecraft.client.input.KeyEvent(257, 0, 0));')
    text=text.replace('public void onPress()', 'public void onPress(net.minecraft.client.input.InputWithModifiers input)')
    text=text.replace('super.mouseClicked(mouseX - canvasOffset, mouseY, button)',
        'super.mouseClicked(new net.minecraft.client.input.MouseButtonEvent(mouseX - canvasOffset, mouseY, new net.minecraft.client.input.MouseButtonInfo(button, 0)), false)')
    text=text.replace('WorldListEntry::canJoin', 'WorldListEntry::canInteract').replace('world.canJoin()', 'world.canInteract()')
    text=text.replace('lan.getServerData()', '((net.wurstclient.mixin.OpaiLanServerEntryAccessor)(Object)lan).opai$serverData()')
    text=text.replace('entry.render(g,0,0,0,300,36,-100,-100,false,delta)', 'entry.renderContent(g,-100,-100,false,delta)')
    text=re.sub(r'public void render\(net.minecraft.client.gui.GuiGraphics graphics, int index, int y, int x,\s*int entryWidth, int entryHeight, int mouseX, int mouseY,\s*boolean hovered, float partialTicks\)',
        'public void renderContent(net.minecraft.client.gui.GuiGraphics graphics, int mouseX, int mouseY, boolean hovered, float partialTicks)',text)
    if rel.endswith('AltEditorScreen.java'):
        text=text.replace('passwordBox.setFormatter((text, start) ->\n\t\t\tFormattedCharSequence.forward("*".repeat(text.length()), Style.EMPTY))',
            'passwordBox.addFormatter((text, start) -> FormattedCharSequence.forward("*".repeat(text.length()), Style.EMPTY))')
    if rel.endswith('LoginAccountsScreen.java'):
        text=text.replace('password.setFormatter(', 'password.addFormatter(').replace('"*".repeat(text.length())','"*".repeat(text.length())')
    if rel.endswith('TestControlServer.java'):
        text=text.replace('new net.minecraft.client.gui.screens.ReceivingLevelScreen(() -> false, net.minecraft.client.gui.screens.ReceivingLevelScreen.Reason.OTHER)',
            'new net.minecraft.client.gui.screens.LevelLoadingScreen(new net.minecraft.client.multiplayer.LevelLoadTracker(), net.minecraft.client.gui.screens.LevelLoadingScreen.Reason.OTHER)')
        text=re.sub(r'new net.minecraft.client.gui.screens.LevelLoadingScreen\(\s*net.minecraft.server.level.progress.StoringChunkProgressListener.create\(2\)\)',
            'new net.minecraft.client.gui.screens.LevelLoadingScreen(new net.minecraft.client.multiplayer.LevelLoadTracker(), net.minecraft.client.gui.screens.LevelLoadingScreen.Reason.OTHER)',text)
        text=text.replace('new net.minecraft.client.gui.screens.ChatScreen("")','new net.minecraft.client.gui.screens.ChatScreen("", false)')
        text=text.replace('MC.screen.charTyped(c, 0)', 'MC.screen.charTyped(new net.minecraft.client.input.CharacterEvent(c, 0))')
        for name in ['mouseClicked','mouseReleased']:
            text=text.replace('MC.screen.'+name+'(x, y, 0)', 'MC.screen.'+name+'(new net.minecraft.client.input.MouseButtonEvent(x, y, new net.minecraft.client.input.MouseButtonInfo(0, 0))'+(', false)' if name=='mouseClicked' else ')'))
        text=text.replace('MC.screen.keyPressed(Integer.parseInt(query.getOrDefault("key", "256")), 0, 0)',
            'MC.screen.keyPressed(new net.minecraft.client.input.KeyEvent(Integer.parseInt(query.getOrDefault("key", "256")), 0, 0))')
    if rel.endswith('MoonEntityGlow.java'):
        # Feature render types resolve Minecraft's main target themselves.
        # Keep their regular model pass inside the mask as well as outlines.
        text=text.replace('      capturing = true;\n      try {',
            '      capturing = true;\n      try (var targetScope = V152Gpu.target(mask)) {')
        text=text.replace('new OutlineBufferSource(buffers)', 'new OutlineBufferSource()')
        text=text.replace('outlines.setColor(255, 255, 255, 255)', 'outlines.setColor(0xFFFFFFFF)')
        text=text.replace('  private TextureTarget mask, blur;', '''  private final SubmitNodeStorage nodes = new SubmitNodeStorage();
  private net.minecraft.client.renderer.feature.FeatureRenderDispatcher features;
  private TextureTarget mask, blur;''')
        text=text.replace('        for (var entity : selected) {', '''        if (features == null) features = new net.minecraft.client.renderer.feature.FeatureRenderDispatcher(
            nodes, mc.getBlockRenderer(), buffers, mc.getAtlasManager(), outlines, buffers, mc.font);
        var cameraState = new net.minecraft.client.renderer.state.CameraRenderState();
        cameraState.pos = camera;
        cameraState.entityPos = camera;
        cameraState.blockPos = net.minecraft.core.BlockPos.containing(camera);
        cameraState.orientation = mc.gameRenderer.getMainCamera().rotation();
        cameraState.initialized = true;
        for (var entity : selected) {''')
        text=text.replace('mc.getEntityRenderDispatcher().render(entity, 0, 0, 0, partialTicks, matrices, maskOnly, 15728880);', '''var state = mc.getEntityRenderDispatcher().extractEntity(entity, partialTicks);
            state.lightCoords = 15728880;
            state.outlineColor = 0xFFFFFFFF;
            state.nameTag = null;
            state.displayFireAnimation = false;
            state.shadowPieces.clear();
            mc.getEntityRenderDispatcher().submit(state, cameraState, 0, 0, 0, matrices, nodes);''')
        text=text.replace('        outlines.endOutlineBatch();', '''        features.renderAllFeatures();
        buffers.endBatch();
        outlines.endOutlineBatch();
        features.endFrame();
        nodes.clear();''')
        text=text.replace('      queued.clear();','      nodes.clear();\n      queued.clear();')
        text=text.replace('  public void close() {','  public void close() {\n    if (features != null) features.close();\n    features = null;\n    nodes.clear();')
    if rel.endswith('NanoVgBlitter.java') and 'state.nextStratum();' not in text:
        # Full-screen snapshots overlap by definition. Keeping them in one
        # node lets GuiRenderer sort by texture and cover the foreground.
        text=text.replace('state.submitBlitToCurrentLayer(new BlitRenderState(',
            'state.nextStratum();\n\t\tstate.submitBlitToCurrentLayer(new BlitRenderState(')
    return text


def configure(project: Path, donor: Path):
    from adapt_v152_fabric1218 import configure as common
    common(project, donor)
    # Mojang removed post/blit.vsh and post/blur.vsh in this release.
    # All full-screen post passes now share core/screenquad.vsh.
    for path in (project/'src/main/resources/assets').glob('*/post_effect/*.json'):
        data=json.loads(path.read_text('utf-8-sig'))
        for render_pass in data.get('passes', []):
            if render_pass.get('vertex_shader') in ('minecraft:post/blit','minecraft:post/blur'):
                render_pass['vertex_shader']='minecraft:core/screenquad'
        path.write_text(json.dumps(data,indent=2)+'\n',encoding='utf-8')
    seed=ROOT/'.dsh-tmp/v152-upstream-seed/seedcrackerX-2.15.5.jar'
    digest='0d3860596669f804dd81446b971ce3a8f77d5f69827f2e1a8b3c1fb1ee3fcc81'
    assert hashlib.sha256(seed.read_bytes()).hexdigest()==digest
    (project/'libs/seedcrackerX-2.15.4.jar').unlink(missing_ok=True)
    (project/'libs'/seed.name).write_bytes(seed.read_bytes())
    with zipfile.ZipFile(seed) as archive:
        meta=json.loads(archive.read('fabric.mod.json'))
        assert meta['depends']['minecraft']=='>=1.21.9 <1.22'
        names={Path(row['file']).name for row in meta['jars']}
        # Remove only previously extracted donor dependencies, retaining any
        # unrelated local library. Their upstream archive is the backup.
        with zipfile.ZipFile(donor/'libs/seedcrackerX-2.15.4.jar') as old:
            for row in json.loads(old.read('fabric.mod.json'))['jars']:
                name=Path(row['file']).name
                if name not in names:
                    (project/'libs/seedcrackerx-deps'/name).unlink(missing_ok=True)
        for row in meta['jars']:
            (project/'libs/seedcrackerx-deps'/Path(row['file']).name).write_bytes(archive.read(row['file']))
    path=project/'build.gradle'
    path.write_text(path.read_text('utf-8').replace('seedcrackerX:2.15.4','seedcrackerX:2.15.5'),encoding='utf-8')
    notice=project/'src/main/resources/META-INF/licenses/seedcrackerx/NOTICE.txt'
    notice.write_text('SeedCrackerX 2.15.5, MIT, unmodified upstream release for Minecraft 1.21.9.\n'
        'https://github.com/19MisterX98/SeedcrackerX/releases/tag/2.15.5\nSHA-256: '+digest+'\n',encoding='utf-8')
    template=Path(__file__).parent/'templates/fabric1219'
    for source in template.rglob('*.java'):
        dest=project/JAVA/source.relative_to(template)
        dest.parent.mkdir(parents=True,exist_ok=True)
        dest.write_bytes(source.read_bytes())
    p=project/(JAVA+'util/render/V152Input.java')
    s=p.read_text('utf-8')
    if 'boolean shift()' not in s:
        s=s.replace('public final class V152Input {', '''public final class V152Input {
  public static boolean shift() {
    var window = net.wurstclient.WurstClient.MC.getWindow();
    return com.mojang.blaze3d.platform.InputConstants.isKeyDown(window, 340)
        || com.mojang.blaze3d.platform.InputConstants.isKeyDown(window, 344);
  }''')
    p.write_text(s,encoding='utf-8')
    p=project/'src/main/resources/wurstpenguin.mixins.json'
    config=json.loads(p.read_text('utf-8'))
    config['client']=[n for n in config['client'] if n!='NeoRavenReceivingLevelMixin']
    (project/(JAVA+'mixin/NeoRavenReceivingLevelMixin.java')).unlink(missing_ok=True)
    config['client'] += ['OpaiLanServerEntryAccessor', 'AvatarMixin', 'ClientAvatarStateMixin']
    config['client']=list(dict.fromkeys(config['client']))
    p.write_text(json.dumps(config,indent=2)+'\n',encoding='utf-8')
