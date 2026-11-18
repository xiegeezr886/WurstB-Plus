package kaptainwutax.seedcrackerX.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;

public class Line extends Renderer {
   public Vec3 start;
   public Vec3 end;
   public Color color;

   public Line() {
      this(Vec3.ZERO, Vec3.ZERO, Color.WHITE);
   }

   public Line(Vec3 start, Vec3 end) {
      this(start, end, Color.WHITE);
   }

   public Line(Vec3 start, Vec3 end, Color color) {
      this.start = start;
      this.end = end;
      this.color = color;
   }

   @Override
   public void render(PoseStack matrixStack, VertexConsumer vertexConsumer, Vec3 cameraPos) {
      this.putVertex(vertexConsumer, matrixStack, this.start, cameraPos);
      this.putVertex(vertexConsumer, matrixStack, this.end, cameraPos);
   }

   protected void putVertex(VertexConsumer vertexConsumer, PoseStack matrixStack, Vec3 pos, Vec3 cameraPos) {
      vertexConsumer.vertex(matrixStack.last().pose(), (float)(pos.x - cameraPos.x), (float)(pos.y - cameraPos.y), (float)(pos.z - cameraPos.z))
         .color(this.color.getFRed(), this.color.getFGreen(), this.color.getFBlue(), 1.0F)
         .endVertex();
   }

   @Override
   public BlockPos getPos() {
      double x = (this.end.x() - this.start.x()) / 2.0 + this.start.x();
      double y = (this.end.y() - this.start.y()) / 2.0 + this.start.y();
      double z = (this.end.z() - this.start.z()) / 2.0 + this.start.z();
      return BlockPos.containing(x, y, z);
   }
}
