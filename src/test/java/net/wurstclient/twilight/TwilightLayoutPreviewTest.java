package net.wurstclient.twilight;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;

import javax.imageio.ImageIO;

import org.junit.jupiter.api.Test;

import net.wurstclient.twilight.TwilightHomeLayout.Home;
import net.wurstclient.twilight.TwilightShellLayout.Frame;
import net.wurstclient.twilight.TwilightShellLayout.Rect;

/**
 * 把主页布局画成 PNG，供人工对照参考图。
 *
 * <p>
 * 布局类本身不含 Minecraft 类型，所以可以直接在测试里出图。这不是断言性质的
 * 测试，价值在于「改布局的人能立刻看到结构」——否则每次都要启动游戏、截图、
 * 再由别人描述，一轮就是好几分钟，几十轮下来反而更慢。
 *
 * <p>
 * 输出的方块标注的是字段名而不是界面文案：这里要比对的是结构与比例。
 */
final class TwilightLayoutPreviewTest
{
	private static final File OUTPUT =
		new File("build/layout-preview/home.png");

	private static final Color INK = new Color(0x1A1D24);
	private static final Color CARD = new Color(0x33FFFFFF, true);
	private static final Color CARD_LINE = new Color(0x559CA3AF, true);
	private static final Color HERO = new Color(0x22007CFF, true);
	private static final Color HERO_LINE = new Color(0x88007CFF, true);
	private static final Color ACCENT = new Color(0xCC007CFF, true);

	@Test
	void rendersTheHomeLayout() throws IOException
	{
		int width = TwilightShellLayout.DESIGN_WIDTH;
		int height = TwilightShellLayout.DESIGN_HEIGHT;

		Frame frame = TwilightShellLayout.layout(width, height, false);
		Home home = TwilightHomeLayout.layout(frame);

		BufferedImage image =
			new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
		Graphics2D g = image.createGraphics();
		g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
			RenderingHints.VALUE_ANTIALIAS_ON);
		g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,
			RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

		// 参考图的页面底色
		g.setColor(new Color(0xFFF5F6F8));
		g.fillRect(0, 0, width, height);

		// 侧栏与播放条
		box(g, frame.sidebar, "sidebar", new Color(0x14FFFFFF, true), CARD_LINE);
		box(g, frame.contentHeader, "contentHeader",
			new Color(0x0A000000, true), CARD_LINE);
		box(g, frame.playerBar, "playerBar", new Color(0x1AFFFFFF, true),
			CARD_LINE);

		// hero 三块
		box(g, home.hero, "hero", HERO, HERO_LINE);
		box(g, home.heroCopy, "heroCopy", new Color(0x14007CFF, true),
			HERO_LINE);
		box(g, home.heroStage, "heroStage", new Color(0x0A007CFF, true),
			HERO_LINE);
		box(g, home.dayBadge, "dayBadge", ACCENT, ACCENT);
		box(g, home.primaryCta, "primaryCta", ACCENT, ACCENT);
		box(g, home.secondaryCta, "secondaryCta", new Color(0x22000000, true),
			CARD_LINE);

		// hero 右侧漂浮的封面
		int index = 0;
		for(Rect cover : home.collage(frame))
			box(g, cover, "collage" + index++, new Color(0x33007CFF, true),
				ACCENT);

		// 两张卡
		box(g, home.duoCardLeft, "duoCardLeft", CARD, CARD_LINE);
		box(g, home.duoCardRight, "duoCardRight", CARD, CARD_LINE);

		// 侧栏：品牌按钮 + 导航项 + 底部账户区，这才是侧栏真正的排版
		int brand = 0;
		for(;; brand++)
		{
			Rect button = frame.brandButton(brand);

			if(button == null || button.right() > frame.sidebar.right())
				break;

			box(g, button, brand == 0 ? "brand" : "", new Color(0x22FFFFFF, true),
				CARD_LINE);
		}

		int nav = 0;
		for(;; nav++)
		{
			Rect item = frame.navItem(nav);

			if(item == null)
				break;

			box(g, item, nav == 0 ? "navItem 45px" : "",
				new Color(0x1A000000, true), CARD_LINE);
			Rect indicator = frame.navIndicator(nav);

			if(indicator != null)
				box(g, indicator, "", ACCENT, ACCENT);
		}

		// 播放条：播放键、传输键、进度条
		box(g, frame.playButton(), "play", ACCENT, ACCENT);
		box(g, frame.transportButton(-1), "", new Color(0x33000000, true),
			CARD_LINE);
		box(g, frame.transportButton(1), "", new Color(0x33000000, true),
			CARD_LINE);
		box(g, frame.progressBar(13), "progressBar", new Color(0x44000000, true),
			CARD_LINE);

		// 区块头
		box(g, home.sectionHead, "sectionHead", new Color(0x0A000000, true),
			CARD_LINE);
		box(g, home.sectionMore, "sectionMore", new Color(0x22000000, true),
			CARD_LINE);

		// 两列歌单：序号 / 封面 / 文字，看的是一行的内部排版
		Rect chart = new Rect(frame.contentBody.x(),
			home.sectionHead.bottom(),
			frame.contentBody.width(),
			Math.max(0, frame.contentBody.bottom()
				- home.sectionHead.bottom()));
		int row = 0;
		for(Rect r : TwilightListLayout.chartRows(frame, chart))
		{
			box(g, r, row == 0 ? "chart row" : "",
				new Color(0x08FFFFFF, true), new Color(0x339CA3AF, true));
			box(g, TwilightListLayout.rowCover(frame, r, true), "", ACCENT,
				ACCENT);
			box(g, TwilightListLayout.rowIndex(frame, r), "", CARD_LINE,
				CARD_LINE);
			box(g, TwilightListLayout.rowTitle(frame, r, true), "", CARD_LINE,
				CARD_LINE);
			box(g, TwilightListLayout.rowDuration(frame, r), "", CARD_LINE,
				CARD_LINE);
			row++;
		}

		// 两张卡的叠放封面与箭头
		for(Rect cover : TwilightHomeLayout.duoCovers(frame, home.duoCardLeft))
			box(g, cover, "", new Color(0x22007CFF, true), ACCENT);
		for(Rect cover : TwilightHomeLayout.duoCovers(frame, home.duoCardRight))
			box(g, cover, "", new Color(0x22007CFF, true), ACCENT);
		box(g, TwilightHomeLayout.duoArrow(frame, home.duoCardLeft), "",
			new Color(0x33000000, true), CARD_LINE);
		box(g, TwilightHomeLayout.duoArrow(frame, home.duoCardRight), "",
			new Color(0x33000000, true), CARD_LINE);

		OUTPUT.getParentFile().mkdirs();
		ImageIO.write(image, "png", OUTPUT);
		System.out.println("布局预览已写出: " + OUTPUT.getAbsolutePath());
	}

	private void box(Graphics2D g, Rect rect, String label, Color fill,
		Color line)
	{
		g.setColor(fill);
		g.fillRoundRect(rect.x(), rect.y(), rect.width(), rect.height(), 10, 10);
		g.setColor(line);
		g.setStroke(new BasicStroke(1F));
		g.drawRoundRect(rect.x(), rect.y(), rect.width(), rect.height(), 10, 10);

		if(label == null || label.isEmpty())
			return;

		g.setColor(INK);
		g.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 12));
		g.drawString(label + " " + rect.width() + "x" + rect.height(),
			rect.x() + 6, rect.y() + 15);
	}
}
