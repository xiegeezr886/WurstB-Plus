/*
 * Copyright (c) 2014-2025 Wurst-Imperium and contributors.
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 */
package net.wurstclient.seed;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;


/**
 * The vanilla 1.20.1 ore generation rules, per dimension.
 *
 * <p>
 * All numbers are transcribed from the worldgen data that ships inside the
 * 1.20.1 client jar, so {@code count}, {@code rarity}, {@code size},
 * {@code discardChanceOnAirExposure}, the height provider and the block ids
 * are exact.
 *
 * <p>
 * <b>{@code index} is an approximation.</b> Vanilla derives it in
 * {@code FeatureSorter#buildFeaturesPerStep}, which sorts a dimension's
 * placed features per generation step by walking every biome of the
 * dimension's biome source and then topologically sorting the resulting
 * "this feature runs before that one" graph. Its result therefore depends on
 * the iteration order of that biome source, which is only available inside a
 * running game. The values below are the positions the ore features get when
 * the vanilla biome files are walked in alphabetical order, which is the
 * closest offline approximation of that order. Predictions are internally
 * consistent either way, but they can only line up with a real world when the
 * indices match the ones the world was generated with.
 */
public final class OreRules
{
	/** {@code GenerationStep.Decoration.UNDERGROUND_ORES} ordinal. */
	public static final int STEP_UNDERGROUND_ORES = 6;
	
	/** {@code GenerationStep.Decoration.UNDERGROUND_DECORATION} ordinal. */
	public static final int STEP_UNDERGROUND_DECORATION = 7;
	
	private static final Map<String, List<OreRule>> BY_DIMENSION = build();
	
	private OreRules()
	{
		
	}
	
	/**
	 * @param dimensionId e.g. {@code "minecraft:overworld"},
	 *            {@code "the_nether"} or {@code "the_end"}. The
	 *            {@code minecraft:} prefix is optional and the lookup is case
	 *            insensitive.
	 * @return an immutable list, empty for dimensions without ore features.
	 */
	public static List<OreRule> forDimension(String dimensionId)
	{
		List<OreRule> rules = BY_DIMENSION.get(normalize(dimensionId));
		return rules == null ? List.of() : rules;
	}
	
	/**
	 * @return the block ids that {@link #forDimension(String)} can produce.
	 */
	public static Set<String> oreIds(String dimensionId)
	{
		Set<String> ids = new LinkedHashSet<>();
		
		for(OreRule rule : forDimension(dimensionId))
			ids.add(rule.blockId);
		
		return Collections.unmodifiableSet(ids);
	}
	
	public static Set<String> dimensions()
	{
		return Collections.unmodifiableSet(BY_DIMENSION.keySet());
	}
	
	private static String normalize(String dimensionId)
	{
		if(dimensionId == null)
			return "";
		
		String id = dimensionId.strip().toLowerCase(Locale.ROOT);
		
		if(id.startsWith("minecraft:"))
			id = id.substring("minecraft:".length());
		
		return id;
	}
	
	private static Map<String, List<OreRule>> build()
	{
		Map<String, List<OreRule>> map = new LinkedHashMap<>();
		map.put("overworld", List.copyOf(overworld()));
		map.put("the_nether", List.copyOf(theNether()));
		map.put("the_end", List.of());
		return Collections.unmodifiableMap(map);
	}
	
	private static List<OreRule> overworld()
	{
		List<OreRule> rules = new ArrayList<>();
		
		// generation step 6: minecraft:underground_ores
		rules.add(ore("minecraft:ore_dirt", "minecraft:dirt", null, 0,
			STEP_UNDERGROUND_ORES, count(7), 1F, uniform(abs(0), abs(160)),
			33, 0F, false));
		rules.add(ore("minecraft:ore_gravel", "minecraft:gravel", null, 1,
			STEP_UNDERGROUND_ORES, count(14), 1F,
			uniform(above(0), below(0)), 33, 0F, false));
		rules.add(ore("minecraft:ore_granite_upper", "minecraft:granite", null,
			2, STEP_UNDERGROUND_ORES, count(1), 6F,
			uniform(abs(64), abs(128)), 64, 0F, false));
		rules.add(ore("minecraft:ore_granite_lower", "minecraft:granite", null,
			3, STEP_UNDERGROUND_ORES, count(2), 1F,
			uniform(abs(0), abs(60)), 64, 0F, false));
		rules.add(ore("minecraft:ore_diorite_upper", "minecraft:diorite", null,
			4, STEP_UNDERGROUND_ORES, count(1), 6F,
			uniform(abs(64), abs(128)), 64, 0F, false));
		rules.add(ore("minecraft:ore_diorite_lower", "minecraft:diorite", null,
			5, STEP_UNDERGROUND_ORES, count(2), 1F,
			uniform(abs(0), abs(60)), 64, 0F, false));
		rules.add(ore("minecraft:ore_andesite_upper", "minecraft:andesite",
			null, 6, STEP_UNDERGROUND_ORES, count(1), 6F,
			uniform(abs(64), abs(128)), 64, 0F, false));
		rules.add(ore("minecraft:ore_andesite_lower", "minecraft:andesite",
			null, 7, STEP_UNDERGROUND_ORES, count(2), 1F,
			uniform(abs(0), abs(60)), 64, 0F, false));
		rules.add(ore("minecraft:ore_tuff", "minecraft:tuff", null, 8,
			STEP_UNDERGROUND_ORES, count(2), 1F,
			uniform(above(0), abs(0)), 64, 0F, false));
		rules.add(ore("minecraft:ore_coal_upper", "minecraft:coal_ore",
			"minecraft:deepslate_coal_ore", 9, STEP_UNDERGROUND_ORES,
			count(30), 1F, uniform(abs(136), below(0)), 17, 0F, false));
		rules.add(ore("minecraft:ore_coal_lower", "minecraft:coal_ore",
			"minecraft:deepslate_coal_ore", 10, STEP_UNDERGROUND_ORES,
			count(20), 1F, trapezoid(abs(0), abs(192)), 17, 0.5F, false));
		rules.add(ore("minecraft:ore_iron_upper", "minecraft:iron_ore",
			"minecraft:deepslate_iron_ore", 11, STEP_UNDERGROUND_ORES,
			count(90), 1F, trapezoid(abs(80), abs(384)), 9, 0F, false));
		rules.add(ore("minecraft:ore_iron_middle", "minecraft:iron_ore",
			"minecraft:deepslate_iron_ore", 12, STEP_UNDERGROUND_ORES,
			count(10), 1F, trapezoid(abs(-24), abs(56)), 9, 0F, false));
		rules.add(ore("minecraft:ore_iron_small", "minecraft:iron_ore",
			"minecraft:deepslate_iron_ore", 13, STEP_UNDERGROUND_ORES,
			count(10), 1F, uniform(above(0), abs(72)), 4, 0F, false));
		rules.add(ore("minecraft:ore_gold", "minecraft:gold_ore",
			"minecraft:deepslate_gold_ore", 14, STEP_UNDERGROUND_ORES,
			count(4), 1F, trapezoid(abs(-64), abs(32)), 9, 0.5F, false));
		rules.add(ore("minecraft:ore_gold_lower", "minecraft:gold_ore",
			"minecraft:deepslate_gold_ore", 15, STEP_UNDERGROUND_ORES,
			OreCount.uniform(0, 1), 1F, uniform(abs(-64), abs(-48)), 9, 0.5F,
			false));
		rules.add(ore("minecraft:ore_redstone", "minecraft:redstone_ore",
			"minecraft:deepslate_redstone_ore", 16, STEP_UNDERGROUND_ORES,
			count(4), 1F, uniform(above(0), abs(15)), 8, 0F, false));
		rules.add(ore("minecraft:ore_redstone_lower", "minecraft:redstone_ore",
			"minecraft:deepslate_redstone_ore", 17, STEP_UNDERGROUND_ORES,
			count(8), 1F, trapezoid(above(-32), above(32)), 8, 0F, false));
		rules.add(ore("minecraft:ore_diamond", "minecraft:diamond_ore",
			"minecraft:deepslate_diamond_ore", 18, STEP_UNDERGROUND_ORES,
			count(7), 1F, trapezoid(above(-80), above(80)), 4, 0.5F, false));
		rules.add(ore("minecraft:ore_diamond_large", "minecraft:diamond_ore",
			"minecraft:deepslate_diamond_ore", 19, STEP_UNDERGROUND_ORES,
			count(1), 9F, trapezoid(above(-80), above(80)), 12, 0.7F, false));
		rules.add(ore("minecraft:ore_diamond_buried", "minecraft:diamond_ore",
			"minecraft:deepslate_diamond_ore", 20, STEP_UNDERGROUND_ORES,
			count(4), 1F, trapezoid(above(-80), above(80)), 8, 1F, false));
		rules.add(ore("minecraft:ore_lapis", "minecraft:lapis_ore",
			"minecraft:deepslate_lapis_ore", 21, STEP_UNDERGROUND_ORES,
			count(2), 1F, trapezoid(abs(-32), abs(32)), 7, 0F, false));
		rules.add(ore("minecraft:ore_lapis_buried", "minecraft:lapis_ore",
			"minecraft:deepslate_lapis_ore", 22, STEP_UNDERGROUND_ORES,
			count(4), 1F, uniform(above(0), abs(64)), 7, 1F, false));
		rules.add(ore("minecraft:ore_copper", "minecraft:copper_ore",
			"minecraft:deepslate_copper_ore", 23, STEP_UNDERGROUND_ORES,
			count(16), 1F, trapezoid(abs(-16), abs(112)), 10, 0F, false));
		rules.add(ore("minecraft:ore_gold_extra", "minecraft:gold_ore",
			"minecraft:deepslate_gold_ore", 25, STEP_UNDERGROUND_ORES,
			count(50), 1F, uniform(abs(32), abs(256)), 9, 0F, false));
		rules.add(ore("minecraft:ore_emerald", "minecraft:emerald_ore",
			"minecraft:deepslate_emerald_ore", 29, STEP_UNDERGROUND_ORES,
			count(100), 1F, trapezoid(abs(-16), abs(480)), 3, 0F, false));
		rules.add(ore("minecraft:ore_copper_large", "minecraft:copper_ore",
			"minecraft:deepslate_copper_ore", 30, STEP_UNDERGROUND_ORES,
			count(16), 1F, trapezoid(abs(-16), abs(112)), 20, 0F, false));
		rules.add(ore("minecraft:ore_clay", "minecraft:clay", null, 31,
			STEP_UNDERGROUND_ORES, count(46), 1F,
			uniform(above(0), abs(256)), 33, 0F, false));
		
		return rules;
	}
	
	private static List<OreRule> theNether()
	{
		List<OreRule> rules = new ArrayList<>();
		
		// generation step 7: minecraft:underground_decoration
		rules.add(ore("minecraft:ore_magma", "minecraft:magma_block", null, 9,
			STEP_UNDERGROUND_DECORATION, count(4), 1F,
			uniform(abs(27), abs(36)), 33, 0F, false));
		rules.add(ore("minecraft:ore_gold_deltas", "minecraft:nether_gold_ore",
			null, 11, STEP_UNDERGROUND_DECORATION, count(20), 1F,
			uniform(above(10), below(10)), 10, 0F, false));
		rules.add(ore("minecraft:ore_quartz_deltas",
			"minecraft:nether_quartz_ore", null, 12,
			STEP_UNDERGROUND_DECORATION, count(32), 1F,
			uniform(above(10), below(10)), 14, 0F, false));
		rules.add(ore("minecraft:ore_ancient_debris_large",
			"minecraft:ancient_debris", null, 13, STEP_UNDERGROUND_DECORATION,
			count(1), 1F, trapezoid(abs(8), abs(24)), 3, 1F, true));
		rules.add(ore("minecraft:ore_debris_small", "minecraft:ancient_debris",
			null, 14, STEP_UNDERGROUND_DECORATION, count(1), 1F,
			uniform(above(8), below(8)), 2, 1F, true));
		rules.add(ore("minecraft:ore_gravel_nether", "minecraft:gravel", null,
			17, STEP_UNDERGROUND_DECORATION, count(2), 1F,
			uniform(abs(5), abs(41)), 33, 0F, false));
		rules.add(ore("minecraft:ore_blackstone", "minecraft:blackstone", null,
			18, STEP_UNDERGROUND_DECORATION, count(2), 1F,
			uniform(abs(5), abs(31)), 33, 0F, false));
		rules.add(ore("minecraft:ore_gold_nether", "minecraft:nether_gold_ore",
			null, 19, STEP_UNDERGROUND_DECORATION, count(10), 1F,
			uniform(above(10), below(10)), 10, 0F, false));
		rules.add(ore("minecraft:ore_quartz_nether",
			"minecraft:nether_quartz_ore", null, 20,
			STEP_UNDERGROUND_DECORATION, count(16), 1F,
			uniform(above(10), below(10)), 14, 0F, false));
		rules.add(ore("minecraft:ore_soul_sand", "minecraft:soul_sand", null,
			22, STEP_UNDERGROUND_DECORATION, count(12), 1F,
			uniform(above(0), abs(31)), 12, 0F, false));
		
		return rules;
	}
	
	private static OreRule ore(String featureId, String blockId,
		String deepslateBlockId, int index, int step, OreCount count,
		float rarity, OreHeight heightProvider, int size,
		float discardOnAirChance, boolean scattered)
	{
		return new OreRule(featureId, blockId, deepslateBlockId, index, step,
			count, rarity, heightProvider, size, discardOnAirChance, scattered);
	}
	
	private static OreCount count(int value)
	{
		return OreCount.constant(value);
	}
	
	private static OreCount count(int min, int max)
	{
		return OreCount.uniform(min, max);
	}
	
	private static OreHeight.Anchor abs(int y)
	{
		return OreHeight.Anchor.absolute(y);
	}
	
	private static OreHeight.Anchor above(int offset)
	{
		return OreHeight.Anchor.aboveBottom(offset);
	}
	
	private static OreHeight.Anchor below(int offset)
	{
		return OreHeight.Anchor.belowTop(offset);
	}
	
	private static OreHeight uniform(OreHeight.Anchor min,
		OreHeight.Anchor max)
	{
		return OreHeight.uniform(min, max);
	}
	
	private static OreHeight trapezoid(OreHeight.Anchor min,
		OreHeight.Anchor max)
	{
		// TrapezoidHeight#of(min, max) uses a plateau of 0.
		return OreHeight.trapezoid(min, max, 0);
	}
}
