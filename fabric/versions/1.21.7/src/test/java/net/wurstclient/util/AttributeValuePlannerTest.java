package net.wurstclient.util;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation;
import org.junit.jupiter.api.Test;

final class AttributeValuePlannerTest
{
	@Test
	void excludesSelectedModifiersAcrossAllOperations()
	{
		AttributeModifier addition = modifier(-0.2, Operation.ADD_VALUE);
		AttributeModifier multiplyBase = modifier(-0.25,
			Operation.ADD_MULTIPLIED_BASE);
		AttributeModifier multiplyTotal = modifier(-0.5,
			Operation.ADD_MULTIPLIED_TOTAL);
		List<AttributeModifier> modifiers =
			List.of(addition, multiplyBase, multiplyTotal);

		assertEquals(0.3, AttributeValuePlanner.calculate(1, modifiers,
			Set.of(), value -> value), 1.0E-9);
		assertEquals(1, AttributeValuePlanner.calculate(1, modifiers,
			Set.of(addition.id(), multiplyBase.id(), multiplyTotal.id()),
			value -> value),
			1.0E-9);
	}

	private AttributeModifier modifier(double amount, Operation operation)
	{
		return new AttributeModifier(ResourceLocation.fromNamespaceAndPath("test",
			UUID.randomUUID().toString()), amount, operation);
	}
}
