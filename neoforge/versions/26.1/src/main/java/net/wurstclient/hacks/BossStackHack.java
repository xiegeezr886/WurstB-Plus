/*
 * Copyright (c) 2025 Penguin
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 */
package net.wurstclient.hacks;

import java.util.Map;
import java.util.UUID;

import net.wurstclient.util.render.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.world.BossEvent;
import net.wurstclient.Category;
import net.wurstclient.SearchTags;
import net.wurstclient.events.GUIRenderListener;
import net.wurstclient.hack.Hack;
import net.wurstclient.mixinterface.IBossHealthOverlay;

@SearchTags({"boss stack", "boss bar", "compact boss"})
public final class BossStackHack extends Hack implements GUIRenderListener
{
	public BossStackHack()
	{
		super("BossStack");
		setCategory(Category.RENDER);
	}

	@Override
	protected void onEnable()
	{
		EVENTS.add(GUIRenderListener.class, this);
	}

	@Override
	protected void onDisable()
	{
		EVENTS.remove(GUIRenderListener.class, this);
	}

	@Override
	public void onRenderGUI(GuiGraphicsExtractor graphics, float partialTicks)
	{
		Map<UUID, ? extends BossEvent> events = ((IBossHealthOverlay)MC.gui
			.getBossOverlay()).wurst_getEvents();

		if(events.isEmpty())
			return;

		int screenWidth = MC.getWindow().getGuiScaledWidth();
		int x = screenWidth / 2 - 91;
		int y = 12;
		int barHeight = 12;

		for(BossEvent event : events.values())
		{
			Component name = event.getName();
			float progress = event.getProgress();

			graphics.fill(x, y, x + 182, y + barHeight, 0x80000000);
			graphics.fill(x + 1, y + 1,
				x + 1 + (int)(180 * progress), y + barHeight - 1,
				0xFF006366);

			graphics.centeredText(MC.font, name.getString(),
				screenWidth / 2, y + 2, 0xFFFFFFFF);

			y += barHeight + 2;
		}
	}
}
