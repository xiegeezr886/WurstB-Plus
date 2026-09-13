package net.wurstclient.util;

import java.util.Optional;
import java.util.stream.Stream;

import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.wurstclient.WurstClient;

public enum EnchantmentUtils
{
	;

	public static Optional<Holder.Reference<Enchantment>> getHolder(
		ResourceKey<Enchantment> key)
	{
		Registry<Enchantment> registry = getRegistry();
		return registry == null ? Optional.empty() : registry.getHolder(key);
	}

	public static Optional<Holder.Reference<Enchantment>> getHolder(
		ResourceLocation id)
	{
		Registry<Enchantment> registry = getRegistry();
		return registry == null ? Optional.empty() : registry.getHolder(id);
	}

	public static Stream<Holder.Reference<Enchantment>> stream()
	{
		Registry<Enchantment> registry = getRegistry();
		return registry == null ? Stream.empty() : registry.holders();
	}

	public static int getLevel(ResourceKey<Enchantment> key, ItemStack stack)
	{
		return getHolder(key)
			.map(holder -> EnchantmentHelper.getItemEnchantmentLevel(holder,
				stack))
			.orElse(0);
	}

	private static Registry<Enchantment> getRegistry()
	{
		if(WurstClient.MC.level == null)
			return null;

		return WurstClient.MC.level.registryAccess()
			.registryOrThrow(Registries.ENCHANTMENT);
	}
}
