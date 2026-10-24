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
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.ShulkerBoxMenu;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.entity.BarrelBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.entity.ShulkerBoxBlockEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.wurstclient.Category;
import net.wurstclient.SearchTags;
import net.wurstclient.events.RenderListener;
import net.wurstclient.events.RightClickListener;
import net.wurstclient.events.UpdateListener;
import net.wurstclient.hack.Hack;
import net.wurstclient.settings.CheckboxSetting;
import net.wurstclient.settings.ColorSetting;
import net.wurstclient.settings.EnumSetting;
import net.wurstclient.settings.SliderSetting;
import net.wurstclient.settings.SliderSetting.ValueDisplay;
import net.wurstclient.settings.TextFieldSetting;
import net.wurstclient.util.ChatUtils;
import net.wurstclient.util.InventoryUtils;
import net.wurstclient.util.RenderUtils;
import net.wurstclient.util.chunk.ChunkUtils;

/**
 * Simplified SchematicPro.
 *
 * <p>
 * The original module reads Litematica's schematic world and fills containers
 * with the materials required by a schematic. Litematica is not a dependency of
 * this project, so this port replaces schematic parsing with a user-configured
 * list of required items and counts. It opens nearby containers (or the one you
 * right-click in manual mode) and moves matching items from the player
 * inventory until the configured counts are satisfied. In creative mode,
 * missing items are spawned into empty inventory slots first. Whole stacks are
 * moved, so a container can be filled slightly past the requested count.
 */
@SearchTags({"schematic pro", "schematic", "litematica", "container filler",
	"material filler"})
public final class SchematicProHack extends Hack
	implements UpdateListener, RightClickListener, RenderListener
{
	private static final int OFFHAND_SLOT = 40;

	private final EnumSetting<OperationMode> operationMode = new EnumSetting<>(
		"Operation mode", "Automatic opens nearby containers. Manual only"
			+ " processes the container you right-click.",
		OperationMode.values(), OperationMode.AUTOMATIC);

	private final EnumSetting<FillMode> fillMode = new EnumSetting<>(
		"Fill mode", "Creative spawns missing items. Survival only uses items"
			+ " already in the player inventory.",
		FillMode.values(), FillMode.CREATIVE);

	private final TextFieldSetting requirements = new TextFieldSetting(
		"Requirements",
		"Comma-separated item=count entries, for example"
			+ " minecraft:chest=27,minecraft:hopper=27.",
		"minecraft:chest=27,minecraft:hopper=27");

	private final SliderSetting interactRange = new SliderSetting(
		"Interact range", "How far away containers may be opened.", 5, 1, 8, 1,
		ValueDisplay.INTEGER.withSuffix(" blocks"));

	private final SliderSetting delay = new SliderSetting("Delay",
		"Ticks between item moves.", 1, 0, 20, 1,
		ValueDisplay.INTEGER.withSuffix(" ticks"));

	private final CheckboxSetting autoClose = new CheckboxSetting("Auto close",
		"Closes the container once all requirements are satisfied.", true);

	private final CheckboxSetting highlight = new CheckboxSetting("Highlight",
		"Draws a box around the current container.", true);

	private final ColorSetting color = new ColorSetting("Highlight color",
		"Color of the target box.", new Color(0, 200, 255, 200));

	private final Set<BlockPos> finished = new HashSet<>();

	private BlockPos target;
	private int timer;
	private int openTimer;

	public SchematicProHack()
	{
		super("SchematicPro");
		setCategory(Category.ITEMS);
		addSetting(operationMode);
		addSetting(fillMode);
		addSetting(requirements);
		addSetting(interactRange);
		addSetting(delay);
		addSetting(autoClose);
		addSetting(highlight);
		addSetting(color);
	}

	@Override
	protected void onEnable()
	{
		target = null;
		timer = 0;
		openTimer = 0;
		finished.clear();
		EVENTS.add(UpdateListener.class, this);
		EVENTS.add(RightClickListener.class, this);
		EVENTS.add(RenderListener.class, this);
	}

	@Override
	protected void onDisable()
	{
		EVENTS.remove(UpdateListener.class, this);
		EVENTS.remove(RightClickListener.class, this);
		EVENTS.remove(RenderListener.class, this);

		if(MC.player != null && MC.player.containerMenu != MC.player.inventoryMenu)
			MC.player.closeContainer();

		target = null;
		finished.clear();
	}

	@Override
	public void onRightClick(RightClickEvent event)
	{
		if(operationMode.getSelected() != OperationMode.MANUAL
			|| MC.player == null)
			return;

		if(!(MC.hitResult instanceof BlockHitResult hit))
			return;

		BlockPos pos = hit.getBlockPos();
		if(!isContainer(pos))
			return;

		target = pos.immutable();
		finished.remove(target);
		openTimer = 20;
	}

	@Override
	public void onUpdate()
	{
		if(MC.player == null || MC.level == null || MC.gameMode == null)
			return;

		AbstractContainerMenu menu = MC.player.containerMenu;
		boolean containerOpen =
			menu instanceof ChestMenu || menu instanceof ShulkerBoxMenu;

		if(containerOpen)
		{
			if(target == null)
				target = findTarget();

			if(target == null)
				return;

			if(timer > 0)
			{
				timer--;
				return;
			}

			handleFilling(menu);
			return;
		}

		if(operationMode.getSelected() != OperationMode.AUTOMATIC)
			return;

		if(MC.screen != null)
			return;

		if(target != null && finished.contains(target))
			target = null;

		if(target == null)
			target = findTarget();

		if(target == null)
			return;

		if(openTimer > 0)
		{
			openTimer--;
			return;
		}

		openTimer = 10;
		IMC.getInteractionManager().rightClickBlock(target, Direction.UP,
			Vec3.atCenterOf(target));
	}

	private void handleFilling(AbstractContainerMenu menu)
	{
		int containerSlots = Math.max(0, menu.slots.size() - 36);
		Map<Item, Integer> counts = countContainerItems(menu, containerSlots);

		Map<Item, Integer> wanted;
		try
		{
			wanted = parseRequirements();
		}catch(RuntimeException e)
		{
			ChatUtils.warning("SchematicPro: invalid requirements string.");
			setEnabled(false);
			return;
		}

		for(Map.Entry<Item, Integer> entry : wanted.entrySet())
		{
			Item item = entry.getKey();
			int missing = entry.getValue()
				- counts.getOrDefault(item, 0);

			if(missing <= 0)
				continue;

			if(!moveItemIntoContainer(menu, item, missing, containerSlots))
			{
				ChatUtils.warning("SchematicPro: missing " + missing + "x "
					+ BuiltInRegistries.ITEM.getKey(item) + ".");
				if(autoClose.isChecked())
					MC.player.closeContainer();
				timer = 20;
				return;
			}

			timer = delay.getValueI();
			return;
		}

		if(target != null)
			finished.add(target);

		ChatUtils.message("SchematicPro: container requirements satisfied.");
		if(autoClose.isChecked())
			MC.player.closeContainer();
	}

	private boolean moveItemIntoContainer(AbstractContainerMenu menu,
		Item item, int missing, int containerSlots)
	{
		int source = InventoryUtils.indexOf(
			stack -> !stack.isEmpty() && stack.is(item), 36);

		if(source == -1 && fillMode.getSelected() == FillMode.CREATIVE
			&& MC.player.getAbilities().instabuild)
		{
			int free = findFreeInventorySlot();
			if(free == -1)
				return false;

			int amount = Math.min(missing,
				new ItemStack(item).getMaxStackSize());
			InventoryUtils.setCreativeStack(free,
				new ItemStack(item, amount));
			source = free;
		}

		if(source == -1)
			return false;

		IMC.getInteractionManager()
			.windowClick_QUICK_MOVE(toMenuSlot(source, containerSlots));
		return true;
	}

	private Map<Item, Integer> parseRequirements()
	{
		Map<Item, Integer> wanted = new java.util.LinkedHashMap<>();

		for(String entry : requirements.getValue().split("[,;]"))
		{
			String trimmed = entry.trim();
			if(trimmed.isEmpty())
				continue;

			String[] parts = trimmed.split("=");
			if(parts.length != 2)
				continue;

			Item item = BuiltInRegistries.ITEM
				.getValue(ResourceLocation.parse(parts[0].trim()));
			int count = Integer.parseInt(parts[1].trim());

			if(item != Items.AIR && count > 0)
				wanted.put(item, count);
		}

		return wanted;
	}

	private Map<Item, Integer> countContainerItems(AbstractContainerMenu menu,
		int containerSlots)
	{
		Map<Item, Integer> counts = new HashMap<>();

		for(int i = 0; i < containerSlots; i++)
		{
			ItemStack stack = menu.getSlot(i).getItem();
			if(!stack.isEmpty())
				counts.merge(stack.getItem(), stack.getCount(), Integer::sum);
		}

		return counts;
	}

	private int findFreeInventorySlot()
	{
		for(int i = 0; i < 36; i++)
			if(MC.player.getInventory().getItem(i).isEmpty())
				return i;

		return -1;
	}

	private int toMenuSlot(int inventorySlot, int containerSlots)
	{
		if(inventorySlot >= 0 && inventorySlot < 9)
			return containerSlots + 27 + inventorySlot;
		if(inventorySlot >= 9 && inventorySlot < 36)
			return containerSlots + inventorySlot - 9;
		if(inventorySlot == OFFHAND_SLOT)
			return containerSlots + 36;
		return inventorySlot;
	}

	private BlockPos findTarget()
	{
		double rangeSq = interactRange.getValueSq();

		return ChunkUtils.getLoadedBlockEntities()
			.filter(be -> be instanceof ChestBlockEntity
				|| be instanceof BarrelBlockEntity
				|| be instanceof ShulkerBoxBlockEntity)
			.map(BlockEntity::getBlockPos).filter(pos -> !finished.contains(pos))
			.filter(pos -> MC.player.getEyePosition()
				.distanceToSqr(Vec3.atCenterOf(pos)) <= rangeSq)
			.min(java.util.Comparator.comparingDouble(pos -> MC.player
				.getEyePosition().distanceToSqr(Vec3.atCenterOf(pos))))
			.orElse(null);
	}

	private boolean isContainer(BlockPos pos)
	{
		BlockEntity be = MC.level.getBlockEntity(pos);
		return be instanceof ChestBlockEntity || be instanceof BarrelBlockEntity
			|| be instanceof ShulkerBoxBlockEntity;
	}

	@Override
	public void onRender(PoseStack matrixStack, float partialTicks)
	{
		if(!highlight.isChecked() || target == null)
			return;

		RenderUtils.drawOutlinedBox(matrixStack, new AABB(target),
			color.getColorI(), false);
	}

	private enum OperationMode
	{
		AUTOMATIC("Automatic"),
		MANUAL("Manual");

		private final String name;

		private OperationMode(String name)
		{
			this.name = name;
		}

		@Override
		public String toString()
		{
			return name;
		}
	}

	private enum FillMode
	{
		CREATIVE("Creative"),
		SURVIVAL("Survival");

		private final String name;

		private FillMode(String name)
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
