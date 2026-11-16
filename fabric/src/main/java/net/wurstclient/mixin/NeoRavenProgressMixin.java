package net.wurstclient.mixin;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.*;
import net.minecraft.network.chat.Component;
import net.wurstclient.neoraven.loading.NeoRavenLoadingOverlay;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ProgressScreen.class)
public abstract class NeoRavenProgressMixin {
  @Shadow private Component header;
  @Shadow private Component stage;
  @Shadow private int progress;
  @Shadow private boolean stop;

  @Inject(method = "render", at = @At("HEAD"), cancellable = true)
  private void drawLoading(
      GuiGraphics graphics, int mouseX, int mouseY, float delta, CallbackInfo ci) {
    if (stop) return; // Vanilla must still clear a completed progress screen.
    NeoRavenLoadingOverlay.render(
        graphics,
        (Screen) (Object) this,
        header == null ? NeoRavenLoadingOverlay.message(Component.translatable("menu.working")) : NeoRavenLoadingOverlay.message(header),
        stage == null ? "" : NeoRavenLoadingOverlay.message(stage) + (progress > 0 ? " " + progress + "%" : ""));
    ci.cancel();
  }
}
