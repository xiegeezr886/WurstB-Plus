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
		for(AttributeModifier modifier : instance.getModifiers())
			if(modifier.operation() == Operation.ADD_VALUE
				&& !excludedModifiers.contains(modifier.id()))
				base += modifier.amount();

		double value = base;
		for(AttributeModifier modifier : instance.getModifiers())
			if(modifier.operation() == Operation.ADD_MULTIPLIED_BASE
				&& !excludedModifiers.contains(modifier.id()))
				value += base * modifier.amount();

		for(AttributeModifier modifier : instance.getModifiers())
			if(modifier.operation() == Operation.ADD_MULTIPLIED_TOTAL
				&& !excludedModifiers.contains(modifier.id()))
				value *= 1 + modifier.amount();

		return instance.getAttribute().value().sanitizeValue(value);
	}
}
