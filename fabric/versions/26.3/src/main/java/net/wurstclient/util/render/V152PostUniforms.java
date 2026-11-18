package net.wurstclient.util.render;

import com.mojang.renderpearl.api.buffers.GpuBuffer;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.renderer.PostChain;
import org.lwjgl.system.MemoryStack;

/** Updates the effect's std140 block without reallocating it every frame. */
public final class V152PostUniforms {
  private V152PostUniforms() {}
  public static void time(PostChain chain, int mode, float time) {
    try (var memory = MemoryStack.stackPush()) {
      var data = memory.calloc(16);
      data.putInt(0, mode).putFloat(4, time);
      for (var pass : chain.passes) {
        var old = pass.customUniforms.get("WurstEffect");
        if (old == null) continue;
        if ((old.usage() & GpuBuffer.USAGE_COPY_DST) == 0) {
          var replacement = RenderSystem.getDevice().createBuffer(() -> "Wurst animated effect",
              GpuBuffer.USAGE_UNIFORM | GpuBuffer.USAGE_COPY_DST, data);
          pass.customUniforms.put("WurstEffect", replacement);
          old.close();
        } else RenderSystem.getDevice().createCommandEncoder().writeToBuffer(old.slice(), data);
      }
    }
  }
}
