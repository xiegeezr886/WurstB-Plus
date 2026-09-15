/*
 * Copyright (c) 2014-2025 Wurst-Imperium and contributors.
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 */
package net.wurstclient.twilight;

/**
 * The geometry of the Twilight Echo application shell, expressed in the
 * Minecraft canvas.
 *
 * <p>
 * <b>Source of truth.</b> The reference defines {@code .side-menu} twice in
 * {@code paper-light.css}: lines 24-92 and then again in lines 115-169, which
 * wins. Every value below comes from that second block or from a pixel
 * measurement of {@code streaming-home.png}, never from the overridden first
 * block - see {@code D:\WurstB\_te_ref\layout-notes.md}.
 *
 * <p>
 * The shell has <b>no full width title bar</b>: the app icons float in the top
 * row of the side bar, the page title belongs to the content area, and only the
 * player bar spans the window - measured: {@code x=700, y=0..59} is still the
 * app background.
 *
 * <p>
 * Deliberately free of Minecraft types so that the layout can be unit tested.
 */
public final class TwilightShellLayout
{
	/** Design size of the reference screenshots. */
	public static final int DESIGN_WIDTH = 1500;
	public static final int DESIGN_HEIGHT = 880;
	
	// ---- 侧栏面板（paper-light.css 115-125）----
	
	/** {@code top: 22px; bottom: 22px} - the panel floats. */
	public static final int PANEL_INSET_Y = 22;
	
	/** {@code border-radius: 0 26px 26px 0}. */
	public static final int SIDEBAR_RADIUS_RIGHT = 26;
	
	/** {@code backdrop-filter: blur(26px) saturate(140%)}. */
	public static final int SIDEBAR_BLUR = 26;
	
	/** {@code --te-menu-width} of the three sidebar states. */
	public static final int SIDEBAR_MAX = 216;
	public static final int SIDEBAR_COMPACT = 164;
	public static final int SIDEBAR_ICON_ONLY = 72;
	
	/**
	 * {@code clamp(180px, 18vw, 216px)} comes from {@code SideMenu.vue}; the
	 * library token {@code --te-menu-width} starts at 132px instead.
	 */
	public static final int SIDEBAR_MIN = 180;
	
	/** Canvas widths at which the sidebar switches to a narrower state. */
	public static final int COMPACT_BELOW = 1100;
	public static final int ICON_ONLY_BELOW = 760;
	
	// ---- 菜单（paper-light.css 127-134）----
	
	/** {@code .menu-items { padding: 22px 13px }}. */
	public static final int NAV_PADDING_TOP = 22;
	public static final int NAV_PADDING_RIGHT = 13;
	public static final int NAV_PADDING_BOTTOM = 22;
	public static final int NAV_PADDING_LEFT = 13;
	
	/** {@code .menu-nav { gap: 5px }} - measured row pitch is 50px. */
	public static final int NAV_GAP = 5;
	
	/** {@code .menu-item { height: 45px }}. */
	public static final int NAV_ITEM_HEIGHT = 45;
	
	/** {@code width: calc(100% - 8px)} of {@code SideMenu.vue}, not overridden. */
	public static final int NAV_ITEM_RIGHT_INSET = 8;
	
	/** {@code .menu-item { border-radius: 13px }}. */
	public static final int RADIUS_ITEM = 13;
	
	/** {@code .menu-item:hover { transform: translateX(3px) }}. */
	public static final int NAV_HOVER_TRANSLATE_X = 3;
	
	/** {@code .menu-item.active::before}: 3px wide, 9px in, 5px from the left. */
	public static final int INDICATOR_WIDTH = 3;
	public static final int INDICATOR_INSET_Y = 9;
	public static final int INDICATOR_LEFT = 5;
	public static final int INDICATOR_GLOW = 18;
	
	/**
	 * The brand row of {@code .side-menu .navigation-brand} is 64px in the
	 * preset and 56px by default, but the reference hides the logo by default,
	 * so the space above the first entry is measured as 58px. With the 22px nav
	 * padding of the preset that leaves 36px.
	 */
	public static final int BRAND_HEIGHT_LOGO_HIDDEN = 36;
	public static final int BRAND_HEIGHT_PRESET = 64;
	public static final int BRAND_HEIGHT_DEFAULT = 56;
	
	// ---- 内容区 ----
	
	/** Height of the page header inside the content area. */
	public static final int CONTENT_HEADER_HEIGHT = 96;
	
	/** Outer margin of the page content and the gap between cards. */
	public static final int CONTENT_MARGIN = 24;
	public static final int CARD_GAP = 16;
	
	/** {@code --home-radius-lg} of {@code StreamingHome.vue}. */
	public static final int RADIUS_HERO = 22;
	public static final int RADIUS_CARD = 18;
	
	// ---- 播放条（实测 796..865，底部留空 14）----
	
	public static final int PLAYER_BAR_HEIGHT = 70;
	public static final int PLAYER_BAR_BOTTOM_MARGIN = 14;
	
	/** Measured left inset inside the content area: 229 - 216 = 13. */
	public static final int PLAYER_BAR_SIDE_MARGIN = 13;
	
	/** The reference snapshot leaves ~138px on the right for its scrollbar rail. */
	public static final int PLAYER_BAR_SCROLLBAR_RAIL = 138;
	
	/** Transport controls of the player bar: 32px, the play button 44px. */
	public static final int TRANSPORT_BUTTON = 32;
	public static final int TRANSPORT_GAP = 12;
	public static final int PLAY_BUTTON = 44;
	public static final int PROGRESS_BAR_HEIGHT = 6;
	
	/** Title bar buttons of {@code TitleBar.vue}: 36px wide, 4px radius. */
	public static final int TOOL_BUTTON = 36;
	public static final int RADIUS_TOOL = 4;
	
	/** How wide the sidebar is on the given canvas. */
	public enum SidebarMode
	{
		EXPANDED,
		COMPACT,
		ICON_ONLY
	}
	
	private TwilightShellLayout()
	{
		
	}
	
	/**
	 * Scales the desktop design to the available canvas.
	 *
	 * @return a factor between 0.7 and 1.6.
	 */
	public static float designScale(int canvasWidth, int canvasHeight)
	{
		if(canvasWidth <= 0 || canvasHeight <= 0)
			return 0.7F;
		
		float byWidth = canvasWidth / (float)DESIGN_WIDTH;
		float byHeight = canvasHeight / (float)DESIGN_HEIGHT;
		float scale = Math.min(byWidth, byHeight);
		
		if(scale < 0.7F)
			return 0.7F;
		if(scale > 1.6F)
			return 1.6F;
		return scale;
	}
	
	public static SidebarMode sidebarMode(int canvasWidth)
	{
		if(canvasWidth < ICON_ONLY_BELOW)
			return SidebarMode.ICON_ONLY;
		if(canvasWidth < COMPACT_BELOW)
			return SidebarMode.COMPACT;
		return SidebarMode.EXPANDED;
	}
	
	/**
	 * {@code clamp(180px, 18vw, 216px)} of {@code SideMenu.vue}, or the fixed
	 * width of a narrower state.
	 */
	public static int sidebarWidth(int canvasWidth, SidebarMode mode,
		float scale)
	{
		int width;
		
		switch(mode)
		{
			case ICON_ONLY:
			width = SIDEBAR_ICON_ONLY;
			break;
			
			case COMPACT:
			width = SIDEBAR_COMPACT;
			break;
			
			default:
			width = Math.round(canvasWidth * 0.18F);
			width = Math.max(SIDEBAR_MIN, Math.min(SIDEBAR_MAX, width));
			break;
		}
		
		return Math.round(width * scale);
	}
	
	/**
	 * A layout for one frame, in canvas pixels with the origin in the top left
	 * corner.
	 */
	public static final class Frame
	{
		public final int canvasWidth;
		public final int canvasHeight;
		public final float scale;
		public final SidebarMode sidebarMode;
		public final boolean logoVisible;
		
		/** The floating panel, {@code top/bottom: 22px}. */
		public final Rect sidebar;
		public final Rect brand;
		public final Rect nav;
		public final Rect sidebarFooter;
		public final Rect content;
		public final Rect contentHeader;
		public final Rect contentBody;
		
		/** The floating player bar, right of the sidebar. */
		public final Rect playerBar;
		
		private Frame(int canvasWidth, int canvasHeight, float scale,
			SidebarMode sidebarMode, boolean logoVisible)
		{
			this.canvasWidth = canvasWidth;
			this.canvasHeight = canvasHeight;
			this.scale = scale;
			this.sidebarMode = sidebarMode;
			this.logoVisible = logoVisible;
			
			int sidebarWidth = sidebarWidth(canvasWidth, sidebarMode, scale);
			int panelInset = px(PANEL_INSET_Y);
			int brandHeight = px(logoVisible ? BRAND_HEIGHT_PRESET
				: BRAND_HEIGHT_LOGO_HIDDEN);
			int barHeight = px(PLAYER_BAR_HEIGHT);
			int barMargin = px(PLAYER_BAR_BOTTOM_MARGIN);
			int barY = Math.max(0, canvasHeight - barMargin - barHeight);
			int footerHeight = px(NAV_ITEM_HEIGHT + NAV_PADDING_BOTTOM);
			
			sidebar = new Rect(0, panelInset, sidebarWidth,
				Math.max(0, canvasHeight - panelInset * 2));
			brand = new Rect(0, panelInset, sidebarWidth, brandHeight);
			sidebarFooter = new Rect(0,
				Math.max(brand.bottom(), sidebar.bottom() - footerHeight),
				sidebarWidth,
				Math.max(0, sidebar.bottom()
					- Math.max(brand.bottom(), sidebar.bottom() - footerHeight)));
			nav = new Rect(0, brand.bottom(), sidebarWidth,
				Math.max(0, sidebarFooter.y() - brand.bottom()));
			content = new Rect(sidebarWidth, 0,
				Math.max(0, canvasWidth - sidebarWidth), canvasHeight);
			contentHeader = new Rect(content.x(), 0, content.width(),
				Math.min(px(CONTENT_HEADER_HEIGHT), content.height()));
			contentBody = new Rect(content.x(), contentHeader.bottom(),
				content.width(),
				Math.max(0, barY - px(PLAYER_BAR_SIDE_MARGIN)
					- contentHeader.bottom()));
			playerBar = new Rect(
				content.x() + px(PLAYER_BAR_SIDE_MARGIN), barY,
				Math.max(0,
					content.width() - px(PLAYER_BAR_SIDE_MARGIN) * 2),
				barHeight);
		}
		
		public int px(float designPixels)
		{
			return Math.round(designPixels * scale);
		}
		
		/**
		 * @return the rectangle of the navigation entry with that index, or null
		 *         when it does not fit into the navigation area.
		 */
		public Rect navItem(int index)
		{
			if(index < 0)
				return null;
			
			int left = nav.x() + px(NAV_PADDING_LEFT);
			int width = nav.width() - px(NAV_PADDING_LEFT)
				- px(NAV_PADDING_RIGHT) - px(NAV_ITEM_RIGHT_INSET);
			
			if(width <= 0)
				return null;
			
			int top = nav.y() + px(NAV_PADDING_TOP)
				+ index * (px(NAV_ITEM_HEIGHT) + px(NAV_GAP));
			int height = px(NAV_ITEM_HEIGHT);
			
			if(top + height > nav.bottom() - px(NAV_PADDING_BOTTOM))
				return null;
			
			return new Rect(left, top, width, height);
		}
		
		/**
		 * @return the index of the entry under the given point, or -1.
		 */
		public int navItemAt(double x, double y)
		{
			for(int index = 0; index < 64; index++)
			{
				Rect rect = navItem(index);
				
				if(rect == null)
					return -1;
				
				if(rect.contains(x, y))
					return index;
			}
			
			return -1;
		}
		
		/**
		 * @return the state indicator stripe of an entry, which the reference
		 *         draws 5px from the left and 9px inside the entry.
		 */
		public Rect navIndicator(int index)
		{
			Rect item = navItem(index);
			
			if(item == null)
				return null;
			
			int inset = px(INDICATOR_INSET_Y);
			return new Rect(item.x() + px(INDICATOR_LEFT) - px(NAV_PADDING_LEFT),
				item.y() + inset, px(INDICATOR_WIDTH),
				Math.max(0, item.height() - inset * 2));
		}
		
		/** The play button, centred in the player bar. */
		public Rect playButton()
		{
			int size = px(PLAY_BUTTON);
			return new Rect(
				playerBar.x() + playerBar.width() / 2 - size / 2,
				playerBar.y() + (playerBar.height() - size) / 2, size, size);
		}
		
		/**
		 * The transport button next to the play button, {@code offset = -1} for
		 * the previous and {@code 1} for the next one.
		 */
		public Rect transportButton(int offset)
		{
			int size = px(TRANSPORT_BUTTON);
			int gap = px(TRANSPORT_GAP);
			int playX = playButton().x();
			int x = offset < 0 ? playX - gap - size
				: playX + px(PLAY_BUTTON) + gap;
			
			return new Rect(x, playerBar.y() + (playerBar.height() - size) / 2,
				size, size);
		}
		
		/** The progress bar of the player bar, spanning its content width. */
		public Rect progressBar(int sideMargin)
		{
			int inset = px(sideMargin);
			return new Rect(playerBar.x() + inset,
				playerBar.bottom() - px(PLAYER_BAR_BOTTOM_MARGIN) - inset,
				Math.max(0, playerBar.width() - inset * 2),
				px(PROGRESS_BAR_HEIGHT));
		}
		
		/** A tool button of the floating top row, counted from the left. */
		public Rect brandButton(int index)
		{
			int size = px(TOOL_BUTTON);
			int gap = px(4);
			int left = px(12) + index * (size + gap);
			
			return new Rect(left, px(PANEL_INSET_Y) + px(4), size, size);
		}
		
		/**
		 * Splits a rectangle into equal columns, used by the statistics row of
		 * the local dashboard.
		 */
		public Rect[] columns(Rect area, int count, int gap)
		{
			if(count <= 0 || area.width() <= 0)
				return new Rect[0];
			
			int spacing = px(gap);
			int width = (area.width() - spacing * (count - 1)) / count;
			Rect[] rects = new Rect[count];
			
			for(int i = 0; i < count; i++)
				rects[i] = new Rect(area.x() + i * (width + spacing), area.y(),
					width, area.height());
			
			return rects;
		}
		
		/** The content area without its outer margin. */
		public Rect contentInner()
		{
			int margin = px(CONTENT_MARGIN);
			return new Rect(content.x() + margin, content.y() + margin,
				Math.max(0, content.width() - margin * 2),
				Math.max(0, content.height() - margin * 2));
		}
	}
	
	public static Frame layout(int canvasWidth, int canvasHeight,
		boolean logoVisible)
	{
		return new Frame(canvasWidth, canvasHeight,
			designScale(canvasWidth, canvasHeight),
			sidebarMode(canvasWidth), logoVisible);
	}
	
	public static Frame layout(int canvasWidth, int canvasHeight)
	{
		return layout(canvasWidth, canvasHeight, false);
	}
	
	/**
	 * A rectangle with integer coordinates and a top left origin.
	 */
	public static final class Rect
	{
		private final int x;
		private final int y;
		private final int width;
		private final int height;
		
		public Rect(int x, int y, int width, int height)
		{
			this.x = x;
			this.y = y;
			this.width = Math.max(0, width);
			this.height = Math.max(0, height);
		}
		
		public int x()
		{
			return x;
		}
		
		public int y()
		{
			return y;
		}
		
		public int width()
		{
			return width;
		}
		
		public int height()
		{
			return height;
		}
		
		public int right()
		{
			return x + width;
		}
		
		public int bottom()
		{
			return y + height;
		}
		
		public int centerX()
		{
			return x + width / 2;
		}
		
		public int centerY()
		{
			return y + height / 2;
		}
		
		public boolean contains(double px, double py)
		{
			return px >= x && px < right() && py >= y && py < bottom();
		}
		
		public boolean intersects(Rect other)
		{
			return other != null && x < other.right() && other.x < right()
				&& y < other.bottom() && other.y < bottom();
		}
		
		@Override
		public String toString()
		{
			return "[" + x + ", " + y + " " + width + "x" + height + "]";
		}
	}
}
