/*
 * Copyright (c) 2015-2026 CCBlueX
 * Copyright (c) 2025-2026 Penguin
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 */
package net.wurstclient.hacks;

import java.util.Comparator;
import java.util.stream.StreamSupport;

import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.projectile.LargeFireball;
import net.minecraft.world.entity.projectile.ShulkerBullet;
import net.minecraft.world.phys.Vec3;
import net.wurstclient.Category;
import net.wurstclient.SearchTags;
import net.wurstclient.events.UpdateListener;
import net.wurstclient.hack.Hack;
import net.wurstclient.hack.HackConflictGroup;
import net.wurstclient.settings.AttackSpeedSliderSetting;
import net.wurstclient.settings.PauseAttackOnContainersSetting;
import net.wurstclient.settings.SliderSetting;
import net.wurstclient.settings.SliderSetting.ValueDisplay;
import net.wurstclient.settings.SwingHandSetting;
import net.wurstclient.settings.SwingHandSetting.SwingHand;
import net.wurstclient.util.CombatRotationController;
import net.wurstclient.util.CombatTargetSession;
import net.wurstclient.util.ProjectileThreatPolicy;
import net.wurstclient.util.RotationQueue;
import net.wurstclient.util.RotationUtils;

@SearchTags({"AntiFireball", "anti fireball", "projectile puncher"})
public final class ProjectilePuncherHack extends Hack implements UpdateListener
{
	private final SliderSetting range = new SliderSetting("Range", 4.5, 3, 6,
		0.1, ValueDisplay.DECIMAL.withSuffix(" blocks"));
	private final AttackSpeedSliderSetting speed = new AttackSpeedSliderSetting();
	private final PauseAttackOnContainersSetting pauseOnContainers =
		new PauseAttackOnContainersSetting(true);
	private final SwingHandSetting swingHand =
		new SwingHandSetting(this, SwingHand.CLIENT);

	private final CombatTargetSession<Entity> targetSession =
		new CombatTargetSession<>();
	private final CombatRotationController rotationController =
		new CombatRotationController(RotationQueue.Priority.COMBAT);

	public ProjectilePuncherHack()
	{
		super("ProjectilePuncher");
		setCategory(Category.COMBAT);
		addConflictGroup(HackConflictGroup.COMBAT_TARGETING);
		addSetting(range);
		addSetting(speed);
		addSetting(pauseOnContainers);
		addSetting(swingHand);
	}

	@Override
	protected void onEnable()
	{
		targetSession.clear();
		speed.resetTimer();
		rotationController.start();
		EVENTS.add(UpdateListener.class, this);
	}

	@Override
	protected void onDisable()
	{
		EVENTS.remove(UpdateListener.class, this);
		targetSession.clear();
		rotationController.stop();
	}

	@Override
	public void onUpdate()
	{
		Entity candidate = findTarget();
		CombatTargetSession.Selection<Entity> selection =
			targetSession.track(candidate);
		if(selection.changed())
			speed.resetTimer();

		Entity target = selection.current();
		if(target == null)
		{
			rotationController.clearRequest();
			return;
		}

		rotationController.request(
			RotationUtils.getNeededRotations(target.getBoundingBox().getCenter()));
		if(!RotationUtils.isFacingBox(target.getBoundingBox(), range.getValue())
			|| !speed.isTimeToAttack())
			return;

		MC.gameMode.attack(MC.player, target);
		swingHand.swing(InteractionHand.MAIN_HAND);
		speed.resetTimer();
	}

	private Entity findTarget()
	{
		if(MC.player == null || MC.level == null || MC.gameMode == null
			|| MC.player.isSpectator() || pauseOnContainers.shouldPause())
			return null;

		Vec3 eyes = RotationUtils.getEyesPos();
		double rangeSquared = range.getValueSq();
		return StreamSupport.stream(
			MC.level.entitiesForRendering().spliterator(), false)
			.filter(this::isThreateningProjectile)
			.filter(entity -> ProjectileThreatPolicy.distanceSquared(eyes,
				entity.getBoundingBox().move(entity.getDeltaMovement())) <= rangeSquared)
			.filter(MC.player::hasLineOfSight)
			.min(Comparator.comparingDouble(entity ->
				ProjectileThreatPolicy.distanceSquared(eyes,
					entity.getBoundingBox())))
			.orElse(null);
	}

	private boolean isThreateningProjectile(Entity entity)
	{
		if(!(entity instanceof LargeFireball)
			&& !(entity instanceof ShulkerBullet))
			return false;
		return entity.isAlive() && ProjectileThreatPolicy.isThreat(
			entity.position(), entity.getDeltaMovement(), entity.getBoundingBox(),
			MC.player.getBoundingBox());
	}
}
