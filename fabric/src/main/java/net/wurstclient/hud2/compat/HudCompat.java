/*
 * Copyright (c) 2026 WurstB+ Plus contributors
 *
 * This source code is subject to the terms of the GNU Affero General Public
 * License, version 3. If a copy of the AGPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/agpl-3.0.txt
 */
package net.wurstclient.hud2.compat;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;
import net.minecraft.world.BossEvent;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.horse.AbstractHorse;
import net.minecraft.world.entity.item.PrimedTnt;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.scores.Objective;
import net.minecraft.world.scores.PlayerTeam;
import net.minecraft.world.scores.Score;
import net.minecraft.world.scores.Scoreboard;
import net.wurstclient.WurstClient;
import net.wurstclient.mixinterface.IBossHealthOverlay;

/**
 * Version-specific data access used by the HUD modules.
 *
 * <p>Only this file changes between Minecraft versions; the modules themselves
 * stay identical.
 */
public final class HudCompat
{
	private static final long START_TIME = System.currentTimeMillis();

	private HudCompat() {}

	public static int fps()
	{
		return WurstClient.MC.getFps();
	}

	public static int ping()
	{
		if(WurstClient.MC.getConnection() == null
			|| WurstClient.MC.player == null)
			return 0;
		PlayerInfo info = WurstClient.MC.getConnection()
			.getPlayerInfo(WurstClient.MC.player.getUUID());
		return info == null ? 0 : info.getLatency();
	}

	public static String serverIp()
	{
		if(WurstClient.MC.isSingleplayer())
			return "Singleplayer";
		ServerData server = WurstClient.MC.getCurrentServer();
		if(server == null)
			return "Not connected";
		return server.ip == null || server.ip.isBlank() ? server.name
			: server.ip;
	}

	public static String serverName()
	{
		ServerData server = WurstClient.MC.getCurrentServer();
		return server == null ? "Singleplayer" : server.name;
	}

	public static String serverVersion()
	{
		ServerData server = WurstClient.MC.getCurrentServer();
		return server == null || server.version == null ? "-"
			: server.version.getString();
	}

	public static int serverProtocol()
	{
		ServerData server = WurstClient.MC.getCurrentServer();
		return server == null ? -1 : server.protocol;
	}

	public static long dayCount()
	{
		return WurstClient.MC.level == null ? 0
			: WurstClient.MC.level.getGameTime() / 24000L;
	}

	public static String gameMode()
	{
		if(WurstClient.MC.gameMode == null)
			return "Mode: Error";
		return switch(WurstClient.MC.gameMode.getPlayerMode())
		{
			case SURVIVAL -> "Mode: Survival";
			case CREATIVE -> "Mode: Creative";
			case SPECTATOR -> "Mode: Spectator";
			case ADVENTURE -> "Mode: Adventure";
		};
	}

	public static float health()
	{
		return WurstClient.MC.player == null ? 0
			: WurstClient.MC.player.getHealth();
	}

	public static int playerCount()
	{
		return WurstClient.MC.getConnection() == null ? 0
			: WurstClient.MC.getConnection().getOnlinePlayers().size();
	}

	public static long usedMemoryMb()
	{
		Runtime runtime = Runtime.getRuntime();
		return (runtime.totalMemory() - runtime.freeMemory())
			/ (1024L * 1024L);
	}

	public static long maxMemoryMb()
	{
		return Runtime.getRuntime().maxMemory() / (1024L * 1024L);
	}

	public static String playerName()
	{
		return WurstClient.MC.player == null ? "-"
			: WurstClient.MC.player.getGameProfile().getName();
	}

	public static String dimension()
	{
		return WurstClient.MC.level == null ? "-"
			: WurstClient.MC.level.dimension().location().getPath();
	}

	public static boolean raining()
	{
		return WurstClient.MC.level != null
			&& WurstClient.MC.level.isRaining();
	}

	public static boolean thundering()
	{
		return WurstClient.MC.level != null
			&& WurstClient.MC.level.isThundering();
	}

	public static double speedBps()
	{
		if(WurstClient.MC.player == null)
			return 0;
		var delta = WurstClient.MC.player.getDeltaMovement();
		return Math.sqrt(delta.x * delta.x + delta.z * delta.z) * 20;
	}

	public static String sessionTime()
	{
		long seconds = (System.currentTimeMillis() - START_TIME) / 1000L;
		long hours = seconds / 3600;
		long minutes = seconds % 3600 / 60;
		long secs = seconds % 60;
		return String.format("%02d:%02d:%02d", hours, minutes, secs);
	}

	public static List<ItemStack> armorItems()
	{
		if(WurstClient.MC.player == null)
			return Collections.emptyList();
		List<ItemStack> items = new ArrayList<>();
		for(int i = 3; i >= 0; i--)
			items.add(WurstClient.MC.player.getInventory().getArmor(i));
		return items;
	}

	public static List<ItemStack> inventoryItems()
	{
		if(WurstClient.MC.player == null)
			return Collections.emptyList();
		List<ItemStack> items = new ArrayList<>();
		for(int i = 9; i < 36; i++)
			items.add(WurstClient.MC.player.getInventory().getItem(i));
		return items;
	}

	public static List<ItemStack> hotbarItems()
	{
		if(WurstClient.MC.player == null)
			return Collections.emptyList();
		List<ItemStack> items = new ArrayList<>();
		for(int i = 0; i < 9; i++)
			items.add(WurstClient.MC.player.getInventory().getItem(i));
		return items;
	}

	public static List<EffectInfo> effects()
	{
		if(WurstClient.MC.player == null)
			return Collections.emptyList();
		List<EffectInfo> effects = new ArrayList<>();
		for(MobEffectInstance instance : WurstClient.MC.player
			.getActiveEffects())
		{
			MobEffect type = instance.getEffect();
			TextureAtlasSprite sprite =
				WurstClient.MC.getMobEffectTextures().get(type);
			int texture = -1;
			float u0 = 0;
			float v0 = 0;
			float u1 = 1;
			float v1 = 1;
			if(sprite != null)
			{
				AbstractTexture atlas = WurstClient.MC.getTextureManager()
					.getTexture(sprite.atlasLocation());
				if(atlas != null)
					texture = atlas.getId();
				u0 = sprite.getU0();
				v0 = sprite.getV0();
				u1 = sprite.getU1();
				v1 = sprite.getV1();
			}

			effects.add(new EffectInfo(type.getDisplayName().getString(),
				instance.getAmplifier(), instance.getDuration(),
				type.getColor(), texture, u0, v0, u1, v1));
		}
		return effects;
	}

	public static List<ScoreLine> scoreboard()
	{
		if(WurstClient.MC.level == null)
			return Collections.emptyList();

		Scoreboard scoreboard = WurstClient.MC.level.getScoreboard();
		Objective objective = scoreboard.getDisplayObjective(1);
		if(objective == null)
			return Collections.emptyList();

		List<Score> scores = new ArrayList<>(
			scoreboard.getPlayerScores(objective));
		scores.sort(Comparator.comparingInt(Score::getScore).reversed()
			.thenComparing(Score::getOwner, String.CASE_INSENSITIVE_ORDER));

		List<ScoreLine> lines = new ArrayList<>();
		for(Score score : scores)
		{
			String owner = score.getOwner();
			PlayerTeam team = scoreboard.getPlayersTeam(owner);
			String name = team == null ? toLegacy(Component.literal(owner))
				: toLegacy(team.getFormattedName(Component.literal(owner)));
			lines.add(new ScoreLine(name, score.getScore()));
			if(lines.size() >= 15)
				break;
		}
		return lines;
	}

	public static String scoreboardTitle()
	{
		if(WurstClient.MC.level == null)
			return "";
		Objective objective = WurstClient.MC.level.getScoreboard()
			.getDisplayObjective(1);
		return objective == null ? ""
			: toLegacy(objective.getDisplayName());
	}

	/**
	 * Converts a component into a legacy {@code §}-coded string so the HUD can
	 * keep team colours and per-run formatting when it draws the text itself.
	 */
	public static String toLegacy(Component component)
	{
		if(component == null)
			return "";

		StringBuilder builder = new StringBuilder();
		component.visit((style, text) ->
		{
			builder.append('\u00a7').append('r');
			TextColor color = style.getColor();
			if(color != null)
			{
				int rgb = color.getValue();
				builder.append("\u00a7x");
				for(int shift = 20; shift >= 0; shift -= 4)
					builder.append('\u00a7').append(
						Character.forDigit(rgb >> shift & 0xF, 16));
			}
			if(style.isBold())
				builder.append("\u00a7l");
			if(style.isItalic())
				builder.append("\u00a7o");
			if(style.isUnderlined())
				builder.append("\u00a7n");
			if(style.isStrikethrough())
				builder.append("\u00a7m");
			if(style.isObfuscated())
				builder.append("\u00a7k");
			builder.append(text);
			return Optional.empty();
		}, Style.EMPTY);
		return builder.toString();
	}

	public static List<PlayerEntry> tabPlayers()
	{
		if(WurstClient.MC.getConnection() == null)
			return Collections.emptyList();
		List<PlayerEntry> players = new ArrayList<>();
		for(PlayerInfo info : WurstClient.MC.getConnection()
			.getOnlinePlayers())
			players.add(new PlayerEntry(info.getProfile().getName(),
				info.getProfile().getId(), info.getLatency(),
				info.getSkinLocation().toString()));
		players.sort(Comparator.comparing(PlayerEntry::name,
			String.CASE_INSENSITIVE_ORDER));
		return players;
	}

	public static boolean playerListVisible()
	{
		return WurstClient.MC.options.keyPlayerList.isDown();
	}

	public static boolean isHypixel()
	{
		ServerData server = WurstClient.MC.getCurrentServer();
		return server != null && server.ip != null && server.ip
			.toLowerCase(java.util.Locale.ROOT).contains("hypixel.net");
	}

	public static List<BossBarInfo> bossBars()
	{
		IBossHealthOverlay overlay = (IBossHealthOverlay)WurstClient.MC.gui
			.getBossOverlay();
		List<BossBarInfo> bars = new ArrayList<>();
		for(var event : overlay.wurst_getEvents().values())
			bars.add(new BossBarInfo(event.getName().getString(),
				event.getProgress(), colorOf(event.getColor())));
		return bars;
	}

	private static int colorOf(BossEvent.BossBarColor color)
	{
		return switch(color)
		{
			case PINK -> 0xFFFF55FF;
			case BLUE -> 0xFF5555FF;
			case RED -> 0xFFFF5555;
			case GREEN -> 0xFF55FF55;
			case YELLOW -> 0xFFFFFF55;
			case PURPLE -> 0xFFAA00AA;
			case WHITE -> 0xFFFFFFFF;
		};
	}

	public static HorseInfo horse()
	{
		if(WurstClient.MC.player == null)
			return null;

		Entity vehicle = WurstClient.MC.player.getVehicle();
		if(vehicle instanceof AbstractHorse horse)
			return horseInfo(horse);

		if(WurstClient.MC.hitResult instanceof EntityHitResult hit
			&& hit.getEntity() instanceof AbstractHorse horse)
			return horseInfo(horse);

		return null;
	}

	private static HorseInfo horseInfo(AbstractHorse horse)
	{
		double maxHealth = horse.getAttributeValue(Attributes.MAX_HEALTH);
		double jump = horse.getAttributeValue(Attributes.JUMP_STRENGTH);
		double speed = horse.getAttributeValue(Attributes.MOVEMENT_SPEED);
		return new HorseInfo(horse.getHealth(), maxHealth, jump, speed);
	}

	public static TargetInfo target()
	{
		Entity entity = WurstClient.MC.crosshairPickEntity;
		if(entity == null
			&& WurstClient.MC.hitResult instanceof EntityHitResult hit)
			entity = hit.getEntity();
		if(!(entity instanceof LivingEntity living))
			return null;

		String skin = living
			instanceof net.minecraft.client.player.AbstractClientPlayer player
				? player.getSkinTextureLocation().toString() : null;
		return new TargetInfo(living.getName().getString(),
			living.getHealth(), living.getMaxHealth(),
			WurstClient.MC.player == null ? 0
				: WurstClient.MC.player.distanceTo(living),
			skin, living.getArmorValue());
	}

	/** Editor preview target: the local player. */
	public static TargetInfo localPlayerTarget()
	{
		if(WurstClient.MC.player == null)
			return null;
		Player player = WurstClient.MC.player;
		String skin = player instanceof net.minecraft.client.player.AbstractClientPlayer clientPlayer
			? clientPlayer.getSkinTextureLocation().toString() : null;
		return new TargetInfo(player.getName().getString(), player.getHealth(),
			player.getMaxHealth(), 0, skin, player.getArmorValue());
	}

	public static String targetName()
	{
		TargetInfo target = target();
		return target == null ? "" : target.name();
	}

	public static List<TntInfo> tnts()
	{
		if(WurstClient.MC.level == null || WurstClient.MC.player == null)
			return Collections.emptyList();
		List<TntInfo> result = new ArrayList<>();
		for(PrimedTnt tnt : WurstClient.MC.level.getEntitiesOfClass(
			PrimedTnt.class,
			WurstClient.MC.player.getBoundingBox().inflate(64)))
			result.add(new TntInfo(tnt.getX(), tnt.getY(), tnt.getZ(),
				tnt.getFuse()));
		return result;
	}

	public static String lookingAtBlock()
	{
		if(WurstClient.MC.hitResult == null
			|| WurstClient.MC.hitResult.getType() != HitResult.Type.BLOCK
			|| WurstClient.MC.level == null)
			return "";
		var pos = ((net.minecraft.world.phys.BlockHitResult)WurstClient.MC.hitResult)
			.getBlockPos();
		return WurstClient.MC.level.getBlockState(pos).getBlock().getName()
			.getString();
	}

	public static String heldItem()
	{
		if(WurstClient.MC.player == null)
			return "";
		ItemStack stack = WurstClient.MC.player.getMainHandItem();
		return stack.isEmpty() ? "" : stack.getHoverName().getString();
	}

	public static String packName()
	{
		var repository = WurstClient.MC.getResourcePackRepository();
		String result = "Vanilla";
		for(var pack : repository.getSelectedPacks())
			result = pack.getTitle().getString();
		return result;
	}

	public static String biomeName()
	{
		if(WurstClient.MC.level == null || WurstClient.MC.player == null)
			return "";
		var biome = WurstClient.MC.level.getBiome(
			WurstClient.MC.player.blockPosition());
		return biome.unwrapKey().map(key -> key.location().getPath())
			.orElse("");
	}

	public record EffectInfo(String name, int amplifier, int durationTicks,
		int color, int iconTexture, float iconU0, float iconV0, float iconU1,
		float iconV1)
	{}

	public record ScoreLine(String name, int score)
	{}

	public record PlayerEntry(String name, java.util.UUID uuid, int ping,
		String skin)
	{}

	public record BossBarInfo(String name, float progress, int color)
	{}

	public record HorseInfo(double health, double maxHealth, double jump,
		double speed)
	{}

	public record TargetInfo(String name, double health, double maxHealth,
		double distance, String skin, int armor)
	{}

	public record TntInfo(double x, double y, double z, int fuse)
	{}
}
