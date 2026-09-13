/*
 * Copyright (c) 2014-2025 Wurst-Imperium and contributors.
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 */
package net.wurstclient.settings;

import net.minecraft.util.RandomSource;
import net.wurstclient.WurstClient;

public final class AttackSpeedSliderSetting extends SliderSetting
{
	private final RandomSource random = RandomSource.createNewThreadLocalInstance();
	private long nextAttackNanos;
	
	public AttackSpeedSliderSetting()
	{
		this("Speed", "description.wurst.setting.generic.attack_speed");
	}
	
	public AttackSpeedSliderSetting(String name, String description)
	{
		super(name, description, 0, 0, 20, 0.1,
			ValueDisplay.DECIMAL.withLabel(0, "auto"));
	}
	
	@Override
	public float[] getKnobColor()
	{
		if(getValue() == 0)
			return new float[]{0, 0.5F, 1};
		
		return super.getKnobColor();
	}
	
	public void resetTimer()
	{
		resetTimer(0);
	}
	
	public void resetTimer(double maxRandMS)
	{
		double value = getValue();
		double baseDelayMS = value <= 0 ? 0 : 1000 / value;
		double randomOffsetMS = maxRandMS <= 0 ? 0
			: random.nextGaussian() * maxRandMS;
		double delayMS = Math.max(0, baseDelayMS + randomOffsetMS);
		nextAttackNanos =
			System.nanoTime() + (long)(delayMS * 1_000_000L);
	}
	
	public void updateTimer()
	{
		// Kept for source compatibility with existing combat modules.
	}
	
	public boolean isTimeToAttack()
	{
		double value = getValue();
		if(value <= 0 && WurstClient.MC.player.getAttackStrengthScale(0) < 1)
			return false;
		
		return System.nanoTime() - nextAttackNanos >= 0;
	}
}
