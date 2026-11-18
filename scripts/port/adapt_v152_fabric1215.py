"""1.21.5 inventory and GPU transition. Keep target-only modules and APIs."""
from pathlib import Path
import hashlib
import json
import re
import zipfile

ROOT = Path(__file__).resolve().parents[2]
JAVA = 'src/main/java/net/wurstclient/'
GPU = 'net.wurstclient.util.render.V152Gpu'
REPLACED = {'AutoAnchorHack', 'AutoArmorHack', 'AutoArmorPlusHack', 'AutoClickerHack',
 'AutoGapHack', 'DirectionalPaver', 'AutoPlaceBlockHack', 'AutoRefreshTradeHack',
 'AutoSprintHack', 'AutoTorchHack', 'AutoTotemHack', 'AutoWeaponHack', 'AutoWebHack',
 'BowAimbotHack', 'CriticalsHack', 'FireworkElytraFlyHack', 'GlassFillerHack',
 'InvWalkHack', 'PrinterHack', 'ReplenishHack', 'ScaffoldWalkHack', 'SelfTrapHack',
 'SaplingFarm', 'TriggerBotHack', 'PostEffectQueue'}


def conflicts(rel, text, theirs):
    if Path(rel).stem in REPLACED:
        # Replace the old combat/placement behavior, then adapt its actual calls.
        return theirs
    if rel in (JAVA+'hud2/mods/ModernHotbarMod.java', JAVA+'WurstClient.java'):
        return re.sub(r'<<<<<<<[^\n]*\n(.*?)=======\n(.*?)>>>>>>>[^\n]*\n?',
            lambda m:m[2], text, flags=re.S).replace('MC_VERSION = "1.21.4"',
                'MC_VERSION = "1.21.5"').encode()
    if rel == JAVA+'hack/HackList.java':
        original = (ROOT/'.dsh-tmp/v152-siblings/originals/fabric/versions/1.21.5'/rel).read_text('utf-8-sig')
        text = theirs.decode('utf-8-sig')
        known = set(re.findall(r'public final\s+\w+\s+(\w+)\s*=', text))
        extra = [m[0] for m in re.finditer(r'public final\s+\w+\s+(\w+)\s*=\s*new\s+\w+\([^;]*?;', original, re.S) if m[1] not in known]
        return text.replace('public HackList()', '\n'.join('\t'+n for n in extra)+'\n\n\tpublic HackList()').encode()
    return None


def adapt(rel, text):
    if rel.endswith('LeavesFlightMixin.java'):
        # Player moved the fall-flying decision into this helper in 1.21.5.
        text = text.replace('method = "updatePlayerPose"', 'method = "getDesiredPose"')
    if rel.endswith('LeavesHotbarMixin.java'):
        text = text.replace('value = "FIELD", target = "Lnet/minecraft/world/entity/player/Inventory;selected:I"',
            'value = "INVOKE", target = "Lnet/minecraft/world/entity/player/Inventory;getSelectedSlot()I"')
    if 'com.mojang.blaze3d.systems.RenderSystem.' in text:
        text = text.replace('com.mojang.blaze3d.systems.RenderSystem.', 'RenderSystem.')
        if 'import com.mojang.blaze3d.systems.RenderSystem;' not in text:
            text = re.sub(r'(package [\w.]+;)', r'\1\nimport com.mojang.blaze3d.systems.RenderSystem;', text, count=1)
    # selected is private in 1.21.5. Assignments must become setter calls first.
    inventory = r'(\b[\w.]+\.getInventory\(\)|\binventory|\binv)'
    text = re.sub(inventory+r'\.selected\s*=(?!=)\s*([^;]+);',
        r'\1.setSelectedSlot(\2);', text)
    text = re.sub(inventory+r'\.selected\b', r'\1.getSelectedSlot()', text)
    text = re.sub(inventory+r'\.getSelected\(\)', r'\1.getSelectedItem()', text)
    text = re.sub(r'([\w.]+)\.input\.forwardImpulse', r'\1.input.getMoveVector().y', text)
    text = re.sub(r'([\w.]+)\.input\.leftImpulse', r'\1.input.getMoveVector().x', text)
    text = re.sub(r'([\w.]+)\.getArmorSlots\(\)', r'net.wurstclient.util.V152ItemCompat.armorSlots(\1)', text)
    text = re.sub(r'\b(?:inv|inventory)\.getArmor\((\w+)\)', r'MC.player.getItemBySlot(net.wurstclient.util.V152ItemCompat.armorSlot(\1))', text)
    for old, new in {'DAMAGE_BOOST':'STRENGTH', 'DAMAGE_RESISTANCE':'RESISTANCE',
        'DIG_SPEED':'HASTE', 'DIG_SLOWDOWN':'MINING_FATIGUE'}.items():
        text = text.replace('MobEffects.'+old, 'MobEffects.'+new)
    text = text.replace('MC.gameMode.hasInfiniteItems()', 'MC.player.hasInfiniteMaterials()')
    text = text.replace('com.mojang.blaze3d.vertex.BufferUploader.drawWithShader(', GPU+'.draw(')
    text = text.replace('com.mojang.blaze3d.platform.GlStateManager', 'com.mojang.blaze3d.opengl.GlStateManager')
    text = re.sub(r'^import (?:com.mojang.blaze3d.vertex.BufferUploader|net.minecraft.client.renderer.CoreShaders);\n', '', text, flags=re.M)
    # The bridge's shader is determined by the mesh format (position/UV/color).
    text = re.sub(r'\s*RenderSystem.setShader\((?:net.minecraft.client.renderer.)?CoreShaders\.\w+\);', '', text)
    text = text.replace('BufferUploader.drawWithShader(', GPU+'.draw(')
    for method in ('enableBlend', 'disableBlend', 'enableDepthTest', 'disableDepthTest',
                   'depthMask', 'depthFunc', 'enableCull', 'disableCull', 'colorMask', 'viewport'):
        text = text.replace('RenderSystem.'+method+'(', 'com.mojang.blaze3d.opengl.GlStateManager._'+method+'(')
    text = text.replace('RenderSystem.defaultBlendFunc()', 'com.mojang.blaze3d.opengl.GlStateManager._blendFuncSeparate(770, 771, 1, 771)')
    text = text.replace('RenderSystem.setShaderTexture(', GPU+'.bindTexture(')
    text = re.sub(r'new TextureTarget\(', 'new TextureTarget("WurstB v1.5.2", ', text)
    # Only render-target expressions, not arbitrary fields/methods.
    target = r'([\w.]+(?:\.getMainRenderTarget\(\))?)'
    text = re.sub(target+r'\.frameBufferId\b', GPU+r'.fbo(\1)', text)
    text = re.sub(target+r'\.getColorTextureId\(\)', GPU+r'.texture(\1)', text)
    text = re.sub(target+r'\.bindWrite\((true|false)\)', GPU+r'.bind(\1, \2)', text)
    text = re.sub(target+r'\.unbindWrite\(\);', 'org.lwjgl.opengl.GL30.glBindFramebuffer(org.lwjgl.opengl.GL30.GL_FRAMEBUFFER, 0);', text)
    text = re.sub(r'\s*[\w.]+\.setClearColor\(0, 0, 0, 0\);', '', text)
    text = re.sub(r'\b(target(?:\.renderTarget)?|capture|reduced|mask|blur|layer|input|output)\.clear\(\)', GPU+r'.clear(\1)', text)
    text = text.replace('.setFilterMode(GL11.GL_LINEAR)', '.setFilterMode(com.mojang.blaze3d.textures.FilterMode.LINEAR)')
    if rel.endswith('PostEffectQueue.java'):
        text = re.sub(r'\s*client.getShaderManager\(\).getProgram\(new net.minecraft.client.renderer.ShaderProgram\(.*?\.getUniform\("Time"\).set\([^;]+;', '', text, flags=re.S)
        text = text.replace('GraphicsResourceAllocator.UNPOOLED);',
            'GraphicsResourceAllocator.UNPOOLED, pass -> pass.setUniform("Time", (System.nanoTime() % 20_000_000_000L) / 20_000_000_000F));')
    if rel.endswith('V152BackdropBlur.java'):
        text = text.replace('GraphicsResourceAllocator.UNPOOLED);', 'GraphicsResourceAllocator.UNPOOLED, pass -> {});')
    if rel.endswith('HudTextures.java'):
        text = text.replace('texture.getId()', '((com.mojang.blaze3d.opengl.GlTexture)texture.getTexture()).glId()')
    if rel.endswith('CoverCache.java'):
        text = text.replace('manager.getTexture(id).getId()', '((com.mojang.blaze3d.opengl.GlTexture)manager.getTexture(id).getTexture()).glId()')
    if rel.endswith('HudManager.java'):
        text = text.replace('skiaTexture.getId()', '((com.mojang.blaze3d.opengl.GlTexture)skiaTexture.getTexture()).glId()')
    if rel.endswith(('CoverCache.java', 'SkiaTextCache.java')):
        text = re.sub(r'new DynamicTexture\((\w+)\)', r'new DynamicTexture(() -> "WurstB v1.5.2", \1)', text)
    if rel.endswith('SkiaHudTexture.java'):
        text = text.replace('super(1, 1, false)', 'super("WurstB Skia HUD", 1, 1, false)')
    if rel.endswith('ArmorStatusMod.java'):
        text = text.replace('ItemDisplayContext.GUI, false, MC.player)', 'ItemDisplayContext.GUI, MC.player)')
        text = text.replace('MC.getTextureManager().getTexture(net.minecraft.client.renderer.texture.TextureAtlas.LOCATION_BLOCKS).getId()',
            'net.wurstclient.hud2.compat.HudTextures.id(net.minecraft.client.renderer.texture.TextureAtlas.LOCATION_BLOCKS.toString())')
    if rel.endswith('DynamicIslandMod.java'):
        text = text.replace('MC.getTextureManager().getTexture(art).getId()', 'net.wurstclient.hud2.compat.HudTextures.id(art.toString())')
    if rel.endswith('PacketLoggerHack.java'):
        text = text.replace('p.getSlotNum()', 'p.slotNum()').replace('p.getButtonNum()', 'p.buttonNum()')
    if rel.endswith('AutoArmorHack.java'):
        text = text.replace('stack.getItem() instanceof ArmorItem item', 'net.wurstclient.util.V152ItemCompat.armorType(stack) >= 0')
    if rel.endswith('AutoArmorPlusHack.java'):
        text = text.replace('!(candidate.getItem() instanceof ArmorItem armor)', 'net.wurstclient.util.V152ItemCompat.armorType(candidate) < 0')
        text = text.replace('!(stack.getItem() instanceof ArmorItem armor)', 'net.wurstclient.util.V152ItemCompat.armorType(stack) < 0')
    if rel.endswith('AutoOffHandHack.java'):
        text = text.replace('item instanceof PickaxeItem', 'item.builtInRegistryHolder().is(net.minecraft.tags.ItemTags.PICKAXES)')
        text = text.replace('item instanceof SwordItem', 'item.builtInRegistryHolder().is(net.minecraft.tags.ItemTags.SWORDS)')
    if rel.endswith('ReplenishHack.java'):
        text = text.replace('stack.getItem() instanceof DiggerItem', 'stack.has(net.minecraft.core.component.DataComponents.TOOL)')
        text = text.replace('stack.getItem() instanceof SwordItem', 'stack.is(net.minecraft.tags.ItemTags.SWORDS)')
    if rel.endswith('HudItemIcons.java'):
        text = text.replace('GL11.glPixelStorei(GL11.GL_PACK_ALIGNMENT, 1);', '')
        text = re.sub(r'GL11.glReadPixels\(0, 0, SIZE, SIZE, GL11.GL_RGBA,\s*GL11.GL_UNSIGNED_BYTE, pixels\);',
            GPU+'.readRgba(target, SIZE, SIZE, pixels);', text)
        text = text.replace('int image = NanoVG.nvgCreateImageRGBA(nvg.getHandle(), SIZE, SIZE,\n\t\t\t\tNanoVG.NVG_IMAGE_NEAREST, flipped);',
            '''int image;
            // Native NanoVG texture uploads bypass Minecraft's texture cache.
            // Restore bindings before the next item in this capture batch.
            try(GlStateGuard uploadState = GlStateGuard.capture()) {
                org.lwjgl.opengl.GL15.glBindBuffer(org.lwjgl.opengl.GL21.GL_PIXEL_UNPACK_BUFFER, 0);
                GL11.glPixelStorei(GL11.GL_UNPACK_ALIGNMENT, 1);
                GL11.glPixelStorei(GL11.GL_UNPACK_ROW_LENGTH, 0);
                GL11.glPixelStorei(GL11.GL_UNPACK_SKIP_PIXELS, 0);
                GL11.glPixelStorei(GL11.GL_UNPACK_SKIP_ROWS, 0);
                image = NanoVG.nvgCreateImageRGBA(nvg.getHandle(), SIZE, SIZE,
                    NanoVG.NVG_IMAGE_NEAREST, flipped);
            }''')
        text = text.replace('try\n\t\t{\n\t\t\tGuiGraphics graphics',
            'try(var targetScope = '+GPU+'.target(target))\n\t\t{\n\t\t\tGuiGraphics graphics')
        text = text.replace('WurstClient.MC.renderBuffers().bufferSource().endBatch();', 'graphics.flush();')
        # target scope is a typed AutoCloseable; failures propagate to capturePending.
        text = text.replace('private static void capture(ItemStack stack, NanoVGContext nvg)',
            'private static void capture(ItemStack stack, NanoVGContext nvg) throws Exception')
    return text


def configure(project):
    config_path = project/'src/main/resources/wurstpenguin.mixins.json'
    config = json.loads(config_path.read_text('utf-8-sig'))
    config['client'] = list(dict.fromkeys('ToastComponentAccessor' if name == 'ToastManagerAccessor' else name for name in config['client']))
    config_path.write_text(json.dumps(config, indent=2)+'\n', encoding='utf-8')
    import sync_v152_fabric1204 as old
    previous = old.TARGET
    try:
        old.TARGET = project
        old.configure_build()
    finally:
        old.TARGET = previous
    props = project/'gradle.properties'
    props.write_text(re.sub(r'^mod_version=.*$', 'mod_version=1.5.2-dev-Fabric-1.21.5', props.read_text('utf-8-sig'), flags=re.M), encoding='utf-8')
    seed = ROOT/'.dsh-tmp/v152-upstream-seed/seedcrackerX-2.15.3.jar'
    digest = '2e4d1c106a1f24e7f27a3ae0a2a5ea7fdd633daf39ee3d245b6861b760bd4486'
    assert hashlib.sha256(seed.read_bytes()).hexdigest() == digest
    libs = project/'libs'
    libs.mkdir(exist_ok=True)
    (libs/seed.name).write_bytes(seed.read_bytes())
    deps = libs/'seedcrackerx-deps'
    deps.mkdir(exist_ok=True)
    with zipfile.ZipFile(seed) as z:
        meta = json.loads(z.read('fabric.mod.json'))
        assert meta['depends']['minecraft'] == '>=1.21.5 <1.22'
        for row in meta['jars']:
            (deps/Path(row['file']).name).write_bytes(z.read(row['file']))
    build = project/'build.gradle'
    text = re.sub(r'seedcrackerX:[^"\s]+', 'seedcrackerX:2.15.3', build.read_text('utf-8-sig'))
    text = text.replace('include: ["*.jar"]', 'include: ["cloth-config*.jar"]')
    # Loader expands nested non-mod seedfinding libraries in production only.
    # Keep their original bytes and make them explicit for Loom development runs.
    if 'include: ["mc_*.jar", "latticg-*.jar"]' not in text:
        text += '\ndependencies { runtimeOnly fileTree(dir: "libs/seedcrackerx-deps", include: ["mc_*.jar", "latticg-*.jar"]) }\n'
    text = re.sub(r'include implementation\("io.netty:([^"\n]+)"\)(?: \{ transitive = false \})?',
        r'include implementation("io.netty:\1") { transitive = false }', text)
    build.write_text(text, encoding='utf-8')
    (project/'src/main/resources/META-INF/licenses/seedcrackerx/NOTICE.txt').write_text(
        'SeedCrackerX 2.15.3, MIT, unmodified upstream binary for Minecraft 1.21.5.\n'
        'https://github.com/19MisterX98/SeedcrackerX/releases/tag/2.15.3\nSHA-256: '+digest+'\nNested metadata and licenses retained.\n', encoding='utf-8')
    from sync_v152_1201 import REPLACED_SCAFFOLD
    for rel in REPLACED_SCAFFOLD:
        (project/rel).unlink(missing_ok=True)
    helper = Path(__file__).parent/'templates/fabric1215/V152Gpu.java'
    (project/(JAVA+'util/render/V152Gpu.java')).write_bytes(helper.read_bytes())
    helper = project/(JAVA+'util/V152ItemCompat.java')
    text = helper.read_text('utf-8-sig')
    if 'armorSlots(LivingEntity' not in text:
        text = text.replace('  public static boolean bindingCurse(', '''  public static EquipmentSlot armorSlot(int type) {
    return switch (type) {
      case 0 -> EquipmentSlot.FEET; case 1 -> EquipmentSlot.LEGS;
      case 2 -> EquipmentSlot.CHEST; case 3 -> EquipmentSlot.HEAD;
      default -> throw new IllegalArgumentException("Invalid armor slot: " + type);
    };
  }
  public static java.util.List<ItemStack> armorSlots(LivingEntity entity) {
    return java.util.stream.IntStream.range(0, 4).mapToObj(i -> entity.getItemBySlot(armorSlot(i))).toList();
  }
  public static boolean bindingCurse(''')
    helper.write_text(text, encoding='utf-8')
    for name in ('HudTextures.java', 'V152BackdropBlur.java'):
        source = Path(__file__).parent/'templates/fabric1214'/name
        dest = project/(JAVA+('hud2/compat/' if name.startswith('Hud') else 'util/render/')+name)
        dest.write_text(adapt(str(dest), source.read_text('utf-8-sig')), encoding='utf-8')
    # Redirect RenderType's explicit main target only during item capture.
    mixin = project/(JAVA+'mixin/MinecraftClientMixin.java')
    text = mixin.read_text('utf-8-sig')
    anchor = 'public abstract class MinecraftClientMixin'
    # Class declarations differ; insert after the class body opens.
    start = text.index('{', text.index('class MinecraftClientMixin')) + 1
    hook = '''
    @Inject(method = "getMainRenderTarget", at = @At("HEAD"), cancellable = true)
    private void wurst$v152ItemTarget(org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable<com.mojang.blaze3d.pipeline.RenderTarget> cir) {
        var target = net.wurstclient.util.render.V152Gpu.targetOverride();
        if (target != null) cir.setReturnValue(target);
    }
'''
    if 'wurst$v152ItemTarget' not in text:
        text = text[:start]+hook+text[start:]
    if 'V152Gpu.close();' not in text:
        text = text.replace('net.wurstclient.util.render.MoonEntityGlow.INSTANCE.close();',
            'net.wurstclient.util.render.MoonEntityGlow.INSTANCE.close();\n        net.wurstclient.util.render.V152Gpu.close();')
    text = re.sub(r'(\s*net\.wurstclient\.util\.render\.V152Gpu\.close\(\);){2,}',
        '\n        net.wurstclient.util.render.V152Gpu.close();', text)
    mixin.write_text(text, encoding='utf-8')
    # 1.21.5 post-chain passes declare vertex/fragment shader ids directly.
    for path in (project/'src/main/resources/assets').glob('*/post_effect/*.json'):
        data = json.loads(path.read_text('utf-8-sig'))
        for row in data.get('passes', []):
            if 'program' in row:
                program = row.pop('program')
                row['vertex_shader'] = 'minecraft:post/blit'
                row['fragment_shader'] = program
            if row.get('fragment_shader') == 'minecraft:post/box_blur':
                row['vertex_shader'] = 'minecraft:post/blur'
            if row.get('fragment_shader') == 'minecraft:post/blit':
                uniforms = row.setdefault('uniforms', [])
                if not any(u['name']=='ColorModulate' for u in uniforms):
                    uniforms.append({'name':'ColorModulate', 'type':'vec4', 'values':[1, 1, 1, 1]})
            for uniform in row.get('uniforms', []):
                uniform['type'] = 'int' if uniform['name']=='Mode' else 'vec2' if uniform['name']=='BlurDir' else 'vec4' if uniform['name']=='ColorModulate' else 'float'
            if row.get('fragment_shader') == 'wurst:program/target_effect':
                if not any(u['name']=='Time' for u in row['uniforms']):
                    row['uniforms'].append({'name':'Time', 'type':'float'})
        path.write_text(json.dumps(data, indent=2)+'\n', encoding='utf-8')
    test = project/'src/test/java/net/wurstclient/util/render/PostEffectResourcesTest.java'
    test.write_bytes((Path(__file__).parent/'templates/fabric1215/PostEffectResourcesTest.java').read_bytes())
