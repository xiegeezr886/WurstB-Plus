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
import net.wurstclient.settings.CheckboxSetting;

@SearchTags({"overlay editor", "overlay", "pumpkin overlay", "fire overlay"})
public final class OverlayEditorHack extends Hack
{
	private final CheckboxSetting hidePumpkin = new CheckboxSetting(
		"Hide pumpkin", "Hides the pumpkin blur overlay.", false);

	private final CheckboxSetting hideFire = new CheckboxSetting("Hide fire",
		"Hides the first-person fire overlay.", false);

	public OverlayEditorHack()
	{
		super("OverlayEditor");
		setCategory(Category.RENDER);
		addSetting(hidePumpkin);
		addSetting(hideFire);
	}

	public boolean isHidePumpkin()
	{
		return hidePumpkin.isChecked();
	}

	public boolean isHideFire()
	{
		return hideFire.isChecked();
	}

	// See GuiMixin/IngameHudMixin for pumpkin and
	// ScreenEffectRendererMixin/InGameOverlayRendererMixin for fire.
}
