package net.wurstclient.mixin;

import org.joml.Matrix4f;
import org.joml.Vector4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.resource.GraphicsResourceAllocator;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Camera;
import net.minecraft.client.renderer.LevelRenderer;
import net.wurstclient.WurstClient;
import net.wurstclient.event.EventManager;
import net.wurstclient.events.RenderListener.RenderEvent;
import net.wurstclient.util.render.V152Gpu;
import net.wurstclient.util.render.MoonEntityGlow;

@Mixin(LevelRenderer.class)
public class LevelRendererMixin {
  @Inject(method = "renderLevel(Lcom/mojang/blaze3d/resource/GraphicsResourceAllocator;Lnet/minecraft/client/DeltaTracker;ZLnet/minecraft/client/Camera;Lorg/joml/Matrix4f;Lorg/joml/Matrix4f;Lcom/mojang/blaze3d/buffers/GpuBufferSlice;Lorg/joml/Vector4f;Z)V", at = @At("RETURN"))
  private void onRender(GraphicsResourceAllocator allocator, DeltaTracker tracker,
      boolean renderOutline, Camera camera, Matrix4f view, Matrix4f projection,
      GpuBufferSlice fogBuffer, Vector4f fogColor, boolean renderSky, CallbackInfo ci) {
    PoseStack matrices = new PoseStack();
    matrices.mulPose(view);
    float partialTicks = tracker.getGameTimeDeltaPartialTick(false);
    var event = new RenderEvent(matrices, partialTicks, projection);
    var previous = V152Gpu.projection();
    try {
      V152Gpu.setProjection(projection, false);
      EventManager.fire(event);
        kaptainwutax.seedcrackerX.finder.FinderQueue.get().renderNative(matrices, camera);
      WurstClient.INSTANCE.getPostEffectQueue().flush(matrices, partialTicks);
      MoonEntityGlow.INSTANCE.flush(matrices, partialTicks);
    } finally { V152Gpu.restoreProjection(previous); }
  }
}
