/*
 * Copyright (c) 2026 WurstB+ Plus contributors
 *
 * This source code is subject to the terms of the GNU Affero General Public
 * License, version 3. If a copy of the AGPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/agpl-3.0.txt
 */
// Ported from InvincibleMachineGun (GPL-3.0).
package net.wurstclient.hacks;

import org.lwjgl.glfw.GLFW;

import com.mojang.blaze3d.platform.InputConstants;

import net.minecraft.core.BlockPos;
import net.minecraft.network.protocol.game.ServerboundInteractPacket;
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket;
import net.minecraft.network.protocol.game.ServerboundSetCarriedItemPacket;
import net.minecraft.network.protocol.game.ServerboundSwingPacket;
import net.minecraft.network.protocol.game.ServerboundUseItemOnPacket;
import net.minecraft.network.protocol.game.ServerboundUseItemPacket;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.wurstclient.Category;
import net.wurstclient.SearchTags;
import net.wurstclient.WurstClient;
import net.wurstclient.events.UpdateListener;
import net.wurstclient.hack.Hack;
import net.wurstclient.settings.CheckboxSetting;
import net.wurstclient.settings.EnumSetting;
import net.wurstclient.settings.SliderSetting;
import net.wurstclient.settings.SliderSetting.ValueDisplay;
import net.wurstclient.util.ChatUtils;

/**
 * Executes place/break/interact/use/attack actions through direct packets
 * while a container or other GUI screen is open.
 *
 * <p>
 * Wurst has no keybind settings, so each action is bound to a key picked from
 * a fixed list of common keys. The placement slot search is limited to the
 * hotbar and the cursor stack.
 */
@SearchTags({"screen actions", "gui actions", "inventory actions",
	"container actions", "quick actions"})
public final class ScreenActionsHack extends Hack implements UpdateListener
{
	private final SliderSetting range = new SliderSetting("Range",
		"Maximum distance to the targeted block or entity.", 4, 1, 6, 1,
		ValueDisplay.INTEGER.withSuffix(" blocks"));

	private final CheckboxSetting autoSearch = new CheckboxSetting(
		"Auto search",
		"Searches the hotbar (and the cursor stack) for a block to place.",
		true);

	private final CheckboxSetting instantBreak = new CheckboxSetting(
		"Instant break", "Sends the stop-destroy packet immediately.", true);

	private final EnumSetting<ActionType> action1 = new EnumSetting<>(
		"Action 1", "Action executed by key 1.", ActionType.values(),
		ActionType.PLACE_BLOCK);
	private final EnumSetting<ActionKey> key1 = new EnumSetting<>("Key 1",
		"Key that triggers action 1.", ActionKey.values(), ActionKey.NONE);

	private final EnumSetting<ActionType> action2 = new EnumSetting<>(
		"Action 2", "Action executed by key 2.", ActionType.values(),
		ActionType.BREAK_BLOCK);
	private final EnumSetting<ActionKey> key2 = new EnumSetting<>("Key 2",
		"Key that triggers action 2.", ActionKey.values(), ActionKey.NONE);

	private final EnumSetting<ActionType> action3 = new EnumSetting<>(
		"Action 3", "Action executed by key 3.", ActionType.values(),
		ActionType.INTERACT_BLOCK);
	private final EnumSetting<ActionKey> key3 = new EnumSetting<>("Key 3",
		"Key that triggers action 3.", ActionKey.values(), ActionKey.NONE);

	private final EnumSetting<ActionType> action4 = new EnumSetting<>(
		"Action 4", "Action executed by key 4.", ActionType.values(),
		ActionType.USE_ITEM);
	private final EnumSetting<ActionKey> key4 = new EnumSetting<>("Key 4",
		"Key that triggers action 4.", ActionKey.values(), ActionKey.NONE);

	private final boolean[] wasPressed = new boolean[4];

	public ScreenActionsHack()
	{
		super("ScreenActions");
		setCategory(Category.ITEMS);
		addSetting(range);
		addSetting(autoSearch);
		addSetting(instantBreak);
		addSetting(action1);
		addSetting(key1);
		addSetting(action2);
		addSetting(key2);
		addSetting(action3);
		addSetting(key3);
		addSetting(action4);
		addSetting(key4);
	}

	@Override
	protected void onEnable()
	{
		EVENTS.add(UpdateListener.class, this);
		for(int i = 0; i < wasPressed.length; i++)
			wasPressed[i] = false;
	}

	@Override
	protected void onDisable()
	{
		EVENTS.remove(UpdateListener.class, this);
	}

	@Override
	public void onUpdate()
	{
		if(MC.player == null || MC.level == null || MC.player.connection == null)
			return;

		check(0, key1, action1);
		check(1, key2, action2);
		check(2, key3, action3);
		check(3, key4, action4);
	}

	private void check(int index, EnumSetting<ActionKey> key,
		EnumSetting<ActionType> action)
	{
		boolean pressed = key.getSelected().isDown();
		if(pressed && !wasPressed[index])
			execute(action.getSelected());
		wasPressed[index] = pressed;
	}

	private void execute(ActionType action)
	{
		try
		{
			switch(action)
			{
				case PLACE_BLOCK -> placeBlock();
				case BREAK_BLOCK -> breakBlock();
				case INTERACT_BLOCK -> interactBlock();
				case USE_ITEM -> useItem();
				case ATTACK_ENTITY -> attackEntity();
			}
		}catch(Exception e)
		{
			ChatUtils.error("ScreenActions: " + e.getMessage());
		}
	}

	private void placeBlock()
	{
		if(!(MC.hitResult instanceof BlockHitResult hit)
			|| hit.getType() != HitResult.Type.BLOCK)
			return;
		if(!inRange(hit.getBlockPos()))
			return;

		int slot = findBlockSlot();
		if(slot == -1)
			return;

		int oldSlot = MC.player.getInventory().selected;
		if(slot != oldSlot)
			MC.player.connection
				.send(new ServerboundSetCarriedItemPacket(slot));

		MC.player.connection.send(
			new ServerboundUseItemOnPacket(InteractionHand.MAIN_HAND, hit, 0));
		MC.player.connection
			.send(new ServerboundSwingPacket(InteractionHand.MAIN_HAND));

		if(slot != oldSlot)
			MC.player.connection
				.send(new ServerboundSetCarriedItemPacket(oldSlot));
	}

	private void breakBlock()
	{
		if(!(MC.hitResult instanceof BlockHitResult hit)
			|| hit.getType() != HitResult.Type.BLOCK)
			return;

		BlockPos pos = hit.getBlockPos();
		if(!inRange(pos) || MC.level.getBlockState(pos).isAir())
			return;

		MC.player.connection.send(new ServerboundPlayerActionPacket(
			ServerboundPlayerActionPacket.Action.START_DESTROY_BLOCK, pos,
			hit.getDirection()));

		if(instantBreak.isChecked())
			MC.player.connection.send(new ServerboundPlayerActionPacket(
				ServerboundPlayerActionPacket.Action.STOP_DESTROY_BLOCK, pos,
				hit.getDirection()));

		MC.player.connection
			.send(new ServerboundSwingPacket(InteractionHand.MAIN_HAND));
	}

	private void interactBlock()
	{
		if(!(MC.hitResult instanceof BlockHitResult hit)
			|| hit.getType() != HitResult.Type.BLOCK)
			return;
		if(!inRange(hit.getBlockPos()))
			return;

		MC.player.connection.send(
			new ServerboundUseItemOnPacket(InteractionHand.MAIN_HAND, hit, 0));
		MC.player.connection
			.send(new ServerboundSwingPacket(InteractionHand.MAIN_HAND));
	}

	private void useItem()
	{
		MC.player.connection.send(new ServerboundUseItemPacket(
			InteractionHand.MAIN_HAND, 0));
		MC.player.connection
			.send(new ServerboundSwingPacket(InteractionHand.MAIN_HAND));
	}

	private void attackEntity()
	{
		if(!(MC.hitResult instanceof EntityHitResult hit))
			return;

		if(MC.player.distanceToSqr(hit.getEntity()) > range.getValueSq())
			return;

		MC.player.connection.send(ServerboundInteractPacket
			.createAttackPacket(hit.getEntity(), MC.player.isShiftKeyDown()));
		MC.player.connection
			.send(new ServerboundSwingPacket(InteractionHand.MAIN_HAND));
	}

	private boolean inRange(BlockPos pos)
	{
		return MC.player.distanceToSqr(Vec3.atCenterOf(pos)) <= range
			.getValueSq();
	}

	private int findBlockSlot()
	{
		int current = MC.player.getInventory().selected;
		if(MC.player.getInventory().getItem(current)
			.getItem() instanceof BlockItem)
			return current;

		if(!autoSearch.isChecked())
			return -1;

		if(MC.player.containerMenu != null)
		{
			ItemStack carried = MC.player.containerMenu.getCarried();
			if(!carried.isEmpty() && carried.getItem() instanceof BlockItem)
			{
				for(int i = 0; i < 9; i++)
					if(MC.player.getInventory().getItem(i).isEmpty())
					{
						moveCursorToSlot(i);
						return i;
					}

				moveCursorToSlot(current);
				return current;
			}
		}

		for(int i = 0; i < 9; i++)
			if(MC.player.getInventory().getItem(i)
				.getItem() instanceof BlockItem)
				return i;

		return -1;
	}

	private void moveCursorToSlot(int hotbarSlot)
	{
		MC.gameMode.handleInventoryMouseClick(
			MC.player.containerMenu.containerId, hotbarSlot + 36, 0,
			ClickType.PICKUP, MC.player);
	}

	public enum ActionType
	{
		PLACE_BLOCK("Place block"),
		BREAK_BLOCK("Break block"),
		INTERACT_BLOCK("Interact block"),
		USE_ITEM("Use item"),
		ATTACK_ENTITY("Attack entity");

		private final String name;

		ActionType(String name)
		{
			this.name = name;
		}

		@Override
		public String toString()
		{
			return name;
		}
	}

	public enum ActionKey
	{
		NONE(-1),
		R(GLFW.GLFW_KEY_R),
		V(GLFW.GLFW_KEY_V),
		G(GLFW.GLFW_KEY_G),
		X(GLFW.GLFW_KEY_X),
		Z(GLFW.GLFW_KEY_Z),
		C(GLFW.GLFW_KEY_C),
		F(GLFW.GLFW_KEY_F),
		H(GLFW.GLFW_KEY_H),
		J(GLFW.GLFW_KEY_J),
		K(GLFW.GLFW_KEY_K),
		L(GLFW.GLFW_KEY_L),
		N(GLFW.GLFW_KEY_N),
		M(GLFW.GLFW_KEY_M),
		B(GLFW.GLFW_KEY_B);

		private final int keyCode;

		ActionKey(int keyCode)
		{
			this.keyCode = keyCode;
		}

		public boolean isDown()
		{
			return keyCode != -1
				&& InputConstants.isKeyDown(WurstClient.MC.getWindow().getWindow(),
					keyCode);
		}

		@Override
		public String toString()
		{
			return this == NONE ? "None" : name();
		}
	}
}
