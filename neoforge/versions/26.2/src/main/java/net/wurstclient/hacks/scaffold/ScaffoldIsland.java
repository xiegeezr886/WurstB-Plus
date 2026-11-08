/* Adapted directly from OpenOpal (GPL-3.0); see META-INF/licenses/openopal/LICENSE. */
package net.wurstclient.hacks.scaffold;
import net.minecraft.client.gui.GuiGraphicsExtractor;
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
    public void render(GuiGraphicsExtractor context, float posX, float posY) {
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
