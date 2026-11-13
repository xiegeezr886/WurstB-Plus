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
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.wurstclient.Category;
import net.wurstclient.SearchTags;
import net.wurstclient.events.PlayerAttacksEntityListener;
import net.wurstclient.hack.Hack;
import net.wurstclient.settings.CheckboxSetting;
import net.wurstclient.settings.SliderSetting;
import net.wurstclient.settings.SliderSetting.ValueDisplay;

@SearchTags({"particle customizer", "particles", "crit particles",
	"sharpness particles"})
public final class ParticleCustomizerHack extends Hack
	implements PlayerAttacksEntityListener
{
	private final CheckboxSetting alwaysSharpness = new CheckboxSetting(
		"Always sharpness", "Always spawns sharpness particles.", false);

	private final CheckboxSetting alwaysCriticals = new CheckboxSetting(
		"Always criticals", "Always spawns critical particles.", false);

	private final CheckboxSetting sharpness = new CheckboxSetting("Sharpness",
		"Spawns sharpness particles for enchanted weapons.", true);

	private final CheckboxSetting criticals = new CheckboxSetting("Criticals",
		"Spawns critical particles when falling.", false);

	private final SliderSetting sharpnessAmount = new SliderSetting(
		"Sharpness amount", "Number of sharpness particles.", 2, 1, 10, 1,
		ValueDisplay.INTEGER);

	private final SliderSetting criticalsAmount = new SliderSetting(
		"Criticals amount", "Number of critical particles.", 2, 1, 10, 1,
		ValueDisplay.INTEGER);

	private final Random random = new Random();

	public ParticleCustomizerHack()
	{
		super("ParticleCustomizer");
		setCategory(Category.RENDER);
		addSetting(alwaysSharpness);
		addSetting(alwaysCriticals);
		addSetting(sharpness);
		addSetting(criticals);
		addSetting(sharpnessAmount);
		addSetting(criticalsAmount);
	}

	@Override
	protected void onEnable()
	{
		EVENTS.add(PlayerAttacksEntityListener.class, this);
	}

	@Override
	protected void onDisable()
	{
		EVENTS.remove(PlayerAttacksEntityListener.class, this);
	}

	@Override
	public void onPlayerAttacksEntity(Entity target)
	{
		if(MC.level == null || MC.player == null
			|| !(target instanceof LivingEntity))
			return;

		boolean critical = MC.player.fallDistance > 0
			&& !MC.player.onGround() && !MC.player.onClimbable()
			&& !MC.player.isInWater() && !MC.player.isPassenger()
			&& !MC.player.hasEffect(MobEffects.BLINDNESS);

		if(alwaysCriticals.isChecked() || criticals.isChecked() && critical)
			spawn(ParticleTypes.CRIT, target, criticalsAmount.getValueI());

		boolean sharp = MC.player.getMainHandItem().isEnchanted();
		if(alwaysSharpness.isChecked() || sharpness.isChecked() && sharp)
			spawn(ParticleTypes.ENCHANTED_HIT, target,
				sharpnessAmount.getValueI());
	}

	private void spawn(net.minecraft.core.particles.ParticleOptions type,
		Entity target, int count)
	{
		for(int i = 0; i < count; i++)
		{
			double vx = (random.nextDouble() - 0.5) * 0.2;
			double vy = random.nextDouble() * 0.2;
			double vz = (random.nextDouble() - 0.5) * 0.2;
			MC.level.addParticle(type, target.getX(),
				target.getY() + target.getBbHeight() * 0.5, target.getZ(), vx,
				vy, vz);
		}
	}
}
