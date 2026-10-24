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

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.wurstclient.Category;
import net.wurstclient.SearchTags;
import net.wurstclient.events.UpdateListener;
import net.wurstclient.hack.Hack;
import net.wurstclient.settings.CheckboxSetting;
import net.wurstclient.settings.SliderSetting;
import net.wurstclient.settings.SliderSetting.ValueDisplay;
import net.wurstclient.util.BlockUtils;

@SearchTags({"tp bow aura", "bow aura", "tp bow", "teleport bow"})
public final class TpBowAuraHack extends Hack implements UpdateListener
{
	private final SliderSetting range = new SliderSetting("Range",
		"Maximum target distance.", 30, 1, 100, 0.5, ValueDisplay.DECIMAL);

	private final CheckboxSetting waitForHurtTime = new CheckboxSetting(
		"Wait for hurt time",
		"Waits until the target's red damage animation ends.", true);

	private final SliderSetting shootDelay = new SliderSetting("Shoot delay",
		"Minimum delay between shots in milliseconds.", 450, 0, 2000, 10,
		ValueDisplay.INTEGER.withSuffix(" ms"));

	private final SliderSetting moveDistance = new SliderSetting(
		"Move distance", "Maximum distance covered by each step packet.", 8, 1,
		20, 0.5, ValueDisplay.DECIMAL);

	private final SliderSetting limitPacket = new SliderSetting(
		"Packet limit", "Maximum packets allowed per shot.", 50, 10, 200, 1,
		ValueDisplay.INTEGER);

	private final CheckboxSetting back = new CheckboxSetting("Blink back",
		"Teleports back to the original position after shooting.", true);

	private final CheckboxSetting attackPlayers = new CheckboxSetting(
		"Attack players", "Include players in the target search.", true);

	private final CheckboxSetting attackMobs = new CheckboxSetting(
		"Attack mobs", "Include non-player living entities.", true);

	private final CheckboxSetting ignoreFriends = new CheckboxSetting(
		"Ignore friends", "Never targets friends.", true);

	private long lastShootTime;
	private Entity target;

	public TpBowAuraHack()
	{
		super("TpBowAura");
		setCategory(Category.COMBAT);
		addSetting(range);
		addSetting(waitForHurtTime);
		addSetting(shootDelay);
		addSetting(moveDistance);
		addSetting(limitPacket);
		addSetting(back);
		addSetting(attackPlayers);
		addSetting(attackMobs);
		addSetting(ignoreFriends);
	}

	@Override
	protected void onEnable()
	{
		target = null;
		lastShootTime = 0;
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

		boolean holdingBow = MC.player.getMainHandItem()
			.getItem() instanceof BowItem
			|| MC.player.getOffhandItem().getItem() instanceof BowItem;

		if(!holdingBow || !MC.player.isUsingItem())
		{
			target = null;
			return;
		}

		if(System.currentTimeMillis() - lastShootTime < shootDelay.getValue())
			return;

		target = findTarget();
		if(target == null)
			return;

		if(waitForHurtTime.isChecked()
			&& ((LivingEntity)target).hurtTime > 1)
			return;

		Vec3 shootPos = findShootPos();
		if(shootPos == null)
			return;

		doTpShoot(shootPos);
	}

	private Entity findTarget()
	{
		Entity best = null;
		double bestDistance = Double.MAX_VALUE;

		for(Entity entity : MC.level.entitiesForRendering())
		{
			if(entity == MC.player || !(entity instanceof LivingEntity)
				|| !entity.isAlive())
				continue;

			if(entity instanceof Player player)
			{
				if(!attackPlayers.isChecked() || player.isCreative()
					|| player.isSpectator())
					continue;

				if(ignoreFriends.isChecked()
					&& WURST.getFriends().isFriend(player))
					continue;
			}else if(!attackMobs.isChecked())
				continue;

			double distance = MC.player.distanceTo(entity);
			if(distance > range.getValue() || distance >= bestDistance)
				continue;

			best = entity;
			bestDistance = distance;
		}

		return best;
	}

	private Vec3 findShootPos()
	{
		List<Vec3> candidates = new ArrayList<>();
		int centerX = (int)Math.floor(target.getX());
		int centerY = (int)Math.floor(target.getY());
		int centerZ = (int)Math.floor(target.getZ());
		Vec3 targetPos = target.position();

		for(int x = centerX - 2; x <= centerX + 2; x++)
		{
			for(int y = centerY - 1; y <= centerY + 2; y++)
			{
				for(int z = centerZ - 2; z <= centerZ + 2; z++)
				{
					Vec3 pos = new Vec3(x + 0.5, y, z + 0.5);

					if(pos.distanceTo(targetPos) > 4)
						continue;

					if(!isSpaceEmpty(pos))
						continue;

					if(canSee(pos.add(0, 1.62, 0),
						target.getBoundingBox().getCenter()))
						candidates.add(pos);
				}
			}
		}

		Vec3 playerPos = MC.player.position();
		candidates.sort(Comparator.comparingDouble(v -> v.distanceTo(playerPos)));
		return candidates.isEmpty() ? null : candidates.get(0);
	}

	private void doTpShoot(Vec3 shootPos)
	{
		Vec3 playerPos = MC.player.position();
		Vec3 vClipStart = null;
		Vec3 vClipEnd = null;

		if(hasClearPath(playerPos, shootPos))
		{
			vClipStart = playerPos;
			vClipEnd = shootPos;
		}else
		{
			double startHeight = Math.max(playerPos.y, shootPos.y) + 1;

			for(double y = startHeight; y < startHeight + 50; y += 1)
			{
				Vec3 testUp = new Vec3(playerPos.x, y, playerPos.z);
				Vec3 testTargetUp = new Vec3(shootPos.x, y, shootPos.z);

				if(isSpaceEmpty(testUp) && isSpaceEmpty(testTargetUp)
					&& hasClearPath(testUp, testTargetUp))
				{
					vClipStart = testUp;
					vClipEnd = testTargetUp;
					break;
				}
			}
		}

		if(vClipStart == null)
			return;

		double totalDistance = playerPos.distanceTo(vClipStart)
			+ vClipStart.distanceTo(vClipEnd) + vClipEnd.distanceTo(shootPos);
		int packetsRequired = (int)Math.ceil(totalDistance
			/ moveDistance.getValue()) + 3;

		if(packetsRequired > limitPacket.getValueI())
			return;

		for(int i = 0; i < packetsRequired; i++)
			sendPos(playerPos.x, playerPos.y, playerPos.z, false);

		if(vClipStart != playerPos)
			sendPos(vClipStart.x, vClipStart.y, vClipStart.z, false);
		if(vClipStart.distanceTo(vClipEnd) > 0.1)
			sendPos(vClipEnd.x, vClipEnd.y, vClipEnd.z, false);

		sendPos(shootPos.x, shootPos.y, shootPos.z, false);

		Vec3 center = target.getBoundingBox().getCenter();
		double dx = center.x - shootPos.x;
		double dy = center.y - (shootPos.y + 1.62);
		double dz = center.z - shootPos.z;
		float yaw =
			(float)(Math.toDegrees(Math.atan2(dz, dx)) - 90);
		float pitch = (float)-Math.toDegrees(
			Math.atan2(dy, Math.sqrt(dx * dx + dz * dz)));

		MC.player.connection.send(new ServerboundMovePlayerPacket.PosRot(
			shootPos.x, shootPos.y, shootPos.z, yaw, pitch, false,
			MC.player.horizontalCollision));

		MC.gameMode.releaseUsingItem(MC.player);
		MC.player.stopUsingItem();
		lastShootTime = System.currentTimeMillis();

		if(back.isChecked())
		{
			if(vClipStart.distanceTo(vClipEnd) > 0.1)
				sendPos(vClipEnd.x, vClipEnd.y, vClipEnd.z, false);
			if(vClipStart != playerPos)
				sendPos(vClipStart.x, vClipStart.y, vClipStart.z, false);

			sendPos(playerPos.x, playerPos.y + 0.01, playerPos.z, false);
			sendPos(playerPos.x, playerPos.y, playerPos.z, true);
			MC.player.setPos(playerPos);
		}else
			MC.player.setPos(shootPos);

		MC.player.fallDistance = 0;
	}

	private boolean isSpaceEmpty(Vec3 pos)
	{
		AABB box = new AABB(pos.x - 0.3, pos.y, pos.z - 0.3, pos.x + 0.3,
			pos.y + 1.8, pos.z + 0.3);
		return MC.level.noCollision(box);
	}

	private boolean hasClearPath(Vec3 start, Vec3 end)
	{
		double distance = start.distanceTo(end);
		int steps = Math.max(1, (int)(distance * 2.5));

		for(int i = 0; i <= steps; i++)
		{
			Vec3 check = start.lerp(end, (double)i / steps);
			if(!isSpaceEmpty(check))
				return false;
		}

		return true;
	}

	private boolean canSee(Vec3 start, Vec3 end)
	{
		return BlockUtils.hasLineOfSight(start, end);
	}

	private void sendPos(double x, double y, double z, boolean onGround)
	{
		MC.player.connection.send(new ServerboundMovePlayerPacket.Pos(x, y, z,
			onGround, MC.player.horizontalCollision));
	}
}
