"""26.2 screen manager/Hud split and staged feature-render phases."""
from pathlib import Path
import json
import re

ROOT=Path(__file__).resolve().parents[2]
JAVA='src/main/java/net/wurstclient/'


def conflicts(rel,text,theirs):
    short=rel.removeprefix(JAVA)
    if short=='mixin/SubmitNodeCollectionMixin.java':
        return (ROOT/'.dsh-tmp/v152-siblings/originals/fabric/versions/26.2'/rel).read_bytes()
    if short.startswith(('altmanager/','clickgui2/','gui/','hacks/')) or short in {
        'keybinds/KeybindProcessor.java','macros/MacroManager.java','WurstClient.java',
        'mixin/IngameHudMixin.java','mixin/LocalPlayerMixin.java',
        'mixin/ScreenEffectRendererMixin.java','mixin/xray/FluidRendererMixin.java'}:
        return theirs
    if rel=='src/main/resources/wurstpenguin.mixins.json':
        config=json.loads(theirs)
        config['client']=list(dict.fromkeys(config['client']+['SubmitNodeCollectionMixin',
            'BaritoneGuiCompatibilityMixin']))
        return (json.dumps(config,indent=2)+'\n').encode()
    return None


def adapt(rel,text):
    rel=rel.replace('\\','/')
    # Assignments are handled before reads. Temporary portal/InvWalk swaps do
    # not invoke the public screen lifecycle or reinitialize the screen.
    text=re.sub(r'\b(MC|mc|client|minecraft)\.screen\s*=(?!=)\s*([^;]+);',
        r'\1.gui.setScreen(\2);',text)
    text=re.sub(r'\b(MC|mc|client|minecraft)\.screen\b',r'\1.gui.screen()',text)
    text=re.sub(r'\b(MC|mc|client|minecraft)\.setScreen\(',r'\1.setScreenAndShow(',text)
    text=text.replace('Minecraft.getInstance().screen','Minecraft.getInstance().gui.screen()')
    text=text.replace('Minecraft.getInstance().setScreen(', 'Minecraft.getInstance().setScreenAndShow(')
    # Hud owns the former Gui APIs; Gui is now the screen/overlay manager.
    text=re.sub(r'\.gui\.(getChat|getBossOverlay|getTabList|getSpectatorGui|setOverlayMessage|setTitle|setSubtitle|setTimes|clear|resetTitleTimes|setNowPlaying|setTitleAnimationTimes|clearCache)\(',r'.gui.hud.\1(',text)
    text=text.replace('net.minecraft.client.gui.Gui;', 'net.minecraft.client.gui.Hud;')
    text=text.replace('@Mixin(Gui.class)','@Mixin(Hud.class)')
    text=text.replace('Lnet/minecraft/client/gui/Gui;', 'Lnet/minecraft/client/gui/Hud;')
    text=text.replace('import com.mojang.blaze3d.vertex.Tesselator;', '')
    text=text.replace('import net.minecraft.client.renderer.MultiBufferSource;', '')
    text=text.replace('com.mojang.blaze3d.vertex.VertexFormat.Mode', 'com.mojang.blaze3d.PrimitiveTopology')
    text=text.replace('VertexFormat.Mode', 'com.mojang.blaze3d.PrimitiveTopology')
    text=text.replace('com.mojang.blaze3d.vertex.com.mojang.blaze3d.PrimitiveTopology', 'com.mojang.blaze3d.PrimitiveTopology')
    text=text.replace('com.mojang.blaze3d.vertex.Tesselator.getInstance().begin(', 'new com.mojang.blaze3d.vertex.BufferBuilder(new com.mojang.blaze3d.vertex.ByteBufferBuilder(4096), ')
    text=text.replace('Tesselator.getInstance().begin(', 'new BufferBuilder(new com.mojang.blaze3d.vertex.ByteBufferBuilder(4096), ')
    text=re.sub(r'new TextureTarget\(("[^"\n]+",\s*\w+,\s*\w+,\s*(?:true|false))\)',r'new TextureTarget(\1, com.mojang.blaze3d.GpuFormat.RGBA8_UNORM)',text)
    text=text.replace('._enableBlend()', '._enableBlend(0)').replace('._disableBlend()', '._disableBlend(0)')
    text=text.replace('.options.hideGui', '.gui.hud.isHidden()')
    text=text.replace('.getOverlay()', '.gui.overlay()')
    text=text.replace('.setScreen(', '.setScreenAndShow(') if '/clickgui2/screens/' in rel else text
    text=text.replace('EntityType.LIGHTNING_BOLT', 'net.minecraft.world.entity.EntityTypes.LIGHTNING_BOLT')
    text=text.replace('RenderSystem.getModelViewMatrix()', 'RenderSystem.getModelViewMatrixCopy()')
    text=text.replace('target.getColorTexture(), 0, target.getDepthTexture(), 1.0', 'target.getColorTexture(), new org.joml.Vector4f(0,0,0,0), target.getDepthTexture(), 1.0')
    text=text.replace('clearColorTexture(target.getColorTexture(), 0)', 'clearColorTexture(target.getColorTexture(), new org.joml.Vector4f(0,0,0,0))')
    if rel.endswith('V152Gpu.java'):
        text=text.replace('return ((GlTexture)target.getColorTexture()).getFbo(\n        ((GlDevice)RenderSystem.getDevice().backend).directStateAccess(), target.getDepthTexture());',
            'var device = (GlDevice)RenderSystem.getDevice().backend;\n    return device.frameBufferCache().getFbo(device.directStateAccess(), java.util.List.of((GlTexture)target.getColorTexture()), (GlTexture)target.getDepthTexture());')
        for semantic in ['POSITION','UV0','COLOR']:
            text=text.replace('VertexFormatElement.'+semantic, 'DefaultVertexFormat.'+semantic+'_SEMANTIC_NAME')
        text=text.replace('VertexFormatElement element,','String element,')
        text=text.replace('format.getOffset(element)', 'format.getElement(element).offset()')
        text=text.replace('state.mode()', 'state.primitiveTopology()')
        text=text.replace('VertexFormat.IndexType.SHORT', 'com.mojang.blaze3d.IndexType.SHORT')
    if rel.endswith('V152GuiBridge.java'):
        text=text.replace('renderer.render(mc.gameRenderer.fogRenderer.getBuffer(FogRenderer.FogMode.NONE))', 'renderer.render()')
    text=text.replace('.getMainCamera()', '.mainCamera()')
    text=text.replace('.getGameRenderState()', '.gameRenderState()')
    text=text.replace('.getMainRenderTarget()', '.gameRenderer.mainRenderTarget()')
    text=text.replace('Minecraft;overlay:Lnet/minecraft/client/gui/screens/Overlay;',
        'gui/Gui;overlay()Lnet/minecraft/client/gui/screens/Overlay;')
    if rel.endswith('MinecraftClientMixin.java'):
        # Target redirection belongs to GameRenderer in 26.2, installed below.
        text=re.sub(r'\s*@Inject\(method = "getMainRenderTarget".*?\n    }\n','\n',text,flags=re.S)
        text=text.replace('@At(value = "FIELD",\n\t\ttarget = "Lnet/minecraft/client/gui/Hud;overlay()',
            '@At(value = "INVOKE",\n\t\ttarget = "Lnet/minecraft/client/gui/Gui;overlay()')
        text=text.replace('Lnet/minecraft/client/gui/Hud;overlay()', 'Lnet/minecraft/client/gui/Gui;overlay()')
    if rel.endswith('LocalPlayerMixin.java'):
        text=text.replace('Lnet/minecraft/client/gui/Hud;screen()', 'Lnet/minecraft/client/gui/Gui;screen()')
        text=text.replace('@At(value = "FIELD",\n\t\t\ttarget = "Lnet/minecraft/client/Minecraft;screen:Lnet/minecraft/client/gui/screens/Screen;",',
            '@At(value = "INVOKE",\n\t\t\ttarget = "Lnet/minecraft/client/gui/Gui;screen()Lnet/minecraft/client/gui/screens/Screen;",')
        text=text.replace('minecraft.setScreenAndShow(null)', 'minecraft.gui.setScreen(null)')
        text=text.replace('minecraft.setScreenAndShow(tempCurrentScreen)', 'minecraft.gui.setScreen(tempCurrentScreen)')
        text=text.replace('Screen;",\n\t\t\topcode = Opcodes.GETFIELD,', 'Screen;",')
    if rel.endswith('MoonEntityGlow.java'):
        text=text.replace('private final MultiBufferSource.BufferSource buffers =\n      MultiBufferSource.immediate(new ByteBufferBuilder(65536));\n  private final OutlineBufferSource outlines = new OutlineBufferSource();',
            'private RenderBuffers buffers;')
        text=text.replace('        outlines.setColor(0xFFFFFFFF);','')
        if 'if (buffers == null)' not in text:
            text=text.replace('if (features == null) features =', 'if (buffers == null) buffers = new RenderBuffers(1);\n        if (features == null) features =')
        text=text.replace('nodes, mc.getModelManager(), buffers, mc.getAtlasManager(), outlines, buffers, mc.font, mc.gameRenderer.gameRenderState()',
            'buffers, mc.getModelManager(), mc.getAtlasManager(), mc.font, mc.gameRenderer.gameRenderState()')
        text=text.replace('features.renderAllFeatures();\n        buffers.endBatch();\n        outlines.endOutlineBatch();\n        features.endFrame();',
            'features.renderAllFeatures(nodes);\n        buffers.endFrame();')
        text=text.replace('nodes.clear();','nodes.getSubmitsPerOrder().clear();')
        if 'buffers.close()' not in text:
            text=text.replace('features = null;','features = null;\n    if (buffers != null) buffers.close();\n    buffers = null;')
        text=re.sub(r'(        if \(buffers == null\) buffers = new RenderBuffers\(1\);\n)+',r'        if (buffers == null) buffers = new RenderBuffers(1);\n',text)
        text=re.sub(r'(    if \(buffers != null\) buffers.close\(\);\n    buffers = null;\n)+',r'    if (buffers != null) buffers.close();\n    buffers = null;\n',text)
    # Keep native allocation ownership explicit after Tesselator removal.
    if rel.endswith('PostEffectQueue.java') and 'var vertices =' not in text:
        text=text.replace('try\n\t\t{\n\t\t\tV152Gpu.setProjection', 'try(var vertices = new com.mojang.blaze3d.vertex.ByteBufferBuilder(4096))\n\t\t{\n\t\t\tV152Gpu.setProjection')
        text=text.replace('new BufferBuilder(new com.mojang.blaze3d.vertex.ByteBufferBuilder(4096),', 'new BufferBuilder(vertices,')
    if rel.endswith('TargetShaderHack.java') and 'var vertices =' not in text:
        text=text.replace('        var buffer = new com.mojang.blaze3d.vertex.BufferBuilder(new com.mojang.blaze3d.vertex.ByteBufferBuilder(4096),', '        try(var vertices = new com.mojang.blaze3d.vertex.ByteBufferBuilder(4096)) {\n        var buffer = new com.mojang.blaze3d.vertex.BufferBuilder(vertices,')
        text=text.replace('V152Gpu.draw(buffer.buildOrThrow());','V152Gpu.draw(buffer.buildOrThrow());\n        }')
    if rel.endswith('BaritoneGuiCompatibilityMixin.java'):
        text=text.replace('import net.minecraft.client.gui.Hud;\nimport net.minecraft.client.gui.Hud;',
            'import net.minecraft.client.gui.Gui;\nimport net.minecraft.client.gui.Hud;')
        text=text.replace('@Mixin(Hud.class)', '@Mixin(Gui.class)')
    return text


def configure(project:Path,donor:Path):
    from adapt_v152_fabric261 import configure as common
    common(project,donor)
    native_loader=project/(JAVA+'hud2/nanovg/NanoVGNativeLoader.java')
    native_loader.write_text(re.sub(r'LIB_VERSION = "[^"]+"',
        'LIB_VERSION = "'+('3.4.3' if project.name=='26.3' else '3.4.1')+'"',
        native_loader.read_text('utf-8-sig')),encoding='utf-8')
    # Baritone 1.19.0 already owns tick/world/render events; only the legacy
    # Settings chat accessor still needs a Gui -> Hud compatibility method.
    path=project/'src/main/resources/wurstpenguin.mixins.json'
    config=json.loads(path.read_text())
    duplicates={'BaritoneMinecraftMixin','BaritoneLevelRendererMixin'}
    config['client']=list(dict.fromkeys([n for n in config['client'] if n not in duplicates]+['BaritoneGuiCompatibilityMixin']))
    path.write_text(json.dumps(config,indent=2)+'\n',encoding='utf-8')
    for name in duplicates:
        source=project/(JAVA+'mixin/'+name+'.java')
        if source.exists():
            archive=ROOT/'.dsh-tmp/v152-obsolete-aliases'/project.name/source.name
            archive.parent.mkdir(parents=True,exist_ok=True)
            if not archive.exists():archive.write_bytes(source.read_bytes())
            source.unlink()
    path=project/'src/main/resources/assets/wurst/translations/zh_cn_names.json'
    names=json.loads(path.read_text())
    names={**json.loads((donor/'src/main/resources/assets/wurst/translations/zh_cn_names.json').read_text()),**names}
    path.write_text(json.dumps(names,indent=2,ensure_ascii=False)+'\n',encoding='utf-8')

    for path in project.joinpath('src').rglob('*.java'):
        path.write_text(adapt(path.as_posix(),path.read_text('utf-8-sig')),encoding='utf-8')
    # 26.2's name-tag nodes are phases, not the former Storage records.
    original=ROOT/'.dsh-tmp/v152-siblings/originals/fabric/versions/26.2'
    path=project/(JAVA+'mixin/SubmitNodeCollectionMixin.java')
    path.write_bytes((original/(JAVA+'mixin/SubmitNodeCollectionMixin.java')).read_bytes())
    # The real fire geometry callback replaces the removed screen renderer.
    path=project/(JAVA+'mixin/ScreenEffectRendererMixin.java')
    path.write_text((original/(JAVA+'mixin/ScreenEffectRendererMixin.java')).read_text('utf-8-sig').replace(', require = 0',', require = 1'),encoding='utf-8')
    path=project/(JAVA+'mixin/GameRendererMixin.java')
    text=path.read_text('utf-8-sig')
    anchor='public abstract class GameRendererMixin'
    # Insert into the actual class body, preserving any implements clause.
    hook='''
    @org.spongepowered.asm.mixin.injection.Inject(method = "mainRenderTarget", at = @org.spongepowered.asm.mixin.injection.At("HEAD"), cancellable = true)
    private void wurst$v152ItemTarget(org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable<com.mojang.blaze3d.pipeline.RenderTarget> cir) {
        var target = net.wurstclient.util.render.V152Gpu.targetOverride();
        if (target != null) cir.setReturnValue(target);
    }
'''
    if 'wurst$v152ItemTarget' not in text:
        index=text.index('{',text.index('class GameRendererMixin'))+1
        text=text[:index]+hook+text[index:]
    path.write_text(text,encoding='utf-8')
    path=project/'src/main/resources/wurstpenguin.accesswidener'
    path.write_text(path.read_text().replace('net/minecraft/client/gui/Gui\t','net/minecraft/client/gui/Hud\t').replace('net/minecraft/client/gui/Gui ','net/minecraft/client/gui/Hud '),encoding='utf-8')
