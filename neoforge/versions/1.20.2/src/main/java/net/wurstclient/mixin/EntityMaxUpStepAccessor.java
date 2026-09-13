package net.wurstclient.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import net.minecraft.world.entity.Entity;

@Mixin(Entity.class)
public interface EntityMaxUpStepAccessor
{
	@Accessor("maxUpStep")
	float wurst_getMaxUpStep();

	@Accessor("maxUpStep")
	void wurst_setMaxUpStep(float value);
}
