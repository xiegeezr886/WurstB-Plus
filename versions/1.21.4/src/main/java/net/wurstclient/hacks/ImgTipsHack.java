/*
 * Copyright (c) 2026 WurstB+ Plus contributors
 *
 * This source code is subject to the terms of the GNU Affero General Public
 * License, version 3. If a copy of the AGPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/agpl-3.0.txt
 */
// Ported from InvincibleMachineGun (GPL-3.0).
package net.wurstclient.hacks;

import java.awt.Color;
import java.text.DecimalFormat;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedQueue;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundEntityEventPacket;
import net.minecraft.network.protocol.game.ClientboundPlayerPositionPacket;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityEvent;
import net.minecraft.world.entity.player.Player;
import net.wurstclient.Category;
import net.wurstclient.SearchTags;
import net.wurstclient.events.DeathListener;
import net.wurstclient.events.GUIRenderListener;
import net.wurstclient.events.PacketInputListener;
import net.wurstclient.events.UpdateListener;
import net.wurstclient.hack.Hack;
import net.wurstclient.settings.CheckboxSetting;
import net.wurstclient.settings.ColorSetting;
import net.wurstclient.settings.SliderSetting;
import net.wurstclient.settings.SliderSetting.ValueDisplay;
import net.wurstclient.util.ChatUtils;
import net.minecraft.client.gui.GuiGraphics;

/**
 * Chat/HUD information module: visual range alerts, totem pop counter, death
 * coordinates, server lag / lagback warnings and active potion timers.
 *
 * <p>
 * The upstream Chinese/English switch and the inventory resistance potion
 * counter were dropped during the port; everything else is preserved.
 */
@SearchTags({"img tips", "tips", "visual range", "pop counter", "lagback",
	"server lag", "potion timer"})
public final class ImgTipsHack extends Hack implements UpdateListener,
	PacketInputListener, GUIRenderListener, DeathListener
{
	private final CheckboxSetting visualRange = new CheckboxSetting(
		"Visual range",
		"Notify when players enter or leave your visual range.", false);

	private final CheckboxSetting notifyFriends = new CheckboxSetting(
		"Notify friends", "Also notify when friends enter or leave.", false);

	private final CheckboxSetting popCounter = new CheckboxSetting(
		"Pop counter", "Reports how many totems a player popped when they die.",
		true);

	private final CheckboxSetting deathCoords = new CheckboxSetting(
		"Death coords", "Records your death coordinates in chat.", true);

	private final CheckboxSetting serverLag = new CheckboxSetting(
		"Server lag", "Shows a server not responding warning on screen.", true);

	private final CheckboxSetting lagBack = new CheckboxSetting("Lagback",
		"Shows a lagback countdown on screen.", true);

	private final CheckboxSetting potion = new CheckboxSetting("Potion",
		"Shows potion effect durations on screen.", true);

	private final CheckboxSetting resistanceLevelCheck = new CheckboxSetting(
		"Resistance level check",
		"Only show resistance when its amplifier is above 0.", true);

	private final SliderSetting textScale = new SliderSetting("Text scale",
		"Scale of the HUD warning and potion text.", 1.5, 0.5, 5, 0.1,
		ValueDisplay.DECIMAL);

	private final ColorSetting warningColor = new ColorSetting(
		"Warning color", "Color of the server lag and lagback warnings.",
		new Color(190, 0, 0));

	private final ColorSetting potionBaseColor = new ColorSetting(
		"Potion color", "Color of the potion duration text.", Color.WHITE);

	private final CheckboxSetting potionShadow = new CheckboxSetting(
		"Potion shadow", "Draw a shadow behind the potion text.", true);

	private final SliderSetting warningX = new SliderSetting("Warning X",
		"X position of the warnings. -1 centers them.", -1, -1, 1920, 1,
		ValueDisplay.INTEGER);

	private final SliderSetting warningY = new SliderSetting("Warning Y",
		"Y position of the warnings.", 19, 0, 1080, 1, ValueDisplay.INTEGER);

	private final SliderSetting potionX = new SliderSetting("Potion X",
		"X position of the potion text. -1 centers it.", -1, -1, 1920, 1,
		ValueDisplay.INTEGER);

	private final SliderSetting potionY = new SliderSetting("Potion Y",
		"Y position of the potion text. -1 uses the screen center.", -1, -1,
		1080, 1, ValueDisplay.INTEGER);

	private final SliderSetting yOffset = new SliderSetting("Y offset",
		"Fine-tunes the potion text position.", 0, -200, 200, 1,
		ValueDisplay.INTEGER);

	private final DecimalFormat df = new DecimalFormat("0.0");
	private final ConcurrentLinkedQueue<Packet<?>> incoming =
		new ConcurrentLinkedQueue<>();
	private final Map<UUID, Integer> totemPops = new HashMap<>();
	private final Map<UUID, Player> knownPlayers = new HashMap<>();
	private final Set<UUID> deadPlayers = new HashSet<>();

	private volatile long lastPacketTime = System.currentTimeMillis();
	private long lagBackTime = -1;
	private ClientLevel lastLevel;

	public ImgTipsHack()
	{
		super("ImgTips");
		setCategory(Category.CHAT);
		addSetting(visualRange);
		addSetting(notifyFriends);
		addSetting(popCounter);
		addSetting(deathCoords);
		addSetting(serverLag);
		addSetting(lagBack);
		addSetting(potion);
		addSetting(resistanceLevelCheck);
		addSetting(textScale);
		addSetting(warningColor);
		addSetting(potionBaseColor);
		addSetting(potionShadow);
		addSetting(warningX);
		addSetting(warningY);
		addSetting(potionX);
		addSetting(potionY);
		addSetting(yOffset);

		notifyFriends.visibleWhen(visualRange::isChecked);
		resistanceLevelCheck.visibleWhen(potion::isChecked);
		potionShadow.visibleWhen(potion::isChecked);
	}

	@Override
	protected void onEnable()
	{
		EVENTS.add(UpdateListener.class, this);
		EVENTS.add(PacketInputListener.class, this);
		EVENTS.add(GUIRenderListener.class, this);
		EVENTS.add(DeathListener.class, this);
		resetState();
	}

	@Override
	protected void onDisable()
	{
		EVENTS.remove(UpdateListener.class, this);
		EVENTS.remove(PacketInputListener.class, this);
		EVENTS.remove(GUIRenderListener.class, this);
		EVENTS.remove(DeathListener.class, this);
		incoming.clear();
		totemPops.clear();
		knownPlayers.clear();
		deadPlayers.clear();
		lastLevel = null;
	}

	private void resetState()
	{
		incoming.clear();
		totemPops.clear();
		knownPlayers.clear();
		deadPlayers.clear();
		lastLevel = MC.level;
		lastPacketTime = System.currentTimeMillis();
		lagBackTime = -1;
		captureKnownPlayers();
	}

	@Override
	public void onReceivedPacket(PacketInputEvent event)
	{
		lastPacketTime = System.currentTimeMillis();

		Packet<?> packet = event.getPacket();
		if(packet instanceof ClientboundEntityEventPacket
			|| packet instanceof ClientboundPlayerPositionPacket)
			incoming.add(packet);
	}

	@Override
	public void onUpdate()
	{
		if(MC.player == null || MC.level == null)
			return;

		if(MC.level != lastLevel)
		{
			lastLevel = MC.level;
			totemPops.clear();
			deadPlayers.clear();
			knownPlayers.clear();
			captureKnownPlayers();
			lastPacketTime = System.currentTimeMillis();
			lagBackTime = -1;
		}

		processPackets();
		checkDeaths();
		updateVisualRange();
	}

	private void processPackets()
	{
		Packet<?> packet;
		while((packet = incoming.poll()) != null)
		{
			if(packet instanceof ClientboundPlayerPositionPacket)
			{
				lagBackTime = System.currentTimeMillis();
				continue;
			}

			handleEntityEvent((ClientboundEntityEventPacket)packet);
		}
	}

	private void handleEntityEvent(ClientboundEntityEventPacket event)
	{
		if(!popCounter.isChecked() || MC.level == null
			|| event.getEventId() != EntityEvent.PROTECTED_FROM_DEATH)
			return;

		Entity entity = event.getEntity(MC.level);
		if(!(entity instanceof Player player))
			return;

		int pops = totemPops.merge(player.getUUID(), 1, Integer::sum);
		String suffix =
			" " + pops + (pops == 1 ? " totem." : " totems.");

		if(player == MC.player)
			ChatUtils.message("You popped" + suffix);
		else
			ChatUtils.message(player.getName().getString() + " has popped"
				+ suffix);
	}

	private void checkDeaths()
	{
		for(AbstractClientPlayer player : MC.level.players())
		{
			UUID uuid = player.getUUID();
			boolean dead = player.isDeadOrDying() || player.getHealth() <= 0
				|| player.deathTime > 0;

			if(dead)
			{
				if(deadPlayers.add(uuid))
					reportDeath(player);
			}else
				deadPlayers.remove(uuid);
		}
	}

	private void reportDeath(Player player)
	{
		if(!popCounter.isChecked())
			return;

		int pops = totemPops.getOrDefault(player.getUUID(), 0);
		String name = player.getName().getString();

		if(pops > 0)
		{
			String suffix = " " + pops
				+ (pops == 1 ? " totem." : " totems.");
			if(player == MC.player)
				ChatUtils.message("You died after popping" + suffix);
			else
				ChatUtils.message(name + " died after popping" + suffix);
		}else if(player == MC.player)
			ChatUtils.message("You died.");
		else
			ChatUtils.message(name + " died.");

		totemPops.remove(player.getUUID());
	}

	private void updateVisualRange()
	{
		Map<UUID, Player> current = new HashMap<>();
		for(AbstractClientPlayer player : MC.level.players())
			if(player != MC.player)
				current.put(player.getUUID(), player);

		if(visualRange.isChecked())
		{
			for(Map.Entry<UUID, Player> entry : current.entrySet())
			{
				if(knownPlayers.containsKey(entry.getKey()))
					continue;
				if(!notifyFriends.isChecked()
					&& WURST.getFriends().isFriend(entry.getValue()))
					continue;

				ChatUtils.message(entry.getValue().getName().getString()
					+ " entered your visual range.");
				playPing();
			}

			for(Map.Entry<UUID, Player> entry : knownPlayers.entrySet())
			{
				if(current.containsKey(entry.getKey()))
					continue;
				if(!notifyFriends.isChecked()
					&& WURST.getFriends().isFriend(entry.getValue()))
					continue;

				ChatUtils.message(entry.getValue().getName().getString()
					+ " left your visual range.");
				playPing();
			}
		}

		knownPlayers.clear();
		knownPlayers.putAll(current);
	}

	private void captureKnownPlayers()
	{
		knownPlayers.clear();
		if(MC.level == null || MC.player == null)
			return;

		for(AbstractClientPlayer player : MC.level.players())
			if(player != MC.player)
				knownPlayers.put(player.getUUID(), player);
	}

	private void playPing()
	{
		MC.level.playLocalSound(MC.player.getX(), MC.player.getY(),
			MC.player.getZ(), SoundEvents.EXPERIENCE_ORB_PICKUP,
			SoundSource.PLAYERS, 0.5F, 1.9F, false);
	}

	@Override
	public void onDeath()
	{
		if(!deathCoords.isChecked() || MC.player == null)
			return;

		ChatUtils.message("You died at " + MC.player.getBlockX() + ", "
			+ MC.player.getBlockY() + ", " + MC.player.getBlockZ());
	}

	@Override
	public void onRenderGUI(GuiGraphics context, float partialTicks)
	{
		if(MC.player == null)
			return;

		double scale = textScale.getValue();
		long now = System.currentTimeMillis();
		boolean lagWarning =
			serverLag.isChecked() && now - lastPacketTime > 1400;
		boolean lagBackWarning = lagBack.isChecked() && lagBackTime >= 0
			&& now - lagBackTime < 1500;

		double y = warningY.getValue();
		if(lagWarning)
		{
			String line = "Server not responding ("
				+ df.format((now - lastPacketTime) / 1000.0) + "s)";
			drawString(context, line, warningX.getValue(), y,
				warningColor.getColor().getRGB(), scale, true, true);
			y += getTextHeight(scale) + 2;
		}

		if(lagBackWarning)
		{
			String line = "Lagback ("
				+ df.format((1500L - (now - lagBackTime)) / 1000.0) + "s)";
			drawString(context, line, warningX.getValue(),
				lagWarning ? y : warningY.getValue(),
				warningColor.getColor().getRGB(), scale, true, true);
		}

		if(!potion.isChecked())
			return;

		String text = buildPotionString();
		if(text.isEmpty())
			return;

		double x = potionX.getValue();
		if(x < 0)
			x = (context.guiWidth() - MC.font.width(text) * scale) / 2.0;

		double py = potionY.getValue();
		if(py < 0)
			py = context.guiHeight() / 2.0 + 9;
		py -= yOffset.getValue();

		drawString(context, text, x, py, potionBaseColor.getColor().getRGB(),
			scale, potionShadow.isChecked(), false);
	}

	private String buildPotionString()
	{
		StringBuilder sb = new StringBuilder();

		MobEffectInstance resistance =
			MC.player.getEffect(MobEffects.DAMAGE_RESISTANCE);
		if(resistance != null && (!resistanceLevelCheck.isChecked()
			|| resistance.getAmplifier() > 0))
			appendEffect(sb, "Resistance", resistance);

		MobEffectInstance strength = MC.player.getEffect(MobEffects.DAMAGE_BOOST);
		appendEffect(sb, "Strength", strength);

		MobEffectInstance speed = MC.player.getEffect(MobEffects.MOVEMENT_SPEED);
		appendEffect(sb, "Speed", speed);

		return sb.toString();
	}

	private void appendEffect(StringBuilder sb, String name,
		MobEffectInstance effect)
	{
		if(effect == null)
			return;
		if(!sb.isEmpty())
			sb.append("  ");
		sb.append(name).append(' ').append(effect.getDuration() / 20 + 1)
			.append('s');
	}

	private void drawString(GuiGraphics context, String text,
		double settingX, double settingY, int color, double scale,
		boolean shadow, boolean centerWhenAuto)
	{
		double x = settingX;
		if(x < 0 && centerWhenAuto)
			x = (context.guiWidth() - MC.font.width(text) * scale) / 2.0;
		else if(x < 0)
			x = 0;

		var pose = context.pose();
		pose.pushPose();
		pose.translate((float)x, (float)settingY, 0);
		pose.scale((float)scale, (float)scale, 1);
		context.drawString(MC.font, text, 0, 0, color, shadow);
		pose.popPose();
	}

	private double getTextHeight(double scale)
	{
		return (MC.font.lineHeight + 1) * scale;
	}
}
