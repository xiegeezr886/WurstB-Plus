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

@SearchTags({"clear water", "clearwater", "no water overlay", "transparent water"})
public final class ClearWaterHack extends Hack
{
	public ClearWaterHack()
	{
		super("ClearWater");
		setCategory(Category.RENDER);
	}

	// See CameraMixin.getFluidInCamera() and
	// ScreenEffectRendererMixin.renderWater()/submitWater().
}
