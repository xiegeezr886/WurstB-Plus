/*
 * Copyright (c) 2026 WurstB+ Plus contributors
 *
 * This source code is subject to the terms of the GNU Affero General Public
 * License, version 3. If a copy of the AGPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/agpl-3.0.txt
 */
// Ported from InvincibleMachineGun (GPL-3.0).
package net.wurstclient.hacks;

import java.awt.Color;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.inventory.AbstractFurnaceMenu;
import net.minecraft.world.inventory.BlastFurnaceMenu;
import net.minecraft.world.inventory.FurnaceMenu;
import net.minecraft.world.inventory.SmokerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.wurstclient.Category;
import net.wurstclient.SearchTags;
import net.wurstclient.events.RenderListener;
import net.wurstclient.events.UpdateListener;
import net.wurstclient.hack.Hack;
import net.wurstclient.settings.CheckboxSetting;
import net.wurstclient.settings.ColorSetting;
import net.wurstclient.settings.EnumSetting;
import net.wurstclient.settings.SliderSetting;
import net.wurstclient.settings.SliderSetting.ValueDisplay;
import net.wurstclient.util.ChatUtils;
import net.wurstclient.util.RenderUtils;
import net.wurstclient.util.chunk.ChunkUtils;

/**
 * Opens nearby furnaces and removes items that cannot be smelted from their
 * input slot. Blocked furnaces are highlighted so they can be found through
 * walls.
 *
 * <p>
 * Only client-loaded furnace block entities are scanned. The original module's
 * manual-open detection event is not available in Wurst, so opening a furnace
 * by hand simply does nothing unless auto clean is enabled.
 */
@SearchTags({"furnace unclogger", "furnace cleaner", "clogged furnace",
	"furnace", "smelter"})
public final class FurnaceUncloggerHack extends Hack
	implements UpdateListener, RenderListener
{
	private final CheckboxSetting autoClean = new CheckboxSetting("Auto clean",
		"Automatically opens nearby furnaces and removes clogged items.", true);

	private final EnumSetting<CleanMode> cleanMode = new EnumSetting<>(
		"Clean mode", "Move clogged items to the inventory or throw them out.",
		CleanMode.values(), CleanMode.INVENTORY);

	private final CheckboxSetting probeOnlyUnlit = new CheckboxSetting(
		"Only unlit furnaces",
		"Only automatically check furnaces that are not currently lit.", true);

	private final SliderSetting interactRange = new SliderSetting(
		"Interact range", "Distance at which furnaces are opened.", 4.5, 1, 6,
		0.1, ValueDisplay.DECIMAL.withSuffix(" blocks"));

	private final SliderSetting scanRange = new SliderSetting("Scan range",
		"How far away loaded furnace block entities are tracked.", 128, 16, 256,
		1, ValueDisplay.INTEGER.withSuffix(" blocks"));

	private final CheckboxSetting normalFurnace = new CheckboxSetting(
		"Normal furnaces", "Check normal furnaces.", true);

	private final CheckboxSetting blastFurnace = new CheckboxSetting(
		"Blast furnaces", "Check blast furnaces.", true);

	private final CheckboxSetting smoker = new CheckboxSetting("Smokers",
		"Check smokers.", true);

	private final SliderSetting scanInterval = new SliderSetting("Scan interval",
		"Ticks between block entity scans.", 10, 1, 100, 1,
		ValueDisplay.INTEGER.withSuffix(" ticks"));

	private final SliderSetting recheckDelay = new SliderSetting(
		"Recheck delay", "Ticks before a checked furnace is checked again.", 600,
		20, 7200, 1, ValueDisplay.INTEGER.withSuffix(" ticks"));

	private final SliderSetting openTimeout = new SliderSetting("Open timeout",
		"Ticks to wait for the furnace screen to open.", 20, 5, 100, 1,
		ValueDisplay.INTEGER.withSuffix(" ticks"));

	private final SliderSetting inspectDelay = new SliderSetting(
		"Inspect delay", "Ticks to wait after a furnace screen opens.", 3, 0, 20,
		1, ValueDisplay.INTEGER.withSuffix(" ticks"));

	private final SliderSetting actionDelay = new SliderSetting("Action delay",
		"Ticks between removal attempts.", 2, 1, 20, 1,
		ValueDisplay.INTEGER.withSuffix(" ticks"));

	private final SliderSetting maxRetries = new SliderSetting("Max retries",
		"How many times to retry removing a clogged item.", 4, 1, 10, 1,
		ValueDisplay.INTEGER);

	private final SliderSetting closeDelay = new SliderSetting("Close delay",
		"Ticks to wait before closing the furnace screen.", 2, 0, 20, 1,
		ValueDisplay.INTEGER.withSuffix(" ticks"));

	private final CheckboxSetting messages = new CheckboxSetting(
		"Messages", "Show a chat message when a furnace is cleaned or fails.",
		false);

	private final CheckboxSetting renderConfirmed = new CheckboxSetting(
		"Render confirmed", "Highlight furnaces that are still clogged.", true);

	private final CheckboxSetting renderSuspected = new CheckboxSetting(
		"Render suspected", "Highlight unlit furnaces that have not been"
			+ " checked yet.",
		true);

	private final SliderSetting renderDistance = new SliderSetting(
		"Render distance", "Maximum distance for furnace highlights.", 128, 8,
		256, 1, ValueDisplay.INTEGER.withSuffix(" blocks"));

	private final EnumSetting<RenderMode> renderMode = new EnumSetting<>(
		"Render mode", "Whether highlights are filled, outlined or both.",
		RenderMode.values(), RenderMode.BOTH);

	private final ColorSetting confirmedColor = new ColorSetting(
		"Confirmed color", "Color of confirmed clogged furnaces.",
		new Color(255, 40, 40, 160));

	private final ColorSetting suspectedColor = new ColorSetting(
		"Suspected color", "Color of unchecked unlit furnaces.",
		new Color(255, 150, 30, 120));

	private final Set<BlockPos> loadedFurnaces = new HashSet<>();
	private final Set<BlockPos> confirmedClogged = new HashSet<>();
	private final Map<BlockPos, Long> nextProbeTick = new HashMap<>();

	private BlockPos target;
	private Stage stage = Stage.IDLE;
	private long ticks;
	private int timer;
	private int cleanAttempts;

	public FurnaceUncloggerHack()
	{
		super("FurnaceUnclogger");
		setCategory(Category.ITEMS);
		addSetting(autoClean);
		addSetting(cleanMode);
		addSetting(probeOnlyUnlit);
		addSetting(interactRange);
		addSetting(scanRange);
		addSetting(normalFurnace);
		addSetting(blastFurnace);
		addSetting(smoker);
		addSetting(scanInterval);
		addSetting(recheckDelay);
		addSetting(openTimeout);
		addSetting(inspectDelay);
		addSetting(actionDelay);
		addSetting(maxRetries);
		addSetting(closeDelay);
		addSetting(messages);
		addSetting(renderConfirmed);
		addSetting(renderSuspected);
		addSetting(renderDistance);
		addSetting(renderMode);
		addSetting(confirmedColor);
		addSetting(suspectedColor);
	}

	@Override
	protected void onEnable()
	{
		resetAll();
		EVENTS.add(UpdateListener.class, this);
		EVENTS.add(RenderListener.class, this);
	}

	@Override
	protected void onDisable()
	{
		EVENTS.remove(UpdateListener.class, this);
		EVENTS.remove(RenderListener.class, this);

		if(target != null && MC.player != null
			&& MC.player.containerMenu instanceof AbstractFurnaceMenu)
			MC.player.closeContainer();

		resetAll();
	}

	@Override
	public void onUpdate()
	{
		if(MC.player == null || MC.level == null || MC.gameMode == null)
			return;

		ticks++;
		if(ticks % scanInterval.getValueI() == 0)
			scanLoadedFurnaces();

		switch(stage)
		{
			case IDLE -> tickIdle();
			case WAITING_FOR_SCREEN -> tickWaitingForScreen();
			case INSPECTING -> tickInspecting();
			case PICKING_UP -> tickPickingUp();
			case VERIFYING -> tickVerifying();
			case CLOSING -> tickClosing();
		}
	}

	private void tickIdle()
	{
		if(MC.screen != null || !autoClean.isChecked())
			return;

		BlockPos best = findBestTarget();
		if(best == null)
			return;

		target = best;
		cleanAttempts = 0;

		if(!openTarget())
		{
			nextProbeTick.put(target, ticks + 40);
			resetTarget();
			return;
		}

		stage = Stage.WAITING_FOR_SCREEN;
		timer = openTimeout.getValueI();
	}

	private void tickWaitingForScreen()
	{
		if(currentFurnaceMenu() != null)
		{
			stage = Stage.INSPECTING;
			timer = inspectDelay.getValueI();
			return;
		}

		if(MC.screen != null)
		{
			nextProbeTick.put(target, ticks + 40);
			resetTarget();
			return;
		}

		if(--timer <= 0)
		{
			nextProbeTick.put(target, ticks + 40);
			resetTarget();
		}
	}

	private void tickInspecting()
	{
		AbstractFurnaceMenu menu = currentFurnaceMenu();
		if(menu == null)
		{
			abortTarget();
			return;
		}

		if(timer-- > 0)
			return;

		if(!menu.getCarried().isEmpty())
		{
			nextProbeTick.put(target, ticks + 40);
			startClosing();
			return;
		}

		ItemStack input =
			menu.getSlot(AbstractFurnaceMenu.INGREDIENT_SLOT).getItem();

		if(input.isEmpty() || isSmeltable(input))
		{
			confirmedClogged.remove(target);
			nextProbeTick.put(target, ticks + recheckDelay.getValueI());
			startClosing();
			return;
		}

		confirmedClogged.add(target);
		cleanAttempts = 0;
		stage = Stage.PICKING_UP;
		timer = actionDelay.getValueI();
	}

	private void tickPickingUp()
	{
		AbstractFurnaceMenu menu = currentFurnaceMenu();
		if(menu == null)
		{
			abortTarget();
			return;
		}

		if(timer-- > 0)
			return;

		ItemStack input =
			menu.getSlot(AbstractFurnaceMenu.INGREDIENT_SLOT).getItem();

		if(input.isEmpty() || isSmeltable(input))
		{
			finishSuccessfulClean();
			return;
		}

		if(cleanAttempts >= maxRetries.getValueI())
		{
			failClean("the server did not accept the removal");
			return;
		}

		if(cleanMode.getSelected() == CleanMode.DROP)
			IMC.getInteractionManager()
				.windowClick_THROW(AbstractFurnaceMenu.INGREDIENT_SLOT);
		else
			IMC.getInteractionManager()
				.windowClick_QUICK_MOVE(AbstractFurnaceMenu.INGREDIENT_SLOT);

		cleanAttempts++;
		stage = Stage.VERIFYING;
		timer = actionDelay.getValueI();
	}

	private void tickVerifying()
	{
		AbstractFurnaceMenu menu = currentFurnaceMenu();
		if(menu == null)
		{
			abortTarget();
			return;
		}

		if(timer-- > 0)
			return;

		ItemStack input =
			menu.getSlot(AbstractFurnaceMenu.INGREDIENT_SLOT).getItem();

		if(input.isEmpty() || isSmeltable(input))
		{
			finishSuccessfulClean();
			return;
		}

		if(cleanAttempts < maxRetries.getValueI())
		{
			stage = Stage.PICKING_UP;
			timer = actionDelay.getValueI();
			return;
		}

		failClean(cleanMode.getSelected() == CleanMode.DROP
			? "the server did not accept the drop"
			: "the item could not be moved (inventory full?)");
	}

	private void tickClosing()
	{
		if(timer-- > 0)
			return;

		AbstractFurnaceMenu menu = currentFurnaceMenu();
		if(menu != null && !menu.getCarried().isEmpty())
		{
			timer = actionDelay.getValueI();
			return;
		}

		if(MC.player.containerMenu instanceof AbstractFurnaceMenu)
			MC.player.closeContainer();

		resetTarget();
	}

	private void finishSuccessfulClean()
	{
		confirmedClogged.remove(target);
		nextProbeTick.put(target, ticks + recheckDelay.getValueI());

		if(messages.isChecked())
			ChatUtils.message("Cleaned clogged furnace at " + formatPos(target));

		startClosing();
	}

	private void failClean(String reason)
	{
		confirmedClogged.add(target);
		nextProbeTick.put(target, ticks + recheckDelay.getValueI());

		if(messages.isChecked())
			ChatUtils.warning("Furnace clean failed: " + reason + " at "
				+ formatPos(target));

		startClosing();
	}

	private void startClosing()
	{
		stage = Stage.CLOSING;
		timer = closeDelay.getValueI();
	}

	private void abortTarget()
	{
		if(target != null)
			nextProbeTick.put(target, ticks + 40);
		resetTarget();
	}

	private void resetTarget()
	{
		target = null;
		stage = Stage.IDLE;
		timer = 0;
		cleanAttempts = 0;
	}

	private void resetAll()
	{
		loadedFurnaces.clear();
		confirmedClogged.clear();
		nextProbeTick.clear();
		target = null;
		stage = Stage.IDLE;
		ticks = 0;
		timer = 0;
		cleanAttempts = 0;
	}

	private void scanLoadedFurnaces()
	{
		loadedFurnaces.clear();
		double maxSq = scanRange.getValueSq();

		for(BlockEntity be : ChunkUtils.getLoadedBlockEntities().toList())
		{
			if(!(be instanceof AbstractFurnaceBlockEntity))
				continue;

			BlockPos pos = be.getBlockPos();
			if(eyeDistanceSq(pos) > maxSq)
				continue;

			BlockState state = MC.level.getBlockState(pos);
			if(!isSupported(state))
				continue;

			loadedFurnaces.add(pos.immutable());
		}

		confirmedClogged.removeIf(pos ->
			!isSupported(MC.level.getBlockState(pos)));
	}

	private BlockPos findBestTarget()
	{
		double maxSq = interactRange.getValueSq();
		BlockPos best = null;
		double bestScore = Double.MAX_VALUE;

		for(BlockPos pos : loadedFurnaces)
		{
			if(ticks < nextProbeTick.getOrDefault(pos, 0L))
				continue;

			double distanceSq = eyeDistanceSq(pos);
			if(distanceSq > maxSq)
				continue;

			BlockState state = MC.level.getBlockState(pos);
			if(!isSupported(state))
				continue;

			boolean confirmed = confirmedClogged.contains(pos);
			if(!confirmed && probeOnlyUnlit.isChecked() && isLit(state))
				continue;

			double score = confirmed ? distanceSq - 10_000.0 : distanceSq;
			if(score < bestScore)
			{
				bestScore = score;
				best = pos;
			}
		}

		return best;
	}

	private boolean openTarget()
	{
		if(target == null)
			return false;

		BlockState state = MC.level.getBlockState(target);
		if(!isSupported(state))
			return false;

		IMC.getInteractionManager().rightClickBlock(target, nearestFace(target),
			Vec3.atCenterOf(target));
		return true;
	}

	private Direction nearestFace(BlockPos pos)
	{
		Vec3 center = Vec3.atCenterOf(pos);
		Vec3 eye = MC.player.getEyePosition();
		double dx = eye.x - center.x;
		double dy = eye.y - center.y;
		double dz = eye.z - center.z;
		double ax = Math.abs(dx);
		double ay = Math.abs(dy);
		double az = Math.abs(dz);

		if(ax >= ay && ax >= az)
			return dx >= 0 ? Direction.EAST : Direction.WEST;
		if(ay >= ax && ay >= az)
			return dy >= 0 ? Direction.UP : Direction.DOWN;
		return dz >= 0 ? Direction.SOUTH : Direction.NORTH;
	}

	private AbstractFurnaceMenu currentFurnaceMenu()
	{
		if(MC.player == null || target == null || MC.level == null)
			return null;

		if(!(MC.player.containerMenu instanceof AbstractFurnaceMenu menu))
			return null;

		BlockState state = MC.level.getBlockState(target);
		if(state.getBlock() == Blocks.FURNACE && !(menu instanceof FurnaceMenu))
			return null;
		if(state.getBlock() == Blocks.BLAST_FURNACE
			&& !(menu instanceof BlastFurnaceMenu))
			return null;
		if(state.getBlock() == Blocks.SMOKER && !(menu instanceof SmokerMenu))
			return null;

		return menu;
	}

	private boolean isSupported(BlockState state)
	{
		if(state.getBlock() == Blocks.FURNACE)
			return normalFurnace.isChecked();
		if(state.getBlock() == Blocks.BLAST_FURNACE)
			return blastFurnace.isChecked();
		if(state.getBlock() == Blocks.SMOKER)
			return smoker.isChecked();
		return false;
	}

	private boolean isLit(BlockState state)
	{
		return state.hasProperty(BlockStateProperties.LIT)
			&& state.getValue(BlockStateProperties.LIT);
	}

	private boolean isSmeltable(ItemStack stack)
	{
		if(stack.isEmpty() || target == null || MC.level == null)
			return false;

		BlockState state = MC.level.getBlockState(target);

		if(state.getBlock() == Blocks.FURNACE)
			return MC.level.getRecipeManager()
				.getRecipeFor(RecipeType.SMELTING, new SimpleContainer(stack),
					MC.level)
				.isPresent();
		if(state.getBlock() == Blocks.BLAST_FURNACE)
			return MC.level.getRecipeManager()
				.getRecipeFor(RecipeType.BLASTING, new SimpleContainer(stack),
					MC.level)
				.isPresent();
		if(state.getBlock() == Blocks.SMOKER)
			return MC.level.getRecipeManager()
				.getRecipeFor(RecipeType.SMOKING, new SimpleContainer(stack),
					MC.level)
				.isPresent();

		return false;
	}

	private double eyeDistanceSq(BlockPos pos)
	{
		return MC.player.getEyePosition().distanceToSqr(Vec3.atCenterOf(pos));
	}

	private String formatPos(BlockPos pos)
	{
		return pos.getX() + ", " + pos.getY() + ", " + pos.getZ();
	}

	@Override
	public void onRender(PoseStack matrixStack, float partialTicks)
	{
		if(MC.player == null || MC.level == null)
			return;

		double maxSq = renderDistance.getValueSq();

		if(renderConfirmed.isChecked())
			for(BlockPos pos : confirmedClogged)
			{
				if(MC.player.getEyePosition()
					.distanceToSqr(Vec3.atCenterOf(pos)) > maxSq)
					continue;

				drawBox(matrixStack, pos, confirmedColor.getColorI());
			}

		if(renderSuspected.isChecked())
			for(BlockPos pos : loadedFurnaces)
			{
				if(confirmedClogged.contains(pos))
					continue;
				if(MC.player.getEyePosition()
					.distanceToSqr(Vec3.atCenterOf(pos)) > maxSq)
					continue;
				if(ticks < nextProbeTick.getOrDefault(pos, 0L))
					continue;

				BlockState state = MC.level.getBlockState(pos);
				if(!isSupported(state) || isLit(state))
					continue;

				drawBox(matrixStack, pos, suspectedColor.getColorI());
			}
	}

	private void drawBox(PoseStack matrixStack, BlockPos pos, int color)
	{
		AABB box = new AABB(pos);

		if(renderMode.getSelected() != RenderMode.OUTLINE)
			RenderUtils.drawSolidBox(matrixStack, box.inflate(0.002), color,
				false);

		if(renderMode.getSelected() != RenderMode.FILL)
			RenderUtils.drawOutlinedBox(matrixStack, box.inflate(0.002), color,
				false);
	}

	private enum Stage
	{
		IDLE,
		WAITING_FOR_SCREEN,
		INSPECTING,
		PICKING_UP,
		VERIFYING,
		CLOSING
	}

	private enum CleanMode
	{
		INVENTORY("Move to inventory"),
		DROP("Drop");

		private final String name;

		private CleanMode(String name)
		{
			this.name = name;
		}

		@Override
		public String toString()
		{
			return name;
		}
	}

	private enum RenderMode
	{
		BOTH("Fill and outline"),
		OUTLINE("Outline only"),
		FILL("Fill only");

		private final String name;

		private RenderMode(String name)
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
