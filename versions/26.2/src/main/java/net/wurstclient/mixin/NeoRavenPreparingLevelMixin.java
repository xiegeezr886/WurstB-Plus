package net.wurstclient.mixin;


import net.minecraft.client.gui.screens.GenericMessageScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.wurstclient.neoraven.loading.NeoRavenLoadingOverlay;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GenericMessageScreen.class)
public abstract class NeoRavenPreparingLevelMixin extends Screen {
  protected NeoRavenPreparingLevelMixin(Component title) { super(title); }

  // GenericMessageScreen inherits render from Screen in this API generation.
  @Override public void extractRenderState(net.minecraft.client.gui.GuiGraphicsExtractor graphics, int x, int y, float delta) {
    NeoRavenLoadingOverlay.render(new net.wurstclient.util.render.GuiGraphicsExtractor(graphics), this,
        NeoRavenLoadingOverlay.message(getTitle()), "");
  }

  @Inject(method = "extractBackground", at = @At("HEAD"), cancellable = true)
  private void loading$background(net.minecraft.client.gui.GuiGraphicsExtractor graphics, int x, int y, float delta, CallbackInfo ci) {
    ci.cancel();
  }
}
