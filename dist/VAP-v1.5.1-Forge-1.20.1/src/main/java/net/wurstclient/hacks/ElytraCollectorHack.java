/*
 * Copyright (c) 2014-2025 Wurst-Imperium and contributors.
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 *
 * Ported from FO (CC0-1.0).
 */
package net.wurstclient.hacks;

import java.util.List;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.wurstclient.Category;
import net.wurstclient.SearchTags;
import net.wurstclient.events.UpdateListener;
import net.wurstclient.hack.Hack;
import net.wurstclient.settings.EnumSetting;
import net.wurstclient.settings.SliderSetting;
import net.wurstclient.settings.SliderSetting.ValueDisplay;
import net.wurstclient.util.Rotation;
import net.wurstclient.util.RotationUtils;

/**
 * Simplified port of FO's ElytraCollector module.
 *
 * <p>
 * While the player is gliding, turns toward the best nearby dropped item and
 * adds a small velocity impulse toward it every tick. The "Elytra first"
 * priority makes dropped elytras win over closer loot.
 *
 * <p>
 * Unlike FO's version this port only handles item pickup. It does not search
 * for end cities, fight item frames, manage shulkers/fireworks or store loot.
 */
@SearchTags({"elytra collector", "item collector", "elytra pickup",
	"loot collector", "auto collect"})
public final class ElytraCollectorHack extends Hack implements UpdateListener
{
	private static final float TURN_SPEED = 25;

	private final SliderSetting range = new SliderSetting("Range",
		"Maximum distance to look for dropped items.", 32, 4, 64, 1,
		ValueDisplay.INTEGER.withSuffix(" blocks"));

	private final SliderSetting speed = new SliderSetting("Speed",
		"How strongly the hack pulls toward the target item.", 1, 0.1, 5,
		0.05, ValueDisplay.DECIMAL);

	private final EnumSetting<Priority> priority =
		new EnumSetting<>("Priority",
			"Which dropped item should be collected first.", Priority.values(),
			Priority.NEAREST);

	public ElytraCollectorHack()
	{
		super("ElytraCollector");
		setCategory(Category.MOVEMENT);
		addSetting(range);
		addSetting(speed);
		addSetting(priority);
	}

	@Override
	protected void onEnable()
	{
		EVENTS.add(UpdateListener.class, this);
	}

	@Override
	protected void onDisable()
	{
		EVENTS.remove(UpdateListener.class, this);
	}

	@Override
	public void onUpdate()
	{
		if(MC.player == null || MC.level == null)
			return;

		if(!MC.player.isFallFlying())
			return;

		ItemEntity target = findTarget();
		if(target == null)
			return;

		Vec3 targetPos = target.position().add(0, target.getBbHeight() / 2, 0);

		Rotation needed = RotationUtils.getNeededRotations(targetPos);
		MC.player.setYRot(RotationUtils.limitAngleChange(MC.player.getYRot(),
			needed.yaw(), TURN_SPEED));
		MC.player.setXRot(RotationUtils.limitAngleChange(MC.player.getXRot(),
			needed.pitch(), TURN_SPEED));

		Vec3 direction = targetPos.subtract(RotationUtils.getEyesPos());
		double distance = direction.length();
		if(distance < 0.001)
			return;

		direction = direction.scale(1 / distance);
		double acceleration = 0.05 * speed.getValue();
		Vec3 velocity = MC.player.getDeltaMovement();

		MC.player.setDeltaMovement(velocity.x + direction.x * acceleration,
			velocity.y + direction.y * acceleration,
			velocity.z + direction.z * acceleration);
	}

	private ItemEntity findTarget()
	{
		double radius = range.getValue();
		AABB box = MC.player.getBoundingBox().inflate(radius);
		List<Entity> entities = MC.level.getEntities((Entity)null, box);

		boolean preferElytra = priority.getSelected() == Priority.ELYTRA_FIRST;
		ItemEntity best = null;
		double bestScore = Double.MAX_VALUE;

		for(Entity entity : entities)
		{
			if(!(entity instanceof ItemEntity item) || item.isRemoved()
				|| item.getItem().isEmpty())
				continue;

			double distanceSq = item.distanceToSqr(MC.player);
			if(distanceSq > radius * radius)
				continue;

			double score =
				preferElytra && !item.getItem().is(Items.ELYTRA)
					? distanceSq + 1.0E6 : distanceSq;

			if(score < bestScore)
			{
				bestScore = score;
				best = item;
			}
		}

		return best;
	}

	public enum Priority
	{
		NEAREST("Nearest"),
		ELYTRA_FIRST("Elytra first");

		private final String name;

		Priority(String name)
		{
			this.name = name;
		}

		@Override
		public String toString()
		{
			return name;
		}
	}
}
