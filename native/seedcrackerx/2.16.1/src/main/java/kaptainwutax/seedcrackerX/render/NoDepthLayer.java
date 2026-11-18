package kaptainwutax.seedcrackerX.render;

import com.mojang.blaze3d.pipeline.DepthStencilState;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.pipeline.RenderPipeline.Snippet;
import com.mojang.blaze3d.platform.CompareOp;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.rendertype.LayeringTransform;
import net.minecraft.client.renderer.rendertype.OutputTarget;
import net.minecraft.client.renderer.rendertype.RenderSetup;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.resources.Identifier;

public final class NoDepthLayer {
   private static final RenderPipeline LINES_NO_DEPTH_PIPELINE = RenderPipelines.register(
      RenderPipeline.builder(new Snippet[]{RenderPipelines.LINES_SNIPPET})
         .withLocation(Identifier.fromNamespaceAndPath("seedcrackerx", "pipeline/lines_no_depth"))
         .withDepthStencilState(new DepthStencilState(CompareOp.ALWAYS_PASS, false, 0.0F, 0.0F))
         .build()
   );
   public static final RenderType LINES_NO_DEPTH_LAYER = RenderType.create(
      "seedcrackerx_no_depth",
      RenderSetup.builder(LINES_NO_DEPTH_PIPELINE)
         .setLayeringTransform(LayeringTransform.VIEW_OFFSET_Z_LAYERING)
         .setOutputTarget(OutputTarget.ITEM_ENTITY_TARGET)
         .createRenderSetup()
   );

   private NoDepthLayer() {
   }
}
