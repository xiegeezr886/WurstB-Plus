/*
 * Copyright (c) 2014-2026 Wurst-Imperium and contributors.
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 *
 * Ported from Meteor Client (GPL-3.0).
 */
package net.wurstclient.hacks;

import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.RenderGuiLayerEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;
import net.neoforged.neoforge.common.NeoForge;
import net.wurstclient.Category;
import net.wurstclient.SearchTags;
import net.wurstclient.hack.Hack;
import net.wurstclient.settings.CheckboxSetting;

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

	@Override
	protected void onEnable()
	{
		NeoForge.EVENT_BUS.register(this);
	}

	@Override
	protected void onDisable()
	{
		NeoForge.EVENT_BUS.unregister(this);
	}

	@SubscribeEvent
	public void onRenderGuiLayer(RenderGuiLayerEvent.Pre event)
	{
		if(!isEnabled() || !hideIcons.isChecked() || MC.options.hideGui)
			return;

		if(VanillaGuiLayers.EFFECTS.equals(event.getName()))
			event.setCanceled(true);
	}
}
