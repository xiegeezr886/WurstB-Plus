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
import net.wurstclient.util.AntiBotPredicate;
import net.wurstclient.util.AntiBotPredicate.Snapshot;
import net.wurstclient.util.AntiBotPredicate.Verdict;
import net.wurstclient.util.AntiBotSettings;
import net.wurstclient.util.AntiBotTracker;

@SearchTags({"anti bot", "fake player", "npc detect"})
public final class AntiBotHack extends Hack
	implements UpdateListener, WorldChangeListener
{
	/**
	 * 「贴地但竖直速度非零」必须连续成立这么多 tick 才算数。取 2 是为了放过
	 * 客户端插值 / 载具 / 丢包造成的单 tick 假象，同时仍然抓得住持续伪造竖直
	 * 位移的假人。判定逻辑见 {@link AntiBotPredicate} 与 {@link AntiBotTracker}。
	 */
	private static final int IMPOSSIBLE_GROUND_GRACE_TICKS = 2;

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
	private final AntiBotTracker tracker =
		new AntiBotTracker(IMPOSSIBLE_GROUND_GRACE_TICKS);

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
		tracker.reset();
	}

	@Override
	public void onWorldChange(ClientLevel world)
	{
		detectedBots.clear();
		tracker.reset();
	}

	@Override
	public void onUpdate()
	{
		if(MC.level == null || MC.player == null)
		{
			detectedBots.clear();
			tracker.reset();
			return;
		}

		Set<UUID> onlineUuids = new HashSet<>();
		Map<String, Integer> nameCounts = new HashMap<>();
		for(Player player : MC.level.players())
		{
			onlineUuids.add(player.getUUID());
			nameCounts.merge(profileName(player), 1, Integer::sum);
		}
		// 玩家下线 / 卸载后立刻丢弃其跨 tick 状态，列表不会随对局时长增长
		tracker.retainOnly(onlineUuids);

		AntiBotSettings settings = snapshotSettings();
		Set<UUID> nextBots = new HashSet<>();
		for(Player player : MC.level.players())
		{
			if(player == MC.player)
				continue;

			UUID uuid = player.getUUID();
			Snapshot snapshot = snapshot(player, uuid, settings);
			if(AntiBotPredicate.classify(snapshot, nameCounts,
				settings) == Verdict.BOT)
				nextBots.add(uuid);
		}

		detectedBots.clear();
		detectedBots.addAll(nextBots);
	}

	private AntiBotSettings snapshotSettings()
	{
		return new AntiBotSettings(checkPlayerInfo.isChecked(),
			checkGameMode.isChecked(), checkPing.isChecked(),
			checkGround.isChecked(), checkInvisible.isChecked(),
			checkUuid.isChecked(), checkIllegalPitch.isChecked(),
			checkIllegalHealth.isChecked(), checkEntityId.isChecked(),
			checkDuplicateName.isChecked(), minimumAge.getValueI());
	}

	private Snapshot snapshot(Player player, UUID uuid, AntiBotSettings settings)
	{
		PlayerInfo info = MC.player.connection.getPlayerInfo(uuid);
		// 关掉该判据时既不读也不写 tracker，避免重新打开时命中旧计数
		boolean impossibleGround = false;
		if(settings.checkGround())
			impossibleGround = tracker.noteImpossibleGround(uuid,
				AntiBotPredicate.isImpossibleGroundState(player.onGround(),
					player.getDeltaMovement().y));

		return new Snapshot(profileName(player), info != null,
			info != null && info.getGameMode() != null,
			info != null ? info.getLatency() : -1, impossibleGround,
			player.isInvisible(), player.getXRot(), player.getHealth(),
			player.getMaxHealth(), player.getId(), uuid.toString(),
			player.tickCount);
	}

	/**
	 * 玩家档案名（不带计分板队伍前缀 / 颜色），小写化后用于重名判据。
	 */
	private String profileName(Player player)
	{
		return AntiBotPredicate
			.normalizeName(player.getGameProfile().getName());
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
