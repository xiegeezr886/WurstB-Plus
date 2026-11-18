package kaptainwutax.seedcrackerX.render;

import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.PoseStack.Pose;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;

public abstract class Renderer {
   public abstract void render(Pose var1, VertexConsumer var2, Vec3 var3);

   public abstract BlockPos getPos();

   protected Vec3 toVec3d(BlockPos pos) {
      return new Vec3(pos.getX(), pos.getY(), pos.getZ());
   }
}
