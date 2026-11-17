/*
 * Copyright (c) 2026 WurstB+ Plus contributors
 *
 * This source code is subject to the terms of the GNU Affero General Public
 * License, version 3. If a copy of the AGPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/agpl-3.0.txt
 */
// Ported from Soar Client v7.1.2 (LGPL-3.0).
package net.wurstclient.hud2.mods;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;

import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.sounds.SoundEventListener;
import net.minecraft.client.sounds.WeighedSoundEvents;
import net.wurstclient.hud2.HUDMod;
import net.wurstclient.hud2.nanovg.HudFonts;

public final class SoundSubtitlesMod extends HUDMod
{
	private static final int MAX_ROWS = 6;
	private static final long LIFETIME = 3000;

	private final List<Subtitle> subtitles = new ArrayList<>();
	private SoundEventListener listener;

	public SoundSubtitlesMod()
	{
		super("SoundSubtitles", "Sound Subtitles",
			"Shows a directional list of nearby sounds.");
		setTranslationKey("sound_subtitles");
		setDefaultPosition(350, 780);
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
					return "SoundSubtitlesMod listener";
					default:
					return null;
				}
			});
		MC.getSoundManager().addListener(listener);
	}

	@Override
	protected void onDisable()
	{
		if(listener != null)
			MC.getSoundManager().removeListener(listener);
		listener = null;
		subtitles.clear();
	}

	private void handleSound(SoundInstance sound, WeighedSoundEvents events)
	{
		String text = null;
		if(events != null && events.getSubtitle() != null)
			text = events.getSubtitle().getString();
		if(text == null || text.isBlank())
			text = sound.getLocation().getPath();
		if(text == null || text.isBlank())
			return;

		long now = System.currentTimeMillis();
		for(Subtitle subtitle : subtitles)
			if(subtitle.text.equals(text))
			{
				subtitle.start = now;
				return;
			}

		subtitles.add(0, new Subtitle(text, now));
		while(subtitles.size() > MAX_ROWS)
			subtitles.remove(subtitles.size() - 1);
	}

	@Override
	protected void draw()
	{
		long now = System.currentTimeMillis();
		subtitles.removeIf(subtitle -> now - subtitle.start > LIFETIME);
		if(subtitles.isEmpty())
		{
			drawBackground(160, 20);
			drawText("No sounds", 6, 5, 9, HudFonts.REGULAR,
				getFontColor(200));
			setSize(160, 20);
			return;
		}

		float width = 160;
		for(Subtitle subtitle : subtitles)
			width = Math.max(width,
				getTextWidth(subtitle.text, 9, HudFonts.REGULAR) + 12);
		float height = 6 + subtitles.size() * 13;
		drawBackground(width, height);
		for(int i = 0; i < subtitles.size(); i++)
			drawText(subtitles.get(i).text, 6, 4 + i * 13, 9,
				HudFonts.REGULAR);
		setSize(Math.round(width), Math.round(height));
	}

	private static final class Subtitle
	{
		private final String text;
		private long start;

		private Subtitle(String text, long start)
		{
			this.text = text;
			this.start = start;
		}
	}
}
