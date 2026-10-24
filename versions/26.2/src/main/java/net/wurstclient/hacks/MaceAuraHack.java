/*
 * Copyright (c) 2026 WurstB+ Plus contributors
 *
 * This source code is subject to the terms of the GNU Affero General Public
 * License, version 3. If a copy of the AGPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/agpl-3.0.txt
 *
 * Ported from InvincibleMachineGun (GPL-3.0).
 */
package net.wurstclient.hacks;

import net.minecraft.core.BlockPos;
import net.minecraft.network.protocol.game.ServerboundAttackPacket;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.network.protocol.game.ServerboundSetCarriedItemPacket;
import net.minecraft.network.protocol.game.ServerboundSwingPacket;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.animal.equine.AbstractHorse;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;
import net.wurstclient.Category;
import net.wurstclient.SearchTags;
import net.wurstclient.events.UpdateListener;
import net.wurstclient.hack.Hack;
import net.wurstclient.settings.CheckboxSetting;
import net.wurstclient.settings.SliderSetting;
import net.wurstclient.settings.SliderSetting.ValueDisplay;
import net.wurstclient.util.InventoryUtils;
import net.wurstclient.util.Rotation;
import net.wurstclient.util.RotationUtils;

@SearchTags({"mace aura", "mace killaura", "MaceAura", "smash aura"})
public final class MaceAuraHack extends Hack implements UpdateListener
{
	private final SliderSetting range = new SliderSetting("Range",
		"Maximum attack range.", 4.5, 0, 6, 0.05, ValueDisplay.DECIMAL);

	private final CheckboxSetting autoSwitch = new CheckboxSetting(
		"Auto switch", "Switches to a mace in the hotbar.", true);

	private final CheckboxSetting allowFlight = new CheckboxSetting(
		"Allow airborne", "Allow attacking while not on the ground.", false);

	private final CheckboxSetting swingHand = new CheckboxSetting("Swing hand",
		"Send a swing packet after attacking.", true);

	private final SliderSetting attackDelay = new SliderSetting("Attack delay",
		"Ticks between attacks.", 15, 0, 40, 1,
		ValueDisplay.INTEGER.withSuffix(" ticks"));

	private final SliderSetting critHeight = new SliderSetting("Crit height",
		"Fake fall height used for the smash attack.", 15, 2, 300, 0.5,
		ValueDisplay.DECIMAL);

	private final CheckboxSetting autoHeight = new CheckboxSetting(
		"Auto height", "Limit the fake height to the space above you.", false);

	private final CheckboxSetting targetPlayers = new CheckboxSetting(
		"Target players", "Allow players as targets.", true);

	private final CheckboxSetting targetHostile = new CheckboxSetting(
		"Target hostile mobs", "Allow hostile mobs as targets.", true);

	private final CheckboxSetting throughWalls = new CheckboxSetting(
		"Through walls", "Ignore line of sight.", false);

	private final CheckboxSetting ignoreNamed = new CheckboxSetting(
		"Ignore named mobs", "Never targets mobs with a custom name.", true);

	private final CheckboxSetting ignoreTamed = new CheckboxSetting(
		"Ignore tamed mobs", "Never targets tamed animals.", true);

	private int timer;
	private int switchCooldown;
	private int originalSlot = -1;
	private Entity currentTarget;

	public MaceAuraHack()
	{
		super("MaceAura");
		setCategory(Category.COMBAT);
		addSetting(range);
		addSetting(autoSwitch);
		addSetting(allowFlight);
		addSetting(swingHand);
		addSetting(attackDelay);
		addSetting(critHeight);
		addSetting(autoHeight);
		addSetting(targetPlayers);
		addSetting(targetHostile);
		addSetting(throughWalls);
		addSetting(ignoreNamed);
		addSetting(ignoreTamed);
	}

	@Override
	protected void onEnable()
	{
		timer = 0;
		switchCooldown = 0;
		originalSlot = -1;
		currentTarget = null;
		EVENTS.add(UpdateListener.class, this);
	}

	@Override
	protected void onDisable()
	{
		EVENTS.remove(UpdateListener.class, this);
		if(originalSlot != -1 && autoSwitch.isChecked() && MC.player != null)
		{
			MC.player.getInventory().setSelectedSlot(originalSlot);
			MC.player.connection
				.send(new ServerboundSetCarriedItemPacket(originalSlot));
		}
		originalSlot = -1;
		currentTarget = null;
	}

	@Override
	public void onUpdate()
	{
		if(MC.player == null || MC.level == null)
			return;

		if(timer > 0)
		{
			timer--;
			return;
		}

		if(switchCooldown > 0)
		{
			switchCooldown--;
			return;
		}

		if(!allowFlight.isChecked() && !MC.player.onGround())
			return;

		if(autoSwitch.isChecked())
		{
			if(!checkAndSwapWeapon())
				return;
		}else if(!MC.player.getMainHandItem().is(Items.MACE))
			return;

		currentTarget = findTarget();
		if(currentTarget == null)
			return;

		doMaceCritAttack(currentTarget);
		timer = attackDelay.getValueI();
	}

	private boolean checkAndSwapWeapon()
	{
		if(MC.player.getMainHandItem().is(Items.MACE))
			return true;

		int slot = InventoryUtils.indexOf(stack -> stack.is(Items.MACE), 9);
		if(slot == -1)
			return false;

		originalSlot = MC.player.getInventory().getSelectedSlot();
		MC.player.getInventory().setSelectedSlot(slot);
		MC.player.connection.send(new ServerboundSetCarriedItemPacket(slot));
		switchCooldown = 2;
		return true;
	}

	private Entity findTarget()
	{
		Entity best = null;
		double bestDistance = Double.MAX_VALUE;

		for(Entity entity : MC.level.entitiesForRendering())
		{
			if(!isValidTarget(entity))
				continue;

			double distance = MC.player.distanceToSqr(entity);
			if(distance < bestDistance)
			{
				bestDistance = distance;
				best = entity;
			}
		}

		return best;
	}

	private boolean isValidTarget(Entity entity)
	{
		if(!(entity instanceof LivingEntity living) || !entity.isAlive()
			|| entity == MC.player)
			return false;

		if(MC.player.distanceTo(entity) > range.getValue())
			return false;
		if(!throughWalls.isChecked() && !MC.player.hasLineOfSight(entity))
			return false;

		if(entity instanceof Player player)
		{
			if(!targetPlayers.isChecked() || player.isCreative())
				return false;
			return !WURST.getFriends()
				.contains(player.getScoreboardName());
		}

		if(!targetHostile.isChecked() || !(entity instanceof Enemy))
			return false;
		if(ignoreNamed.isChecked() && living.hasCustomName())
			return false;
		if(ignoreTamed.isChecked() && isTamed(living))
			return false;

		return true;
	}

	private boolean isTamed(LivingEntity living)
	{
		if(living instanceof TamableAnimal tameable && tameable.isTame())
			return true;
		return living instanceof AbstractHorse horse && horse.isTamed();
	}

	private void doMaceCritAttack(Entity target)
	{
		double x = MC.player.getX();
		double y = MC.player.getY();
		double z = MC.player.getZ();
		double height = critHeight.getValue();

		if(autoHeight.isChecked())
		{
			double safeHeight = 0;
			BlockPos.MutableBlockPos checkPos = new BlockPos.MutableBlockPos(
				MC.player.getX(), MC.player.getEyeY(), MC.player.getZ());

			for(int i = 0; i < 256; i++)
			{
				checkPos.setY(checkPos.getY() + 1);
				if(!MC.level.isEmptyBlock(checkPos))
					break;
				safeHeight++;
			}

			height = Math.min(height, safeHeight);
		}

		boolean wasOnGround = MC.player.onGround();
		MC.player.setDeltaMovement(Vec3.ZERO);

		Rotation look = RotationUtils
			.getNeededRotations(target.getBoundingBox().getCenter());
		MC.player.connection
			.send(new ServerboundMovePlayerPacket.Rot(look.yaw(), look.pitch(),
				wasOnGround, MC.player.horizontalCollision));

		sendPosition(x, y, z, true);

		double currentY = y;
		double targetY = y + height;
		while(currentY < targetY)
		{
			currentY = Math.min(currentY + 8.0, targetY);
			sendPosition(x, currentY, z, false);
		}

		double hitY = y + 1.1;
		double droppingY = targetY;
		while(droppingY > hitY + 8.0)
		{
			droppingY -= 8.0;
			sendPosition(x, droppingY, z, false);
		}

		sendPosition(x, hitY, z, false);

		MC.player.connection.send(new ServerboundAttackPacket(target.getId()));
		if(swingHand.isChecked())
			MC.player.connection.send(
				new ServerboundSwingPacket(InteractionHand.MAIN_HAND));

		sendPosition(x, y, z, true);
	}

	private void sendPosition(double x, double y, double z, boolean onGround)
	{
		MC.player.connection.send(new ServerboundMovePlayerPacket.Pos(x, y, z,
			onGround, MC.player.horizontalCollision));
	}
}
