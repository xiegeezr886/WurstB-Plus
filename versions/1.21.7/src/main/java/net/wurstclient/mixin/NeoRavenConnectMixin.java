package net.wurstclient.mixin;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.*;
import net.minecraft.network.chat.Component;
import net.wurstclient.neoraven.loading.NeoRavenLoadingOverlay;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;

@Mixin(ConnectScreen.class)
public abstract class NeoRavenConnectMixin {
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
    var server = net.minecraft.client.Minecraft.getInstance().getCurrentServer();
    NeoRavenLoadingOverlay.render(
        new net.wurstclient.util.render.GuiGraphicsExtractor(graphics), (Screen) (Object) this, NeoRavenLoadingOverlay.message(text), server == null ? "" : server.name);
  }
}
