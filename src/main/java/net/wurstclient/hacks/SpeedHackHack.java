/*
 * Copyright (c) 2014-2025 Wurst-Imperium and contributors.
 * Copyright (c) 2015-2026 CCBlueX
 * Copyright (c) 2025 Penguin
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 */
package net.wurstclient.hacks;

import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.phys.Vec3;
import net.wurstclient.Category;
import net.wurstclient.SearchTags;
import net.wurstclient.events.UpdateListener;
import net.wurstclient.hack.Hack;
import net.wurstclient.hack.HackConflictGroup;
import net.wurstclient.hacks.speed.BrutalSpeedMode;
import net.wurstclient.hacks.speed.LowHopSpeedMode;
import net.wurstclient.hacks.speed.NcpBhopSpeedMode;
import net.wurstclient.hacks.speed.OnGroundSpeedMode;
import net.wurstclient.hacks.speed.SpeedMode;
import net.wurstclient.hacks.speed.StrafeSpeedMode;
import net.wurstclient.settings.CheckboxSetting;
import net.wurstclient.settings.EnumSetting;
import net.wurstclient.settings.SliderSetting;
import net.wurstclient.settings.SliderSetting.ValueDisplay;
import net.wurstclient.util.MovementPlanner;

@SearchTags({"speed hack", "bhop", "strafe"})
public final class SpeedHackHack extends Hack implements UpdateListener
{
	private static final double BASE_SPEED = 0.2873;

	private final EnumSetting<Mode> mode = new EnumSetting<>("Mode",
		"\u00a7lNCP Bhop\u00a7r - Directional bunny hop.\n"
			+ "\u00a7lStrafe\u00a7r - Smoothly redirects current velocity.\n"
			+ "\u00a7lLowHop\u00a7r - Uses a short jump arc.\n"
			+ "\u00a7lOnGround\u00a7r - Only modifies grounded movement.\n"
			+ "\u00a7lBrutal\u00a7r - Direct high-speed bunny hop.",
		Mode.values(), Mode.LOW_HOP);

	private final SliderSetting speed = new SliderSetting("Speed",
		"Movement speed multiplier.", 2.0, 1.0, 10.0, 0.05,
		ValueDisplay.DECIMAL);

	private final SliderSetting strafeSpeed = new SliderSetting(
		"Strafe strength", "How quickly air movement follows input.", 0.4,
		0.01, 1.0, 0.01, ValueDisplay.DECIMAL);

	private final CheckboxSetting autoJump = new CheckboxSetting("Auto jump",
		"Automatically jumps while moving in air-capable modes.", true);
	private final CheckboxSetting whileUsingItems = new CheckboxSetting(
		"While using items", "Allows speed control during item-use slowdown.",
		false);

	public SpeedHackHack()
	{
		super("SpeedHack");
		setCategory(Category.MOVEMENT);
		addConflictGroup(HackConflictGroup.MOVEMENT_CONTROL);
		addSetting(mode);
		addSetting(speed);
		addSetting(strafeSpeed);
		addSetting(autoJump);
		addSetting(whileUsingItems);
	}

	@Override
	public String getRenderName()
	{
		return getName() + " [" + mode.getSelected() + "]";
	}

	@Override
	protected void onEnable()
	{
		resetModeState();
		EVENTS.add(UpdateListener.class, this);
	}

	@Override
	protected void onDisable()
	{
		EVENTS.remove(UpdateListener.class, this);
		resetModeState();
	}

	@Override
	public void onUpdate()
	{
		LocalPlayer player = MC.player;
		if(!canControl(player))
		{
			resetModeState();
			return;
		}

		float forward = player.input.forwardImpulse;
		float sideways = player.input.leftImpulse;
		if(!MovementPlanner.isMoving(forward, sideways))
		{
			resetModeState();
			return;
		}
		if(mode.getSelected() != Mode.LOW_HOP)
			resetModeState();

		double targetSpeed =
			BASE_SPEED * speed.getValue() * speedEffectFactor(player);

		// 具体怎么把速度写到玩家身上交给模式类（见 net.wurstclient.hacks.speed）。
		mode.getSelected().impl().apply(this, player, forward, sideways,
			targetSpeed);
	}

	/** 清掉所有模式的状态（只有 LowHop 有状态，其余是空操作）。 */
	private void resetModeState()
	{
		for(Mode m : Mode.values())
			m.impl().reset();
	}

	private boolean canControl(LocalPlayer player)
	{
		return player != null && !player.isShiftKeyDown() && !player.isPassenger()
			&& !player.onClimbable() && !player.isFallFlying()
			&& !player.getAbilities().flying && !player.isInWaterOrBubble()
			&& !player.isInLava()
			&& (whileUsingItems.isChecked() || !player.isUsingItem());
	}

	/**
	 * 速度药水带来的倍率，没有药水时是 1（也就是不改变原有行为）。
	 *
	 * <p>
	 * 修的是什么：旧实现的目标速度是固定常数 {@code BASE_SPEED * Speed}。
	 * 喝了速度药水的玩家实际水平速度会高于它，于是
	 * {@link MovementPlanner#clampControlledHorizontal} 的「保留现有动量」
	 * 分支每 tick 都命中、直接原样返回当前水平速度，玩家的转向输入被丢掉——
	 * 表现是「按住 W+D 也只会沿原方向飞」。乘上药水倍率后目标速度追上实际
	 * 速度，该分支不再命中，转向恢复正常，速度也正好是「原版疾跑 × 药水 ×
	 * Speed 倍数」。
	 */
	private static double speedEffectFactor(LocalPlayer player)
	{
		MobEffectInstance effect = player.getEffect(MobEffects.MOVEMENT_SPEED);
		return effect == null ? 1
			: MovementPlanner.effectSpeedFactor(effect.getAmplifier());
	}

	/**
	 * 地面用目标速度、空中按 {@code airStrength} 平滑（NCP Bhop 与 Brutal 共用，
	 * 所以留在 hack 上给模式类调用）。
	 */
	public void applyHop(LocalPlayer player, float forward, float sideways,
		double targetSpeed, double jumpMotion, double airStrength)
	{
		Vec3 current = player.getDeltaMovement();
		Vec3 movement = current;
		if(player.onGround())
		{
			movement = MovementPlanner.setHorizontal(movement, forward, sideways,
				player.getYRot(), targetSpeed);
			if(autoJump.isChecked())
				movement = new Vec3(movement.x, jumpMotion, movement.z);
		}else
			movement = MovementPlanner.blendHorizontal(movement, forward, sideways,
				player.getYRot(), targetSpeed, airStrength);

		player.setDeltaMovement(MovementPlanner.clampControlledHorizontal(current,
			movement, targetSpeed));
	}

	/** 供模式类读取 Auto jump 设置。 */
	public boolean isAutoJump()
	{
		return autoJump.isChecked();
	}

	/** 供模式类读取 Strafe speed 设置。 */
	public double getStrafeSpeed()
	{
		return strafeSpeed.getValue();
	}

	/** 供 Brutal 模式读取 Speed 设置。 */
	public double getSpeedSetting()
	{
		return speed.getValue();
	}

	private enum Mode
	{
		NCP_BHOP("NCP Bhop", new NcpBhopSpeedMode()),
		STRAFE("Strafe", new StrafeSpeedMode()),
		LOW_HOP("LowHop", new LowHopSpeedMode()),
		ON_GROUND("OnGround", new OnGroundSpeedMode()),
		BRUTAL("Brutal", new BrutalSpeedMode());

		private final String name;
		private final SpeedMode impl;

		Mode(String name, SpeedMode impl)
		{
			this.name = name;
			this.impl = impl;
		}

		public SpeedMode impl()
		{
			return impl;
		}

		@Override
		public String toString()
		{
			return name;
		}
	}
}
