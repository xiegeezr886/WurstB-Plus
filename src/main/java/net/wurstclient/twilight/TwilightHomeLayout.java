/*
 * Copyright (c) 2014-2025 Wurst-Imperium and contributors.
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 */
package net.wurstclient.twilight;

import net.wurstclient.twilight.TwilightShellLayout.Frame;
import net.wurstclient.twilight.TwilightShellLayout.Rect;

/**
 * The geometry of the streaming home page, taken from {@code StreamingHome.vue}
 * of the reference (local variables at lines 489-500, layout at 539-810).
 *
 * <p>
 * All values are the reference's CSS values converted to canvas pixels: the
 * {@code clamp()} calls keep their shape, only the {@code vw} unit becomes a
 * share of the content width. Rotation is not part of the geometry - it is
 * applied while drawing.
 *
 * <p>
 * Deliberately free of Minecraft types so that the layout can be unit tested.
 */
public final class TwilightHomeLayout
{
	/** {@code .home-flow { gap: 44px }}. */
	public static final int SECTION_GAP = 44;
	
	/** {@code --home-radius-lg: 22px}. */
	public static final int RADIUS_LARGE = 22;
	
	/** {@code --home-radius-md: 14px}. */
	public static final int RADIUS_MEDIUM = 14;
	
	/** {@code .hero-inner { gap: 24px; min-height: clamp(280px, 30vw, 340px) }}. */
	public static final int HERO_GAP = 24;
	public static final float HERO_MIN_HEIGHT = 280F;
	public static final float HERO_MAX_HEIGHT = 340F;
	public static final float HERO_HEIGHT_RATIO = 0.30F;
	
	/** {@code .hero-copy { flex: 1 1 52%; padding: clamp(28px, 3.6vw, 48px) }}. */
	public static final float HERO_COPY_SHARE = 0.52F;
	public static final float COPY_PADDING_MIN = 28F;
	public static final float COPY_PADDING_MAX = 48F;
	public static final float COPY_PADDING_RATIO = 0.036F;
	
	/** {@code .hero-kicker-day}: 46x46, radius 13. */
	public static final int DAY_BADGE = 46;
	public static final int DAY_BADGE_RADIUS = 13;
	
	/** {@code .hero-title}: margin-top 22, font-size clamp(40px, 5vw, 62px). */
	public static final int TITLE_MARGIN = 22;
	public static final float TITLE_MIN_SIZE = 40F;
	public static final float TITLE_MAX_SIZE = 62F;
	public static final float TITLE_SIZE_RATIO = 0.05F;
	
	/** {@code .hero-title-en}: margin-top 6, letter-spacing .42em. */
	public static final int TITLE_EN_MARGIN = 6;
	public static final float TITLE_EN_SPACING = 0.42F;
	
	/** {@code .hero-desc}: max-width 400, margin-top 18, line-height 1.7. */
	public static final int DESC_MAX_WIDTH = 400;
	public static final int DESC_MARGIN = 18;
	public static final float DESC_LINE_HEIGHT = 1.7F;
	
	/** {@code .hero-actions { gap: 12px; margin-top: 28px }}. */
	public static final int ACTIONS_GAP = 12;
	public static final int ACTIONS_MARGIN = 28;
	
	/** Both call to action pills are 46px high. */
	public static final int CTA_HEIGHT = 46;
	public static final int CTA_PRIMARY_PADDING = 24;
	public static final int CTA_SECONDARY_PADDING = 20;
	
	/** The three floating covers of {@code .hero-stage}. */
	public static final float[] COLLAGE_MIN = {168F, 120F, 96F};
	public static final float[] COLLAGE_MAX = {218F, 150F, 122F};
	public static final float[] COLLAGE_RATIO = {0.155F, 0.105F, 0.085F};
	public static final float[] COLLAGE_RIGHT = {0.24F, 0.07F, 0.12F};
	public static final int COLLAGE_RADIUS = 18;
	
	/** The reference stacks the hero and the duo row below 880px. */
	public static final int STACK_BELOW = 880;
	
	/** {@code .hero-stage { height: 240px }} in the stacked layout. */
	public static final float STAGE_STACKED_HEIGHT = 240F;
	
	/** {@code .duo { grid 2 列; gap: 20px }} and {@code .duo-card}. */
	public static final int DUO_GAP = 20;
	public static final int DUO_PADDING_X = 22;
	public static final int DUO_PADDING_Y = 20;
	public static final int DUO_INNER_GAP = 18;
	public static final int DUO_MIN_HEIGHT = 112;
	
	/** {@code .duo-stack { width:96px; height:72px }} and its covers. */
	public static final int DUO_STACK_WIDTH = 96;
	public static final int DUO_STACK_HEIGHT = 72;
	public static final int DUO_COVER = 60;
	public static final int DUO_COVER_RADIUS = 12;
	public static final int DUO_ARROW = 38;
	
	/** {@code .section-head { margin-bottom: 18px }} and {@code .section-more}. */
	public static final int SECTION_HEAD_MARGIN = 18;
	public static final int SECTION_MORE_HEIGHT = 34;
	public static final int SECTION_MORE_PADDING = 14;
	
	private TwilightHomeLayout()
	{
		
	}
	
	/**
	 * A laid out home page.
	 */
	public static final class Home
	{
		public final Rect hero;
		public final Rect heroCopy;
		public final Rect heroStage;
		public final Rect dayBadge;
		public final Rect primaryCta;
		public final Rect secondaryCta;
		public final Rect collageArea;
		public final Rect duoRow;
		public final Rect duoCardLeft;
		public final Rect duoCardRight;
		public final Rect sectionHead;
		public final Rect sectionMore;
		public final float copyPadding;
		public final float titleSize;
		
		private Home(Rect hero, Rect heroCopy, Rect heroStage, Rect dayBadge,
			Rect primaryCta, Rect secondaryCta, Rect collageArea, Rect duoRow,
			Rect duoCardLeft, Rect duoCardRight, Rect sectionHead,
			Rect sectionMore, float copyPadding, float titleSize)
		{
			this.hero = hero;
			this.heroCopy = heroCopy;
			this.heroStage = heroStage;
			this.dayBadge = dayBadge;
			this.primaryCta = primaryCta;
			this.secondaryCta = secondaryCta;
			this.collageArea = collageArea;
			this.duoRow = duoRow;
			this.duoCardLeft = duoCardLeft;
			this.duoCardRight = duoCardRight;
			this.sectionHead = sectionHead;
			this.sectionMore = sectionMore;
			this.copyPadding = copyPadding;
			this.titleSize = titleSize;
		}
		
		/** The three floating covers, in reference order (0 is the big one). */
		public Rect[] collage(Frame frame)
		{
			return TwilightHomeLayout.collage(frame, collageArea);
		}
	}
	
	public static Home layout(Frame frame)
	{
		float scale = frame.scale;
		Rect body = frame.contentBody;
		int margin = frame.px(TwilightShellLayout.CONTENT_MARGIN);
		int gap = frame.px(SECTION_GAP);
		
		int left = body.x() + margin;
		int width = Math.max(0, body.width() - margin * 2);
		int top = body.y() + frame.px(8);
		
		// the reference mixes px with vw: clamp(280px, 30vw, 340px) is a share of
		// the viewport width, not of the content column
		float viewport = frame.canvasWidth;
		boolean stacked = viewport < STACK_BELOW;
		float heroHeight = clamp(viewport * HERO_HEIGHT_RATIO, HERO_MIN_HEIGHT,
			HERO_MAX_HEIGHT) * scale;
		
		if(stacked)
			heroHeight += STAGE_STACKED_HEIGHT * scale;
		
		Rect hero = new Rect(left, top, width, Math.round(heroHeight));
		
		// .hero-inner: two flexible halves with a 24px gap, a column below 880px
		int innerGap = frame.px(HERO_GAP);
		int innerWidth = Math.max(0, hero.width() - innerGap);
		int copyWidth = stacked ? hero.width()
			: Math.round(innerWidth * HERO_COPY_SHARE);
		float copyPadding = clamp(viewport * COPY_PADDING_RATIO,
			COPY_PADDING_MIN, COPY_PADDING_MAX) * scale;
		Rect heroCopy = new Rect(hero.x(), hero.y(), copyWidth,
			stacked ? Math.max(0, hero.height() - Math.round(
				STAGE_STACKED_HEIGHT * scale)) : hero.height());
		Rect heroStage = stacked
			? new Rect(hero.x(), heroCopy.bottom(), hero.width(),
				Math.max(0, hero.height() - heroCopy.height()))
			: new Rect(heroCopy.right() + innerGap, hero.y(),
				Math.max(0, innerWidth - copyWidth), hero.height());
		
		// the date badge and the two call to action pills
		int badge = frame.px(DAY_BADGE);
		Rect dayBadge = new Rect(Math.round(heroCopy.x() + copyPadding),
			Math.round(heroCopy.y() + copyPadding), badge, badge);
		
		float titleSize = clamp(viewport * TITLE_SIZE_RATIO, TITLE_MIN_SIZE,
			TITLE_MAX_SIZE) * scale;
		int ctaHeight = frame.px(CTA_HEIGHT);
		int primaryWidth =
			frame.px(CTA_PRIMARY_PADDING * 2) + Math.round(titleSize * 2.4F);
		int secondaryWidth =
			frame.px(CTA_SECONDARY_PADDING * 2) + Math.round(titleSize * 1.6F);
		int actionsY = Math.round(hero.bottom() - copyPadding) - ctaHeight;
		Rect primaryCta =
			new Rect(dayBadge.x(), actionsY, primaryWidth, ctaHeight);
		Rect secondaryCta = new Rect(primaryCta.right() + frame.px(ACTIONS_GAP),
			actionsY, secondaryWidth, ctaHeight);
		
		// the covers float inside the right half
		int collageGap = frame.px(COLLAGE_MIN.length > 0 ? 0 : 0);
		Rect collageArea = new Rect(heroStage.x(), heroStage.y() + collageGap,
			heroStage.width(), heroStage.height());
		
		// the duo row and the section head below it
		int duoTop = hero.bottom() + gap;
		int duoHeight = Math.max(frame.px(DUO_MIN_HEIGHT),
			frame.px(DUO_STACK_HEIGHT) + frame.px(DUO_PADDING_Y * 2));
		boolean singleColumn = viewport < STACK_BELOW;
		int duoWidth = singleColumn ? width
			: Math.max(0, (width - frame.px(DUO_GAP)) / 2);
		int rowHeight = singleColumn ? duoHeight * 2 + frame.px(DUO_GAP)
			: duoHeight;
		Rect duoRow = new Rect(left, duoTop, width, rowHeight);
		Rect duoCardLeft = new Rect(left, duoTop, duoWidth, duoHeight);
		Rect duoCardRight = singleColumn
			? new Rect(left, duoCardLeft.bottom() + frame.px(DUO_GAP), duoWidth,
				duoHeight)
			: new Rect(left + duoWidth + frame.px(DUO_GAP), duoTop,
				Math.max(0, width - duoWidth - frame.px(DUO_GAP)), duoHeight);
		
		int headTop = duoRow.bottom() + gap;
		int headHeight = frame.px(40);
		Rect sectionHead = new Rect(left, headTop, width, headHeight);
		int moreWidth = frame.px(SECTION_MORE_PADDING * 2) + frame.px(56);
		Rect sectionMore = new Rect(sectionHead.right() - moreWidth,
			sectionHead.bottom() - frame.px(SECTION_MORE_HEIGHT), moreWidth,
			frame.px(SECTION_MORE_HEIGHT));
		
		return new Home(hero, heroCopy, heroStage, dayBadge, primaryCta,
			secondaryCta, collageArea, duoRow, duoCardLeft, duoCardRight,
			sectionHead, sectionMore, copyPadding, titleSize);
	}
	
	/**
	 * The three floating covers of the hero, positioned like the reference:
	 * {@code right: 24% / 7% / 12%}, the first vertically centred.
	 */
	public static Rect[] collage(Frame frame, Rect stage)
	{
		Rect[] rects = new Rect[COLLAGE_MIN.length];
		
		for(int i = 0; i < rects.length; i++)
		{
			float size = clamp(frame.canvasWidth * COLLAGE_RATIO[i],
				COLLAGE_MIN[i], COLLAGE_MAX[i]) * frame.scale;
			int px = Math.round(size);
			int right =
				stage.right() - Math.round(stage.width() * COLLAGE_RIGHT[i]);
			int x = right - px;
			int y;
			
			switch(i)
			{
				case 0:
				y = stage.centerY() - Math.round(px * 0.54F);
				break;
				
				case 1:
				y = stage.y() + Math.round(stage.height() * 0.14F);
				break;
				
				default:
				y = stage.bottom() - Math.round(stage.height() * 0.08F) - px;
				break;
			}
			
			rects[i] = new Rect(x, y, px, px);
		}
		
		return rects;
	}
	
	/** The three stacked covers of a duo card, in draw order. */
	public static Rect[] duoCovers(Frame frame, Rect card)
	{
		Rect[] rects = new Rect[3];
		int px = frame.px(DUO_COVER);
		int stackLeft = card.right() - frame.px(DUO_PADDING_X)
			- frame.px(DUO_ARROW) - frame.px(DUO_INNER_GAP)
			- frame.px(DUO_STACK_WIDTH);
		int stackTop = card.y() + (card.height() - frame.px(DUO_STACK_HEIGHT)) / 2;
		int[][] offsets = {{0, 6}, {22, 0}, {42, 10}};
		
		for(int i = 0; i < 3; i++)
			rects[i] = new Rect(stackLeft + frame.px(offsets[i][0]),
				stackTop + frame.px(offsets[i][1]), px, px);
		
		return rects;
	}
	
	/** The round arrow button of a duo card. */
	public static Rect duoArrow(Frame frame, Rect card)
	{
		int size = frame.px(DUO_ARROW);
		return new Rect(card.right() - frame.px(DUO_PADDING_X) - size,
			card.centerY() - size / 2, size, size);
	}
	
	private static float clamp(float value, float min, float max)
	{
		return value < min ? min : value > max ? max : value;
	}
}
