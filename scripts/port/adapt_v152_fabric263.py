"""26.3 RenderPearl/SDL port, retaining the new feature implementation."""
from pathlib import Path
import json
import re
import subprocess

ROOT=Path(__file__).resolve().parents[2]
JAVA='src/main/java/net/wurstclient/'
PACKAGES={
    'com.mojang.blaze3d.opengl':'com.mojang.renderpearl.backend.opengl',
    'com.mojang.blaze3d.buffers':'com.mojang.renderpearl.api.buffers',
    'com.mojang.blaze3d.textures':'com.mojang.renderpearl.api.textures',
    'com.mojang.blaze3d.GpuFormat':'com.mojang.renderpearl.api.GpuFormat',
    'com.mojang.blaze3d.PrimitiveTopology':'com.mojang.renderpearl.api.pipeline.PrimitiveTopology',
    'com.mojang.blaze3d.IndexType':'com.mojang.renderpearl.api.pipeline.IndexType',
    'com.mojang.blaze3d.vertex.VertexFormat':'com.mojang.renderpearl.api.vertex.VertexFormat',
    'com.mojang.blaze3d.vertex.VertexFormatElement':'com.mojang.renderpearl.api.vertex.VertexFormatElement',
    'com.mojang.blaze3d.pipeline.RenderPipeline':'com.mojang.renderpearl.api.pipeline.RenderPipeline',
    'com.mojang.blaze3d.systems.RenderPass':'com.mojang.renderpearl.api.commands.RenderPass',
    'com.mojang.blaze3d.systems.CommandEncoder':'com.mojang.renderpearl.api.commands.CommandEncoder',
    'com.mojang.blaze3d.systems.GpuDeviceBackend':'com.mojang.renderpearl.backend.api.GpuDeviceBackend',
    'com.mojang.blaze3d.systems.CommandEncoderBackend':'com.mojang.renderpearl.backend.api.CommandEncoderBackend',
    'com.mojang.blaze3d.systems.GpuDevice':'com.mojang.renderpearl.api.device.GpuDevice',
}


def conflicts(rel,text,theirs):
    short=rel.removeprefix(JAVA)
    if short=='mixin/LevelRendererMixin.java':
        return (ROOT/'.dsh-tmp/v152-siblings/originals/fabric/versions/26.3'/rel).read_bytes()
    if short.startswith(('altmanager/','clickgui2/','hacks/','options/','serverfinder/')) or short in {
        'WurstClient.java','hud2/nanovg/NanoVgBlitter.java'}:return theirs
    if rel=='src/main/resources/wurstpenguin.mixins.json':
        config=json.loads(theirs)
        old=json.loads((ROOT/'.dsh-tmp/v152-siblings/originals/fabric/versions/26.3'/rel).read_text())
        config['client']=list(dict.fromkeys(config['client']+old['client']))
        return (json.dumps(config,indent=2)+'\n').encode()
    return None


def adapt(rel,text):
    for old,new in sorted(PACKAGES.items(),key=lambda x:-len(x[0])):
        text=text.replace(old,new).replace(old.replace('.','/'),new.replace('.','/'))
    # GLFW key numbers differ from SDL scancodes. Use named game constants.
    text=text.replace('import org.lwjgl.glfw.GLFW;', 'import com.mojang.blaze3d.platform.InputConstants;')
    text=text.replace('import static org.lwjgl.glfw.GLFW.*;', 'import static com.mojang.blaze3d.platform.InputConstants.*;')
    aliases={'LEFT_SHIFT':'LSHIFT','RIGHT_SHIFT':'RSHIFT','LEFT_CONTROL':'LCONTROL','RIGHT_CONTROL':'RCONTROL',
        'LEFT_ALT':'LALT','RIGHT_ALT':'RALT','PAGE_UP':'PAGEUP','PAGE_DOWN':'PAGEDOWN',
        'KP_ENTER':'NUMPADENTER','KP_ADD':'ADD','KP_SUBTRACT':'SUBTRACT','KP_DECIMAL':'DECIMAL',
        'KP_MULTIPLY':'MULTIPLY','KP_DIVIDE':'DIVIDE','GRAVE_ACCENT':'GRAVE'}
    text=re.sub(r'GLFW\.GLFW_KEY_(\w+)',lambda m:'InputConstants.KEY_'+aliases.get(m[1],m[1]),text)
    text=re.sub(r'\bGLFW_KEY_(\w+)',lambda m:'KEY_'+aliases.get(m[1],m[1]),text)
    text=re.sub(r'\bKEY_ENTER\b', 'KEY_RETURN',text)
    text=text.replace('GLFW.GLFW_MOUSE_BUTTON_LEFT','InputConstants.MOUSE_BUTTON_LEFT').replace('GLFW.GLFW_MOUSE_BUTTON_RIGHT','InputConstants.MOUSE_BUTTON_RIGHT')
    text=re.sub(r'GLFW\.GLFW_(MOUSE_BUTTON_\w+|PRESS|RELEASE|REPEAT)',r'InputConstants.\1',text)
    text=text.replace('org.lwjgl.glfw.InputConstants.', 'com.mojang.blaze3d.platform.InputConstants.')
    text=text.replace('InputConstants.KEY_ENTER','InputConstants.KEY_RETURN')
    text=text.replace('.scancode()', '.keycode()')
    text=re.sub(r'(InputConstants\.isKeyDown\()\s*MC\.getWindow\(\),\s*',r'\1',text)
    for name in ['CONTROL','SHIFT','ALT','SUPER']:
        text=text.replace('GLFW.GLFW_MOD_'+name,'InputConstants.MOD_'+name)
        text=text.replace('GLFW_MOD_'+name,'MOD_'+name)
    text=text.replace('com.mojang.renderpearl.api.vertex.VertexFormatElement', 'com.mojang.renderpearl.api.vertex.VertexFormatElement')
    text=text.replace('((GlDevice)RenderSystem.getDevice().backend)',
        '((GlDevice)((com.mojang.renderpearl.frontend.FrontendGpuDevice)RenderSystem.getDevice()).backend)')
    text=text.replace('(GlDevice)RenderSystem.getDevice().backend',
        '(GlDevice)((com.mojang.renderpearl.frontend.FrontendGpuDevice)RenderSystem.getDevice()).backend')
    text=text.replace('RenderSystem.getDevice().createCommandEncoder().backend',
        '((com.mojang.renderpearl.frontend.FrontendCommandEncoder)RenderSystem.getDevice().createCommandEncoder()).backend()')
    # PoseStack quaternion rotation changed its method name; matrix mulPose stays.
    text=re.sub(r'\.mulPose\((new Quaternionf\([^;]+?)\);',r'.rotate(\1);',text)
    text=text.replace('.mulPose(Axis.', '.rotate(Axis.')
    text=text.replace('.mulPose(camera.rotation())', '.rotate(camera.rotation())')
    text=re.sub(r'(?<!player)\.swing\((InteractionHand\.\w+|hand), net\.minecraft\.world\.item\.component\.SwingAnimation\.DEFAULT, false\)',
        r'.swing(\1)',text)
    text=re.sub(r'(?<=player)\.swing\((InteractionHand\.\w+|hand)\)',
        r'.swing(\1, net.minecraft.world.item.component.SwingAnimation.DEFAULT, false)',text)
    text=re.sub(r'(new TextureTarget\([^;\n]+?), (true|false), (com\.mojang\.renderpearl\.api\.GpuFormat\.RGBA8_UNORM)\)',
        lambda m:m[1]+', '+m[3]+', '+('com.mojang.renderpearl.api.GpuFormat.D32_FLOAT' if m[2]=='true' else 'null')+')',text)
    text=text.replace('target.useDepth', 'target.hasDepth()')
    text=text.replace('Util.getPlatform().openFile(', 'com.mojang.blaze3d.Blaze3D.openPath(')
    if 'Blaze3D.openPath(' in text:
        text=text.replace('.toFile()', '')
    text=re.sub(r'Util\.getPlatform\(\)\.openUri\(\s*("[^"\n]*")\)',
        r'com.mojang.blaze3d.Blaze3D.openUri(java.net.URI.create(\1))',text)
    if rel.endswith('V152Input.java'):
        text=text.replace('isKeyDown(window, 340)', 'isKeyDown(com.mojang.blaze3d.platform.InputConstants.KEY_LSHIFT)')
        text=text.replace('isKeyDown(window, 344)', 'isKeyDown(com.mojang.blaze3d.platform.InputConstants.KEY_RSHIFT)')
    if rel.endswith('NanoVgBlitter.java'):
        text=text.replace('state.addBlitToCurrentLayer(', 'state.addGuiElement(')
    if rel.endswith('V152Gpu.java') and 'import com.mojang.renderpearl.api.vertex.VertexFormat;' not in text:
        text=text.replace('import com.mojang.blaze3d.vertex.*;',
            'import com.mojang.blaze3d.vertex.*;\nimport com.mojang.renderpearl.api.vertex.VertexFormat;')
    if '/seed/' in rel:
        text=text.replace('net.minecraft.world.level.levelgen.feature.configurations.OreConfiguration',
            'net.minecraft.world.level.levelgen.feature.AbstractOreFeature')
        text=text.replace('OreConfiguration','AbstractOreFeature')
        text=text.replace('feature.config() instanceof AbstractOreFeature ore', 'feature instanceof AbstractOreFeature ore')
        text=text.replace('feature.feature() instanceof ScatteredOreFeature','feature instanceof ScatteredOreFeature')
        for field in ['size','targetStates','discardChanceOnAirExposure']:
            text=re.sub(r'config\.'+field+r'\b(?!\()', 'config.'+field+'()',text)
        text=text.replace('ore.targetStates.stream()', 'ore.targetStates().stream()')
        text=re.sub(r'\b(t|target)\.(state|target)\b(?!\()',r'\1.\2()',text)
        text=text.replace('AbstractOreFeature.TargetBlockState','net.minecraft.world.level.levelgen.feature.BlockReplacement')
        text=text.replace('target.target().test(state, (RandomSource) rand)',
            'target.target().test(state, pos, (RandomSource) rand)')
        text=text.replace('VanillaRegistries::createLookup','VanillaRegistries::createWorldLookup')
        text=text.replace('modifiers.add(pos -> modifier.getPositions(context, random, pos).iterator());',
            '''modifiers.add(pos -> {
              List<BlockPos> positions = new ArrayList<>();
              modifier.modify(context, random, pos, p -> positions.add(p.immutable()));
              return positions.iterator();
            });''')
    if rel.endswith('PacketLoggerHack.java'):
        text=text.replace('filter("hand-swing", ServerboundSwingPacket.class);','filter("punch", ServerboundPunchPacket.class);')
        text=text.replace('if (packet instanceof ServerboundSwingPacket p) return "hand=" + p.getHand();',
            'if (packet instanceof ServerboundPunchPacket) return "punch";')
        text=text.replace('p.getHitResult()', 'p.hitResult()')
        text=text.replace('ServerboundUseItemPacket p) return "hand=" + p.getHand()',
            'ServerboundUseItemPacket p) return "hand=" + p.hand()')
        text=text.replace('ServerboundAcceptTeleportationPacket p) return "id=" + p.getId()',
            'ServerboundAcceptTeleportationPacket p) return "id=" + p.id()')
        text=text.replace('"position=" + p.position()', '"position=" + p.movingTo().position()')
    if rel.endswith('BacktrackHack.java'):
        text=re.sub(r'tracked =\s*new Vec3\(\s*decode\(tracked.x, move.getXa\(\)\),\s*decode\(tracked.y, move.getYa\(\)\),\s*decode\(tracked.z, move.getZa\(\)\)\);',
            '''var codec = new net.minecraft.network.protocol.game.VecDeltaCodec();
      codec.setBase(tracked);
      tracked = move.getPositionDelta().decode(codec).endPosition();''',text)
    if rel.endswith('MoonEntityGlow.java'):
        text=text.replace('features.renderAllFeatures(nodes);', '''try (var prepared = features.prepareFrame(nodes);
            var pass = RenderSystem.getDevice().createCommandEncoder().createRenderPass(
                () -> "WurstB Moon entity mask", mask.getColorTextureView(), java.util.Optional.empty())) {
          net.minecraft.client.renderer.feature.FeatureRenderDispatcher.renderAllFeatures(pass, prepared);
        }''')
        text=text.replace('net.minecraft.client.renderer.feature.FeatureRenderDispatcher.renderAllFeatures(pass, prepared);',
            'prepared.executeOutline(pass);')
    if rel.endswith('GlobalSettingHack.java') and 'ServerboundSwingPacket' in text:
        text=text.replace('import net.minecraft.network.protocol.game.ServerboundSwingPacket;',
            'import net.minecraft.world.item.component.SwingAnimation;')
        start=text.index('    if (mode == SwingMode.Both)')
        end=text.index('\n  }',start)
        text=text[:start]+'''    // In 26.3 the interaction packet drives the server animation; the
    // standalone swing packet was removed. Only local animation is optional.
    if (mode == SwingMode.Both || mode == SwingMode.Client)
      MC.player.swing(selected, MC.player.getItemInHand(selected).getInteractAnimation(), false);'''+text[end:]
    return text


def configure(project:Path,donor:Path):
    from adapt_v152_fabric262 import configure as common
    common(project,donor)
    path=project/(JAVA+'mixin/SubmitNodeCollectionMixin.java')
    path.write_bytes(subprocess.run(['git','show','HEAD:fabric/versions/26.3/'+JAVA+'mixin/SubmitNodeCollectionMixin.java'],cwd=ROOT,capture_output=True,check=True).stdout)
    path=project/(JAVA+'mixin/ScreenEffectRendererMixin.java')
    path.write_bytes(subprocess.run(['git','show','HEAD:fabric/versions/26.3/'+JAVA+'mixin/ScreenEffectRendererMixin.java'],cwd=ROOT,capture_output=True,check=True).stdout.replace(b'require = 0',b'require = 1'))
    # PreparedFrame routes the outline directly to our RenderPass. The old
    # LevelRenderer target accessor no longer exists and is no longer needed.
    path=project/'src/main/resources/wurstpenguin.mixins.json'
    config=json.loads(path.read_text());config['client']=[n for n in config['client'] if n!='WorldRendererMixin']
    path.write_text(json.dumps(config,indent=2)+'\n',encoding='utf-8')
    path=project/(JAVA+'mixin/WorldRendererMixin.java')
    if path.exists():
        archive=ROOT/'.dsh-tmp/v152-obsolete-aliases/26.3/WorldRendererMixin.java'
        archive.parent.mkdir(parents=True,exist_ok=True)
        archive.write_bytes(path.read_bytes());path.unlink()
    path=project/(JAVA+'mixin/LevelRendererMixin.java')
    original=ROOT/'.dsh-tmp/v152-siblings/originals/fabric/versions/26.3'
    text=(original/(JAVA+'mixin/LevelRendererMixin.java')).read_text('utf-8-sig')
    text=text.replace('EventManager.fire(event);','''var previous = net.wurstclient.util.render.V152Gpu.projection();
        try {
            net.wurstclient.util.render.V152Gpu.setProjection(cameraState.projectionMatrix, false);
            EventManager.fire(event);
            net.wurstclient.WurstClient.INSTANCE.getPostEffectQueue().flush(matrixStack, tickProgress);
            net.wurstclient.util.render.MoonEntityGlow.INSTANCE.flush(matrixStack, tickProgress);
        } finally { net.wurstclient.util.render.V152Gpu.restoreProjection(previous); }''')
    path.write_text(text,encoding='utf-8')
    for path in project.joinpath('src').rglob('*.java'):
        path.write_text(adapt(path.as_posix(),path.read_text('utf-8-sig')),encoding='utf-8')
    path=project/'src/main/resources/wurstpenguin.accesswidener'
    text=adapt(path.as_posix(),path.read_text())
    text=text.replace('lastPipeline Lcom/mojang/renderpearl/api/pipeline/RenderPipeline;',
        'lastPipeline Lcom/mojang/renderpearl/backend/opengl/GlRenderPipeline;')
    text='\n'.join(row for row in text.splitlines() if not any(n in row for n in [
        'accessible field com/mojang/renderpearl/api/device/GpuDevice backend',
        'accessible field com/mojang/renderpearl/api/commands/CommandEncoder backend']))+'\n'
    text+='accessible class com/mojang/renderpearl/backend/opengl/GlDevice\n'
    text+='accessible class com/mojang/renderpearl/backend/opengl/GlCommandEncoder\n'
    text+='accessible field com/mojang/renderpearl/frontend/FrontendGpuDevice backend Lcom/mojang/renderpearl/backend/api/GpuDeviceBackend;\n'
    path.write_text('\n'.join(dict.fromkeys(text.splitlines()))+'\n',encoding='utf-8')
    path=project/'gradle.properties'
    path.write_text(re.sub(r'^lwjgl_nanovg_version=.*$','lwjgl_nanovg_version=3.4.3',path.read_text(),flags=re.M),encoding='utf-8')
    adapt_shaders(project)


def adapt_shaders(project:Path):
    # RenderPearl compiles GLSL through SPIR-V, which requires explicit
    # interface locations even when the selected backend is OpenGL.
    path=project/'src/main/resources/assets/wurst/shaders/program/target_effect.fsh'
    text=path.read_text().replace('#version 150', '#version 330\n#extension GL_ARB_separate_shader_objects : require')
    text=text.replace('\nin vec2 texCoord;', '\nlayout(location = 0) in vec2 texCoord;')
    text=text.replace('\nout vec4 fragColor;', '\nlayout(location = 0) out vec4 fragColor;')
    path.write_text(text,encoding='utf-8')
