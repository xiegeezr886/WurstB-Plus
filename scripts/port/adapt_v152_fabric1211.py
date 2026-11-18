"""Reviewed 1.21/1.21.1 component API family, using the 1.21.3 donor."""
from pathlib import Path
import hashlib
import json
import re
import zipfile

ROOT = Path(__file__).resolve().parents[2]
JAVA = 'src/main/java/net/wurstclient/'
MODULES = {'AutoAnchorHack', 'AutoArmorHack', 'AutoArmorPlusHack', 'AutoTotemHack',
           'BacktrackHack', 'CriticalsHack', 'CrystalAuraHack', 'CrystalAuraPlusHack',
           'InvWalkHack', 'KillauraHack', 'ScaffoldWalkHack'}


def conflicts(target, rel, text, theirs):
    if rel == JAVA + 'WurstClient.java':
        text = re.sub(r'<<<<<<<[^\n]*\n(.*?)=======\n(.*?)>>>>>>>[^\n]*\n?', lambda m:m[2], text, flags=re.S)
        return text.replace('MC_VERSION = "1.21.3"', 'MC_VERSION = "' + target.rsplit('/',1)[1] + '"').encode()
    if Path(rel).stem in MODULES or rel == JAVA + 'hud2/compat/HudTextures.java':
        return theirs
    if rel in (JAVA + 'mixin/BlockMixin.java', JAVA + 'util/render/PostEffectQueue.java'):
        # The pre-framegraph shader and five-argument block APIs remain target-owned.
        return re.sub(r'<<<<<<<[^\n]*\n(.*?)=======\n(.*?)>>>>>>>[^\n]*\n?', lambda m:m[1], text, flags=re.S).encode()
    return None


def adapt(rel, text):
    for old,new in {
        '.getMinY()':'.getMinBuildHeight()', '.getMaxY()':'.getMaxBuildHeight()',
        'public int getMinY()':'public int getMinBuildHeight()',
        '.getUnitVec3i()':'.getNormal()',
        'net.minecraft.world.item.ItemUseAnimation':'net.minecraft.world.item.UseAnim',
        'ItemUseAnimation':'UseAnim',
        'MC.player.input.keyPresses.jump()':'MC.player.input.jumping',
        'player.input.keyPresses.jump()':'player.input.jumping',
        '.isOnCooldown(stack)':'.isOnCooldown(stack.getItem())',
        'com.mojang.blaze3d.ProjectionType.ORTHOGRAPHIC':'com.mojang.blaze3d.vertex.VertexSorting.ORTHOGRAPHIC_Z',
        'import com.mojang.blaze3d.ProjectionType;':'import com.mojang.blaze3d.vertex.VertexSorting;',
        'ProjectionType.ORTHOGRAPHIC':'VertexSorting.ORTHOGRAPHIC_Z',
        'net.minecraft.client.renderer.CompiledShaderProgram':'net.minecraft.client.renderer.ShaderInstance',
        'import net.minecraft.client.renderer.ShaderProgram;':'import net.minecraft.client.renderer.ShaderInstance;',
    }.items(): text=text.replace(old,new)
    text = re.sub(r'net.minecraft.client.renderer.CoreShaders\.(POSITION_TEX_COLOR|POSITION_COLOR|POSITION_TEX|POSITION)',
        lambda m:'net.minecraft.client.renderer.GameRenderer::get' + {'POSITION_TEX_COLOR':'PositionTexColor','POSITION_COLOR':'PositionColor','POSITION_TEX':'PositionTex','POSITION':'Position'}[m[1]] + 'Shader',text)
    text = re.sub(r'new TextureTarget\(([^;\n]+), (true|false)\)',r'new TextureTarget(\1, \2, net.minecraft.client.Minecraft.ON_OSX)',text)
    if any(n in rel for n in ('HudItemIcons','HudGlassBackground','OpaiBackground','MoonEntityGlow','NavigatorBloom','DuoGlassTransition')):
        text=re.sub(r'\b(target|capture|reduced|mask|blur|input|output|layer)\.clear\(\)',r'\1.clear(net.minecraft.client.Minecraft.ON_OSX)',text)
    if any(n in rel for n in ('HudGlassBackground','OpaiBackground','MoonEntityGlow','NavigatorBloom','DuoGlassTransition')):
        text=re.sub(r'\.resize\((\w+), (\w+)\)',r'.resize(\1, \2, net.minecraft.client.Minecraft.ON_OSX)',text)
    if 'RenderSystem.getModelViewStack()' in text:
        text=text.replace('RenderSystem.restoreProjectionMatrix();','RenderSystem.applyModelViewMatrix();\n        RenderSystem.restoreProjectionMatrix();')
    text=text.replace('graphics.blit(net.minecraft.client.renderer.RenderType::guiTextured, ', 'graphics.blit(')
    # The extra horizontal-collision flag was added in 1.21.2. All donor calls
    # append this same expression, after the existing onGround argument.
    text=text.replace(', net.wurstclient.WurstClient.MC.player.horizontalCollision', '')
    if rel.endswith(('SkiaHudTexture.java','CoverCache.java','SkiaTextCache.java')):
        text=text.replace('image.setPixel(x, y, a << 24 | r << 16 | g << 8 | b)', 'image.setPixelRGBA(x, y, a << 24 | b << 16 | g << 8 | r)')
        text=text.replace('nativeImage.setPixel(x, y, p)', 'nativeImage.setPixelRGBA(x, y, (p & 0xFF00FF00) | ((p >> 16) & 255) | ((p & 255) << 16))')
    if rel.endswith('EnchantmentListSetting.java'):
        text=text.replace('.lookupOrThrow(net.minecraft.core.registries.Registries.ENCHANTMENT).listElements()', '.registryOrThrow(net.minecraft.core.registries.Registries.ENCHANTMENT).holders()')
    if rel.endswith('PacketLoggerHack.java'):
        text=text.replace('"position=" + p.change().position()', '"position=" + p.getX() + "," + p.getY() + "," + p.getZ()')
    if rel.endswith('PushCrystalHack.java'):text=text.replace('state.isSolidRender()', 'state.isSolidRender(MC.level, pos)')
    if rel.endswith('BacktrackHack.java'):
        text=text.replace('tp.id()', 'tp.getId()')
        text=re.sub(r'net.minecraft.world.entity.PositionMoveRotation.calculateAbsolute\([^;]+?\.position\(\)', 'new Vec3(tp.getX(), tp.getY(), tp.getZ())',text)
    if rel.endswith('AntiKnockbackHack.java'):
        text=text.replace('access.wurst_setPlayerKnockback(java.util.Optional.empty());','access.wurst_setKnockbackX(0); access.wurst_setKnockbackY(0); access.wurst_setKnockbackZ(0);')
    if rel.endswith('MoonEntityGlow.java'):
        text=text.replace('.render(entity, 0, 0, 0, partialTicks,', '.render(entity, 0, 0, 0, Mth.rotLerp(partialTicks, entity.yRotO, entity.getYRot()), partialTicks,')
    if rel.endswith('WorldRendererMixin.java'):text=text.replace('entityOutlineTarget()', 'entityTarget()')
    if rel.endswith('LeavesPlayerRendererMixin.java'):text=text.replace('PoseStack;FFF)V', 'PoseStack;FFFF)V')
    if rel.endswith('TestControlServer.java'):
        text=text.replace('new net.minecraft.world.level.GameRules(net.minecraft.world.flag.FeatureFlags.DEFAULT_FLAGS)','new net.minecraft.world.level.GameRules()')
    return text


def configure(project, seed_version='2.15.0', seed_digest=None, seed_mc='1.21/1.21.1'):
    import sync_v152_fabric1204 as old
    previous=old.TARGET
    try:
        old.TARGET=project
        old.configure_build()
    finally:old.TARGET=previous
    from host_netty import configure as configure_netty
    configure_netty(project)
    mc=project.name
    props=project/'gradle.properties'
    props.write_text(re.sub(r'^mod_version=.*$', 'mod_version=1.5.2-dev-Fabric-'+mc,props.read_text('utf-8-sig'),flags=re.M),encoding='utf-8')
    seed=ROOT/('.dsh-tmp/v152-upstream-seed/seedcrackerX-'+seed_version+'.jar')
    digest=seed_digest or '52f8e11d95cf65710a051710334556e0d99494f1ce33ea9775d43f36c2d4cce9'
    assert hashlib.sha256(seed.read_bytes()).hexdigest()==digest
    libs=project/'libs';libs.mkdir(exist_ok=True)
    (libs/seed.name).write_bytes(seed.read_bytes())
    deps=libs/'seedcrackerx-deps';deps.mkdir(exist_ok=True)
    with zipfile.ZipFile(seed) as z:
        for jar in json.loads(z.read('fabric.mod.json'))['jars']:
            (deps/Path(jar['file']).name).write_bytes(z.read(jar['file']))
    build=project/'build.gradle'
    build.write_text(re.sub(r'seedcrackerX:[^"\s]+','seedcrackerX:'+seed_version,build.read_text('utf-8-sig')).replace('include: ["*.jar"]','include: ["cloth-config*.jar"]'),encoding='utf-8')
    (project/'src/main/resources/META-INF/licenses/seedcrackerx/NOTICE.txt').write_text(
        'SeedCrackerX '+seed_version+', MIT, KaptainWutax / 19MisterX98.\nUnmodified upstream release for Minecraft '+seed_mc+'.\n'
        'https://github.com/19MisterX98/SeedcrackerX/releases/tag/'+seed_version+'\nSHA-256: '+digest+'\nNested metadata and licenses retained.\n',encoding='utf-8')
    from sync_v152_1201 import REPLACED_SCAFFOLD
    for rel in REPLACED_SCAFFOLD:(project/rel).unlink(missing_ok=True)
    helper=(Path(__file__).parent/'templates/fabric1214/V152ItemCompat.java').read_text('utf-8')
    helper=helper.replace('var equipped = stack.get(DataComponents.EQUIPPABLE);','var equipped = stack.getItem() instanceof net.minecraft.world.item.ArmorItem armorItem ? armorItem.getEquipmentSlot() : null;')
    helper=helper.replace('equipped.slot()', 'equipped')
    (project/(JAVA+'util/V152ItemCompat.java')).write_text(helper,encoding='utf-8')
    # Keep the target's registered render-state mixin; 1.21.1 still renders Player directly.
    orig=ROOT/'.dsh-tmp/v152-siblings/originals'/project.relative_to(ROOT)
    for name in ('LeavesPlayerRendererMixin','NeoRavenPreparingLevelMixin'):
        p=project/(JAVA+'mixin/'+name+'.java')
        source=ROOT/'fabric/versions/1.20.4'/(JAVA+'mixin/'+name+'.java')
        if name=='NeoRavenPreparingLevelMixin':
            source=Path(__file__).parent/'templates/fabric1214/NeoRavenPreparingLevelMixin.java'
        if source.exists():p.write_text(adapt(str(p),source.read_text('utf-8-sig')),encoding='utf-8')
    # Old post chains use target-owned Java APIs and the baseline shader format.
    baseline=ROOT/'fabric/versions/1.20.4/src/main/resources/assets'
    for folder in ('wurst/shaders','wurstpenguin/shaders'):
        for source in (baseline/folder).rglob('*'):
            if not source.is_file():continue
            dest=project/'src/main/resources/assets'/source.relative_to(baseline)
            dest.parent.mkdir(parents=True,exist_ok=True);dest.write_bytes(source.read_bytes())
    for folder in ('wurst/post_effect','wurstpenguin/post_effect'):
        for p in (project/'src/main/resources/assets'/folder).glob('*.json'):p.unlink()
    for name in ('HudGlassBackground.java','OpaiBackground.java'):
        p=next((project/'src/main/java').rglob(name))
        text=p.read_text('utf-8-sig')
        text=text.replace('net.wurstclient.util.render.V152BackdropBlur','PostChain')
        capture='capture' if name.startswith('Hud') else 'reduced'
        text=text.replace('new PostChain()', 'new PostChain(mc.getTextureManager(), mc.getResourceManager(), '+capture+', ResourceLocation.fromNamespaceAndPath("wurstpenguin", "shaders/post/opai_background.json"))')
        text=text.replace('blur.process('+capture+')','blur.process(partialTicks)')
        if capture=='capture':
            text=text.replace('capture.resize(width, height, net.minecraft.client.Minecraft.ON_OSX);', 'capture.resize(width, height, net.minecraft.client.Minecraft.ON_OSX);\n                blur.resize(width, height);')
            text=text.replace('"shaders/post/opai_background.json"));', '"shaders/post/opai_background.json"));\n                blur.resize(width, height);')
        else:
            text=text.replace('if(blur == null)', 'if(blur != null && (blur.getTempTarget("swap").width != reducedWidth || blur.getTempTarget("swap").height != reducedHeight)) blur.resize(reducedWidth, reducedHeight);\n            if(blur == null)')
            text=text.replace('"shaders/post/opai_background.json"));', '"shaders/post/opai_background.json"));\n                blur.resize(reducedWidth, reducedHeight);')
        p.write_text(text,encoding='utf-8')
    (project/(JAVA+'util/render/V152BackdropBlur.java')).unlink(missing_ok=True)
    config=project/'src/main/resources/wurstpenguin.mixins.json'
    data=json.loads(config.read_text('utf-8-sig'))
    data['client']=list(dict.fromkeys('ToastComponentAccessor' if n=='ToastManagerAccessor' else n for n in data['client']))
    config.write_text(json.dumps(data,indent=2)+'\n',encoding='utf-8')
    test=project/'src/test/java/net/wurstclient/util/render/PostEffectResourcesTest.java'
    text=test.read_text('utf-8-sig').replace('../post_effect/target_', 'post/target_')
    text=text.replace('assertTrue(json.getAsJsonObject("targets").has("swap"));', 'assertTrue(json.getAsJsonArray("targets").contains(JsonParser.parseString("\\\"swap\\\"")));')
    text=text.replace('get("program").getAsString()', 'get("name").getAsString()').replace('"wurst:program/target_effect"','"wurst:target_effect"')
    test.write_text(text,encoding='utf-8')
