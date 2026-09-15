/*
 * Copyright (c) 2014-2025 Wurst-Imperium and contributors.
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 */
package net.wurstclient.twilight;

/**
 * Twilight Echo 桌面播放器设计令牌在 Java 侧的权威副本。
 *
 * <p>取值全部逐条抄自参考工程 {@code src/renderer/src/assets/base.css} 与
 * {@code assets/theme-layouts/paper-light.css}，每个常量都在注释里标了
 * <b>文件 + 行号</b>；没有实据的令牌一律不写，而不是靠感觉补一个近似色。</p>
 *
 * <h2>为什么浅色有两套取值</h2>
 * <p>base.css 的浅色令牌写在两个选择器块里，两者对 {@code :root} 的特异性
 * 相同（都是 (0,1,0)），因此<b>后出现的那个才是生效值</b>：</p>
 * <ol>
 * <li>{@code :root} —— base.css 行 1–222。作者把它当「基础/兜底层」，
 * {@code --te-primary-500} 还是紫色 {@code #7c4dff}。</li>
 * <li>{@code :root, :root[data-theme='pureWhite']} —— base.css 行 484–523。
 * 注意选择器列表里第一个就是裸 {@code :root}，所以这一块<b>永远匹配</b>，
 * 与 html 上有没有 {@code data-theme} 属性无关；它把主色改成蓝
 * {@code #2563eb}，也顺带改了 glass / neutral 系列。</li>
 * </ol>
 * <p>截图（paper-light 预设）里生效的是第 2 层。为了让「任务书引用的值」和
 * 「实际渲染的值」都能被取到，本类对凡是两层不一致的令牌都<b>各存一份</b>：
 * 不带后缀的字段是第 1 层（{@code :root}），带 {@code PureWhite} 后缀的是
 * 第 2 层。默认渲染请用 {@code PureWhite} 那一组，除非你明确要复刻兜底层。</p>
 *
 * <p>深色只有一层：{@code :root[data-theme='dark']}，base.css 行 525–617。
 * 深色块<b>没有</b>重新声明字体栈、动效时长、{@code --te-radius-global}、
 * {@code --te-menu-width}、{@code --te-dialog-radius} 等令牌，它们从浅色层
 * 继承，所以这些在本类里是静态常量而不是实例字段。</p>
 *
 * <p>实例字段全部 {@code final}，在构造函数里用
 * {@code dark ? 深色值 : 浅色值} 一次性定值 —— Java 不允许在辅助方法里给
 * final 字段赋值，而这个写法顺带把两套取值并排放在一起，便于逐条核对。</p>
 *
 * <h2>来源索引</h2>
 * <ul>
 * <li>{@code base.css} 行 1–222：{@code :root} 浅色基础层</li>
 * <li>{@code base.css} 行 484–523：浅色生效层（pureWhite，恒匹配）</li>
 * <li>{@code base.css} 行 525–617：{@code :root[data-theme='dark']}</li>
 * <li>{@code base.css} 行 649–652：{@code .drag-region} 32px</li>
 * <li>{@code base.css} 行 722–746：{@code body} 行高 1.65 / 字号 14px</li>
 * <li>{@code base.css} 行 836–854：浅色 {@code body::before} 环境光</li>
 * <li>{@code base.css} 行 817–829：深色 {@code body::before} 环境光</li>
 * <li>{@code theme-layouts/paper-light.css} 行 1–111：paper-light 第一层</li>
 * <li>{@code theme-layouts/paper-light.css} 行 115–169：paper-light 第二层
 * （<b>后者覆盖前者</b>，截图里生效的是这一层）</li>
 * </ul>
 *
 * <p>本文件只读令牌，不含任何绘制逻辑：它是后续 {@code twilight} 包下各
 * 屏幕类的唯一取色/取尺寸入口。</p>
 */
public final class TwilightTheme
{
	// ------------------------------------------------------------------
	// 单例入口
	// ------------------------------------------------------------------

	private static final TwilightTheme LIGHT = new TwilightTheme(false);
	private static final TwilightTheme DARK = new TwilightTheme(true);

	/** 浅色主题（paper-light 预设实际生效值）。 */
	public static TwilightTheme light()
	{
		return LIGHT;
	}

	/** 深色主题（{@code :root[data-theme='dark']}）。 */
	public static TwilightTheme dark()
	{
		return DARK;
	}

	/** 按布尔开关取主题。 */
	public static TwilightTheme of(boolean dark)
	{
		return dark ? DARK : LIGHT;
	}

	private final boolean dark;

	private TwilightTheme(boolean dark)
	{
		this.dark = dark;

		// ---- 主色与强调色：深色行 / 浅色生效层行 ----
		primary500 = dark ? 0xFFF59E0B : 0xFF2563EB; // 深 527 / 浅 486
		primary400 = dark ? 0xFFFBBF24 : 0xFF3B82F6; // 深 528 / 浅 487
		primary300 = dark ? 0xFFFDE68A : 0xFF93C5FD; // 深 529 / 浅 488
		primaryRgb = dark ? new float[]{245F / 255F, 158F / 255F, 11F / 255F}
			: new float[]{37F / 255F, 99F / 255F, 235F / 255F}; // 深 530 / 浅 489
		favorite500 = dark ? 0xFFD94F7D : 0xFFDB2777; // 深 531 / 浅 490
		success500 = dark ? 0xFF14B881 : 0xFF16A34A; // 深 532 / 浅 491
		warning500 = dark ? 0xFFF59E0B : 0xFFD97706; // 深 533 / 浅 492
		info500 = dark ? 0xFF38BDF8 : 0xFF2563EB; // 深 534 / 浅 493
		accentCyan = dark ? 0xFF2DD4BF : 0xFF0891B2; // 深 535 / 浅 494

		// ---- 中性色阶 ----
		neutral50 = dark ? 0xFF050505 : 0xFFFFFFFF; // 深 536 / 浅 495
		neutral100 = dark ? 0xFF111111 : 0xFFF8FAFC; // 深 537 / 浅 496
		neutral200 = dark ? 0xFF1F1F1F : 0xFFE5E7EB; // 深 538 / 浅 497
		neutral300 = dark ? 0xFF343434 : 0xFFD1D5DB; // 深 539 / 浅 498
		neutral500 = dark ? 0xFF9B9B9B : 0xFF64748B; // 深 540 / 浅 499
		neutral700 = dark ? 0xFFD8D8D8 : 0xFF334155; // 深 541 / 浅 500
		neutral900 = dark ? 0xFFF7F7F2 : 0xFF0F172A; // 深 542 / 浅 501

		text = neutral900; // 深 551 / 浅 509 --color-text
		textMuted = neutral500; // 次要文字＝neutral-500（无独立令牌）
		chromeText = dark ? 0xFFD8D8D8 : 0xFF475569; // 深 543 / 浅 66
		settingsTextMuted = dark ? 0xFF9B9B9B : 0xFF8A8F98; // 深 579 / 浅 93

		// ---- 玻璃层 ----
		glassBg = dark ? 0xEB181818 : 0xE6FFFFFF; // 深 544 / 浅 18 (255,255,255,.9)
		glassBgPureWhite = dark ? glassBg : 0xF0FFFFFF; // 深同 544 / 浅 502 (.94)
		glassBgStrong = dark ? 0xF71D1D1D : 0xF5FFFFFF; // 深 545 / 浅 19 (.96)
		glassBgStrongPureWhite = dark ? glassBgStrong : 0xFAFFFFFF; // 深同 545 / 浅 503 (.98)
		glassBorder = dark ? 0x17FFFFFF : 0x8CFFFFFF; // 深 546 / 浅 20 (.55 白)
		glassBorderPureWhite = dark ? glassBorder : 0x1A0F172A; // 深同 546 / 浅 504
		glassShadowCss = dark ? "0 18px 54px rgba(0, 0, 0, 0.34)" // 深 547
			: "0 16px 42px rgba(15, 23, 42, 0.08)"; // 浅 505

		// ---- 辉光 ----
		glowMain = dark ? 0x2EF59E0B : 0x1F2563EB; // 深 548 / 浅 506
		glowSoft = dark ? 0x1FD94F7D : 0x143B82F6; // 深 549 / 浅 507
		glowCyan = dark ? 0x1A2DD4BF : 0x140891B2; // 深 550 / 浅 508

		// ---- 应用背景 ----
		appBg = dark ? 0xFF17181A : 0xFFF4F4F7; // 深 552 / 浅 67
		appBgImage = 0x00000000; // 68 none，深色块未覆盖
		backgroundGradientStart = 0xFFEFF6FF; // 70，深色块未覆盖
		backgroundGradientEnd = 0xFFF5F3FF; // 71，深色块未覆盖
		backgroundGradientAngle = 135F; // 72
		backgroundCoverBlur = 28F; // 73
		backgroundOverlayOpacity = 0.12F; // 74
		streamingSurface = dark ? appBg : 0xFFFAFBFE; // 深 558 / 浅 161
		settingsBg = dark ? appBg : 0xFFF5F6F8; // 深 554 / 浅 82
		settingsBackplate = dark ? 0xFF17181A : 0xFFF5F6F8; // 深 555 / 浅 84

		// ---- 导航（侧栏） ----
		navigationBg = dark ? 0xFF17181A : 0xF0FFFFFF; // 深 590 / 浅 104 (.94 白)
		navigationBorder = dark ? 0x00000000 : 0x0D000000; // 深 591 / 浅 105
		navigationShadowCss = dark ? "none" // 深 592
			: "4px 0 24px rgba(15, 23, 42, 0.03)"; // 浅 106
		navigationText = dark ? 0xFFD8D8D8 : 0xFF475569; // 深 593 / 浅 107
		navigationIcon = dark ? 0xFF9B9B9B : 0xFF64748B; // 深 594 / 浅 108
		navigationHover = dark ? 0x11FFFFFF : 0x0A0F172A; // 深 595 / 浅 109
		navigationHoverText = dark ? 0xFFF7F7F2 : 0xFF0F172A; // 深 596 / 浅 110
		navigationActive = dark ? 0x29F59E0B : 0x142563EB; // 深 597 / 浅 111
		navigationActiveText = dark ? 0xFFF59E0B : 0xFF2563EB; // 深 598 / 浅 112
		navigationIndicator = dark ? 0xFFF59E0B : 0xFF2563EB; // 深 599 / 浅 113

		// ---- 卡片 / 表面对比 ----
		cardBg = dark ? 0xFF181818 : 0xFFFFFFFF; // 深 559 / 浅 162
		cardBorder = dark ? 0x1AFFFFFF : 0x140F172A; // 深 560 / 浅 163
		subtleBg = dark ? 0xFF121212 : 0xFFF8FAFC; // 深 561 / 浅 164
		hoverBg = dark ? 0x11FFFFFF : 0xFFF3F4F6; // 深 562 / 浅 165
		activeBg = dark ? 0x29F59E0B : 0xFFE8E8E8; // 深 563 / 浅 166
		shellControlText = dark ? 0xFFF7F7F2 : 0xFF0F172A; // 深 564 / 浅 167
		shellControlHover = dark ? 0x1FF59E0B : 0x142563EB; // 深 577 / 浅 168

		// ---- 曲库 / 表格 ----
		// 深色块 600–603 重复了浅色值：深色下曲库仍是浅色表面。按原样保留，
		// 不替上游"修 bug"，否则与参考实现不一致。
		libraryBgCss = "linear-gradient(180deg, rgba(255, 255, 255, 0.96),"
			+ " rgba(255, 255, 255, 0.9))"; // 浅 116 / 深 600
		libraryTableBg = 0x29FFFFFF; // 浅 117 / 深 601 (.16 白)
		libraryTableBorder = 0x85FFFFFF; // 浅 118 / 深 602 (.52 白)
		libraryTableShadowCss = "0 26px 78px rgba(86, 70, 160, 0.1)"; // 浅 119 / 深 603
		libraryRowText = dark ? 0xFFD8D8D8 : 0xFF334155; // 深 604 / 浅 120
		libraryRowHover = dark ? 0x11FFFFFF : 0x38FFFFFF; // 深 605 / 浅 121
		librarySelectionBg = dark ? 0x0FFFFFFF : 0x0B0F172A; // 深 606 / 浅 122
		librarySelectionHover = dark ? 0x17FFFFFF : 0x120F172A; // 深 607 / 浅 123
		librarySelectionIndicator = dark ? 0xB8FFFFFF : 0x8C0F172A; // 深 608 / 浅 124
		libraryIcon = 0xFF64748B; // 125，深色块未覆盖
		libraryActionBg = 0x142563EB; // 131，深色块未覆盖

		// ---- 播放条 ----
		playerProgressTrack = 0x292563EB; // 140，深色块未覆盖
		playerProgressFillCss = "linear-gradient(90deg, #2563eb, #0d9488)"; // 141
		playerTimeSurface = 0x142563EB; // 145，深色块未覆盖
		equalizerButtonBg = 0x142563EB; // 158，深色块未覆盖
		equalizerSpectrum = 0xFF2563EB; // 157，深色块未覆盖

		// ---- 语义软色 ----
		successSoftBg = dark ? 0x2614B881 : 0xFFF0FDF4; // 深 609 / 浅 202
		successSoftFg = dark ? 0xFF5EE3B4 : 0xFF16A34A; // 深 610 / 浅 203
		infoSoftBg = dark ? 0x2438BDF8 : 0xFFEFF6FF; // 深 611 / 浅 204
		infoSoftFg = dark ? 0xFF7DD3FC : 0xFF3B82F6; // 深 612 / 浅 205
		warningSoftBg = dark ? 0x29F59E0B : 0xFFFFF7ED; // 深 613 / 浅 206
		warningSoftFg = dark ? 0xFFFBBF24 : 0xFFD97706; // 深 614 / 浅 207
		dangerSoftBg = dark ? 0x29D94F7D : 0xFFFEF2F2; // 深 615 / 浅 208
		dangerSoftFg = dark ? 0xFFF9A8C2 : 0xFFB91C1C; // 深 616 / 浅 209

		// ---- 滚动条 / 返回按钮 / 回顶按钮 ----
		scrollbarThumb = dark ? 0x57D4D4D8 : 0x57475569; // 深 565 / 浅 210
		scrollbarThumbHover = dark ? 0x94F4F4F5 : 0x8F334155; // 深 566 / 浅 211
		backButtonBg = dark ? 0xE018181B : 0xE6FFFFFF; // 深 567 / 浅 212
		backButtonBgHover = dark ? 0xF527272A : 0xFFFFFFFF; // 深 568 / 浅 213
		backButtonBorder = dark ? 0x24FFFFFF : 0x1F0F172A; // 深 569 / 浅 214
		backButtonColor = dark ? 0xFF60A5FA : 0xFF2563EB; // 深 570 / 浅 215
		backButtonShadowCss = dark ? "0 4px 14px rgba(0, 0, 0, 0.24)" // 深 571
			: "0 4px 12px rgba(15, 23, 42, 0.08)"; // 浅 216
		scrollTopBg = dark ? 0xE61C1C1F : 0xEBFFFFFF; // 深 572 / 浅 217
		scrollTopBgHover = dark ? 0xF52E2E33 : 0xFFFFFFFF; // 深 573 / 浅 218
		scrollTopBorder = dark ? 0x24FFFFFF : 0x1A0F172A; // 深 574 / 浅 219
		scrollTopColor = dark ? 0xFF60A5FA : 0xFF2563EB; // 深 575 / 浅 220
		scrollTopShadowCss = dark ? "0 10px 28px rgba(0, 0, 0, 0.44)" // 深 576
			: "0 8px 24px rgba(15, 23, 42, 0.14)"; // 浅 221

		// ---- 设置页 ----
		settingsText = dark ? 0xFFF7F7F2 : 0xFF1A1A1A; // 深 578 / 浅 92
		settingsControlBg = dark ? 0xFF181818 : 0xFFFFFFFF; // 深 580 / 浅 94
		settingsControlBorder = dark ? 0x1AFFFFFF : 0x0F0F172A; // 深 581 / 浅 95
		settingsPanelBorder = 0x00000000; // 浅 96 / 深 582，都是 transparent
		settingsNavText = dark ? 0xFFD8D8D8 : 0xFF5C6370; // 深 583 / 浅 97
		settingsNavHover = dark ? 0x11FFFFFF : 0x0A0F172A; // 深 584 / 浅 98
		settingsNavActive = dark ? 0x1AFFFFFF : 0xFFFFFFFF; // 深 585 / 浅 99
		settingsRowBg = dark ? 0x0AFFFFFF : 0xFFFFFFFF; // 深 586 / 浅 100
		settingsSearchBg = dark ? 0x14FFFFFF : 0xFFEEF0F3; // 深 587 / 浅 101
		settingsShadowCss = dark ? "0 2px 16px rgba(0, 0, 0, 0.28)" // 深 588
			: "0 2px 16px rgba(15, 23, 42, 0.04)"; // 浅 102
		settingsShadowSoftCss = dark ? "0 1px 4px rgba(0, 0, 0, 0.22)" // 深 589
			: "0 1px 4px rgba(15, 23, 42, 0.04)"; // 浅 103
	}

	public boolean isDark()
	{
		return dark;
	}

	// ------------------------------------------------------------------
	// 主题无关常量：深色块未重声明，两套主题同值
	// ------------------------------------------------------------------

	/** {@code --te-font-size-body: 14px}，base.css 行 65。 */
	public static final float FONT_SIZE_BODY = 14F;

	/** {@code html { font-size: 14px }}，base.css 行 644（rem 基准）。 */
	public static final float ROOT_FONT_SIZE = 14F;

	/** {@code body { line-height: 1.65 }}，base.css 行 732。 */
	public static final float LINE_HEIGHT = 1.65F;

	/**
	 * 品牌行与侧栏菜单标签的字号：{@code calc(var(--te-font-size-body, 14px)
	 * * 12 / 14)} = 12px。paper-light.css 行 37（品牌行）与行 85（菜单标签）。
	 */
	public static final float FONT_SIZE_CHROME = 12F;

	/** 字重：{@code --te-text-title: 500}，base.css 行 61。 */
	public static final int WEIGHT_TITLE = 500;
	/** 字重：{@code --te-text-body: 400}，base.css 行 62。 */
	public static final int WEIGHT_BODY = 400;
	/** 字重：{@code --te-text-meta: 400}，base.css 行 63。 */
	public static final int WEIGHT_META = 400;
	/** 字重：{@code --te-text-strong: 500}，base.css 行 64。 */
	public static final int WEIGHT_STRONG = 500;

	/**
	 * {@code --te-font-sans}，base.css 行 58–60。Minecraft 端没有 CSS 字体栈，
	 * 这里的顺序就是「先试哪个字体文件、再回退到哪个」的优先级。
	 */
	public static final String[] FONT_STACK_SANS = {"Inter", "Plus Jakarta Sans",
		"MiSans", "Microsoft YaHei UI", "Microsoft YaHei", "PingFang SC",
		"Hiragino Sans GB", "system-ui", "-apple-system",
		"BlinkMacSystemFont", "Segoe UI", "sans-serif"};

	/** {@code --te-font-display}，base.css 行 52–54。 */
	public static final String[] FONT_STACK_DISPLAY = {"Inter",
		"Plus Jakarta Sans", "MiSans", "Microsoft YaHei UI", "Microsoft YaHei",
		"PingFang SC", "Hiragino Sans GB", "system-ui", "sans-serif"};

	/** {@code --te-font-rounded}，base.css 行 55–57。 */
	public static final String[] FONT_STACK_ROUNDED = {"Inter",
		"Plus Jakarta Sans", "MiSans", "Microsoft YaHei UI", "Microsoft YaHei",
		"PingFang SC", "Hiragino Sans GB", "system-ui", "sans-serif"};

	/**
	 * paper-light 品牌行专用字体：{@code 'Space Grotesk', var(--te-font-sans)}，
	 * paper-light.css 行 36。Space Grotesk 本身<b>不在</b> FONT_STACK_SANS 里。
	 */
	public static final String FONT_FAMILY_BRAND = "Space Grotesk";

	// ---- 动效令牌：base.css 行 26–46，深色块未覆盖 ----

	/** {@code --te-ease-enter: cubic-bezier(0.4, 0, 0.2, 1)}，行 26。 */
	public static final float[] EASE_ENTER = {0.4F, 0F, 0.2F, 1F};
	/** {@code --te-ease-spring: cubic-bezier(0.22, 1.14, 0.36, 1)}，行 33。 */
	public static final float[] EASE_SPRING = {0.22F, 1.14F, 0.36F, 1F};
	/** {@code --te-ease-out-quint: cubic-bezier(0.22, 1, 0.36, 1)}，行 34。 */
	public static final float[] EASE_OUT_QUINT = {0.22F, 1F, 0.36F, 1F};
	/** {@code --te-ease-out-expo: cubic-bezier(0.16, 1, 0.3, 1)}，行 35。 */
	public static final float[] EASE_OUT_EXPO = {0.16F, 1F, 0.3F, 1F};
	/** {@code --te-ease-out-strong: cubic-bezier(0.23, 1, 0.32, 1)}，行 37。 */
	public static final float[] EASE_OUT_STRONG = {0.23F, 1F, 0.32F, 1F};

	/**
	 * {@code --te-ease-soft: var(--te-ease-out-quint)}，行 32。注释（行 27–31）
	 * 说明运行时权威是 {@code src/shared/themeTokens.ts} 的 {@code motion.soft}，
	 * 由 {@code useThemeStore} 以 {@code !important} 注入 {@code :root}。
	 */
	public static final float[] EASE_SOFT = EASE_OUT_QUINT;

	/** {@code --te-motion-press: 90ms}，行 38。 */
	public static final int MOTION_PRESS_MS = 90;
	/** {@code --te-motion-hover: 160ms}，行 39。 */
	public static final int MOTION_HOVER_MS = 160;
	/** {@code --te-motion-panel: 280ms}，行 40。 */
	public static final int MOTION_PANEL_MS = 280;
	/** {@code --te-motion-page: 400ms}，行 41。 */
	public static final int MOTION_PAGE_MS = 400;
	/** {@code --te-motion-settle: 500ms}，行 43（hover-in 长尾）。 */
	public static final int MOTION_SETTLE_MS = 500;
	/** {@code --te-motion-return: 220ms}，行 44（hover-out 快速回位）。 */
	public static final int MOTION_RETURN_MS = 220;
	/** {@code --te-motion-press-scale: 0.97}，行 45。 */
	public static final float MOTION_PRESS_SCALE = 0.97F;
	/** {@code --te-motion-hover-translate: -1px}，行 46。 */
	public static final float MOTION_HOVER_TRANSLATE = -1F;
	/** {@code --te-ui-scale: 0.94}，行 47（上游未说明用途，按原值保留）。 */
	public static final float UI_SCALE = 0.94F;

	/** {@code html[data-te-motion='reduced']} 覆盖值，行 420–427。 */
	public static final int MOTION_HOVER_REDUCED_MS = 100;
	/** 行 423。 */
	public static final int MOTION_PANEL_REDUCED_MS = 120;
	/** 行 424。 */
	public static final int MOTION_PAGE_REDUCED_MS = 120;

	// ---- 几何令牌 ----

	/**
	 * {@code --te-menu-width: clamp(132px, 18vw, 216px)}，base.css 行 48。
	 * 注意任务书里写的 {@code clamp(180px,18vw,216px)} 与 base.css 实测不符：
	 * base.css 的下限是 <b>132px</b>；180px 来自 SideMenu.vue 的组件级宽度
	 * （见 {@link #SIDEBAR_WIDTH_MIN_VUE}）。
	 */
	public static final float MENU_WIDTH_MIN = 132F;
	/** 行 48，{@code 18vw} 的比例系数。 */
	public static final float MENU_WIDTH_VIEWPORT_RATIO = 0.18F;
	/** 行 48，clamp 上限 216px。 */
	public static final float MENU_WIDTH_MAX = 216F;

	/** {@code --te-radius-global: 10px}，行 49。 */
	public static final float RADIUS_GLOBAL = 10F;
	/** {@code --te-dialog-radius: 8px}，行 75。 */
	public static final float RADIUS_DIALOG = 8F;
	/** {@code --te-search-radius: 10px}，行 76。 */
	public static final float RADIUS_SEARCH = 10F;
	/** {@code --te-toast-radius: 8px}，行 77。 */
	public static final float RADIUS_TOAST = 8F;
	/** {@code --te-track-title-radius: 6px}，行 78。 */
	public static final float RADIUS_TRACK_TITLE = 6F;
	/** {@code --te-library-selection-radius: 10px}，行 127。 */
	public static final float RADIUS_LIBRARY_SELECTION = 10F;
	/** {@code --te-library-cover-radius: 8px}，行 129。 */
	public static final float RADIUS_LIBRARY_COVER = 8F;
	/** {@code --te-library-action-radius: 12px}，行 132。 */
	public static final float RADIUS_LIBRARY_ACTION = 12F;
	/** {@code --te-artwork-list-radius: 12px}，行 134。 */
	public static final float RADIUS_ARTWORK_LIST = 12F;
	/** {@code --te-equalizer-panel-radius: 20px}，行 150。 */
	public static final float RADIUS_EQUALIZER_PANEL = 20F;
	/** {@code --te-equalizer-button-radius: 10px}，行 159。 */
	public static final float RADIUS_EQUALIZER_BUTTON = 10F;
	/** 返回按钮 icon 变体半径 10px，行 283。 */
	public static final float RADIUS_BACK_BUTTON_ICON = 10F;
	/** 返回按钮 pill / 播放控件 / 进度条：999px = 全圆，行 287、138、143。 */
	public static final float RADIUS_PILL = 999F;

	/** {@code --te-library-icon-size: 18px}，行 126。 */
	public static final float LIBRARY_ICON_SIZE = 18F;
	/** {@code --te-navigation-radius: 0px}，行 115。 */
	public static final float NAVIGATION_RADIUS = 0F;
	/** {@code --te-navigation-opacity: 94%}，行 114。 */
	public static final float NAVIGATION_OPACITY = 0.94F;
	/** {@code --te-surface-opacity: 100%}，行 50。 */
	public static final float SURFACE_OPACITY = 1F;
	/** {@code --te-library-selection-inline-inset: 0px}，行 128。 */
	public static final float LIBRARY_SELECTION_INLINE_INSET = 0F;
	/** {@code --te-playback-cover-size: 100%}，行 133。 */
	public static final float PLAYBACK_COVER_SIZE = 1F;
	/** {@code --te-library-title-overlay-opacity: 72%}，行 130。 */
	public static final float LIBRARY_TITLE_OVERLAY_OPACITY = 0.72F;
	/** {@code --te-track-title-opacity: 0%}，行 79。 */
	public static final float TRACK_TITLE_OPACITY = 0F;
	/** {@code --te-player-time-opacity: 0%}，行 147。 */
	public static final float PLAYER_TIME_OPACITY = 0F;

	// ---- 播放条几何：base.css 行 135–146 ----

	/** {@code --te-player-control-size: 32px}，行 135。 */
	public static final float PLAYER_CONTROL_SIZE = 32F;
	/** {@code --te-player-play-size: 44px}，行 136。 */
	public static final float PLAYER_PLAY_SIZE = 44F;
	/** {@code --te-player-control-gap: 12px}，行 137。 */
	public static final float PLAYER_CONTROL_GAP = 12F;
	/** {@code --te-player-control-border-width: 0px}，行 139。 */
	public static final float PLAYER_CONTROL_BORDER_WIDTH = 0F;
	/** {@code --te-player-progress-height: 6px}，行 142。 */
	public static final float PLAYER_PROGRESS_HEIGHT = 6F;
	/** {@code --te-player-progress-thumb-size: 12px}，行 144。 */
	public static final float PLAYER_PROGRESS_THUMB_SIZE = 12F;
	/** {@code --te-player-time-radius: 8px}，行 146。 */
	public static final float PLAYER_TIME_RADIUS = 8F;

	// ---- 均衡器几何：base.css 行 154、160 ----

	/** {@code --te-equalizer-slider-thumb-size: 20px}，行 154。 */
	public static final float EQUALIZER_SLIDER_THUMB_SIZE = 20F;
	/** {@code --te-equalizer-knob-size: 18px}，行 160。 */
	public static final float EQUALIZER_KNOB_SIZE = 18F;

	// ---- 标题栏 / 侧栏 / 窗口控件几何 ----

	/** {@code .drag-region { height: 32px }}，base.css 行 649–652。 */
	public static final float DRAG_REGION_HEIGHT = 32F;

	/**
	 * paper-light {@code .title-bar.drag-region { min-height: 54px }}，
	 * paper-light.css 行 5–12。这是截图预设的标题栏高度。
	 */
	public static final float TITLE_BAR_HEIGHT = 54F;

	/** paper-light 标题栏按钮圆角 4px，paper-light.css 行 18–22。 */
	public static final float TITLE_BAR_BUTTON_RADIUS = 4F;

	/** paper-light 品牌行高度 64px，paper-light.css 行 33–39。 */
	public static final float BRAND_ROW_HEIGHT_PAPER_LIGHT = 64F;

	/** paper-light 品牌行图标圆角 4px，paper-light.css 行 41–43。 */
	public static final float BRAND_ICON_RADIUS = 4F;

	/** paper-light 播放页封面圆角 3px，paper-light.css 行 108–111。 */
	public static final float PLAYING_COVER_RADIUS_PAPER_LIGHT = 3F;

	/**
	 * SideMenu.vue 组件级侧栏宽度下限（任务书与父级核实值）。base.css 的
	 * {@code --te-menu-width} 下限是 132px，两者不同，取哪个由屏幕决定。
	 */
	public static final float SIDEBAR_WIDTH_MIN_VUE = 180F;
	/** SideMenu.vue 紧凑档宽度。 */
	public static final float SIDEBAR_WIDTH_COMPACT = 164F;
	/** SideMenu.vue 图标轨（rail）宽度。 */
	public static final float SIDEBAR_WIDTH_RAIL = 72F;

	/** SideMenu.vue 常规档菜单项高度，父级核实值。 */
	public static final float SIDEBAR_ITEM_HEIGHT_VUE = 40F;
	/** paper-light 第二层把菜单项高度改成 45px，paper-light.css 行 136–144。 */
	public static final float SIDEBAR_ITEM_HEIGHT_PAPER_LIGHT = 45F;

	/** SideMenu.vue 导航内边距 {@code 16px 12px 16px 4px}（父级核实值）。 */
	public static final float[] SIDEBAR_NAV_PADDING_VUE = {16F, 12F, 16F, 4F};
	/** paper-light 第一层导航内边距 {@code 18px 12px}，paper-light.css 行 45–47。 */
	public static final float[] SIDEBAR_NAV_PADDING_PAPER_BASE = {18F, 12F};
	/** paper-light 第二层导航内边距 {@code 22px 13px}，paper-light.css 行 127–130。 */
	public static final float[] SIDEBAR_NAV_PADDING_PAPER_LIGHT = {22F, 13F};

	/** SideMenu.vue 菜单项间距 6px（父级核实值）。 */
	public static final float SIDEBAR_ITEM_GAP_VUE = 6F;
	/** paper-light 第一层 menu-nav gap 4px，paper-light.css 行 49–51。 */
	public static final float SIDEBAR_ITEM_GAP_PAPER_BASE = 4F;
	/** paper-light 第二层 menu-nav gap 5px，paper-light.css 行 132–134。 */
	public static final float SIDEBAR_ITEM_GAP_PAPER_LIGHT = 5F;
	/** paper-light 第二层 menu-items gap 12px，paper-light.css 行 127–130。 */
	public static final float SIDEBAR_SECTION_GAP_PAPER_LIGHT = 12F;

	/** SideMenu.vue 标题栏工具按钮宽度 36px（父级核实值）。 */
	public static final float TITLE_BAR_BUTTON_SIZE = 36F;

	/**
	 * 播放条高度<b>实测值</b> 70px。
	 *
	 * <p>{@code PlayerBar.css} 不在参考快照内 ⇒ <b>没有权威值</b>。70px 来自
	 * {@code streaming-home.png} 的像素扫描：底部白色浮动条纵向占 y = 796..865
	 * （70 行），其下留空约 13px。请勿把 {@link #TITLE_BAR_HEIGHT}（54px，
	 * paper-light 的 {@code .title-bar.drag-region { min-height: 54px }}）当成
	 * 播放条高度——那是标题栏的令牌。</p>
	 */
	public static final float PLAYER_BAR_HEIGHT_MEASURED = 70F;

	/**
	 * 侧栏菜单项行距<b>实测值</b> 50px。
	 *
	 * <p>来自 {@code streaming-home.png} 里 5 个菜单项的文字行心
	 * （101.5 / 151.5 / 201.5 / 251.5 / 301.5），行距恒定 50px。
	 * 它唯一地印证 paper-light <b>第二层</b>（{@link
	 * #SIDEBAR_ITEM_HEIGHT_PAPER_LIGHT} 45px + {@link
	 * #SIDEBAR_ITEM_GAP_PAPER_LIGHT} 5px = 50px），而不是第一层的 40+4
	 * 或 SideMenu.vue 的 40+6。</p>
	 */
	public static final float SIDEBAR_ITEM_PITCH_MEASURED = 50F;

	// ---- paper-light 第一层（被第二层覆盖，保留以便对照）----

	/** 菜单项圆角 4px，paper-light.css 行 53–57。 */
	public static final float MENU_ITEM_RADIUS_PAPER_BASE = 4F;
	/** 选中项左侧指示条宽度 2px，paper-light.css 行 69–78。 */
	public static final float MENU_INDICATOR_WIDTH_PAPER_BASE = 2F;
	/** 选中项指示条上下内缩 12px，行 70–71。 */
	public static final float MENU_INDICATOR_INSET_PAPER_BASE = 12F;
	/** 菜单分隔线外边距 {@code 14px 8px}，行 89–92。 */
	public static final float[] MENU_SEPARATOR_MARGIN_PAPER_BASE = {14F, 8F};

	// ---- paper-light 第二层（截图实际生效值，行 115–169）----

	/** 侧栏浮动面板上下内缩 22px，行 116–117。 */
	public static final float SHELL_SIDEBAR_INSET_Y = 22F;
	/** 侧栏右侧圆角 {@code border-radius: 0 26px 26px 0}，行 120。 */
	public static final float SHELL_SIDEBAR_RADIUS_RIGHT = 26F;
	/** 侧栏背景模糊 {@code blur(26px) saturate(140%)}，行 123–124。 */
	public static final float SHELL_SIDEBAR_BLUR_RADIUS = 26F;
	/** 行 123–124 的饱和度倍数。 */
	public static final float SHELL_SIDEBAR_BLUR_SATURATE = 1.4F;
	/** 侧栏投影 {@code 12px 22px 58px}，行 122。 */
	public static final float[] SHELL_SIDEBAR_SHADOW = {12F, 22F, 58F};
	/** 侧栏底色混合比例：{@code color-mix(navigation-bg 82%, primary-500)}，行 121。 */
	public static final float SHELL_SIDEBAR_BG_NAV_WEIGHT = 0.82F;
	/** 菜单项圆角 13px，行 136–139。 */
	public static final float MENU_ITEM_RADIUS_PAPER_LIGHT = 13F;
	/** 菜单项横向内边距 14px，行 138。 */
	public static final float MENU_ITEM_PADDING_X_PAPER_LIGHT = 14F;
	/** 选中项指示条宽度 3px，行 156–164。 */
	public static final float MENU_INDICATOR_WIDTH_PAPER_LIGHT = 3F;
	/** 选中项指示条上下内缩 9px，行 157–158。 */
	public static final float MENU_INDICATOR_INSET_PAPER_LIGHT = 9F;
	/** 选中项指示条左边距 5px，行 159。 */
	public static final float MENU_INDICATOR_LEFT_PAPER_LIGHT = 5F;
	/** 选中项指示条辉光半径 {@code 0 0 18px}，行 163。 */
	public static final float MENU_INDICATOR_GLOW_PAPER_LIGHT = 18F;
	/** hover 位移 {@code translateX(3px)}，行 146–149。 */
	public static final float MENU_ITEM_HOVER_TRANSLATE_PAPER_LIGHT = 3F;
	/** 菜单分隔线外边距 {@code 10px 18px}，行 166–169。 */
	public static final float[] MENU_SEPARATOR_MARGIN_PAPER_LIGHT = {10F, 18F};
	/** 选中项内描边宽度 {@code inset 0 0 0 1px}，行 151–154。 */
	public static final float MENU_ACTIVE_INSET_BORDER = 1F;

	/**
	 * {@code --sf-shell-line: color-mix(in srgb, var(--te-neutral-900) 13%,
	 * transparent)}，paper-light.css 行 1–3。它没有自己的十六进制值，是
	 * neutral-900 的 13% 透明版，所以随主题变化。
	 */
	public static final float SHELL_LINE_ALPHA = 0.13F;

	// ---- 浅色环境光背景：base.css 行 836–854 ----

	/** {@code body::before} 的 conic-gradient 起点角度 18deg，行 843–844。 */
	public static final float AMBIENT_CONIC_FROM_DEG = 18F;
	/** conic 圆心 {@code at 72% 18%}，行 843。 */
	public static final float[] AMBIENT_CONIC_CENTER = {0.72F, 0.18F};
	/** conic 第一段色标 {@code rgba(124, 77, 255, 0.2) 52deg}，行 846。 */
	public static final int AMBIENT_CONIC_STOP_1 = 0x337C4DFF;
	/** 行 846 的角度。 */
	public static final float AMBIENT_CONIC_STOP_1_DEG = 52F;
	/** conic 第二段色标 {@code rgba(255, 126, 182, 0.12) 96deg}，行 847。 */
	public static final int AMBIENT_CONIC_STOP_2 = 0x1FFF7EB6;
	/** 行 847 的角度。 */
	public static final float AMBIENT_CONIC_STOP_2_DEG = 96F;
	/** conic 收束角 {@code transparent 148deg}，行 848。 */
	public static final float AMBIENT_CONIC_END_DEG = 148F;
	/** 叠在 conic 上的线性渐变两端，行 851。 */
	public static final int AMBIENT_LINEAR_START = 0xEBFFFFFF;
	/** 行 851。 */
	public static final int AMBIENT_LINEAR_END = 0xC7F7F4FF;
	/** {@code opacity: 0.9}，行 852。 */
	public static final float AMBIENT_OPACITY = 0.9F;
	/** {@code animation: ambient-light-shift 14s ... infinite alternate}，行 853。 */
	public static final int AMBIENT_PERIOD_MS = 14_000;

	// ---- 深色环境光背景：base.css 行 817–829 ----

	/** 深色 conic 第一段 {@code rgba(var(--te-primary-rgb), 0.1) 54deg}，行 822。 */
	public static final float AMBIENT_DARK_CONIC_STOP_1_DEG = 54F;
	/** 行 823 {@code rgba(217, 79, 125, 0.06) 98deg}。 */
	public static final float AMBIENT_DARK_CONIC_STOP_2_DEG = 98F;
	/** 行 824 收束角 {@code transparent 150deg}。 */
	public static final float AMBIENT_DARK_CONIC_END_DEG = 150F;
	/** {@code opacity: 0.72}，行 828。 */
	public static final float AMBIENT_DARK_OPACITY = 0.72F;

	/**
	 * 禁用态不是独立颜色令牌：base.css 行 370–376 用
	 * {@code opacity: 0.52} 压暗整控件，因此禁用文字 = 正文色 × 0.52。
	 */
	public static final float DISABLED_OPACITY = 0.52F;

	/**
	 * {@code clamp(132px, 18vw, 216px)} 的 Java 版，对应 base.css 行 48。
	 *
	 * @param viewportWidth 视口宽度（px）
	 */
	public static float menuWidthPx(float viewportWidth)
	{
		return clamp(viewportWidth * MENU_WIDTH_VIEWPORT_RATIO, MENU_WIDTH_MIN,
			MENU_WIDTH_MAX);
	}

	/**
	 * SideMenu.vue 组件版本的侧栏宽度：{@code clamp(180px, 18vw, 216px)}，
	 * 低于断点时切 164px 紧凑档或 72px 图标轨。
	 *
	 * @param viewportWidth 视口宽度（px）
	 * @param compact       是否紧凑档
	 * @param rail          是否图标轨
	 */
	public static float sidebarWidthPx(float viewportWidth, boolean compact,
		boolean rail)
	{
		if(rail)
			return SIDEBAR_WIDTH_RAIL;
		if(compact)
			return SIDEBAR_WIDTH_COMPACT;
		return clamp(viewportWidth * MENU_WIDTH_VIEWPORT_RATIO,
			SIDEBAR_WIDTH_MIN_VUE, MENU_WIDTH_MAX);
	}

	// ------------------------------------------------------------------
	// 实例令牌
	// ------------------------------------------------------------------

	// ---- 主色与强调色 ----

	/** {@code --te-primary-500}。浅 486 {@code #2563eb}（兜底 2 {@code #7c4dff}）。 */
	public final int primary500;
	/** {@code --te-primary-400}。浅 487 {@code #3b82f6}（兜底 3）。 */
	public final int primary400;
	/** {@code --te-primary-300}。浅 488 {@code #93c5fd}（兜底 4）。 */
	public final int primary300;
	/** {@code --te-primary-rgb} 的 0..1 分量，浅 489；深 530。 */
	public final float[] primaryRgb;
	/** {@code --te-favorite-500}，浅 490；深 531。 */
	public final int favorite500;
	/** {@code --te-success-500}，浅 491；深 532。 */
	public final int success500;
	/** {@code --te-warning-500}，浅 492；深 533。 */
	public final int warning500;
	/** {@code --te-info-500}，浅 493；深 534。 */
	public final int info500;
	/** {@code --te-accent-cyan}，浅 494；深 535。 */
	public final int accentCyan;

	// ---- 中性色阶 ----

	/** {@code --te-neutral-50}，浅 495；深 536。 */
	public final int neutral50;
	/** {@code --te-neutral-100}，浅 496；深 537。 */
	public final int neutral100;
	/** {@code --te-neutral-200}，浅 497；深 538。 */
	public final int neutral200;
	/** {@code --te-neutral-300}，浅 498；深 539。 */
	public final int neutral300;
	/** {@code --te-neutral-500}，浅 499；深 540。 */
	public final int neutral500;
	/** {@code --te-neutral-700}，浅 500；深 541。 */
	public final int neutral700;
	/** {@code --te-neutral-900}，浅 501；深 542。 */
	public final int neutral900;

	/** {@code --color-text: var(--te-neutral-900)}，浅 509；深 551。 */
	public final int text;
	/** 次要文字＝{@code --te-neutral-500}（上游没有独立令牌承担这个角色）。 */
	public final int textMuted;
	/** {@code --te-chrome-text}，浅 66 {@code #475569}；深 543。 */
	public final int chromeText;
	/** {@code --te-settings-text-muted}，浅 93；深 579。 */
	public final int settingsTextMuted;

	// ---- 玻璃层 ----

	/** {@code --te-glass-bg}，base.css 行 18 {@code rgba(255,255,255,0.9)} → 0xE6FFFFFF。 */
	public final int glassBg;
	/** {@code --te-glass-bg} 生效值（行 502 {@code 0.94}）；深色同 {@link #glassBg}。 */
	public final int glassBgPureWhite;
	/** {@code --te-glass-bg-strong}，行 19 {@code 0.96} → 0xF5FFFFFF。 */
	public final int glassBgStrong;
	/** {@code --te-glass-bg-strong} 生效值（行 503 {@code 0.98}）。 */
	public final int glassBgStrongPureWhite;
	/** {@code --te-glass-border}，行 20 {@code rgba(255,255,255,0.55)} → 0x8CFFFFFF。 */
	public final int glassBorder;
	/** {@code --te-glass-border} 生效值（行 504 {@code rgba(15,23,42,0.1)}）。 */
	public final int glassBorderPureWhite;
	/** {@code --te-glass-shadow} 原文：浅 505；深 547。 */
	public final String glassShadowCss;

	// ---- 辉光 ----

	/** {@code --te-glow-main}：浅 506；深 548。 */
	public final int glowMain;
	/** {@code --te-glow-soft}：浅 507；深 549。 */
	public final int glowSoft;
	/** {@code --te-glow-cyan}：浅 508；深 550。 */
	public final int glowCyan;

	// ---- 应用背景 ----

	/** {@code --te-app-bg}，浅 67 {@code #f4f4f7}；深 552 {@code #17181a}。 */
	public final int appBg;
	/** {@code --te-app-bg-image: none}，行 68。0 表示无图。 */
	public final int appBgImage;
	/** {@code --te-background-gradient-start}，行 70。 */
	public final int backgroundGradientStart;
	/** {@code --te-background-gradient-end}，行 71。 */
	public final int backgroundGradientEnd;
	/** {@code --te-background-gradient-angle: 135deg}，行 72。 */
	public final float backgroundGradientAngle;
	/** {@code --te-background-cover-blur: 28px}，行 73。 */
	public final float backgroundCoverBlur;
	/** {@code --te-background-overlay-opacity: 12%}，行 74。 */
	public final float backgroundOverlayOpacity;
	/** {@code --te-streaming-surface}，浅 161 {@code #fafbfe}；深 558 = appBg。 */
	public final int streamingSurface;
	/** {@code --te-settings-bg}，浅 82 {@code #f5f6f8}；深 554 = appBg。 */
	public final int settingsBg;
	/** {@code --te-settings-backplate}，浅 84；深 555。 */
	public final int settingsBackplate;

	// ---- 导航（侧栏） ----

	/** {@code --te-navigation-bg}，浅 104 {@code rgba(255,255,255,0.94)}；深 590。 */
	public final int navigationBg;
	/** {@code --te-navigation-border}，浅 105；深 591（transparent）。 */
	public final int navigationBorder;
	/** {@code --te-navigation-shadow} 原文：浅 106；深 592（{@code none}）。 */
	public final String navigationShadowCss;
	/** {@code --te-navigation-text}，浅 107；深 593。 */
	public final int navigationText;
	/** {@code --te-navigation-icon}，浅 108；深 594。 */
	public final int navigationIcon;
	/** {@code --te-navigation-hover}，浅 109；深 595。 */
	public final int navigationHover;
	/** {@code --te-navigation-hover-text}，浅 110；深 596。 */
	public final int navigationHoverText;
	/** {@code --te-navigation-active}，浅 111；深 597。 */
	public final int navigationActive;
	/** {@code --te-navigation-active-text}，浅 112 {@code #2563eb}；深 598。 */
	public final int navigationActiveText;
	/** {@code --te-navigation-indicator}，浅 113 {@code #2563eb}；深 599。 */
	public final int navigationIndicator;

	// ---- 卡片 / 表面对比 ----

	/** {@code --te-card-bg}，浅 162；深 559 {@code #181818}。 */
	public final int cardBg;
	/** {@code --te-card-border}，浅 163；深 560。 */
	public final int cardBorder;
	/** {@code --te-subtle-bg}，浅 164；深 561。 */
	public final int subtleBg;
	/** {@code --te-hover-bg}，浅 165；深 562。 */
	public final int hoverBg;
	/** {@code --te-active-bg}，浅 166；深 563。 */
	public final int activeBg;
	/** {@code --te-shell-control-text}，浅 167；深 564。 */
	public final int shellControlText;
	/** {@code --te-shell-control-hover}，浅 168；深 577。 */
	public final int shellControlHover;

	// ---- 曲库 / 表格 ----

	/**
	 * {@code --te-library-bg} 渐变原文。浅 116 / 深 600 —— 深色块重复了浅色值，
	 * 所以深色下曲库表面仍是白色渐变（上游疑似 bug，按原样保留）。
	 */
	public final String libraryBgCss;
	/** {@code --te-library-table-bg}，浅 117 / 深 601（同浅色）。 */
	public final int libraryTableBg;
	/** {@code --te-library-table-border}，浅 118 / 深 602（同浅色）。 */
	public final int libraryTableBorder;
	/** {@code --te-library-table-shadow} 原文，浅 119 / 深 603（同浅色）。 */
	public final String libraryTableShadowCss;
	/** {@code --te-library-row-text}，浅 120；深 604 {@code #d8d8d8}。 */
	public final int libraryRowText;
	/** {@code --te-library-row-hover}，浅 121；深 605。 */
	public final int libraryRowHover;
	/** {@code --te-library-selection-bg}，浅 122；深 606。 */
	public final int librarySelectionBg;
	/** {@code --te-library-selection-hover}，浅 123；深 607。 */
	public final int librarySelectionHover;
	/** {@code --te-library-selection-indicator}，浅 124；深 608。 */
	public final int librarySelectionIndicator;
	/** {@code --te-library-icon}，行 125（深色块未覆盖）。 */
	public final int libraryIcon;
	/** {@code --te-library-action-bg}，行 131（深色块未覆盖）。 */
	public final int libraryActionBg;

	// ---- 播放条 ----

	/** {@code --te-player-progress-track}，行 140（深色块未覆盖）。 */
	public final int playerProgressTrack;
	/** {@code --te-player-progress-fill} 渐变原文，行 141。 */
	public final String playerProgressFillCss;
	/** {@code --te-player-time-surface}，行 145（深色块未覆盖）。 */
	public final int playerTimeSurface;
	/** {@code --te-equalizer-button-bg}，行 158（深色块未覆盖）。 */
	public final int equalizerButtonBg;
	/** {@code --te-equalizer-spectrum}，行 157（深色块未覆盖）。 */
	public final int equalizerSpectrum;

	// ---- 语义软色 ----

	/** {@code --te-success-soft-bg}，浅 202；深 609。 */
	public final int successSoftBg;
	/** {@code --te-success-soft-fg}，浅 203；深 610。 */
	public final int successSoftFg;
	/** {@code --te-info-soft-bg}，浅 204；深 611。 */
	public final int infoSoftBg;
	/** {@code --te-info-soft-fg}，浅 205；深 612。 */
	public final int infoSoftFg;
	/** {@code --te-warning-soft-bg}，浅 206；深 613。 */
	public final int warningSoftBg;
	/** {@code --te-warning-soft-fg}，浅 207；深 614。 */
	public final int warningSoftFg;
	/** 危险色底：{@code --te-danger-soft-bg}，浅 208；深 615。 */
	public final int dangerSoftBg;
	/** 危险色前景：{@code --te-danger-soft-fg}，浅 209；深 616。 */
	public final int dangerSoftFg;

	// ---- 滚动条 / 返回按钮 / 回顶按钮 ----

	/** {@code --te-scrollbar-thumb}，浅 210；深 565。 */
	public final int scrollbarThumb;
	/** {@code --te-scrollbar-thumb-hover}，浅 211；深 566。 */
	public final int scrollbarThumbHover;
	/** {@code --te-back-button-bg}，浅 212；深 567。 */
	public final int backButtonBg;
	/** {@code --te-back-button-bg-hover}，浅 213；深 568。 */
	public final int backButtonBgHover;
	/** {@code --te-back-button-border}，浅 214；深 569。 */
	public final int backButtonBorder;
	/** {@code --te-back-button-color}，浅 215；深 570。 */
	public final int backButtonColor;
	/** {@code --te-back-button-shadow} 原文：浅 216；深 571。 */
	public final String backButtonShadowCss;
	/** {@code --te-scroll-top-bg}，浅 217；深 572。 */
	public final int scrollTopBg;
	/** {@code --te-scroll-top-bg-hover}，浅 218；深 573。 */
	public final int scrollTopBgHover;
	/** {@code --te-scroll-top-border}，浅 219；深 574。 */
	public final int scrollTopBorder;
	/** {@code --te-scroll-top-color}，浅 220；深 575。 */
	public final int scrollTopColor;
	/** {@code --te-scroll-top-shadow} 原文：浅 221；深 576。 */
	public final String scrollTopShadowCss;

	// ---- 设置页 ----

	/** {@code --te-settings-text}，浅 92；深 578。 */
	public final int settingsText;
	/** {@code --te-settings-control-bg}，浅 94；深 580。 */
	public final int settingsControlBg;
	/** {@code --te-settings-control-border}，浅 95；深 581。 */
	public final int settingsControlBorder;
	/** {@code --te-settings-panel-border}，浅 96 / 深 582（都是 transparent）。 */
	public final int settingsPanelBorder;
	/** {@code --te-settings-nav-text}，浅 97；深 583。 */
	public final int settingsNavText;
	/** {@code --te-settings-nav-hover}，浅 98；深 584。 */
	public final int settingsNavHover;
	/** {@code --te-settings-nav-active}，浅 99；深 585。 */
	public final int settingsNavActive;
	/** {@code --te-settings-row-bg}，浅 100；深 586。 */
	public final int settingsRowBg;
	/** {@code --te-settings-search-bg}，浅 101；深 587。 */
	public final int settingsSearchBg;
	/** {@code --te-settings-shadow} 原文：浅 102；深 588。 */
	public final String settingsShadowCss;
	/** {@code --te-settings-shadow-soft} 原文：浅 103；深 589。 */
	public final String settingsShadowSoftCss;

	// ------------------------------------------------------------------
	// 派生取值
	// ------------------------------------------------------------------

	/** 强调色＝{@code --te-primary-500}。 */
	public int accent()
	{
		return primary500;
	}

	/** 正文色＝{@code --color-text} → {@code --te-neutral-900}。 */
	public int bodyText()
	{
		return text;
	}

	/** 次要文字色：base.css 没有专门令牌，用 {@code --te-neutral-500}。 */
	public int mutedText()
	{
		return textMuted;
	}

	/**
	 * 禁用文字色：base.css 行 370–376 的禁用态是
	 * {@code opacity: 0.52}，不是独立颜色，所以这里是正文色 × 0.52。
	 */
	public int disabledText()
	{
		return withAlpha(text, DISABLED_OPACITY);
	}

	/**
	 * 分隔线 / 描边色：paper-light 的 {@code --sf-shell-line}，
	 * {@code color-mix(in srgb, neutral-900 13%, transparent)}
	 * （paper-light.css 行 2）。
	 */
	public int shellLine()
	{
		return withAlpha(neutral900, SHELL_LINE_ALPHA);
	}

	/**
	 * paper-light 第二层的侧栏底色：
	 * {@code color-mix(in srgb, var(--te-navigation-bg) 82%, var(--te-primary-500))}
	 * （paper-light.css 行 121）。
	 *
	 * <p>注意：{@link #mix(int, int, float)} 是<b>非预乘</b>的按通道线性混合，
	 * 而浏览器 {@code color-mix()} 在 srgb 下先预乘 alpha 再混、最后还原。
	 * {@code navigationBg} 自带 alpha 0.94，所以这里的结果与浏览器可能差
	 * 1–3/255（例如本方法给 {@code 0xF3D8E3FB}，预乘应为 {@code 0xF3D6E3FB}）。
	 * 肉眼看不出，但做像素级比对时要按预乘重算。</p>
	 */
	public int shellSidebarBg()
	{
		return mix(navigationBg, primary500, 1F - SHELL_SIDEBAR_BG_NAV_WEIGHT);
	}

	/**
	 * 侧栏投影色：{@code color-mix(in srgb, neutral-50 32%, transparent)}，
	 * paper-light.css 行 122。
	 */
	public int shellSidebarShadowColor()
	{
		return withAlpha(neutral50, 0.32F);
	}

	/** 深色 {@code body::before} 里 {@code rgba(var(--te-primary-rgb), 0.1)}，行 822。 */
	public int ambientDarkPrimaryWash()
	{
		return withAlpha(primary500, 0.1F);
	}

	// ------------------------------------------------------------------
	// 颜色工具（与 gui.visual.VisualTheme 同语义，避免跨包依赖）
	// ------------------------------------------------------------------

	/** 按通道线性混合两个 ARGB 颜色。 */
	public static int mix(int from, int to, float progress)
	{
		float amount = clamp(progress, 0F, 1F);
		return mixChannel(from >>> 24, to >>> 24, amount) << 24
			| mixChannel(from >> 16 & 0xFF, to >> 16 & 0xFF, amount) << 16
			| mixChannel(from >> 8 & 0xFF, to >> 8 & 0xFF, amount) << 8
			| mixChannel(from & 0xFF, to & 0xFF, amount);
	}

	/** 0..1 透明度写入 ARGB。 */
	public static int withAlpha(int color, float alpha)
	{
		return withAlpha(color, Math.round(clamp(alpha, 0F, 1F) * 255F));
	}

	/** 0..255 透明度写入 ARGB。 */
	public static int withAlpha(int color, int alpha)
	{
		return Math.max(0, Math.min(255, alpha)) << 24 | color & 0xFFFFFF;
	}

	private static int mixChannel(int from, int to, float progress)
	{
		return Math.round(from + (to - from) * progress);
	}

	private static float clamp(float value, float min, float max)
	{
		return Math.max(min, Math.min(max, value));
	}
}
