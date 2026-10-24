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
import net.wurstclient.Category;
import net.wurstclient.SearchTags;
import net.wurstclient.events.PreMotionListener;
import net.wurstclient.events.UpdateListener;
import net.wurstclient.hack.Hack;
import net.wurstclient.hack.HackConflictGroup;
import net.wurstclient.mixinterface.IKeyBinding;
import net.wurstclient.settings.CheckboxSetting;
import net.wurstclient.settings.SliderSetting;
import net.wurstclient.settings.SliderSetting.ValueDisplay;
import net.wurstclient.util.AttackTimerPolicy;

@SearchTags({"auto clicker", "autoclicker", "left clicker", "连点器",
	"左键连点"})
public final class AutoClickerHack extends Hack
	implements UpdateListener, PreMotionListener
{
	private final SliderSetting minCps = new SliderSetting("Minimum CPS", 8, 1,
		20, 0.5, ValueDisplay.DECIMAL);
	private final SliderSetting maxCps = new SliderSetting("Maximum CPS", 12, 1,
		20, 0.5, ValueDisplay.DECIMAL);
	private final CheckboxSetting holdToClick =
		new CheckboxSetting("Hold to click", true);
	private final SliderSetting startDelay = new SliderSetting("Start delay", 0,
		0, 1000, 10, ValueDisplay.INTEGER.withSuffix("ms"));

	private final RandomSource random =
		RandomSource.createNewThreadLocalInstance();
	private boolean active;
	private boolean pressedByUs;
	private long nextClickNanos;

	public AutoClickerHack()
	{
		super("AutoClicker");
		setCategory(Category.COMBAT);
		addConflictGroup(HackConflictGroup.COMBAT_TARGETING);
		addSetting(minCps);
		addSetting(maxCps);
		addSetting(holdToClick);
		addSetting(startDelay);
	}

	@Override
	protected void onEnable()
	{
		active = false;
		pressedByUs = false;
		EVENTS.add(UpdateListener.class, this);
		EVENTS.add(PreMotionListener.class, this);
	}

	@Override
	protected void onDisable()
	{
		releaseClick();
		EVENTS.remove(UpdateListener.class, this);
		EVENTS.remove(PreMotionListener.class, this);
		active = false;
	}

	@Override
	public void onUpdate()
	{
		// Safety net in case PreMotion didn't run since the last click.
		releaseClick();

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
		if(!AttackTimerPolicy.isElapsed(now, nextClickNanos))
			return;

		IKeyBinding.get(MC.options.keyAttack).simulatePress(true);
		pressedByUs = true;
		scheduleNextClick(now);
	}

	@Override
	public void onPreMotion()
	{
		releaseClick();
	}

	private void releaseClick()
	{
		if(!pressedByUs)
			return;
		pressedByUs = false;

		IKeyBinding keyAttack = IKeyBinding.get(MC.options.keyAttack);
		keyAttack.simulatePress(false);
		// Don't cancel the player's real attack key if they're still holding it.
		keyAttack.resetPressedState();
	}

	private boolean canClick()
	{
		if(MC.player == null || MC.level == null || MC.gameMode == null)
			return false;
		if(MC.screen != null)
			return false;
		if(MC.player.isUsingItem())
			return false;
		if(holdToClick.isChecked() && !MC.options.keyAttack.isDown())
			return false;
		return true;
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
