package net.wurstclient.mixin;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.LevelLoadingScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.multiplayer.LevelLoadTracker;
import net.minecraft.network.chat.Component;
import net.wurstclient.neoraven.loading.NeoRavenLoadingOverlay;
import net.wurstclient.util.render.GuiGraphicsExtractor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LevelLoadingScreen.class)
public abstract class NeoRavenLevelLoadingMixin {
  @Shadow private LevelLoadTracker loadTracker;

  @Inject(method="render", at=@At("HEAD"), cancellable=true)
  private void renderLoading(GuiGraphics graphics, int mouseX, int mouseY,
      float delta, CallbackInfo ci) {
    boolean progress = loadTracker.hasProgress();
    NeoRavenLoadingOverlay.render(new GuiGraphicsExtractor(graphics), (Screen)(Object)this,
        NeoRavenLoadingOverlay.message(Component.translatable(progress
            ? "menu.generatingTerrain" : "multiplayer.downloadingTerrain")),
        progress ? Math.max(0, Math.min(100, Math.round(loadTracker.serverProgress()*100)))+"%" : "");
    ci.cancel();
  }
}
