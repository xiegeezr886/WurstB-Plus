package kaptainwutax.seedcrackerX.render;

import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.PoseStack.Pose;
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
   public void render(Pose matrix4f, VertexConsumer vertexConsumer, Vec3 cameraPos) {
      Vec3 normal = this.end.subtract(this.start).normalize();
      this.putVertex(vertexConsumer, matrix4f, this.start, normal, cameraPos);
      this.putVertex(vertexConsumer, matrix4f, this.end, normal, cameraPos);
   }

   protected void putVertex(VertexConsumer vertexConsumer, Pose matrix4f, Vec3 pos, Vec3 normal, Vec3 cameraPos) {
      vertexConsumer.addVertex(matrix4f, (float)(pos.x - cameraPos.x), (float)(pos.y - cameraPos.y), (float)(pos.z - cameraPos.z))
         .setColor(this.color.getFRed(), this.color.getFGreen(), this.color.getFBlue(), 1.0F)
         .setNormal(matrix4f, (float)normal.x(), (float)normal.y(), (float)normal.z());
   }

   @Override
   public BlockPos getPos() {
      double x = (this.end.x() - this.start.x()) / 2.0 + this.start.x();
      double y = (this.end.y() - this.start.y()) / 2.0 + this.start.y();
      double z = (this.end.z() - this.start.z()) / 2.0 + this.start.z();
      return BlockPos.containing(x, y, z);
   }
}
