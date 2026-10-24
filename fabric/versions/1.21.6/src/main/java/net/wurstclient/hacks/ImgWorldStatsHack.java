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
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Random;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ServerboundClientCommandPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.stats.Stats;
import net.minecraft.world.level.block.Block;
import net.wurstclient.Category;
import net.wurstclient.SearchTags;
import net.wurstclient.events.GUIRenderListener;
import net.wurstclient.events.UpdateListener;
import net.wurstclient.events.WorldChangeListener;
import net.wurstclient.hack.Hack;
import net.wurstclient.settings.CheckboxSetting;
import net.wurstclient.settings.ColorSetting;
import net.wurstclient.settings.EnumSetting;
import net.wurstclient.settings.SliderSetting;
import net.wurstclient.settings.SliderSetting.ValueDisplay;
import net.wurstclient.util.render.GuiGraphicsExtractor;

/**
 * Renders a configurable world information panel: in-game time, days, biome,
 * weather, coordinates, playtime, travelled distance, kills, deaths and mined
 * blocks, with several visual themes.
 */
@SearchTags({"img world stats", "world stats", "world info", "statistics",
	"playtime", "coordinates"})
public final class ImgWorldStatsHack extends Hack
	implements UpdateListener, GUIRenderListener, WorldChangeListener
{
	private final CheckboxSetting showTicks = new CheckboxSetting(
		"Show time", "Shows the current in-game time.", true);

	private final CheckboxSetting showDays = new CheckboxSetting("Show days",
		"Shows how many in-game days you have survived.", true);

	private final CheckboxSetting showBiome = new CheckboxSetting(
		"Show biome", "Shows the current biome name.", true);

	private final CheckboxSetting showWeather = new CheckboxSetting(
		"Show weather", "Shows the current weather.", true);

	private final CheckboxSetting showCoords = new CheckboxSetting(
		"Show coordinates", "Shows overworld and nether coordinates.", true);

	private final CheckboxSetting showPlaytime = new CheckboxSetting(
		"Show playtime", "Shows the total playtime of this world.", true);

	private final CheckboxSetting showDistance = new CheckboxSetting(
		"Show distance", "Shows the total distance travelled.", true);

	private final CheckboxSetting showKills = new CheckboxSetting(
		"Show kills", "Shows player and mob kill counts.", true);

	private final CheckboxSetting showDeaths = new CheckboxSetting(
		"Show deaths", "Shows the death count.", true);

	private final CheckboxSetting showBreaks = new CheckboxSetting(
		"Show mined blocks", "Shows the total amount of mined blocks.", true);

	private final EnumSetting<Theme> theme = new EnumSetting<>("Theme",
		"Visual style of the stats panel.", Theme.values(), Theme.SIMPLE);

	private final SliderSetting textX = new SliderSetting("Text X",
		"X position of the text.", 15, 0, 2000, 1, ValueDisplay.INTEGER);

	private final SliderSetting textY = new SliderSetting("Text Y",
		"Y position of the text.", 15, 0, 2000, 1, ValueDisplay.INTEGER);

	private final SliderSetting textScale = new SliderSetting("Text scale",
		"Scale of the stats text.", 1, 0.1, 3, 0.1, ValueDisplay.DECIMAL);

	private final ColorSetting textColor = new ColorSetting("Text color",
		"Color of the stats text for themes that don't override it.",
		Color.WHITE);

	private final CheckboxSetting textShadow = new CheckboxSetting(
		"Text shadow", "Draw a shadow behind the stats text.", true);

	private final SliderSetting bgX = new SliderSetting("Background X",
		"X position of the background panel.", 10, 0, 2000, 1,
		ValueDisplay.INTEGER);

	private final SliderSetting bgY = new SliderSetting("Background Y",
		"Y position of the background panel.", 10, 0, 2000, 1,
		ValueDisplay.INTEGER);

	private final SliderSetting bgWidth = new SliderSetting(
		"Background width", "Width of the background panel.", 150, 10, 1000,
		1, ValueDisplay.INTEGER);

	private final SliderSetting bgHeight = new SliderSetting(
		"Background height", "Height of the background panel.", 180, 10,
		1000, 1, ValueDisplay.INTEGER);

	private final List<Particle> particles = new ArrayList<>();
	private final Random random = new Random();

	private int syncTimer;
	private int cachedBlocksMined;
	private ClientLevel lastLevel;

	public ImgWorldStatsHack()
	{
		super("ImgWorldStats");
		setCategory(Category.OTHER);
		addSetting(showTicks);
		addSetting(showDays);
		addSetting(showBiome);
		addSetting(showWeather);
		addSetting(showCoords);
		addSetting(showPlaytime);
		addSetting(showDistance);
		addSetting(showKills);
		addSetting(showDeaths);
		addSetting(showBreaks);
		addSetting(theme);
		addSetting(textX);
		addSetting(textY);
		addSetting(textScale);
		addSetting(textColor);
		addSetting(textShadow);
		addSetting(bgX);
		addSetting(bgY);
		addSetting(bgWidth);
		addSetting(bgHeight);
	}

	@Override
	protected void onEnable()
	{
		EVENTS.add(UpdateListener.class, this);
		EVENTS.add(GUIRenderListener.class, this);
		EVENTS.add(WorldChangeListener.class, this);
		syncTimer = 0;
		cachedBlocksMined = 0;
		lastLevel = MC.level;
		particles.clear();
		for(int i = 0; i < 40; i++)
			particles.add(new Particle(random.nextDouble() * 200,
				random.nextDouble() * 100));
		requestStats();
	}

	@Override
	protected void onDisable()
	{
		EVENTS.remove(UpdateListener.class, this);
		EVENTS.remove(GUIRenderListener.class, this);
		EVENTS.remove(WorldChangeListener.class, this);
		particles.clear();
		lastLevel = null;
	}

	@Override
	public void onWorldChange(ClientLevel world)
	{
		lastLevel = world;
		syncTimer = 0;
		cachedBlocksMined = 0;
		particles.clear();
		for(int i = 0; i < 40; i++)
			particles.add(new Particle(random.nextDouble() * 200,
				random.nextDouble() * 100));
		if(world != null)
			requestStats();
	}

	@Override
	public void onUpdate()
	{
		if(MC.player == null || MC.level == null)
			return;

		if(MC.level != lastLevel)
		{
			lastLevel = MC.level;
			syncTimer = 0;
		}

		if(++syncTimer >= 200)
		{
			requestStats();
			syncTimer = 0;
		}

		double speed = theme.getSelected() == Theme.BLACK_EMPIRE ? 2.5 : 0.8;
		for(Particle particle : particles)
		{
			particle.y += speed;
			if(particle.y > 1000)
				particle.y = 0;
		}
	}

	private void requestStats()
	{
		if(MC.player == null || MC.player.connection == null)
			return;

		MC.player.connection.send(new ServerboundClientCommandPacket(
			ServerboundClientCommandPacket.Action.REQUEST_STATS));
		updateCachedBlocksMined();
	}

	private void updateCachedBlocksMined()
	{
		if(MC.player == null)
			return;

		int total = 0;
		for(Block block : BuiltInRegistries.BLOCK)
			total += MC.player.getStats().getValue(Stats.BLOCK_MINED, block);
		cachedBlocksMined = total;
	}

	@Override
	public void onRenderGUI(GuiGraphicsExtractor context, float partialTicks)
	{
		if(MC.player == null || MC.level == null)
			return;

		List<String> lines = buildLines();
		if(lines.isEmpty())
			return;

		renderBackground(context);

		double scale = textScale.getValue();
		double y = textY.getValue();
		Theme current = theme.getSelected();
		int color = getThemeTextColor(current);

		for(String line : lines)
		{
			var pose = context.pose();
			pose.pushMatrix();
			pose.translate((float)textX.getValue(), (float)y);
			pose.scale((float)scale, (float)scale);
			context.text(MC.font, line, 0, 0, color, textShadow.isChecked());
			pose.popMatrix();
			y += (MC.font.lineHeight + 2) * scale;
		}
	}

	private List<String> buildLines()
	{
		List<String> lines = new ArrayList<>();

		if(showTicks.isChecked())
			lines.add("Time: " + MC.level.getDayTime() % 24000L);

		if(showDays.isChecked())
			lines.add("Day: " + MC.level.getDayTime() / 24000L);

		if(showBiome.isChecked())
			lines.add("Biome: " + getBiomeName());

		if(showWeather.isChecked())
			lines.add("Weather: " + getWeatherName());

		if(showCoords.isChecked())
		{
			double x = MC.player.getX();
			double y = MC.player.getY();
			double z = MC.player.getZ();
			boolean nether = MC.level.dimension().location().getPath()
				.contains("nether");

			lines.add(String.format(Locale.ROOT, "Overworld: %.1f, %.1f, %.1f",
				nether ? x * 8.0 : x, y, nether ? z * 8.0 : z));
			lines.add(String.format(Locale.ROOT, "Nether: %.1f, %.1f, %.1f",
				nether ? x : x / 8.0, y, nether ? z : z / 8.0));
		}

		if(showPlaytime.isChecked())
		{
			int ticks = stat(Stats.PLAY_TIME);
			int hours = ticks / 72000;
			int minutes = ticks % 72000 / 1200;
			lines.add(String.format(Locale.ROOT, "Playtime: %dh %dm", hours,
				minutes));
		}

		if(showDistance.isChecked())
		{
			long cm = stat(Stats.WALK_ONE_CM) + stat(Stats.SPRINT_ONE_CM)
				+ stat(Stats.FLY_ONE_CM);
			lines.add(String.format(Locale.ROOT, "Distance: %.1f km",
				cm / 100000.0));
		}

		if(showKills.isChecked())
			lines.add("Kills: players[" + stat(Stats.PLAYER_KILLS)
				+ "] mobs[" + stat(Stats.MOB_KILLS) + "]");

		if(showDeaths.isChecked())
			lines.add("Deaths: " + stat(Stats.DEATHS));

		if(showBreaks.isChecked())
			lines.add("Mined: " + cachedBlocksMined);

		return lines;
	}

	private String getBiomeName()
	{
		return MC.level.getBiome(MC.player.blockPosition()).unwrapKey()
			.map(key -> Component
				.translatable(key.location().toLanguageKey("biome")).getString())
			.orElse("Unknown");
	}

	private String getWeatherName()
	{
		if(MC.level.isThundering())
			return "Thunder";
		if(MC.level.isRaining())
			return "Rain";
		return "Clear";
	}

	private int stat(ResourceLocation id)
	{
		return MC.player.getStats().getValue(Stats.CUSTOM, id);
	}

	private void renderBackground(GuiGraphicsExtractor context)
	{
		int x = bgX.getValueI();
		int y = bgY.getValueI();
		int w = bgWidth.getValueI();
		int h = bgHeight.getValueI();
		Theme current = theme.getSelected();

		switch(current)
		{
			case SIMPLE -> context.fill(x, y, x + w, y + h,
				new Color(20, 20, 20, 160).getRGB());

			case APPLE -> context.fill(x, y, x + w, y + h,
				new Color(245, 245, 247, 210).getRGB());

			case BLACK_EMPIRE ->
			{
				context.fill(x, y, x + w, y + h,
					new Color(0, 15, 0, 230).getRGB());
				for(Particle particle : particles)
				{
					int px = (int)(particle.x % w);
					int py = (int)(particle.y % h);
					context.fill(x + px, y + py, x + px + 2, y + py + 5,
						new Color(0, 255, 70, 150).getRGB());
				}
			}

			case SNOW ->
			{
				context.fill(x, y, x + w, y + h,
					new Color(25, 30, 45, 180).getRGB());
				for(Particle particle : particles)
				{
					int px = (int)(particle.x % w);
					int py = (int)(particle.y % h);
					context.fill(x + px, y + py, x + px + 2, y + py + 2,
						new Color(255, 255, 255, 180).getRGB());
				}
			}

			case SYNTHWAVE -> context.fillGradient(x, y, x + w, y + h,
				new Color(45, 0, 70, 200).getRGB(),
				new Color(0, 180, 255, 200).getRGB());
		}
	}

	private int getThemeTextColor(Theme current)
	{
		return switch(current)
		{
			case APPLE -> new Color(25, 25, 25).getRGB();
			case BLACK_EMPIRE -> new Color(0, 255, 0).getRGB();
			case SYNTHWAVE -> new Color(255, 50, 200).getRGB();
			default -> textColor.getColor().getRGB();
		};
	}

	public enum Theme
	{
		SIMPLE("Simple"),
		APPLE("Apple"),
		BLACK_EMPIRE("Black Empire"),
		SNOW("Snow"),
		SYNTHWAVE("Synthwave");

		private final String name;

		Theme(String name)
		{
			this.name = name;
		}

		@Override
		public String toString()
		{
			return name;
		}
	}

	private static final class Particle
	{
		private double x;
		private double y;

		private Particle(double x, double y)
		{
			this.x = x;
			this.y = y;
		}
	}
}
