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
import net.minecraft.core.BlockPos;
import net.minecraft.network.protocol.game.ClientboundPlayerPositionPacket;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.wurstclient.Category;
import net.wurstclient.SearchTags;
import net.wurstclient.events.PacketInputListener;
import net.wurstclient.events.PacketOutputListener;
import net.wurstclient.events.UpdateListener;
import net.wurstclient.hack.Hack;
import net.wurstclient.settings.CheckboxSetting;
import net.wurstclient.settings.EnumSetting;
import net.wurstclient.settings.SliderSetting;
import net.wurstclient.settings.SliderSetting.ValueDisplay;
import net.wurstclient.util.BlockUtils;
import net.wurstclient.util.RotationUtils;

@SearchTags({"spear kill", "spear lunge", "SpearKill", "spear blink"})
public final class SpearKillHack extends Hack
	implements UpdateListener, PacketOutputListener, PacketInputListener
{
	private final CheckboxSetting disableNoFall = new CheckboxSetting(
		"Disable NoFall", "Temporarily disables NoFall while charging.", true);

	private final EnumSetting<Mode> mode = new EnumSetting<>("Mode",
		"Lunge grants velocity, Blink delays movement packets.", Mode.values(),
		Mode.LUNGE);

	private final SliderSetting maxRange = new SliderSetting("Max range",
		"Maximum distance at which entities can be targeted.", 256, 0, 512, 1,
		ValueDisplay.INTEGER);

	private final CheckboxSetting targetPlayers = new CheckboxSetting(
		"Target players", "Allow players as targets.", true);

	private final CheckboxSetting targetHostile = new CheckboxSetting(
		"Target hostile mobs", "Allow hostile mobs as targets.", true);

	private final CheckboxSetting ignoreFriends = new CheckboxSetting(
		"Ignore friends", "Never targets friends.", true);

	private final CheckboxSetting blinkLunge = new CheckboxSetting(
		"Blink + Lunge", "Combine Blink with a velocity boost.", false);

	private final SliderSetting blinkLungeStrength = new SliderSetting(
		"Blink lunge strength", "Velocity applied to the target direction.", 1,
		0.1, 2, 0.05, ValueDisplay.DECIMAL);

	private final SliderSetting blinkLungeTicks = new SliderSetting(
		"Blink lunge delay", "Ticks to charge before lunging.", 15, 1, 30, 1,
		ValueDisplay.INTEGER.withSuffix(" ticks"));

	private final SliderSetting flushRange = new SliderSetting("Flush range",
		"Distance at which queued packets are flushed.", 3, 1, 10, 0.1,
		ValueDisplay.DECIMAL);

	private final SliderSetting maxFlushRange = new SliderSetting(
		"Forced flush range", "Distance that forces a packet flush.", 9.5, 1,
		20, 0.1, ValueDisplay.DECIMAL);

	private final CheckboxSetting blinkAimbot = new CheckboxSetting(
		"Blink aimbot", "Aim at the target while blinking.", true);

	private final SliderSetting blinkDistanceBoost = new SliderSetting(
		"Distance boost", "Extra blocks added to the blink start position.", 0,
		0, 10, 0.1, ValueDisplay.DECIMAL);

	private final EnumSetting<LungeDirection> lungeDirection = new EnumSetting<>(
		"Lunge direction", "How the lunge direction is calculated.",
		LungeDirection.values(), LungeDirection.DIRECTION_BASED);

	private final SliderSetting aboveHeight = new SliderSetting("Above height",
		"Blocks above the target used for the from-above lunge.", 10, 5, 50,
		0.5, ValueDisplay.DECIMAL);

	private final SliderSetting aboveTriggerDistance = new SliderSetting(
		"Above trigger distance",
		"Distance at which the lunge switches from above to the target.", 3, 1,
		10, 0.1, ValueDisplay.DECIMAL);

	private final CheckboxSetting validateDistance = new CheckboxSetting(
		"Validate path",
		"Check the area around the from-above position for hazards.", true);

	private final SliderSetting lungeStrength = new SliderSetting(
		"Lunge strength", "Velocity applied towards the target.", 5, 1, 10, 0.1,
		ValueDisplay.DECIMAL);

	private final CheckboxSetting stopAtTarget = new CheckboxSetting(
		"Stop at target", "Stop lunging once the target is reached.", true);

	private final SliderSetting stopDistance = new SliderSetting(
		"Stop distance", "Bounding box distance at which lunging stops.", 2, 0,
		10, 0.1, ValueDisplay.DECIMAL);

	private final SliderSetting readyTicksModifier = new SliderSetting(
		"Ready ticks modifier",
		"Percentage of the spear charge time to wait before lunging.", 100, 0,
		100, 1, ValueDisplay.INTEGER.withSuffix("%"));

	private final List<ServerboundMovePlayerPacket> packets = new ArrayList<>();
	private boolean blinking;
	private boolean flushing;
	private boolean currentlyCharging;
	private boolean wasCharging;
	private boolean wasApproaching;
	private boolean firstPhase;
	private boolean noFallToggled;
	private boolean wasNoFallEnabled;
	private Vec3 startPos;
	private Vec3 aboveTargetPos;
	private double lastTargetDistance = Double.MAX_VALUE;
	private Entity killTarget;
	private int blinkChargeTicks;
	private int flushCooldown;

	public SpearKillHack()
	{
		super("SpearKill");
		setCategory(Category.COMBAT);
		addSetting(disableNoFall);
		addSetting(mode);
		addSetting(maxRange);
		addSetting(targetPlayers);
		addSetting(targetHostile);
		addSetting(ignoreFriends);
		addSetting(blinkLunge);
		addSetting(blinkLungeStrength);
		addSetting(blinkLungeTicks);
		addSetting(flushRange);
		addSetting(maxFlushRange);
		addSetting(blinkAimbot);
		addSetting(blinkDistanceBoost);
		addSetting(lungeDirection);
		addSetting(aboveHeight);
		addSetting(aboveTriggerDistance);
		addSetting(validateDistance);
		addSetting(lungeStrength);
		addSetting(stopAtTarget);
		addSetting(stopDistance);
		addSetting(readyTicksModifier);
	}

	@Override
	protected void onEnable()
	{
		resetState();
		EVENTS.add(UpdateListener.class, this);
		EVENTS.add(PacketOutputListener.class, this);
		EVENTS.add(PacketInputListener.class, this);
	}

	@Override
	protected void onDisable()
	{
		flushPackets();
		EVENTS.remove(UpdateListener.class, this);
		EVENTS.remove(PacketOutputListener.class, this);
		EVENTS.remove(PacketInputListener.class, this);
		resetState();
	}

	private void resetState()
	{
		packets.clear();
		blinking = false;
		flushing = false;
		currentlyCharging = false;
		wasCharging = false;
		wasApproaching = false;
		firstPhase = false;
		startPos = null;
		aboveTargetPos = null;
		lastTargetDistance = Double.MAX_VALUE;
		killTarget = null;
		blinkChargeTicks = 0;
		flushCooldown = 0;

		if(noFallToggled && wasNoFallEnabled)
			WURST.getHax().noFallHack.setEnabled(true);
		noFallToggled = false;
		wasNoFallEnabled = false;
	}

	@Override
	public void onUpdate()
	{
		if(MC.player == null || MC.level == null)
			return;

		currentlyCharging = isUsingSpear();
		updateNoFall();

		if(mode.getSelected() == Mode.LUNGE)
		{
			if(currentlyCharging)
			{
				if(killTarget == null || !killTarget.isAlive()
					|| !canSeeTarget(killTarget))
					killTarget = target();

				if(killTarget != null && killTarget.isAlive()
					&& isValidTarget(killTarget))
					lunge();
			}else
			{
				killTarget = null;
				firstPhase = false;
				aboveTargetPos = null;
			}
			return;
		}

		updateBlink();
	}

	private void updateNoFall()
	{
		if(!disableNoFall.isChecked())
			return;

		boolean enabled = WURST.getHax().noFallHack.isEnabled();

		if(currentlyCharging && !noFallToggled)
		{
			wasNoFallEnabled = enabled;
			if(enabled)
			{
				WURST.getHax().noFallHack.setEnabled(false);
				noFallToggled = true;
			}
		}else if(!currentlyCharging && noFallToggled)
		{
			if(wasNoFallEnabled)
				WURST.getHax().noFallHack.setEnabled(true);
			noFallToggled = false;
		}
	}

	private void updateBlink()
	{
		if(currentlyCharging)
		{
			blinkChargeTicks++;
			if(killTarget == null || !killTarget.isAlive()
				|| !canSeeTarget(killTarget))
			{
				killTarget = target();
				lastTargetDistance = killTarget != null
					? MC.player.distanceTo(killTarget) : Double.MAX_VALUE;
				wasApproaching = false;
			}
		}else
		{
			blinkChargeTicks = 0;
			killTarget = null;
			lastTargetDistance = Double.MAX_VALUE;
			wasApproaching = false;
		}

		if(flushCooldown > 0)
			flushCooldown--;

		if(currentlyCharging && !wasCharging)
			startBlink();

		if(!currentlyCharging && wasCharging && blinking)
		{
			if(killTarget != null)
				rotateToTarget(killTarget);
			flushPackets();
			blinking = false;
			startPos = null;
		}

		wasCharging = currentlyCharging;

		if(blinking && killTarget != null && currentlyCharging)
		{
			double currentDistance = MC.player.distanceTo(killTarget);
			boolean approaching = currentDistance < lastTargetDistance;
			boolean shouldFlush = false;

			if(currentDistance <= flushRange.getValue())
				shouldFlush = true;
			else if(wasApproaching && !approaching && currentDistance < 8.0)
				shouldFlush = true;
			else if(!blinkLunge.isChecked() && startPos != null
				&& MC.player.position().distanceTo(startPos) >= maxFlushRange
					.getValue())
			{
				flushPackets();
				startBlink();
			}

			if(shouldFlush)
			{
				rotateToTarget(killTarget);
				flushPackets();

				if(blinkLunge.isChecked())
					flushCooldown = blinkLungeTicks.getValueI();

				blinking = true;
				startPos = MC.player.position();
				packets.clear();
				lastTargetDistance = MC.player.distanceTo(killTarget);
				wasApproaching = false;
			}else
			{
				lastTargetDistance = currentDistance;
				wasApproaching = approaching;
			}
		}

		if(currentlyCharging && blinkLunge.isChecked() && killTarget != null
			&& flushCooldown == 0 && blinkChargeTicks >= blinkLungeTicks
				.getValueI())
		{
			rotateToTarget(killTarget);
			Vec3 direction = Vec3.directionFromRotation(MC.player.getXRot(),
				MC.player.getYRot());
			MC.player.setSprinting(true);
			MC.player.setDeltaMovement(
				direction.scale(blinkLungeStrength.getValue()));
		}
	}

	private void lunge()
	{
		Item handItem = MC.player.getUsedItemHand() == InteractionHand.MAIN_HAND
			? MC.player.getMainHandItem().getItem()
			: MC.player.getOffhandItem().getItem();
		int readyTicks = getReadyTicks(handItem);

		rotateToTarget(killTarget);

		if(MC.player.getTicksUsingItem() <= readyTicks)
			return;

		AABB playerBox =
			MC.player.getBoundingBox().inflate(stopDistance.getValue());
		if(playerBox.intersects(killTarget.getBoundingBox()))
		{
			if(stopAtTarget.isChecked())
			{
				killTarget = null;
				MC.player.setDeltaMovement(Vec3.ZERO);
				MC.player.setSprinting(false);
			}
			firstPhase = false;
			aboveTargetPos = null;
			return;
		}

		Vec3 direction;
		switch(lungeDirection.getSelected())
		{
			case DIRECTION_BASED -> direction = killTarget.getBoundingBox()
				.getCenter().subtract(MC.player.position()).normalize();

			case FROM_ABOVE -> direction = fromAboveDirection(false);

			default -> direction = fromAboveDirection(true);
		}

		MC.player.setSprinting(true);
		MC.player.setDeltaMovement(
			direction.scale(lungeStrength.getValue()));
	}

	private Vec3 fromAboveDirection(boolean validate)
	{
		Vec3 targetCenter = killTarget.getBoundingBox().getCenter();
		Vec3 playerPos = MC.player.position();

		if(!firstPhase || aboveTargetPos == null)
		{
			aboveTargetPos = new Vec3(targetCenter.x,
				targetCenter.y + aboveHeight.getValue(), targetCenter.z);
			firstPhase = true;
		}

		double distanceToAbove = playerPos.distanceTo(aboveTargetPos);
		boolean validPath =
			!validate || isFromAbovePathValid(aboveTargetPos, killTarget);

		if(!validPath
			|| distanceToAbove < aboveTriggerDistance.getValue())
		{
			firstPhase = false;
			return targetCenter.subtract(playerPos).normalize();
		}

		return aboveTargetPos.subtract(playerPos).normalize();
	}

	private boolean isFromAbovePathValid(Vec3 abovePos, Entity target)
	{
		if(MC.level == null || abovePos == null)
			return false;

		Vec3 targetCenter = target.getBoundingBox().getCenter();

		if(isInvalid(abovePos))
			return false;

		if(validateDistance.isChecked())
		{
			double checkDistance = aboveTriggerDistance.getValue();
			int radius = (int)checkDistance;

			for(int x = -radius; x <= radius; x++)
				for(int y = -radius; y <= radius; y++)
					for(int z = -radius; z <= radius; z++)
					{
						Vec3 testPos = abovePos.add(x, y, z);
						if(testPos.distanceTo(abovePos) <= checkDistance
							&& isInvalid(testPos))
							return false;
					}
		}

		int pathSteps =
			Math.max(10, (int)(abovePos.distanceTo(targetCenter) * 2.5));
		for(int i = 1; i < pathSteps; i++)
		{
			double progress = i / (double)pathSteps;
			Vec3 sample = abovePos.lerp(targetCenter, progress);
			if(isInvalid(sample))
				return false;
		}

		return true;
	}

	private boolean isInvalid(Vec3 pos)
	{
		if(MC.level == null)
			return true;

		if(pos.y < MC.level.getMinBuildHeight() || pos.y >= MC.level.getMaxBuildHeight())
			return true;

		BlockPos floored = BlockPos.containing(pos);
		Vec3 delta = pos.subtract(MC.player.position());
		AABB box = MC.player.getBoundingBox().move(delta);
		BlockPos.MutableBlockPos mutable = new BlockPos.MutableBlockPos();

		for(int x = -1; x <= 1; x++)
			for(int y = -1; y <= 1; y++)
				for(int z = -1; z <= 1; z++)
				{
					mutable.set(floored.getX() + x, floored.getY() + y,
						floored.getZ() + z);
					BlockState state = MC.level.getBlockState(mutable);
					if(state.is(Blocks.LAVA) || state.is(Blocks.FIRE)
						|| state.is(Blocks.SOUL_FIRE)
						|| state.is(Blocks.MAGMA_BLOCK)
						|| state.is(Blocks.CAMPFIRE)
						|| state.is(Blocks.SWEET_BERRY_BUSH)
						|| state.is(Blocks.POWDER_SNOW))
						return true;
				}

		for(Entity entity : MC.level.getEntities(MC.player, box,
			entity -> entity != MC.player
				&& entity.canBeCollidedWith()))
			return true;

		return MC.level.getBlockCollisions(MC.player, box).iterator().hasNext();
	}

	private void rotateToTarget(Entity target)
	{
		if(MC.player == null || target == null)
			return;

		Vec3 eyesPos = MC.player.getEyePosition();
		AABB box = target.getBoundingBox();
		double centerY = box.getCenter().y;
		double heightDifference = centerY - eyesPos.y;
		double boxHeight = box.maxY - box.minY;
		double targetY;

		if(Math.abs(heightDifference) < 1.0)
			targetY = centerY;
		else if(heightDifference > 0)
			targetY = centerY
				- boxHeight * Math.min(heightDifference / 5.0, 0.4);
		else
			targetY = centerY
				+ boxHeight * Math.min(-heightDifference / 5.0, 0.4);

		Vec3 targetPos = new Vec3(box.getCenter().x, targetY,
			box.getCenter().z);
		Vec3 toTarget = targetPos.subtract(eyesPos).normalize();
		float yaw = (float)(Math.toDegrees(Math.atan2(toTarget.z, toTarget.x))
			- 90.0);
		float pitch = (float)-Math.toDegrees(Math.asin(toTarget.y));

		MC.player.setYRot(yaw);
		MC.player.setYHeadRot(yaw);
		MC.player.setXRot(pitch);
	}

	@Override
	public void onSentPacket(PacketOutputEvent event)
	{
		if(MC.player == null || MC.level == null)
			return;
		if(mode.getSelected() != Mode.BLINK || !blinking || flushing)
			return;
		if(!(event.getPacket() instanceof ServerboundMovePlayerPacket packet))
			return;

		event.cancel();
		packets.add(packet);
	}

	@Override
	public void onReceivedPacket(PacketInputEvent event)
	{
		if(MC.player == null || MC.level == null)
			return;
		if(!(event.getPacket() instanceof ClientboundPlayerPositionPacket))
			return;
		if(mode.getSelected() != Mode.BLINK || !blinking)
			return;

		packets.clear();
		startPos = MC.player.position();
		lastTargetDistance = killTarget != null
			? MC.player.distanceTo(killTarget) : Double.MAX_VALUE;
		wasApproaching = false;
	}

	private void startBlink()
	{
		blinking = true;
		startPos = MC.player.position();
		packets.clear();
		lastTargetDistance = killTarget != null
			? MC.player.distanceTo(killTarget) : Double.MAX_VALUE;
		wasApproaching = false;
	}

	private void flushPackets()
	{
		if(MC.player == null || MC.player.connection == null)
			return;
		if(packets.isEmpty())
			return;

		Vec3 currentPos = MC.player.position();
		double distance = startPos != null ? startPos.distanceTo(currentPos) : 0;

		if(distance < flushRange.getValue())
		{
			packets.clear();
			return;
		}

		flushing = true;
		try
		{
			Vec3 sendStartPos = startPos;
			double boost = blinkDistanceBoost.getValue();

			if(boost > 0 && startPos != null)
			{
				Vec3 direction = currentPos.subtract(startPos);
				Vec3 horizontalDir = new Vec3(direction.x, 0, direction.z);

				if(horizontalDir.length() > 0.01)
				{
					horizontalDir = horizontalDir.normalize();
					Vec3 boostedStart =
						startPos.subtract(horizontalDir.scale(boost));
					BlockHitResult hit =
						BlockUtils.raycast(startPos, boostedStart);

					if(hit.getType() == HitResult.Type.MISS)
						sendStartPos = boostedStart;
					else
						sendStartPos = hit.getLocation()
							.add(horizontalDir.scale(0.5));
				}
			}

			if(sendStartPos != null)
				MC.player.connection.send(
					new ServerboundMovePlayerPacket.PosRot(sendStartPos.x,
						sendStartPos.y, sendStartPos.z, MC.player.getYRot(),
						MC.player.getXRot(), false));

			MC.player.connection.send(
				new ServerboundMovePlayerPacket.PosRot(currentPos.x,
					currentPos.y, currentPos.z, MC.player.getYRot(),
					MC.player.getXRot(), MC.player.onGround()));

			packets.clear();
		}finally
		{
			flushing = false;
		}
	}

	private Entity target()
	{
		if(MC.player == null || MC.level == null)
			return null;

		if(MC.hitResult instanceof EntityHitResult hit
			&& isValidTarget(hit.getEntity()))
			return hit.getEntity();

		double maxDistance = maxRange.getValue();
		Vec3 eyePos = MC.player.getEyePosition();
		Vec3 lookVec = MC.player.getLookAngle();
		BlockHitResult blockHit = BlockUtils.raycast(eyePos,
			eyePos.add(lookVec.scale(maxDistance)));
		double rayLength = blockHit.getType() == HitResult.Type.MISS
			? maxDistance : eyePos.distanceTo(blockHit.getLocation());

		AABB searchBox = MC.player.getBoundingBox()
			.expandTowards(lookVec.scale(rayLength)).inflate(1.0);
		List<Entity> candidates = MC.level.getEntities(MC.player, searchBox,
			entity -> entity instanceof LivingEntity && entity.isAlive()
				&& entity != MC.player);

		candidates.sort(Comparator.comparingDouble(entity -> eyePos
			.distanceToSqr(entity.getBoundingBox().getCenter())));

		for(Entity entity : candidates)
		{
			double distance =
				eyePos.distanceTo(entity.getBoundingBox().getCenter());
			if(distance > maxDistance)
				break;
			if(!isValidTarget(entity) || !canSeeTarget(entity))
				continue;

			Vec3 toEntity = entity.getBoundingBox().getCenter()
				.subtract(eyePos).normalize();
			if(lookVec.dot(toEntity) > 0.999)
				return entity;
		}

		return null;
	}

	private boolean canSeeTarget(Entity target)
	{
		if(MC.player == null || MC.level == null)
			return false;

		Vec3 eyesPos = MC.player.getEyePosition();
		Vec3 targetCenter = target.getBoundingBox().getCenter();
		BlockHitResult result =
			BlockUtils.raycast(eyesPos, targetCenter);

		if(result.getType() == HitResult.Type.MISS)
			return true;

		return eyesPos.distanceTo(result.getLocation())
			>= eyesPos.distanceTo(targetCenter) - 0.5;
	}

	private boolean isValidTarget(Entity entity)
	{
		if(entity == null)
			return false;

		if(entity instanceof Player player)
		{
			if(!targetPlayers.isChecked())
				return false;
			return !ignoreFriends.isChecked() || !WURST.getFriends()
				.contains(player.getScoreboardName());
		}

		return targetHostile.isChecked() && entity instanceof Enemy;
	}

	private boolean isUsingSpear()
	{
		if(MC.player == null)
			return false;

		ItemStack stack = MC.player.isUsingItem()
			? MC.player.getUseItem() : MC.player.getMainHandItem();
		return stack.getItem().toString().toLowerCase().contains("spear");
	}

	private int getReadyTicks(Item item)
	{
		String name = item.toString().toLowerCase();
		int value = 14;

		if(name.contains("wooden"))
			value = 14;
		else if(name.contains("stone") || name.contains("golden"))
			value = 13;
		else if(name.contains("copper"))
			value = 12;
		else if(name.contains("iron"))
			value = 11;
		else if(name.contains("diamond"))
			value = 9;
		else if(name.contains("netherite"))
			value = 7;

		return Math.round(
			value * (readyTicksModifier.getValueI() / 100.0F));
	}

	private enum Mode
	{
		LUNGE("Lunge"),
		BLINK("Blink");

		private final String name;

		Mode(String name)
		{
			this.name = name;
		}

		@Override
		public String toString()
		{
			return name;
		}
	}

	private enum LungeDirection
	{
		DIRECTION_BASED("Direction based"),
		FROM_ABOVE("From above"),
		AUTO_FROM_ABOVE("Auto from above");

		private final String name;

		LungeDirection(String name)
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
