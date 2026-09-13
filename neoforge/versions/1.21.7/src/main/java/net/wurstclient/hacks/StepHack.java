/*
 * Copyright (c) 2014-2025 Wurst-Imperium and contributors.
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 */
package net.wurstclient.hacks;

import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec2;
import net.wurstclient.Category;
import net.wurstclient.events.UpdateListener;
import net.wurstclient.hack.Hack;
import net.wurstclient.settings.EnumSetting;
import net.wurstclient.settings.SliderSetting;
import net.wurstclient.settings.SliderSetting.ValueDisplay;
import net.wurstclient.util.BlockUtils;
import net.wurstclient.util.MovementPlanner;

public final class StepHack extends Hack implements UpdateListener
{
	private final EnumSetting<Mode> mode = new EnumSetting<>("Mode",
		"\u00a7lSimple\u00a7r mode can step up multiple blocks (enables Height slider).\n"
			+ "\u00a7lLegit\u00a7r mode can bypass NoCheat+.",
		Mode.values(), Mode.LEGIT);
	
	private final SliderSetting height =
		new SliderSetting("Height", "Only works in \u00a7lSimple\u00a7r mode.",
			1, 1, 5, 1, ValueDisplay.INTEGER);

	private float previousStepHeight = 0.6F;
	private int stepCooldown;
	private LocalPlayer trackedPlayer;
	
	public StepHack()
	{
		super("Step");
		setCategory(Category.MOVEMENT);
		addSetting(mode);
		addSetting(height);
	}
	
	@Override
	protected void onEnable()
	{
		trackedPlayer = null;
		trackPlayer(MC.player);
		stepCooldown = 0;
		EVENTS.add(UpdateListener.class, this);
	}
	
	@Override
	protected void onDisable()
	{
		EVENTS.remove(UpdateListener.class, this);
		restoreTrackedPlayer();
	}
	
	@Override
	public void onUpdate()
	{
		LocalPlayer player = MC.player;
		if(player == null || MC.level == null)
			return;
		trackPlayer(player);
		if(stepCooldown > 0)
			stepCooldown--;

		if(mode.getSelected() == Mode.SIMPLE)
		{
			setStepHeight(player, height.getValueF());
			return;
		}
		
		setStepHeight(player, previousStepHeight);
		
		if(stepCooldown > 0 || !player.horizontalCollision)
			return;
		
		if(!player.onGround() || player.onClimbable()
			|| player.isInWater() || player.isInLava())
			return;
		
		Vec2 moveVector = MovementPlanner.getMoveVector(player.input);
		if(moveVector.y == 0 && moveVector.x == 0)
			return;
		
		if(MC.options.keyJump.isDown())
			return;
		
		AABB box = player.getBoundingBox().move(0, 0.05, 0).inflate(0.05);
		
		if(!MC.level.noCollision(player, box.move(0, 1, 0)))
			return;
		
		double stepHeight = BlockUtils.getBlockCollisions(box)
			.mapToDouble(bb -> bb.maxY).max().orElse(Double.NEGATIVE_INFINITY);
		
		stepHeight -= player.getY();
		
		if(stepHeight <= 0.5 || stepHeight > 1)
			return;
		
		ClientPacketListener netHandler = player.connection;
		
		netHandler.send(new ServerboundMovePlayerPacket.Pos(
			player.getX(), player.getY() + 0.42 * stepHeight, player.getZ(),
			false, false));
		
		netHandler.send(new ServerboundMovePlayerPacket.Pos(
			player.getX(), player.getY() + 0.753 * stepHeight, player.getZ(),
			false, false));
		
		player.setPos(player.getX(), player.getY() + stepHeight,
			player.getZ());
		stepCooldown = 2;
	}

	private void trackPlayer(LocalPlayer player)
	{
		if(player == null || player == trackedPlayer)
			return;

		restoreTrackedPlayer();
		trackedPlayer = player;
		previousStepHeight = (float)player
			.getAttributeValue(Attributes.STEP_HEIGHT);
	}

	private void restoreTrackedPlayer()
	{
		if(trackedPlayer != null)
			setStepHeight(trackedPlayer, previousStepHeight);
		trackedPlayer = null;
	}

	private void setStepHeight(LocalPlayer player, float value)
	{
		AttributeInstance attribute = player.getAttribute(Attributes.STEP_HEIGHT);
		if(attribute != null)
			attribute.setBaseValue(value);
	}

	public float adjustStepHeight(float stepHeight)
	{
		if(isEnabled() && mode.getSelected() == Mode.SIMPLE)
			return height.getValueF();
		
		return stepHeight;
	}
	
	public boolean isAutoJumpAllowed()
	{
		return !isEnabled() && !WURST.getCmds().goToCmd.isActive();
	}
	
	private enum Mode
	{
		SIMPLE("Simple"),
		LEGIT("Legit");
		
		private final String name;
		
		private Mode(String name)
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
