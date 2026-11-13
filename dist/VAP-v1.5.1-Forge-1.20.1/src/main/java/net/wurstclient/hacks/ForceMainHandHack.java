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
import net.wurstclient.settings.EnumSetting;

@SearchTags({"force main hand", "main hand", "left hand", "right hand"})
public final class ForceMainHandHack extends Hack
{
	public enum Hand
	{
		RIGHT,
		LEFT
	}

	private final EnumSetting<Hand> hand = new EnumSetting<>("Hand",
		"Main hand used when rendering other players.", Hand.values(),
		Hand.RIGHT);

	public ForceMainHandHack()
	{
		super("ForceMainHand");
		setCategory(Category.RENDER);
		addSetting(hand);
	}

	public boolean isRightHand()
	{
		return hand.getSelected() == Hand.RIGHT;
	}

	// See PlayerMixin.
}
