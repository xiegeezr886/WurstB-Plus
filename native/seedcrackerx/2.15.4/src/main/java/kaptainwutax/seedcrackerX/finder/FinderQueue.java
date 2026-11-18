package kaptainwutax.seedcrackerX.finder;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.pipeline.RenderPipeline.Snippet;
import com.mojang.blaze3d.platform.DepthTestFunction;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.PoseStack.Pose;
import java.util.Arrays;
import java.util.List;
import java.util.OptionalDouble;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;
import kaptainwutax.seedcrackerX.config.Config;
import net.minecraft.client.Camera;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.RenderStateShard.LineStateShard;
import net.minecraft.client.renderer.RenderType.CompositeState;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class FinderQueue {
   private static final FinderQueue INSTANCE = new FinderQueue();
   private static final Logger log = LoggerFactory.getLogger(FinderQueue.class);
   public static ExecutorService SERVICE = Executors.newFixedThreadPool(5);
   private static final RenderPipeline LINES_NO_DEPTH_PIPELINE = RenderPipelines.register(
      RenderPipeline.builder(new Snippet[]{RenderPipelines.LINES_SNIPPET})
         .withLocation(ResourceLocation.fromNamespaceAndPath("seedcrackerx", "pipeline/lines_no_depth"))
         .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
         .withFragmentShader(ResourceLocation.fromNamespaceAndPath("seedcrackerx", "core/rendertype_lines"))
         .build()
   );
   public static final RenderType LINES_NO_DEPTH_LAYER = RenderType.create(
      "seedcrackerx_no_depth",
      1536,
      LINES_NO_DEPTH_PIPELINE,
      CompositeState.builder()
         .setLayeringState(RenderStateShard.VIEW_OFFSET_Z_LAYERING)
         .setLineState(new LineStateShard(OptionalDouble.of(2.0)))
         .createCompositeState(false)
   );
   public FinderControl finderControl = new FinderControl();

   private FinderQueue() {
      this.clear();
   }

   public static FinderQueue get() {
      return INSTANCE;
   }

   public void onChunkData(Level world, ChunkPos chunkPos) {
      if (Config.get().active) {
         this.getActiveFinderTypes().forEach(type -> SERVICE.submit(() -> {
            try {
               List<Finder> finders = type.finderBuilder.build(world, chunkPos);
               finders.forEach(finder -> {
                  if (finder.isValidDimension(world.dimensionType())) {
                     finder.findInChunk();
                     this.finderControl.addFinder(type, finder);
                  }
               });
            } catch (Exception var5) {
               var5.printStackTrace();
            }
         }));
      }
   }

   public void renderFinders(Pose matrix4f, VertexConsumer buffer, Camera camera) {
      if (Config.get().render != Config.RenderType.OFF) {
         Vec3 camPos = camera.getPosition();
         this.finderControl.getActiveFinders().forEach(finder -> {
            if (finder.shouldRender()) {
               finder.render(matrix4f, buffer, camPos);
            }
         });
      }
   }

   /** Submit the actual upstream structure geometry through Minecraft's GPU pipeline. */
   public void renderNative(com.mojang.blaze3d.vertex.PoseStack matrices, Camera camera) {
      if (Config.get().render == Config.RenderType.OFF) return;
      var buffers = net.minecraft.client.Minecraft.getInstance().renderBuffers().bufferSource();
      try (var ignored = net.wurstclient.hud2.nanovg.GlStateGuard.capture()) {
         renderFinders(matrices.last(), buffers.getBuffer(LINES_NO_DEPTH_LAYER), camera);
         buffers.endBatch(LINES_NO_DEPTH_LAYER);
      }
   }

   public List<Finder.Type> getActiveFinderTypes() {
      return Arrays.stream(Finder.Type.values()).filter(type -> type.enabled.get()).collect(Collectors.toList());
   }

   public void clear() {
      this.finderControl = new FinderControl();
   }

}
