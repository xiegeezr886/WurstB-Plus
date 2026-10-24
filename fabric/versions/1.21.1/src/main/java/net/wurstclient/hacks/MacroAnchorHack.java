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
import net.minecraft.network.protocol.game.ServerboundUseItemOnPacket;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.wurstclient.Category;
import net.wurstclient.SearchTags;
import net.wurstclient.events.PacketOutputListener;
import net.wurstclient.events.UpdateListener;
import net.wurstclient.hack.Hack;
import net.wurstclient.settings.CheckboxSetting;
import net.wurstclient.settings.SliderSetting;
import net.wurstclient.settings.SliderSetting.ValueDisplay;
import net.wurstclient.util.BlockUtils;
import net.wurstclient.util.InventoryUtils;
import net.wurstclient.util.Rotation;
import net.wurstclient.util.RotationUtils;

@SearchTags({"macro anchor", "anchor macro", "MacroAnchor", "auto anchor"})
public final class MacroAnchorHack extends Hack
	implements UpdateListener, PacketOutputListener
{
	private final SliderSetting actionDelay = new SliderSetting(
		"Action delay", "Ticks between each macro action.", 2, 1, 10, 1,
		ValueDisplay.INTEGER.withSuffix(" ticks"));

	private final CheckboxSetting blockAnchor = new CheckboxSetting(
		"Block anchor", "Place glowstone between you and the anchor first.",
		true);

	private final SliderSetting detonateSlot = new SliderSetting(
		"Detonate slot", "Hotbar slot (1-9) used to detonate the anchor.", 7, 1,
		9, 1, ValueDisplay.INTEGER);

	private State state = State.IDLE;
	private int timer;
	private BlockPos anchorPos;
	private BlockPos blockerPos;
	private int originalSlot = -1;

	public MacroAnchorHack()
	{
		super("MacroAnchor");
		setCategory(Category.COMBAT);
		addSetting(actionDelay);
		addSetting(blockAnchor);
		addSetting(detonateSlot);
	}

	@Override
	protected void onEnable()
	{
		state = State.IDLE;
		timer = 0;
		anchorPos = null;
		blockerPos = null;
		originalSlot = -1;
		EVENTS.add(UpdateListener.class, this);
		EVENTS.add(PacketOutputListener.class, this);
	}

	@Override
	protected void onDisable()
	{
		EVENTS.remove(UpdateListener.class, this);
		EVENTS.remove(PacketOutputListener.class, this);
		state = State.IDLE;
		anchorPos = null;
		blockerPos = null;
	}

	@Override
	public void onSentPacket(PacketOutputEvent event)
	{
		if(state != State.IDLE || MC.player == null || MC.level == null)
			return;
		if(!(event.getPacket() instanceof ServerboundUseItemOnPacket packet))
			return;
		if(!MC.player.getItemInHand(packet.getHand()).is(Items.RESPAWN_ANCHOR))
			return;

		BlockHitResult hitResult = packet.getHitResult();
		BlockPos clicked = hitResult.getBlockPos();
		BlockPos placed = clicked.relative(hitResult.getDirection());

		BlockState clickedState = MC.level.getBlockState(clicked);
		if(clickedState.canBeReplaced())
			placed = clicked;

		startMacro(placed);
	}

	private void startMacro(BlockPos pos)
	{
		anchorPos = pos.immutable();
		originalSlot = MC.player.getInventory().selected;
		timer = actionDelay.getValueI();

		if(blockAnchor.isChecked())
		{
			blockerPos = calculateBlockerPos(anchorPos,
				RotationUtils.getEyesPos());
			if(blockerPos != null && canPlaceBlocker(blockerPos))
			{
				state = State.PLACE_BLOCKER;
				return;
			}
		}

		blockerPos = null;
		state = State.CHARGE;
	}

	@Override
	public void onUpdate()
	{
		if(state == State.IDLE || MC.player == null || MC.level == null)
			return;

		if(timer > 0)
		{
			timer--;
			return;
		}

		switch(state)
		{
			case PLACE_BLOCKER -> handlePlaceBlocker();
			case CHARGE -> handleCharge();
			case DETONATE -> handleDetonate();
			case SWITCH_BACK -> handleSwitchBack();
			default -> state = State.IDLE;
		}
	}

	private void handlePlaceBlocker()
	{
		int slot = InventoryUtils
			.indexOf(stack -> stack.is(Items.GLOWSTONE), 9);
		if(slot == -1)
		{
			abort();
			return;
		}

		selectSlot(slot);
		placeBlockLegit(blockerPos, anchorPos);
		state = State.CHARGE;
		timer = actionDelay.getValueI();
	}

	private void handleCharge()
	{
		int slot = InventoryUtils
			.indexOf(stack -> stack.is(Items.GLOWSTONE), 9);
		if(slot == -1)
		{
			abort();
			return;
		}

		selectSlot(slot);
		interactBlockLegit(anchorPos);
		state = State.DETONATE;
		timer = actionDelay.getValueI();
	}

	private void handleDetonate()
	{
		selectSlot(detonateSlot.getValueI() - 1);
		interactBlockLegit(anchorPos);
		state = State.SWITCH_BACK;
		timer = actionDelay.getValueI();
	}

	private void handleSwitchBack()
	{
		selectSlot(originalSlot);
		state = State.IDLE;
		anchorPos = null;
		blockerPos = null;
	}

	private BlockPos calculateBlockerPos(BlockPos anchor, Vec3 playerEyes)
	{
		double centerX = anchor.getX() + 0.5;
		double centerZ = anchor.getZ() + 0.5;
		double diffX = playerEyes.x - centerX;
		double diffZ = playerEyes.z - centerZ;

		if(Math.abs(diffX) < 0.1 && Math.abs(diffZ) < 0.1)
			return null;

		double angle = Math.atan2(diffZ, diffX);
		long sector = Math.round(angle / (Math.PI / 4.0));

		int offsetX = 0;
		int offsetZ = 0;

		if(sector == 0)
		{
			offsetX = 1;
		}else if(sector == 1)
		{
			offsetX = 1;
			offsetZ = 1;
		}else if(sector == 2)
		{
			offsetZ = 1;
		}else if(sector == 3)
		{
			offsetX = -1;
			offsetZ = 1;
		}else if(sector == 4 || sector == -4)
		{
			offsetX = -1;
		}else if(sector == -3)
		{
			offsetX = -1;
			offsetZ = -1;
		}else if(sector == -2)
		{
			offsetZ = -1;
		}else if(sector == -1)
		{
			offsetX = 1;
			offsetZ = -1;
		}

		return new BlockPos(anchor.getX() + offsetX, anchor.getY(),
			anchor.getZ() + offsetZ);
	}

	private boolean canPlaceBlocker(BlockPos pos)
	{
		return MC.level.getBlockState(pos).canBeReplaced();
	}

	private boolean placeBlockLegit(BlockPos targetPos, BlockPos excludePos)
	{
		Direction[] priorities = {Direction.DOWN, Direction.NORTH,
			Direction.SOUTH, Direction.EAST, Direction.WEST, Direction.UP};

		for(Direction side : priorities)
		{
			BlockPos neighborPos = targetPos.relative(side);
			if(neighborPos.equals(excludePos))
				continue;
			if(!BlockUtils.canBeClicked(neighborPos)
				|| BlockUtils.getState(neighborPos).canBeReplaced())
				continue;

			Direction hitSide = side.getOpposite();
			Vec3 hitVec = Vec3.atCenterOf(neighborPos)
				.add(Vec3.atLowerCornerOf(hitSide.getNormal())
					.scale(0.49));

			face(hitVec);
			IMC.getInteractionManager().rightClickBlock(neighborPos, hitSide,
				hitVec);
			MC.player.swing(InteractionHand.MAIN_HAND);
			return true;
		}

		return false;
	}

	private void interactBlockLegit(BlockPos pos)
	{
		Direction bestSide = null;
		double shortestDistance = Double.MAX_VALUE;
		Vec3 eyesPos = RotationUtils.getEyesPos();

		for(Direction direction : Direction.values())
		{
			Vec3 faceCenter = Vec3.atCenterOf(pos)
				.add(Vec3.atLowerCornerOf(direction.getNormal())
					.scale(0.5));
			BlockHitResult hit = BlockUtils.raycast(eyesPos, faceCenter);

			if(hit.getType() != HitResult.Type.MISS
				&& !hit.getBlockPos().equals(pos))
				continue;

			double distance = eyesPos.distanceToSqr(faceCenter);
			if(distance < shortestDistance)
			{
				shortestDistance = distance;
				bestSide = direction;
			}
		}

		if(bestSide == null)
			bestSide = Direction.UP;

		Vec3 hitVec = Vec3.atCenterOf(pos)
			.add(Vec3.atLowerCornerOf(bestSide.getNormal()).scale(0.49));

		face(hitVec);
		IMC.getInteractionManager().rightClickBlock(pos, bestSide, hitVec);
		MC.player.swing(InteractionHand.MAIN_HAND);
	}

	private void face(Vec3 vec)
	{
		Rotation rotation = RotationUtils.getNeededRotations(vec);
		MC.player.setYRot(rotation.yaw());
		MC.player.setXRot(rotation.pitch());
		MC.player.connection.send(new ServerboundMovePlayerPacket.Rot(
			rotation.yaw(), rotation.pitch(), MC.player.onGround()));
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

	private void abort()
	{
		state = State.IDLE;
		anchorPos = null;
		blockerPos = null;
		if(originalSlot != -1)
			selectSlot(originalSlot);
	}

	private enum State
	{
		IDLE,
		PLACE_BLOCKER,
		CHARGE,
		DETONATE,
		SWITCH_BACK
	}
}
