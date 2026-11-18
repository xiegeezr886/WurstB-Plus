package kaptainwutax.seedcrackerX.render;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.pipeline.RenderPipeline.Snippet;
import com.mojang.blaze3d.platform.DepthTestFunction;
import java.util.OptionalDouble;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.RenderStateShard.LineStateShard;
import net.minecraft.client.renderer.RenderType.CompositeState;
import net.minecraft.resources.ResourceLocation;

public final class NoDepthLayer {
   private static final RenderPipeline LINES_NO_DEPTH_PIPELINE = RenderPipelines.register(
      RenderPipeline.builder(new Snippet[]{RenderPipelines.LINES_SNIPPET})
         .withLocation(ResourceLocation.fromNamespaceAndPath("seedcrackerx", "pipeline/lines_no_depth"))
         .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
         .build()
   );
   public static final RenderType LINES_NO_DEPTH_LAYER = RenderType.create(
      "seedcrackerx_no_depth",
      1536,
      LINES_NO_DEPTH_PIPELINE,
      CompositeState.builder()
         .setLayeringState(RenderType.VIEW_OFFSET_Z_LAYERING)
         .setLineState(new LineStateShard(OptionalDouble.of(2.0)))
         .createCompositeState(false)
   );

   private NoDepthLayer() {
   }
}
