/*
 * Copyright (c) 2014-2025 Wurst-Imperium and contributors.
 * Copyright (c) 2025 Penguin
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 */
package net.wurstclient.hacks;

import net.minecraft.core.BlockPos;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.protocol.game.ServerboundSetCarriedItemPacket;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.FallingBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.wurstclient.Category;
import net.wurstclient.SearchTags;
import net.wurstclient.events.UpdateListener;
import net.wurstclient.hack.Hack;
import net.wurstclient.settings.CheckboxSetting;
import net.wurstclient.settings.EnumSetting;
import net.wurstclient.settings.SliderSetting;
import net.wurstclient.settings.SliderSetting.ValueDisplay;
import net.wurstclient.settings.SwingHandSetting;
import net.wurstclient.settings.SwingHandSetting.SwingHand;
import net.wurstclient.mixinterface.IClientPlayerInteractionManager;
import net.wurstclient.util.BlockUtils;
import net.wurstclient.util.PlacementPlan;
import net.wurstclient.util.RotationQueue;
import net.wurstclient.util.ScaffoldPlacementPlanner;

@SearchTags({"scaffold walk", "BridgeWalk", "bridge walk", "AutoBridge",
	"auto bridge", "tower"})
public final class ScaffoldWalkHack extends Hack implements UpdateListener
{
	private final EnumSetting<Mode> mode = new EnumSetting<>("Mode",
		"\u00a7lNormal\u00a7r - Standard scaffold placement.\n"
			+ "\u00a7lTower\u00a7r - Hold jump key to tower up.",
		Mode.values(), Mode.NORMAL);

	private final SliderSetting placeDelay = new SliderSetting("Place delay",
		"Delay between block placements in ticks.\nLower = faster.",
		1, 0, 5, 1, ValueDisplay.INTEGER);

	private final CheckboxSetting silentSwap = new CheckboxSetting(
		"Silent swap",
		"Temporarily changes the server slot while preserving the visible slot.",
		true);

	private final SliderSetting searchRange = new SliderSetting("Search range",
		"How many hotbar slots to search for blocks.",
		9, 1, 9, 1, ValueDisplay.INTEGER);

	private final SliderSetting prediction = new SliderSetting("Prediction",
		"Plans placements around the player's predicted horizontal position.",
		1, 0, 2, 1, ValueDisplay.INTEGER.withSuffix(" ticks"));

	private final CheckboxSetting requireLineOfSight = new CheckboxSetting(
		"Require line of sight",
		"Only uses support faces that are visible from the player's eyes.",
		true);

	private final CheckboxSetting safeWalk = new CheckboxSetting("Safe walk",
		"Prevents walking off the scaffold edge in Normal mode.", true);

	private final SliderSetting towerMotion = new SliderSetting("Tower motion",
		"Upward velocity used by Tower mode.", 0.42, 0.2, 1, 0.01,
		ValueDisplay.DECIMAL);

	private final SwingHandSetting swingHand =
		new SwingHandSetting(this, SwingHand.CLIENT);

	private int oldSlot = -1;
	private int placementCooldown;
	private PlacementPlan currentPlan;
	private RotationQueue rotationQueue;

	public ScaffoldWalkHack()
	{
		super("ScaffoldWalk");
		setCategory(Category.BLOCKS);
		addSetting(mode);
		addSetting(placeDelay);
		addSetting(silentSwap);
		addSetting(searchRange);
		addSetting(prediction);
		addSetting(requireLineOfSight);
		addSetting(safeWalk);
		addSetting(towerMotion);
		addSetting(swingHand);
	}

	@Override
	public String getRenderName()
	{
		return getName() + " [" + mode.getSelected() + "]";
	}

	@Override
	protected void onEnable()
	{
		oldSlot = -1;
		placementCooldown = 0;
		currentPlan = null;
		rotationQueue = new RotationQueue(
			RotationQueue.Priority.BLOCK_PLACEMENT);
		rotationQueue.start();
		EVENTS.add(UpdateListener.class, this);
	}

	@Override
	protected void onDisable()
	{
		EVENTS.remove(UpdateListener.class, this);
		resetSlot();
		currentPlan = null;
		if(rotationQueue != null)
			rotationQueue.stop();
		rotationQueue = null;
	}

	@Override
	public void onUpdate()
	{
		LocalPlayer player = MC.player;
		if(player == null)
		{
			oldSlot = -1;
			return;
		}
		if(MC.level == null || MC.gameMode == null || player.isSpectator()
			|| player.isFallFlying())
		{
			resetSlot();
			return;
		}

		boolean towerActive = mode.getSelected() == Mode.TOWER
			&& MC.options.keyJump.isDown();
		if(towerActive && findBlockSlot() != -1)
			updateTowerMotion(player);

		if(placementCooldown > 0)
		{
			placementCooldown--;
			return;
		}

		boolean placed = towerActive ? doTower() : doScaffold();
		if(placed)
			placementCooldown = placeDelay.getValueI();
	}

	private boolean doScaffold()
	{
		Vec3 movement = MC.player.getDeltaMovement();
		int ticks = prediction.getValueI();
		Vec3 predictedPosition = MC.player.position().add(movement.x * ticks,
			0, movement.z * ticks);
		PlacementPlan plan = ScaffoldPlacementPlanner.find(predictedPosition,
			currentPlan, requireLineOfSight.isChecked());
		if(plan == null)
		{
			currentPlan = null;
			return false;
		}

		int newSlot = findBlockSlot();
		if(newSlot == -1)
			return false;

		swapToSlot(newSlot);
		try
		{
			return placePlan(plan);
		}finally
		{
			resetSlot();
		}
	}

	private boolean doTower()
	{
		BlockPos towerPos =
			BlockPos.containing(MC.player.position()).below();
		PlacementPlan plan = ScaffoldPlacementPlanner.findAt(towerPos,
			currentPlan, requireLineOfSight.isChecked());
		if(plan == null)
		{
			currentPlan = null;
			return false;
		}

		int newSlot = findBlockSlot();
		if(newSlot == -1)
			return false;

		swapToSlot(newSlot);
		try
		{
			return placePlan(plan);
		}finally
		{
			resetSlot();
		}
	}

	private void updateTowerMotion(LocalPlayer player)
	{
		Vec3 movement = player.getDeltaMovement();
		if(player.onGround())
			player.setDeltaMovement(movement.x, towerMotion.getValue(), movement.z);
		else if(movement.y < 0 && BlockUtils
			.getState(BlockPos.containing(player.position()).below())
			.canBeReplaced())
			player.setDeltaMovement(movement.x, -0.15, movement.z);
	}

	private int findBlockSlot()
	{
		int maxSlot = (int)searchRange.getValueI();
		int bestSlot = -1;
		int bestCount = -1;
		for(int i = 0; i < maxSlot; i++)
		{
			ItemStack stack = MC.player.getInventory().getItem(i);
			if(stack.isEmpty() || !(stack.getItem() instanceof BlockItem))
				continue;

			Block block = Block.byItem(stack.getItem());
			BlockState state = block.defaultBlockState();
			if(!state.isCollisionShapeFullBlock(EmptyBlockGetter.INSTANCE, BlockPos.ZERO))
				continue;

			if(block instanceof FallingBlock && FallingBlock
				.isFree(BlockUtils.getState(
					BlockPos.containing(MC.player.position()).below(2))))
				continue;

			if(stack.getCount() > bestCount)
			{
				bestSlot = i;
				bestCount = stack.getCount();
			}
		}
		return bestSlot;
	}

	private void swapToSlot(int newSlot)
	{
		if(newSlot == MC.player.getInventory().getSelectedSlot())
		{
			oldSlot = -1;
			return;
		}

		if(silentSwap.isChecked())
		{
			oldSlot = MC.player.getInventory().getSelectedSlot();
			MC.player.getInventory().setSelectedSlot(newSlot);
		}
		else
			MC.player.getInventory().setSelectedSlot(newSlot);
	}

	private void resetSlot()
	{
		if(oldSlot == -1)
			return;
		if(MC.player == null)
		{
			oldSlot = -1;
			return;
		}

		MC.player.getInventory().setSelectedSlot(oldSlot);
		MC.player.connection.send(new ServerboundSetCarriedItemPacket(oldSlot));
		oldSlot = -1;
	}

	private boolean placePlan(PlacementPlan plan)
	{
		if(!BlockUtils.getState(plan.target()).canBeReplaced())
		{
			currentPlan = null;
			return false;
		}

		rotationQueue.setRotation(plan.rotation());
		InteractionResult result = ((IClientPlayerInteractionManager)IMC
			.getInteractionManager()).rightClickBlock(plan.neighbor(),
				plan.side(), plan.hitVec());
		if(!result.consumesAction())
			return false;
		if(result instanceof InteractionResult.Success success
			&& success.swingSource() != InteractionResult.SwingSource.NONE)
			swingHand.swing(InteractionHand.MAIN_HAND);
		// MC.rightClickDelay = 4; // TODO: 26.1.2 - rightClickDelay is private
		currentPlan = plan;
		return true;
	}

	public boolean shouldSafeWalk()
	{
		return isEnabled() && safeWalk.isChecked()
			&& mode.getSelected() == Mode.NORMAL && MC.player != null
			&& MC.player.onGround() && !MC.options.keyJump.isDown();
	}

	private enum Mode
	{
		NORMAL("Normal"),
		TOWER("Tower");

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
}
