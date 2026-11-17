/*
 * Copyright (c) 2026 WurstB+ Plus contributors
 *
 * This source code is subject to the terms of the GNU Affero General Public
 * License, version 3. If a copy of the AGPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/agpl-3.0.txt
 */
// Ported from Soar Client v7.1.2 (LGPL-3.0).
package net.wurstclient.hacks;

import java.util.Random;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.wurstclient.Category;
import net.wurstclient.SearchTags;
import net.wurstclient.events.LeftClickListener;
import net.wurstclient.events.RightClickListener;
import net.wurstclient.hack.Hack;
import net.wurstclient.settings.CheckboxSetting;
import net.wurstclient.settings.SliderSetting;
import net.wurstclient.settings.SliderSetting.ValueDisplay;

/**
 * Spawns particles at the block or entity that was just clicked.
 *
 * <p>Soar drew a HUD arc at the mouse cursor. This port uses real world
 * particles at the clicked position instead.
 */
@SearchTags({"click effect", "click", "hit effect", "particles", "clickfx"})
public final class ClickEffectHack extends Hack
	implements LeftClickListener, RightClickListener
{
	private final SliderSetting particleCount = new SliderSetting("Particles",
		"How many particles to spawn per click.", 8, 1, 30, 1,
		ValueDisplay.INTEGER);

	private final CheckboxSetting onBlocks = new CheckboxSetting("On blocks",
		"Spawns particles when clicking a block.", true);

	private final CheckboxSetting onEntities = new CheckboxSetting(
		"On entities", "Spawns particles when clicking an entity.", true);

	private final Random random = new Random();

	public ClickEffectHack()
	{
		super("ClickEffect");
		setCategory(Category.RENDER);
		addSetting(particleCount);
		addSetting(onBlocks);
		addSetting(onEntities);
	}

	@Override
	protected void onEnable()
	{
		EVENTS.add(LeftClickListener.class, this);
		EVENTS.add(RightClickListener.class, this);
	}

	@Override
	protected void onDisable()
	{
		EVENTS.remove(LeftClickListener.class, this);
		EVENTS.remove(RightClickListener.class, this);
	}

	@Override
	public void onLeftClick(LeftClickEvent event)
	{
		spawnAtHit(true);
	}

	@Override
	public void onRightClick(RightClickEvent event)
	{
		spawnAtHit(false);
	}

	private void spawnAtHit(boolean attack)
	{
		if(MC.level == null || MC.player == null)
			return;

		HitResult hit = MC.hitResult;
		if(hit == null)
			return;

		Vec3 pos;
		if(hit instanceof EntityHitResult entityHit)
		{
			if(!onEntities.isChecked())
				return;

			Entity entity = entityHit.getEntity();
			if(entity == null)
				return;

			pos = entity.getBoundingBox().getCenter();
		}else if(hit instanceof BlockHitResult blockHit)
		{
			if(!onBlocks.isChecked())
				return;

			pos = blockHit.getLocation();
		}else
			return;

		int count = particleCount.getValueI();
		for(int i = 0; i < count; i++)
		{
			double vx = (random.nextDouble() - 0.5) * 0.35;
			double vy = random.nextDouble() * 0.3;
			double vz = (random.nextDouble() - 0.5) * 0.35;

			MC.level.addParticle(
				attack ? ParticleTypes.CRIT : ParticleTypes.END_ROD, pos.x,
				pos.y + 0.05, pos.z, vx, vy, vz);
		}
	}
}
