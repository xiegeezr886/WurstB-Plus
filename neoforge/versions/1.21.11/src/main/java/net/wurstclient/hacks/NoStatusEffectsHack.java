/*
 * Copyright (c) 2014-2025 Wurst-Imperium and contributors.
 * Copyright (c) 2015-2026 CCBlueX
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 *
 * Ported from Meteor Client (GPL-3.0).
 */
package net.wurstclient.hacks;

import net.wurstclient.Category;
import net.wurstclient.SearchTags;
import net.wurstclient.hack.Hack;
import net.wurstclient.settings.CheckboxSetting;

/**
 * Hides the vanilla status effect icons.
 *
 * <p>
 * The {@code Gui.renderEffects} call is cancelled from {@code GuiMixin}, so
 * the behaviour is identical on Fabric, Forge and NeoForge.
 */
@SearchTags({"no status effects", "hide effects", "potion icons",
	"status icons"})
public final class NoStatusEffectsHack extends Hack
{
	private final CheckboxSetting hideIcons = new CheckboxSetting(
		"Hide icons", "Hides the status effect icons in the HUD.", true);

	public NoStatusEffectsHack()
	{
		super("NoStatusEffects");
		setCategory(Category.RENDER);
		addSetting(hideIcons);
	}

	public boolean isHiding()
	{
		return isEnabled() && hideIcons.isChecked() && !MC.options.hideGui;
	}
}
