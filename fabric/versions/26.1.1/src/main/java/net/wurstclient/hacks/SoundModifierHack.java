/*
 * Copyright (c) 2026 WurstB+ Plus contributors
 *
 * This source code is subject to the terms of the GNU Affero General Public
 * License, version 3. If a copy of the AGPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/agpl-3.0.txt
 */
// Ported from Soar Client v7.1.2 (LGPL-3.0).
package net.wurstclient.hacks;

import java.util.EnumMap;

import net.minecraft.sounds.SoundSource;
import net.wurstclient.Category;
import net.wurstclient.SearchTags;
import net.wurstclient.events.UpdateListener;
import net.wurstclient.hack.Hack;
import net.wurstclient.settings.SliderSetting;
import net.wurstclient.settings.SliderSetting.ValueDisplay;

/**
 * Overrides vanilla sound category volumes.
 *
 * <p>Soar matched individual sound names. This port exposes the vanilla sound
 * categories instead, which is version-independent and still lets players mute
 * or boost specific groups.
 */
@SearchTags({"sound modifier", "soundmodifier", "volume", "sound volume",
	"mute sounds"})
public final class SoundModifierHack extends Hack implements UpdateListener
{
	private static final SoundSource[] SOURCES = {SoundSource.MASTER,
		SoundSource.MUSIC, SoundSource.RECORDS, SoundSource.WEATHER,
		SoundSource.BLOCKS, SoundSource.HOSTILE, SoundSource.NEUTRAL,
		SoundSource.PLAYERS, SoundSource.AMBIENT, SoundSource.VOICE};

	private final SliderSetting master = volume("Master", 100);
	private final SliderSetting music = volume("Music", 100);
	private final SliderSetting records = volume("Records", 100);
	private final SliderSetting weather = volume("Weather", 100);
	private final SliderSetting blocks = volume("Blocks", 100);
	private final SliderSetting hostile = volume("Hostile mobs", 100);
	private final SliderSetting neutral = volume("Neutral mobs", 100);
	private final SliderSetting players = volume("Players", 100);
	private final SliderSetting ambient = volume("Ambient", 100);
	private final SliderSetting voice = volume("Voice", 100);

	private final EnumMap<SoundSource, Double> oldVolumes =
		new EnumMap<>(SoundSource.class);

	public SoundModifierHack()
	{
		super("SoundModifier");
		setCategory(Category.OTHER);
		addSetting(master);
		addSetting(music);
		addSetting(records);
		addSetting(weather);
		addSetting(blocks);
		addSetting(hostile);
		addSetting(neutral);
		addSetting(players);
		addSetting(ambient);
		addSetting(voice);
	}

	private SliderSetting volume(String name, int value)
	{
		return new SliderSetting(name, "Volume of this sound category.",
			value, 0, 100, 1, ValueDisplay.INTEGER.withSuffix("%"));
	}

	@Override
	protected void onEnable()
	{
		for(SoundSource source : SOURCES)
			oldVolumes.put(source,
				MC.options.getSoundSourceOptionInstance(source).get());

		EVENTS.add(UpdateListener.class, this);
		apply();
	}

	@Override
	protected void onDisable()
	{
		EVENTS.remove(UpdateListener.class, this);

		for(SoundSource source : SOURCES)
		{
			Double value = oldVolumes.get(source);
			if(value != null)
				MC.options.getSoundSourceOptionInstance(source).set(value);
		}
		oldVolumes.clear();
	}

	@Override
	public void onUpdate()
	{
		apply();
	}

	private void apply()
	{
		set(SoundSource.MASTER, master);
		set(SoundSource.MUSIC, music);
		set(SoundSource.RECORDS, records);
		set(SoundSource.WEATHER, weather);
		set(SoundSource.BLOCKS, blocks);
		set(SoundSource.HOSTILE, hostile);
		set(SoundSource.NEUTRAL, neutral);
		set(SoundSource.PLAYERS, players);
		set(SoundSource.AMBIENT, ambient);
		set(SoundSource.VOICE, voice);
	}

	private void set(SoundSource source, SliderSetting setting)
	{
		MC.options.getSoundSourceOptionInstance(source)
			.set(setting.getValue() / 100.0);
	}
}
