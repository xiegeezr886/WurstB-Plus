package net.wurstclient.mixin;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.*;
import net.wurstclient.neoraven.loading.NeoRavenLoadingOverlay;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GenericDirtMessageScreen.class)
public abstract class NeoRavenPreparingLevelMixin {
  @Inject(method = "render", at = @At("HEAD"), cancellable = true)
  private void drawLoading(
      GuiGraphics graphics, int mouseX, int mouseY, float delta, CallbackInfo ci) {
    Screen screen = (Screen) (Object) this;
    NeoRavenLoadingOverlay.render(graphics, screen, NeoRavenLoadingOverlay.message(screen.getTitle()), "");
    ci.cancel();
  }
}
