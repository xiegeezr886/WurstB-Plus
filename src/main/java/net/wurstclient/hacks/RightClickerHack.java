/*
 * Copyright (c) 2025-2026 Penguin
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 */
package net.wurstclient.hacks;

import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.wurstclient.Category;
import net.wurstclient.SearchTags;
import net.wurstclient.events.UpdateListener;
import net.wurstclient.hack.Hack;
import net.wurstclient.settings.CheckboxSetting;
import net.wurstclient.settings.PauseAttackOnContainersSetting;
import net.wurstclient.settings.SliderSetting;
import net.wurstclient.settings.SliderSetting.ValueDisplay;
import net.wurstclient.settings.SwingHandSetting;
import net.wurstclient.settings.SwingHandSetting.SwingHand;
import net.wurstclient.util.AttackTimerPolicy;
import net.wurstclient.util.InteractionSimulator;

@SearchTags({"right clicker", "right autoclicker", "use clicker"})
public final class RightClickerHack extends Hack implements UpdateListener
{
	private final SliderSetting minCps = new SliderSetting("Minimum CPS", 7, 1,
		20, 0.5, ValueDisplay.DECIMAL);
	private final SliderSetting maxCps = new SliderSetting("Maximum CPS", 13, 1,
		20, 0.5, ValueDisplay.DECIMAL);
	private final CheckboxSetting holdToClick =
		new CheckboxSetting("Hold to click", true);
	private final SliderSetting startDelay = new SliderSetting("Start delay", 0,
		0, 1000, 10, ValueDisplay.INTEGER.withSuffix("ms"));
	private final CheckboxSetting onlyWithItem = new CheckboxSetting(
		"Only with item", "Requires an item in either hand.", true);
	private final PauseAttackOnContainersSetting pauseOnContainers =
		new PauseAttackOnContainersSetting(true);
	private final SwingHandSetting swingHand =
		new SwingHandSetting(this, SwingHand.CLIENT);

	private final RandomSource random = RandomSource.createNewThreadLocalInstance();
	private boolean active;
	private long nextClickNanos;

	public RightClickerHack()
	{
		super("RightClicker");
		setCategory(Category.COMBAT);
		addSetting(minCps);
		addSetting(maxCps);
		addSetting(holdToClick);
		addSetting(startDelay);
		addSetting(onlyWithItem);
		addSetting(pauseOnContainers);
		addSetting(swingHand);
	}

	@Override
	protected void onEnable()
	{
		active = false;
		EVENTS.add(UpdateListener.class, this);
	}

	@Override
	protected void onDisable()
	{
		EVENTS.remove(UpdateListener.class, this);
		active = false;
	}

	@Override
	public void onUpdate()
	{
		if(!canClick())
		{
			active = false;
			return;
		}

		long now = System.nanoTime();
		if(!active)
		{
			active = true;
			nextClickNanos = AttackTimerPolicy.deadline(now,
				(long)(startDelay.getValue() * 1_000_000D));
		}
		if(!AttackTimerPolicy.isElapsed(now, nextClickNanos)
			|| MC.player.isUsingItem())
			return;

		if(MC.hitResult instanceof BlockHitResult blockHit
			&& blockHit.getType() == HitResult.Type.BLOCK)
			InteractionSimulator.rightClickBlock(blockHit, swingHand.getSelected());
		else
			InteractionSimulator.rightClickItem(swingHand.getSelected());

		scheduleNextClick(now);
	}

	private boolean canClick()
	{
		if(MC.player == null || MC.level == null || MC.gameMode == null
			|| pauseOnContainers.shouldPause())
			return false;
		if(holdToClick.isChecked() && !MC.options.keyUse.isDown())
			return false;
		return !onlyWithItem.isChecked() || !MC.player.getMainHandItem().isEmpty()
			|| !MC.player.getOffhandItem().isEmpty();
	}

	private void scheduleNextClick(long now)
	{
		double low = Math.min(minCps.getValue(), maxCps.getValue());
		double high = Math.max(minCps.getValue(), maxCps.getValue());
		double cps = Mth.lerp(random.nextDouble(), low, high);
		nextClickNanos = AttackTimerPolicy.deadline(now,
			AttackTimerPolicy.delayNanos(cps, 0, 0));
	}
}
