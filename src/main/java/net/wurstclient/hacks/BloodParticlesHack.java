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
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.wurstclient.Category;
import net.wurstclient.SearchTags;
import net.wurstclient.events.UpdateListener;
import net.wurstclient.events.WorldChangeListener;
import net.wurstclient.hack.Hack;
import net.wurstclient.settings.SliderSetting;
import net.wurstclient.settings.SliderSetting.ValueDisplay;

/**
 * Spawns red blood particles when a living entity takes damage.
 *
 * <p>Damage is detected by watching for increases in {@code hurtTime}, so no
 * mixin or attack hook is needed. Soar additionally played a dig sound and
 * used the old redstone BLOCK_CRACK particle; this port uses the damage
 * indicator particle instead and skips the sound.
 */
@SearchTags({"blood particles", "blood", "blood effect", "hit particles",
	"damage particles"})
public final class BloodParticlesHack extends Hack
	implements UpdateListener, WorldChangeListener
{
	private final SliderSetting amount = new SliderSetting("Amount",
		"Number of blood particles per hit.", 2, 1, 10, 1,
		ValueDisplay.INTEGER);

	private final Map<LivingEntity, Integer> hurtTimes =
		new IdentityHashMap<>();

	public BloodParticlesHack()
	{
		super("BloodParticles");
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
				spawnBlood(living);
		}

		hurtTimes.keySet().retainAll(seen);
	}

	private void spawnBlood(LivingEntity entity)
	{
		double x = entity.getX();
		double y = Math.max(entity.getY() + entity.getBbHeight() - 0.75,
			entity.getY() + 0.1);
		double z = entity.getZ();
		double spread = entity.getBbWidth() * 0.5;

		for(int i = 0; i < amount.getValueI(); i++)
			MC.level.addParticle(ParticleTypes.DAMAGE_INDICATOR,
				x + (Math.random() - 0.5) * spread,
				y + (Math.random() - 0.5) * 0.4,
				z + (Math.random() - 0.5) * spread,
				(Math.random() - 0.5) * 0.15, Math.random() * 0.1,
				(Math.random() - 0.5) * 0.15);
	}
}
