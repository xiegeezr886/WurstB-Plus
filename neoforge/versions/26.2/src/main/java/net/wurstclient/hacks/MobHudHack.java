/*
 * Copyright (c) 2014-2026 Wurst-Imperium and contributors.
 *
 * This source code is subject to the terms of the GNU Affero General Public
 * License, version 3. If a copy of the AGPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/agpl-3.0.txt
 *
 * Ported from InvincibleMachineGun (GPL-3.0).
 */
package net.wurstclient.hacks;

import java.awt.Color;
import java.util.Locale;

import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.NeutralMob;
import net.minecraft.world.entity.monster.EnderMan;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import net.wurstclient.Category;
import net.wurstclient.SearchTags;
import net.wurstclient.events.ChatOutputListener;
import net.wurstclient.events.GUIRenderListener;
import net.wurstclient.events.UpdateListener;
import net.wurstclient.hack.Hack;
import net.wurstclient.settings.CheckboxSetting;
import net.wurstclient.settings.ColorSetting;
import net.wurstclient.settings.SliderSetting;
import net.wurstclient.settings.SliderSetting.ValueDisplay;
import net.wurstclient.settings.TextFieldSetting;
import net.minecraft.client.gui.GuiGraphicsExtractor;

@SearchTags({"mob hud", "smart hud", "weather hud", "speed hud", "jarvis"})
public final class MobHudHack extends Hack
	implements UpdateListener, GUIRenderListener, ChatOutputListener
{
	private static final int LEFT_MARGIN = 5;
	private static final int MOB_EXTRA_SHIFT = 3;
	private static final int BOX_PADDING = 4;
	private static final int BOX_HEIGHT = 12;
	private static final int BLACK_FRAME = 0xFF000000;
	private static final float SHADOW_FACTOR = 0.5F;

	private final ColorSetting hudColor =
		new ColorSetting("HUD color", new Color(255, 255, 255));

	private final TextFieldSetting aiName =
		new TextFieldSetting("AI name", "Jarvis");

	private final CheckboxSetting textBoxEnabled =
		new CheckboxSetting("Text box", true);

	private final SliderSetting entityRange = new SliderSetting(
		"Entity range", "Maximum distance for the looked-at entity info.", 40,
		5, 100, 1, ValueDisplay.INTEGER.withSuffix(" blocks"));

	private final CheckboxSetting showWeather =
		new CheckboxSetting("Weather and temperature", true);

	private final CheckboxSetting showEntityInfo =
		new CheckboxSetting("Looked-at entity", true);

	private final CheckboxSetting showSpeed =
		new CheckboxSetting("Speed", true);

	private final CheckboxSetting showAltitude =
		new CheckboxSetting("Altitude and depth", true);

	private final CheckboxSetting showVitals =
		new CheckboxSetting("Vitals warnings", true);

	private final CheckboxSetting showNavigation =
		new CheckboxSetting("Navigation", true);

	private final CheckboxSetting showToggleMessage =
		new CheckboxSetting("Toggle messages", true);

	private int tickCounter;
	private double lastX;
	private double lastZ;
	private double blocksPerSecond;

	private int targetX = Integer.MIN_VALUE;
	private int targetZ = Integer.MIN_VALUE;

	private String toggleMessage = "";
	private long toggleMessageUntil;
	private boolean pendingDisable;

	public MobHudHack()
	{
		super("MobHud");
		setCategory(Category.RENDER);
		addSetting(hudColor);
		addSetting(aiName);
		addSetting(textBoxEnabled);
		addSetting(entityRange);
		addSetting(showWeather);
		addSetting(showEntityInfo);
		addSetting(showSpeed);
		addSetting(showAltitude);
		addSetting(showVitals);
		addSetting(showNavigation);
		addSetting(showToggleMessage);
	}

	@Override
	protected void onEnable()
	{
		tickCounter = 0;
		targetX = Integer.MIN_VALUE;
		targetZ = Integer.MIN_VALUE;
		pendingDisable = false;
		toggleMessage = "";
		toggleMessageUntil = 0;

		if(MC.player != null)
		{
			lastX = MC.player.getX();
			lastZ = MC.player.getZ();
		}

		EVENTS.add(UpdateListener.class, this);
		EVENTS.add(GUIRenderListener.class, this);
		EVENTS.add(ChatOutputListener.class, this);
	}

	@Override
	protected void onDisable()
	{
		EVENTS.remove(UpdateListener.class, this);
		EVENTS.remove(GUIRenderListener.class, this);
		EVENTS.remove(ChatOutputListener.class, this);
	}

	@Override
	public void onUpdate()
	{
		LocalPlayer player = MC.player;
		if(player == null || MC.level == null)
			return;

		tickCounter++;
		if(tickCounter >= 20)
		{
			double dx = player.getX() - lastX;
			double dz = player.getZ() - lastZ;
			blocksPerSecond = Math.sqrt(dx * dx + dz * dz);
			lastX = player.getX();
			lastZ = player.getZ();
			tickCounter = 0;
		}
	}

	@Override
	public void onSentMessage(ChatOutputEvent event)
	{
		String raw = event.getMessage();
		if(raw == null)
			return;

		String msg = raw.trim().toLowerCase(Locale.ROOT);
		String ai = aiName.getValue().toLowerCase(Locale.ROOT);

		if(msg.equals(ai + " enable smart hud"))
		{
			if(!isEnabled())
				setEnabled(true);
			showMessage(capitalize(aiName.getValue())
				+ ": Smart HUD Enabled Sir");
			event.cancel();
			return;
		}
		if(msg.equals(ai + " disable smart hud"))
		{
			pendingDisable = true;
			showMessage(capitalize(aiName.getValue())
				+ ": Smart HUD Disabled Sir");
			event.cancel();
			return;
		}
		if(msg.startsWith("ai name "))
		{
			String newName = raw.substring("ai name ".length()).trim();
			if(newName.isEmpty())
				showMessage("Invalid AI name");
			else
			{
				aiName.setValue(newName);
				showMessage("AI name set to " + capitalize(newName));
			}
			event.cancel();
			return;
		}
		if(msg.startsWith("smart hud color "))
		{
			String colorName = raw.substring("smart hud color ".length())
				.trim();
			Integer parsed = parseNamedColor(colorName);
			if(parsed != null)
			{
				hudColor.setColor(new Color(parsed));
				showMessage(capitalize(aiName.getValue())
					+ ": Smart HUD color set to " + colorName);
			}else
				showMessage(capitalize(aiName.getValue())
					+ ": Unknown color: " + colorName);
			event.cancel();
			return;
		}
		if(msg.equals(ai + " disable text box"))
		{
			textBoxEnabled.setChecked(false);
			showMessage(capitalize(aiName.getValue())
				+ ": Text Box Disabled Sir!");
			event.cancel();
			return;
		}
		if(msg.equals(ai + " enable text box"))
		{
			textBoxEnabled.setChecked(true);
			showMessage(capitalize(aiName.getValue())
				+ ": Text Box Enabled Sir!");
			event.cancel();
			return;
		}
		if(msg.equals(ai + " where am i"))
		{
			LocalPlayer player = MC.player;
			if(player != null)
				player.sendSystemMessage(Component.literal(
					capitalize(aiName.getValue()) + ": You are at "
						+ (int)player.getX() + ", " + (int)player.getY() + ", "
						+ (int)player.getZ() + " coordinates Sir"));
			event.cancel();
			return;
		}
		if(msg.startsWith(ai + " get me to "))
		{
			String coords =
				msg.substring((ai + " get me to ").length()).trim();
			String[] parts = coords.split("\\s+");
			if(parts.length == 2)
			{
				try
				{
					targetX = Integer.parseInt(parts[0]);
					targetZ = Integer.parseInt(parts[1]);
					showMessage(capitalize(aiName.getValue())
						+ ": Navigation set to " + targetX + ", " + targetZ
						+ " Sir");
				}catch(NumberFormatException e)
				{
					showMessage(capitalize(aiName.getValue())
						+ ": Invalid coordinates Sir");
				}
			}else
				showMessage(capitalize(aiName.getValue())
					+ ": Use format: get me to x z Sir");
			event.cancel();
			return;
		}
		if(msg.equals(ai + " cancel trip"))
		{
			targetX = Integer.MIN_VALUE;
			targetZ = Integer.MIN_VALUE;
			showMessage(capitalize(aiName.getValue())
				+ ": Navigation canceled Sir");
			event.cancel();
		}
	}

	@Override
	public void onRenderGUI(GuiGraphicsExtractor context, float partialTicks)
	{
		LocalPlayer player = MC.player;
		if(player == null || MC.level == null)
			return;

		int screenWidth = context.guiWidth();
		int screenHeight = context.guiHeight();
		long now = System.currentTimeMillis();

		if(pendingDisable && now >= toggleMessageUntil)
		{
			pendingDisable = false;
			MC.execute(() -> setEnabled(false));
			return;
		}

		int argbColor = hudColor.getColor().getRGB();

		if(showToggleMessage.isChecked() && now < toggleMessageUntil
			&& !toggleMessage.isEmpty())
		{
			int msgX = (screenWidth - MC.font.width(toggleMessage)) / 2;
			drawTextWithBox(context, toggleMessage, msgX, screenHeight - 62,
				argbColor);
		}

		if(showWeather.isChecked())
		{
			String weatherText;
			if(MC.level.isThundering())
				weatherText = "Weather: Thunder";
			else if(MC.level.isRaining())
				weatherText = "Weather: Rain";
			else
				weatherText = "Weather: Clear";

			int weatherX = LEFT_MARGIN + MC.font.width("We");
			int weatherY = screenHeight - 15;
			drawTextWithBox(context, weatherText, weatherX, weatherY,
				argbColor);

			int tempC = computeTemperatureC(player);
			String tempStr = tempC + " \u00b0C";
			int tempX = weatherX + MC.font.width(weatherText) + 6;
			drawTextWithBox(context, tempStr, tempX, weatherY, argbColor);
		}

		if(showEntityInfo.isChecked())
			renderEntityInfo(context, player, screenHeight, argbColor);

		if(showSpeed.isChecked())
		{
			String speed = "Speed: "
				+ String.format(Locale.ROOT, "%.2f", blocksPerSecond) + " b/s";
			int speedX = (screenWidth - MC.font.width(speed)) / 2;
			drawTextWithBox(context, speed, speedX, LEFT_MARGIN, argbColor);
		}

		if(showAltitude.isChecked())
		{
			BlockPos pos = player.blockPosition();
			int groundY = MC.level.getHeight(Heightmap.Types.MOTION_BLOCKING,
				pos.getX(), pos.getZ());
			int diff = (int)(player.getY() - groundY);

			if(player.isUnderWater())
			{
				int surfaceY = MC.level.getHeight(
					Heightmap.Types.WORLD_SURFACE, pos.getX(), pos.getZ());
				int depth = (int)(player.getEyeY() - surfaceY);
				String depthStr = "Depth: " + (-Math.abs(depth));
				int depthX = (screenWidth - MC.font.width(depthStr)) / 2;
				drawTextWithBox(context, depthStr, depthX, 20, argbColor);
			}else if(diff > LEFT_MARGIN)
			{
				String altitude = "Altitude: " + diff;
				int altX = (screenWidth - MC.font.width(altitude)) / 2;
				drawTextWithBox(context, altitude, altX, 20, argbColor);
			}
		}

		if(showVitals.isChecked())
		{
			if(player.getHealth() < 10)
			{
				int pulse = (int)((now / 500) % 2);
				int lowPulse = pulse == 0 ? 0xFFFFDD44 : 0xFFFF0000;
				int vx = (screenWidth - MC.font.width("!! Vitals Low !!")) / 2;
				drawTextWithBox(context, "!! Vitals Low !!", vx,
					screenHeight - 70, lowPulse);
			}
			if(player.getAirSupply() < 120)
			{
				int pulse = (int)((now / 500) % 2);
				int oxygenColor = pulse == 0 ? 0xFFFFDD44 : 0xFFFF6600;
				int oxX = (screenWidth - MC.font.width("!! Oxygen !!")) / 2;
				drawTextWithBox(context, "!! Oxygen !!", oxX, screenHeight - 55,
					oxygenColor);
			}
		}

		if(showNavigation.isChecked() && targetX != Integer.MIN_VALUE
			&& targetZ != Integer.MIN_VALUE)
			renderNavigation(context, player, screenWidth, argbColor);
	}

	private void renderEntityInfo(GuiGraphicsExtractor context,
		LocalPlayer player, int screenHeight, int argbColor)
	{
		Entity lookedAt = getLookedAtEntity(entityRange.getValue());
		if(lookedAt == null)
			return;

		double dist = player.distanceTo(lookedAt);
		String mobName = lookedAt.getName().getString();
		String mobDist = "Distance: " + (int)dist + " Blocks Away";

		int weatherShift = MC.font.width("We");
		int disShift = MC.font.width("Dis");
		int mobX = LEFT_MARGIN + weatherShift + MOB_EXTRA_SHIFT + disShift;
		int baseY = screenHeight / 2;

		drawTextWithBox(context, mobName, mobX, baseY, argbColor);
		drawTextWithBox(context, mobDist, mobX, baseY + 10, argbColor);

		int danger = getDangerState(lookedAt, player);
		if(danger == 2)
		{
			int pulse = (int)((System.currentTimeMillis() / 500) % 2);
			int color = pulse == 0 ? 0xFFFF8C00 : 0xFFFF0000;
			drawTextWithBox(context, "Danger!", mobX, baseY + 20, color);
		}else if(danger == 1)
		{
			int pulse = (int)((System.currentTimeMillis() / 600) % 2);
			int color = pulse == 0 ? 0xFFFFA500 : 0xFFFF7F50;
			drawTextWithBox(context, "Possible Danger!", mobX, baseY + 20,
				color);
		}
	}

	private void renderNavigation(GuiGraphicsExtractor context,
		LocalPlayer player, int screenWidth, int argbColor)
	{
		double dx = targetX - player.getX();
		double dz = targetZ - player.getZ();
		double distance = Math.sqrt(dx * dx + dz * dz);

		if(distance <= 1)
		{
			targetX = Integer.MIN_VALUE;
			targetZ = Integer.MIN_VALUE;
			showMessage(
				capitalize(aiName.getValue()) + ": Target reached Sir!");
			return;
		}

		Vec3 lookVec = player.getLookAngle();
		double lookAngle = Math.toDegrees(Math.atan2(lookVec.z, lookVec.x));
		double targetAngle = Math.toDegrees(Math.atan2(dz, dx));
		double angleDiff = (targetAngle - lookAngle + 360.0) % 360.0;
		String direction;
		if(angleDiff < 45 || angleDiff >= 315)
			direction = "<Forward>";
		else if(angleDiff < 135)
			direction = "<Right>";
		else if(angleDiff < 225)
			direction = "<Turn Around>";
		else
			direction = "<Left>";

		int dirX = (screenWidth - MC.font.width(direction)) / 2;
		drawTextWithBox(context, direction, dirX, 35, argbColor);
		String distText = "Distance: " + (int)distance;
		int distX = (screenWidth - MC.font.width(distText)) / 2;
		drawTextWithBox(context, distText, distX, 50, argbColor);
	}

	private void drawTextWithBox(GuiGraphicsExtractor context, String text,
		int x, int y, int color)
	{
		if(!textBoxEnabled.isChecked())
		{
			context.text(MC.font, text, x, y, color, true);
			return;
		}

		int textWidth = MC.font.width(text);
		int boxWidth = textWidth + 8;
		int boxX = x - BOX_PADDING;
		int boxY = y - 1;
		int r = (int)(((color >> 16) & 255) * SHADOW_FACTOR);
		int g = (int)(((color >> 8) & 255) * SHADOW_FACTOR);
		int b = (int)((color & 255) * SHADOW_FACTOR);
		int boxColor = 0x80000000 | r << 16 | g << 8 | b;

		context.fill(boxX, boxY + 1, boxX + boxWidth, boxY + BOX_HEIGHT - 1,
			boxColor);
		context.fill(boxX, boxY, boxX + boxWidth, boxY + 1, BLACK_FRAME);
		context.fill(boxX, boxY + BOX_HEIGHT - 1, boxX + boxWidth,
			boxY + BOX_HEIGHT, BLACK_FRAME);
		context.fill(boxX, boxY, boxX + 1, boxY + BOX_HEIGHT, BLACK_FRAME);
		context.fill(boxX + boxWidth - 1, boxY, boxX + boxWidth,
			boxY + BOX_HEIGHT, BLACK_FRAME);
		context.text(MC.font, text, x, y, color, true);
	}

	private void showMessage(String message)
	{
		toggleMessage = message;
		toggleMessageUntil = System.currentTimeMillis() + 3000;
	}

	private int computeTemperatureC(LocalPlayer player)
	{
		Biome biome = MC.level.getBiome(player.blockPosition()).value();
		float base = biome.getBaseTemperature();
		int temp = Math.round((base - 0.5F) * 20.0F);
		long t = MC.level.getOverworldClockTime() % 24000;
		boolean isNight = t >= 13000 && t <= 23000;
		if(isNight)
			temp -= 2;
		if(MC.level.isThundering())
			temp -= 2;
		else if(MC.level.isRaining())
			temp--;
		if(player.getY() > 120)
			temp -= 3;
		if(player.isUnderWater() && base <= 0.15F)
			return temp - 3;
		return temp;
	}

	private Entity getLookedAtEntity(double range)
	{
		if(MC.player == null || MC.level == null)
			return null;

		Vec3 eyePos = MC.player.getEyePosition();
		Vec3 reachVec = eyePos.add(MC.player.getLookAngle().scale(range));
		Entity closest = null;
		double closestDist = Double.MAX_VALUE;

		for(Entity entity : MC.level.entitiesForRendering())
		{
			if(entity == MC.player || !entity.isAttackable())
				continue;

			if(entity.getBoundingBox().inflate(0.3).clip(eyePos, reachVec)
				.isPresent())
			{
				double d = entity.distanceToSqr(eyePos);
				if(d < closestDist)
				{
					closestDist = d;
					closest = entity;
				}
			}
		}
		return closest;
	}

	private int getDangerState(Entity entity, LocalPlayer player)
	{
		if(!(entity instanceof Mob mob))
			return 0;

		boolean hostile = mob.getType().getCategory() == MobCategory.MONSTER
			&& !(mob instanceof NeutralMob);
		boolean neutral = mob instanceof NeutralMob;
		boolean angry = mob.isAggressive() || mob.getTarget() == player
			|| mob.getLastHurtByMob() == player;

		if(mob instanceof EnderMan enderman)
			angry |= enderman.isCreepy();

		if(angry || hostile)
			return 2;
		if(neutral)
			return 1;
		return 0;
	}

	private String capitalize(String str)
	{
		return str == null || str.isEmpty() ? str
			: str.substring(0, 1).toUpperCase(Locale.ROOT) + str.substring(1);
	}

	private Integer parseNamedColor(String name)
	{
		String key = name.toLowerCase(Locale.ROOT);
		switch(key)
		{
			case "white":
				return 0xFFFFFF;
			case "black":
				return 0;
			case "red":
				return 0xFF3535;
			case "blue":
				return 0x5555FF;
			case "green":
				return 0x55FF55;
			case "yellow":
				return 0xFFFF55;
			case "aqua":
			case "cyan":
				return 0x55FFFF;
			case "pink":
				return 0xFF55FF;
			case "purple":
				return 0xAA00FF;
			case "magenta":
				return 0xFF00FF;
			case "orange":
				return 0xFF8800;
			case "gray":
			case "grey":
				return 0xAAAAAA;
			case "lightgray":
			case "lightgrey":
				return 0xCCCCCC;
			case "lime":
				return 0xAAFF00;
			case "teal":
				return 0x008080;
			case "gold":
				return 0xFFD700;
			default:
				if(key.startsWith("#") && key.length() == 7)
					try
					{
						return Integer.parseInt(key.substring(1), 16);
					}catch(NumberFormatException e)
					{
						return null;
					}
				return null;
		}
	}
}
