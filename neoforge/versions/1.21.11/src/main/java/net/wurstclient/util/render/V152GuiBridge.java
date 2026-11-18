package net.wurstclient.util.render;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.render.GuiRenderer;
import net.minecraft.client.renderer.fog.FogRenderer;
import net.wurstclient.hud2.nanovg.GlStateGuard;

/** Consumes deferred GUI state before an immediate GL pass or readback. */
public final class V152GuiBridge {
  private V152GuiBridge() {}

  public static void flush(GuiGraphicsExtractor graphics) {
    var mc = Minecraft.getInstance();
    if (graphics.getRenderState() != mc.gameRenderer.guiRenderer.renderState)
      throw new IllegalArgumentException("Expected Minecraft's GUI render state");
    flushMain();
  }

  public static void flushMain() { render(Minecraft.getInstance().gameRenderer.guiRenderer); }

  private static void render(GuiRenderer renderer) {
    var mc = Minecraft.getInstance();
    var projection = RenderSystem.getProjectionMatrixBuffer();
    var type = RenderSystem.getProjectionType();
    try (var ignored = GlStateGuard.capture()) {
      renderer.incrementFrameNumber();
      renderer.render(mc.gameRenderer.fogRenderer.getBuffer(FogRenderer.FogMode.NONE));
    } finally {
      if (projection != null) RenderSystem.setProjectionMatrix(projection, type);
    }
  }

  public static void item(net.minecraft.world.item.ItemStack stack, RenderTarget target) throws Exception {
    var mc = Minecraft.getInstance();
    // Fabric owns special-element registration for the single game renderer.
    // capturePending consumes earlier GUI work before selecting the icon target.
    var renderer = mc.gameRenderer.guiRenderer;
    var graphics = new net.minecraft.client.gui.GuiGraphics(mc, renderer.renderState, 0, 0);
    // GuiRenderer uses the window's logical projection even for a small target.
    // Scale the sixteen-unit icon to that projection; its viewport is 32x32.
    graphics.pose().scale(mc.getWindow().getWidth() / (16F * mc.getWindow().getGuiScale()),
        mc.getWindow().getHeight() / (16F * mc.getWindow().getGuiScale()));
    graphics.renderItem(stack, 0, 0);
    try (var scope = V152Gpu.target(target)) { render(renderer); }
  }
}
