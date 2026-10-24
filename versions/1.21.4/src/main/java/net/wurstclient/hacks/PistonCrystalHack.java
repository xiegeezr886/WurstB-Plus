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
import net.minecraft.core.Direction;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.network.protocol.game.ServerboundSetCarriedItemPacket;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.boss.enderdragon.EndCrystal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.wurstclient.Category;
import net.wurstclient.SearchTags;
import net.wurstclient.events.UpdateListener;
import net.wurstclient.hack.Hack;
import net.wurstclient.settings.CheckboxSetting;
import net.wurstclient.settings.EnumSetting;
import net.wurstclient.settings.SliderSetting;
import net.wurstclient.settings.SliderSetting.ValueDisplay;
import net.wurstclient.util.BlockUtils;
import net.wurstclient.util.DamageUtils;
import net.wurstclient.util.InventoryUtils;
import net.wurstclient.util.RotationUtils;

@SearchTags({"piston crystal", "PistonCrystal", "auto piston", "crystal piston"})
public final class PistonCrystalHack extends Hack implements UpdateListener
{
	private final SliderSetting targetRange = new SliderSetting(
		"Target range", "Maximum distance to the target.", 6, 1, 6, 0.1,
		ValueDisplay.DECIMAL);

	private final SliderSetting range = new SliderSetting("Place range",
		"Maximum placement distance.", 4.5, 1, 6, 0.1, ValueDisplay.DECIMAL);

	private final SliderSetting delayMs = new SliderSetting("Place delay",
		"Milliseconds between actions.", 50, 0, 500, 1,
		ValueDisplay.INTEGER.withSuffix(" ms"));

	private final SliderSetting breakDelayMs = new SliderSetting(
		"Break delay", "Milliseconds between crystal detonations.", 300, 0,
		500, 1, ValueDisplay.INTEGER.withSuffix(" ms"));

	private final SliderSetting minDamage = new SliderSetting("Min damage",
		"Minimum damage the crystal must deal to the target.", 4, 1, 36, 0.5,
		ValueDisplay.DECIMAL);

	private final SliderSetting maxSelfDamage = new SliderSetting(
		"Max self damage", "Maximum damage accepted on yourself.", 12, 1, 36,
		0.5, ValueDisplay.DECIMAL);

	private final CheckboxSetting pauseWhileUsing = new CheckboxSetting(
		"Pause while using", "Pauses while you are using an item.", true);

	private final CheckboxSetting onlyMain = new CheckboxSetting(
		"Only main hand",
		"Only pause for items used in the main hand.", true);

	private final CheckboxSetting noSuicide = new CheckboxSetting("No suicide",
		"Never use a crystal that could kill you.", true);

	private final CheckboxSetting rotate = new CheckboxSetting("Rotate",
		"Face blocks before interacting with them.", true);

	private final CheckboxSetting inventorySwap = new CheckboxSetting(
		"Inventory swap", "Search the whole inventory for required items.",
		true);

	private final CheckboxSetting mine = new CheckboxSetting("Mine piston",
		"Break extended pistons after detonating.", true);

	private final CheckboxSetting yawDeceive = new CheckboxSetting(
		"Yaw deceive", "Rotate so placed pistons face the target.", true);

	private final EnumSetting<RedstoneMode> redstoneMode = new EnumSetting<>(
		"Redstone mode", "Whether to use a redstone block or torch.",
		RedstoneMode.values(), RedstoneMode.BLOCK);

	private long lastAction;
	private long nextBreakTime;
	private BlockPos crystalPos;
	private BlockPos pistonPos;
	private BlockPos redstonePos;
	private BlockPos lastPiston;
	private BlockPos lastCrystal;
	private BlockPos lastRedstone;
	private Direction face;
	private Player target;

	public PistonCrystalHack()
	{
		super("PistonCrystal");
		setCategory(Category.COMBAT);
		addSetting(targetRange);
		addSetting(range);
		addSetting(delayMs);
		addSetting(breakDelayMs);
		addSetting(minDamage);
		addSetting(maxSelfDamage);
		addSetting(pauseWhileUsing);
		addSetting(onlyMain);
		addSetting(noSuicide);
		addSetting(rotate);
		addSetting(inventorySwap);
		addSetting(mine);
		addSetting(yawDeceive);
		addSetting(redstoneMode);
	}

	@Override
	protected void onEnable()
	{
		lastAction = 0;
		nextBreakTime = 0;
		clearPositions();
		lastPiston = null;
		lastCrystal = null;
		lastRedstone = null;
		target = null;
		EVENTS.add(UpdateListener.class, this);
	}

	@Override
	protected void onDisable()
	{
		EVENTS.remove(UpdateListener.class, this);
		clearPositions();
		lastPiston = null;
		lastCrystal = null;
		lastRedstone = null;
		target = null;
	}

	@Override
	public void onUpdate()
	{
		if(MC.player == null || MC.level == null)
			return;

		long now = System.currentTimeMillis();
		if(now - lastAction < delayMs.getValueI())
			return;

		target = findTarget();
		int redstoneSlot = findRedstoneSlot();
		int crystalSlot = findItemSlot(Items.END_CRYSTAL);
		int pistonSlot = findPistonSlot();

		if(isPausing())
			return;

		if(redstoneSlot == -1 || crystalSlot == -1 || pistonSlot == -1)
		{
			clearPositions();
			return;
		}

		if(crystalPos != null && redstonePos != null && pistonPos != null
			&& (!canPlaceCrystal(crystalPos) || !canPlace(redstonePos)
				|| !canPlace(pistonPos)))
		{
			clearPositions();
		}

		if(target == null)
			return;

		if(crystalPos == null && pistonPos == null && redstonePos == null)
			doPistonCrystal(target);

		if(lastPiston != null && face != null
			&& BlockUtils.getBlock(lastPiston.relative(face)) == Blocks.PISTON_HEAD)
		{
			if(mine.isChecked())
			{
				Direction side = getClickSide(lastPiston);
				MC.gameMode.startDestroyBlock(lastPiston, side);
				MC.gameMode.continueDestroyBlock(lastPiston, side);
				lastPiston = null;
				lastCrystal = null;
				lastRedstone = null;
				return;
			}
		}

		if(pistonPos != null && canPlace(pistonPos))
		{
			placeBlock(Items.PISTON, pistonSlot, pistonPos, face);
			lastPiston = pistonPos;
		}

		if(redstonePos != null && canPlace(redstonePos))
		{
			if(redstoneMode.getSelected() == RedstoneMode.TORCH)
				placeBlock(Items.REDSTONE_TORCH, redstoneSlot, redstonePos,
					null);
			else
				placeBlock(Items.REDSTONE_BLOCK, redstoneSlot, redstonePos,
					null);
			lastRedstone = redstonePos;
		}

		if(crystalPos != null && canPlaceCrystal(crystalPos))
		{
			placeCrystal(crystalPos, crystalSlot);
			lastCrystal = crystalPos;
			lastAction = now;
			return;
		}

		if(now >= nextBreakTime)
		{
			BlockPos head = target.blockPosition().above();
			BlockPos headTwo = target.blockPosition().above(2);
			if(attackCrystal(head) || attackCrystal(headTwo))
			{
				lastAction = now;
				clearPositions();
			}
			nextBreakTime = now + breakDelayMs.getValueI();
		}
	}

	private Player findTarget()
	{
		Player best = null;
		double bestDistance = Double.MAX_VALUE;

		for(Player player : MC.level.players())
		{
			if(player == MC.player || !player.isAlive()
				|| WURST.getFriends().contains(player.getScoreboardName()))
				continue;

			double distance = MC.player.distanceToSqr(player);
			if(distance > targetRange.getValueSq())
				continue;

			if(distance < bestDistance)
			{
				bestDistance = distance;
				best = player;
			}
		}

		return best;
	}

	private boolean isPausing()
	{
		if(!pauseWhileUsing.isChecked())
			return false;

		if(!MC.options.keyUse.isDown() && !MC.player.isUsingItem())
			return false;

		return !onlyMain.isChecked() || !MC.player.isUsingItem()
			|| MC.player.getUsedItemHand() == InteractionHand.MAIN_HAND;
	}

	private int findItemSlot(Item item)
	{
		return InventoryUtils.indexOf(item,
			inventorySwap.isChecked() ? 36 : 9);
	}

	private int findPistonSlot()
	{
		return InventoryUtils.indexOf(
			stack -> stack.is(Items.PISTON)
				|| stack.is(Items.STICKY_PISTON),
			inventorySwap.isChecked() ? 36 : 9);
	}

	private int findRedstoneSlot()
	{
		Item item = redstoneMode.getSelected() == RedstoneMode.TORCH
			? Items.REDSTONE_TORCH : Items.REDSTONE_BLOCK;
		return findItemSlot(item);
	}

	private void doPistonCrystal(Player target)
	{
		BlockPos base = target.blockPosition();

		for(int level = 1; level <= 2; level++)
		{
			BlockPos centerPos = base.above(level);
			Vec3 crystalVec = new Vec3(centerPos.getX() + 0.5,
				centerPos.getY(), centerPos.getZ() + 0.5);
			float targetDamage =
				DamageUtils.calculateDamage(crystalVec, target);
			float selfDamage =
				DamageUtils.calculateDamage(crystalVec, MC.player);

			if(noSuicide.isChecked() && selfDamage > MC.player.getHealth()
				+ MC.player.getAbsorptionAmount())
				return;

			if(targetDamage > minDamage.getValue()
				&& selfDamage <= maxSelfDamage.getValue())
			{
				if(searchPlacement(base, level))
					return;
			}
		}
	}

	private boolean searchPlacement(BlockPos base, int level)
	{
		for(Direction direction : Direction.Plane.HORIZONTAL)
		{
			if(!yawDeceive.isChecked()
				&& direction != MC.player.getDirection())
				continue;

			BlockPos crystalCandidate = base.relative(direction).above(level);
			if(!canPlaceCrystal(crystalCandidate))
				continue;
			if(RotationUtils.getEyesPos()
				.distanceToSqr(Vec3.atCenterOf(crystalCandidate)) > range
					.getValueSq())
				continue;

			for(Direction sideDirection : Direction.Plane.HORIZONTAL)
			{
				if(sideDirection == direction.getOpposite())
					continue;

				BlockPos pistonCandidate =
					crystalCandidate.relative(direction)
						.relative(sideDirection);

				BlockPos behind =
					pistonCandidate.relative(direction.getOpposite());
				if(!MC.level.isEmptyBlock(behind)
					&& !BlockUtils.getState(behind).canBeReplaced())
					continue;

				if(!canPlace(pistonCandidate))
					continue;
				if(RotationUtils.getEyesPos()
					.distanceToSqr(Vec3.atCenterOf(pistonCandidate)) > range
						.getValueSq())
					continue;

				BlockPos redstoneCandidate =
					findRedstonePlacement(pistonCandidate, direction);
				if(redstoneCandidate == null)
					continue;

				face = direction;
				crystalPos = crystalCandidate.immutable();
				pistonPos = pistonCandidate.immutable();
				redstonePos = redstoneCandidate.immutable();
				return true;
			}
		}

		return false;
	}

	private BlockPos findRedstonePlacement(BlockPos piston, Direction pistonFace)
	{
		for(Direction direction : Direction.values())
		{
			if(direction == pistonFace.getOpposite())
				continue;

			BlockPos candidate = piston.relative(direction);
			Block block = BlockUtils.getBlock(candidate);

			boolean correctExisting =
				redstoneMode.getSelected() == RedstoneMode.BLOCK
					? block == Blocks.REDSTONE_BLOCK
					: block == Blocks.REDSTONE_TORCH;
			if(correctExisting)
				return candidate;

			if(!canPlace(candidate))
				continue;
			if(RotationUtils.getEyesPos()
				.distanceToSqr(Vec3.atCenterOf(candidate)) > range.getValueSq())
				continue;

			return candidate;
		}

		return null;
	}

	private void clearPositions()
	{
		crystalPos = null;
		pistonPos = null;
		redstonePos = null;
	}

	private boolean canPlace(BlockPos pos)
	{
		return BlockUtils.getState(pos).canBeReplaced();
	}

	private boolean canPlaceCrystal(BlockPos pos)
	{
		Block baseBlock = BlockUtils.getBlock(pos.below());
		if(baseBlock != Blocks.OBSIDIAN && baseBlock != Blocks.BEDROCK)
			return false;
		if(!BlockUtils.getState(pos).canBeReplaced())
			return false;
		if(!BlockUtils.getState(pos.above()).canBeReplaced())
			return false;

		AABB box = new AABB(pos.getX(), pos.getY(), pos.getZ(),
			pos.getX() + 1, pos.getY() + 2, pos.getZ() + 1);
		return MC.level.getEntities((Entity)null, box, entity -> true)
			.isEmpty();
	}

	private boolean attackCrystal(BlockPos pos)
	{
		AABB box = new AABB(pos).inflate(1.0);
		for(Entity entity : MC.level.getEntities((Entity)null, box,
			entity -> entity instanceof EndCrystal))
		{
			MC.gameMode.attack(MC.player, entity);
			MC.player.swing(InteractionHand.MAIN_HAND);
			return true;
		}

		return false;
	}

	private void placeBlock(Item item, int slot, BlockPos pos, Direction facing)
	{
		if(slot == -1)
			return;

		if(slot > 8)
		{
			InventoryUtils.selectItem(slot);
			return;
		}

		Direction clickSide = null;
		BlockPos neighborPos = null;
		Vec3 hitVec = null;

		for(Direction side : Direction.values())
		{
			if(item == Items.REDSTONE_TORCH && side == Direction.UP)
				continue;

			BlockPos neighbor = pos.relative(side);
			if(!BlockUtils.canBeClicked(neighbor)
				|| BlockUtils.getState(neighbor).canBeReplaced())
				continue;

			Direction hitSide = side.getOpposite();
			Vec3 candidateHit = Vec3.atCenterOf(neighbor)
				.add(Vec3.atLowerCornerOf(hitSide.getUnitVec3i())
					.scale(0.5));

			if(RotationUtils.getEyesPos().distanceToSqr(candidateHit) > range
				.getValueSq())
				continue;

			clickSide = hitSide;
			neighborPos = neighbor;
			hitVec = candidateHit;
			break;
		}

		if(neighborPos == null)
			return;

		int originalSlot = MC.player.getInventory().selected;
		float oldYaw = MC.player.getYRot();
		float oldPitch = MC.player.getXRot();

		selectSlot(slot);
		if(rotate.isChecked())
			face(hitVec);
		if(item == Items.PISTON && yawDeceive.isChecked()
			&& rotate.isChecked() && facing != null)
			aimPiston(facing);

		IMC.getInteractionManager().rightClickBlock(neighborPos, clickSide,
			hitVec);

		if(item == Items.PISTON && yawDeceive.isChecked()
			&& rotate.isChecked())
		{
			MC.player.setYRot(oldYaw);
			MC.player.setXRot(oldPitch);
			sendRotation(oldYaw, oldPitch);
		}

		selectSlot(originalSlot);
	}

	private void placeCrystal(BlockPos pos, int slot)
	{
		if(slot == -1)
			return;

		if(slot > 8)
		{
			InventoryUtils.selectItem(slot);
			return;
		}

		BlockPos base = pos.below();
		if(!BlockUtils.canBeClicked(base))
			return;

		int originalSlot = MC.player.getInventory().selected;
		Vec3 hitVec = Vec3.atCenterOf(base).add(0, 0.5, 0);

		selectSlot(slot);
		if(rotate.isChecked())
			face(hitVec);

		IMC.getInteractionManager().rightClickBlock(base, Direction.UP,
			hitVec);
		selectSlot(originalSlot);
	}

	private void selectSlot(int slot)
	{
		if(slot < 0 || slot > 8 || MC.player == null)
			return;
		if(MC.player.getInventory().selected == slot)
			return;

		MC.player.getInventory().selected = (slot);
		MC.player.connection
			.send(new ServerboundSetCarriedItemPacket(slot));
	}

	private void face(Vec3 vec)
	{
		var rotation = RotationUtils.getNeededRotations(vec);
		MC.player.setYRot(rotation.yaw());
		MC.player.setXRot(rotation.pitch());
		sendRotation(rotation.yaw(), rotation.pitch());
	}

	private void aimPiston(Direction direction)
	{
		float yaw = switch(direction)
		{
			case EAST -> -90.0F;
			case WEST -> 90.0F;
			case NORTH -> 180.0F;
			default -> 0.0F;
		};

		MC.player.setYRot(yaw);
		MC.player.setXRot(5.0F);
		sendRotation(yaw, 5.0F);
	}

	private void sendRotation(float yaw, float pitch)
	{
		MC.player.connection.send(new ServerboundMovePlayerPacket.Rot(yaw,
			pitch, MC.player.onGround(), MC.player.horizontalCollision));
	}

	private Direction getClickSide(BlockPos pos)
	{
		Vec3 eyesPos = RotationUtils.getEyesPos();
		Direction bestSide = Direction.UP;
		double bestDistance = Double.MAX_VALUE;

		for(Direction side : Direction.values())
		{
			if(!BlockUtils.canBeClicked(pos.relative(side)))
				continue;

			Vec3 hitVec = Vec3.atCenterOf(pos)
				.add(Vec3.atLowerCornerOf(side.getUnitVec3i()).scale(0.5));
			double distance = eyesPos.distanceToSqr(hitVec);
			if(distance < bestDistance)
			{
				bestDistance = distance;
				bestSide = side;
			}
		}

		return bestSide;
	}

	private enum RedstoneMode
	{
		TORCH("Torch"),
		BLOCK("Block");

		private final String name;

		RedstoneMode(String name)
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
