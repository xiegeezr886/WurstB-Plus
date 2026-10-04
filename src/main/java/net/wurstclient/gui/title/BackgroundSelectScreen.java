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
import net.wurstclient.background.SteamLocator;
import net.wurstclient.background.WallpaperEngineImporter;
import net.wurstclient.gui.visual.VisualRenderer;
import net.wurstclient.gui.visual.VisualTheme;
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

	private final Screen parent;
	private final Map<String, ResourceLocation> thumbnails = new HashMap<>();
	private final Set<String> requested = new HashSet<>();

	private List<BackgroundEntry> entries = List.of();
	private int scroll;
	private String status = "";
	private boolean busy;

	public BackgroundSelectScreen(Screen parent)
	{
		super(Component.literal("选择背景"));
		this.parent = parent;
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
		graphics.fill(panel.x, panel.y, panel.right(), panel.bottom(), PANEL);
		graphics.fill(panel.x, panel.y, panel.right(), panel.y + 1, 0x33FFFFFF);

		drawHeader(graphics, panel, mouseX, mouseY);
		drawGrid(graphics, panel, mouseX, mouseY);

		if(!status.isEmpty())
			graphics.drawString(font, status, panel.x + PADDING,
				panel.bottom() - 18, MUTED, false);
	}

	private void drawHeader(GuiGraphics graphics, Rect panel, int mouseX,
		int mouseY)
	{
		graphics.drawString(font, "选择背景", panel.x + PADDING,
			panel.y + 16, TEXT, false);

		// close
		Rect close = closeButton(panel);
		boolean closeHovered = close.contains(mouseX, mouseY);
		graphics.drawString(font, "✕", close.x + 5, close.y + 4,
			closeHovered ? TEXT : MUTED, false);

		// scan Steam library
		Rect scan = scanButton(panel);
		boolean scanHovered = scan.contains(mouseX, mouseY);
		graphics.fill(scan.x, scan.y, scan.right(), scan.bottom(),
			scanHovered ? CARD_HOVER : CARD_BG);
		graphics.drawString(font, busy ? "扫描中…" : "扫描 Steam 库",
			scan.x + 8, scan.y + 6, scanHovered ? TEXT : MUTED, false);

		// motion picker: cycles, which is enough for four options
		Rect motion = motionButton(panel);
		boolean motionHovered = motion.contains(mouseX, mouseY);
		graphics.fill(motion.x, motion.y, motion.right(), motion.bottom(),
			motionHovered ? CARD_HOVER : CARD_BG);
		String label = "运动：" + BackgroundManager.get().motion();
		graphics.drawString(font, label, motion.x + 8, motion.y + 6,
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
		for(int x = card.x + 8; x < card.right() - 8; x += 12)
			graphics.fill(x, card.y + 8, x + 1, card.y + CARD_HEIGHT - 26,
				0x22FFFFFF);
		for(int y = card.y + 8; y < card.y + CARD_HEIGHT - 26; y += 12)
			graphics.fill(card.x + 8, y, card.right() - 8, y + 1, 0x22FFFFFF);
		drawCardLabel(graphics, card, "默认背景", "内置");
	}

	private void drawImportCard(GuiGraphics graphics, Rect card, int mouseX,
		int mouseY)
	{
		boolean hovered = card.contains(mouseX, mouseY);
		graphics.fill(card.x, card.y, card.right(), card.bottom(),
			hovered ? CARD_HOVER : 0x331B1E24);
		outline(graphics, card, hovered ? ACCENT : 0x44FFFFFF);
		int centerX = card.centerX();
		int centerY = card.y + CARD_HEIGHT / 2 - 8;
		graphics.fill(centerX - 10, centerY, centerX + 10, centerY + 2,
			hovered ? ACCENT : MUTED);
		graphics.fill(centerX - 1, centerY - 9, centerX + 1, centerY + 11,
			hovered ? ACCENT : MUTED);
		graphics.drawString(font, "导入图片 / GIF", card.x + 22,
			card.bottom() - 18, hovered ? TEXT : MUTED, false);
	}

	private void drawEntryCard(GuiGraphics graphics, Rect card,
		BackgroundEntry entry, int mouseX, int mouseY)
	{
		boolean selected = entry.id().equals(BackgroundManager.get().selectedId());
		cardBackground(graphics, card, mouseX, mouseY, selected);

		ResourceLocation thumbnail = thumbnail(entry);
		int previewBottom = card.y + CARD_HEIGHT - 26;

		if(thumbnail != null)
			graphics.blit(thumbnail, card.x + 4, card.y + 4,
				card.width - 8, previewBottom - card.y - 4, 0, 0, 256, 256,
				256, 256);
		else
			graphics.fill(card.x + 4, card.y + 4, card.right() - 4, previewBottom,
				0x22FFFFFF);

		if(entry.kind().isAnimated())
			drawBadge(graphics, card, entry.kind() == BackgroundKind.GIF
				? "GIF" : "视频");

		drawCardLabel(graphics, card, entry.title(), entry.origin());
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
		graphics.drawString(font, trim(title, card.width - 12), card.x + 6,
			card.bottom() - 22, TEXT, false);
		graphics.drawString(font, trim(subtitle, card.width - 12), card.x + 6,
			card.bottom() - 11, MUTED, false);
	}

	private void cardBackground(GuiGraphics graphics, Rect card, int mouseX,
		int mouseY, boolean selected)
	{
		boolean hovered = card.contains(mouseX, mouseY);
		graphics.fill(card.x, card.y, card.right(), card.bottom(),
			hovered ? CARD_HOVER : CARD_BG);

		if(selected)
			outline(graphics, card, ACCENT);
		else
			outline(graphics, card, hovered ? 0x66FFFFFF : 0x22FFFFFF);
	}

	private void outline(GuiGraphics graphics, Rect card, int color)
	{
		graphics.fill(card.x, card.y, card.right(), card.y + 1, color);
		graphics.fill(card.x, card.bottom() - 1, card.right(), card.bottom(),
			color);
		graphics.fill(card.x, card.y, card.x + 1, card.bottom(), color);
		graphics.fill(card.right() - 1, card.y, card.right(), card.bottom(),
			color);
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
				status = "不支持的文件格式：" + path.getFileName();
				return;
			}

			WurstClient.INSTANCE.getGuiPreferences().setSelectedBackground(
				BackgroundStorage.DEFAULT_ID);
			BackgroundManager.get().forget();

			// decoding the thumbnail is slow, so it happens off the client
			// thread and the import follows it
			Thread worker = new Thread(() -> {
				byte[] thumbnail = BackgroundThumbnail.create(path,
					BackgroundThumbnail.MAX_SIZE);
				String id = BackgroundManager.get().storage().importFile(path,
					kind, path.getFileName().toString(), path.getFileName()
						.toString(), thumbnail);

				minecraft.execute(() -> {
					if(id == null)
						status = "导入失败：" + path.getFileName();
					else
					{
						BackgroundManager.get().select(id);
						entries = BackgroundManager.get().entries();
						status = "已导入 " + path.getFileName();
					}
				});
			}, "WurstB-BackgroundImport");
			worker.setDaemon(true);
			worker.start();
		});
	}

	private void scanSteamLibrary()
	{
		busy = true;
		status = "正在扫描 Steam 库…";

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
					byte[] thumbnail = BackgroundThumbnail.create(
						candidate.media(), BackgroundThumbnail.MAX_SIZE);

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
				status = "导入完成：可播放 " + importedCount + " 张，仅预览 "
					+ previewCount + " 张，跳过 " + skippedCount + " 张";
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
			status = "已删除 " + entry.title();
		}else
			status = "删除失败：" + entry.title();
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
			"background_thumb/" + entry.id());

		AsyncTextureLoader.load(file, location).whenComplete((loaded, error) -> {
			if(error == null)
				thumbnails.put(entry.id(), loaded);
		});

		return null;
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
