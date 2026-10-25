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
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import net.wurstclient.Category;
import net.wurstclient.SearchTags;
import net.wurstclient.events.PlayerAttacksEntityListener;
import net.wurstclient.events.UpdateListener;
import net.wurstclient.events.WorldChangeListener;
import net.wurstclient.hack.Hack;
import net.wurstclient.settings.CheckboxSetting;
import net.wurstclient.settings.EnumSetting;
import net.wurstclient.settings.SliderSetting;
import net.wurstclient.settings.SliderSetting.ValueDisplay;
import net.wurstclient.settings.TextFieldSetting;

/**
 * Spawns a burst of particles around entities that die near you.
 *
 * <p>Simplification: Soar's lightning option spawned a client-side lightning
 * bolt and its blood option used the old BLOCK_CRACK particle. This port uses
 * particle-only effects (lightning becomes electric sparks) because it must
 * not add mixins or client-side entities that differ from the server.
 */
@SearchTags({"kill effects", "death effects", "kill particles", "blood",
	"lightning kill", "kill effect"})
public final class KillEffectsHack extends Hack implements UpdateListener,
	PlayerAttacksEntityListener, WorldChangeListener
{
	private final EnumSetting<KillEffect> effect = new EnumSetting<>("Effect",
		"Particle effect spawned when an entity dies.", KillEffect.values(),
		KillEffect.BLOOD);

	private final SliderSetting multiplier = new SliderSetting("Multiplier",
		"Multiplies the amount of particles that are spawned.", 1, 1, 10, 1,
		ValueDisplay.INTEGER);

	private final CheckboxSetting sound = new CheckboxSetting("Sound",
		"Plays a sound when an entity dies.", true);

	private final TextFieldSetting soundId = new TextFieldSetting("Sound ID",
		"Sound event ID played on kills, e.g. minecraft:entity.generic.hurt.",
		"minecraft:entity.generic.hurt");

	private final SliderSetting volume = new SliderSetting("Volume",
		"Volume of the kill sound.", 1, 0, 1, 0.01, ValueDisplay.PERCENTAGE);

	private final CheckboxSetting onlyTargeted = new CheckboxSetting(
		"Only targeted",
		"Only triggers the effects for entities you have attacked.", true);

	private final SliderSetting targetMemory = new SliderSetting(
		"Target memory", "Seconds an attacked target is remembered.", 3.5,
		0.5, 10, 0.1, ValueDisplay.DECIMAL.withSuffix("s"));

	private final Map<Integer, Long> attackedTargets = new HashMap<>();
	private final Set<Integer> processedEntities = new HashSet<>();

	public KillEffectsHack()
	{
		super("KillEffects");
		setCategory(Category.RENDER);
		addSetting(effect);
		addSetting(multiplier);
		addSetting(sound);
		addSetting(soundId);
		addSetting(volume);
		addSetting(onlyTargeted);
		addSetting(targetMemory);
		soundId.visibleWhen(sound::isChecked);
		volume.visibleWhen(sound::isChecked);
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

			spawnEffect(living);
		}
	}

	private void spawnEffect(LivingEntity entity)
	{
		Vec3 pos = entity.position();
		double width = entity.getBbWidth();
		double height = entity.getBbHeight();
		int count = 10 * multiplier.getValueI();
		ParticleOptions particle = effect.getSelected().particle;

		for(int i = 0; i < count; i++)
		{
			double x = pos.x + (Math.random() - 0.5) * width;
			double y = pos.y + Math.random() * height;
			double z = pos.z + (Math.random() - 0.5) * width;
			double vx = (Math.random() - 0.5) * 0.4;
			double vy = Math.random() * 0.3;
			double vz = (Math.random() - 0.5) * 0.4;

			MC.level.addParticle(particle, x, y, z, vx, vy, vz);
		}

		if(!sound.isChecked())
			return;

		ResourceLocation id =
			ResourceLocation.tryParse(soundId.getValue().trim());
		if(id == null)
			return;

		SoundEvent event = SoundEvent.createVariableRangeEvent(id);
		MC.level.playLocalSound(pos.x, pos.y + height / 2, pos.z, event,
			SoundSource.PLAYERS, volume.getValueF(), 1, false);
	}

	public enum KillEffect
	{
		BLOOD(ParticleTypes.DAMAGE_INDICATOR, "Blood"),
		FLAMES(ParticleTypes.FLAME, "Flames"),
		CLOUD(ParticleTypes.CLOUD, "Cloud"),
		LIGHTNING(ParticleTypes.ELECTRIC_SPARK, "Lightning");

		private final ParticleOptions particle;
		private final String name;

		KillEffect(ParticleOptions particle, String name)
		{
			this.particle = particle;
			this.name = name;
		}

		@Override
		public String toString()
		{
			return name;
		}
	}
}
