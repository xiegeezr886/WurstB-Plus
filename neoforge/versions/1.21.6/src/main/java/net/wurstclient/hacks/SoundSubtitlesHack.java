/*
 * Copyright (c) 2026 WurstB+ Plus contributors
 *
 * This source code is subject to the terms of the GNU Affero General Public
 * License, version 3. If a copy of the AGPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/agpl-3.0.txt
 */
// Ported from Soar Client v7.1.2 (LGPL-3.0).
package net.wurstclient.hacks;

import java.awt.Color;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;

import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.sounds.SoundEventListener;
import net.minecraft.client.sounds.WeighedSoundEvents;
import net.minecraft.world.phys.Vec3;
import net.wurstclient.Category;
import net.wurstclient.SearchTags;
import net.wurstclient.events.GUIRenderListener;
import net.wurstclient.hack.Hack;
import net.wurstclient.settings.CheckboxSetting;
import net.wurstclient.settings.ColorSetting;
import net.wurstclient.settings.SliderSetting;
import net.wurstclient.settings.SliderSetting.ValueDisplay;
import net.wurstclient.util.render.GuiGraphicsExtractor;

/**
 * Directional sound subtitle HUD.
 *
 * <p>Uses {@code SoundManager.addListener} through a reflective proxy so the
 * same class compiles against the 2-argument (1.20.2) and 3-argument
 * (1.20.3+) listener signatures.
 */
@SearchTags({"sound subtitles", "subtitles", "sound titles", "directional sound"})
public final class SoundSubtitlesHack extends Hack
	implements GUIRenderListener
{
	private static final int WIDTH = 200;
	private static final int ROW_HEIGHT = 12;

	private final SliderSetting max = new SliderSetting("Max",
		"Maximum number of subtitle rows.", 3, 1, 10, 1,
		ValueDisplay.INTEGER);

	private final SliderSetting duration = new SliderSetting("Duration",
		"How long each subtitle stays on screen.", 3, 1, 10, 1,
		ValueDisplay.INTEGER.withSuffix("s"));

	private final CheckboxSetting background = new CheckboxSetting(
		"Background", "Draws a background behind the subtitles.", true);

	private final ColorSetting color = new ColorSetting("Color",
		"Color of the subtitle text.", Color.WHITE);

	private final List<Subtitle> subtitles = new ArrayList<>();
	private SoundEventListener listener;

	public SoundSubtitlesHack()
	{
		super("SoundSubtitles");
		setCategory(Category.RENDER);
		addSetting(max);
		addSetting(duration);
		addSetting(background);
		addSetting(color);
	}

	@Override
	protected void onEnable()
	{
		listener = (SoundEventListener)Proxy.newProxyInstance(
			SoundEventListener.class.getClassLoader(),
			new Class<?>[]{SoundEventListener.class}, (proxy, method, args) ->
			{
				if("onPlaySound".equals(method.getName()) && args != null
					&& args.length > 0
					&& args[0] instanceof SoundInstance sound)
				{
					WeighedSoundEvents events = args.length > 1
						&& args[1] instanceof WeighedSoundEvents w ? w : null;
					handleSound(sound, events);
					return null;
				}

				switch(method.getName())
				{
					case "equals":
					return args != null && args.length == 1
						&& proxy == args[0];

					case "hashCode":
					return System.identityHashCode(proxy);

					case "toString":
					return "SoundSubtitlesHack listener";

					default:
					return null;
				}
			});

		MC.getSoundManager().addListener(listener);
		EVENTS.add(GUIRenderListener.class, this);
	}

	@Override
	protected void onDisable()
	{
		if(listener != null)
			MC.getSoundManager().removeListener(listener);
		listener = null;
		subtitles.clear();
		EVENTS.remove(GUIRenderListener.class, this);
	}

	private void handleSound(SoundInstance sound, WeighedSoundEvents events)
	{
		if(subtitles.size() >= max.getValueI())
			return;

		String text = null;
		if(events != null && events.getSubtitle() != null)
			text = events.getSubtitle().getString();
		if(text == null || text.isBlank())
		{
			String id = soundId(sound);
			int slash = Math.max(id.lastIndexOf('/'), id.lastIndexOf('.'));
			text = slash >= 0 ? id.substring(slash + 1) : id;
		}
		if(text == null || text.isBlank())
			return;

		long now = System.currentTimeMillis();
		for(Subtitle subtitle : subtitles)
			if(subtitle.text.equals(text))
			{
				subtitle.x = sound.getX();
				subtitle.y = sound.getY();
				subtitle.z = sound.getZ();
				subtitle.start = now;
				return;
			}

		subtitles.add(new Subtitle(text, sound.getX(), sound.getY(),
			sound.getZ(), now));
	}

	private String soundId(SoundInstance sound)
	{
		try
		{
			return String.valueOf(sound.getClass().getMethod("getIdentifier")
				.invoke(sound));
		}catch(Exception ignored)
		{}

		try
		{
			return String.valueOf(sound.getClass().getMethod("getLocation")
				.invoke(sound));
		}catch(Exception ignored)
		{}

		return "sound";
	}

	@Override
	public void onRenderGUI(GuiGraphicsExtractor context, float partialTicks)
	{
		if(MC.player == null || uiHidden())
			return;

		long now = System.currentTimeMillis();
		long life = (long)(duration.getValue() * 1000.0);
		subtitles.removeIf(subtitle -> now - subtitle.start > life);
		if(subtitles.isEmpty())
			return;

		int rows = Math.min(subtitles.size(), max.getValueI());
		int totalHeight = rows * ROW_HEIGHT;
		int width = MC.getWindow().getGuiScaledWidth();
		int height = MC.getWindow().getGuiScaledHeight();
		int left = width / 2 - WIDTH / 2;
		int top = height - 55 - totalHeight;

		if(background.isChecked())
			context.fill(left, top - 3, left + WIDTH, top + totalHeight + 3,
				0x90000000);

		Vec3 look = MC.player.getLookAngle();
		Vec3 right = look.cross(new Vec3(0, 1, 0));
		if(right.lengthSqr() > 1.0E-4)
			right = right.normalize();

		for(int i = 0; i < rows; i++)
		{
			Subtitle subtitle = subtitles.get(i);
			Vec3 rel = new Vec3(subtitle.x, subtitle.y, subtitle.z)
				.subtract(MC.player.getEyePosition());
			double side = rel.lengthSqr() > 1.0E-6
				? right.dot(rel.normalize()) : 0;
			String arrow = side > 0.15 ? "> " : side < -0.15 ? "< " : "";
			int rgb = color.getColor().getRGB() & 0xFFFFFF;
			centeredText(context, arrow + subtitle.text, width / 2,
				top + i * ROW_HEIGHT + 2, 0xFF000000 | rgb);
		}
	}

	private static final class Subtitle
	{
		private final String text;
		private double x;
		private double y;
		private double z;
		private long start;

		private Subtitle(String text, double x, double y, double z,
			long start)
		{
			this.text = text;
			this.x = x;
			this.y = y;
			this.z = z;
			this.start = start;
		}
	}

	private int argb(java.awt.Color color, int alpha)
	{
		int a = Math.max(0, Math.min(255, alpha));
		return a << 24 | color.getRGB() & 0xFFFFFF;
	}

	private boolean uiHidden()
	{
		try
		{
			Object gui = MC.getClass().getField("gui").get(MC);
			if(gui.getClass().getMethod("screen").invoke(gui) != null)
				return true;
		}catch(Exception ignored)
		{}

		try
		{
			return Boolean.TRUE.equals(MC.options.getClass()
				.getField("hideGui").get(MC.options));
		}catch(Exception ignored)
		{
			return false;
		}
	}

	private void text(GuiGraphicsExtractor context, String text, int x,
		int y, int color, boolean shadow)
	{
		context.text(MC.font, text, x, y, color, shadow);
	}

	private void centeredText(GuiGraphicsExtractor context, String text,
		int x, int y, int color)
	{
		context.centeredText(MC.font, text, x, y, color);
	}

}
