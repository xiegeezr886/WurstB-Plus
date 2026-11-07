# -*- coding: utf-8 -*-
"""Extra API fixes for the 1.21.6-1.21.8 wrapper HUD port."""
import re
from pathlib import Path

STUB = '''package net.wurstclient.hud2.compat;

public final class HudTextures
{
	private HudTextures() {}

	public static int id(String path)
	{
		return -1;
	}

	public static int localPlayerSkin()
	{
		return -1;
	}
}
'''


def patch(base):
    root = Path(base) / 'src/main/java/net/wurstclient'
    # GUIRenderListener import (the original has it inside a comment).
    p = root / 'events/GUIRenderListener.java'
    t = p.read_text(encoding='utf-8')
    if 'import net.wurstclient.hud2.HackHudElement;' not in t:
        t = t.replace('import net.wurstclient.event.Listener;',
                      'import net.wurstclient.event.Listener;\n'
                      'import net.wurstclient.hud2.HackHudElement;', 1)
        p.write_text(t, encoding='utf-8', newline='\n')

    (root / 'hud2/compat/HudTextures.java').write_text(
        STUB, encoding='utf-8', newline='\n')

    for p in root.rglob('*.java'):
        t = p.read_text(encoding='utf-8')
        o = t
        t = t.replace('getSkin().body().texturePath()', 'getSkin().texture()')
        t = t.replace('getSkinTextureLocation().toString()',
                      'getSkin().texture().toString()')
        t = t.replace('MC.player.getInventory().selected',
                      'MC.player.getInventory().getSelectedSlot()')
        t = t.replace(
            'float exhaustion = MC.player == null ? 0\n\t\t\t'
            ': MC.player.getFoodData().getExhaustionLevel();',
            'float exhaustion = 0;')
        t = re.sub(r'\n\s*target\.bindWrite\(false\);', '', t)
        if t != o:
            p.write_text(t, encoding='utf-8', newline='\n')

    compat = root / 'hud2/compat/HudCompat.java'
    t = compat.read_text(encoding='utf-8')
    old = '''		List<ItemStack> items = new ArrayList<>();
		for(int i = 3; i >= 0; i--)
			items.add(WurstClient.MC.player.getInventory().getArmor(i));
		return items;'''
    new = '''		List<ItemStack> items = new ArrayList<>();
		for(net.minecraft.world.entity.EquipmentSlot slot
			: new net.minecraft.world.entity.EquipmentSlot[]{
				net.minecraft.world.entity.EquipmentSlot.HEAD,
				net.minecraft.world.entity.EquipmentSlot.CHEST,
				net.minecraft.world.entity.EquipmentSlot.LEGS,
				net.minecraft.world.entity.EquipmentSlot.FEET})
			items.add(WurstClient.MC.player.getItemBySlot(slot));
		return items;'''
    t = t.replace(old, new)
    compat.write_text(t, encoding='utf-8', newline='\n')

    nvg = root / 'hud2/nanovg/NanoVGUI.java'
    t = nvg.read_text(encoding='utf-8')
    t = t.replace('\t\tgraphics.flush();\n', '')
    t = re.sub(
        r'public static void flushDeferredItems\(GuiGraphicsExtractor graphics\)'
        r'.*?\n\t\}',
        '''public static void flushDeferredItems(GuiGraphicsExtractor graphics)
	{
		if(ITEMS.isEmpty())
			return;
		List<DeferredItem> items = new ArrayList<>(ITEMS);
		ITEMS.clear();
		for(DeferredItem item : items)
		{
			if(item.scissor() != null)
			{
				float[] s = item.scissor();
				graphics.enableScissor(Math.round(s[0]), Math.round(s[1]),
					Math.round(s[2]), Math.round(s[3]));
			}
			RenderUtils.drawItem(graphics, item.stack(), item.x(), item.y(),
				item.large());
			if(item.scissor() != null)
				graphics.disableScissor();
		}
	}''', t, flags=re.S)
    t = re.sub(
        r'Matrix4f pose = new Matrix4f\(graphics\.pose\(\)\.last\(\)\.pose\(\)\);'
        r'.*?ITEMS\.add\(new DeferredItem\(stack, x, y, large, combined, scissor\)\);',
        'ITEMS.add(new DeferredItem(stack, x, y, large, scissor));', t,
        flags=re.S)
    t = re.sub(
        r'private record DeferredItem\(ItemStack stack, int x, int y, '
        r'boolean large,\s*Matrix4f pose, float\[\] scissor\)',
        'private record DeferredItem(ItemStack stack, int x, int y, '
        'boolean large,\n\t\tfloat[] scissor)', t)
    nvg.write_text(t, encoding='utf-8', newline='\n')

    editor = root / 'hud2/GuiEditHUD.java'
    t = editor.read_text(encoding='utf-8')
    t = t.replace(
        'public void render(GuiGraphicsExtractor graphics, int mouseX, int mouseY,\n'
        '\t\tfloat partialTicks)',
        'public void render(net.minecraft.client.gui.GuiGraphics graphics,\n'
        '\t\tint mouseX, int mouseY, float partialTicks)')
    t = t.replace('NanoVGUI.pass(graphics, () -> {',
                  'NanoVGUI.pass(new GuiGraphicsExtractor(graphics), () -> {')
    editor.write_text(t, encoding='utf-8', newline='\n')
    print('adapted wrapper', base)


if __name__ == '__main__':
    import sys
    for b in sys.argv[1:]:
        patch(b)
