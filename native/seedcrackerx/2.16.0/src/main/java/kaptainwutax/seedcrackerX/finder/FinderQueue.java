package kaptainwutax.seedcrackerX.finder;

import com.mojang.blaze3d.vertex.PoseStack;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;
import kaptainwutax.seedcrackerX.config.Config;
import kaptainwutax.seedcrackerX.render.Cuboid;
import net.minecraft.client.Camera;
import net.minecraft.client.renderer.MultiBufferSource.BufferSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class FinderQueue {
   private static final FinderQueue INSTANCE = new FinderQueue();
   private static final Logger log = LoggerFactory.getLogger(FinderQueue.class);
   public static ExecutorService SERVICE = Executors.newFixedThreadPool(5);
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
            } catch (Exception e) {
               e.printStackTrace();
            }
         }));
      }
   }

   /** Capture an immutable camera-relative snapshot for this render pass. */
   private Set<Cuboid> extractCuboids(Camera camera) {
      if (Config.get().render == Config.RenderType.OFF) return Set.of();
      Set<Cuboid> cuboids = new HashSet<>();
      this.finderControl.getActiveFinders().forEach(finder -> {
         if (finder.shouldRender())
            finder.cuboids.forEach(cuboid -> cuboids.add(cuboid.offset(camera)));
      });
      return Set.copyOf(cuboids);
   }

   /** The native RETURN hook renders immediately, after Minecraft's frame graph. */
   public void renderNative(PoseStack matrices, Camera camera) {
      var snapshot = extractCuboids(camera);
      if (snapshot.isEmpty()) return;
      var buffers = net.minecraft.client.Minecraft.getInstance().renderBuffers().bufferSource();
      try (var ignored = net.wurstclient.hud2.nanovg.GlStateGuard.capture()) {
         snapshot.forEach(cuboid -> cuboid.render(matrices, buffers));
         buffers.endBatch(kaptainwutax.seedcrackerX.render.NoDepthLayer.LINES_NO_DEPTH_LAYER);
      }
   }

   public List<Finder.Type> getActiveFinderTypes() {
      return Arrays.stream(Finder.Type.values()).filter(type -> type.enabled.get()).collect(Collectors.toList());
   }

   public void clear() {
      this.finderControl = new FinderControl();
   }
}
