package net.wurstclient.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.wurstclient.WurstClient;
import net.wurstclient.hack.HackList;

@Mixin(LivingEntity.class)
public abstract class LivingEntityMixin
{
	@Inject(at = @At("RETURN"),
		method = "getAttributeValue(Lnet/minecraft/world/entity/ai/attributes/Attribute;)D",
		cancellable = true)
	private void onGetAttributeValue(Attribute attribute,
		CallbackInfoReturnable<Double> cir)
	{
		LivingEntity self = (LivingEntity)(Object)this;
		if(self != WurstClient.MC.player || attribute != Attributes.MOVEMENT_SPEED)
			return;

		HackList hax = WurstClient.INSTANCE.getHax();
		if(hax == null || !hax.noSlowdownHack.shouldBypassItemSlowness())
			return;

		AttributeInstance instance = self.getAttribute(attribute);
		if(instance != null)
			cir.setReturnValue(hax.noSlowdownHack
				.getMovementSpeedWithoutItemSlowness(instance));
	}
}
