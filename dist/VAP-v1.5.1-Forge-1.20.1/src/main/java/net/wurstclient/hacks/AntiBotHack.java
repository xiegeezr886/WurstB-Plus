/*
 * This file contains a Forge/Mojmap adaptation of LiquidBounce's custom
 * AntiBot predicates.
 *
 * Copyright (c) 2015-2026 CCBlueX
 * Copyright (c) 2025 Penguin
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 */
package net.wurstclient.hacks;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.Locale;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.world.entity.player.Player;
import net.wurstclient.Category;
import net.wurstclient.SearchTags;
import net.wurstclient.events.UpdateListener;
import net.wurstclient.events.WorldChangeListener;
import net.wurstclient.hack.Hack;
import net.wurstclient.settings.CheckboxSetting;
import net.wurstclient.settings.SliderSetting;
import net.wurstclient.settings.SliderSetting.ValueDisplay;

@SearchTags({"anti bot", "fake player", "npc detect"})
public final class AntiBotHack extends Hack
	implements UpdateListener, WorldChangeListener
{
	private final CheckboxSetting checkPlayerInfo = new CheckboxSetting(
		"Check player info", "Detects entities missing from the tab list.", true);
	private final CheckboxSetting checkGameMode = new CheckboxSetting(
		"Check game mode", "Detects tab entries without a game mode.", true);
	private final CheckboxSetting checkPing = new CheckboxSetting("Check ping",
		"Detects tab entries with zero latency.", false);
	private final CheckboxSetting checkGround = new CheckboxSetting(
		"Check ground", "Detects impossible on-ground vertical movement.", false);
	private final CheckboxSetting checkInvisible = new CheckboxSetting(
		"Check invisible", "Treats invisible players as bots.", true);
	private final CheckboxSetting checkUuid = new CheckboxSetting("Check UUID",
		"Detects suspicious UUID patterns.", true);
	private final CheckboxSetting checkIllegalPitch = new CheckboxSetting(
		"Check illegal pitch", "Detects pitch values outside -90 to 90.", true);
	private final CheckboxSetting checkIllegalHealth = new CheckboxSetting(
		"Check illegal health", "Detects health above the entity maximum.", true);
	private final CheckboxSetting checkEntityId = new CheckboxSetting(
		"Check entity ID", "Detects entity IDs outside the vanilla range.", true);
	private final CheckboxSetting checkDuplicateName = new CheckboxSetting(
		"Check duplicate name", "Detects duplicate player profile names.", true);
	private final SliderSetting minimumAge = new SliderSetting("Minimum age",
		"Players younger than this many ticks are temporarily ignored.", 5, 0,
		40, 1, ValueDisplay.INTEGER.withSuffix(" ticks"));

	private final Set<UUID> detectedBots = new HashSet<>();

	public AntiBotHack()
	{
		super("AntiBot");
		setCategory(Category.COMBAT);
		addSetting(checkPlayerInfo);
		addSetting(checkGameMode);
		addSetting(checkPing);
		addSetting(checkGround);
		addSetting(checkInvisible);
		addSetting(checkUuid);
		addSetting(checkIllegalPitch);
		addSetting(checkIllegalHealth);
		addSetting(checkEntityId);
		addSetting(checkDuplicateName);
		addSetting(minimumAge);
	}

	@Override
	protected void onEnable()
	{
		EVENTS.add(UpdateListener.class, this);
		EVENTS.add(WorldChangeListener.class, this);
	}

	@Override
	protected void onDisable()
	{
		EVENTS.remove(UpdateListener.class, this);
		EVENTS.remove(WorldChangeListener.class, this);
		detectedBots.clear();
	}

	@Override
	public void onWorldChange(ClientLevel world)
	{
		detectedBots.clear();
	}

	@Override
	public void onUpdate()
	{
		if(MC.level == null || MC.player == null)
		{
			detectedBots.clear();
			return;
		}

		Map<String, Integer> nameCounts = new HashMap<>();
		for(Player player : MC.level.players())
			nameCounts.merge(normalizeName(player), 1, Integer::sum);

		Set<UUID> nextBots = new HashSet<>();
		for(Player player : MC.level.players())
		{
			if(player == MC.player)
				continue;
			PlayerInfo info = MC.player.connection
				.getPlayerInfo(player.getUUID());
			if(isBot(player, info, nameCounts))
				nextBots.add(player.getUUID());
		}

		detectedBots.clear();
		detectedBots.addAll(nextBots);
	}

	private boolean isBot(Player player, PlayerInfo info,
		Map<String, Integer> nameCounts)
	{
		if(checkPlayerInfo.isChecked() && info == null)
			return true;
		if(checkGameMode.isChecked() && info != null && info.getGameMode() == null)
			return true;
		if(checkPing.isChecked() && info != null && info.getLatency() <= 0)
			return true;
		if(checkGround.isChecked() && player.onGround()
			&& Math.abs(player.getDeltaMovement().y) > 0.1)
			return true;
		if(checkInvisible.isChecked() && player.isInvisible())
			return true;
		if(checkIllegalPitch.isChecked() && Math.abs(player.getXRot()) > 90)
			return true;
		if(checkIllegalHealth.isChecked()
			&& (!Float.isFinite(player.getHealth()) || player.getHealth() < 0
				|| player.getHealth() > player.getMaxHealth()))
			return true;
		if(checkEntityId.isChecked()
			&& (player.getId() < 0 || player.getId() > 1_000_000_000))
			return true;
		if(checkDuplicateName.isChecked()
			&& nameCounts.getOrDefault(normalizeName(player), 0) > 1)
			return true;
		if(player.tickCount < minimumAge.getValueI())
			return true;

		if(!checkUuid.isChecked())
			return false;
		String uuid = player.getUUID().toString();
		return uuid.startsWith("00000000") || uuid.endsWith("000000000000");
	}

	private String normalizeName(Player player)
	{
		return player.getGameProfile().getName().toLowerCase(Locale.ROOT);
	}

	public boolean isBot(Player player)
	{
		return isEnabled() && detectedBots.contains(player.getUUID());
	}

	public boolean isBot(UUID uuid)
	{
		return isEnabled() && detectedBots.contains(uuid);
	}
}
