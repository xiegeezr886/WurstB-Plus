/*
 * This file contains a Forge/Mojmap adaptation of LiquidBounce's Velocity
 * Modify and JumpReset modes.
 *
 * Copyright (c) 2015-2026 CCBlueX
 * Copyright (c) 2025 Penguin
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 */
package net.wurstclient.hacks;

import java.util.concurrent.ThreadLocalRandom;
import net.minecraft.network.protocol.game.ClientboundExplodePacket;
import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket;
import net.minecraft.world.phys.Vec3;
import net.wurstclient.Category;
import net.wurstclient.SearchTags;
import net.wurstclient.events.PacketInputListener;
import net.wurstclient.events.UpdateListener;
import net.wurstclient.hack.Hack;
import net.wurstclient.mixin.ClientboundExplodePacketMixin;
import net.wurstclient.settings.CheckboxSetting;
import net.wurstclient.settings.EnumSetting;
import net.wurstclient.settings.SliderSetting;
import net.wurstclient.settings.SliderSetting.ValueDisplay;
import net.wurstclient.util.VelocityPlanner;
import net.wurstclient.util.VelocityPlanner.JumpResetDecision;
import net.wurstclient.util.VelocityPlanner.Trigger;

@SearchTags({"no velocity", "novelocity", "antivelocity", "velocity",
	"jump reset", "AntiKnockback", "anti knockback", "no knockback"})
public final class NoVelocityHack extends Hack
	implements PacketInputListener, UpdateListener
{
	private final EnumSetting<Mode> mode =
		new EnumSetting<>("Mode", Mode.values(), Mode.MODIFY);

	private final SliderSetting horizontal = new SliderSetting("Horizontal",
		"Horizontal knockback multiplier. Negative values reverse knockback.",
		0, -100, 100, 1, ValueDisplay.PERCENTAGE);

	private final SliderSetting vertical = new SliderSetting("Vertical",
		"Vertical knockback multiplier. Negative values reverse knockback.", 0,
		-100, 100, 1, ValueDisplay.PERCENTAGE);

	private final SliderSetting retainHorizontal = new SliderSetting(
		"Retain horizontal motion",
		"Current horizontal movement retained when Horizontal is zero.", 100,
		0, 100, 1, ValueDisplay.PERCENTAGE);

	private final SliderSetting retainVertical = new SliderSetting(
		"Retain vertical motion",
		"Current vertical movement retained when Vertical is zero.", 100, 0,
		100, 1, ValueDisplay.PERCENTAGE);

	private final SliderSetting chance = new SliderSetting("Chance",
		"Percentage of matching velocity events to process.", 100, 0, 100, 1,
		ValueDisplay.PERCENTAGE);

	private final EnumSetting<Trigger> trigger = new EnumSetting<>("Trigger",
		Trigger.values(), Trigger.ALWAYS);

	private final CheckboxSetting onlyMoving = new CheckboxSetting(
		"Only while moving", "Only processes velocity while movement input is held.",
		false);

	private final CheckboxSetting explosions = new CheckboxSetting(
		"Explosions", "Applies Modify mode to explosion knockback.", true);

	private final CheckboxSetting allowInFluid = new CheckboxSetting(
		"While in fluids", "Allows velocity processing while in water or lava.",
		false);

	private final CheckboxSetting allowWhileFlying = new CheckboxSetting(
		"While fall flying", "Allows velocity processing while using an elytra.",
		false);

	private final SliderSetting jumpDelay = new SliderSetting("Jump delay",
		"Ticks to wait after knockback before performing JumpReset.", 0, 0, 10,
		1, ValueDisplay.INTEGER.withSuffix(" ticks"));

	private final CheckboxSetting requireSprint = new CheckboxSetting(
		"Require sprint", "Only performs JumpReset while sprinting.", true);

	private int pendingJumpTicks = -1;
	private int pendingJumpAge;
	private int pendingJumpMaximumAge;

	public NoVelocityHack()
	{
		super("NoVelocity");
		setCategory(Category.MOVEMENT);
		addSetting(mode);
		addSetting(horizontal);
		addSetting(vertical);
		addSetting(retainHorizontal);
		addSetting(retainVertical);
		addSetting(chance);
		addSetting(trigger);
		addSetting(onlyMoving);
		addSetting(explosions);
		addSetting(allowInFluid);
		addSetting(allowWhileFlying);
		addSetting(jumpDelay);
		addSetting(requireSprint);
	}

	@Override
	public String getRenderName()
	{
		return getName() + " [" + mode.getSelected() + "]";
	}

	@Override
	protected void onEnable()
	{
		clearPendingJump();
		EVENTS.add(PacketInputListener.class, this);
		EVENTS.add(UpdateListener.class, this);
	}

	@Override
	protected void onDisable()
	{
		EVENTS.remove(PacketInputListener.class, this);
		EVENTS.remove(UpdateListener.class, this);
		clearPendingJump();
	}

	@Override
	public void onReceivedPacket(PacketInputEvent event)
	{
		if(MC.player == null)
			return;

		if(event.getPacket() instanceof ClientboundSetEntityMotionPacket packet
			&& packet.getId() == MC.player.getId())
			handleEntityVelocity(event, packet);
		else if(event.getPacket() instanceof ClientboundExplodePacket packet
			&& explosions.isChecked() && mode.getSelected() == Mode.MODIFY
			&& shouldApply())
			handleExplosionVelocity(packet);
	}

	private void handleEntityVelocity(PacketInputEvent event,
		ClientboundSetEntityMotionPacket packet)
	{
		Vec3 incoming = new Vec3(packet.getXa() / 8000.0,
			packet.getYa() / 8000.0, packet.getZa() / 8000.0);
		if(!shouldApply())
			return;

		if(mode.getSelected() == Mode.JUMP_RESET)
		{
			if(!VelocityPlanner.isFallDamageVelocity(incoming))
			{
				pendingJumpTicks = jumpDelay.getValueI();
				pendingJumpAge = 0;
				pendingJumpMaximumAge = pendingJumpTicks + 2;
			}
			return;
		}

		Vec3 modified = VelocityPlanner.modify(incoming,
			MC.player.getDeltaMovement(), horizontal.getValue() / 100,
			vertical.getValue() / 100, retainHorizontal.getValue() / 100,
			retainVertical.getValue() / 100);
		MC.player.setDeltaMovement(modified);
		event.cancel();
	}

	private void handleExplosionVelocity(ClientboundExplodePacket packet)
	{
		double horizontalMultiplier = horizontal.getValue() / 100;
		double verticalMultiplier = vertical.getValue() / 100;
		ClientboundExplodePacketMixin accessor =
			(ClientboundExplodePacketMixin)(Object)packet;
		accessor.wurst_setKnockbackX(
			(float)(packet.getKnockbackX() * horizontalMultiplier));
		accessor.wurst_setKnockbackY(
			(float)(packet.getKnockbackY() * verticalMultiplier));
		accessor.wurst_setKnockbackZ(
			(float)(packet.getKnockbackZ() * horizontalMultiplier));
	}

	private boolean shouldApply()
	{
		boolean moving = MC.player.input.getMoveVector().length() > 1.0E-5F;
		return VelocityPlanner.shouldApply(chance.getValueI(),
			ThreadLocalRandom.current().nextInt(100), onlyMoving.isChecked(),
			moving, trigger.getSelected(), MC.player.onGround(),
			MC.player.isInWaterOrBubble() || MC.player.isInLava(),
			allowInFluid.isChecked(), MC.player.isFallFlying(),
			allowWhileFlying.isChecked());
	}

	@Override
	public void onUpdate()
	{
		if(mode.getSelected() != Mode.JUMP_RESET)
		{
			clearPendingJump();
			return;
		}
		if(pendingJumpTicks < 0)
			return;
		if(MC.player == null)
		{
			clearPendingJump();
			return;
		}

		boolean moving = MC.player.input.getMoveVector().length() > 1.0E-5F;
		JumpResetDecision decision = VelocityPlanner.evaluateJumpReset(
			pendingJumpTicks, pendingJumpAge, pendingJumpMaximumAge,
			MC.player.onGround(), moving, onlyMoving.isChecked(),
			MC.player.isSprinting(), requireSprint.isChecked());
		if(decision == JumpResetDecision.WAIT)
		{
			pendingJumpTicks--;
			pendingJumpAge++;
			return;
		}
		if(decision == JumpResetDecision.JUMP)
			MC.player.jumpFromGround();
		clearPendingJump();
	}

	private void clearPendingJump()
	{
		pendingJumpTicks = -1;
		pendingJumpAge = 0;
		pendingJumpMaximumAge = 0;
	}

	private enum Mode
	{
		MODIFY("Modify"),
		JUMP_RESET("JumpReset");

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
