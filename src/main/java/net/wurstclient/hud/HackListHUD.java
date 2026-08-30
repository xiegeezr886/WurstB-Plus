/*
 * Copyright (c) 2014-2025 Wurst-Imperium and contributors.
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 */
package net.wurstclient.hud;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.wurstclient.Category;
import net.wurstclient.WurstClient;
import net.wurstclient.compose.ComposeHackList;
import net.wurstclient.compose.ModuleColors;
import net.wurstclient.events.UpdateListener;
import net.wurstclient.hack.Hack;
import net.wurstclient.other_features.HackListOtf;
import net.wurstclient.other_features.HackListOtf.Mode;
import net.wurstclient.other_features.HackListOtf.Position;

public final class HackListHUD implements UpdateListener
{
	private final ArrayList<HackListEntry> activeHax = new ArrayList<>();
	private final HackListOtf otf = WurstClient.INSTANCE.getOtfs().hackListOtf;
	private static final int ENTRY_HEIGHT = 12;
	private float posY;
	private float baseX;
	private int containerWidth;
	private boolean alignRight;

	
	public HackListHUD()
	{
		WurstClient.INSTANCE.getEventManager().add(UpdateListener.class, this);
	}
	
	public void render(GuiGraphics context, float partialTicks)
	{
		int y;
		if(otf.getPosition() == Position.LEFT
			&& !WurstClient.INSTANCE.getOtfs().tabGuiOtf.isHidden())
			y = 32 + Category.values().length * 10;
		else if(otf.getPosition() == Position.LEFT
			&& WurstClient.INSTANCE.getOtfs().wurstLogoOtf.isVisible())
			y = 28;
		else
			y = 4;
		boolean right = otf.getPosition() == Position.RIGHT;
		int x = right ? context.guiWidth() - getWidth() - 3 : 3;
		renderAt(context, partialTicks, x, y, right);
	}

	public void renderAt(GuiGraphics context, float partialTicks, int x, int y,
		boolean rightAligned)
	{
		if(otf.getMode() == Mode.HIDDEN)
			return;
		posY = y;
		baseX = x;
		containerWidth = getWidth();
		alignRight = rightAligned;

		// 颜色模式：rainbowUi 开启走 RAINBOW，否则 STATIC 单色
		ModuleColors colors = buildColors();

		float height = y + activeHax.size() * ENTRY_HEIGHT;

		if(otf.getMode() == Mode.COUNT || height > context.guiHeight())
			drawCounter(context, colors);
		else
			drawHackList(context, partialTicks, colors);
	}

	private ModuleColors buildColors()
	{
		ModuleColors colors = new ModuleColors();
		if(WurstClient.INSTANCE.getHax().rainbowUiHack.isEnabled())
		{
			float[] acColor = WurstClient.INSTANCE.getGui().getAcColor();
			colors.mode(ModuleColors.Mode.RAINBOW).rainbow(3, 0.6F, 1, 0.05F);
		}else
			colors.mode(ModuleColors.Mode.STATIC)
				.staticColor(otf.getColor(0x04));
		return colors;
	}

	public void renderPreview(GuiGraphics context, int x, int y,
		boolean rightAligned)
	{
		baseX = x;
		posY = y;
		containerWidth = getWidth();
		alignRight = rightAligned;
		ComposeHackList.Entry preview = new ComposeHackList.Entry("HackList");
		preview.progress = 1;
		preview.color = 0xFF007CFF;
		ComposeHackList.renderEntry(context, preview, baseX, posY,
			containerWidth, alignRight);
	}

	public int getWidth()
	{
		Font font = WurstClient.MC.font;
		if(otf.getMode() == Mode.COUNT)
			return Math.max(90, font.width(activeHax.size()
				+ " hacks enabled") + 11);
		return Math.max(90, activeHax.stream()
			.map(entry -> entry.hack.getDisplayName()).mapToInt(font::width)
			.max().orElse(font.width("HackList")) + 11);
	}

	public int getHeight()
	{
		if(otf.getMode() == Mode.COUNT)
			return ENTRY_HEIGHT;
		return Math.max(ENTRY_HEIGHT, activeHax.size() * ENTRY_HEIGHT);
	}
	
	private void drawCounter(GuiGraphics context, ModuleColors colors)
	{
		long size = activeHax.stream().filter(e -> e.hack.isEnabled()).count();
		ComposeHackList.Entry entry = new ComposeHackList.Entry(size
			+ " 项功能已启用");
		entry.progress = 1;
		entry.color = colors.colorFor(0, 1, System.currentTimeMillis());
		ComposeHackList.renderEntry(context, entry, baseX, posY,
			containerWidth, alignRight);
	}

	private void drawHackList(GuiGraphics context, float partialTicks,
		ModuleColors colors)
	{
		ArrayList<ComposeHackList.Entry> composeEntries = new ArrayList<>();
		for(Iterator<HackListEntry> iterator = activeHax.iterator();
			iterator.hasNext();)
		{
			HackListEntry entry = iterator.next();
			float progress = otf.isAnimations()
				? entry.update(entry.hack.isEnabled()) : 1;
			if(!entry.hack.isEnabled() && progress <= 0)
			{
				iterator.remove();
				continue;
			}
			ComposeHackList.Entry composeEntry = new ComposeHackList.Entry(
				entry.hack.getDisplayName());
			composeEntry.progress = progress;
			composeEntries.add(composeEntry);
		}
		long now = System.currentTimeMillis();
		for(int index = 0; index < composeEntries.size(); index++)
			composeEntries.get(index).color = colors.colorFor(index,
				composeEntries.size(), now);
		ComposeHackList.render(context, composeEntries, baseX, posY,
			alignRight, partialTicks);
	}
	
	public void updateState(Hack hack)
	{
		HackListEntry entry = new HackListEntry(hack);
		
		if(hack.isEnabled())
		{
			if(activeHax.contains(entry))
				return;
			
			activeHax.add(entry);
			sort();
			
		}else if(!otf.isAnimations())
			activeHax.remove(entry);
	}
	
	private void sort()
	{
		Comparator<HackListEntry> comparator =
			Comparator.comparing(hle -> hle.hack, otf.getComparator());
		Collections.sort(activeHax, comparator);
	}
	
	@Override
	public void onUpdate()
	{
		if(otf.shouldSort())
			sort();

		if(!otf.isAnimations())
		{
			activeHax.removeIf(entry -> !entry.hack.isEnabled());
			return;
		}
	}

	private static final class HackListEntry
	{
		private final Hack hack;
		private float progress;
		private long lastUpdateNanos;
		
		public HackListEntry(Hack mod)
		{
			hack = mod;
		}

		private float update(boolean enabled)
		{
			long now = System.nanoTime();
			if(lastUpdateNanos == 0)
			{
				lastUpdateNanos = now;
				return progress;
			}

			float frameTime = Math.min(0.05F,
				(now - lastUpdateNanos) / 1_000_000_000F);
			lastUpdateNanos = now;
			float target = enabled ? 1 : 0;
			progress += (target - progress)
				* (1 - (float)Math.exp(-16 * frameTime));
			if(Math.abs(target - progress) < 0.002F)
				progress = target;
			return progress;
		}
		
		@Override
		public boolean equals(Object obj)
		{
			if(!(obj instanceof HackListEntry other))
				return false;
			
			return hack == other.hack;
		}
		
		@Override
		public int hashCode()
		{
			return hack.hashCode();
		}
	}
}
