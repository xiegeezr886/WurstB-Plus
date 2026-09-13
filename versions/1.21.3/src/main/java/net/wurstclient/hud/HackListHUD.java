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
import net.wurstclient.clickgui2.FlatRenderer;
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
	private int textColor;
	
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

		// color
		if(WurstClient.INSTANCE.getHax().rainbowUiHack.isEnabled())
		{
			float[] acColor = WurstClient.INSTANCE.getGui().getAcColor();
			textColor = 0x04 << 24 | (int)(acColor[0] * 0xFF) << 16
				| (int)(acColor[1] * 0xFF) << 8 | (int)(acColor[2] * 0xFF);
			
		}else
			textColor = otf.getColor(0x04);
		
		float height = y + activeHax.size() * ENTRY_HEIGHT;
		
		if(otf.getMode() == Mode.COUNT
			|| height > context.guiHeight())
			drawCounter(context);
		else
			drawHackList(context, partialTicks);
	}

	public void renderPreview(GuiGraphics context, int x, int y,
		boolean rightAligned)
	{
		baseX = x;
		posY = y;
		containerWidth = getWidth();
		alignRight = rightAligned;
		textColor = 0xFF006366;
		drawEntry(context, "HackList", 1);
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
	
	private void drawCounter(GuiGraphics context)
	{
		long size = activeHax.stream().filter(e -> e.hack.isEnabled()).count();
		drawEntry(context, size + " 项功能已启用", 1);
	}
	
	private void drawHackList(GuiGraphics context, float partialTicks)
	{
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
			drawEntry(context, entry.hack.getDisplayName(), progress);
		}
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
	
	private void drawEntry(GuiGraphics context, String text, float progress)
	{
		if(progress <= 0)
			return;

		Font font = WurstClient.MC.font;
		int textWidth = font.width(text);
		float slide = (textWidth + 12) * (1 - progress);
		float x1;
		float x2;
		if(!alignRight)
		{
			x1 = baseX - slide;
			x2 = x1 + textWidth + 11;
		}
		else
		{
			x2 = baseX + containerWidth + slide;
			x1 = x2 - textWidth - 11;
		}

		int top = Math.round(posY);
		int bottom = top + 11;
		int left = Math.round(x1);
		int right = Math.round(x2);
		FlatRenderer.fillRoundedRect(context, left, top, right, bottom, 3,
			withAlpha(0x070B10, Math.round(104 * progress)));
		FlatRenderer.drawRoundedOutline(context, left, top, right, bottom, 3,
			withAlpha(0xFFFFFF, Math.round(20 * progress)));
		int accentX = alignRight ? right - 3 : left + 1;
		FlatRenderer.fillRoundedRect(context, accentX, top + 2, accentX + 2,
			bottom - 2, 1, withAlpha(textColor, Math.round(220 * progress)));
		int textX = alignRight ? left + 4 : left + 6;
		int textY = top + 2;
		context.drawString(font, text, textX + 1, textY + 1,
			withAlpha(0, Math.round(145 * progress)), false);
		context.drawString(font, text, textX, textY,
			withAlpha(textColor, Math.round(255 * progress)), false);
		posY += ENTRY_HEIGHT * progress;
	}

	private static int withAlpha(int color, int alpha)
	{
		return Math.max(0, Math.min(255, alpha)) << 24 | color & 0xFFFFFF;
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
