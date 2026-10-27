package net.wurstclient.hud2.nanovg;

import net.minecraft.client.gui.GuiGraphicsExtractor;

/**
 * 26.1/26.2 do not expose the RenderPearl GpuTexture/BlitRenderState API used
 * by 26.3. The HUD still renders into NanoVG's offscreen framebuffer; the
 * on-screen composite is a later porting step.
 */
public final class NanoVgBlitter
{
	private NanoVgBlitter() {}

	public static void queueBlit(GuiGraphicsExtractor graphics,
		NanoVGContext ctx) {}
}
