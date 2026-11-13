/*
 * Copyright (c) 2026 WurstB+ Plus contributors
 *
 * This source code is subject to the terms of the GNU Affero General Public
 * License, version 3. If a copy of the AGPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/agpl-3.0.txt
 */
// Ported from InvincibleMachineGun (GPL-3.0).
package net.wurstclient.hacks;

import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundPlayerPositionPacket;
import net.wurstclient.Category;
import net.wurstclient.SearchTags;
import net.wurstclient.events.PacketInputListener;
import net.wurstclient.events.UpdateListener;
import net.wurstclient.hack.Hack;
import net.wurstclient.settings.CheckboxSetting;
import net.wurstclient.settings.EnumSetting;
import net.wurstclient.settings.SliderSetting;
import net.wurstclient.settings.SliderSetting.ValueDisplay;
import net.wurstclient.settings.TextFieldSetting;
import net.wurstclient.util.ChatUtils;

/**
 * Repeatedly runs an RTP command until a target coordinate or biome is found.
 *
 * <p>
 * Simplified from InvincibleMachineGun's RTsearch: the Discord webhook, the
 * custom biome picker GUI and the full biome list are omitted. The built-in
 * biome list covers the common overworld, nether and end biomes.
 */
@SearchTags({"rtp search", "rtsearch", "random teleport", "biome finder",
	"coordinate search"})
public final class RTsearchHack extends Hack
	implements UpdateListener, PacketInputListener
{
	private final EnumSetting<Mode> mode = new EnumSetting<>("Mode",
		"Coordinates searches for an X/Z position. Biome searches for a biome.",
		Mode.values(), Mode.COORDINATES);

	private final SliderSetting targetX = new SliderSetting("Target X",
		"Target X coordinate.", 0, -30000000, 30000000, 100,
		ValueDisplay.INTEGER);

	private final SliderSetting targetZ = new SliderSetting("Target Z",
		"Target Z coordinate.", 0, -30000000, 30000000, 100,
		ValueDisplay.INTEGER);

	private final TextFieldSetting distance = new TextFieldSetting(
		"Target distance",
		"Stop when within this horizontal distance. Supports k and m suffixes.",
		"1000");

	private final TextFieldSetting rtpCommand = new TextFieldSetting(
		"RTP command", "Command sent without the leading slash.", "rtp");

	private final EnumSetting<Biome> targetBiome = new EnumSetting<>(
		"Target biome", "Biome to search for in biome mode.", Biome.values(),
		Biome.PLAINS);

	private final CheckboxSetting disconnectOnReach = new CheckboxSetting(
		"Disconnect on reach",
		"Disconnect from the server once the target is reached.", true);

	private final SliderSetting rtpDelay = new SliderSetting("RTP delay",
		"Seconds between RTP attempts.", 3, 1, 30, 1,
		ValueDisplay.INTEGER.withSuffix(" s"));

	private int tickTimer;
	private int rtpWaitTicks;
	private int attempts;
	private boolean rtping;

	public RTsearchHack()
	{
		super("RTsearch");
		setCategory(Category.MOVEMENT);
		addSetting(mode);
		addSetting(targetX);
		addSetting(targetZ);
		addSetting(distance);
		addSetting(rtpCommand);
		addSetting(targetBiome);
		addSetting(disconnectOnReach);
		addSetting(rtpDelay);

		targetX.visibleWhen(() -> mode.getSelected() == Mode.COORDINATES);
		targetZ.visibleWhen(() -> mode.getSelected() == Mode.COORDINATES);
		distance.visibleWhen(() -> mode.getSelected() == Mode.COORDINATES);
		targetBiome.visibleWhen(() -> mode.getSelected() == Mode.BIOME);
	}

	@Override
	protected void onEnable()
	{
		tickTimer = rtpDelay.getValueI() * 20;
		rtpWaitTicks = 0;
		attempts = 0;
		rtping = false;
		EVENTS.add(UpdateListener.class, this);
		EVENTS.add(PacketInputListener.class, this);
	}

	@Override
	protected void onDisable()
	{
		EVENTS.remove(UpdateListener.class, this);
		EVENTS.remove(PacketInputListener.class, this);
	}

	@Override
	public void onUpdate()
	{
		if(MC.player == null || MC.level == null)
			return;

		if(mode.getSelected() == Mode.COORDINATES)
		{
			double current = getCurrentDistance();
			if(current <= parseDistance())
			{
				finish("Reached target coordinates after " + attempts
					+ " attempts (" + String.format("%.1f", current)
					+ " blocks away).");
				return;
			}
		}else if(isInTargetBiome())
		{
			finish("Found " + targetBiome.getSelected().getDisplayName()
				+ " after " + attempts + " attempts.");
			return;
		}

		tickTimer++;

		if(rtping && ++rtpWaitTicks >= 5 * 20)
		{
			rtping = false;
			rtpWaitTicks = 0;
		}

		if(!rtping && tickTimer >= rtpDelay.getValueI() * 20)
		{
			performRtp();
			tickTimer = 0;
		}
	}

	@Override
	public void onReceivedPacket(PacketInputEvent event)
	{
		if(!(event.getPacket() instanceof ClientboundPlayerPositionPacket))
			return;

		rtping = false;
		rtpWaitTicks = 0;
		attempts++;
	}

	private void performRtp()
	{
		if(MC.getConnection() == null)
			return;

		String command = rtpCommand.getValue().trim();
		if(command.startsWith("/"))
			command = command.substring(1);

		if(command.isEmpty())
			return;

		rtping = true;
		rtpWaitTicks = 0;
		MC.getConnection().sendCommand(command);
	}

	private boolean isInTargetBiome()
	{
		return getCurrentBiomeId()
			.equals(targetBiome.getSelected().getId());
	}

	private String getCurrentBiomeId()
	{
		if(MC.level == null || MC.player == null)
			return "";

		return MC.level.getBiome(MC.player.blockPosition()).unwrapKey()
			.map(Object::toString).orElse("");
	}

	private double getCurrentDistance()
	{
		double dx = MC.player.getX() - targetX.getValue();
		double dz = MC.player.getZ() - targetZ.getValue();
		return Math.sqrt(dx * dx + dz * dz);
	}

	private int parseDistance()
	{
		String value = distance.getValue().toLowerCase().replace(",", "").trim();
		double multiplier = 1;

		if(value.endsWith("k"))
		{
			multiplier = 1000;
			value = value.substring(0, value.length() - 1);
		}else if(value.endsWith("m"))
		{
			multiplier = 1_000_000;
			value = value.substring(0, value.length() - 1);
		}

		try
		{
			return Math.max(1, (int)(Double.parseDouble(value) * multiplier));
		}catch(NumberFormatException e)
		{
			ChatUtils.warning("RTsearch: invalid distance, using 1000.");
			return 1000;
		}
	}

	private void finish(String message)
	{
		ChatUtils.message("RTsearch: " + message);
		setEnabled(false);

		if(disconnectOnReach.isChecked() && MC.getConnection() != null)
			MC.getConnection().getConnection()
				.disconnect(Component.literal("RTsearch: target reached"));
	}

	private enum Mode
	{
		COORDINATES("Coordinates"),
		BIOME("Biome");

		private final String name;

		private Mode(String name)
		{
			this.name = name;
		}

		@Override
		public String toString()
		{
			return name;
		}
	}

	private enum Biome
	{
		PLAINS("Plains", "minecraft:plains"),
		SUNFLOWER_PLAINS("Sunflower Plains", "minecraft:sunflower_plains"),
		SNOWY_PLAINS("Snowy Plains", "minecraft:snowy_plains"),
		FOREST("Forest", "minecraft:forest"),
		FLOWER_FOREST("Flower Forest", "minecraft:flower_forest"),
		BIRCH_FOREST("Birch Forest", "minecraft:birch_forest"),
		DARK_FOREST("Dark Forest", "minecraft:dark_forest"),
		TAIGA("Taiga", "minecraft:taiga"),
		SNOWY_TAIGA("Snowy Taiga", "minecraft:snowy_taiga"),
		JUNGLE("Jungle", "minecraft:jungle"),
		BAMBOO_JUNGLE("Bamboo Jungle", "minecraft:bamboo_jungle"),
		DESERT("Desert", "minecraft:desert"),
		SAVANNA("Savanna", "minecraft:savanna"),
		BADLANDS("Badlands", "minecraft:badlands"),
		SWAMP("Swamp", "minecraft:swamp"),
		MANGROVE_SWAMP("Mangrove Swamp", "minecraft:mangrove_swamp"),
		BEACH("Beach", "minecraft:beach"),
		SNOWY_SLOPES("Snowy Slopes", "minecraft:snowy_slopes"),
		JAGGED_PEAKS("Jagged Peaks", "minecraft:jagged_peaks"),
		FROZEN_PEAKS("Frozen Peaks", "minecraft:frozen_peaks"),
		STONY_PEAKS("Stony Peaks", "minecraft:stony_peaks"),
		OCEAN("Ocean", "minecraft:ocean"),
		DEEP_OCEAN("Deep Ocean", "minecraft:deep_ocean"),
		RIVER("River", "minecraft:river"),
		MUSHROOM_FIELDS("Mushroom Fields", "minecraft:mushroom_fields"),
		DRIPSTONE_CAVES("Dripstone Caves", "minecraft:dripstone_caves"),
		LUSH_CAVES("Lush Caves", "minecraft:lush_caves"),
		DEEP_DARK("Deep Dark", "minecraft:deep_dark"),
		CHERRY_GROVE("Cherry Grove", "minecraft:cherry_grove"),
		MEADOW("Meadow", "minecraft:meadow"),
		GROVE("Grove", "minecraft:grove"),
		NETHER_WASTES("Nether Wastes", "minecraft:nether_wastes"),
		SOUL_SAND_VALLEY("Soul Sand Valley", "minecraft:soul_sand_valley"),
		CRIMSON_FOREST("Crimson Forest", "minecraft:crimson_forest"),
		WARPED_FOREST("Warped Forest", "minecraft:warped_forest"),
		BASALT_DELTAS("Basalt Deltas", "minecraft:basalt_deltas"),
		THE_END("The End", "minecraft:the_end"),
		END_HIGHLANDS("End Highlands", "minecraft:end_highlands"),
		END_MIDLANDS("End Midlands", "minecraft:end_midlands"),
		SMALL_END_ISLANDS("Small End Islands",
			"minecraft:small_end_islands"),
		END_BARRENS("End Barrens", "minecraft:end_barrens");

		private final String displayName;
		private final String id;

		private Biome(String displayName, String id)
		{
			this.displayName = displayName;
			this.id = id;
		}

		private String getDisplayName()
		{
			return displayName;
		}

		private String getId()
		{
			return id;
		}

		@Override
		public String toString()
		{
			return displayName;
		}
	}
}
