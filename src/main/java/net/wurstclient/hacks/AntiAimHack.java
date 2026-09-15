/*
 * Copyright (c) 2025 Penguin
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 */
package net.wurstclient.hacks;

import java.util.Random;

import net.minecraft.client.KeyMapping;
import net.wurstclient.Category;
import net.wurstclient.SearchTags;
import net.wurstclient.events.UpdateListener;
import net.wurstclient.hack.Hack;
import net.wurstclient.mixinterface.IKeyBinding;
import net.wurstclient.settings.CheckboxSetting;
import net.wurstclient.settings.EnumSetting;
import net.wurstclient.settings.SliderSetting;
import net.wurstclient.settings.SliderSetting.ValueDisplay;
import net.wurstclient.util.Rotation;

@SearchTags({"anti aim", "spinbot", "headroll", "invert", "SpinBot"})
public final class AntiAimHack extends Hack implements UpdateListener
{
	private static final Random RANDOM = new Random();

	private final EnumSetting<Mode> mode = new EnumSetting<>("Mode",
		"\u00a7lSpin\u00a7r - Spins your head around continuously.\n"
			+ "\u00a7lJitter\u00a7r - Randomly jitters your head.\n"
			+ "\u00a7lInvert\u00a7r - Faces the opposite direction.\n"
			+ "\u00a7lDown\u00a7r - Stares at the ground.\n"
			+ "\u00a7lBackwards\u00a7r - Runs backwards while looking forward.",
		Mode.values(), Mode.SPIN);

	private final SliderSetting spinSpeed = new SliderSetting("Spin Speed",
		"Degrees per tick for Spin mode.", 10, 1, 180, 1,
		ValueDisplay.DEGREES.withSuffix("/tick"));

	private final CheckboxSetting silent = new CheckboxSetting("Silent rotation",
		"只把假朝向发给服务器，本地视角保持不动。（参考实现里的 ServerSide 开关；"
			+ "关掉时和以前一样，连你自己的视角一起转。）",
		false);

	private float yaw;
	private float pitch;
	private float baseYaw;

	public AntiAimHack()
	{
		super("AntiAim");
		setCategory(Category.FUN);
		addSetting(mode);
		addSetting(spinSpeed);
		addSetting(silent);
	}

	@Override
	public String getRenderName()
	{
		return getName() + " [" + mode.getSelected() + "]";
	}

	@Override
	protected void onEnable()
	{
		yaw = MC.player.getYRot();
		pitch = MC.player.getXRot();
		baseYaw = yaw;
		EVENTS.add(UpdateListener.class, this);
	}

	@Override
	protected void onDisable()
	{
		EVENTS.remove(UpdateListener.class, this);
		releaseMovementKeys();
	}

	@Override
	public void onUpdate()
	{
		switch(mode.getSelected())
		{
			case SPIN:
			yaw += spinSpeed.getValueF();
			if(yaw > 360) yaw -= 360;
			applyRotation(yaw, MC.player.getXRot());
			break;

			case JITTER:
			yaw += RANDOM.nextFloat() * 60 - 30;
			pitch += RANDOM.nextFloat() * 20 - 10;
			if(yaw > 360) yaw -= 360;
			if(yaw < 0) yaw += 360;
			pitch = (float)Math.max(-90, Math.min(90, pitch));
			applyRotation(yaw, pitch);
			break;

			case INVERT:
			// 旧实现是每 tick 执行 "当前朝向 + 180"，而"当前朝向"正是自己上一
			// tick 写回去的值 ⇒ 视角每 tick 翻 180°（20Hz 抖动），既不是"面朝
			// 反方向"，也不跟随鼠标。改成以开启时的朝向为基准稳定朝反方向
			// （和 SPIN 一样锁定视角）。
			applyRotation(baseYaw + 180, MC.player.getXRot());
			break;

			case DOWN:
			applyRotation(MC.player.getYRot(), 90);
			break;

			case BACKWARDS:
			mirrorMovementKeys();
			break;
		}
	}

	/**
	 * 按当前设置写回假朝向。
	 *
	 * <p>
	 * {@code Silent rotation} 打开时只发旋转包（与 {@code DerpHack} /
	 * {@code HeadRollHack} / {@code TiredHack} 同一条路径），本地视角完全不动 ——
	 * 原版不会因此补发真实朝向，因为 {@code sendPosition()} 比较的是客户端自己的
	 * 朝向，而我们没有改它。
	 */
	private void applyRotation(float newYaw, float newPitch)
	{
		if(silent.isChecked())
		{
			new Rotation(newYaw, newPitch).sendPlayerLookPacket();
			MC.player.setYHeadRot(newYaw);
			MC.player.yBodyRot = newYaw;
			return;
		}

		MC.player.setYRot(newYaw);
		MC.player.setXRot(newPitch);
		MC.player.setYHeadRot(newYaw);
		MC.player.yBodyRot = newYaw;
		MC.player.yRotO = newYaw;
	}

	/**
	 * BACKWARDS 模式：把移动键映射到相反方向。
	 *
	 * <p>
	 * 旧实现直接取反 {@code player.input.forwardImpulse / leftImpulse}，但原版在
	 * 同一个 tick 稍后会用**按键状态**把这些值整个重算一遍
	 * （{@code LocalPlayer.aiStep()} → {@code KeyboardInput.tick()}，
	 * 1.20.2 反编译源 {@code LocalPlayer.java:692}），而 UpdateEvent 是在
	 * {@code LocalPlayer.tick()} 的 {@code super.tick()} 之前触发的
	 * （{@code mixin/ClientPlayerEntityMixin.java:64-70}）⇒ 旧写法必然被覆盖，
	 * 该模式实际没有任何效果。
	 *
	 * <p>
	 * 改成镜像按键（与本工程 {@code AutoWalkHack} 设置 keyUp 用的是同一机制）：
	 * 先按玩家真实按键状态复位，再两两交换，这样每 tick 都从物理输入重新推导，
	 * 不会自己跟自己在两个状态之间反复交换。
	 */
	private void mirrorMovementKeys()
	{
		KeyMapping up = MC.options.keyUp;
		KeyMapping down = MC.options.keyDown;
		KeyMapping left = MC.options.keyLeft;
		KeyMapping right = MC.options.keyRight;

		for(KeyMapping key : new KeyMapping[]{up, down, left, right})
			IKeyBinding.get(key).resetPressedState();

		boolean forward = up.isDown();
		boolean backward = down.isDown();
		boolean strafeLeft = left.isDown();
		boolean strafeRight = right.isDown();

		up.setDown(backward);
		down.setDown(forward);
		left.setDown(strafeRight);
		right.setDown(strafeLeft);
	}

	/**
	 * 松开被镜像过的移动键，按玩家真实按键状态恢复
	 * （{@code mixin/KeyBindingMixin.java:29-38} 直接查 GLFW）。没有开过
	 * BACKWARDS 时调用它是无害的：恢复出来的就是玩家本来的按键状态。
	 */
	private void releaseMovementKeys()
	{
		for(KeyMapping key : new KeyMapping[]{MC.options.keyUp,
			MC.options.keyDown, MC.options.keyLeft, MC.options.keyRight})
			IKeyBinding.get(key).resetPressedState();
	}

	private enum Mode
	{
		SPIN("Spin"),
		JITTER("Jitter"),
		INVERT("Invert"),
		DOWN("Down"),
		BACKWARDS("Backwards");

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
