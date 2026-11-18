"""Pre-data-driven-enchantment component API, ported from Fabric 1.21."""
from pathlib import Path
import re

JAVA = 'src/main/java/net/wurstclient/'
REPLACED = {'AutoAnchorHack', 'AutoArmorPlusHack', 'AutoClickerHack',
            'AutoRefreshTradeHack', 'AutoWeaponHack', 'BacktrackHack',
            'ElytraGrimAccelerateHack', 'FireworkElytraFlyHack',
            'GlassFillerHack', 'PrinterHack', 'ReplenishHack', 'HudTextures',
            'WurstTitleMenu'}


def conflicts(rel, text, theirs):
    if Path(rel).stem in REPLACED:
        return theirs
    if rel in (JAVA+'WurstClient.java', JAVA+'hack/HackList.java'):
        text = re.sub(r'<<<<<<<[^\n]*\n(.*?)=======\n(.*?)>>>>>>>[^\n]*\n?',
                      lambda m:m[2], text, flags=re.S)
        text = text.replace('MC_VERSION = "1.21"', 'MC_VERSION = "1.20.6"')
        return text.encode()
    return None


def adapt(rel, text):
    text = text.replace('ResourceLocation.fromNamespaceAndPath(', 'new ResourceLocation(')
    text = text.replace('ResourceLocation.withDefaultNamespace(', 'new ResourceLocation("minecraft", ')
    text = text.replace('net.minecraft.resources.new ResourceLocation(', 'new net.minecraft.resources.ResourceLocation(')
    if rel.endswith('AutoWeaponHack.java'):
        text = text.replace('net.minecraft.resources.ResourceKey<Enchantment> enchantment', 'Enchantment enchantment')
    if rel.endswith('EnchantmentListSetting.java'):
        text = re.sub(r'Enchantment.getFullname\((e|value), 1\)', r'\1.value().getFullname(1)', text)
    if rel.endswith('LeavesDamage.java'):
        text = text.replace('target, damage, world.damageSources()', 'damage, world.damageSources()')
    if rel.endswith('ClientPlayerInteractionManagerMixin.java') and 'import net.wurstclient.WurstClient;' not in text:
        text = text.replace('package net.wurstclient.mixin;', 'package net.wurstclient.mixin;\nimport net.wurstclient.WurstClient;')
    if rel.endswith('WurstInitializer.java'):
        text = text.replace('context.tickCounter().getGameTimeDeltaPartialTick(false)', 'context.tickDelta()')
    if rel.endswith('TargetShaderHack.java'):
        text = text.replace('var buffer = com.mojang.blaze3d.vertex.Tesselator.getInstance().begin(',
            'var buffer = com.mojang.blaze3d.vertex.Tesselator.getInstance().getBuilder();\n        buffer.begin(')
        text = text.replace('buffer.buildOrThrow()', 'buffer.end()')
    if rel.endswith('MoonEntityGlow.java'):
        text = text.replace('new ByteBufferBuilder(65536)', 'new BufferBuilder(65536)')
        for old, new in [('addVertex(float x, float y, float z)', 'vertex(double x, double y, double z)'),
            ('setColor(int r, int g, int b, int a)', 'color(int r, int g, int b, int a)'),
            ('setUv(float u, float v)', 'uv(float u, float v)'),
            ('setUv1(int u, int v)', 'overlayCoords(int u, int v)'),
            ('setUv2(int u, int v)', 'uv2(int u, int v)'),
            ('setNormal(float x, float y, float z)', 'normal(float x, float y, float z)')]:
            text = text.replace(old, new)
    return text


def configure(project):
    from adapt_v152_fabric1211 import configure as component_configure
    component_configure(project, seed_version='2.14.7',
        seed_digest='f453bae397cfa66f1667e0ed3ce1983c96006ddde9b90b40ead260fd80a1cded',
        seed_mc='1.20.6')
    for path in (project/'src').rglob('*.java'):
        text = path.read_text('utf-8-sig')
        adjusted = adapt(str(path), text)
        if adjusted != text:
            path.write_text(adjusted, encoding='utf-8')
