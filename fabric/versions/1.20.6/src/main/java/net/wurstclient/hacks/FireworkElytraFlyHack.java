/*
 * Copyright (c) 2026 WurstB+ Plus contributors
 *
 * This source code is subject to the terms of the GNU Affero General Public
 * License, version 3. If a copy of the AGPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/agpl-3.0.txt
 */
// Ported from LeavesHack (AGPL-3.0).
package net.wurstclient.hacks;

import net.minecraft.network.protocol.game.ServerboundPlayerCommandPacket;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
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

/**
 * Firework-powered elytra flight with automatic takeoff, automatic firework
 * use and optional WASD/Space/Shift flight control.
 */
@SearchTags({"firework elytra", "elytra fly", "firework fly", "rocket elytra",
	"elytra"})
public final class FireworkElytraFlyHack extends Hack
	implements UpdateListener
{
	private final SliderSetting speed = new SliderSetting("Speed",
		"How far the movement keys push you horizontally.", 1.5, 0.1, 5,
		0.05, ValueDisplay.DECIMAL);

	private final SliderSetting height = new SliderSetting("Height",
		"How fast Jump/Sneak move you vertically.", 0.5, 0.05, 2, 0.05,
		ValueDisplay.DECIMAL);

	private final SliderSetting fireworkDelay = new SliderSetting(
		"Firework delay", "Ticks to wait between firework boosts.", 20, 1, 100,
		1, ValueDisplay.INTEGER.withSuffix(" ticks"));

	private final CheckboxSetting autoFirework = new CheckboxSetting(
		"Auto firework", "Automatically use firework rockets while flying.",
		true);

	private final CheckboxSetting autoTakeoff = new CheckboxSetting(
		"Auto takeoff", "Jump and start gliding automatically.", true);

	private final CheckboxSetting flightControl = new CheckboxSetting(
		"Flight control",
		"Use the movement keys for horizontal speed and Jump/Sneak for"
			+ " height while gliding.",
		true);

	private final CheckboxSetting searchInventory = new CheckboxSetting(
		"Search inventory",
		"Also take fireworks from the main inventory, not just the hotbar.",
		true);

	private final CheckboxSetting stopInWater = new CheckboxSetting(
		"Stop in water", "Stop gliding when you touch water.", true);

	private int fireworkTimer;
	private int jumpTimer;
	private int takeoffTimer;
	private int previousSlot = -1;

	public FireworkElytraFlyHack()
	{
		super("FireworkElytraFly");
		setCategory(Category.MOVEMENT);
		addSetting(speed);
		addSetting(height);
		addSetting(fireworkDelay);
		addSetting(autoFirework);
		addSetting(autoTakeoff);
		addSetting(flightControl);
		addSetting(searchInventory);
		addSetting(stopInWater);
	}

	@Override
	protected void onEnable()
	{
		fireworkTimer = 0;
		jumpTimer = 0;
		takeoffTimer = 0;
		previousSlot = -1;
		EVENTS.add(UpdateListener.class, this);
	}

	@Override
	protected void onDisable()
	{
		EVENTS.remove(UpdateListener.class, this);

		if(previousSlot >= 0 && MC.player != null)
			MC.player.getInventory().selected = previousSlot;
		previousSlot = -1;
	}

	@Override
	public void onUpdate()
	{
		if(MC.player == null || MC.level == null || MC.gameMode == null)
			return;

		if(fireworkTimer > 0)
			fireworkTimer--;
		if(jumpTimer > 0)
			jumpTimer--;
		if(takeoffTimer > 0)
			takeoffTimer--;

		ItemStack chest = MC.player.getItemBySlot(EquipmentSlot.CHEST);
		if(!chest.is(Items.ELYTRA))
			return;

		if(MC.player.isFallFlying())
		{
			if(stopInWater.isChecked() && MC.player.isInWater())
			{
				sendStartStopPacket();
				return;
			}

			if(MC.screen != null)
				return;

			controlFlight();
			useFirework();
			return;
		}

		if(autoTakeoff.isChecked() && MC.screen == null)
			doTakeoff();
	}

	private void controlFlight()
	{
		if(!flightControl.isChecked())
			return;

		Vec3 velocity = MC.player.getDeltaMovement();
		double yaw = Math.toRadians(MC.player.getYRot());
		double forwardX = -Math.sin(yaw);
		double forwardZ = Math.cos(yaw);
		double base = 0.05 * speed.getValue();

		double x = velocity.x;
		double y = velocity.y;
		double z = velocity.z;

		if(MC.options.keyUp.isDown())
		{
			x += forwardX * base;
			z += forwardZ * base;
		}else if(MC.options.keyDown.isDown())
		{
			x -= forwardX * base;
			z -= forwardZ * base;
		}

		double vertical = 0.04 * height.getValue();
		if(MC.options.keyJump.isDown())
			y += vertical;
		else if(MC.options.keyShift.isDown())
			y -= vertical;

		MC.player.setDeltaMovement(x, y, z);
	}

	private void useFirework()
	{
		if(!autoFirework.isChecked() || fireworkTimer > 0)
			return;

		InteractionHand hand;
		if(MC.player.getOffhandItem().is(Items.FIREWORK_ROCKET))
			hand = InteractionHand.OFF_HAND;
		else
		{
			if(!ensureFireworkSelected())
				return;
			hand = InteractionHand.MAIN_HAND;
		}

		MC.gameMode.useItem(MC.player, hand);
		fireworkTimer = fireworkDelay.getValueI();
	}

	private boolean ensureFireworkSelected()
	{
		if(MC.player.getMainHandItem().is(Items.FIREWORK_ROCKET))
			return true;

		int maxSlot = searchInventory.isChecked() ? 36 : 9;
		int slot = InventoryUtils.indexOf(Items.FIREWORK_ROCKET, maxSlot);
		if(slot == -1)
			return false;

		if(previousSlot < 0)
			previousSlot = MC.player.getInventory().selected;

		InventoryUtils.selectItem(slot);
		return MC.player.getMainHandItem().is(Items.FIREWORK_ROCKET);
	}

	private void doTakeoff()
	{
		if(MC.player.onGround())
		{
			if(jumpTimer > 0)
				return;

			MC.player.setJumping(false);
			MC.player.setSprinting(true);
			MC.player.jumpFromGround();
			jumpTimer = 20;
		}

		if(takeoffTimer > 0)
			return;

		sendStartStopPacket();
		takeoffTimer = 5;
	}

	private void sendStartStopPacket()
	{
		if(MC.getConnection() == null)
			return;

		MC.player.connection.send(new ServerboundPlayerCommandPacket(MC.player,
			ServerboundPlayerCommandPacket.Action.START_FALL_FLYING));
	}
}
