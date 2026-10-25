package net.wurstclient.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.world.entity.Entity;
import net.wurstclient.WurstClient;
import net.wurstclient.hack.HackList;

@Mixin(EntityRenderDispatcher.class)
public abstract class EntityCullingMixin
{
	@Inject(method = "shouldRender", at = @At("HEAD"), cancellable = true)
	private void soar$shouldRender(Entity entity, Frustum frustum, double x,
		double y, double z, float partialTicks, CallbackInfoReturnable<Boolean> cir)
	{
		HackList hax = WurstClient.INSTANCE.getHax();
		if(hax != null && hax.entityCullingHack.shouldCull(entity))
			cir.setReturnValue(false);
	}
}
