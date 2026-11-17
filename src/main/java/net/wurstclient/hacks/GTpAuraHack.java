/*
 * Copyright (c) 2026 WurstB+ Plus contributors
 *
 * This source code is subject to the terms of the GNU Affero General Public
 * License, version 3. If a copy of the AGPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/agpl-3.0.txt
 */
// Ported from InvincibleMachineGun v1.9.0 (GPL-3.0).
package net.wurstclient.hacks;

import java.util.Comparator;
import java.util.stream.Stream;

import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.wurstclient.Category;
import net.wurstclient.SearchTags;
import net.wurstclient.events.UpdateListener;
import net.wurstclient.hack.Hack;
import net.wurstclient.hack.HackConflictGroup;
import net.wurstclient.settings.CheckboxSetting;
import net.wurstclient.settings.SliderSetting;
import net.wurstclient.settings.SliderSetting.ValueDisplay;
import net.wurstclient.settings.filterlists.EntityFilterList;
import net.wurstclient.util.EntityUtils;
import net.wurstclient.util.ServerEquivTp;

@SearchTags({"g tp aura", "gtpaura", "grim tp aura", "grim传送光环"})
public final class GTpAuraHack extends Hack implements UpdateListener
{
	private static final double CRIT_OFFSET = 0.001;

	private final SliderSetting range = new SliderSetting("Range",
		"Maximum distance to search for a target.", 100, 1, 300, 1,
		ValueDisplay.INTEGER.withSuffix(" blocks"));

	private final SliderSetting attackDelay = new SliderSetting(
		"Attack delay",
		"Fixed delay between attacks when the vanilla cooldown is disabled.",
		800, 1, 2000, 1, ValueDisplay.INTEGER.withSuffix("ms"));

	private final SliderSetting attackTimes = new SliderSetting(
		"Attack times", "How many attacks to send per teleport.", 1, 1, 200, 1,
		ValueDisplay.INTEGER);

	private final CheckboxSetting useCooldown =
		new CheckboxSetting("Use cooldown",
			"Wait for the vanilla attack cooldown before attacking.", true);

	private final SliderSetting cooldownThreshold = new SliderSetting(
		"Cooldown threshold",
		"Attack once the vanilla cooldown is at least this full.", 0.75, 0.1,
		1, 0.05, ValueDisplay.PERCENTAGE);

	private final CheckboxSetting critical = new CheckboxSetting("Critical",
		"Send a fake falling packet so the hits count as criticals.", true);

	private final CheckboxSetting swingHand = new CheckboxSetting("Swing hand",
		"Play the hand swing animation when attacking.", true);

	private final CheckboxSetting findSpot = new CheckboxSetting(
		"Find attack spot",
		"Search for a nearby standable spot if the target's feet are blocked.",
		true);

	private final CheckboxSetting keepTarget = new CheckboxSetting(
		"Keep target",
		"Keep the last target when no new target is found in the current tick.",
		true);

	private final SliderSetting packetDistance = new SliderSetting(
		"Packet distance",
		"Distance covered by each movement packet when the"
			+ " TPServerEquivalent gate is active.",
		10, 1, 100, 0.5, ValueDisplay.DECIMAL.withSuffix(" blocks"));

	private final EntityFilterList entityFilters =
		EntityFilterList.genericCombat();

	private Entity target;
	private long lastAttack;

	public GTpAuraHack()
	{
		super("GTpAura");
		setCategory(Category.COMBAT);
		addConflictGroup(HackConflictGroup.COMBAT_TARGETING);
		addSetting(range);
		addSetting(attackDelay);
		addSetting(attackTimes);
		addSetting(useCooldown);
		addSetting(cooldownThreshold);
		addSetting(critical);
		addSetting(swingHand);
		addSetting(findSpot);
		addSetting(keepTarget);
		addSetting(packetDistance);
		entityFilters.forEach(this::addSetting);
	}

	@Override
	protected void onEnable()
	{
		target = null;
		lastAttack = 0;
		EVENTS.add(UpdateListener.class, this);
	}

	@Override
	protected void onDisable()
	{
		EVENTS.remove(UpdateListener.class, this);
		target = null;
	}

	@Override
	public void onUpdate()
	{
		if(MC.player == null || MC.level == null)
			return;

		updateTarget();
		Entity t = target;
		if(t == null || !t.isAlive() || t.isRemoved())
			return;
		if(MC.player.distanceToSqr(t) > range.getValueSq())
			return;
		if(!isReadyToAttack())
			return;

		Vec3 destination = t.position();
		if(!ServerEquivTp.canStand(destination))
		{
			if(!findSpot.isChecked())
				return;
			destination = findAttackSpot(t);
			if(destination == null)
				return;
		}

		lastAttack = System.currentTimeMillis();
		ServerEquivTp.teleport("GTpAura", destination,
			packetDistance.getValue());

		if(critical.isChecked())
			ServerEquivTp.sendPos(destination.x, destination.y - CRIT_OFFSET,
				destination.z, false);

		for(int i = 0; i < attackTimes.getValueI(); i++)
			MC.gameMode.attack(MC.player, t);

		if(swingHand.isChecked())
			MC.player.swing(InteractionHand.MAIN_HAND);
	}

	private void updateTarget()
	{
		Stream<Entity> stream = EntityUtils.getAttackableEntities();
		double rangeSq = range.getValueSq();
		stream = stream
			.filter(e -> e != MC.player && MC.player.distanceToSqr(e) <= rangeSq);
		stream = entityFilters.applyTo(stream);

		Entity found = stream
			.min(Comparator.comparingDouble(e -> MC.player.distanceToSqr(e)))
			.orElse(null);

		if(found != null || !keepTarget.isChecked())
			target = found;
	}

	private boolean isReadyToAttack()
	{
		if(useCooldown.isChecked())
			return MC.player.getAttackStrengthScale(0)
				>= cooldownThreshold.getValue();

		return System.currentTimeMillis() - lastAttack >= attackDelay.getValueI();
	}

	private Vec3 findAttackSpot(Entity t)
	{
		Vec3 base = t.position();
		for(int radius = 1; radius <= 2; radius++)
			for(int dx = -radius; dx <= radius; dx++)
				for(int dz = -radius; dz <= radius; dz++)
					for(int dy = 0; dy <= 1; dy++)
					{
						Vec3 pos = new Vec3(base.x + dx, base.y + dy,
							base.z + dz);
						if(pos.distanceTo(base) > 3)
							continue;
						if(ServerEquivTp.canStand(pos))
							return pos;
					}
		return null;
	}
}
