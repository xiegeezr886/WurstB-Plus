/*
 * OpaiBounce Dynamic Island adaptation for Fabric 1.20.1.
 * Layout/motion: Island.svelte, IslandNoticeIcon.svelte, ChestIsland.svelte.
 * See assets/wurstpenguin/opai/NOTICE.txt for upstream attribution/licenses.
 */
package net.wurstclient.hud2.mods;

import java.awt.Color;
import net.wurstclient.neoraven.music.*;
import net.minecraft.client.gui.screens.ChatScreen;
import java.util.LinkedHashMap;

import org.lwjgl.nanovg.NanoVG;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ShulkerBoxMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.item.ItemStack;
import net.wurstclient.Feature;
import net.wurstclient.events.UpdateListener;
import net.wurstclient.events.WorldChangeListener;
import net.wurstclient.hud2.HUDMod;
import net.wurstclient.hud2.NotificationSeverity;
import net.wurstclient.hud2.compat.HudCompat;
import net.wurstclient.hud2.compat.HudItemIcons;
import net.wurstclient.hud2.island.IslandMotion;
import net.wurstclient.hud2.island.IslandActivity;
import net.wurstclient.hud2.island.IslandContainerView;
import net.wurstclient.hud2.island.IslandState;
import net.wurstclient.hud2.island.IslandState.Kind;
import net.wurstclient.hud2.island.IslandState.Notice;
import net.wurstclient.hud2.island.IslandState.NoticeKind;
import net.wurstclient.hud2.nanovg.HudFonts;
import net.wurstclient.hud2.nanovg.HudIcon;
import net.wurstclient.settings.CheckboxSetting;

public final class DynamicIslandMod extends HUDMod implements UpdateListener, WorldChangeListener
{
	private static final Color ACCENT = new Color(accentColor(255), true);
	private static final Color TEXT = new Color(0xE4E1E6);
	private static final Color GREEN = new Color(0x55FF55);
	private static final Color RED = new Color(0xFF5555);
	private static final Color TILE = new Color(0xC8141616, true);
	private final CheckboxSetting centered = new CheckboxSetting("Center horizontally",
		"Keep the island centered. Dragging in the HUD editor turns this off.", true);
	private final CheckboxSetting showStats = new CheckboxSetting("Show FPS and ping", false);
	private final IslandState state = new IslandState();
	private final IslandActivity activity = new IslandActivity();
	private final IslandMotion widthMotion = new IslandMotion();
	private final IslandMotion heightMotion = new IslandMotion();
	private final IslandMotion progressMotion = new IslandMotion();
	private ClientLevel level;
	private AbstractContainerMenu chest;
	private AbstractContainerMenu observedMenu;
	private IslandContainerView containerView;
	private int hoveredSlot = -1, lastClickedSlot = -1;
	private long lastClickedAt;
	private int chestRows;
	private final ItemStack[] stacks = new ItemStack[54];
	private final long[] slotChanged = new long[54];
	private boolean scaffold, breaking;
	private String blockName = "", scaffoldDetail = "";
	private float breakProgress;
	private Kind previousKind = Kind.IDLE;
	private long changedAt, lastFrame;
	private float visibility = 1;
	private float manualCenter = Float.NaN;
	private String username = "Player", server = "Singleplayer", fps = "0 fps", ping = "0 ms";
	private long nextInfoUpdate;
	private final float[] textBounds = new float[4];
	private final LinkedHashMap<String, float[]> textWidths = new LinkedHashMap<>();

	public DynamicIslandMod()
	{
		super("DynamicIsland", "Dynamic Island",
			"Opai-style island: session, notifications, scaffold, mining and chest contents.");
		setTranslationKey("dynamic_island");
		setSize(215, 23);
		setDefaultPosition(0, 12);
		setDefaultEnabled(true);
		addSetting(centered);
		addSetting(showStats);
	}

	private static long now() { return System.nanoTime() / 1_000_000; }

	@Override protected void onEnable()
	{
		reset();
		EVENTS.add(UpdateListener.class, this);
		EVENTS.add(WorldChangeListener.class, this);
	}

	@Override protected void onDisable()
	{
		EVENTS.remove(UpdateListener.class, this);
		EVENTS.remove(WorldChangeListener.class, this);
		reset();
	}

	@Override public void onWorldChange(ClientLevel world)
	{
		reset();
		level = world;
	}

	private void reset()
	{
		state.reset();
		activity.reset();
		widthMotion.reset();
		heightMotion.reset();
		progressMotion.reset();
		chest = null;
		observedMenu = null;
		containerView = null;
		hoveredSlot = lastClickedSlot = -1;
		chestRows = 0;
		level = null;
		scaffold = breaking = false;
		previousKind = Kind.IDLE;
		lastFrame = nextInfoUpdate = changedAt = 0;
		visibility = 1;
		java.util.Arrays.fill(stacks, ItemStack.EMPTY);
		java.util.Arrays.fill(slotChanged, 0);
	}

	/** Also called before routing notifications so a new world cannot erase a fresh notice. */
	private void syncWorld()
	{
		if(level != MC.level)
		{
			reset();
			level = MC.level;
		}
	}

	public void notify(Feature feature)
	{
		if(feature == this || feature == WURST.getHax().scaffoldWalkHack) return;
		String key = "module:" + feature.getName();
		String detail = WURST.translate("hud.dynamic_island.has_been",
			feature.getDisplayName());
		boolean enabled = feature.isEnabled();
		postToggle(key, detail, enabled, MC.level);
	}

	private void postToggle(String key, String detail, boolean enabled, ClientLevel sourceLevel)
	{
		if(!MC.isSameThread())
		{
			MC.execute(() -> postToggle(key, detail, enabled, sourceLevel));
			return;
		}
		if(!isEnabled() || MC.level != sourceLevel || MC.player == null) return;
		syncWorld();
		state.post(key, WURST.translate("hud.dynamic_island.module_toggled"),
			detail, NoticeKind.TOGGLE, enabled, now());
	}

	public void notify(String title, String message, NotificationSeverity severity)
	{
		postNotice(title, message, severity, MC.level);
	}

	private void postNotice(String title, String message, NotificationSeverity severity, ClientLevel sourceLevel)
	{
		if(!MC.isSameThread())
		{
			MC.execute(() -> postNotice(title, message, severity, sourceLevel));
			return;
		}
		if(!isEnabled() || MC.level != sourceLevel) return;
		syncWorld();
		if(MC.player == null) return;
		NoticeKind kind = switch(severity)
		{
			case ENABLED, DISABLED -> NoticeKind.TOGGLE;
			case SUCCESS -> NoticeKind.SUCCESS;
			case ERROR -> NoticeKind.WARNING;
			default -> NoticeKind.INFO;
		};
		state.post(title, title, message, kind,
			severity != NotificationSeverity.DISABLED && severity != NotificationSeverity.ERROR, now());
	}

	@Override public void onUpdate()
	{
		syncWorld();
		if(MC.player == null || MC.level == null) return;
		long now = now();
		state.expire(now);
		scaffold = WURST.getHax().scaffoldWalkHack.isEnabled();
		if(scaffold)
		{
			int count = WURST.getHax().scaffoldWalkHack.getRemainingBlockCount();
			state.blocks(count, now);
			scaffoldDetail = WURST.translate("hud.dynamic_island.blocks_left",
				state.count(), state.rate(now));
		}else state.resetBlocks();

		updateContainer(now);
	}

	/** Called after the actual interaction-manager operation, not before hack updates. */
	public void recordMining(long position, String name, float progress)
	{
		if(!isEnabled() || MC.player == null || MC.level == null) return;
		syncWorld();
		if(activity.mine(position, name, progress, now())) progressMotion.reset();
	}

	private void updateContainer(long now)
	{
		AbstractContainerMenu next = MC.player.containerMenu;
		observedMenu = next;
		int rows = next instanceof ChestMenu menu ? menu.getRowCount()
			: next instanceof ShulkerBoxMenu ? 3 : 0;
		if(rows == 0) next = null;
		boolean changed = activity.container(next, rows, now);
		if(changed)
		{
			containerView = null;
			hoveredSlot = lastClickedSlot = -1;
			java.util.Arrays.fill(stacks, ItemStack.EMPTY);
			java.util.Arrays.fill(slotChanged, 0);
		}
		for(int i = 0; next != null && i < rows * 9; i++)
		{
			ItemStack stack = next.getSlot(i).getItem();
			if(!ItemStack.matches(stacks[i], stack))
			{
				if(!changed) slotChanged[i] = now;
				stacks[i] = stack.copy();
			}
		}
		chest = next;
		chestRows = activity.rows(now);
	}

	private boolean canInteractWithContainer()
	{
		return isEnabled() && !isEditing() && MC.player != null && MC.gameMode != null
			&& MC.screen instanceof AbstractContainerScreen<?> screen
			&& screen.getMenu() == MC.player.containerMenu && chest == MC.player.containerMenu
			&& containerView != null && containerView.accepts(chest, now())
			&& previousKind == Kind.CHEST && visibility >= .95F
			&& !MC.options.keyPlayerList.isDown();
	}

	public void updateContainerHover(double mouseX, double mouseY)
	{
		hoveredSlot = canInteractWithContainer() ? containerView.slotAt(mouseX, mouseY) : -1;
	}

	public ItemStack hoveredContainerStack(double mouseX, double mouseY)
	{
		updateContainerHover(mouseX, mouseY);
		return hoveredSlot < 0 ? ItemStack.EMPTY : chest.getSlot(hoveredSlot).getItem();
	}

	/** A real screen click uses vanilla quick-move; the server remains authoritative. */
	public boolean clickContainer(double mouseX, double mouseY, int button)
	{
		if(!canInteractWithContainer() || !containerView.contains(mouseX, mouseY)) return false;
		int slot = containerView.slotAt(mouseX, mouseY);
		long now = now();
		if(button == 0 && slot >= 0 && MC.player.containerMenu.getCarried().isEmpty()
			&& chest.getSlot(slot).hasItem() && chest.getSlot(slot).mayPickup(MC.player)
			&& (slot != lastClickedSlot || now - lastClickedAt >= 150))
		{
			lastClickedSlot = slot;
			lastClickedAt = slotChanged[slot] = now;
			MC.gameMode.handleContainerInput(chest.containerId, slot, 0, ContainerInput.QUICK_MOVE, MC.player);
		}
		// Never let a click on the overlay drop a carried stack into the world.
		return true;
	}

	@Override public void setDragging(boolean dragging)
	{
		if(dragging) centered.setChecked(false);
		super.setDragging(dragging);
	}

	@Override public void setX(int x)
	{
		super.setX(x);
		manualCenter = Float.NaN;
	}

	@Override protected void draw()
	{
		syncWorld();
		containerView = null;
		if(MC.player == null && !isEditing()) return;
		long now = now();
		state.expire(now);
		// Menus may open or close between ticks (or while the game is paused).
		if(MC.player != null && MC.player.containerMenu != observedMenu) updateContainer(now);
		chestRows = activity.rows(now);
		IslandActivity.Mining mining = activity.mining(now);
		breaking = mining != null;
		if(breaking)
		{
			blockName = mining.name();
			breakProgress = mining.progress();
		}
		if(now >= nextInfoUpdate)
		{
			username = MC.getUser().getName();
			server = HudCompat.serverIp();
			if(showStats.isChecked())
			{
				fps = HudCompat.fps() + " fps";
				ping = HudCompat.ping() + " ms";
			}
			nextInfoUpdate = now + 250;
		}
        MusicManager music = MusicManager.get();
        music.observePlayback();
        boolean musicVisible = music.current() != null && (music.isLoadingTrack()
            || music.isPlaying() || !music.pausedFor(10000) || MC.screen instanceof ChatScreen);
        Kind kind = isEditing() ? Kind.IDLE : state.kind(chestRows > 0, breaking, scaffold, musicVisible);
		boolean changed = kind != previousKind;
		if(changed)
		{
			changedAt = now;
			previousKind = kind;
			progressMotion.reset();
		}
		float available = Math.max(40, Math.min(400,
			MC.getWindow().getGuiScaledWidth() / getScale() - 16));
		String to = WURST.translate("hud.dynamic_island.to");
		float statsWidth = showStats.isChecked()
			? 13 + measure(ping, 8) + measure(to, 8) + 12 + 15.5F + measure(fps, 8) : 0;
		float fixedWidth = 9 + 15.5F + measure("WurstB+", 8) + 12 + 15.5F + 12 + 12.5F + statsWidth;
		float flexible = Math.max(0, available - fixedWidth);
		String user = fit(username, 8, Math.min(90, flexible * .45F));
		String connection = fit(server, 8, Math.min(125, flexible - measure(user, 8)));
		float idleWidth = fixedWidth + measure(user, 8) + measure(connection, 8);
		float targetWidth = idleWidth, targetHeight = 23;
        boolean expandedMusic = MC.screen instanceof ChatScreen;
        if(kind == Kind.MUSIC) {
            targetWidth = expandedMusic ? 296 : 240;
            targetHeight = expandedMusic ? 100 : 48;
        } else if(kind == Kind.NOTICE)
		{
			targetWidth = 100;
			for(Notice notice : state.notices())
				targetWidth = Math.max(targetWidth, 41.5F + Math.max(measure(notice.title(), 10),
					measure(notice.detail() + (notice.kind() == NoticeKind.TOGGLE
						? WURST.translate(notice.enabled()
							? "hud.dynamic_island.status_enabled"
							: "hud.dynamic_island.status_disabled")
							+ WURST.translate("hud.dynamic_island.exclamation")
						: ""), 9)));
			targetHeight = state.notices().size() * 36;
		}else if(kind == Kind.SCAFFOLD)
		{
			targetWidth = 45 + Math.max(
				measure(WURST.translate("hud.dynamic_island.scaffold_toggled"), 10),
				measure(scaffoldDetail, 9));
			targetHeight = 50;
		}else if(kind == Kind.BREAKING)
		{
			targetWidth = 44 + Math.max(
				measure(WURST.translate("hud.dynamic_island.breaking", blockName), 9),
				measure(WURST.translate("hud.dynamic_island.break_progress", "100%"), 8));
			targetHeight = 48;
		}else if(kind == Kind.CHEST)
		{
			targetWidth = 190;
			targetHeight = chestRows * 20 + 12;
		}
		targetWidth = Math.min(available, targetWidth);
		float impulse = changed ? kind == Kind.IDLE ? 5.5F : 7.25F : 0;
		float width = Math.max(1, Math.min(available, widthMotion.value(targetWidth, now, impulse)));
		float height = Math.max(23, heightMotion.value(targetHeight, now, impulse));
		if(Float.isNaN(manualCenter)) manualCenter = getX() + getWidth() / 2F;
		setSize(Math.round(width), Math.round(height));
		float center = centered.isChecked() ? MC.getWindow().getGuiScaledWidth() / 2F : manualCenter;
		super.setX(Math.round(center - getWidth() / 2F));
		super.clampToScreen(MC.getWindow().getGuiScaledWidth(), MC.getWindow().getGuiScaledHeight());
		float dt = lastFrame == 0 ? 0 : Math.min(1, (now - lastFrame) / 1000F);
		lastFrame = now;
		boolean hidden = !isEditing() && MC.options.keyPlayerList.isDown();
		visibility += ((hidden ? 0 : 1) - visibility) * (1 - (float)Math.exp(-20 * dt));
		if(visibility < .005F) return;
		nvg.setAlpha(visibility);
		nvg.translate(0, -5 * (1 - visibility) * getScale());
		drawBackground(width, height);
		markDrawn();
		save();
		scissor(2, 1, Math.max(0, width - 4), height - 2);
		// Shell morphs; glyphs stay at their original scale.
		float fade = Math.min(1, (now - changedAt) / 160F);
		nvg.setAlpha(visibility * (changedAt == 0 ? 1 : fade));
		float left = (width - targetWidth) / 2;
		if(kind == Kind.CHEST && chest != null && visibility >= .95F && fade >= 1)
			containerView = new IslandContainerView(chest, getX(), getY() - 5 * (1 - visibility) * getScale(), width * getScale(),
				height * getScale(), getScale(), chestRows, now);
		switch(kind)
		{
			case MUSIC -> drawMusic(left, targetWidth, expandedMusic);
			case IDLE -> drawIdle(left, user, connection);
			case NOTICE -> drawNotices(left, targetWidth, now, visibility * (changedAt == 0 ? 1 : fade));
			case SCAFFOLD -> drawActivity(left, targetWidth, true, now);
			case BREAKING -> drawActivity(left, targetWidth, false, now);
			case CHEST -> drawChest((width - 190) / 2, now);
		}
		restore();
	}

    private String musicLyric = "", oldMusicLyric = "";
    private long musicLyricAt;
    private void drawMusic(float left, float width, boolean expanded) {
        MusicManager music = MusicManager.get();
        Song song = music.current();
        if(song == null) return;
        float artSize = expanded ? 34 : 28;
        drawRoundedRect(left+9, 8, artSize, artSize, 8, new Color(0xFF343044,true));
        var art = CoverCache.get(song.cover(96));
        if(art != null) nvg.drawRoundedImage(net.wurstclient.hud2.compat.HudTextures.id(art.toString()),
            getX()+(left+9)*getScale(), getY()+8*getScale(), artSize*getScale(), artSize*getScale(),8*getScale(),1);
        else icon(HudIcon.MUSIC, left+9+artSize/2, 8+artSize/2, 16, ACCENT);
        float tx = left+artSize+17;
        text(fit(song.name, 10, width-artSize-50), tx, 15, 10, TEXT);
        String next = MusicView.lyric();
        long time = now();
        if(!next.equals(musicLyric)) { oldMusicLyric = musicLyric; musicLyric=next; musicLyricAt=time; }
        float fade = Math.min(1,(time-musicLyricAt)/240F);
        if(fade<1) text(fit(oldMusicLyric,8,width-artSize-48),tx,30-fade*4,8,new Color(228,225,230,(int)((1-fade)*180)));
        text(fit(musicLyric,8,width-artSize-48),tx,30+(1-fade)*4,8,new Color(228,225,230,(int)(fade*180)));
        // RMS bars reflect decoded PCM, rather than an unrelated looping animation.
        for(int i=0;i<3;i++) {
            float bar=3+Math.min(12,music.engine().level()*65)*(i==1?1:.65F);
            drawRoundedRect(left+width-20+i*4,22-bar/2,2,bar,1,ACCENT);
        }
        float progressY = expanded ? 53 : 41;
        drawRoundedRect(left+10,progressY,width-20,2,1,new Color(0xFF44414E,true));
        drawRoundedRect(left+10,progressY,(width-20)*MusicView.progress(),2,1,ACCENT);
        if(expanded) {
            text(MusicView.time(music.positionMs()),left+10,65,8,TEXT);
            text(MusicView.time(music.durationMs()),left+width-35,65,8,TEXT);
            text(music.repeat()==MusicManager.Repeat.ONE ? "单曲" : music.repeat()==MusicManager.Repeat.ALL ? "循环" : "顺序",left+15,83,8,ACCENT);
            icon(HudIcon.BACK,left+width/2-42,81,14,TEXT);
            drawRoundedRect(left+width/2-13,68,26,26,13,new Color(0x334F4B72,true));
            icon(music.isPlaying()?HudIcon.PAUSE:HudIcon.PLAY,left+width/2,81,15,TEXT);
            icon(HudIcon.FORWARD,left+width/2+42,81,14,TEXT);
            icon(HudIcon.EXTERNAL_LINK,left+width-24,81,13,TEXT);
        }
    }

    /** Chat supplies a cursor; normal gameplay and the HUD editor never consume music clicks. */
    public boolean clickMusic(double mx, double my, int button) {
        if(button != 0 || !isEnabled() || isEditing() || !(MC.screen instanceof ChatScreen)
            || previousKind != Kind.MUSIC || visibility < .95F || MC.options.keyPlayerList.isDown()
            || now()-changedAt < 200 || getUnscaledHeight()<95) return false;
        float x=(float)(mx-getX())/getScale(), y=(float)(my-getY())/getScale();
        float width=Math.min(296,getWidth()/getScale());
        if(x<0 || x>width || y<0 || y>100) return false;
        MusicManager music=MusicManager.get();
        if(y>=47 && y<=61 && x>=10 && x<=width-10)
            music.seek((long)(music.durationMs()*(x-10)/(width-20)));
        else if(y>=68) {
            if(x<50) music.cycleRepeat();
            else if(Math.abs(x-(width/2-42))<18) music.previous();
            else if(Math.abs(x-width/2)<18) music.togglePlay();
            else if(Math.abs(x-(width/2+42))<18) music.next();
            else if(x>width-45) MC.setScreen(new MusicScreen(MC.screen));
        } else if(x<48) MC.setScreen(new MusicScreen(MC.screen));
        return true;
    }

	private float measure(String text, float size)
	{
		if(text.isEmpty() || !HudFonts.MEDIUM.isLoaded()) return 0;
		float[] widths = textWidths.get(text);
		if(widths == null)
		{
			if(textWidths.size() >= 128) textWidths.remove(textWidths.keySet().iterator().next());
			widths = new float[11];
			java.util.Arrays.fill(widths, -1);
			textWidths.put(text, widths);
		}
		int index = (int)size;
		if(widths[index] >= 0) return widths[index];
		long vg = nvg.getHandle();
		NanoVG.nvgFontSize(vg, size);
		NanoVG.nvgFontFace(vg, HudFonts.MEDIUM.getName());
		NanoVG.nvgTextAlign(vg, NanoVG.NVG_ALIGN_LEFT | NanoVG.NVG_ALIGN_MIDDLE);
		// The returned advance includes spaces; ink bounds drop the trailing gap
		// before the differently colored Enabled/Disabled segment.
		return widths[index] = NanoVG.nvgTextBounds(vg, 0, 0, text, textBounds);
	}

	private String fit(String text, float size, float width)
	{
		if(measure(text, size) <= width) return text;
		if(measure("…", size) > width) return "";
		int low = 0, high = text.length();
		while(low < high)
		{
			int mid = (low + high + 1) / 2;
			if(measure(text.substring(0, mid) + "…", size) <= width) low = mid;
			else high = mid - 1;
		}
		if(low > 0 && Character.isHighSurrogate(text.charAt(low - 1))) low--;
		return text.substring(0, low) + "…";
	}

	private void text(String text, float x, float y, float size, Color color)
	{
		drawText(text, x, y - size / 2, size, HudFonts.MEDIUM, color);
	}

	private float label(String value, float x, Color color)
	{
		text(value, x, 11.5F, 8, color);
		return x + measure(value, 8);
	}

	private float dot(float x)
	{
		text("·", x + 3.5F, 11.5F, 8, TEXT);
		return x + 12;
	}

	private void icon(String glyph, float x, float y, float size, Color color)
	{
		drawText(glyph, x, y - size / 2, size, HudFonts.ICON, color);
	}

	private void drawIdle(float left, String user, String connection)
	{
		float x = left + 9;
		// User-supplied Wurst mark, fitted to a transparent square without stretching.
		drawImage(nvg.loadImage("assets/wurstpenguin/opai/wurst-logo.png"),
			x, 5.5F, 12, 12);
		x = dot(label("WurstB+", x + 15.5F, ACCENT));
		drawCircle(x + 6, 8.7F, 2.2F, TEXT);
		drawRoundedRect(x + 2, 12, 8, 4.2F, 2, TEXT);
		x = dot(label(user, x + 15.5F, TEXT));
		if(!showStats.isChecked())
		{
			label(connection, x, TEXT);
			return;
		}
		Color latency = HudCompat.ping() < 80 ? new Color(0x77EE66)
			: HudCompat.ping() < 180 ? new Color(0xFFD166) : RED;
		drawRoundedOutline(x + 1, 9, 6.7F, 5, 2.4F, 1.3F, latency);
		drawRoundedOutline(x + 4.5F, 9, 6.7F, 5, 2.4F, 1.3F, latency);
		x = label(ping, x + 13, latency);
		x = dot(label(WURST.translate("hud.dynamic_island.to") + connection,
			x, TEXT));
		icon(HudIcon.REFRESH, x, 11.5F, 12, TEXT);
		label(fps, x + 15.5F, TEXT);
	}

	private void drawNotices(float left, float width, long now, float opacity)
	{
		float y = 0;
		float radius = backgroundRadius(width, 36);
		for(Notice notice : state.notices())
		{
			save();
			float appearance = notice.appearance(now);
			nvg.setAlpha(opacity * appearance);
			nvg.translate(0, 2 * (1 - appearance) * getScale());
			if(notice.kind() == NoticeKind.TOGGLE)
			{
				float t = notice.toggle(now);
				drawRoundedRect(left + 3.44F, y + 9.04F, 29.12F, 17.92F, radius,
					mix(new Color(0x9E8D8B), ACCENT, t));
				drawRoundedRect(left + 4.56F, y + 10.16F, 26.88F, 15.68F, radius - 1.12F,
					mix(new Color(0x36343B), ACCENT, t));
				float size = (8 + 3.5F * t) * 1.12F;
				drawRoundedRect(left + 11.84F + 11.48F * t - size / 2, y + 18 - size / 2,
					size, size, size / 2, mix(new Color(0x858488), new Color(0x181316), t));
			}else
			{
				Color tile = notice.kind() == NoticeKind.INFO ? ACCENT
					: new Color(notice.kind() == NoticeKind.SUCCESS ? 0xC85F8F50 : 0xC88F5050, true);
				drawRoundedRect(left + 4.55F, y + 4.55F, 26.9F, 26.9F, radius, tile);
				centeredNoticeIcon(notice.kind() == NoticeKind.SUCCESS ? HudIcon.CHECK
					: notice.kind() == NoticeKind.WARNING ? HudIcon.ALERT_TRIANGLE : HudIcon.INFO,
					left + 18, y + 18, 15,
					notice.kind() == NoticeKind.INFO ? new Color(0x181316) : Color.WHITE);
			}
			text(fit(notice.title(), 10, width - 41.5F), left + 36, y + 13, 10,
				notice.kind() == NoticeKind.INFO ? ACCENT : Color.WHITE);
			String bang = WURST.translate("hud.dynamic_island.exclamation");
			String status = notice.kind() == NoticeKind.TOGGLE
				? WURST.translate(notice.enabled()
					? "hud.dynamic_island.status_enabled"
					: "hud.dynamic_island.status_disabled")
				: "";
			String detail = fit(notice.detail(), 9, width - 41.5F - measure(status + (status.isEmpty() ? "" : bang), 9));
			text(detail, left + 36, y + 25.25F, 9, Color.WHITE);
			if(!status.isEmpty())
			{
				float x = left + 36 + measure(detail, 9);
				text(status, x, y + 25.25F, 9, notice.enabled() ? GREEN : RED);
				text(bang, x + measure(status, 9), y + 25.25F, 9, Color.WHITE);
			}
			restore();
			y += 36;
		}
	}

	private void centeredNoticeIcon(String glyph, float x, float y,
		float size, Color color)
	{
		if(!HudFonts.ICON.isLoaded()) return;
		long vg = nvg.getHandle();
		NanoVG.nvgFontSize(vg, size);
		NanoVG.nvgFontFace(vg, HudFonts.ICON.getName());
		NanoVG.nvgTextAlign(vg, NanoVG.NVG_ALIGN_LEFT | NanoVG.NVG_ALIGN_MIDDLE);
		NanoVG.nvgTextBounds(vg, 0, 0, glyph, textBounds);
		// Center the visible glyph, including fonts with asymmetric bearings.
		icon(glyph, x - (textBounds[0] + textBounds[2]) / 2,
			y - (textBounds[1] + textBounds[3]) / 2, size, color);
	}

	private void drawActivity(float left, float width, boolean blocks, long now)
	{
		drawRoundedRect(left + 4, 4, 28, 28, 7, TILE);
		if(blocks)
		{
			line(left + 18, 11, left + 24.3F, 14.5F);
			line(left + 24.3F, 14.5F, left + 18, 18);
			line(left + 18, 18, left + 11.7F, 14.5F);
			line(left + 11.7F, 14.5F, left + 18, 11);
			line(left + 11.7F, 14.5F, left + 11.7F, 21.5F);
			line(left + 11.7F, 21.5F, left + 18, 25);
			line(left + 18, 25, left + 24.3F, 21.5F);
			line(left + 24.3F, 21.5F, left + 24.3F, 14.5F);
			line(left + 18, 18, left + 18, 25);
		}else
		{
			drawRect(left + 8, 10, 2, 17, ACCENT);
			drawRect(left + 10, 19, 18, 5, ACCENT);
			drawRect(left + 26, 22, 2, 5, ACCENT);
			drawCircle(left + 13, 15, 2.5F, ACCENT);
			drawRoundedRect(left + 17, 13, 11, 5, 2, ACCENT);
		}
		text(fit(blocks ? WURST.translate("hud.dynamic_island.scaffold_toggled")
			: WURST.translate("hud.dynamic_island.breaking", blockName),
			blocks ? 10 : 9, width - 45), left + 38, 12.5F, blocks ? 10 : 9, ACCENT);
		text(fit(blocks ? scaffoldDetail
			: WURST.translate("hud.dynamic_island.break_progress",
				(int)(breakProgress * 100) + "%"),
			blocks ? 9 : 8, width - 45), left + 38, 25, blocks ? 9 : 8, Color.WHITE);
		float barY = blocks ? 37 : 35, barH = blocks ? 8 : 7.5F;
		drawRoundedRect(left + 4, barY, width - 8, barH, 4, new Color(0x343636));
		float progress = Math.max(0, Math.min(1,
			progressMotion.value(blocks ? state.progress() : breakProgress, now, 0)));
		if(progress > 0) drawRoundedRect(left + 4, barY, (width - 8) * progress, barH, 4, ACCENT);
	}

	private void line(float x1, float y1, float x2, float y2)
	{
		nvg.drawLine(getX() + x1 * getScale(), getY() + y1 * getScale(),
			getX() + x2 * getScale(), getY() + y2 * getScale(), getScale(), ACCENT.getRGB());
	}

	private void drawChest(float left, long now)
	{
		for(int i = 0; i < chestRows * 9; i++)
		{
			float x = left + 5 + i % 9 * 20, y = 6 + i / 9 * 20;
			if(i == hoveredSlot && canInteractWithContainer())
				drawRoundedRect(x, y, 20, 20, 3, new Color(accentColor(40), true));
			float feedback = 1 - Math.min(1, (now - slotChanged[i]) / 380F);
			if(slotChanged[i] > 0 && feedback > 0)
			{
				float expansion = Math.min(1, (1 - feedback) / .32F);
				float size = 20 * (1 - (1 - expansion) * (1 - expansion));
				drawRoundedRect(x + (20 - size) / 2, y + (20 - size) / 2,
					size, size, 3 + 7 * (1 - expansion),
					new Color(accentColor((int)(feedback * 80)), true));
			}
			ItemStack stack = stacks[i];
			if(stack == null || stack.isEmpty()) continue;
			drawImage(HudItemIcons.get(stack), x + 2, y + 2, 16, 16);
			if(stack.getCount() > 1)
			{
				String count = Integer.toString(stack.getCount());
				text(count, x + 19 - measure(count, 7), y + 16, 7, Color.WHITE);
			}
		}
	}

	private static Color mix(Color a, Color b, float t)
	{
		return new Color(Math.round(a.getRed() + (b.getRed() - a.getRed()) * t),
			Math.round(a.getGreen() + (b.getGreen() - a.getGreen()) * t),
			Math.round(a.getBlue() + (b.getBlue() - a.getBlue()) * t));
	}
}
