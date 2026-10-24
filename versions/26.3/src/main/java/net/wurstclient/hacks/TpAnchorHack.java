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
import java.util.Collections;
import java.util.List;
import java.util.function.Predicate;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.network.protocol.game.ServerboundSetCarriedItemPacket;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.wurstclient.Category;
import net.wurstclient.SearchTags;
import net.wurstclient.events.UpdateListener;
import net.wurstclient.hack.Hack;
import net.wurstclient.settings.BlockListSetting;
import net.wurstclient.settings.CheckboxSetting;
import net.wurstclient.settings.SliderSetting;
import net.wurstclient.settings.SliderSetting.ValueDisplay;
import net.wurstclient.util.BlockUtils;
import net.wurstclient.util.InventoryUtils;

@SearchTags({"tp anchor", "anchor aura", "tpaura", "anchor"})
public final class TpAnchorHack extends Hack implements UpdateListener
{
	private final SliderSetting range = new SliderSetting("Range",
		"Maximum target distance.", 49, 1, 100, 1, ValueDisplay.DECIMAL);

	private final SliderSetting attackDelay = new SliderSetting("Attack delay",
		"Ticks to wait between attacks.", 2, 0, 20, 1, ValueDisplay.INTEGER);

	private final CheckboxSetting pauseOnEat = new CheckboxSetting(
		"Pause on eat", "Pauses while using an item.", true);

	private final CheckboxSetting autoRefill = new CheckboxSetting(
		"Auto refill", "Searches the whole inventory for required items.", true);

	private final SliderSetting placeRange = new SliderSetting("Place range",
		"Radius around the target in which anchors may be placed.", 5, 2, 8, 1,
		ValueDisplay.INTEGER);

	private final CheckboxSetting goUp = new CheckboxSetting("VClip",
		"Uses vertical clipping to bypass terrain.", true);

	private final SliderSetting spamPackets = new SliderSetting(
		"Spam packets", "Stationary packets sent before teleporting.", 4, 0, 20,
		1, ValueDisplay.INTEGER);

	private final SliderSetting horizontalOffset = new SliderSetting(
		"Horizontal offset", "Horizontal offset used when returning.", 0.05, 0,
		0.5, 0.01, ValueDisplay.DECIMAL);

	private final SliderSetting yOffset = new SliderSetting("Vertical offset",
		"Vertical offset used when returning.", 0.01, 0, 0.5, 0.01,
		ValueDisplay.DECIMAL);

	private final CheckboxSetting airPlace = new CheckboxSetting("Air place",
		"Allows placing anchors without a supporting block.", true);

	private final CheckboxSetting autoProtect = new CheckboxSetting(
		"Auto protect", "Places a protective block between you and the target.",
		true);

	private final BlockListSetting shieldBlocks = new BlockListSetting(
		"Shield blocks", "Blocks used for auto protect.", "minecraft:obsidian",
		"minecraft:crying_obsidian", "minecraft:netherite_block",
		"minecraft:anvil");

	private final CheckboxSetting attackPlayers = new CheckboxSetting(
		"Attack players", "Include players in the target search.", true);

	private final CheckboxSetting attackMobs = new CheckboxSetting(
		"Attack mobs", "Include non-player living entities.", false);

	private final CheckboxSetting ignoreNamed = new CheckboxSetting(
		"Ignore named", "Never targets entities with a custom name.", false);

	private int delayTimer;
	private Entity target;

	public TpAnchorHack()
	{
		super("TpAnchor");
		setCategory(Category.COMBAT);
		addSetting(range);
		addSetting(attackDelay);
		addSetting(pauseOnEat);
		addSetting(autoRefill);
		addSetting(placeRange);
		addSetting(goUp);
		addSetting(spamPackets);
		addSetting(horizontalOffset);
		addSetting(yOffset);
		addSetting(airPlace);
		addSetting(autoProtect);
		addSetting(shieldBlocks);
		addSetting(attackPlayers);
		addSetting(attackMobs);
		addSetting(ignoreNamed);
	}

	@Override
	protected void onEnable()
	{
		delayTimer = 0;
		target = null;
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

		if(delayTimer > 0)
		{
			delayTimer--;
			return;
		}

		if(pauseOnEat.isChecked() && MC.player.isUsingItem())
			return;

		target = findTarget();
		if(target == null)
			return;

		if(findItem(Items.RESPAWN_ANCHOR) < 0
			|| findItem(Items.GLOWSTONE) < 0)
			return;

		if(autoProtect.isChecked() && findShield() < 0)
			return;

		AttackPos attack = findBestPos();
		if(attack == null)
			return;

		execute(attack);
		delayTimer = attackDelay.getValueI();
	}

	private void execute(AttackPos attack)
	{
		Vec3 start = MC.player.position();
		Vec3 tpPos = attack.tpPos;
		double clipHeight = Math.min(40, Math.max(1, range.getValue()));
		Vec3 upPos = start.add(0, clipHeight, 0);
		Vec3 targetUpPos = tpPos.add(0, clipHeight, 0);
		boolean useVClip = goUp.isChecked();

		for(int i = 0; i < spamPackets.getValueI(); i++)
			sendPos(start, false);

		if(useVClip)
		{
			sendPos(upPos, false);
			sendPos(targetUpPos, false);
		}

		sendPos(tpPos, false);

		if(!selectAnchor())
			return;

		sendPlace(attack.pos);

		if(autoProtect.isChecked())
		{
			BlockPos shieldPos = getShieldPos(attack.pos,
				BlockPos.containing(tpPos));
			if(shieldPos != null
				&& MC.level.getBlockState(shieldPos).canBeReplaced()
				&& selectShield())
				sendPlace(shieldPos);
		}

		if(!selectGlowstone())
			return;

		Direction side = findBestSide(attack.pos);
		sendInteract(attack.pos, side);

		if(!selectAnchor())
			return;

		sendInteract(attack.pos, side);

		if(useVClip)
		{
			sendPos(targetUpPos, false);
			sendPos(upPos, false);
		}

		sendPos(start, false);

		Vec3 finalPos = getOffset(start);
		sendPos(finalPos, false);
		MC.player.setPos(finalPos);
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

				if(WURST.getFriends().isFriend(player))
					continue;
			}else if(!attackMobs.isChecked())
				continue;

			if(ignoreNamed.isChecked() && entity.hasCustomName())
				continue;

			double distance = MC.player.distanceTo(entity);
			if(distance > range.getValue() || distance >= bestDistance)
				continue;

			best = entity;
			bestDistance = distance;
		}

		return best;
	}

	private AttackPos findBestPos()
	{
		BlockPos targetPos = target.blockPosition();
		Vec3 targetCenter = target.position().add(0,
			target.getBbHeight() / 2.0, 0);
		int radius = placeRange.getValueI();
		double radiusSq = radius * radius;

		AttackPos best = null;

		for(int x = -radius; x <= radius; x++)
		{
			for(int y = -radius; y <= radius; y++)
			{
				for(int z = -radius; z <= radius; z++)
				{
					if(x * x + y * y + z * z > radiusSq)
						continue;

					BlockPos pos = targetPos.offset(x, y, z);
					if(!checkPlace(pos))
						continue;

					Vec3 tpPos = findSmartTpSpot(pos);
					if(tpPos == null)
						continue;

					double score = calculateScore(targetCenter, pos);
					if(best == null || score < best.score)
						best = new AttackPos(pos, tpPos, score);
				}
			}
		}

		return best;
	}

	private boolean checkPlace(BlockPos pos)
	{
		if(!MC.level.isInWorldBounds(pos))
			return false;

		if(!MC.level.getBlockState(pos).canBeReplaced())
			return false;

		if(airPlace.isChecked())
			return true;

		for(Direction direction : Direction.values())
			if(!MC.level.getBlockState(pos.relative(direction)).canBeReplaced())
				return true;

		return false;
	}

	private Vec3 findSmartTpSpot(BlockPos anchorPos)
	{
		BlockPos[] offsets = {anchorPos.above(2), anchorPos.north(2),
			anchorPos.south(2), anchorPos.east(2), anchorPos.west(2),
			anchorPos.above(1), anchorPos.north(1), anchorPos.south(1),
			anchorPos.east(1), anchorPos.west(1)};

		for(BlockPos pos : offsets)
		{
			Vec3 vec = new Vec3(pos.getX() + 0.5, pos.getY(),
				pos.getZ() + 0.5);
			if(!invalid(vec))
				return vec;
		}

		return null;
	}

	private double calculateScore(Vec3 targetCenter, BlockPos anchorPos)
	{
		Vec3 anchorVec = Vec3.atCenterOf(anchorPos);
		double score = anchorVec.distanceToSqr(targetCenter);

		if(!isExposed(anchorPos))
			score += 1000;

		int openness = 0;
		for(Direction direction : Direction.values())
			if(MC.level.getBlockState(anchorPos.relative(direction))
				.canBeReplaced())
				openness++;

		return score + (6 - openness) * 2.0;
	}

	private boolean isExposed(BlockPos anchorPos)
	{
		Vec3 start = target.getEyePosition();
		Vec3 end = Vec3.atCenterOf(anchorPos);
		HitResult result = BlockUtils.raycast(start, end);

		return result.getType() == HitResult.Type.MISS
			|| anchorPos.equals(result instanceof BlockHitResult hit
				? hit.getBlockPos() : null);
	}

	private boolean invalid(Vec3 pos)
	{
		if(MC.level == null)
			return true;

		BlockPos blockPos = BlockPos.containing(pos);
		if(!MC.level.isLoaded(blockPos))
			return true;

		AABB box = MC.player.getBoundingBox()
			.move(pos.subtract(MC.player.position()));

		int minX = (int)Math.floor(box.minX);
		int minY = (int)Math.floor(box.minY);
		int minZ = (int)Math.floor(box.minZ);
		int maxX = (int)Math.floor(box.maxX);
		int maxY = (int)Math.floor(box.maxY);
		int maxZ = (int)Math.floor(box.maxZ);

		for(int x = minX; x <= maxX; x++)
		{
			for(int y = minY; y <= maxY; y++)
			{
				for(int z = minZ; z <= maxZ; z++)
				{
					BlockPos check = new BlockPos(x, y, z);
					BlockState state = MC.level.getBlockState(check);

					if(state.is(Blocks.LAVA))
						return true;

					if(!state.getCollisionShape(MC.level, check).isEmpty())
						return true;
				}
			}
		}

		return false;
	}

	private Vec3 getOffset(Vec3 base)
	{
		double horizontal = horizontalOffset.getValue();
		double vertical = yOffset.getValue();

		List<Vec3> offsets = new ArrayList<>(List.of(
			base.add(horizontal, vertical, 0),
			base.add(-horizontal, vertical, 0),
			base.add(0, vertical, horizontal),
			base.add(0, vertical, -horizontal),
			base.add(horizontal, vertical, horizontal),
			base.add(-horizontal, vertical, -horizontal),
			base.add(-horizontal, vertical, horizontal),
			base.add(horizontal, vertical, -horizontal)));
		Collections.shuffle(offsets);

		for(Vec3 pos : offsets)
			if(!invalid(pos))
				return pos;

		return base.add(0, vertical, 0);
	}

	private BlockPos getShieldPos(BlockPos anchor, BlockPos playerPos)
	{
		int dx = playerPos.getX() - anchor.getX();
		int dz = playerPos.getZ() - anchor.getZ();

		if(dx == 0 && dz == 0)
			return anchor.above();

		return anchor.offset(Integer.compare(dx, 0), 0,
			Integer.compare(dz, 0));
	}

	private Direction findBestSide(BlockPos pos)
	{
		for(Direction direction : Direction.values())
			if(MC.level.getBlockState(pos.relative(direction)).canBeReplaced())
				return direction;

		return Direction.UP;
	}

	private int findItem(net.minecraft.world.item.Item item)
	{
		return InventoryUtils.indexOf(item, maxInvSlot());
	}

	private int findShield()
	{
		return InventoryUtils.indexOf(stack -> stack.getItem() instanceof BlockItem
			&& shieldBlocks.contains(((BlockItem)stack.getItem()).getBlock()),
			maxInvSlot());
	}

	private boolean selectAnchor()
	{
		return selectItem(stack -> stack.is(Items.RESPAWN_ANCHOR));
	}

	private boolean selectGlowstone()
	{
		return selectItem(stack -> stack.is(Items.GLOWSTONE));
	}

	private boolean selectShield()
	{
		return selectItem(stack -> stack.getItem() instanceof BlockItem
			&& shieldBlocks.contains(((BlockItem)stack.getItem())
				.getBlock()));
	}

	private boolean selectItem(Predicate<ItemStack> predicate)
	{
		int slot = InventoryUtils.indexOf(predicate, maxInvSlot());
		if(slot < 0)
			return false;

		int selected = MC.player.getInventory().getSelectedSlot();
		if(slot < 9)
			MC.player.getInventory().setSelectedSlot(slot);
		else
			IMC.getInteractionManager().windowClick_SWAP(
				InventoryUtils.toNetworkSlot(slot), selected);

		MC.player.connection.send(new ServerboundSetCarriedItemPacket(
			MC.player.getInventory().getSelectedSlot()));
		return true;
	}

	private int maxInvSlot()
	{
		return autoRefill.isChecked() ? 36 : 9;
	}

	private void sendPos(Vec3 pos, boolean onGround)
	{
		MC.player.connection.send(new ServerboundMovePlayerPacket.Pos(pos.x,
			pos.y, pos.z, onGround, MC.player.horizontalCollision));
	}

	private void sendPlace(BlockPos pos)
	{
		for(Direction direction : Direction.values())
		{
			BlockPos neighbor = pos.relative(direction);
			BlockState state = MC.level.getBlockState(neighbor);

			if(state.canBeReplaced()
				|| state.getCollisionShape(MC.level, neighbor).isEmpty())
				continue;

			Direction face = direction.getOpposite();
			Vec3 hitVec = Vec3.atCenterOf(neighbor)
				.add(Vec3.atLowerCornerOf(face.getUnitVec3i()).scale(0.5));
			sendInteract(neighbor, face, hitVec);
			return;
		}

		if(!airPlace.isChecked() || !MC.level.getBlockState(pos).canBeReplaced())
			return;

		sendInteract(pos, Direction.UP, Vec3.atCenterOf(pos));
	}

	private void sendInteract(BlockPos pos, Direction side)
	{
		sendInteract(pos, side, Vec3.atCenterOf(pos));
	}

	private void sendInteract(BlockPos pos, Direction side, Vec3 hitVec)
	{
		IMC.getInteractionManager().sendPlayerInteractBlockPacket(
			InteractionHand.MAIN_HAND,
			new BlockHitResult(hitVec, side, pos, false));
		MC.player.swing(InteractionHand.MAIN_HAND, net.minecraft.world.item.component.SwingAnimation.DEFAULT, false);
	}

	private static final class AttackPos
	{
		private final BlockPos pos;
		private final Vec3 tpPos;
		private final double score;

		private AttackPos(BlockPos pos, Vec3 tpPos, double score)
		{
			this.pos = pos;
			this.tpPos = tpPos;
			this.score = score;
		}
	}
}
