/*
 * Copyright (c) 2026 WurstB+ Plus contributors
 *
 * This source code is subject to the terms of the GNU Affero General Public
 * License, version 3. If a copy of the AGPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/agpl-3.0.txt
 */
// Ported from Soar Client v7.1.2 (LGPL-3.0).
package net.wurstclient.hacks;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Set;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import net.wurstclient.Category;
import net.wurstclient.SearchTags;
import net.wurstclient.events.UpdateListener;
import net.wurstclient.events.WorldChangeListener;
import net.wurstclient.hack.Hack;
import net.wurstclient.settings.SliderSetting;
import net.wurstclient.settings.SliderSetting.ValueDisplay;

/**
 * Spawns an upward burst of crit and enchanted-hit sparks when a living entity
 * takes damage.
 *
 * <p>Damage is detected from increases in {@code hurtTime}. Soar rendered
 * floating damage numbers; this port spawns particle bursts instead, giving it
 * a different look from {@link BloodParticlesHack}.
 */
@SearchTags({"damage particles", "damage", "hit sparks", "crit particles",
	"damage burst"})
public final class DamageParticlesHack extends Hack
	implements UpdateListener, WorldChangeListener
{
	private final SliderSetting amount = new SliderSetting("Amount",
		"Number of particles spawned per hit.", 12, 2, 40, 1,
		ValueDisplay.INTEGER);

	private final Map<LivingEntity, Integer> hurtTimes =
		new IdentityHashMap<>();

	public DamageParticlesHack()
	{
		super("DamageParticles");
		setCategory(Category.RENDER);
		addSetting(amount);
	}

	@Override
	protected void onEnable()
	{
		EVENTS.add(UpdateListener.class, this);
		EVENTS.add(WorldChangeListener.class, this);
		hurtTimes.clear();
	}

	@Override
	protected void onDisable()
	{
		EVENTS.remove(UpdateListener.class, this);
		EVENTS.remove(WorldChangeListener.class, this);
		hurtTimes.clear();
	}

	@Override
	public void onWorldChange(ClientLevel world)
	{
		hurtTimes.clear();
	}

	@Override
	public void onUpdate()
	{
		if(MC.level == null || MC.player == null)
		{
			hurtTimes.clear();
			return;
		}

		Set<LivingEntity> seen =
			Collections.newSetFromMap(new IdentityHashMap<>());

		for(Entity entity : MC.level.entitiesForRendering())
		{
			if(!(entity instanceof LivingEntity living) || living == MC.player
				|| living.isRemoved() || living.isDeadOrDying())
				continue;

			seen.add(living);
			int hurtTime = living.hurtTime;
			Integer previous = hurtTimes.put(living, hurtTime);

			if(previous != null && hurtTime > previous)
				spawnBurst(living);
		}

		hurtTimes.keySet().retainAll(seen);
	}

	private void spawnBurst(LivingEntity entity)
	{
		Vec3 center = entity.position().add(0,
			entity.getBbHeight() * 0.5, 0);
		double width = entity.getBbWidth() * 0.5;

		for(int i = 0; i < amount.getValueI(); i++)
		{
			ParticleOptions particle = i % 3 == 0
				? ParticleTypes.ENCHANTED_HIT : ParticleTypes.CRIT;
			double angle = Math.random() * Math.PI * 2;
			double speed = 0.1 + Math.random() * 0.15;

			MC.level.addParticle(particle,
				center.x + (Math.random() - 0.5) * width, center.y,
				center.z + (Math.random() - 0.5) * width,
				Math.cos(angle) * speed, 0.1 + Math.random() * 0.2,
				Math.sin(angle) * speed);
		}
	}
}
