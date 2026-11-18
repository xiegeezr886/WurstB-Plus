/* SPDX-License-Identifier: AGPL-3.0-or-later */
package net.wurstclient.leaves;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.Vec3;
import net.wurstclient.WurstClient;
import net.wurstclient.util.RenderUtils;

public final class LeavesRender {
  private LeavesRender() {}

  public static void text(PoseStack matrices, Vec3 position, String text, int color, double scale) {
    var mc = WurstClient.MC;
    var camera = mc.gameRenderer.mainCamera();
    var pos = position.subtract(camera.position());
    matrices.pushPose();
    matrices.translate(pos.x, pos.y, pos.z);
    matrices.mulPose(camera.rotation());
    matrices.scale((float) (-0.025 * scale), (float) (-0.025 * scale), (float) (0.025 * scale));
    RenderUtils.submitText(
        matrices,
        -mc.font.width(text) / 2F,
        0,
        Component.literal(text).getVisualOrderText(),
        false,
        Font.DisplayMode.SEE_THROUGH,
        0xF000F0,
        color,
        0,
        0);
    matrices.popPose();
  }

  public static int lerp(int start, int end, double progress) {
    int out = 0;
    for (int shift = 0; shift < 32; shift += 8)
      out |=
          (int)
                  Math.round(
                      ((start >>> shift) & 255) * (1 - progress)
                          + ((end >>> shift) & 255) * progress)
              << shift;
    return out;
  }
}
