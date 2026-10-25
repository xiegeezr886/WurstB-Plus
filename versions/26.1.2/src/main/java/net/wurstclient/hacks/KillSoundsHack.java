/*
 * Copyright (c) 2026 WurstB+ Plus contributors
 *
 * This source code is subject to the terms of the GNU Affero General Public
 * License, version 3. If a copy of the AGPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/agpl-3.0.txt
 */
// Ported from Soar Client v7.1.2 (LGPL-3.0).
package net.wurstclient.hacks;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.wurstclient.Category;
import net.wurstclient.SearchTags;
import net.wurstclient.events.PlayerAttacksEntityListener;
import net.wurstclient.events.UpdateListener;
import net.wurstclient.events.WorldChangeListener;
import net.wurstclient.hack.Hack;
import net.wurstclient.settings.CheckboxSetting;
import net.wurstclient.settings.SliderSetting;
import net.wurstclient.settings.SliderSetting.ValueDisplay;
import net.wurstclient.settings.TextFieldSetting;

/**
 * Plays a configurable sound whenever an entity dies near you.
 *
 * <p>Simplification: Soar loaded a bundled "oof" WAV and supported a custom
 * sound file. This port uses a configurable vanilla sound event ID instead, so
 * no audio assets need to be shipped.
 */
@SearchTags({"kill sounds", "kill sound", "death sound", "oof", "killsound"})
public final class KillSoundsHack extends Hack implements UpdateListener,
	PlayerAttacksEntityListener, WorldChangeListener
{
	private final TextFieldSetting soundId = new TextFieldSetting("Sound ID",
		"Sound event ID played on kills, e.g. minecraft:entity.player.levelup.",
		"minecraft:entity.player.levelup");

	private final SliderSetting volume = new SliderSetting("Volume",
		"Volume of the kill sound.", 0.5, 0, 1, 0.01,
		ValueDisplay.PERCENTAGE);

	private final SliderSetting pitch = new SliderSetting("Pitch",
		"Pitch of the kill sound.", 1, 0.5, 2, 0.01, ValueDisplay.DECIMAL);

	private final CheckboxSetting onlyTargeted = new CheckboxSetting(
		"Only targeted",
		"Only plays the sound for entities you have attacked.", true);

	private final SliderSetting targetMemory = new SliderSetting(
		"Target memory", "Seconds an attacked target is remembered.", 3.5,
		0.5, 10, 0.1, ValueDisplay.DECIMAL.withSuffix("s"));

	private final Map<Integer, Long> attackedTargets = new HashMap<>();
	private final Set<Integer> processedEntities = new HashSet<>();

	public KillSoundsHack()
	{
		super("KillSounds");
		setCategory(Category.OTHER);
		addSetting(soundId);
		addSetting(volume);
		addSetting(pitch);
		addSetting(onlyTargeted);
		addSetting(targetMemory);
		targetMemory.visibleWhen(onlyTargeted::isChecked);
	}

	@Override
	protected void onEnable()
	{
		EVENTS.add(UpdateListener.class, this);
		EVENTS.add(PlayerAttacksEntityListener.class, this);
		EVENTS.add(WorldChangeListener.class, this);
		attackedTargets.clear();
		processedEntities.clear();
	}

	@Override
	protected void onDisable()
	{
		EVENTS.remove(UpdateListener.class, this);
		EVENTS.remove(PlayerAttacksEntityListener.class, this);
		EVENTS.remove(WorldChangeListener.class, this);
		attackedTargets.clear();
		processedEntities.clear();
	}

	@Override
	public void onWorldChange(ClientLevel world)
	{
		attackedTargets.clear();
		processedEntities.clear();
	}

	@Override
	public void onPlayerAttacksEntity(Entity target)
	{
		if(target instanceof LivingEntity)
			attackedTargets.put(target.getId(), System.currentTimeMillis());
	}

	@Override
	public void onUpdate()
	{
		if(MC.player == null || MC.level == null)
			return;

		long now = System.currentTimeMillis();
		long timeout = (long)(targetMemory.getValue() * 1000);
		attackedTargets.entrySet()
			.removeIf(entry -> now - entry.getValue() > timeout);

		for(Entity entity : MC.level.entitiesForRendering())
		{
			if(!(entity instanceof LivingEntity living)
				|| entity == MC.player)
				continue;

			if(living.getHealth() > 0 && !living.isDeadOrDying())
				continue;

			if(!processedEntities.add(entity.getId()))
				continue;

			if(onlyTargeted.isChecked()
				&& !attackedTargets.containsKey(entity.getId()))
				continue;

			playKillSound();
		}
	}

	private void playKillSound()
	{
		Identifier id = Identifier.tryParse(soundId.getValue().trim());
		if(id == null)
			return;

		SoundEvent sound = SoundEvent.createVariableRangeEvent(id);
		MC.getSoundManager().play(SimpleSoundInstance.forUI(sound,
			pitch.getValueF(), volume.getValueF()));
	}
}
