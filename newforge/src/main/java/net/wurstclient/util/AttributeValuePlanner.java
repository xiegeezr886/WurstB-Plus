package net.wurstclient.util;

import java.util.Set;
import java.util.UUID;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation;

public enum AttributeValuePlanner
{
	;

	public static double calculateExcluding(AttributeInstance instance,
		Set<UUID> excludedModifiers)
	{
		if(excludedModifiers.isEmpty())
			return instance.getValue();

		double base = instance.getBaseValue();
		for(AttributeModifier modifier : instance.getModifiers(Operation.ADDITION))
			if(!excludedModifiers.contains(modifier.getId()))
				base += modifier.getAmount();

		double value = base;
		for(AttributeModifier modifier : instance
			.getModifiers(Operation.MULTIPLY_BASE))
			if(!excludedModifiers.contains(modifier.getId()))
				value += base * modifier.getAmount();

		for(AttributeModifier modifier : instance
			.getModifiers(Operation.MULTIPLY_TOTAL))
			if(!excludedModifiers.contains(modifier.getId()))
				value *= 1 + modifier.getAmount();

		return instance.getAttribute().sanitizeValue(value);
	}
}
