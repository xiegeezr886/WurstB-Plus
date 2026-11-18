package net.wurstclient.mixin;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.*;
import net.minecraft.network.chat.Component;
import net.wurstclient.neoraven.loading.NeoRavenLoadingOverlay;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ReceivingLevelScreen.class)
public abstract class NeoRavenReceivingLevelMixin {
  @Inject(method = "render", at = @At("HEAD"), cancellable = true)
  private void drawLoading(
      GuiGraphics graphics, int mouseX, int mouseY, float delta, CallbackInfo ci) {
    NeoRavenLoadingOverlay.render(
        graphics,
        (Screen) (Object) this,
        NeoRavenLoadingOverlay.message(Component.translatable("multiplayer.downloadingTerrain")),
        "");
    ci.cancel();
  }
}
