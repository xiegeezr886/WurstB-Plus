/*
 * Copyright (c) 2026 WurstB+ Plus contributors
 *
 * This source code is subject to the terms of the GNU Affero General Public
 * License, version 3. If a copy of the AGPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/agpl-3.0.txt
 */
// Ported from Soar Client v7.1.2 (LGPL-3.0).
package net.wurstclient.hacks;

import org.lwjgl.glfw.GLFW;

import com.mojang.blaze3d.platform.InputConstants;

import net.wurstclient.Category;
import net.wurstclient.SearchTags;
import net.wurstclient.events.KeyPressListener;
import net.wurstclient.hack.Hack;
import net.wurstclient.settings.EnumSetting;
import net.wurstclient.settings.TextFieldSetting;

/**
 * Sends a Hypixel /play command for the selected game mode when the keybind is
 * pressed. The original Soar module opened a picker GUI; this port simplifies
 * that to an enum setting.
 */
@SearchTags({"hypixel quick play", "quickplay", "quick play", "hypixel", "play",
	"bedwars", "skywars", "duels", "arcade", "murder mystery", "tnt", "uhc"})
public final class HypixelQuickPlayHack extends Hack
	implements KeyPressListener
{
	private final EnumSetting<GameMode> mode = new EnumSetting<>("Game mode",
		"The /play command that will be sent.", GameMode.values(),
		GameMode.BEDWARS_SOLO);

	private final TextFieldSetting keybind = new TextFieldSetting("Keybind",
		"Key that sends the /play command, e.g. key.keyboard.n.",
		"key.keyboard.n", HypixelQuickPlayHack::isValidKey);

	public HypixelQuickPlayHack()
	{
		super("HypixelQuickPlay");
		setCategory(Category.CHAT);
		addSetting(mode);
		addSetting(keybind);
	}

	@Override
	protected void onEnable()
	{
		EVENTS.add(KeyPressListener.class, this);
	}

	@Override
	protected void onDisable()
	{
		EVENTS.remove(KeyPressListener.class, this);
	}

	@Override
	public void onKeyPress(KeyPressEvent event)
	{
		if(event.getAction() != GLFW.GLFW_PRESS)
			return;

		if(MC.gui.screen() != null || MC.player == null || MC.getConnection() == null)
			return;

		String key = keybind.getValue().trim();
		if(key.isEmpty())
			return;

		int keyCode;
		try
		{
			keyCode = InputConstants.getKey(key).getValue();
		}catch(IllegalArgumentException e)
		{
			return;
		}

		if(event.getKeyCode() != keyCode)
			return;

		MC.getConnection().sendCommand("play " + mode.getSelected().command);
	}

	private static boolean isValidKey(String value)
	{
		String key = value.trim();
		if(key.isEmpty())
			return false;

		try
		{
			InputConstants.getKey(key);
			return true;
		}catch(IllegalArgumentException e)
		{
			return false;
		}
	}

	private enum GameMode
	{
		BEDWARS_SOLO("Bedwars Solo", "bedwars_eight_one"),
		BEDWARS_DOUBLES("Bedwars Doubles", "bedwars_eight_two"),
		BEDWARS_3V3V3V3("Bedwars 3v3v3v3", "bedwars_four_three"),
		BEDWARS_4V4V4V4("Bedwars 4v4v4v4", "bedwars_four_four"),
		BEDWARS_4V4("Bedwars 4v4", "bedwars_two_four"),
		BEDWARS_CASTLE("Bedwars Castle", "bedwars_castle"),
		SKYWARS_SOLO_NORMAL("SkyWars Solo Normal", "solo_normal"),
		SKYWARS_SOLO_INSANE("SkyWars Solo Insane", "solo_insane"),
		SKYWARS_TEAMS_NORMAL("SkyWars Teams Normal", "teams_normal"),
		SKYWARS_TEAMS_INSANE("SkyWars Teams Insane", "teams_insane"),
		SKYWARS_MEGA("SkyWars Mega", "mega_normal"),
		DUELS_CLASSIC("Duels Classic", "duels_classic_duel"),
		DUELS_SUMO("Duels Sumo", "duels_sumo_duel"),
		DUELS_BRIDGE_1V1("Duels Bridge 1v1", "duels_bridge_duel"),
		DUELS_BRIDGE_2V2("Duels Bridge 2v2", "duels_bridge_doubles"),
		DUELS_BRIDGE_4V4("Duels Bridge 4v4", "duels_bridge_four"),
		ARCADE_CTW("Arcade Capture The Wool", "arcade_pvp_ctw"),
		ARCADE_HOLE_IN_THE_WALL("Arcade Hole In The Wall",
			"arcade_hole_in_the_wall"),
		ARCADE_SOCCER("Arcade Football", "arcade_soccer"),
		ARCADE_ZOMBIES_DEAD_END("Arcade Zombies Dead End",
			"arcade_zombies_dead_end"),
		MURDER_CLASSIC("Murder Mystery Classic", "murder_classic"),
		MURDER_DOUBLE_UP("Murder Mystery Double Up", "murder_double_up"),
		MURDER_INFECTION("Murder Mystery Infection", "murder_infection"),
		TNT_RUN("TNT Run", "tnt_tntrun"),
		TNT_PVP_RUN("TNT PVP Run", "tnt_pvprun"),
		TNT_BOW_SPLEEF("TNT Bow Spleef", "tnt_bowspleef"),
		TNT_TAG("TNT Tag", "tnt_tntag"),
		UHC_SOLO("UHC Solo", "uhc_solo"),
		UHC_TEAMS("UHC Teams", "uhc_teams");

		private final String displayName;
		private final String command;

		private GameMode(String displayName, String command)
		{
			this.displayName = displayName;
			this.command = command;
		}

		@Override
		public String toString()
		{
			return displayName;
		}
	}
}
