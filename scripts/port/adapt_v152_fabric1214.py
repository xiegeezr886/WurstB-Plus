"""Reviewed 1.21.4 API transforms for the v1.5.2 canary; not for 1.21.5+."""
from pathlib import Path
import hashlib
import json
import re
import zipfile

ROOT = Path(__file__).resolve().parents[2]
JAVA = 'src/main/java/net/wurstclient/'
REPLACED = {'altmanager/screens/AltEditorScreen.java', 'clickgui2/NavigatorScreen.java',
 'gui/title/WurstTitleMenu.java', 'hacks/AutoAnchorHack.java', 'hacks/AutoArmorHack.java',
 'hacks/AutoArmorPlusHack.java', 'hacks/AutoClickerHack.java', 'hacks/AutoRefreshTradeHack.java',
 'hacks/AutoTotemHack.java', 'hacks/AutoWeaponHack.java', 'hacks/BacktrackHack.java',
 'hacks/CriticalsHack.java', 'hacks/CrystalAuraHack.java', 'hacks/CrystalAuraPlusHack.java',
 'hacks/ElytraGrimAccelerateHack.java', 'hacks/FireworkElytraFlyHack.java',
 'hacks/GlassFillerHack.java', 'hacks/InvWalkHack.java', 'hacks/KillauraHack.java',
 'hacks/PrinterHack.java', 'hacks/ReachHack.java', 'hacks/ReplenishHack.java',
 'hacks/ScaffoldWalkHack.java', 'hacks/TriggerBotHack.java', 'hud2/compat/HudItemIcons.java',
 'hud2/compat/HudCompat.java', 'hud2/nanovg/NanoVGUI.java'}


def conflicts(rel, text, theirs):
    if rel in {JAVA + n for n in REPLACED}:
        if rel == JAVA + 'hud2/compat/HudCompat.java':
            # Preserve the target scoreboard/skin adapters; add effect identity.
            return re.sub(r'<<<<<<<[^\n]*\n(.*?)=======\n(.*?)>>>>>>>[^\n]*\n?', lambda m:m[2], text, flags=re.S).encode()
        return theirs
    if rel == JAVA + 'WurstClient.java':
        text = re.sub(r'<<<<<<<[^\n]*\n(.*?)=======\n(.*?)>>>>>>>[^\n]*\n?',lambda m:m[2],text,flags=re.S)
        return text.replace('MC_VERSION = "1.20.4"','MC_VERSION = "1.21.4"').encode()
    if rel == JAVA + 'hack/HackList.java':
        # Read complete declarations, including wrapped initializers.
        original = (ROOT / '.dsh-tmp/v152-siblings/originals/fabric/versions/1.21.4' / rel).read_text('utf-8-sig')
        text = theirs.decode('utf-8-sig')
        pattern = r'public final\s+\w+\s+(\w+)\s*=\s*new\s+\w+\([^;]*?;'
        known = set(re.findall(r'public final\s+\w+\s+(\w+)\s*=', text))
        extra = [m[0] for m in re.finditer(pattern,original,re.S) if m[1] not in known]
        text = text.replace('public HackList()', '\n'.join('\t' + n for n in extra) + '\n\n\tpublic HackList()')
        return text.encode()
    if rel == JAVA + 'commands/RenameCmd.java':
        text = re.sub(r'<<<<<<<[^\n]*\n(.*?)=======\n(.*?)>>>>>>>[^\n]*\n?',lambda m:m[2],text,flags=re.S)
        return text.replace('item.setHoverName(Component.literal(message))','item.set(DataComponents.CUSTOM_NAME, Component.literal(message))').encode()
    if rel == JAVA + 'mixin/GameRendererMixin.java':
        # Rendering/picking signatures belong to the target. World effects are
        # wired through its LevelRenderer callback separately.
        return re.sub(r'<<<<<<<[^\n]*\n(.*?)=======\n(.*?)>>>>>>>[^\n]*\n?',lambda m:m[1],text,flags=re.S).encode()
    return None


def adapt(rel, text):
    text = re.sub(r'new ResourceLocation\(([^()\n]*?)\)', r'ResourceLocation.fromNamespaceAndPath(\1)', text)
    # One-argument resource ids use the parser.
    text = re.sub(r'ResourceLocation.fromNamespaceAndPath\(("[^"\n]*")\)', r'ResourceLocation.parse(\1)', text)
    text = text.replace('net.minecraft.client.gui.screens.OptionsScreen', 'net.minecraft.client.gui.screens.options.OptionsScreen')
    text = text.replace('net.minecraft.client.gui.screens.LanguageSelectScreen', 'net.minecraft.client.gui.screens.options.LanguageSelectScreen')
    text = text.replace('EffectRenderingInventoryScreen', 'AbstractContainerScreen')
    text = text.replace('net.minecraft.world.item.UseAnim', 'net.minecraft.world.item.ItemUseAnimation')
    text = re.sub(r'\bUseAnim\b', 'ItemUseAnimation', text)
    text = text.replace('net.minecraft.world.level.chunk.ChunkStatus', 'net.minecraft.world.level.chunk.status.ChunkStatus')
    text = text.replace('GenericDirtMessageScreen', 'GenericMessageScreen')
    if 'CallbackInfoReturnable<' in text and 'import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;' not in text and 'import org.spongepowered.asm.mixin.injection.callback.*;' not in text:
        text = text.replace('package net.wurstclient.mixin;', 'package net.wurstclient.mixin;\nimport org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;')
    text = text.replace('EnchantmentHelper.hasBindingCurse(', 'net.wurstclient.util.V152ItemCompat.bindingCurse(')
    text = text.replace('EnchantmentHelper.getItemEnchantmentLevel(Enchantments.', 'net.wurstclient.util.EnchantmentUtils.getLevel(Enchantments.')
    text = text.replace('Enchantments.ALL_DAMAGE_PROTECTION', 'Enchantments.PROTECTION')
    if rel == JAVA + 'hud2/compat/HudCompat.java':
        text = text.replace('type.getColor(), texture, u0, v0, u1, v1,', 'type.getColor(), -1, 0, 0, 1, 1,')
    if rel == JAVA + 'hud2/compat/HudItemIcons.java':
        text = text.replace('RenderSystem.getModelViewStack().pushPose()', 'RenderSystem.getModelViewStack().pushMatrix()')
        text = text.replace('RenderSystem.getModelViewStack().popPose()', 'RenderSystem.getModelViewStack().popMatrix()')
        text = text.replace('RenderSystem.getModelViewStack().setIdentity()', 'RenderSystem.getModelViewStack().identity()')
        text = text.replace('RenderSystem.applyModelViewMatrix();', '')
        text = text.replace('String tag = stack.getTag() == null ? "" : stack.getTag().toString();', 'String tag = stack.getComponents().toString();')
        text = text.replace('VertexSorting.ORTHOGRAPHIC_Z', 'com.mojang.blaze3d.ProjectionType.ORTHOGRAPHIC')
    return modern_api(rel, text)


def modern_api(rel, text):
    text = re.sub(r'new (net\.minecraft\.resources\.)?ResourceLocation\(\s*("[^"]+")\s*,\s*("[^"]+")\s*\)',
                  r'\1ResourceLocation.fromNamespaceAndPath(\2, \3)', text)
    for old, new in {
        '.getMinBuildHeight()': '.getMinY()', '.getMaxBuildHeight()': '.getMaxY()',
        '.getNormal()': '.getUnitVec3i()',
        'ItemStack.isSameItemSameTags(': 'ItemStack.isSameItemSameComponents(',
        'Enchantments.MOB_LOOTING': 'Enchantments.LOOTING',
        'Enchantments.BLOCK_EFFICIENCY': 'Enchantments.EFFICIENCY',
        'stack.isEdible()': 'stack.has(net.minecraft.core.component.DataComponents.FOOD)',
        '.isOnCooldown(stack.getItem())': '.isOnCooldown(stack)',
        'MC.player.input.jumping': 'MC.player.input.keyPresses.jump()',
        'ElytraItem.isFlyEnabled(stack)': '(stack.getDamageValue() < stack.getMaxDamage() - 1)',
        'state.isSolidRender(MC.level, pos)': 'state.isSolidRender()',
        'new TextureTarget(width, height, false, false)': 'new TextureTarget(width, height, false)',
    }.items():
        text = text.replace(old, new)
    text = re.sub(r'new TextureTarget\((\w+), (\w+), false, false\)', r'new TextureTarget(\1, \2, false)', text)
    if any(n in rel for n in ('HudGlassBackground', 'OpaiBackground', 'DuoGlassTransition', 'NavigatorBloom')):
        text = re.sub(r'\.resize\((\w+), (\w+), false\)', r'.resize(\1, \2)', text)
        text = text.replace('.clear(false)', '.clear()')
        for name in ('matrix', 'modelView'):
            text = text.replace(name + '.pushPose()', name + '.pushMatrix()').replace(name + '.popPose()', name + '.popMatrix()')
        text = text.replace('RenderSystem.applyModelViewMatrix();', '')
    if rel.endswith(('HudGlassBackground.java', 'OpaiBackground.java')):
        text = re.sub(r'(?:private )?PostChain blur;', lambda m:m[0].replace('PostChain', 'net.wurstclient.util.render.V152BackdropBlur'), text)
        text = re.sub(r'new PostChain\(mc.getTextureManager\(\), mc.getResourceManager\(\),\s*(?:capture|reduced),\s*ResourceLocation.fromNamespaceAndPath\("wurstpenguin",\s*"shaders/post/opai_background.json"\)\)',
                      'new net.wurstclient.util.render.V152BackdropBlur()', text)
        text = re.sub(r'\s*(?:if\(blur != null\) )?blur.resize\([^;]+;', '', text)
        text = text.replace('blur.process(partialTicks)', 'blur.process(' + ('capture' if 'HudGlassBackground' in rel else 'reduced') + ')')
    if rel.endswith('HudManager.java'):
        text = text.replace('graphics.blit(SKIA_TEXTURE, 0, 0, skiaWidth, skiaHeight, 0, 0,\n\t\t\t\tskiaWidth, skiaHeight, skiaWidth, skiaHeight)',
            'graphics.blit(net.minecraft.client.renderer.RenderType::guiTextured, SKIA_TEXTURE, 0, 0, 0, 0, skiaWidth, skiaHeight, skiaWidth, skiaHeight)')
    if rel.endswith('SkiaHudTexture.java'):
        # NativeImage now accepts ARGB instead of the old ABGR pixel method.
        text = text.replace('image.setPixelRGBA(x, y, a << 24 | b << 16 | g << 8 | r)', 'image.setPixel(x, y, a << 24 | r << 16 | g << 8 | b)')
    if rel.endswith('AutoWeaponHack.java'):
        text = text.replace('Enchantment enchantment)', 'net.minecraft.resources.ResourceKey<Enchantment> enchantment)')
        text = text.replace('EnchantmentHelper.getItemEnchantmentLevel(enchantment, stack)', 'net.wurstclient.util.EnchantmentUtils.getLevel(enchantment, stack)')
        for old, tag in [('UNDEAD', 'UNDEAD'), ('ARTHROPOD', 'ARTHROPOD'), ('WATER', 'AQUATIC')]:
            text = text.replace('target.getMobType() == MobType.' + old, 'target.getType().is(net.minecraft.tags.EntityTypeTags.' + tag + ')')
    if rel.endswith('AntiKnockbackHack.java'):
        text = re.sub(r'access.wurst_setKnockbackX\(0\);\s*access.wurst_setKnockbackY\(0\);\s*access.wurst_setKnockbackZ\(0\);',
                      'access.wurst_setPlayerKnockback(java.util.Optional.empty());', text)
    if rel.endswith('AutoArmorHack.java'):
        text = text.replace('stack.getItem() instanceof ArmorItem item ? item.getEquipmentSlot().getIndex() : -1',
                            'net.wurstclient.util.V152ItemCompat.armorType(stack)')
        text = re.sub(r'value =\s*\(int\)\s*\(\(item.getDefense\(\).*?\* 10\);',
                      'value = net.wurstclient.util.V152ItemCompat.armorValue(stack) * 10;', text, flags=re.S)
    if rel.endswith('AutoArmorPlusHack.java'):
        text = text.replace('armor.getEquipmentSlot().getIndex() != type', 'net.wurstclient.util.V152ItemCompat.armorType(candidate) != type')
        text = re.sub(r'EquipmentSlot slot = armor.getEquipmentSlot\(\);.*?\n    return result;',
                      'result += net.wurstclient.util.V152ItemCompat.armorProtection(stack, MC.player);\n    return result;', text, flags=re.S)
    if rel.endswith('CrystalAuraPlusHack.java'):
        text = re.sub(r'for \(var modifier :\s*stack.getAttributeModifiers\(EquipmentSlot.MAINHAND\).*?value \+= modifier.getAmount\(\);',
                      'value += net.wurstclient.util.V152ItemCompat.weaponDamage(stack);', text, flags=re.S)
    if rel.endswith('AutoRefreshTradeHack.java'):
        text = text.replace('EnchantmentHelper.getEnchantments(stack)', 'EnchantmentHelper.getEnchantmentsForCrafting(stack)')
        text = text.replace('e.getKey().getMaxLevel()', 'e.getKey().value().getMaxLevel()')
        text = re.sub(r'\be.getValue\(\)', 'e.getIntValue()', text)
    if rel.endswith('EnchantmentListSetting.java'):
        text = text.replace('contains(Enchantment value)', 'contains(net.minecraft.core.Holder<Enchantment> value)')
        text = text.replace('BuiltInRegistries.ENCHANTMENT.getKey(value)', 'value.unwrapKey().orElseThrow().location()')
        text = text.replace('List<Enchantment> values', 'List<net.minecraft.core.Holder.Reference<Enchantment>> values')
        text = text.replace('BuiltInRegistries.ENCHANTMENT.stream()', 'WurstClient.MC.level.registryAccess().lookupOrThrow(net.minecraft.core.registries.Registries.ENCHANTMENT).listElements()')
        text = re.sub(r'BuiltInRegistries.ENCHANTMENT\s*\.getKey\(e\)', 'e.key().location()', text)
        text = text.replace('Enchantment value = values.get(i)', 'var value = values.get(i)')
        text = text.replace('value.getFullname(1)', 'Enchantment.getFullname(value, 1)')
        text = re.sub(r'\be.getFullname\(1\)', 'Enchantment.getFullname(e, 1)', text)
    if rel.endswith('PacketLoggerHack.java'):
        text = text.replace('filter("pick-inventory", ServerboundPickItemPacket.class);',
            'filter("pick-block", ServerboundPickItemFromBlockPacket.class);\n    filter("pick-entity", ServerboundPickItemFromEntityPacket.class);')
        text = text.replace('if (packet instanceof ServerboundPickItemPacket p) return "slot=" + p.getSlot();',
            'if (packet instanceof ServerboundPickItemFromBlockPacket p) return "position=" + p.pos() + ", includeData=" + p.includeData();\n    if (packet instanceof ServerboundPickItemFromEntityPacket p) return "entity=" + p.id() + ", includeData=" + p.includeData();')
        text = text.replace('"position=" + p.getX() + "," + p.getY() + "," + p.getZ()', '"position=" + p.position()')
    if rel.endswith('BacktrackHack.java'):
        text = text.replace('tp.getId()', 'tp.id()').replace('new Vec3(tp.getX(), tp.getY(), tp.getZ())',
            'net.minecraft.world.entity.PositionMoveRotation.calculateAbsolute(new net.minecraft.world.entity.PositionMoveRotation(tracked, target.getDeltaMovement(), target.getYRot(), target.getXRot()), tp.change(), tp.relatives()).position()')
    # Append the new horizontal-collision flag only to legacy constructors.
    # Balanced calls are necessary because their arguments contain nested calls.
    pattern = r'new (?:net\.minecraft\.network\.protocol\.game\.)?ServerboundMovePlayerPacket\.(Rot|Pos|PosRot|StatusOnly)\('
    offset = 0
    for match in list(re.finditer(pattern, text)):
        start = match.end() + offset
        depth, commas, end = 1, 0, start
        while depth:
            c = text[end]
            if c == '(': depth += 1
            elif c == ')': depth -= 1
            elif c == ',' and depth == 1: commas += 1
            end += 1
        expected = {'Rot': 3, 'Pos': 4, 'PosRot': 6, 'StatusOnly': 1}[match[1]]
        if commas + 1 == expected:
            extra = ', net.wurstclient.WurstClient.MC.player.horizontalCollision'
            text = text[:end-1] + extra + text[end-1:]
            offset += len(extra)
    return render_api(rel, text)


def render_api(rel, text):
    text = re.sub(r'new (net\.minecraft\.resources\.)?ResourceLocation\(', r'\1ResourceLocation.fromNamespaceAndPath(', text)
    if rel.endswith('WurstTitleButton.java'):
        text = text.replace('protected boolean clicked(', 'public boolean isMouseOver(')
        text = text.replace('super.clicked(', 'super.isMouseOver(').replace('(clicked(mouseX, mouseY)', '(isMouseOver(mouseX, mouseY)')
    if rel.endswith('WorldRendererMixin.java'):
        text = text.replace('entityTarget()Lcom/mojang/blaze3d/pipeline/RenderTarget;', 'entityOutlineTarget()Lcom/mojang/blaze3d/pipeline/RenderTarget;')
    if rel.endswith('LeavesHotbarMixin.java'):
        text = text.replace('method = "renderHotbar"', 'method = "renderItemHotbar"')
    if rel.endswith('MultiplayerScreenMixin.java'):
        text = text.replace('@Override public void onClose(){if(opai$menu!=null)opai$menu.close();else minecraft.setScreen(lastScreen);}',
            '@Inject(method="onClose",at=@At("HEAD"),cancellable=true)\n private void opai$close(CallbackInfo ci){if(opai$menu!=null){opai$menu.close();ci.cancel();}}')
    if rel.endswith('IngameHudMixin.java'):
        # GameRenderer owns the single HUD composition pass in this target.
        text = re.sub(r'\s*@Inject\(at = @At\("TAIL"\),\s*method = "render\(Lnet/minecraft/client/gui/GuiGraphics;F\)V"\).*?(?=\n\tprivate static boolean isHudModuleEnabled)', '', text, flags=re.S)
    if rel.endswith('BlockMixin.java'):
        text = re.sub(r'\s*@Inject\(at = @At\("HEAD"\),\s*method = "shouldRenderFace\(Lnet/minecraft/world/level/BlockGetter;.*?(?=\n\tprivate static void fireShouldDrawSide)', '', text, flags=re.S)
        text = text.replace('cancellable = true,\n\t\trequire = 0)', 'cancellable = true)')
    if rel.endswith('AutoVaultHack.java'):
        text = text.replace('Category.ITEMS', 'Category.PLAYER')
    if rel.endswith('HudItemIcons.java'):
        text = text.replace('new TextureTarget(TARGET_SIZE, TARGET_SIZE, true, false)', 'new TextureTarget(TARGET_SIZE, TARGET_SIZE, true)').replace('target.clear(true)', 'target.clear()')
    if rel.endswith('ArmorStatusMod.java'):
        text = text.replace('var sprite = MC.getItemRenderer().getModel(stack, MC.level, MC.player, 0).getParticleIcon();',
            'var state = new net.minecraft.client.renderer.item.ItemStackRenderState();\n    MC.getItemModelResolver().updateForLiving(state, stack, net.minecraft.world.item.ItemDisplayContext.GUI, false, MC.player);\n    var sprite = state.pickParticleIcon(net.minecraft.util.RandomSource.create(0));')
        text = text.replace('InventoryMenu.BLOCK_ATLAS', 'net.minecraft.client.renderer.texture.TextureAtlas.LOCATION_BLOCKS')
        text = re.sub(r'var provider =\s*net.fabricmc.fabric.api.client.rendering.v1.ColorProviderRegistry.ITEM.get\(stack.getItem\(\)\);\s*int color = provider == null \? 0xFFFFFFFF : provider.getColor\(stack, 0\) \| 0xFF000000;',
            'int color = 0xFFFFFFFF;', text)
    if rel.endswith('SkiaTextCache.java'):
        text = text.replace('graphics.blit(entry.id, Math.round(draw.x), Math.round(top),\n\t\t\t\t(int)Math.ceil(width), (int)Math.ceil(height), 0, 0,',
            'graphics.blit(net.minecraft.client.renderer.RenderType::guiTextured, entry.id, Math.round(draw.x), Math.round(top), 0, 0, (int)Math.ceil(width), (int)Math.ceil(height),')
        text = text.replace('image.setPixelRGBA(x, y, a << 24 | b << 16 | g << 8 | r)', 'image.setPixel(x, y, a << 24 | r << 16 | g << 8 | b)')
    if rel.endswith('CoverCache.java'):
        text = text.replace('nativeImage.setPixelRGBA(\n                                x, y, (p & 0xFF00FF00) | ((p >> 16) & 255) | ((p & 255) << 16))',
                            'nativeImage.setPixel(x, y, p)')
    if rel.endswith('LeavesDamage.java'):
        text = text.replace('public int getMinBuildHeight()', 'public int getMinY()')
        text = text.replace('CombatRules.getDamageAfterAbsorb(\n            damage,', 'CombatRules.getDamageAfterAbsorb(\n            target, damage, world.damageSources().explosion(null, null),')
        text = text.replace('EnchantmentHelper.getDamageProtection(\n                target.getArmorSlots(), world.damageSources().explosion(null, null))',
                            'net.wurstclient.util.V152ItemCompat.explosionProtection(target)')
    if rel.endswith('ClientPlayerInteractionManagerMixin.java') and 'import net.wurstclient.hack.HackList;' not in text:
        text = text.replace('package net.wurstclient.mixin;', 'package net.wurstclient.mixin;\nimport net.wurstclient.hack.HackList;')
    if rel.endswith('MoonEntityGlow.java'):
        # LAST supplies a pose stack separately from the global camera view.
        # Frustum culling must use the same combined view as model rendering.
        text = text.replace('matrices.last().pose(), RenderSystem.getProjectionMatrix()',
            'new org.joml.Matrix4f(RenderSystem.getModelViewMatrix()).mul(matrices.last().pose()), RenderSystem.getProjectionMatrix()')
        if 'RenderSystem.colorMask(true, true, true, true);' not in text:
            text = text.replace('        outlines.setColor(255, 255, 255, 255);',
                '        RenderSystem.colorMask(true, true, true, true);\n'
                '        RenderSystem.setShaderColor(1, 1, 1, 1);\n'
                '        RenderSystem.disableDepthTest();\n'
                '        RenderSystem.disableCull();\n'
                '        GL11.glDisable(GL11.GL_STENCIL_TEST);\n'
                '        outlines.setColor(255, 255, 255, 255);')
        text = text.replace('new BufferBuilder(65536)', 'new ByteBufferBuilder(65536)').replace('mask.clear(false)', 'mask.clear()')
        text = re.sub(r'mc.getEntityRenderDispatcher\(\)\s*\.getRenderer\(entity\)\s*\.render\(\s*entity,\s*Mth.rotLerp\(partialTicks, entity.yRotO, entity.getYRot\(\)\),\s*partialTicks,\s*matrices,\s*maskOnly,\s*15728880\);',
            'mc.getEntityRenderDispatcher().render(entity, 0, 0, 0, partialTicks, matrices, maskOnly, 15728880);', text)
        for old, new in [('vertex(double x, double y, double z)', 'addVertex(float x, float y, float z)'), ('color(int r, int g, int b, int a)', 'setColor(int r, int g, int b, int a)'), ('uv(float u, float v)', 'setUv(float u, float v)'), ('overlayCoords(int u, int v)', 'setUv1(int u, int v)'), ('uv2(int u, int v)', 'setUv2(int u, int v)'), ('normal(float x, float y, float z)', 'setNormal(float x, float y, float z)')]:
            text = text.replace(old, new)
    if rel.endswith('TestControlServer.java'):
        text = text.replace('.checkForBackupAndLoad(', '.openWorld(').replace('player.input.jumping', 'player.input.keyPresses.jump()')
        text = text.replace('ReceivingLevelScreen(() -> false)', 'ReceivingLevelScreen(() -> false, net.minecraft.client.gui.screens.ReceivingLevelScreen.Reason.OTHER)')
        text = text.replace('new net.minecraft.server.level.progress.StoringChunkProgressListener(2)', 'net.minecraft.server.level.progress.StoringChunkProgressListener.create(2)')
        text = text.replace('new net.minecraft.world.level.GameRules()', 'new net.minecraft.world.level.GameRules(net.minecraft.world.flag.FeatureFlags.DEFAULT_FLAGS)')
    return text


def configure(project):
    from host_netty import configure as configure_netty
    configure_netty(project)
    import sync_v152_fabric1204 as old
    previous = old.TARGET
    try:
        old.TARGET = project
        old.configure_build()
    finally:
        old.TARGET = previous
    mc = project.name
    if mc not in ('1.21.2', '1.21.3', '1.21.4'):
        raise ValueError('Unreviewed post-effect family: ' + mc)
    props = project / 'gradle.properties'
    props.write_text(props.read_text('utf-8-sig').replace('1.5.2-dev-Fabric-1.20.4','1.5.2-dev-Fabric-' + mc),encoding='utf-8')
    seed_version = '2.15.2' if mc == '1.21.4' else '2.15.1'
    source = ROOT / ('.dsh-tmp/v152-upstream-seed/seedcrackerX-' + seed_version + '.jar')
    digest = {'2.15.2':'5c961e6a3d24b81fe4d0ee58635911d941a58e192bd7a934808a9f27841ca06a',
              '2.15.1':'a1529a9c69332c9f5f96c73044b9cfe02f65c355045f99aec8178daebe4f7b76'}[seed_version]
    raw = source.read_bytes()
    if hashlib.sha256(raw).hexdigest() != digest:
        raise ValueError('SeedCrackerX 2.15.2 hash differs from reviewed binary')
    libs = project / 'libs'
    libs.mkdir(exist_ok=True)
    (libs / source.name).write_bytes(raw)
    deps = libs / 'seedcrackerx-deps'
    deps.mkdir(exist_ok=True)
    with zipfile.ZipFile(source) as z:
        meta = json.loads(z.read('fabric.mod.json'))
        assert meta['depends']['minecraft'] == ('>=1.21.4 <=1.21.4' if mc == '1.21.4' else '>=1.21.2 <=1.21.3')
        for jar in meta['jars']:
            (deps / Path(jar['file']).name).write_bytes(z.read(jar['file']))
    build = project / 'build.gradle'
    build.write_text(re.sub(r'seedcrackerX:2\.\d+\.\d+', 'seedcrackerX:' + seed_version, build.read_text('utf-8-sig')),encoding='utf-8')
    build.write_text(build.read_text('utf-8-sig').replace('include: ["*.jar"]', 'include: ["cloth-config*.jar"]'),encoding='utf-8')
    notice = project / 'src/main/resources/META-INF/licenses/seedcrackerx/NOTICE.txt'
    notice.write_text('SeedCrackerX ' + seed_version + ', MIT, KaptainWutax / 19MisterX98.\n'
        'Official unmodified binary for Minecraft ' + mc + '.\n'
        'https://github.com/19MisterX98/SeedcrackerX/releases/tag/' + seed_version + '\n'
        'Bundled jar SHA-256: ' + digest + '\nNested metadata and licenses retained.\n',encoding='utf-8')
    from sync_v152_1201 import REPLACED_SCAFFOLD
    for rel in REPLACED_SCAFFOLD:
        (project / rel).unlink(missing_ok=True)
    # Shared item operations use data components and registry enchantments.
    helper = project / (JAVA + 'util/V152ItemCompat.java')
    helper.write_text('''package net.wurstclient.util;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.core.component.DataComponents;
public final class V152ItemCompat {
 private V152ItemCompat() {}
 public static boolean bindingCurse(ItemStack stack) {
  return EnchantmentUtils.getLevel(Enchantments.BINDING_CURSE, stack)>0;
 }
 public static int armorType(ItemStack stack) {
  var equipped=stack.get(DataComponents.EQUIPPABLE);
  if(equipped==null)return -1;
  EquipmentSlot slot=equipped.slot();
  return slot==EquipmentSlot.FEET?0:slot==EquipmentSlot.LEGS?1:slot==EquipmentSlot.CHEST?2:slot==EquipmentSlot.HEAD?3:-1;
 }
 public static int armorValue(ItemStack stack) {
  double[] points={0};
  stack.forEachModifier(EquipmentSlotGroup.ARMOR,(attribute,modifier)->{
   if(attribute.is(Attributes.ARMOR)||attribute.is(Attributes.ARMOR_TOUGHNESS))points[0]+=modifier.amount();
  });
  return (int)Math.ceil(points[0]);
 }
}
''',encoding='utf-8')
    templates = Path(__file__).parent / 'templates/fabric1214'
    helper.write_bytes((templates / 'V152ItemCompat.java').read_bytes())
    (project / (JAVA + 'hud2/compat/HudTextures.java')).write_bytes((templates / 'HudTextures.java').read_bytes())
    blur_helper = project / (JAVA + 'util/render/V152BackdropBlur.java')
    blur_helper.write_bytes((templates / 'V152BackdropBlur.java').read_bytes())
    (project / (JAVA + 'mixin/LeavesPlayerRendererMixin.java')).write_bytes((templates / 'LeavesPlayerRendererMixin.java').read_bytes())
    (project / (JAVA + 'mixin/NeoRavenPreparingLevelMixin.java')).write_bytes((templates / 'NeoRavenPreparingLevelMixin.java').read_bytes())
    effect = project / 'src/main/resources/assets/wurstpenguin/post_effect/opai_background.json'
    effect.parent.mkdir(parents=True, exist_ok=True)
    effect.write_text(json.dumps({'targets': {'swap': {}}, 'passes': [
        {'program': 'minecraft:post/box_blur',
         'inputs': [{'sampler_name': 'In', 'target': source, 'bilinear': True}],
         'output': dest, 'uniforms': [{'name':'BlurDir','values':direction}, {'name':'Radius','values':[4.0]}]}
        for source, dest, direction in [('minecraft:main','swap',[1.0,0.0]),('swap','minecraft:main',[0.0,1.0])]
    ]}, indent=2) + '\n', encoding='utf-8')
    # ShaderManager scans shaders/**/*.json as programs; legacy post-chain JSON
    # lives under post_effect/ in this generation.
    (project / 'src/main/resources/assets/wurstpenguin/shaders/post/opai_background.json').unlink(missing_ok=True)
    # This target-only module was not changed in the donor, but categories were.
    vault = project / (JAVA + 'hacks/AutoVaultHack.java')
    vault.write_text(render_api(vault.name, vault.read_text('utf-8-sig')), encoding='utf-8')
    initializer = project / (JAVA + 'WurstInitializer.java')
    text = initializer.read_text('utf-8-sig')
    marker = 'net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents.LAST.register'
    if marker not in text:
        text = text.replace('net.wurstclient.seed.SeedCrackerBridge.initialize();', '''net.wurstclient.seed.SeedCrackerBridge.initialize();
            net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents.LAST.register(context -> {
                var matrices = context.matrixStack();
                float delta = context.tickCounter().getGameTimeDeltaPartialTick(false);
                net.wurstclient.event.EventManager.fire(new net.wurstclient.events.RenderListener.RenderEvent(matrices, delta));
                WurstClient.INSTANCE.getPostEffectQueue().flush(matrices, delta);
                net.wurstclient.util.render.MoonEntityGlow.INSTANCE.flush(matrices, delta);
            });''')
    initializer.write_text(text, encoding='utf-8')
    config = project / 'src/main/resources/wurstpenguin.mixins.json'
    data = json.loads(config.read_text('utf-8-sig'))
    data['compatibilityLevel'] = 'JAVA_21'
    for name in ('ClientCommonNetworkHandlerMixin', 'ToastManagerAccessor'):
        if name not in data['client'] and (project / (JAVA + 'mixin/' + name + '.java')).is_file():
            data['client'].append(name)
    config.write_text(json.dumps(data, indent=2) + '\n', encoding='utf-8')
    port_post_effects(project)


def port_post_effects(project):
    """Replace the target's historical no-op queue with the baseline effect path."""
    source = ROOT / 'fabric/versions/1.20.4'
    # RenderType's default output state binds the game's main framebuffer in
    # this generation. Mask tasks must draw directly into the bound mask.
    target_hack = project / (JAVA + 'hacks/TargetShaderHack.java')
    target_text = target_hack.read_text('utf-8-sig')
    target_text = target_text.replace('RenderUtils.drawSolidBox(\n\t\t\t\ttaskMatrices, box, Color.WHITE.getRGB(),\n\t\t\t\t!throughWalls.isChecked())',
                                    'drawMask(taskMatrices, box)')
    if 'private void drawMask(' not in target_text:
        target_text = target_text.replace('\tprivate Entity resolveTarget()', '''    private void drawMask(PoseStack matrices, AABB box)
    {
        com.mojang.blaze3d.systems.RenderSystem.setShader(net.minecraft.client.renderer.CoreShaders.POSITION_COLOR);
        if(throughWalls.isChecked()) com.mojang.blaze3d.systems.RenderSystem.disableDepthTest();
        else com.mojang.blaze3d.systems.RenderSystem.enableDepthTest();
        com.mojang.blaze3d.systems.RenderSystem.disableCull();
        com.mojang.blaze3d.systems.RenderSystem.disableBlend();
        var buffer = com.mojang.blaze3d.vertex.Tesselator.getInstance().begin(
            com.mojang.blaze3d.vertex.VertexFormat.Mode.QUADS,
            com.mojang.blaze3d.vertex.DefaultVertexFormat.POSITION_COLOR);
        RenderUtils.drawSolidBox(matrices, buffer, box.move(RenderUtils.getCameraPos().reverse()), Color.WHITE.getRGB());
        com.mojang.blaze3d.vertex.BufferUploader.drawWithShader(buffer.buildOrThrow());
    }

\tprivate Entity resolveTarget()''')
    target_hack.write_text(target_text, encoding='utf-8')
    text = (source / (JAVA + 'util/render/PostEffectQueue.java')).read_text('utf-8-sig')
    text = text.replace('new TextureTarget(width, height, true,\n\t\t\tMinecraft.ON_OSX)', 'new TextureTarget(width, height, true)')
    text = text.replace('target.renderTarget.clear(Minecraft.ON_OSX)', 'target.renderTarget.clear()')
    text = text.replace('target.postChain.process(partialTicks)', '''client.getShaderManager().getProgram(new net.minecraft.client.renderer.ShaderProgram(
            ResourceLocation.fromNamespaceAndPath("wurst", "program/target_effect"),
            DefaultVertexFormat.POSITION, net.minecraft.client.renderer.ShaderDefines.EMPTY))
            .getUniform("Time").set((System.nanoTime() % 20_000_000_000L) / 20_000_000_000F);
        target.postChain.process(target.renderTarget, com.mojang.blaze3d.resource.GraphicsResourceAllocator.UNPOOLED)''')
    text = text.replace('ResourceLocation shader = ResourceLocation.tryBuild(NAMESPACE,\n\t\t\t"shaders/post/target_" + effect.fileName + ".json");',
                        'ResourceLocation shader = ResourceLocation.fromNamespaceAndPath(NAMESPACE, "target_" + effect.fileName);')
    text = re.sub(r'postChain = new PostChain\(client.getTextureManager\(\),\s*client.getResourceManager\(\), renderTarget, shader\);',
                  'postChain = client.getShaderManager().getPostChain(shader, java.util.Set.of(ResourceLocation.withDefaultNamespace("main")));\n            if(postChain == null) throw new IOException("Missing target effect: " + shader);', text)
    # Resolve the post chain every frame so resource reloads do not keep a stale program.
    text = text.replace('if(target != null)\n\t\t\treturn target;', '''if(target != null) {
            target.postChain = client.getShaderManager().getPostChain(
                ResourceLocation.fromNamespaceAndPath(NAMESPACE, "target_" + effect.fileName),
                java.util.Set.of(ResourceLocation.withDefaultNamespace("main")));
            if(target.postChain == null) throw new IOException("Missing target effect: " + effect);
            return target;
        }''')
    text = text.replace('private final PostChain postChain;', 'private PostChain postChain;')
    text = text.replace('postChain.resize(width, height)', 'renderTarget.resize(width, height)')
    text = text.replace('import com.mojang.blaze3d.vertex.VertexSorting;', 'import com.mojang.blaze3d.ProjectionType;')
    text = text.replace('VertexSorting.ORTHOGRAPHIC_Z', 'ProjectionType.ORTHOGRAPHIC')
    text = text.replace('PoseStack modelView = RenderSystem.getModelViewStack()', 'var modelView = RenderSystem.getModelViewStack()')
    text = text.replace('modelView.pushPose()', 'modelView.pushMatrix()').replace('modelView.popPose()', 'modelView.popMatrix()').replace('modelView.setIdentity()', 'modelView.identity()')
    text = text.replace('RenderSystem.applyModelViewMatrix();', '')
    text = text.replace('GameRenderer::getPositionTexShader', 'net.minecraft.client.renderer.CoreShaders.POSITION_TEX')
    text = text.replace('BufferBuilder buffer = Tesselator.getInstance().getBuilder();\n\t\t\tbuffer.begin(', 'BufferBuilder buffer = Tesselator.getInstance().begin(')
    text = text.replace('buffer.vertex(', 'buffer.addVertex(').replace('.uv(', '.setUv(').replace('.endVertex()', '')
    text = text.replace('buffer.end()', 'buffer.buildOrThrow()')
    (project / (JAVA + 'util/render/PostEffectQueue.java')).write_text(text, encoding='utf-8')
    for effect in ('outline', 'pulse', 'gradient', 'smoke'):
        data = json.loads((source / f'src/main/resources/assets/wurst/shaders/post/target_{effect}.json').read_text())
        converted = {'targets': {n:{} for n in data['targets']}, 'passes': [
            {'program': 'wurst:program/target_effect' if p['name'] == 'wurst:target_effect' else 'minecraft:post/blit',
             'inputs': [{'sampler_name': 'In', 'target': p['intarget']}], 'output': p['outtarget'],
             'uniforms': p.get('uniforms', [])} for p in data['passes']]}
        dest = project / f'src/main/resources/assets/wurst/post_effect/target_{effect}.json'
        dest.parent.mkdir(parents=True, exist_ok=True)
        dest.write_text(json.dumps(converted, indent=2) + '\n', encoding='utf-8')
    for ext in ('json', 'vsh', 'fsh'):
        dest = project / f'src/main/resources/assets/wurst/shaders/program/target_effect.{ext}'
        text = (source / dest.relative_to(project)).read_text('utf-8-sig')
        text = text.replace('wurst:target_effect', 'wurst:program/target_effect').replace('DiffuseSampler', 'InSampler')
        if ext == 'json':
            data = json.loads(text)
            data.pop('blend', None)
            data.pop('attributes', None)
            text = json.dumps(data, indent=2) + '\n'
        dest.write_text(text, encoding='utf-8')
    test = project / 'src/test/java/net/wurstclient/util/render/PostEffectResourcesTest.java'
    text = test.read_text('utf-8-sig')
    text = text.replace('ROOT.resolve("post/target_" + name + ".json")', 'ROOT.resolve("../post_effect/target_" + name + ".json")')
    text = text.replace('assertTrue(json.getAsJsonArray("targets").contains(\n\t\t\t\tJsonParser.parseString("\\\"swap\\\"")));',
        'assertTrue(json.getAsJsonObject("targets").has("swap"));\n            assertEquals("wurst:program/target_effect", json.getAsJsonArray("passes").get(0).getAsJsonObject().get("program").getAsString());')
    test.write_text(text, encoding='utf-8')
