/*
 * Copyright (c) 2026 WurstB+ Plus contributors
 *
 * This source code is subject to the terms of the GNU Affero General Public
 * License, version 3. If a copy of the AGPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/agpl-3.0.txt
 */
package net.wurstclient.hud2;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import com.mojang.blaze3d.pipeline.RenderTarget;

import org.lwjgl.opengl.GL11;

import net.wurstclient.Feature;
import net.wurstclient.WurstClient;
import net.wurstclient.hud2.mods.NotificationsMod;
import net.wurstclient.hud2.nanovg.GlStateGuard;
import net.wurstclient.hud2.nanovg.NanoVGContext;
import net.wurstclient.util.json.JsonException;
import net.wurstclient.util.json.JsonUtils;

/**
 * Soar v7 style HUD manager.
 *
 * <p>Owns every {@link HUDMod}, loads/saves the absolute position, scale and
 * enabled state, and draws the whole HUD through NanoVG after the vanilla GUI
 * pass.
 */
public final class HudManager
{
	private static final WurstClient WURST = WurstClient.INSTANCE;
	private static final Map<String, String> LEGACY_IDS = Map.ofEntries(
		Map.entry("fps", "FPSDisplay"),
		Map.entry("coords", "Coords"),
		Map.entry("coordinates", "Coords"),
		Map.entry("ping", "PingDisplay"),
		Map.entry("tps", "Tps"),
		Map.entry("speed", "Speedometer"),
		Map.entry("server", "ServerIPDisplay"),
		Map.entry("clock", "Clock"),
		Map.entry("armor", "ArmorStatus"),
		Map.entry("potions", "PotionStatus"),
		Map.entry("potion_effects", "PotionStatus"),
		Map.entry("combo", "ComboCounter"),
		Map.entry("keystrokes", "Keystrokes"),
		Map.entry("target_hud", "TargetInfo"),
		Map.entry("inventory", "InventoryDisplay"),
		Map.entry("minimap", "Minimap"),
		Map.entry("notifications", "Notifications"),
		Map.entry("hacklist", "ArrayList"),
		Map.entry("logo", "Logo"));

	private final Map<String, HUDMod> mods = new LinkedHashMap<>();
	private final HudSettingsFeature settings = new HudSettingsFeature();
	private final HudNotificationRenderer notificationRenderer =
		new HudNotificationRenderer();

	private Path stateFile;
	private boolean started;
	private boolean stopping;

	public HudManager()
	{
		registerDefaults();
	}

	public void start()
	{
		stateFile = WURST.getWurstFolder().resolve("soar-hud.json");
		loadState();
		HUDMod.setTheme(settings.getTheme());

		for(HUDMod mod : mods.values())
			if(mod.isEnabled())
				try
				{
					mod.onEnable();
				}catch(Throwable t)
				{
					System.err.println("[HUD] " + mod.getId()
						+ " failed to enable at startup");
					t.printStackTrace();
				}

		started = true;
		System.out.println("[HUD] Soar v7 HUD started (" + mods.size()
			+ " modules)");
	}

	public void stop()
	{
		stopping = true;
		saveState();
		for(HUDMod mod : mods.values())
			if(mod.isEnabled())
				mod.onDisable();
		stopping = false;
		started = false;
	}

	public boolean isStopping()
	{
		return stopping;
	}

	public void prepareFrame(float partialTicks)
	{
		// Reserved for texture/item capture scheduling.
	}

	public void renderFrame(float partialTicks)
	{
		if(!started || WURST.MC.screen instanceof GuiEditHUD
			|| !isBaseVisible())
			return;

		boolean screenBlocked = isScreenBlocked();

		NanoVGContext nvg = NanoVGContext.get();
		if(!nvg.create())
			return;

		net.wurstclient.hud2.compat.HudItemIcons.capturePending(nvg, 6);

		try(GlStateGuard ignored = GlStateGuard.capture())
		{
			if(!nvg.beginFrame())
				return;

			try
			{
				if(screenBlocked)
					drawScreenOverlayModules(nvg, partialTicks);
				else
					drawModules(nvg, partialTicks);
			}finally
			{
				nvg.endFrame();
			}

			RenderTarget target = WURST.MC.getMainRenderTarget();
			target.bindWrite(false);
			GL11.glViewport(0, 0, target.width, target.height);
			nvg.composite();
		}catch(Throwable t)
		{
			System.err.println("[HUD] Render failed");
			t.printStackTrace();
		}
	}

	/**
	 * Keeps the hotbar replacement visible while a screen (ClickGUI,
	 * Navigator, inventory, chat, ...) is open.
	 */
	private void drawScreenOverlayModules(NanoVGContext nvg, float partialTicks)
	{
		HUDMod hotbar = mods.get("ModernHotbar");
		if(hotbar == null || !hotbar.isEnabled())
			return;

		int screenWidth = WURST.MC.getWindow().getGuiScaledWidth();
		int screenHeight = WURST.MC.getWindow().getGuiScaledHeight();
		hotbar.clampToScreen(screenWidth, screenHeight);
		hotbar.render(nvg, partialTicks);
	}

	private void drawModules(NanoVGContext nvg, float partialTicks)
	{
		int screenWidth = WURST.MC.getWindow().getGuiScaledWidth();
		int screenHeight = WURST.MC.getWindow().getGuiScaledHeight();
		HUDMod notifications = mods.get("Notifications");

		for(HUDMod mod : mods.values())
		{
			if(mod == notifications || !mod.isEnabled())
				continue;

			mod.clampToScreen(screenWidth, screenHeight);
			mod.render(nvg, partialTicks);
		}

		// Notifications are an overlay and must render above every HUD card.
		if(notifications != null && notifications.isEnabled())
		{
			notifications.clampToScreen(screenWidth, screenHeight);
			notifications.render(nvg, partialTicks);
		}
	}

	/**
	 * Draws all modules (including disabled previews), the editor outlines and
	 * the snap guides inside the editor's own NanoVG pass.
	 */
	public void renderEditor(NanoVGContext nvg, GuiEditHUD editor,
		float partialTicks)
	{
		int screenWidth = WURST.MC.getWindow().getGuiScaledWidth();
		int screenHeight = WURST.MC.getWindow().getGuiScaledHeight();

		HUDMod notifications = mods.get("Notifications");
		for(HUDMod mod : mods.values())
		{
			if(mod == notifications || !mod.isEnabled())
				continue;

			renderEditorModule(nvg, editor, mod, partialTicks, screenWidth,
				screenHeight);
		}
		if(notifications != null && notifications.isEnabled())
			renderEditorModule(nvg, editor, notifications, partialTicks,
				screenWidth, screenHeight);

		if(editor.getSnapLineX() >= 0)
			nvg.drawRect(editor.getSnapLineX(), 0, 1, screenHeight,
				new java.awt.Color(217, 60, 255));
		if(editor.getSnapLineY() >= 0)
			nvg.drawRect(0, editor.getSnapLineY(), screenWidth, 1,
				new java.awt.Color(217, 60, 255));
	}

	private void renderEditorModule(NanoVGContext nvg, GuiEditHUD editor,
		HUDMod mod, float partialTicks, int screenWidth, int screenHeight)
	{
		mod.clampToScreen(screenWidth, screenHeight);
		mod.render(nvg, partialTicks);
		if(!mod.hasContent())
			return;

		// The selection ring sits exactly on the card's edge, using the same
		// radius as HUDMod.drawBackground(). Drawing it a few pixels outside
		// made the ring look detached from the card on dark backgrounds.
		boolean hovered = mod.getId().equals(editor.getHoveredId());
		int color = hovered ? 0xFFFFFFFF : 0x66FFFFFF;
		nvg.drawOutlineRoundedRect(mod.getX(), mod.getY(), mod.getWidth(),
			mod.getHeight(), Math.max(2, 6F * mod.getScale()), 1F,
			new java.awt.Color(color, true));

		// Label every enabled module so empty overlays are identifiable.
		nvg.drawText(mod.getTranslatedName(), mod.getX(),
			Math.max(0, mod.getY() - 11), 8F,
			net.wurstclient.hud2.nanovg.HudFonts.REGULAR,
			new java.awt.Color(0xCCFFFFFF, true));
	}

	private boolean isBaseVisible()
	{
		if(WURST.MC == null || WURST.MC.level == null
			|| WURST.MC.player == null)
			return false;
		if(WURST.MC.options.hideGui)
			return false;
		if(settings.hideWithDebug() && WURST.MC.options.renderDebug)
			return false;

		return true;
	}

	private boolean isScreenBlocked()
	{
		return WURST.MC.screen != null
			&& !(WURST.MC.screen instanceof GuiEditHUD)
			&& settings.hideWithChat();
	}

	public void addNotification(String title, String message,
		NotificationSeverity severity)
	{
		if(settings.showNotifications())
			notificationRenderer.addNotification(title, message, severity);
	}

	public void addNotification(Feature feature)
	{
		NotificationSeverity severity = feature.isEnabled()
			? NotificationSeverity.ENABLED : NotificationSeverity.DISABLED;
		String title = feature.isEnabled() ? "Enabled" : "Disabled";
		addNotification(title, feature.getDisplayName(), severity);
	}

	void onModuleEnabledChanged(HUDMod mod)
	{
		saveState();
	}

	public Map<String, HUDMod> getMods()
	{
		return mods;
	}

	public HUDMod getModule(String id)
	{
		return mods.get(id);
	}

	public List<Feature> getAllFeatures()
	{
		List<Feature> features = new ArrayList<>(mods.values());
		features.add(settings);
		return features;
	}

	public int getFeatureCount()
	{
		return mods.size() + 1;
	}

	public HudSettingsFeature getSettings()
	{
		return settings;
	}

	public HudNotificationRenderer getNotificationRenderer()
	{
		return notificationRenderer;
	}

	public HudTheme getTheme()
	{
		return settings.getTheme();
	}

	public void setTheme(HudTheme theme)
	{
		settings.setTheme(theme);
		HUDMod.setTheme(theme);
		saveState();
	}

	public void setModuleEnabled(String id, boolean enabled)
	{
		HUDMod mod = mods.get(id);
		if(mod != null)
			mod.setEnabled(enabled);
	}

	public void toggleModule(String id)
	{
		HUDMod mod = mods.get(id);
		if(mod != null)
			mod.setEnabled(!mod.isEnabled());
	}

	public void resetLayout()
	{
		for(HUDMod mod : mods.values())
		{
			mod.setX(mod.getDefaultX());
			mod.setY(mod.getDefaultY());
			mod.setScale(mod.getDefaultScale());
		}

		HUDMod notifications = mods.get("Notifications");
		if(notifications != null && WURST.MC.getWindow() != null)
		{
			int width = WURST.MC.getWindow().getGuiScaledWidth();
			int height = WURST.MC.getWindow().getGuiScaledHeight();
			notifications.setDefaultPosition(Math.max(8, width - 250),
				Math.max(8, height - 80));
		}

		saveState();
	}

	public void moveModule(String id, int x, int y)
	{
		HUDMod mod = mods.get(id);
		if(mod == null)
			return;
		mod.setX(x);
		mod.setY(y);
		saveState();
	}

	public void scaleModule(String id, float scale)
	{
		HUDMod mod = mods.get(id);
		if(mod == null)
			return;
		mod.setScale(scale);
		saveState();
	}

	private void register(HUDMod mod)
	{
		mod.bindManager(this);
		mods.put(mod.getId(), mod);
	}

	private void registerDefaults()
	{
		register(new NotificationsMod(this));
		register(new net.wurstclient.hud2.mods.FPSDisplayMod());
		register(new net.wurstclient.hud2.mods.PingDisplayMod());
		register(new net.wurstclient.hud2.mods.CoordsMod());
		register(new net.wurstclient.hud2.mods.ClockMod());
		register(new net.wurstclient.hud2.mods.DayCounterMod());
		register(new net.wurstclient.hud2.mods.GameModeDisplayMod());
		register(new net.wurstclient.hud2.mods.HealthDisplayMod());
		register(new net.wurstclient.hud2.mods.ServerIPDisplayMod());
		register(new net.wurstclient.hud2.mods.SpeedometerMod());
		register(new net.wurstclient.hud2.mods.WeatherDisplayMod());
		register(new net.wurstclient.hud2.mods.PlayerCounterMod());
		register(new net.wurstclient.hud2.mods.MemoryUsageMod());
		register(new net.wurstclient.hud2.mods.PlayTimeDisplayMod());
		register(new net.wurstclient.hud2.mods.NameDisplayMod());
		register(new net.wurstclient.hud2.mods.TpsMod());
		register(new net.wurstclient.hud2.mods.ArmorStatusMod());
		register(new net.wurstclient.hud2.mods.PotionStatusMod());
		register(new net.wurstclient.hud2.mods.KeystrokesMod());
		register(new net.wurstclient.hud2.mods.MouseStrokesMod());
		register(new net.wurstclient.hud2.mods.CPSDisplayMod());
		register(new net.wurstclient.hud2.mods.ComboCounterMod());
		register(new net.wurstclient.hud2.mods.ReachDisplayMod());
		register(new net.wurstclient.hud2.mods.ToggleSprintMod());
		register(new net.wurstclient.hud2.mods.SessionInfoMod());
		register(new net.wurstclient.hud2.mods.TNTTimerMod());
		register(new net.wurstclient.hud2.mods.StopwatchMod());
		register(new net.wurstclient.hud2.mods.BlockInfoMod());
		register(new net.wurstclient.hud2.mods.ItemInfoMod());
		register(new net.wurstclient.hud2.mods.TargetInfoMod());
		register(new net.wurstclient.hud2.mods.ScoreboardMod());
		register(new net.wurstclient.hud2.mods.PlayerListMod());
		register(new net.wurstclient.hud2.mods.BossHealthMod());
		register(new net.wurstclient.hud2.mods.BedwarsStatsOverlayMod());
		register(new net.wurstclient.hud2.mods.HorseStatsMod());
		register(new net.wurstclient.hud2.mods.PotionCounterMod());
		register(new net.wurstclient.hud2.mods.ArrayListMod());
		register(new net.wurstclient.hud2.mods.LogoMod());
		register(new net.wurstclient.hud2.mods.PlayerDisplayMod());
		register(new net.wurstclient.hud2.mods.PackDisplayMod());
		register(new net.wurstclient.hud2.mods.CalendarMod());
		register(new net.wurstclient.hud2.mods.CompassMod());
		register(new net.wurstclient.hud2.mods.PitchDisplayMod());
		register(new net.wurstclient.hud2.mods.YawDisplayMod());
		register(new net.wurstclient.hud2.mods.JumpResetIndicatorMod());
		register(new net.wurstclient.hud2.mods.ViaVersionMod());
		register(new net.wurstclient.hud2.mods.UHCOverlayMod());
		register(new net.wurstclient.hud2.mods.AppleSkinMod());
		register(new net.wurstclient.hud2.mods.InventoryDisplayMod());
		register(new net.wurstclient.hud2.mods.ModernHotbarMod());
		register(new net.wurstclient.hud2.mods.MinimapMod());
		register(new net.wurstclient.hud2.mods.RearviewMod());
		register(new net.wurstclient.hud2.mods.MusicInfoMod());
		register(new net.wurstclient.hud2.mods.WebBrowserMod());
		register(new net.wurstclient.hud2.mods.GodbridgeAssistMod());
		register(new net.wurstclient.hud2.mods.Skin3DMod());
		register(new net.wurstclient.hud2.mods.ImageDisplayMod());
		register(new net.wurstclient.hud2.mods.SoundSubtitlesMod());
		register(new net.wurstclient.hud2.mods.WaypointMod());
		register(new net.wurstclient.hud2.mods.TabEditorMod());
	}

	public void saveState()
	{
		if(stateFile == null)
			return;

		JsonObject root = new JsonObject();
		root.addProperty("schema", 2);
		root.addProperty("theme", getTheme().getId());
		JsonArray array = new JsonArray();
		for(HUDMod mod : mods.values())
		{
			JsonObject obj = new JsonObject();
			obj.addProperty("id", mod.getId());
			obj.addProperty("x", mod.getX());
			obj.addProperty("y", mod.getY());
			obj.addProperty("scale", mod.getScale());
			obj.addProperty("enabled", mod.isEnabled());
			array.add(obj);
		}
		root.add("mods", array);

		try
		{
			JsonUtils.toJson(root, stateFile);
		}catch(IOException | JsonException e)
		{
			System.err.println("[HUD] Failed to save state: "
				+ e.getMessage());
		}
	}

	private void loadState()
	{
		if(stateFile == null || !Files.isRegularFile(stateFile))
		{
			loadLegacyLayout();
			return;
		}

		try
		{
			JsonElement json = JsonUtils.parseFile(stateFile);
			if(!json.isJsonObject())
				return;
			JsonObject root = json.getAsJsonObject();

			if(root.has("theme"))
				settings.setTheme(HudTheme.byId(root.get("theme").getAsString()));

			JsonArray array = root.getAsJsonArray("mods");
			if(array == null)
				return;

			for(JsonElement element : array)
			{
				JsonObject obj = element.getAsJsonObject();
				if(!obj.has("id"))
					continue;
				String id = resolveId(obj.get("id").getAsString());
				HUDMod mod = mods.get(id);
				if(mod == null)
					continue;

				if(obj.has("x"))
					mod.setX(obj.get("x").getAsInt());
				if(obj.has("y"))
					mod.setY(obj.get("y").getAsInt());
				if(obj.has("scale"))
					mod.setScale(obj.get("scale").getAsFloat());
				if(obj.has("enabled"))
					mod.setEnabledSilently(obj.get("enabled").getAsBoolean());
			}
		}catch(IOException | JsonException e)
		{
			System.err.println("[HUD] Failed to load state: "
				+ e.getMessage());
		}
	}

	private void loadLegacyLayout()
	{
		Path legacy = WURST.getWurstFolder().resolve("hud-layout.json");
		if(!Files.isRegularFile(legacy))
			return;

		try
		{
			JsonElement json = JsonUtils.parseFile(legacy);
			if(!json.isJsonObject())
				return;
			JsonArray elements = json.getAsJsonObject()
				.getAsJsonArray("elements");
			if(elements == null)
				return;

			for(JsonElement element : elements)
			{
				JsonObject obj = element.getAsJsonObject();
				if(!obj.has("id"))
					continue;
				HUDMod mod = mods.get(resolveId(obj.get("id").getAsString()));
				if(mod == null)
					continue;

				if(obj.has("enabled"))
					mod.setEnabledSilently(obj.get("enabled").getAsBoolean());
				if(obj.has("horizontalOffset"))
					mod.setX(obj.get("horizontalOffset").getAsInt());
				if(obj.has("verticalOffset"))
					mod.setY(obj.get("verticalOffset").getAsInt());
			}
		}catch(IOException | JsonException e)
		{
			System.err.println("[HUD] Failed to migrate hud-layout.json: "
				+ e.getMessage());
		}
	}

	private String resolveId(String rawId)
	{
		if(mods.containsKey(rawId))
			return rawId;

		String id = rawId;
		if(id.startsWith("hack."))
			id = id.substring(5);

		String legacy = LEGACY_IDS.get(id.toLowerCase(Locale.ROOT));
		if(legacy != null)
			id = legacy;

		for(String candidate : mods.keySet())
			if(candidate.equalsIgnoreCase(id))
				return candidate;

		for(HUDMod mod : mods.values())
			if(mod.getTranslationKey().equalsIgnoreCase(id))
				return mod.getId();

		return rawId;
	}
}
