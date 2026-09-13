/*
 * Copyright (c) 2014-2025 Wurst-Imperium and contributors.
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 */
package net.wurstclient.commands;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.wurstclient.WurstClient;
import net.wurstclient.command.BrigadierCommand;
import net.wurstclient.hacks.PerimeterDiggerHack;
import net.wurstclient.perimeter.PerimeterArea;
import net.wurstclient.perimeter.PerimeterAutomation;
import net.wurstclient.perimeter.PerimeterLiquidPolicy;
import net.wurstclient.perimeter.PerimeterRecoveryMode;
import net.wurstclient.perimeter.PerimeterText;
import net.wurstclient.perimeter.config.PerimeterAdvancedOption;
import net.wurstclient.perimeter.config.PerimeterAdvancedOptions;
import net.wurstclient.perimeter.config.PerimeterConfig;
import net.wurstclient.perimeter.config.PerimeterConfigStore;
import net.wurstclient.perimeter.config.PerimeterPosition;
import net.wurstclient.perimeter.config.PerimeterUnloadingPoint;
import net.wurstclient.perimeter.detect.BoundaryDetector;
import net.wurstclient.perimeter.detect.PerimeterGrid;
import net.wurstclient.util.BaritoneUtils;
import net.wurstclient.util.BlockUtils;

/**
 * The client side {@code /perimeterdig} command, mirroring the reference mod's
 * command tree. Literal arguments make Brigadier offer tab completion for every
 * sub command, option key and value.
 */
public final class PerimeterDigCommand extends BrigadierCommand
{
	public PerimeterDigCommand()
	{
		super("perimeterdig",
			"Plans, detects and runs the perimeter digger automation.");
	}
	
	@Override
	public void build(LiteralArgumentBuilder<CommandSourceStack> builder)
	{
		builder.then(literalWithRun("start", this::start))
			.then(literalWithRun("stop", this::stop))
			.then(literalWithRun("pause", this::pause))
			.then(literalWithRun("resume", this::resume))
			.then(literalWithRun("status", this::status))
			.then(literalWithRun("history", this::history))
			.then(buildArea())
			.then(buildFluids())
			.then(buildLists())
			.then(buildUnloading())
			.then(buildFacilities())
			.then(buildFunctions())
			.then(buildDurability())
			.then(buildAdvanced())
			.then(buildConfig());
	}
	
	// ------------------------------------------------------------------
	// area
	// ------------------------------------------------------------------
	
	private LiteralArgumentBuilder<CommandSourceStack> buildArea()
	{
		var maxY = Commands.argument("maxY", IntegerArgumentType.integer())
			.executes(this::planRect);
		var minY = Commands.argument("minY", IntegerArgumentType.integer())
			.then(maxY);
		var z1 = Commands.argument("z1", IntegerArgumentType.integer())
			.then(minY);
		var x1 = Commands.argument("x1", IntegerArgumentType.integer())
			.then(z1);
		var z0 = Commands.argument("z0", IntegerArgumentType.integer())
			.then(x1);
		var x0 = Commands.argument("x0", IntegerArgumentType.integer())
			.then(z0);
		var rectangle = Commands.literal("rectangle").then(x0);
		
		var y = Commands.argument("y", IntegerArgumentType.integer(-64, 320))
			.executes(this::detect);
		var block = Commands.argument("block", StringArgumentType.word())
			.suggests((context, suggestions) -> SharedSuggestionProvider
				.suggest(blockIds(), suggestions))
			.then(y);
		var detect = Commands.literal("detect").then(block);
		
		return Commands.literal("area")
			.then(Commands.literal("plan").then(rectangle)).then(detect)
			.then(literalWithRun("info", this::areaInfo))
			.then(literalWithRun("clear", this::areaClear));
	}
	
	private int planRect(CommandContext<CommandSourceStack> context)
	{
		int x0 = IntegerArgumentType.getInteger(context, "x0");
		int z0 = IntegerArgumentType.getInteger(context, "z0");
		int x1 = IntegerArgumentType.getInteger(context, "x1");
		int z1 = IntegerArgumentType.getInteger(context, "z1");
		int minY = IntegerArgumentType.getInteger(context, "minY");
		int maxY = IntegerArgumentType.getInteger(context, "maxY");
		
		if(minY > maxY)
			return feedback(context, PerimeterText.get("perimeterdigger.command.bad_y_range"));
		
		PerimeterDiggerHack hack = hack();
		hack.ensureConfigLoaded();
		PerimeterArea area = PerimeterArea.fromXZ(x0, z0, x1, z1, minY, maxY);
		hack.plan(area);
		return feedback(context, PerimeterText.get("perimeterdigger.command.planned", area.describe()));
	}
	
	private int detect(CommandContext<CommandSourceStack> context)
	{
		String name = StringArgumentType.getString(context, "block");
		int y = IntegerArgumentType.getInteger(context, "y");
		Block block = BlockUtils.getBlockFromNameOrID(name);
		
		if(block == null)
			return feedback(context, PerimeterText
				.get("perimeterdigger.command.unknown_value", name));
		
		if(WurstClient.MC.level == null || WurstClient.MC.player == null)
			return feedback(context, PerimeterText.get("perimeterdigger.command.no_world"));
		
		String blockId = BuiltInRegistries.BLOCK.getKey(block).toString();
		
		try
		{
			var detected = new BoundaryDetector().detect(
				grid(WurstClient.MC.level), blockId, y,
				WurstClient.MC.player.getBlockX(),
				WurstClient.MC.player.getBlockZ());
			
			PerimeterDiggerHack hack = hack();
			hack.ensureConfigLoaded();
			hack.getConfig().detectedArea = detected;
			hack.saveConfig();
			
			return feedback(context,
				PerimeterText.get("perimeterdigger.command.detected",
					detected.columnCount, detected.scanlines.size()));
		}catch(IllegalStateException e)
		{
			return feedback(context, e.getMessage());
		}
	}
	
	private int areaInfo(CommandContext<CommandSourceStack> context)
	{
		PerimeterConfig config = config();
		
		if(!config.hasDetectedArea())
			return feedback(context, PerimeterText.get("perimeterdigger.command.no_area"));
		
		StringBuilder builder = new StringBuilder();
		builder.append(config.detectedArea.columnCount).append(" columns, ")
			.append(config.detectedArea.scanlines.size())
			.append(" scanlines, X ")
			.append(config.detectedArea.minX).append("..")
			.append(config.detectedArea.maxX).append(", Z ")
			.append(config.detectedArea.minZ).append("..")
			.append(config.detectedArea.maxZ);
		
		if(config.hasDiggingRange())
			builder.append(", Y ").append(config.diggingMinY).append("..")
				.append(config.diggingMaxY);
		else
			builder.append(", no Y range set");
		
		return feedback(context, builder.toString());
	}
	
	private int areaClear(CommandContext<CommandSourceStack> context)
	{
		PerimeterDiggerHack hack = hack();
		hack.ensureConfigLoaded();
		hack.getConfig().detectedArea = new net.wurstclient.perimeter.config
			.PerimeterDetectedArea();
		hack.getConfig().diggingMinY = null;
		hack.getConfig().diggingMaxY = null;
		hack.saveConfig();
		return feedback(context, PerimeterText.get("perimeterdigger.command.area_cleared"));
	}
	
	// ------------------------------------------------------------------
	// fluids, food, whitelist
	// ------------------------------------------------------------------
	
	private LiteralArgumentBuilder<CommandSourceStack> buildFluids()
	{
		return Commands.literal("fluids")
			.then(Commands.literal("policy")
				.then(Commands
					.argument("policy", StringArgumentType.word())
					.suggests((context, suggestions) -> SharedSuggestionProvider
						.suggest(List.of("avoid", "replace", "seal_boundary"),
							suggestions))
					.executes(context -> {
						String value = StringArgumentType.getString(context,
							"policy");
						PerimeterLiquidPolicy policy =
							PerimeterLiquidPolicy.fromId(value);
						
						if(policy == null)
							return feedback(context, PerimeterText.get(
								"perimeterdigger.command.unknown_value",
								value));
						
						PerimeterDiggerHack hack = hack();
						hack.ensureConfigLoaded();
						hack.getConfig().liquidPolicy = policy.id();
						hack.saveConfig();
						return feedback(context, PerimeterText.get(
							"perimeterdigger.command.set", "liquid policy",
							policy.id()));
					})))
			.then(Commands.literal("blocks")
				.then(Commands.literal("add")
					.then(Commands
						.argument("block", StringArgumentType.word())
						.suggests((context, suggestions) ->
							SharedSuggestionProvider
								.suggest(blockIds(), suggestions))
						.executes(context -> {
							String id = blockId(StringArgumentType
								.getString(context, "block"));
							
							if(id == null)
								return feedback(context, PerimeterText.get(
									"perimeterdigger.command.unknown_value",
									StringArgumentType.getString(context,
										"block")));
							
							PerimeterConfig config = config();
							PerimeterConfig.addIdentifier(
								config.sealingBlocks, id);
							save();
							return feedback(context, PerimeterText
								.get("perimeterdigger.command.added", id));
						})))
				.then(Commands.literal("remove")
					.then(Commands
						.argument("block", StringArgumentType.word())
						.suggests((context, suggestions) ->
							SharedSuggestionProvider.suggest(
								config().sealingBlocks, suggestions))
						.executes(context -> {
							String id = StringArgumentType
								.getString(context, "block");
							PerimeterConfig.removeIdentifier(
								config().sealingBlocks, id);
							save();
							return feedback(context, PerimeterText
								.get("perimeterdigger.command.removed", id));
						})))
				.then(literalWithRun("list", context -> feedback(context,
					String.join(", ", config().sealingBlocks)))));
	}
	
	private LiteralArgumentBuilder<CommandSourceStack> buildLists()
	{
		return Commands.literal("food")
			.then(Commands.literal("add")
				.then(Commands.argument("item", StringArgumentType.word())
					.executes(context -> {
						String id = itemId(StringArgumentType.getString(
							context, "item"));
						
						if(id == null)
							return feedback(context, PerimeterText.get(
								"perimeterdigger.command.unknown_value",
								StringArgumentType.getString(context, "item")));
						
						PerimeterConfig.addIdentifier(config().foods, id);
						save();
						return feedback(context, PerimeterText
							.get("perimeterdigger.command.added", id));
					})))
			.then(Commands.literal("remove")
				.then(Commands.argument("item", StringArgumentType.word())
					.executes(context -> {
						String id = StringArgumentType.getString(context,
							"item");
						PerimeterConfig.removeIdentifier(config().foods, id);
						save();
						return feedback(context, PerimeterText
							.get("perimeterdigger.command.removed", id));
					})))
			.then(literalWithRun("list", context -> feedback(context,
				String.join(", ", config().foods))))
			.then(Commands.literal("whitelist")
				.then(Commands.literal("add")
					.then(Commands.argument("item", StringArgumentType.word())
						.executes(context -> {
							String id = itemId(StringArgumentType.getString(
								context, "item"));
							
							if(id == null)
								return feedback(context, PerimeterText.get(
									"perimeterdigger.command.unknown_value",
									StringArgumentType.getString(context,
										"item")));
							
							PerimeterConfig.addIdentifier(
								config().unloadingWhitelist, id);
							save();
							return feedback(context, PerimeterText
								.get("perimeterdigger.command.added", id));
						})))
				.then(Commands.literal("remove")
					.then(Commands.argument("item", StringArgumentType.word())
						.executes(context -> {
							String id = StringArgumentType.getString(context,
								"item");
							PerimeterConfig.removeIdentifier(
								config().unloadingWhitelist, id);
							save();
							return feedback(context, PerimeterText
								.get("perimeterdigger.command.removed", id));
						})))
				.then(literalWithRun("list", context -> feedback(context,
					String.join(", ", config().unloadingWhitelist)))));
	}
	
	// ------------------------------------------------------------------
	// unloading and facilities
	// ------------------------------------------------------------------
	
	private LiteralArgumentBuilder<CommandSourceStack> buildUnloading()
	{
		var z = Commands.argument("z", IntegerArgumentType.integer())
			.executes(this::addUnloadingPoint);
		var y = Commands.argument("y", IntegerArgumentType.integer()).then(z);
		var x = Commands.argument("x", IntegerArgumentType.integer()).then(y);
		var nameArg = Commands.argument("name", StringArgumentType.word())
			.then(x);
		
		return Commands.literal("unloading")
			.then(Commands.literal("add").then(nameArg))
			.then(Commands.literal("remove")
				.then(Commands
					.argument("name", StringArgumentType.word())
					.suggests((context, suggestions) ->
						SharedSuggestionProvider.suggest(
							config().unloadingPoints.keySet(), suggestions))
					.executes(context -> {
						String name = StringArgumentType.getString(context,
							"name");
						config().removeUnloadingPoint(name);
						save();
						return feedback(context, PerimeterText
							.get("perimeterdigger.command.removed", name));
					})))
			.then(literalWithRun("list", context -> {
				List<String> lines = new ArrayList<>();
				
				for(var entry : config().unloadingPoints.entrySet())
				{
					PerimeterUnloadingPoint point = entry.getValue();
					lines.add(entry.getKey() + " " + point.x + " "
						+ point.minY + " " + point.z);
				}
				
				return feedback(context,
					lines.isEmpty() ? PerimeterText.get("perimeterdigger.command.no_unloading_points")
						: String.join(", ", lines));
			}))
			.then(literalWithRun("goto", context -> {
				PerimeterDiggerHack hack = hack();
				hack.ensureConfigLoaded();
				var points = hack.getConfig().unloadingPoints;
				
				if(points.isEmpty())
					return feedback(context, PerimeterText.get("perimeterdigger.command.no_unloading_points"));
				
				var first = points.entrySet().iterator().next();
				var target = first.getValue();
				BaritoneUtils.walkTo(
					new BlockPos(target.x, target.minY, target.z));
				return feedback(context,
					PerimeterText.get("perimeterdigger.command.walking_to",
						first.getKey()));
			}));
	}
	
	private int addUnloadingPoint(CommandContext<CommandSourceStack> context)
	{
		PerimeterDiggerHack hack = hack();
		hack.ensureConfigLoaded();
		int x = IntegerArgumentType.getInteger(context, "x");
		int y = IntegerArgumentType.getInteger(context, "y");
		int z = IntegerArgumentType.getInteger(context, "z");
		String name = StringArgumentType.getString(context, "name");
		hack.getConfig().putUnloadingPoint(name, x, y, z);
		hack.saveConfig();
		return feedback(context,
			PerimeterText.get("perimeterdigger.command.added", name));
	}
	
	private LiteralArgumentBuilder<CommandSourceStack> buildFacilities()
	{
		var z = Commands.argument("z", IntegerArgumentType.integer())
			.executes(this::setFacility);
		var y = Commands.argument("y", IntegerArgumentType.integer()).then(z);
		var x = Commands.argument("x", IntegerArgumentType.integer()).then(y);
		var kind = Commands.argument("kind", StringArgumentType.word())
			.suggests((context, suggestions) -> SharedSuggestionProvider
				.suggest(List.of("consumable", "durability", "bed",
					"portal_overworld", "portal_nether",
					"repair_portal_overworld", "repair_portal_nether",
					"furnace_start", "furnace_end"), suggestions))
			.then(x);
		
		return Commands.literal("facility").then(kind);
	}
	
	private int setFacility(CommandContext<CommandSourceStack> context)
	{
		String kind = StringArgumentType.getString(context, "kind");
		int x = IntegerArgumentType.getInteger(context, "x");
		int y = IntegerArgumentType.getInteger(context, "y");
		int z = IntegerArgumentType.getInteger(context, "z");
		
		PerimeterDiggerHack hack = hack();
		hack.ensureConfigLoaded();
		PerimeterConfig config = hack.getConfig();
		PerimeterPosition position = new PerimeterPosition(x, y, z);
		
		switch(kind.toLowerCase(Locale.ROOT))
		{
			case "consumable" -> config.consumableSupplyPoint = position;
			case "durability" -> config.durabilitySupplyPoint = position;
			case "bed" -> config.bedPoint = position;
			case "portal_overworld" -> config.perimeterPortalOverworld =
				position;
			case "portal_nether" -> config.perimeterPortalNether = position;
			case "repair_portal_overworld" ->
				config.repairPortalOverworld = position;
			case "repair_portal_nether" -> config.repairPortalNether =
				position;
			case "furnace_start" -> config.furnaceRowStart = position;
			case "furnace_end" -> config.furnaceRowEnd = position;
			default -> {
				return feedback(context, PerimeterText
					.get("perimeterdigger.command.unknown_value", kind));
			}
		}
		
		hack.saveConfig();
		return feedback(context, kind + " set to " + x + " " + y + " " + z);
	}
	
	private LiteralArgumentBuilder<CommandSourceStack> buildFunctions()
	{
		return Commands.literal("function")
			.then(Commands
				.argument("name", StringArgumentType.word())
				.suggests((context, suggestions) -> SharedSuggestionProvider
					.suggest(List.of("collect_drops", "unload", "eat",
						"durability_recovery", "cross_dimension_repair",
						"resupply", "elytra_navigation", "sleep"),
						suggestions))
				.then(Commands
					.argument("value", StringArgumentType.word())
					.suggests((context, suggestions) -> SharedSuggestionProvider
						.suggest(List.of("true", "false"), suggestions))
					.executes(this::setFunction)));
	}
	
	private int setFunction(CommandContext<CommandSourceStack> context)
	{
		String name = StringArgumentType.getString(context, "name");
		boolean value = Boolean.parseBoolean(
			StringArgumentType.getString(context, "value"));
		
		PerimeterDiggerHack hack = hack();
		hack.ensureConfigLoaded();
		var functions = hack.getConfig().functions;
		
		switch(name.toLowerCase(Locale.ROOT))
		{
			case "collect_drops" -> functions.collectDrops = value;
			case "unload" -> functions.unload = value;
			case "eat" -> functions.eat = value;
			case "durability_recovery" -> functions.durabilityRecovery = value;
			case "cross_dimension_repair" ->
				functions.crossDimensionRepair = value;
			case "resupply" -> functions.resupply = value;
			case "elytra_navigation" -> functions.elytraNavigation = value;
			case "sleep" -> functions.sleep = value;
			default -> {
				return feedback(context, PerimeterText
					.get("perimeterdigger.command.unknown_value", name));
			}
		}
		
		hack.saveConfig();
		return feedback(context, name + " = " + value);
	}
	
	private LiteralArgumentBuilder<CommandSourceStack> buildDurability()
	{
		return Commands.literal("durability")
			.then(Commands.literal("mode")
				.then(Commands
					.argument("mode", StringArgumentType.word())
					.suggests((context, suggestions) -> SharedSuggestionProvider
						.suggest(List.of("repair_portal", "supply_point"),
							suggestions))
					.executes(context -> {
						String value = StringArgumentType.getString(context,
							"mode");
						PerimeterRecoveryMode mode =
							PerimeterRecoveryMode.fromId(value);
						
						if(mode == null)
							return feedback(context, PerimeterText.get(
								"perimeterdigger.command.unknown_value",
								value));
						
						PerimeterDiggerHack hack = hack();
						hack.ensureConfigLoaded();
						hack.getConfig().durabilityRecoveryMode = mode.id();
						hack.saveConfig();
						return feedback(context, PerimeterText.get(
							"perimeterdigger.command.set", "durability mode",
							mode.id()));
					})));
	}
	
	private LiteralArgumentBuilder<CommandSourceStack> buildAdvanced()
	{
		return Commands.literal("advanced")
			.then(literalWithRun("list", context -> {
				StringBuilder builder = new StringBuilder();
				
				for(String key : PerimeterAdvancedOptions.keys())
				{
					PerimeterAdvancedOption option =
						PerimeterAdvancedOptions.get(key);
					
					if(builder.length() > 0)
						builder.append(", ");
					
					builder.append(key).append('=')
						.append(option.format(config().advanced));
				}
				
				return feedback(context, builder.toString());
			}))
			.then(Commands.literal("get")
				.then(Commands
					.argument("key", StringArgumentType.word())
					.suggests((context, suggestions) ->
						SharedSuggestionProvider.suggest(
							PerimeterAdvancedOptions.keys(), suggestions))
					.executes(context -> {
						String key = StringArgumentType.getString(context,
							"key");
						
						if(!PerimeterAdvancedOptions.has(key))
							return feedback(context, PerimeterText.get(
								"perimeterdigger.command.unknown_option",
								key));
						
						return feedback(context, key + " = "
							+ PerimeterAdvancedOptions.get(key)
								.format(config().advanced));
					})))
			.then(Commands.literal("set")
				.then(Commands
					.argument("key", StringArgumentType.word())
					.suggests((context, suggestions) ->
						SharedSuggestionProvider.suggest(
							PerimeterAdvancedOptions.keys(), suggestions))
					.then(Commands
						.argument("value", StringArgumentType.word())
						.executes(this::setAdvanced))));
	}
	
	private int setAdvanced(CommandContext<CommandSourceStack> context)
	{
		String key = StringArgumentType.getString(context, "key");
		String value = StringArgumentType.getString(context, "value");
		
		if(!PerimeterAdvancedOptions.has(key))
			return feedback(context, PerimeterText
				.get("perimeterdigger.command.unknown_option", key));
		
		double parsed;
		
		try
		{
			parsed = Double.parseDouble(value);
		}catch(NumberFormatException e)
		{
			return feedback(context, PerimeterText
				.get("perimeterdigger.command.unknown_value", value));
		}
		
		try
		{
			PerimeterAdvancedOptions.get(key).set(config().advanced, parsed);
		}catch(IllegalArgumentException e)
		{
			return feedback(context, e.getMessage());
		}
		
		save();
		return feedback(context, key + " = "
			+ PerimeterAdvancedOptions.get(key).format(config().advanced));
	}
	
	private LiteralArgumentBuilder<CommandSourceStack> buildConfig()
	{
		return Commands.literal("config")
			.then(literalWithRun("save", context -> {
				save();
				return feedback(context,
					PerimeterText.get("perimeterdigger.command.saved",
						hack().getStore().path().toString()));
			}))
			.then(literalWithRun("load", context -> {
				hack().loadConfig();
				return feedback(context, PerimeterText.get("perimeterdigger.command.config_reloaded"));
			}))
			.then(literalWithRun("path", context -> feedback(context,
				hack().getStore().path() + " (" + hack().getStore().identity()
					+ ")")))
			.then(literalWithRun("reset", context -> {
				PerimeterDiggerHack hack = hack();
				hack.ensureConfigLoaded();
				PerimeterConfig fresh = new PerimeterConfig();
				var replacement = hack.getConfig();
				replacement.detectedArea = fresh.detectedArea;
				replacement.diggingMinY = null;
				replacement.diggingMaxY = null;
				replacement.unloadingPoints.clear();
				replacement.consumableSupplyPoint = new PerimeterPosition();
				replacement.durabilitySupplyPoint = new PerimeterPosition();
				replacement.bedPoint = new PerimeterPosition();
				replacement.perimeterPortalOverworld = new PerimeterPosition();
				replacement.perimeterPortalNether = new PerimeterPosition();
				replacement.repairPortalOverworld = new PerimeterPosition();
				replacement.repairPortalNether = new PerimeterPosition();
				replacement.furnaceRowStart = new PerimeterPosition();
				replacement.furnaceRowEnd = new PerimeterPosition();
				replacement.liquidPolicy = fresh.liquidPolicy;
				replacement.durabilityRecoveryMode =
					fresh.durabilityRecoveryMode;
				replacement.sealingBlocks = new ArrayList<>(
					fresh.sealingBlocks);
				replacement.foods = new ArrayList<>(fresh.foods);
				replacement.unloadingWhitelist = new ArrayList<>();
				replacement.functions = fresh.functions;
				replacement.advanced = fresh.advanced;
				hack.saveConfig();
				return feedback(context, PerimeterText.get("perimeterdigger.command.config_reset"));
			}));
	}
	
	// ------------------------------------------------------------------
	// runtime
	// ------------------------------------------------------------------
	
	private int start(CommandContext<CommandSourceStack> context)
	{
		hack().start();
		return feedback(context,
			PerimeterText.get("perimeterdigger.command.started"));
	}
	
	private int stop(CommandContext<CommandSourceStack> context)
	{
		hack().stop();
		return feedback(context,
			PerimeterText.get("perimeterdigger.command.stopped"));
	}
	
	private int pause(CommandContext<CommandSourceStack> context)
	{
		hack().pause();
		return feedback(context,
			PerimeterText.get("perimeterdigger.command.paused"));
	}
	
	private int resume(CommandContext<CommandSourceStack> context)
	{
		hack().resume();
		return feedback(context,
			PerimeterText.get("perimeterdigger.command.resumed"));
	}
	
	private int status(CommandContext<CommandSourceStack> context)
	{
		PerimeterAutomation automation = hack().getAutomation();
		List<String> lines = automation.describe();
		lines.add("state: " + PerimeterText
			.stateName(automation.getState()));
		return feedback(context, String.join(" | ", lines));
	}
	
	private int history(CommandContext<CommandSourceStack> context)
	{
		return feedback(context, String.join(" | ",
			hack().getAutomation().getHistory().describe()));
	}
	
	// ------------------------------------------------------------------
	// helpers
	// ------------------------------------------------------------------
	
	private interface CommandRunner
	{
		int run(CommandContext<CommandSourceStack> context);
	}
	
	private static LiteralArgumentBuilder<CommandSourceStack> literalWithRun(
		String name, CommandRunner runner)
	{
		return Commands.literal(name).executes(runner::run);
	}
	
	private static int feedback(CommandContext<CommandSourceStack> context,
		String message)
	{
		context.getSource().sendSystemMessage(Component.literal(message));
		return 1;
	}
	
	private static PerimeterDiggerHack hack()
	{
		return WurstClient.INSTANCE.getHax().perimeterDiggerHack;
	}
	
	private static PerimeterConfig config()
	{
		PerimeterDiggerHack hack = hack();
		hack.ensureConfigLoaded();
		return hack.getConfig();
	}
	
	private static void save()
	{
		hack().saveConfig();
	}
	
	private static List<String> blockIds()
	{
		List<String> ids = new ArrayList<>();
		
		for(Block block : BuiltInRegistries.BLOCK)
			ids.add(BuiltInRegistries.BLOCK.getKey(block).toString());
		
		return ids;
	}
	
	private static String blockId(String nameOrId)
	{
		Block block = BlockUtils.getBlockFromNameOrID(nameOrId);
		return block == null ? null
			: BuiltInRegistries.BLOCK.getKey(block).toString();
	}
	
	private static String itemId(String nameOrId)
	{
		var item = net.wurstclient.util.ItemUtils
			.getItemFromNameOrID(nameOrId);
		return item == null ? null
			: BuiltInRegistries.ITEM.getKey(item).toString();
	}
	
	private static PerimeterGrid grid(Level level)
	{
		return new PerimeterGrid()
		{
			@Override
			public String blockIdAt(int x, int y, int z)
			{
				BlockState state = level.getBlockState(new BlockPos(x, y, z));
				return BuiltInRegistries.BLOCK.getKey(state.getBlock())
					.toString();
			}
			
			@Override
			public int renderDistanceChunks()
			{
				return WurstClient.MC.options.renderDistance().get();
			}
			
			@Override
			public boolean isChunkLoaded(int chunkX, int chunkZ)
			{
				return level.hasChunk(chunkX, chunkZ);
			}
		};
	}
}
