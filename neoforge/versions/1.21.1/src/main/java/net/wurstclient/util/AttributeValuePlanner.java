package net.wurstclient.util;

import java.util.Collection;
import java.util.Set;
import java.util.function.DoubleUnaryOperator;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation;

public enum AttributeValuePlanner
{
	;

	public static double calculateExcluding(AttributeInstance instance,
		Set<ResourceLocation> excludedModifiers)
	{
		if(excludedModifiers.isEmpty())
			return instance.getValue();

		return calculate(instance.getBaseValue(), instance.getModifiers(),
			excludedModifiers,
			instance.getAttribute().value()::sanitizeValue);
	}

	static double calculate(double baseValue,
		Collection<AttributeModifier> modifiers,
		Set<ResourceLocation> excludedModifiers,
		DoubleUnaryOperator sanitizer)
	{
		double base = baseValue;
		for(AttributeModifier modifier : modifiers)
			if(modifier.operation() == Operation.ADD_VALUE
				&& !excludedModifiers.contains(modifier.id()))
				base += modifier.amount();

		double value = base;
		for(AttributeModifier modifier : modifiers)
			if(modifier.operation() == Operation.ADD_MULTIPLIED_BASE
				&& !excludedModifiers.contains(modifier.id()))
				value += base * modifier.amount();

		for(AttributeModifier modifier : modifiers)
			if(modifier.operation() == Operation.ADD_MULTIPLIED_TOTAL
				&& !excludedModifiers.contains(modifier.id()))
				value *= 1 + modifier.amount();

		return sanitizer.applyAsDouble(value);
	}
}
