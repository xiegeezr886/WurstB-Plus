package kaptainwutax.seedcrackerX.finder;

import com.mojang.blaze3d.platform.GlStateManager.DestFactor;
import com.mojang.blaze3d.platform.GlStateManager.SourceFactor;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat.Mode;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;
import kaptainwutax.seedcrackerX.config.Config;
import net.minecraft.client.Camera;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

public class FinderQueue {
   private static final FinderQueue INSTANCE = new FinderQueue();
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
            } catch (Exception var5) {
               var5.printStackTrace();
            }
         }));
      }
   }

   public void renderFinders(Matrix4f matrix4f, Camera camera) {
      if (Config.get().render != Config.RenderType.OFF) {
         Vec3 camPos = camera.getPosition();
         if (Config.get().render == Config.RenderType.XRAY) {
            RenderSystem.disableDepthTest();
         }

         RenderSystem.setShader(GameRenderer::getPositionColorShader);
         RenderSystem.enableBlend();
         RenderSystem.blendFuncSeparate(SourceFactor.SRC_ALPHA, DestFactor.ONE_MINUS_SRC_ALPHA, SourceFactor.ONE, DestFactor.ZERO);
         RenderSystem.lineWidth(2.0F);
         Tesselator tessellator = Tesselator.getInstance();
         BufferBuilder buffer = tessellator.begin(Mode.DEBUG_LINES, DefaultVertexFormat.POSITION_COLOR);
         this.finderControl.getActiveFinders().forEach(finder -> {
            if (finder.shouldRender()) {
               finder.render(matrix4f, buffer, camPos);
            }
         });
         MeshData builtBuffer = buffer.build();
         if (builtBuffer != null) {
            BufferUploader.drawWithShader(builtBuffer);
         }

         RenderSystem.enableDepthTest();
         RenderSystem.disableBlend();
         RenderSystem.applyModelViewMatrix();
      }
   }

   public List<Finder.Type> getActiveFinderTypes() {
      return Arrays.stream(Finder.Type.values()).filter(type -> type.enabled.get()).collect(Collectors.toList());
   }

   public void clear() {
      this.finderControl = new FinderControl();
   }
}
