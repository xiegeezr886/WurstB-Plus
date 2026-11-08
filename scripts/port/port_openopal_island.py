"""Adapt ScaffoldIsland's block counter to the host NanoVG renderer."""
from refactor_openopal_combat import ROOT,PLAN,PACKAGE,projects,replace
from port_openopal_input import original,HEADER

ISLAND=HEADER+'''package net.wurstclient.hacks.scaffold;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.wurstclient.WurstClient;
import net.wurstclient.opal.Animation;
import net.wurstclient.opal.Easing;
import net.wurstclient.hud2.nanovg.NanoVGUI;
import net.wurstclient.hud2.nanovg.HudFonts;

/** ScaffoldIsland layout and counter animation, rendered with the host fonts. */
public final class ScaffoldIsland {
    private float width = 160;
    private Animation blockCounterAnimation;
    public float getWidth() { return width; }
    public float getHeight() { return 25; }
    public void onDisable() { blockCounterAnimation = null; }
    public void render(GuiGraphics context, float posX, float posY) {
        var mc = WurstClient.MC;
        if(mc.player == null) return;
        ItemStack handStack = mc.player.getMainHandItem().getItem() instanceof BlockItem
            ? mc.player.getMainHandItem() : mc.player.getOffhandItem();
        boolean isBlock = handStack.getItem() instanceof BlockItem;
        int stackSize = isBlock ? handStack.getCount() : 0;
        String stackText = stackSize + " ";
        String postStackText = "block" + (stackSize != 1 ? "s" : "");
        double speed = Math.hypot(mc.player.getX() - mc.player.xo, mc.player.getZ() - mc.player.zo)
            * 20 * WurstClient.INSTANCE.getHax().timerHack.getTimerSpeed();
        String bpsText = Math.round(speed * 100.0) / 100.0 + " b/s";
        var nvg = NanoVGUI.context();
        float stackWidth = nvg.getTextWidth(stackText, 8, HudFonts.DEMIBOLD);
        width = 130 + Math.max(stackWidth + nvg.getTextWidth(postStackText, 8, HudFonts.MEDIUM),
            nvg.getTextWidth(bpsText, 6, HudFonts.MEDIUM));
        int color = isBlock ? 0xFF000000 | ((BlockItem)handStack.getItem()).getBlock()
            .defaultMapColor().col : -1;
        nvg.drawRoundedRect(posX, posY, width, 25, 12.5F, 0xD0181818);
        nvg.drawRoundedRect(posX + 5.5F, posY + 4, 17, 17, 8.25F, color & 0xFFFFFF | 0x78000000);
        if(isBlock) NanoVGUI.item(context, handStack, (int)(posX + 6), (int)(posY + 4.5F), false);
        nvg.drawRoundedRect(posX + 28, posY + 11.5F, 85, 2.5F, 1.5F, darker(color, 0.55F));
        float scaledWidth = Math.min(stackSize, 64) / 64F * 85;
        if(blockCounterAnimation == null) {
            blockCounterAnimation = new Animation(Easing.EASE_OUT_EXPO, 200);
            blockCounterAnimation.setValue(scaledWidth);
        } else blockCounterAnimation.run(scaledWidth);
        if(stackSize > 0) nvg.drawHorizontalGradient(posX + 28, posY + 11.5F,
            blockCounterAnimation.getValue(), 2.5F, darker(color, 0.4F), color);
        nvg.drawText(stackText, posX + 120, posY + 8, color, 8, HudFonts.DEMIBOLD);
        nvg.drawText(postStackText, posX + 120 + stackWidth, posY + 8, -1, 8, HudFonts.MEDIUM);
        nvg.drawText(bpsText, posX + 120, posY + 17, 0xFFA0A0A0, 6, HudFonts.MEDIUM);
    }
    private int darker(int color, float amount) {
        float factor = 1 - amount;
        return color & 0xFF000000 | (int)(((color >> 16) & 255) * factor) << 16
            | (int)(((color >> 8) & 255) * factor) << 8 | (int)((color & 255) * factor);
    }
}
'''

def main():
    pending={}
    for p in projects():
        gui=(p/PACKAGE/'events/GUIRenderListener.java').read_text(encoding='utf-8')
        graphics='GuiGraphicsExtractor' if 'GuiGraphicsExtractor' in gui else 'GuiGraphics'
        pending[p/PACKAGE/'hacks/scaffold/ScaffoldIsland.java']=ISLAND.replace('GuiGraphics',graphics)
        for cls in ['Animation','Easing']:
            s=original('utility/render/animation/'+cls+'.java')
            s=s.replace('wtf.opal.utility.render.animation','net.wurstclient.opal')
            s=s.replace('net.minecraft.util.math.MathHelper','net.minecraft.util.Mth').replace('MathHelper','Mth')
            pending[p/PACKAGE/('opal/'+cls+'.java')]=HEADER+s
        path=p/PACKAGE/'hacks/ScaffoldWalkHack.java'
        s=path.read_text(encoding='utf-8')
        s=replace(s,'public final class ScaffoldWalkHack extends Hack','public final class ScaffoldWalkHack extends Hack')
        s=replace(s,'implements UpdateListener,','implements net.wurstclient.events.GUIRenderListener, UpdateListener,')
        s=replace(s,'\tprivate int placeTick = -1;', '''    private final net.wurstclient.hacks.scaffold.ScaffoldIsland island =
        new net.wurstclient.hacks.scaffold.ScaffoldIsland();
    private final CheckboxSetting blockCounter = new CheckboxSetting("Block counter", true);
	private int placeTick = -1;''')
        s=replace(s,'\t\taddSetting(safeWalk);','\t\taddSetting(safeWalk);\n\t\taddSetting(blockCounter);')
        s=replace(s,'\tprotected void onEnable()\n\t{','\tprotected void onEnable()\n\t{\n\t\tEVENTS.add(net.wurstclient.events.GUIRenderListener.class, this);')
        s=replace(s,'\tprotected void onDisable()\n\t{','\tprotected void onDisable()\n\t{\n\t\tEVENTS.remove(net.wurstclient.events.GUIRenderListener.class, this);\n\t\tisland.onDisable();')
        s=replace(s,'\t@Override\n\tpublic void onRender(PoseStack',f'''    @Override
    public void onRenderGUI(net.minecraft.client.gui.{graphics} context, float partialTicks) {{
        if(!blockCounter.isChecked() || MC.player == null || MC.level == null) return;
        net.wurstclient.hud2.nanovg.NanoVGUI.pass(context, () -> island.render(context,
            (MC.getWindow().getGuiScaledWidth() - island.getWidth()) / 2, 20));
    }}

	@Override
	public void onRender(PoseStack''')
        pending[path]=s
    for path,s in pending.items():
        if path.exists():
            backup=PLAN/'before-island-port'/path.relative_to(ROOT)
            backup.parent.mkdir(parents=True,exist_ok=True)
            assert not backup.exists(),backup
            backup.write_bytes(path.read_bytes())
        path.parent.mkdir(parents=True,exist_ok=True)
        path.write_text(s,encoding='utf-8',newline='\n')
    print('ScaffoldIsland source adaptation:',len(pending),'files')


if __name__=='__main__':main()
