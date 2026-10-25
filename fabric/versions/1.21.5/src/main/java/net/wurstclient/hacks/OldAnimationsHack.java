/*
 * Copyright (c) 2026 WurstB+ Plus contributors
 *
 * This source code is subject to the terms of the GNU Affero General Public
 * License, version 3. If a copy of the AGPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/agpl-3.0.txt
 */
// Ported from Soar Client v7.1.2 (LGPL-3.0).
package net.wurstclient.hacks;

import net.minecraft.client.AttackIndicatorStatus;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.BlockHitResult;
import net.wurstclient.Category;
import net.wurstclient.SearchTags;
import net.wurstclient.events.LeftClickListener;
import net.wurstclient.events.PlayerAttacksEntityListener;
import net.wurstclient.events.UpdateListener;
import net.wurstclient.hack.Hack;
import net.wurstclient.settings.CheckboxSetting;

/**
 * Applies a practical subset of Soar's 1.8-style animation toggles.
 *
 * <p>Implemented: block-hit particles, sprint hit particles, hiding the attack
 * indicator and disabling vanilla damage tilt. Rotating the first-person hand
 * model itself would need a render-state hook and is not attempted here.
 */
@SearchTags({"old animations", "1.8 animations", "oldanimations", "legacy animations"})
public final class OldAnimationsHack extends Hack
	implements UpdateListener, LeftClickListener, PlayerAttacksEntityListener
{
	private final CheckboxSetting blockHit = new CheckboxSetting("Block hit",
		"Spawns extra particles when hitting a block.", true);

	private final CheckboxSetting pushingParticles = new CheckboxSetting(
		"Pushing particles", "Spawns sweep particles when sprint-hitting.", true);

	private final CheckboxSetting hideAttackIndicator = new CheckboxSetting(
		"Hide attack indicator", "Hides the 1.9 attack cooldown indicator.", true);

	private final CheckboxSetting oldDamageTilt = new CheckboxSetting(
		"Old damage tilt", "Disables the vanilla damage camera tilt.", false);

	private AttackIndicatorStatus oldIndicator;
	private double oldTilt;
	private boolean indicatorApplied;
	private boolean tiltApplied;

	public OldAnimationsHack()
	{
		super("OldAnimations");
		setCategory(Category.RENDER);
		addSetting(blockHit);
		addSetting(pushingParticles);
		addSetting(hideAttackIndicator);
		addSetting(oldDamageTilt);
	}

	@Override
	protected void onEnable()
	{
		oldIndicator = MC.options.attackIndicator().get();
		oldTilt = MC.options.damageTiltStrength().get();
		indicatorApplied = false;
		tiltApplied = false;

		EVENTS.add(UpdateListener.class, this);
		EVENTS.add(LeftClickListener.class, this);
		EVENTS.add(PlayerAttacksEntityListener.class, this);
		applyOptions();
	}

	@Override
	protected void onDisable()
	{
		EVENTS.remove(UpdateListener.class, this);
		EVENTS.remove(LeftClickListener.class, this);
		EVENTS.remove(PlayerAttacksEntityListener.class, this);

		if(indicatorApplied)
			MC.options.attackIndicator().set(oldIndicator);
		if(tiltApplied)
			MC.options.damageTiltStrength().set(oldTilt);

		indicatorApplied = false;
		tiltApplied = false;
	}

	@Override
	public void onUpdate()
	{
		applyOptions();
	}

	private void applyOptions()
	{
		if(hideAttackIndicator.isChecked())
		{
			if(!indicatorApplied)
			{
				MC.options.attackIndicator().set(AttackIndicatorStatus.OFF);
				indicatorApplied = true;
			}
		}else if(indicatorApplied)
		{
			MC.options.attackIndicator().set(oldIndicator);
			indicatorApplied = false;
		}

		if(oldDamageTilt.isChecked())
		{
			if(!tiltApplied)
			{
				MC.options.damageTiltStrength().set(0.0);
				tiltApplied = true;
			}
		}else if(tiltApplied)
		{
			MC.options.damageTiltStrength().set(oldTilt);
			tiltApplied = false;
		}
	}

	@Override
	public void onLeftClick(LeftClickEvent event)
	{
		if(!blockHit.isChecked() || MC.level == null
			|| !(MC.hitResult instanceof BlockHitResult hit))
			return;

		for(int i = 0; i < 4; i++)
			MC.level.addParticle(ParticleTypes.CRIT, hit.getLocation().x,
				hit.getLocation().y, hit.getLocation().z, 0, 0.05, 0);
	}

	@Override
	public void onPlayerAttacksEntity(Entity target)
	{
		if(!pushingParticles.isChecked() || MC.level == null
			|| MC.player == null || !MC.player.isSprinting()
			|| !(target instanceof LivingEntity))
			return;

		MC.level.addParticle(ParticleTypes.SWEEP_ATTACK, target.getX(),
			target.getY() + target.getBbHeight() * 0.5, target.getZ(), 0, 0, 0);
	}
}
