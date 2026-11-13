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
import net.wurstclient.settings.SliderSetting;
import net.wurstclient.settings.SliderSetting.ValueDisplay;

@SearchTags({"custom held items", "held item", "hand item", "item position",
	"item scale"})
public final class CustomHeldItemsHack extends Hack
{
	private final SliderSetting x = new SliderSetting("X",
		"Horizontal offset of the held item.", 0, -1, 1, 0.01,
		ValueDisplay.DECIMAL);

	private final SliderSetting y = new SliderSetting("Y",
		"Vertical offset of the held item.", 0, -1, 1, 0.01,
		ValueDisplay.DECIMAL);

	private final SliderSetting z = new SliderSetting("Z",
		"Depth offset of the held item.", 0, -1, 1, 0.01,
		ValueDisplay.DECIMAL);

	private final SliderSetting scaleX = new SliderSetting("X scale",
		"Horizontal scale of the held item.", 1, 0.1, 2, 0.05,
		ValueDisplay.DECIMAL);

	private final SliderSetting scaleY = new SliderSetting("Y scale",
		"Vertical scale of the held item.", 1, 0.1, 2, 0.05,
		ValueDisplay.DECIMAL);

	private final SliderSetting scaleZ = new SliderSetting("Z scale",
		"Depth scale of the held item.", 1, 0.1, 2, 0.05,
		ValueDisplay.DECIMAL);

	public CustomHeldItemsHack()
	{
		super("CustomHeldItems");
		setCategory(Category.RENDER);
		addSetting(x);
		addSetting(y);
		addSetting(z);
		addSetting(scaleX);
		addSetting(scaleY);
		addSetting(scaleZ);
	}

	public float getX()
	{
		return x.getValueF();
	}

	public float getY()
	{
		return y.getValueF();
	}

	public float getZ()
	{
		return z.getValueF();
	}

	public float getScaleX()
	{
		return scaleX.getValueF();
	}

	public float getScaleY()
	{
		return scaleY.getValueF();
	}

	public float getScaleZ()
	{
		return scaleZ.getValueF();
	}

	// See HeldItemRendererMixin.applyCustomHeldItemTransform().
}
