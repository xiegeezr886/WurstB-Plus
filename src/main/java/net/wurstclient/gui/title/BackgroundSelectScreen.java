/*
 * Copyright (c) 2014-2025 Wurst-Imperium and contributors.
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 */
package net.wurstclient.gui.title;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.wurstclient.WurstClient;
import net.wurstclient.background.BackgroundEntry;
import net.wurstclient.background.BackgroundFilePicker;
import net.wurstclient.background.BackgroundKind;
import net.wurstclient.background.BackgroundManager;
import net.wurstclient.background.BackgroundMotion;
import net.wurstclient.background.BackgroundStorage;
import net.wurstclient.background.BackgroundThumbnail;
import net.wurstclient.background.BackgroundVideo;
import net.wurstclient.background.SteamLocator;
import net.wurstclient.background.WallpaperEngineImporter;
import net.wurstclient.clickgui2.FlatRenderer;
import net.wurstclient.gui.visual.VisualRenderer;
import net.wurstclient.gui.visual.VisualTheme;
import net.wurstclient.twilight.TwilightCoverFit;
import net.wurstclient.util.render.AsyncTextureLoader;

/**
 * The "Select Background" modal.
 *
 * <p>
 * A child screen rather than a layer inside the title menu: it gets vanilla
 * input, resizing and closing for free, and it is not affected by the title
 * mixin cancelling the vanilla render. The wallpaper keeps being drawn behind
 * it, because {@link BackgroundManager} is client wide.
 */
public final class BackgroundSelectScreen extends Screen
{
	private static final int CARD_WIDTH = 148;
	private static final int CARD_HEIGHT = 104;
	private static final int GAP = 12;
	private static final int PADDING = 20;
	private static final int HEADER_HEIGHT = 44;
	private static final int SCROLL_STEP = 36;

	private static final int PANEL = 0xF01B1E24;
	private static final int CARD_BG = 0xFF23272F;
	private static final int CARD_HOVER = 0xFF2C313A;
	private static final int TEXT = VisualTheme.TEXT;
	private static final int MUTED = VisualTheme.TEXT_MUTED;
	private static final int ACCENT = VisualTheme.ACCENT;

	/** 圆角半径：面板、卡片、顶部按钮各一档（参考图就是这个观感）。 */
	private static final int PANEL_RADIUS = 12;
	private static final int CARD_RADIUS = 6;
	private static final int CHIP_RADIUS = 8;

	/** 缩略图往卡片内侧缩这么多，方角就落在圆角卡片里面。 */
	private static final int PREVIEW_INSET = 3;

	/** 卡片底部标题条的高度（参考图里 "Default" 那一条）；两行字要放得下。 */
	private static final int CAPTION_HEIGHT = 28;
	private static final int CAPTION_FILL = 0xCC12151A;

	/** 面板描边，比底色亮一点点。 */
	private static final int PANEL_BORDER = 0x33FFFFFF;
	private static final int CARD_BORDER = 0x22FFFFFF;
	private static final int CARD_BORDER_HOVER = 0x66FFFFFF;

	private final Screen parent;
	private final Map<String, ResourceLocation> thumbnails = new HashMap<>();
	private final Set<String> requested = new HashSet<>();

	/** 视频能不能放：探测一次就缓存下来，画卡片不会每帧去读文件。 */
	private final Map<String, BackgroundVideo.Probe> videoProbes = new HashMap<>();
	private final Set<String> videoProbesRequested = new HashSet<>();

	private List<BackgroundEntry> entries = List.of();
	private int scroll;
	private String status = "";
	private boolean busy;

	public BackgroundSelectScreen(Screen parent)
	{
		super(Component.translatable("wurst.background.title"));
		this.parent = parent;
	}

	/**
	 * 文案按当前语言解析。这个界面是模态的、每帧画的字符串也就十来个，所以不
	 * 缓存——语言在设置里一改就该立刻生效。
	 */
	private static String tr(String key, Object... args)
	{
		return Component.translatable(key, args).getString();
	}

	@Override
	protected void init()
	{
		entries = BackgroundManager.get().entries();
	}

	// ------------------------------------------------------------------
	// 绘制
	// ------------------------------------------------------------------

	@Override
	public void render(GuiGraphics graphics, int mouseX, int mouseY,
		float partialTick)
	{
		super.render(graphics, mouseX, mouseY, partialTick);

		// the wallpaper itself, so switching is visible immediately. Drawn
		// here rather than in renderBackground because the motion follows the
		// pointer, and only this method gets it.
		if(!BackgroundManager.get().render(graphics, width, height, mouseX,
			mouseY))
			VisualRenderer.gridBackground(graphics, width, height);

		graphics.fill(0, 0, width, height, 0xB4000000);

		Rect panel = panel();
		FlatRenderer.fillRoundedRect(graphics, panel.x, panel.y, panel.right(),
			panel.bottom(), PANEL_RADIUS, PANEL);
		FlatRenderer.drawRoundedOutline(graphics, panel.x, panel.y,
			panel.right(), panel.bottom(), PANEL_RADIUS, PANEL_BORDER);

		drawHeader(graphics, panel, mouseX, mouseY);
		drawGrid(graphics, panel, mouseX, mouseY);

		if(!status.isEmpty())
			graphics.drawString(font, status, panel.x + PADDING,
				panel.bottom() - 18, MUTED, false);
	}

	private void drawHeader(GuiGraphics graphics, Rect panel, int mouseX,
		int mouseY)
	{
		graphics.drawString(font, tr("wurst.background.title"),
			panel.x + PADDING, panel.y + 16, TEXT, false);

		// close
		Rect close = closeButton(panel);
		boolean closeHovered = close.contains(mouseX, mouseY);
		graphics.drawString(font, "✕", close.x + 5, close.y + 4,
			closeHovered ? TEXT : MUTED, false);

		// scan Steam library
		Rect scan = scanButton(panel);
		boolean scanHovered = scan.contains(mouseX, mouseY);
		FlatRenderer.fillRoundedRect(graphics, scan.x, scan.y, scan.right(),
			scan.bottom(), CHIP_RADIUS, scanHovered ? CARD_HOVER : CARD_BG);
		graphics.drawString(font,
			busy ? tr("wurst.background.scanning")
				: tr("wurst.background.scan_steam"),
			scan.x + 10, scan.y + 6, scanHovered ? TEXT : MUTED, false);

		// motion picker: cycles, which is enough for four options
		Rect motion = motionButton(panel);
		boolean motionHovered = motion.contains(mouseX, mouseY);
		FlatRenderer.fillRoundedRect(graphics, motion.x, motion.y, motion.right(),
			motion.bottom(), CHIP_RADIUS, motionHovered ? CARD_HOVER : CARD_BG);
		String label = tr("wurst.background.motion",
			BackgroundManager.get().motion());
		graphics.drawString(font, label, motion.x + 10, motion.y + 6,
			motionHovered ? TEXT : MUTED, false);
	}

	private void drawGrid(GuiGraphics graphics, Rect panel, int mouseX,
		int mouseY)
	{
		int columns = columns(panel);
		int contentTop = panel.y + HEADER_HEIGHT;
		int contentHeight = panel.bottom() - contentTop - 26;

		int cards = entries.size() + 2; // default + the import card
		int rows = (cards + columns - 1) / columns;
		int contentTotal = rows * (CARD_HEIGHT + GAP) - GAP + PADDING * 2;
		int maxScroll = Math.max(0, contentTotal - contentHeight);

		scroll = Math.max(0, Math.min(scroll, maxScroll));

		for(int index = 0; index < cards; index++)
		{
			int column = index % columns;
			int row = index / columns;
			int x = panel.x + PADDING + column * (CARD_WIDTH + GAP);
			int y = contentTop + PADDING + row * (CARD_HEIGHT + GAP) - scroll;

			if(y + CARD_HEIGHT < contentTop || y > panel.bottom() - 26)
				continue;

			Rect card = new Rect(x, y, CARD_WIDTH, CARD_HEIGHT);

			if(index == 0)
				drawDefaultCard(graphics, card, mouseX, mouseY);
			else if(index == cards - 1)
				drawImportCard(graphics, card, mouseX, mouseY);
			else
				drawEntryCard(graphics, card, entries.get(index - 1), mouseX,
					mouseY);
		}

		if(maxScroll > 0)
		{
			int trackHeight = contentHeight - PADDING * 2;
			int thumbHeight = Math.max(24,
				trackHeight * contentHeight / Math.max(1, contentTotal));
			int thumbY = contentTop + PADDING
				+ (trackHeight - thumbHeight) * scroll / maxScroll;
			graphics.fill(panel.right() - 6, contentTop + PADDING,
				panel.right() - 3, contentTop + PADDING + trackHeight,
				0x22FFFFFF);
			graphics.fill(panel.right() - 6, thumbY, panel.right() - 3,
				thumbY + thumbHeight, 0x66FFFFFF);
		}
	}

	private void drawDefaultCard(GuiGraphics graphics, Rect card, int mouseX,
		int mouseY)
	{
		boolean selected = BackgroundManager.get().isDefaultSelected();
		cardBackground(graphics, card, mouseX, mouseY, selected);
		// a miniature of the built-in grid, so the card is not blank
		int gridTop = card.y + PREVIEW_INSET;
		int gridBottom = card.bottom() - CAPTION_HEIGHT;
		for(int x = card.x + 12; x < card.right() - 12; x += 12)
			graphics.fill(x, gridTop, x + 1, gridBottom, 0x22FFFFFF);
		for(int y = gridTop + 4; y < gridBottom; y += 12)
			graphics.fill(card.x + 12, y, card.right() - 12, y + 1, 0x22FFFFFF);
		drawCaption(graphics, card);
		drawCardLabel(graphics, card, tr("wurst.background.default"),
			tr("wurst.background.builtin"));
	}

	private void drawImportCard(GuiGraphics graphics, Rect card, int mouseX,
		int mouseY)
	{
		boolean hovered = card.contains(mouseX, mouseY);
		FlatRenderer.fillRoundedRect(graphics, card.x, card.y, card.right(),
			card.bottom(), CARD_RADIUS, hovered ? CARD_HOVER : 0x331B1E24);
		outline(graphics, card, hovered ? ACCENT : 0x44FFFFFF);
		int centerX = card.centerX();
		int centerY = card.y + (CARD_HEIGHT - CAPTION_HEIGHT) / 2;
		graphics.fill(centerX - 10, centerY - 1, centerX + 10, centerY + 1,
			hovered ? ACCENT : MUTED);
		graphics.fill(centerX - 1, centerY - 10, centerX + 1, centerY + 10,
			hovered ? ACCENT : MUTED);
		drawCaption(graphics, card);
		drawCardLabel(graphics, card, tr("wurst.background.import"), "");
	}

	private void drawEntryCard(GuiGraphics graphics, Rect card,
		BackgroundEntry entry, int mouseX, int mouseY)
	{
		boolean selected = entry.id().equals(BackgroundManager.get().selectedId());
		cardBackground(graphics, card, mouseX, mouseY, selected);

		ResourceLocation thumbnail = thumbnail(entry);
		int previewBottom = card.bottom() - CAPTION_HEIGHT;

		if(thumbnail != null)
			drawPreview(graphics, thumbnail, card.x + PREVIEW_INSET,
				card.y + PREVIEW_INSET, card.width - PREVIEW_INSET * 2,
				previewBottom - card.y - PREVIEW_INSET);
		else
			graphics.fill(card.x + PREVIEW_INSET, card.y + PREVIEW_INSET,
				card.right() - PREVIEW_INSET, previewBottom, 0x22FFFFFF);

		if(entry.kind() == BackgroundKind.VIDEO)
			// 视频的徽章跟着探测结果走：没探完是「检测中」，能放就只写「视频」，
			// 放不了才写「不能播放」
			drawBadge(graphics, card, videoBadge(entry));
		else if(entry.kind() == BackgroundKind.GIF)
			drawBadge(graphics, card, "GIF");

		drawCaption(graphics, card);
		drawCardLabel(graphics, card, entry.title(), entry.origin());
	}

	/**
	 * 卡片预览：按 <b>cover</b> 裁切，不拉伸。
	 *
	 * <p>
	 * 缩略图是保持原图宽高比的（{@link BackgroundThumbnail} 只按最长边缩放），而卡片
	 * 的预览区是 140×74。之前直接把整张缩略图铺满这个框，正方形的预览图会被横向拉宽
	 * 近两倍——工坊场景的缩略图正好是作者那张 250×250 的 {@code preview.gif}，拉得最
	 * 明显（实机一眼就能看出来）。这里改成从缩略图里裁一块与预览框同比例的，和壁纸
	 * 自身那条路（{@link TwilightCoverFit}）保持一致。</p>
	 */
	private void drawPreview(GuiGraphics graphics, ResourceLocation thumbnail,
		int x, int y, int width, int height)
	{
		int textureWidth = 256;
		int textureHeight = 256;

		if(minecraft != null && minecraft.getTextureManager()
			.getTexture(thumbnail) instanceof DynamicTexture texture
			&& texture.getPixels() != null)
		{
			textureWidth = texture.getPixels().getWidth();
			textureHeight = texture.getPixels().getHeight();
		}

		int[] crop =
			TwilightCoverFit.sourceRect(textureWidth, textureHeight, width, height);

		if(crop[2] <= 0 || crop[3] <= 0)
			return;

		graphics.blit(thumbnail, x, y, width, height, crop[0], crop[1], crop[2],
			crop[3], textureWidth, textureHeight);
	}

	private void drawBadge(GuiGraphics graphics, Rect card, String text)
	{
		int width = font.width(text) + 8;
		int x = card.right() - width - 5;
		graphics.fill(x, card.y + 5, x + width, card.y + 17, 0xAA000000);
		graphics.drawString(font, text, x + 4, card.y + 8, 0xFFFFFFFF, false);
	}

	private void drawCardLabel(GuiGraphics graphics, Rect card, String title,
		String subtitle)
	{
		// 两行都压在标题条里：一行标题、一行来路（参考图只有一行，这里信息多一点）
		graphics.drawString(font, trim(title, card.width - 14), card.x + 7,
			card.bottom() - 20, TEXT, false);
		graphics.drawString(font, trim(subtitle, card.width - 14), card.x + 7,
			card.bottom() - 11, MUTED, false);
	}

	private void cardBackground(GuiGraphics graphics, Rect card, int mouseX,
		int mouseY, boolean selected)
	{
		boolean hovered = card.contains(mouseX, mouseY);
		FlatRenderer.fillRoundedRect(graphics, card.x, card.y, card.right(),
			card.bottom(), CARD_RADIUS, hovered ? CARD_HOVER : CARD_BG);

		if(selected)
			FlatRenderer.drawRoundedOutline(graphics, card.x, card.y,
				card.right(), card.bottom(), CARD_RADIUS, ACCENT);
		else
			FlatRenderer.drawRoundedOutline(graphics, card.x, card.y,
				card.right(), card.bottom(), CARD_RADIUS,
				hovered ? CARD_BORDER_HOVER : CARD_BORDER);
	}

	/** 卡片底部的标题条：参考图里 "Default" 就是压在这样一条深色带上。 */
	private void drawCaption(GuiGraphics graphics, Rect card)
	{
		int top = card.bottom() - CAPTION_HEIGHT;
		FlatRenderer.fillRoundedRect(graphics, card.x + 1, top, card.right() - 1,
			card.bottom() - 1, CARD_RADIUS - 1, CAPTION_FILL);
		// the top corners of the strip are square, so the seam does not curve
		graphics.fill(card.x + 1, top, card.right() - 1, top + CARD_RADIUS - 1,
			CAPTION_FILL);
	}

	private void outline(GuiGraphics graphics, Rect card, int color)
	{
		FlatRenderer.drawRoundedOutline(graphics, card.x, card.y, card.right(),
			card.bottom(), CARD_RADIUS, color);
	}

	// ------------------------------------------------------------------
	// 输入
	// ------------------------------------------------------------------

	@Override
	public boolean mouseClicked(double mouseX, double mouseY, int button)
	{
		Rect panel = panel();

		if(closeButton(panel).contains(mouseX, mouseY))
		{
			onClose();
			return true;
		}

		if(scanButton(panel).contains(mouseX, mouseY) && !busy)
		{
			scanSteamLibrary();
			return true;
		}

		if(motionButton(panel).contains(mouseX, mouseY))
		{
			cycleMotion();
			return true;
		}

		int index = cardAt(panel, mouseX, mouseY);

		if(index < 0)
			return true;

		int last = entries.size() + 1;

		if(index == 0)
			BackgroundManager.get().select(BackgroundStorage.DEFAULT_ID);
		else if(index == last)
			importFromFile();
		else
		{
			BackgroundEntry entry = entries.get(index - 1);

			if(button == 1)
				delete(entry);
			else if(!entry.kind().canPlay())
				// 今天四种类型在原理上都能放，这道闸门留给以后新增的类型
				status = tr("wurst.background.kind_unsupported");
			else if(entry.kind() == BackgroundKind.VIDEO)
				clickVideo(entry);
			else
				BackgroundManager.get().select(entry.id());
		}

		return true;
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double delta)
	{
		scroll -= (int)Math.signum(delta) * SCROLL_STEP;
		return true;
	}

	@Override
	public boolean keyPressed(int keyCode, int scanCode, int modifiers)
	{
		if(keyCode == 256)
		{
			onClose();
			return true;
		}

		return super.keyPressed(keyCode, scanCode, modifiers);
	}

	@Override
	public void onClose()
	{
		if(parent != null)
			minecraft.setScreen(parent);
		else
			super.onClose();
	}

	@Override
	public void removed()
	{
		for(ResourceLocation location : thumbnails.values())
			minecraft.getTextureManager().release(location);

		thumbnails.clear();
		requested.clear();
		videoProbes.clear();
		videoProbesRequested.clear();
	}

	// ------------------------------------------------------------------
	// 视频能不能放
	// ------------------------------------------------------------------

	/**
	 * 视频卡片的徽章。
	 *
	 * <p>
	 * 「不能播放」现在是探出来的结论而不是预设：探测（
	 * {@link BackgroundVideo#probeAsync}）在后台线程读文件头并解一帧，结果按条目 id
	 * 留在本界面里，所以画卡片不会每帧去读磁盘。
	 * </p>
	 */
	private String videoBadge(BackgroundEntry entry)
	{
		BackgroundVideo.Probe probe = videoProbe(entry);

		if(probe == null)
			return tr("wurst.background.video_checking");

		return probe.playable() ? tr("wurst.background.video_playable")
			: tr("wurst.background.video_badge");
	}

	/**
	 * @return 探测结果，还没探完返回 null；同一个条目只会发起一次探测
	 */
	private BackgroundVideo.Probe videoProbe(BackgroundEntry entry)
	{
		BackgroundVideo.Probe cached = videoProbes.get(entry.id());

		if(cached != null)
			return cached;

		if(!videoProbesRequested.add(entry.id()))
			return null;

		Path media = BackgroundManager.get().storage().mediaPath(entry.id());

		BackgroundVideo.probeAsync(media).whenComplete((probe, error) -> {
			// 探测本身不抛异常，这里只是兜底：拿不到结果就按"文件不在"处理
			BackgroundVideo.Probe result =
				probe != null ? probe : BackgroundVideo.probe(null);

			minecraft.execute(() -> {
				videoProbes.put(entry.id(), result);

				if(!result.playable())
					System.out.println("[Background] 视频 " + entry.id()
						+ " 不能播放：" + result.reason() + " / "
						+ result.detail());
			});
		});

		return null;
	}

	/**
	 * 点视频卡片：探出来能放才允许选中。
	 *
	 * <p>
	 * 探测没做完时先不选——选中一个放不出来的视频，管理器会退回内置背景，看起来
	 * 就是"点了没反应"。宁可让用户再点一次，并且明确告诉他为什么。
	 * </p>
	 */
	private void clickVideo(BackgroundEntry entry)
	{
		BackgroundVideo.Probe probe = videoProbe(entry);

		if(probe == null)
		{
			status = tr("wurst.background.video_checking_status");
			return;
		}

		if(!probe.playable())
		{
			status =
				tr("wurst.background.video_unsupported", videoReason(probe));
			return;
		}

		BackgroundManager.get().select(entry.id());
	}

	/** 放不了的原因；文案在语言文件里，编码名从探测结果里取。 */
	private String videoReason(BackgroundVideo.Probe probe)
	{
		return switch(probe.reason())
		{
			case UNSUPPORTED_CODEC -> tr("wurst.background.video_codec",
				BackgroundVideo.describeFourcc(probe.detail()));
			case MISSING -> tr("wurst.background.video_missing");
			case EMPTY_FILE -> tr("wurst.background.video_empty");
			case NOT_MP4 -> tr("wurst.background.video_not_mp4");
			case NO_VIDEO_TRACK -> tr("wurst.background.video_no_track");
			case NO_FRAME, DECODE_FAILED, OK -> tr(
				"wurst.background.video_broken");
		};
	}

	// ------------------------------------------------------------------
	// 导入与删除
	// ------------------------------------------------------------------

	private void importFromFile()
	{
		BackgroundFilePicker.pickImage(path -> {
			if(path == null)
				return;

			BackgroundKind kind = BackgroundKind
				.fromFileName(path.getFileName().toString());

			if(kind == null)
			{
				status = tr("wurst.background.unsupported_format",
					path.getFileName());
				return;
			}

			WurstClient.INSTANCE.getGuiPreferences().setSelectedBackground(
				BackgroundStorage.DEFAULT_ID);
			BackgroundManager.get().forget();

			// decoding the thumbnail is slow, so it happens off the client
			// thread and the import follows it
			Thread worker = new Thread(() -> {
				byte[] thumbnail = kind == BackgroundKind.GIF
					? BackgroundThumbnail.createAnimated(path,
						BackgroundThumbnail.MAX_SIZE)
					: BackgroundThumbnail.create(path,
						BackgroundThumbnail.MAX_SIZE);
				String id = BackgroundManager.get().storage().importFile(path,
					kind, path.getFileName().toString(), path.getFileName()
						.toString(), thumbnail);

				minecraft.execute(() -> {
					if(id == null)
						status = tr("wurst.background.import_failed",
							path.getFileName());
					else
					{
						BackgroundManager.get().select(id);
						entries = BackgroundManager.get().entries();
						status = tr("wurst.background.imported",
							path.getFileName());
					}
				});
			}, "WurstB-BackgroundImport");
			worker.setDaemon(true);
			worker.start();
		});
	}

	/**
	 * 从候选自带的缩略图来源生成缩略图。场景包的 {@code thumbnail} 指向它的
	 * 预览图而不是包本身——解码器读不了 {@code .pkg}。
	 */
	private static byte[] createThumbnail(
		WallpaperEngineImporter.Candidate candidate)
	{
		Path source = candidate.thumbnail();

		if(source == null)
			return null;

		boolean animated = source.equals(candidate.media())
			&& candidate.kind() == BackgroundKind.GIF;

		return animated
			? BackgroundThumbnail.createAnimated(source,
				BackgroundThumbnail.MAX_SIZE)
			: BackgroundThumbnail.create(source,
				BackgroundThumbnail.MAX_SIZE);
	}

	private void scanSteamLibrary()
	{
		busy = true;
		status = tr("wurst.background.scan_started");

		Thread worker = new Thread(() -> {
			int imported = 0;
			int previews = 0;
			int skipped = 0;

			try
			{
				List<Path> folders = SteamLocator.wallpaperFolders();
				List<WallpaperEngineImporter.Candidate> candidates =
					WallpaperEngineImporter.scan(folders);

				for(WallpaperEngineImporter.Candidate candidate : candidates)
				{
					byte[] thumbnail = createThumbnail(candidate);

					String id = BackgroundManager.get().storage().importFile(
						candidate.media(), candidate.kind(), candidate.title(),
						"Wallpaper Engine", thumbnail);

					if(id == null)
						skipped++;
					else if(candidate.playable())
						imported++;
					else
						previews++;
				}
			}catch(RuntimeException e)
			{
				skipped++;
			}

			int importedCount = imported;
			int previewCount = previews;
			int skippedCount = skipped;

			minecraft.execute(() -> {
				busy = false;
				entries = BackgroundManager.get().entries();
				status = tr("wurst.background.scan_done", importedCount,
					previewCount, skippedCount);
			});
		}, "WurstB-WallpaperScan");

		worker.setDaemon(true);
		worker.start();
	}

	private void delete(BackgroundEntry entry)
	{
		if(BackgroundManager.get().storage().delete(entry.id()))
		{
			if(entry.id().equals(BackgroundManager.get().selectedId()))
				BackgroundManager.get().select(BackgroundStorage.DEFAULT_ID);

			BackgroundManager.get().forget();
			entries = BackgroundManager.get().entries();
			status = tr("wurst.background.deleted", entry.title());
		}else
			status = tr("wurst.background.delete_failed", entry.title());
	}

	private void cycleMotion()
	{
		BackgroundMotion[] values = BackgroundMotion.values();
		BackgroundMotion current = BackgroundManager.get().motion();
		WurstClient.INSTANCE.getGuiPreferences()
			.setBackgroundMotion(values[(current.ordinal() + 1) % values.length]);
	}

	// ------------------------------------------------------------------
	// 加载与几何
	// ------------------------------------------------------------------

	private ResourceLocation thumbnail(BackgroundEntry entry)
	{
		ResourceLocation cached = thumbnails.get(entry.id());

		if(cached != null)
			return cached;

		if(requested.contains(entry.id()))
			return null;

		Path file = BackgroundManager.get().storage().thumbnailPath(entry.id());

		if(!java.nio.file.Files.isRegularFile(file))
		{
			requested.add(entry.id());
			return null;
		}

		requested.add(entry.id());
		ResourceLocation location = new ResourceLocation(WurstClient.MOD_ID,
			thumbnailTextureName(entry.id()));

		AsyncTextureLoader.load(file, location).whenComplete((loaded, error) -> {
			if(error == null)
				thumbnails.put(entry.id(), loaded);
		});

		return null;
	}

	/**
	 * 缩略图贴图名（包级可见，便于单测）。
	 *
	 * <p>
	 * <b>不能直接把 {@code entry.id()} 拼进贴图名</b>：{@code ResourceLocation} 的路径
	 * 只接受 {@code [a-z0-9/._-]}，而工坊条目的 id 是由标题派生的，可能是中文。实测
	 * 扫到「arknights-明日方舟-阿米娅-王冠…」这个条目时，选择界面一渲染就抛
	 * {@code ResourceLocationException} 把客户端崩掉（crash-2026-10-05_15.15.30）。
	 * </p>
	 *
	 * <p>
	 * 这里把 id 洗成合法字符（非法的换成 {@code _}，小写，最多 32 个字符）并在末尾接上
	 * 哈希：洗字符只是为了让日志里能认出是哪张卡，哈希负责不撞名（只洗字符的话，
	 * 两个只有非法字符不同的 id 会指向同一张贴图）。同样的做法在
	 * {@code TwilightCoverCache} 与 {@code NeteaseImageCache} 里已经用了。</p>
	 */
	static String thumbnailTextureName(String id)
	{
		StringBuilder safe = new StringBuilder();

		for(int i = 0; i < id.length() && safe.length() < 32; i++)
		{
			char c = Character.toLowerCase(id.charAt(i));
			boolean legal = c >= 'a' && c <= 'z' || c >= '0' && c <= '9'
				|| c == '.' || c == '_' || c == '-';
			safe.append(legal ? c : '_');
		}

		return "background_thumb/" + safe + "_"
			+ Integer.toUnsignedString(id.hashCode(), 16);
	}

	private int cardAt(Rect panel, double mouseX, double mouseY)
	{
		int columns = columns(panel);
		int contentTop = panel.y + HEADER_HEIGHT;
		int cards = entries.size() + 2;

		for(int index = 0; index < cards; index++)
		{
			int column = index % columns;
			int row = index / columns;
			int x = panel.x + PADDING + column * (CARD_WIDTH + GAP);
			int y = contentTop + PADDING + row * (CARD_HEIGHT + GAP) - scroll;

			if(x <= mouseX && mouseX < x + CARD_WIDTH && y <= mouseY
				&& mouseY < y + CARD_HEIGHT)
				return index;
		}

		return -1;
	}

	private int columns(Rect panel)
	{
		return Math.max(1,
			(panel.width - PADDING * 2 + GAP) / (CARD_WIDTH + GAP));
	}

	private Rect panel()
	{
		int width = Math.min(this.width - 40, 700);
		int height = Math.min(this.height - 40, 420);
		return new Rect((this.width - width) / 2, (this.height - height) / 2,
			width, height);
	}

	private Rect closeButton(Rect panel)
	{
		return new Rect(panel.right() - 30, panel.y + 10, 20, 20);
	}

	private Rect scanButton(Rect panel)
	{
		return new Rect(panel.right() - 30 - 12 - 110, panel.y + 10, 110, 20);
	}

	private Rect motionButton(Rect panel)
	{
		return new Rect(panel.right() - 30 - 12 - 110 - 12 - 120, panel.y + 10,
			120, 20);
	}

	private String trim(String text, int maxWidth)
	{
		return font.width(text) <= maxWidth ? text
			: font.plainSubstrByWidth(text, maxWidth - 6) + "…";
	}

	/** A screen space rectangle. */
	private record Rect(int x, int y, int width, int height)
	{
		int right()
		{
			return x + width;
		}

		int bottom()
		{
			return y + height;
		}

		int centerX()
		{
			return x + width / 2;
		}

		boolean contains(double pointX, double pointY)
		{
			return pointX >= x && pointX < right() && pointY >= y
				&& pointY < bottom();
		}
	}
}
