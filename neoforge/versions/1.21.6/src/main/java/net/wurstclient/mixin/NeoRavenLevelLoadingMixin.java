package net.wurstclient.mixin;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.*;
import net.minecraft.network.chat.Component;
import net.wurstclient.neoraven.loading.NeoRavenLoadingOverlay;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;

@Mixin(LevelLoadingScreen.class)
public abstract class NeoRavenLevelLoadingMixin {
  @Shadow @Final
  private net.minecraft.server.level.progress.StoringChunkProgressListener progressListener;

  @Redirect(
      method = "render",
      at =
          @At(
              value = "INVOKE",
              target =
                  "Lnet/minecraft/client/gui/screens/LevelLoadingScreen;renderChunks(Lnet/minecraft/client/gui/GuiGraphics;Lnet/minecraft/server/level/progress/StoringChunkProgressListener;IIII)V"))
  private void skipGrid(
      GuiGraphics g,
      net.minecraft.server.level.progress.StoringChunkProgressListener listener,
      int x,
      int y,
      int size,
      int gap) {}

  @Redirect(
      method = "render",
      at =
          @At(
              value = "INVOKE",
              target =
                  "Lnet/minecraft/client/gui/GuiGraphics;drawCenteredString(Lnet/minecraft/client/gui/Font;Lnet/minecraft/network/chat/Component;III)V"))
  private void drawLoading(
      GuiGraphics graphics,
      net.minecraft.client.gui.Font font,
      Component text,
      int x,
      int y,
      int color) {
    NeoRavenLoadingOverlay.render(
        new net.wurstclient.util.render.GuiGraphicsExtractor(graphics),
        (Screen) (Object) this,
        NeoRavenLoadingOverlay.message(Component.translatable("menu.generatingTerrain")),
        Math.max(0, Math.min(100, progressListener.getProgress())) + "%");
  }
}
