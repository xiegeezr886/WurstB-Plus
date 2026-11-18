package net.wurstclient.gui.title;

import net.wurstclient.util.render.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.wurstclient.clickgui2.animation.HoverAnimation;
import net.wurstclient.gui.CustomRenderedButton;
import net.wurstclient.hud2.nanovg.NanoVGUI;

/** Nextgen white translucent material with the existing launcher geometry. */
final class WurstTitleButton extends AbstractButton implements CustomRenderedButton
{
    private static final Identifier GLOW = Identifier.fromNamespaceAndPath("wurstpenguin", "title-nextgen/button-glow.png");
    private final Identifier icon;
    private final Runnable action;
    private final boolean dangerous;
    private final HoverAnimation hover = new HoverAnimation(16);
    private float opacity, drawX, drawY;

    WurstTitleButton(int size, String label, Identifier icon, Runnable action, boolean dangerous)
    {
        super(0, 0, size, size, Component.literal(label));
        this.icon = icon;
        this.action = action;
        this.dangerous = dangerous;
    }
    void presentation(float x, float y, float opacity, boolean interactive)
    {
        drawX = x; drawY = y;
        setX(Math.round(x)); setY(Math.round(y));
        this.opacity = Math.max(0, Math.min(1, opacity));
        active = interactive; visible = this.opacity > .001F;
    }
    @Override public void onPress(net.minecraft.client.input.InputWithModifiers input) { if(active) action.run(); }
    @Override public boolean isMouseOver(double x, double y)
    {
        double dx = x - drawX - width / 2D, dy = y - drawY - height / 2D;
        return super.isMouseOver(x, y) && dx * dx + dy * dy <= width * width / 4D;
    }
    @Override protected void extractContents(net.minecraft.client.gui.GuiGraphicsExtractor g, int mx, int my, float delta) { renderWidget(new GuiGraphicsExtractor(g), mx, my, delta); }
protected void renderWidget(GuiGraphicsExtractor g, int mx, int my, float delta) {}
    void renderNanoVG(int mouseX, int mouseY, float menuOpacity)
    {
        if(!visible) return;
        float amount = hover.update(active && (isMouseOver(mouseX, mouseY) || isFocused()));
        float extra = width * .03F * amount;
        float x = drawX - extra, y = drawY - extra, size = width + 2 * extra;
        int accent = 0xFFFFFFFF;
        NanoVGUI.push();
        try
        {
            NanoVGUI.context().setAlpha(opacity * menuOpacity);
            if(amount > .001F)
            {
                float glow = size * 25 / 110;
                NanoVGUI.image(GLOW, x - glow, y - glow, size + 2 * glow,
                    size + 2 * glow, 0, (Math.round(255 * amount) << 24) | 0xFFFFFF);
            }
            TitleGlassMaterial.draw(x,y,size,size,size/2,size*2/110,amount,1);
            float iconSize = width * .32F;
            if(icon != null)
                NanoVGUI.image(icon, drawX + (width - iconSize) / 2,
                    drawY + width * .21F, iconSize, iconSize, 0, accent);
            else
            {
                float cx = drawX + width / 2F, cy = drawY + width * .37F;
                NanoVGUI.line(cx + iconSize * .18F, cy - iconSize * .35F,
                    cx - iconSize * .18F, cy, 1.5F, accent);
                NanoVGUI.line(cx - iconSize * .18F, cy,
                    cx + iconSize * .18F, cy + iconSize * .35F, 1.5F, accent);
            }
            float font = Math.max(6, Math.min(9, width * .15F));
            String label = NanoVGUI.plainSubstrByWidth(getMessage().getString(), width - 8, font);
            NanoVGUI.centeredText(label, drawX + width / 2F, drawY + width * .64F,
                0xFFF2F2F8, font);
        }finally { NanoVGUI.pop(); }
    }
    @Override protected void updateWidgetNarration(NarrationElementOutput out) { defaultButtonNarrationText(out); }
}
