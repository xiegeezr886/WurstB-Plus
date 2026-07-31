package net.wurstclient.util;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Set;
import java.util.UUID;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation;
import net.minecraft.world.entity.ai.attributes.RangedAttribute;
import org.junit.jupiter.api.Test;

final class AttributeValuePlannerTest
{
	@Test
	void excludesSelectedModifiersAcrossAllOperations()
	{
		AttributeInstance instance = new AttributeInstance(
			new RangedAttribute("test.speed", 1, 0, 100), ignored -> {});
		AttributeModifier addition = modifier(-0.2, Operation.ADDITION);
		AttributeModifier multiplyBase = modifier(-0.25,
			Operation.MULTIPLY_BASE);
		AttributeModifier multiplyTotal = modifier(-0.5,
			Operation.MULTIPLY_TOTAL);
		instance.addTransientModifier(addition);
		instance.addTransientModifier(multiplyBase);
		instance.addTransientModifier(multiplyTotal);

		assertEquals(0.3, instance.getValue(), 1.0E-9);
		assertEquals(1, AttributeValuePlanner.calculateExcluding(instance,
			Set.of(addition.getId(), multiplyBase.getId(), multiplyTotal.getId())),
			1.0E-9);
	}

	private AttributeModifier modifier(double amount, Operation operation)
	{
		return new AttributeModifier(UUID.randomUUID(), "test", amount,
			operation);
	}
}
