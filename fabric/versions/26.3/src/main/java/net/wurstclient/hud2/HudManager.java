package net.wurstclient.hud2;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import net.wurstclient.Feature;
import net.wurstclient.WurstClient;
import net.wurstclient.event.EventManager;
import net.wurstclient.events.GUIRenderListener;
import net.wurstclient.hud2.elements.ArmorHudElement;
import net.wurstclient.hud2.elements.ClockHudElement;
import net.wurstclient.hud2.elements.ComboHudElement;
import net.wurstclient.hud2.elements.CoordsHudElement;
import net.wurstclient.hud2.elements.FpsHudElement;
import net.wurstclient.hud2.elements.InventoryHudElement;
import net.wurstclient.hud2.elements.KeystrokesHudElement;
import net.wurstclient.hud2.elements.MinimapHudElement;
import net.wurstclient.hud2.elements.NotificationHudElement;
import net.wurstclient.hud2.elements.PingHudElement;
import net.wurstclient.hud2.elements.PotionHudElement;
import net.wurstclient.hud2.elements.ServerHudElement;
import net.wurstclient.hud2.elements.SpeedHudElement;
import net.wurstclient.hud2.elements.TargetHudElement;
import net.wurstclient.hud2.elements.TpsHudElement;
import net.wurstclient.util.ScreenRegistry;
import net.wurstclient.util.json.JsonException;
import net.wurstclient.util.json.JsonUtils;
import net.minecraft.client.gui.GuiGraphicsExtractor;

/**
 * Soar Client v7 style HUD manager: every HUD module has an absolute position
 * and a scale factor stored in {@code soar-hud.json}.
 */
public final class HudManager implements GUIRenderListener
{
	private static final WurstClient WURST = WurstClient.INSTANCE;
	private final Map<String, HUDMod> mods = new LinkedHashMap<>();
	private final HudNotificationRenderer notificationRenderer =
		new HudNotificationRenderer();
	private final Map<String, ModState> loadedStates = new LinkedHashMap<>();
	private Path stateFile;
	private HudTheme theme = HudTheme.NORMAL;
	private boolean stopping;

	public void start()
	{
		stateFile = WURST.getWurstFolder().resolve("soar-hud.json");
		loadState();
		registerDefaults();
		applyState();

		for(HUDMod mod : mods.values())
			if(mod.isEnabled())
				mod.onEnable(this);

		EventManager eventManager = WURST.getEventManager();
		if(eventManager != null)
			eventManager.add(GUIRenderListener.class, this);

		System.out.println("[HUD] Soar v7 HUD manager started");
	}

	public void stop()
	{
		stopping = true;
		saveState();
		EventManager eventManager = WURST.getEventManager();
		if(eventManager != null)
			eventManager.remove(GUIRenderListener.class, this);
		for(HUDMod mod : mods.values())
			if(mod.isEnabled())
				mod.onDisable(this);
		HackHudElement.clearManaged();
		stopping = false;
	}

	public boolean isStopping()
	{
		return stopping;
	}

	@Override
	public void onRenderGUI(GuiGraphicsExtractor graphics, float partialTicks)
	{
		syncPortedHudMods();

		if(ScreenRegistry.HUD_EDITOR.isOpen())
			return;

		for(HUDMod mod : mods.values())
		{
			if(!mod.isEnabled())
				continue;
			mod.clampToScreen(graphics.guiWidth(), graphics.guiHeight());
			mod.render(graphics, partialTicks);
		}
	}

	private void syncPortedHudMods()
	{
		boolean changed = false;
		for(HUDMod mod : mods.values())
		{
			if(!(mod instanceof HackHudElement hackMod))
				continue;
			changed |= hackMod.isEnabled() != hackMod.getHack().isEnabled();
			hackMod.syncFromHack();
		}
		if(changed)
			saveState();
	}

	private void registerDefaults()
	{
		register(new FpsHudElement());
		register(new CoordsHudElement());
		register(new PingHudElement());
		register(new TpsHudElement());
		register(new SpeedHudElement());
		register(new ServerHudElement());
		register(new ClockHudElement());
		register(new ArmorHudElement());
		register(new PotionHudElement());
		register(new ComboHudElement());
		register(new KeystrokesHudElement());
		register(new TargetHudElement());
		register(new InventoryHudElement());
		register(new MinimapHudElement());
		register(new NotificationHudElement());
		register(new HackListHudMod());
		register(new LogoHudMod());

		for(HackHudElement element : HackHudElement.createAll())
			register(element);
	}

	private void register(HUDMod mod)
	{
		mods.put(mod.getId(), mod);
	}

	private void applyState()
	{
		theme = HudTheme.byId(loadedTheme);
		HUDMod.setTheme(theme);
		for(HUDMod mod : mods.values())
		{
			ModState state = loadedStates.get(mod.getId());
			if(state == null)
			{
				mod.setEnabledSilently(mod.isDefaultEnabled());
				continue;
			}
			mod.setX(state.x);
			mod.setY(state.y);
			mod.setScale(state.scale);
			mod.setEnabledSilently(state.enabled);
		}
	}

	private String loadedTheme = "normal";

	private void loadState()
	{
		if(!Files.isRegularFile(stateFile))
			return;

		try
		{
			JsonElement json = JsonUtils.parseFile(stateFile);
			if(!json.isJsonObject())
				return;
			JsonObject root = json.getAsJsonObject();
			if(root.has("theme"))
				loadedTheme = root.get("theme").getAsString();
			JsonArray array = root.getAsJsonArray("mods");
			if(array == null)
				return;
			for(JsonElement element : array)
			{
				JsonObject obj = element.getAsJsonObject();
				if(!obj.has("id"))
					continue;
				String id = obj.get("id").getAsString();
				ModState state = new ModState();
				state.x = obj.has("x") ? obj.get("x").getAsInt() : 100;
				state.y = obj.has("y") ? obj.get("y").getAsInt() : 100;
				state.scale = obj.has("scale")
					? obj.get("scale").getAsFloat() : 1.0F;
				state.enabled = !obj.has("enabled")
					|| obj.get("enabled").getAsBoolean();
				loadedStates.put(id, state);
			}
		}catch(IOException | JsonException e)
		{
			System.err.println("[HUD] Failed to load state: "
				+ e.getMessage());
		}
	}

	public void saveState()
	{
		if(stateFile == null)
			return;

		JsonObject root = new JsonObject();
		root.addProperty("theme", theme.getId());
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

	public Map<String, HUDMod> getMods()
	{
		return mods;
	}

	public HUDMod getMod(String id)
	{
		return mods.get(id);
	}

	public void setModEnabled(String id, boolean enabled)
	{
		HUDMod mod = mods.get(id);
		if(mod == null || mod.isEnabled() == enabled)
			return;
		mod.setEnabled(enabled);
		if(enabled)
			mod.onEnable(this);
		else
			mod.onDisable(this);
		saveState();
	}

	public void toggleMod(String id)
	{
		HUDMod mod = mods.get(id);
		if(mod != null)
			setModEnabled(id, !mod.isEnabled());
	}

	public void moveMod(String id, int x, int y)
	{
		HUDMod mod = mods.get(id);
		if(mod == null)
			return;
		mod.setX(x);
		mod.setY(y);
		saveState();
	}

	public void scaleMod(String id, float scale)
	{
		HUDMod mod = mods.get(id);
		if(mod == null)
			return;
		mod.setScale(scale);
		saveState();
	}

	public HudTheme getTheme()
	{
		return theme;
	}

	public void setTheme(HudTheme theme)
	{
		this.theme = theme == null ? HudTheme.NORMAL : theme;
		HUDMod.setTheme(this.theme);
		saveState();
	}

	public HudNotificationRenderer getNotificationRenderer()
	{
		return notificationRenderer;
	}

	public void addNotification(String title, String message,
		NotificationSeverity severity)
	{
		notificationRenderer.addNotification(title, message, severity);
	}

	public void addNotification(Feature feature)
	{
		NotificationSeverity severity = feature.isEnabled()
			? NotificationSeverity.ENABLED : NotificationSeverity.DISABLED;
		String title = feature.isEnabled() ? "Enabled" : "Disabled";
		notificationRenderer.addNotification(title,
			feature.getDisplayName(), severity);
	}

	private final class HackListHudMod extends HUDMod
	{
		private HackListHudMod()
		{
			super("hacklist", "Hack List");
			setDefaultPosition(760, 4);
		}

		@Override
		protected void draw()
		{
			var hackList = WURST.getHud().getHackList();
			int width = hackList.getWidth();
			int height = hackList.getHeight();
			drawBackground(width, height);
			graphics.pose().pushMatrix();
			graphics.pose().translate(getX(), getY());
			graphics.pose().scale(getScale(), getScale());
			hackList.renderAt(graphics, partialTicks, 0, 0, false);
			graphics.pose().popMatrix();
			setSize(width, height);
		}
	}

	private final class LogoHudMod extends HUDMod
	{
		private LogoHudMod()
		{
			super("logo", "Logo");
			setDefaultPosition(4, 4);
		}

		@Override
		protected void draw()
		{
			var logo = WURST.getHud().getWurstLogo();
			int width = logo.getWidth();
			int height = logo.getHeight();
			drawBackground(width, height);
			graphics.pose().pushMatrix();
			graphics.pose().translate(getX(), getY());
			graphics.pose().scale(getScale(), getScale());
			logo.renderAt(graphics, 0, 0);
			graphics.pose().popMatrix();
			setSize(width, height);
		}
	}

	private static final class ModState
	{
		int x;
		int y;
		float scale;
		boolean enabled;
	}
}
