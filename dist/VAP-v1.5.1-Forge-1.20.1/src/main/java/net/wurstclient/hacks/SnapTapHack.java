/*
 * Copyright (c) 2026 WurstB+ Plus contributors
 *
 * This source code is subject to the terms of the GNU Affero General Public
 * License, version 3. If a copy of the AGPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/agpl-3.0.txt
 */
// Ported from Soar Client v7.1.2 (LGPL-3.0).
package net.wurstclient.hacks;

import net.wurstclient.Category;
import net.wurstclient.SearchTags;
import net.wurstclient.hack.Hack;

/**
 * Snap Tap / SOCD resolution for A-D and W-S.
 *
 * <p>The most recently pressed key wins while both directions are held. See
 * KeyBindingMixin / KeyMappingMixin.
 */
@SearchTags({"snap tap", "snaptap", "socd", "strafe", "keyboard"})
public final class SnapTapHack extends Hack
{
	private long leftPressTime;
	private long rightPressTime;
	private long forwardPressTime;
	private long backPressTime;

	public SnapTapHack()
	{
		super("SnapTap");
		setCategory(Category.MOVEMENT);
	}

	@Override
	protected void onDisable()
	{
		leftPressTime = 0;
		rightPressTime = 0;
		forwardPressTime = 0;
		backPressTime = 0;
	}

	public long getLeftPressTime()
	{
		return leftPressTime;
	}

	public void setLeftPressTime(long time)
	{
		leftPressTime = time;
	}

	public long getRightPressTime()
	{
		return rightPressTime;
	}

	public void setRightPressTime(long time)
	{
		rightPressTime = time;
	}

	public long getForwardPressTime()
	{
		return forwardPressTime;
	}

	public void setForwardPressTime(long time)
	{
		forwardPressTime = time;
	}

	public long getBackPressTime()
	{
		return backPressTime;
	}

	public void setBackPressTime(long time)
	{
		backPressTime = time;
	}
}
