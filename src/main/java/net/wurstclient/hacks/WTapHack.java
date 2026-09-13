/*
 * Copyright (c) 2025-2026 Penguin
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 */
package net.wurstclient.hacks;

import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.wurstclient.Category;
import net.wurstclient.SearchTags;
import net.wurstclient.events.PlayerAttacksEntityListener;
import net.wurstclient.events.UpdateListener;
import net.wurstclient.hack.Hack;
import net.wurstclient.mixinterface.IKeyBinding;
import net.wurstclient.settings.CheckboxSetting;
import net.wurstclient.settings.SliderSetting;
import net.wurstclient.settings.SliderSetting.ValueDisplay;
import net.wurstclient.util.AttackTimerPolicy;

@SearchTags({"w tap", "sprint reset", "knockback"})
public final class WTapHack extends Hack
	implements PlayerAttacksEntityListener, UpdateListener
{
	private final SliderSetting chance = new SliderSetting("Chance", 100, 0,
		100, 1, ValueDisplay.PERCENTAGE);
	private final SliderSetting releaseDelay = new SliderSetting("Release delay",
		0, 0, 500, 5, ValueDisplay.INTEGER.withSuffix("ms"));
	private final SliderSetting rePressDelay = new SliderSetting("Re-press delay",
		50, 0, 500, 5, ValueDisplay.INTEGER.withSuffix("ms"));
	private final CheckboxSetting selectHits = new CheckboxSetting("Select hits",
		"Only resets sprint when the target can take another hit.", true);

	private final RandomSource random = RandomSource.createNewThreadLocalInstance();
	private Stage stage = Stage.IDLE;
	private long deadlineNanos;

	public WTapHack()
	{
		super("WTap");
		setCategory(Category.COMBAT);
		addSetting(chance);
		addSetting(releaseDelay);
		addSetting(rePressDelay);
		addSetting(selectHits);
	}

	@Override
	protected void onEnable()
	{
		stage = Stage.IDLE;
		EVENTS.add(PlayerAttacksEntityListener.class, this);
		EVENTS.add(UpdateListener.class, this);
	}

	@Override
	protected void onDisable()
	{
		EVENTS.remove(PlayerAttacksEntityListener.class, this);
		EVENTS.remove(UpdateListener.class, this);
		restoreForwardKey();
	}

	@Override
	public void onPlayerAttacksEntity(Entity target)
	{
		if(!(target instanceof LivingEntity living) || stage != Stage.IDLE)
			return;
		if(selectHits.isChecked() && living.invulnerableTime > 14)
			return;
		if(!canSprint())
			return;
		if(random.nextDouble() * 100 >= chance.getValue())
			return;

		stage = Stage.WAITING_TO_RELEASE;
		deadlineNanos = deadlineAfterMillis(releaseDelay.getValue());
		advance(System.nanoTime());
	}
	
	/**
	 * 饥饿值低于 6 时原版根本不会进入疾跑状态（`Player.canSprint` 的条件之一），
	 * 这时再松开/重按前进键只会白白停一下移动，换不到任何击退收益。
	 *
	 * <p>
	 * 这一条取自参考项目 CakeSlayers/OpenEpsilon 的 WTap
	 * （见 {@code _oe_ref/core-spec.md} §14：其条件之一就是 {@code foodLevel < 6} 丢弃）。
	 *
	 * <p>
	 * 注意本 hack 与参考的**实现方式不同**，而且是有意为之：参考拦下 ATTACK 包并补发
	 * {@code START_SPRINTING → STOP_SPRINTING → START_SPRINTING} 三个动作包，属于包级
	 * 手法；本 hack 是模拟按键（松开前进键再重按），更接近真人输入。所以这里**只借用了
	 * 这个前置判断，没有换掉实现**——换成连发疾跑包会在反作弊里明显得多。
	 */
	private boolean canSprint()
	{
		return MC.player != null
			&& MC.player.getFoodData().getFoodLevel() >= 6;
	}

	@Override
	public void onUpdate()
	{
		if(MC.screen == null)
			advance(System.nanoTime());
	}

	private void advance(long nowNanos)
	{
		if(!AttackTimerPolicy.isElapsed(nowNanos, deadlineNanos))
			return;

		IKeyBinding forward = IKeyBinding.get(MC.options.keyUp);
		if(stage == Stage.WAITING_TO_RELEASE)
		{
			forward.setPressed(false);
			stage = Stage.WAITING_TO_REPRESS;
			deadlineNanos = deadlineAfterMillis(rePressDelay.getValue());
		}else if(stage == Stage.WAITING_TO_REPRESS)
		{
			forward.resetPressedState();
			stage = Stage.IDLE;
		}
	}

	private long deadlineAfterMillis(double delayMillis)
	{
		long delayNanos = (long)(Math.max(0, delayMillis) * 1_000_000D);
		return AttackTimerPolicy.deadline(System.nanoTime(), delayNanos);
	}

	private void restoreForwardKey()
	{
		if(MC.options != null)
			IKeyBinding.get(MC.options.keyUp).resetPressedState();
		stage = Stage.IDLE;
	}

	private enum Stage
	{
		IDLE,
		WAITING_TO_RELEASE,
		WAITING_TO_REPRESS
	}
}
