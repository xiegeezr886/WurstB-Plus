/*
 * Copyright (c) 2014-2025 Wurst-Imperium and contributors.
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 */
package net.wurstclient.other_features;

import java.awt.Color;
import java.util.Comparator;

import net.wurstclient.DontBlock;
import net.wurstclient.SearchTags;
import net.wurstclient.WurstClient;
import net.wurstclient.hack.Hack;
import net.wurstclient.other_feature.OtherFeature;
import net.wurstclient.settings.CheckboxSetting;
import net.wurstclient.settings.ColorSetting;
import net.wurstclient.settings.EnumSetting;

@SearchTags({"hack list", "HakList", "hak list", "HacksList", "hacks list",
	"HaxList", "hax list", "ArrayList", "array list", "ModList", "mod list",
	"CheatList", "cheat list"})
@DontBlock
public final class HackListOtf extends OtherFeature
{
	private final EnumSetting<Mode> mode = new EnumSetting<>("Mode",
		"\u00a7lAuto\u00a7r mode renders the whole list if it fits onto the screen.\n"
			+ "\u00a7lCount\u00a7r mode only renders the number of active hacks.\n"
			+ "\u00a7lHidden\u00a7r mode renders nothing.",
		Mode.values(), Mode.AUTO);
	
	private final EnumSetting<Position> position = new EnumSetting<>("Position",
		"Which side of the screen the HackList should be shown on."
			+ "\nChange this to \u00a7lRight\u00a7r when using TabGUI.",
		Position.values(), Position.LEFT);
	
	private final ColorSetting color = new ColorSetting("Color",
		"Color of the HackList text.\n"
			+ "Only visible when \u00a76RainbowUI\u00a7r is disabled.",
		Color.WHITE);
	
	private final EnumSetting<SortBy> sortBy = new EnumSetting<>("Sort by",
		"Determines how the HackList entries are sorted.\n"
			+ "Only visible when \u00a76Mode\u00a7r is set to \u00a76Auto\u00a7r.",
		SortBy.values(), SortBy.NAME);
	
	private final CheckboxSetting revSort =
		new CheckboxSetting("Reverse sorting", false);
	
	private final CheckboxSetting animations = new CheckboxSetting("Animations",
		"When enabled, entries slide into and out of the HackList as hacks are enabled and disabled.",
		true);
	
	// 参考 OpenOpal 的 ToggledSettings.BarMode：每项旁边的竖条画在哪一侧。
	// 默认 OUTER 就是本工程一直以来那个「跟着 Position 走外缘」的画法，因此
	// 默认观感逐像素不变；LEFT/RIGHT 是参考那两种固定侧的语义。
	private final EnumSetting<BarMode> barMode = new EnumSetting<>("Bar",
		"Which side of each entry the colored bar is drawn on.\n"
			+ "\u00a7lOuter edge\u00a7r follows the HackList position.\n"
			+ "\u00a7lNone\u00a7r hides the bar entirely.",
		BarMode.values(), BarMode.OUTER);
	
	private SortBy prevSortBy;
	private Boolean prevRevSort;
	
	public HackListOtf()
	{
		super("HackList", "Shows a list of active hacks on the screen.");
		
		addSetting(mode);
		addSetting(position);
		addSetting(color);
		addSetting(barMode);
		addSetting(sortBy);
		addSetting(revSort);
		addSetting(animations);
	}
	
	public Mode getMode()
	{
		return mode.getSelected();
	}
	
	public BarMode getBarMode()
	{
		return barMode.getSelected();
	}
	
	public Position getPosition()
	{
		return position.getSelected();
	}
	
	public boolean isAnimations()
	{
		return animations.isChecked();
	}
	
	public Comparator<Hack> getComparator()
	{
		if(revSort.isChecked())
			return sortBy.getSelected().comparator.reversed();
		
		return sortBy.getSelected().comparator;
	}
	
	public boolean shouldSort()
	{
		try
		{
			// width of a renderName could change at any time
			// must sort the HackList every tick
			if(sortBy.getSelected() == SortBy.WIDTH)
				return true;
			
			if(sortBy.getSelected() != prevSortBy)
				return true;
			
			if(!Boolean.valueOf(revSort.isChecked()).equals(prevRevSort))
				return true;
			
			return false;
			
		}finally
		{
			prevSortBy = sortBy.getSelected();
			prevRevSort = revSort.isChecked();
		}
	}
	
	public int getColor(int alpha)
	{
		return color.getColorI(alpha);
	}
	
	public static enum Mode
	{
		AUTO("Auto"),
		
		COUNT("Count"),
		
		HIDDEN("Hidden");
		
		private final String name;
		
		private Mode(String name)
		{
			this.name = name;
		}
		
		@Override
		public String toString()
		{
			return name;
		}
	}
	
	/**
	 * 竖条画在哪一侧，移植自 OpenOpal 的 {@code ToggledSettings.BarMode}
	 * （GPL-3.0）。参考只有 NONE/LEFT/RIGHT；本工程多一个 {@code OUTER}，
	 * 因为原本的竖条是「跟着 Position 走外缘」的——加上它才能在不改动既有
	 * 观感的前提下，把参考那两种固定侧的选择也提供出来。
	 *
	 * <p>
	 * {@link #toString()} 会被 {@code EnumSetting} 当作持久化值写进配置，
	 * 改名会导致旧配置回落默认值。
	 */
	public static enum BarMode
	{
		OUTER("Outer edge"),
		
		LEFT("Left"),
		
		RIGHT("Right"),
		
		NONE("None");
		
		private final String name;
		
		private BarMode(String name)
		{
			this.name = name;
		}
		
		@Override
		public String toString()
		{
			return name;
		}
	}
	
	public static enum Position
	{
		LEFT("Left"),
		
		RIGHT("Right");
		
		private final String name;
		
		private Position(String name)
		{
			this.name = name;
		}
		
		@Override
		public String toString()
		{
			return name;
		}
	}
	
	public static enum SortBy
	{
		NAME("Name", (a, b) -> a.getName().compareToIgnoreCase(b.getName())),
		
		WIDTH("Width", Comparator.comparingInt(
			h -> WurstClient.MC.font.width(h.getDisplayName())));
		
		private final String name;
		private final Comparator<Hack> comparator;
		
		private SortBy(String name, Comparator<Hack> comparator)
		{
			this.name = name;
			this.comparator = comparator;
		}
		
		@Override
		public String toString()
		{
			return name;
		}
	}
}
