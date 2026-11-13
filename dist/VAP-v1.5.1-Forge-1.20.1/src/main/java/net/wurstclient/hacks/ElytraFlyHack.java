/*
 * Copyright (c) 2025 Penguin
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 */
package net.wurstclient.hacks;

import net.minecraft.network.protocol.game.ServerboundPlayerCommandPacket;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ElytraItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;
import net.wurstclient.Category;
import net.wurstclient.SearchTags;
import net.wurstclient.events.UpdateListener;
import net.wurstclient.hack.Hack;
import net.wurstclient.hack.HackConflictGroup;
import net.wurstclient.settings.CheckboxSetting;
import net.wurstclient.settings.SliderSetting;
import net.wurstclient.settings.SliderSetting.ValueDisplay;

@SearchTags({"elytra fly", "elytra"})
public final class ElytraFlyHack extends Hack implements UpdateListener
{
	private final SliderSetting speed = new SliderSetting("Speed",
		"Forward flight speed multiplier.", 1.5, 0.1, 5, 0.05,
		ValueDisplay.DECIMAL);

	private final SliderSetting verticalSpeed = new SliderSetting(
		"Vertical speed", "Up/down movement speed.", 0.5, 0.05, 2, 0.05,
		ValueDisplay.DECIMAL);

	private final CheckboxSetting instantFly = new CheckboxSetting(
		"Instant fly", "Jump once to start flying instead of double-jumping.",
		true);

	private final CheckboxSetting stopInWater = new CheckboxSetting(
		"Stop flying in water", true);

	private int jumpTimer;

	public ElytraFlyHack()
	{
		super("ElytraFly");
		setCategory(Category.MOVEMENT);
		addConflictGroup(HackConflictGroup.MOVEMENT_CONTROL);
		addSetting(speed);
		addSetting(verticalSpeed);
		addSetting(instantFly);
		addSetting(stopInWater);
	}

	@Override
	protected void onEnable()
	{
		EVENTS.add(UpdateListener.class, this);
		jumpTimer = 0;
	}

	@Override
	protected void onDisable()
	{
		EVENTS.remove(UpdateListener.class, this);
	}

	@Override
	public void onUpdate()
	{
		if(jumpTimer > 0)
			jumpTimer--;

		ItemStack chest = MC.player.getItemBySlot(EquipmentSlot.CHEST);
		if(chest.getItem() != Items.ELYTRA)
			return;

		if(MC.player.isFallFlying())
		{
			if(stopInWater.isChecked() && MC.player.isInWater())
			{
				sendStartStopPacket();
				return;
			}

			controlFlight();
			return;
		}

		if(ElytraItem.isFlyEnabled(chest) && MC.options.keyJump.isDown())
			doInstantFly();
	}

	private void controlFlight()
	{
		float yaw = (float)Math.toRadians(MC.player.getYRot());
		double forwardX = -Mth.sin(yaw);
		double forwardZ = Mth.cos(yaw);

		Vec3 v = MC.player.getDeltaMovement();
		double motionX = v.x;
		double motionY = v.y;
		double motionZ = v.z;

		double base = 0.05 * speed.getValue();
		if(MC.options.keyUp.isDown())
		{
			motionX += forwardX * base;
			motionZ += forwardZ * base;
		}else if(MC.options.keyDown.isDown())
		{
			motionX -= forwardX * base;
			motionZ -= forwardZ * base;
		}else
		{
			// maintain minimum forward glide speed
			motionX = forwardX * base;
			motionZ = forwardZ * base;
		}

		double vert = 0.04 * verticalSpeed.getValue();
		if(MC.options.keyJump.isDown())
			motionY += vert;
		else if(MC.options.keyShift.isDown())
			motionY -= vert;

		MC.player.setDeltaMovement(motionX, motionY, motionZ);
	}

	private void doInstantFly()
	{
		if(!instantFly.isChecked())
			return;

		if(jumpTimer <= 0)
		{
			jumpTimer = 20;
			MC.player.setJumping(false);
			MC.player.setSprinting(true);
			MC.player.jumpFromGround();
		}

		sendStartStopPacket();
	}

	private void sendStartStopPacket()
	{
		ServerboundPlayerCommandPacket packet =
			new ServerboundPlayerCommandPacket(MC.player,
				ServerboundPlayerCommandPacket.Action.START_FALL_FLYING);
		MC.player.connection.send(packet);
	}
}
