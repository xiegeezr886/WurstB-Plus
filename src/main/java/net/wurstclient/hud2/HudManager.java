package net.wurstclient.hud2;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import net.wurstclient.Feature;
import net.wurstclient.WurstClient;
import net.wurstclient.event.EventManager;
import net.wurstclient.events.GUIRenderListener;
import net.wurstclient.hack.Hack;
import net.wurstclient.hud2.HudLayout.HudElementConfig;
import net.wurstclient.util.json.JsonException;
import net.wurstclient.util.json.JsonUtils;
import net.wurstclient.hud2.elements.CoordsHudElement;
import net.wurstclient.hud2.elements.ClockHudElement;
import net.wurstclient.hud2.elements.ArmorHudElement;
import net.wurstclient.hud2.elements.ComboHudElement;
import net.wurstclient.hud2.elements.CompassHudElement;
import net.wurstclient.hud2.elements.FpsHudElement;
import net.wurstclient.hud2.elements.CpsHudElement;
import net.wurstclient.hud2.elements.DayCounterHudElement;
import net.wurstclient.hud2.elements.GameModeHudElement;
import net.wurstclient.hud2.elements.HealthHudElement;
import net.wurstclient.hud2.elements.InventoryHudElement;
import net.wurstclient.hud2.elements.KeystrokesHudElement;
import net.wurstclient.hud2.elements.MemoryHudElement;
import net.wurstclient.hud2.elements.MinimapHudElement;
import net.wurstclient.hud2.elements.MusicIslandHudElement;
import net.wurstclient.hud2.elements.MusicLyricsHudElement;
import net.wurstclient.hud2.elements.NameHudElement;
import net.wurstclient.hud2.elements.PingHudElement;
import net.wurstclient.hud2.elements.PlayerCounterHudElement;
import net.wurstclient.hud2.elements.PlayTimeHudElement;
import net.wurstclient.hud2.elements.PotionHudElement;
import net.wurstclient.hud2.elements.PotionCounterHudElement;
import net.wurstclient.hud2.elements.ReachHudElement;
import net.wurstclient.hud2.elements.ScoreboardHudElement;
import net.wurstclient.hud2.elements.TargetHudElement;
import net.wurstclient.hud2.elements.ServerHudElement;
import net.wurstclient.hud2.elements.SpeedHudElement;
import net.wurstclient.hud2.elements.TpsHudElement;
import net.wurstclient.hud2.elements.WeatherHudElement;
import net.wurstclient.hud2.render.RiseFrostedGlass;
import net.wurstclient.util.ScreenRegistry;

public final class HudManager implements GUIRenderListener
{
	private static final WurstClient WURST = WurstClient.INSTANCE;
	private final Map<String, HudElement> elements = new LinkedHashMap<>();
	private final HudLayout layout = new HudLayout();
	private final Map<String, String> elementDisplayNames = new LinkedHashMap<>();
	private final HudNotificationRenderer notificationRenderer;
	private Path layoutFile;

	public HudManager()
	{
		notificationRenderer = new HudNotificationRenderer();
	}

	public void start()
	{
		layoutFile = WURST.getWurstFolder().resolve("hud-layout.json");
		loadLayout();
		registerDefaultElements();
		migrateLegacyLayout();

		for(HudElement element : elements.values())
		{
			HudElementConfig config = layout.get(element.getId());
			if(config != null && config.isEnabled())
				element.onEnable(this);
		}

		EventManager eventManager = WURST.getEventManager();
		if(eventManager != null)
			eventManager.add(GUIRenderListener.class, this);

		System.out.println("[HUD] Notification system started");
	}

	public void stop()
	{
		saveLayout();
		EventManager eventManager = WURST.getEventManager();
		if(eventManager != null)
			eventManager.remove(GUIRenderListener.class, this);
		for(HudElement element : elements.values())
		{
			HudElementConfig config = layout.get(element.getId());
			if(config != null && config.isEnabled())
				element.onDisable(this);
		}
		RiseFrostedGlass.close();
	}

	@Override
	public void onRenderGUI(net.minecraft.client.gui.GuiGraphics graphics,
		float partialTicks)
	{
		if(ScreenRegistry.HUD_EDITOR.isOpen())
			return;

		if(isElementEnabled(ScoreboardHudElement.ID)
			|| isElementEnabled("target_hud"))
		{
			graphics.flush();
			RiseFrostedGlass.captureFrame();
		}

		for(HudElement element : elements.values())
		{
			HudElementConfig config = layout.get(element.getId());
			if(config == null || !config.isEnabled()
				|| element.getId().equals("notifications"))
				continue;
			int scaledWidth = getScaledWidth(element, config);
			int scaledHeight = getScaledHeight(element, config);
			int x = getElementX(config, graphics.guiWidth(), scaledWidth);
			int y = getElementY(config, graphics.guiHeight(), scaledHeight);
			renderElement(graphics, element, config, x, y, partialTicks);
		}
		notificationRenderer.onRenderGUI(graphics, partialTicks);
	}

	public int getElementX(HudElementConfig config, int screenWidth,
		int elementWidth)
	{
		if(config.getHorizontalAlignment()
			.equals(HudElementConfig.HORIZONTAL_RIGHT))
			return screenWidth - elementWidth - config.getHorizontalOffset();
		if(config.getHorizontalAlignment()
			.equals(HudElementConfig.HORIZONTAL_CENTER))
			return (screenWidth - elementWidth) / 2
				+ config.getHorizontalOffset();
		return config.getHorizontalOffset();
	}

	public int getElementY(HudElementConfig config, int screenHeight,
		int elementHeight)
	{
		if(config.getVerticalAlignment().equals(HudElementConfig.VERTICAL_BOTTOM))
			return screenHeight - elementHeight - config.getVerticalOffset();
		if(config.getVerticalAlignment().equals(HudElementConfig.VERTICAL_CENTER))
			return (screenHeight - elementHeight) / 2
				+ config.getVerticalOffset();
		return config.getVerticalOffset();
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
		notificationRenderer.addNotification(title, feature.getDisplayName(), severity);
	}

	public void updateElementLayout(String elementId, String horizontalAlignment,
		String verticalAlignment, int horizontalOffset, int verticalOffset)
	{
		HudElementConfig config = layout.getOrCreate(elementId);
		config.setHorizontalAlignment(horizontalAlignment);
		config.setVerticalAlignment(verticalAlignment);
		config.setHorizontalOffset(horizontalOffset);
		config.setVerticalOffset(verticalOffset);
		saveLayout();
	}

	private int getScaledWidth(HudElement element, HudElementConfig config)
	{
		return Math.max(1, Math.round(element.getWidth() * config.getScale()));
	}

	private int getScaledHeight(HudElement element, HudElementConfig config)
	{
		return Math.max(1, Math.round(element.getHeight() * config.getScale()));
	}

	void renderElement(net.minecraft.client.gui.GuiGraphics graphics,
		HudElement element, HudElementConfig config, int x, int y,
		float partialTicks)
	{
		float scale = config.getScale();
		graphics.pose().pushPose();
		try
		{
			graphics.pose().translate(x, y, 0);
			graphics.pose().scale(scale, scale, 1);
			graphics.pose().translate(-x, -y, 0);
			element.render(graphics, x, y, partialTicks);
		}finally
		{
			graphics.pose().popPose();
		}
	}

	void toggleElementEnabled(String elementId)
	{
		HudElementConfig config = layout.get(elementId);
		if(config != null)
		{
			config.setEnabled(!config.isEnabled());
			HudElement element = elements.get(elementId);
			if(element != null)
			{
				if(config.isEnabled())
					element.onEnable(this);
				else
					element.onDisable(this);
			}
			saveLayout();
		}
	}

	public HudLayout getLayout()
	{
		return layout;
	}

	public Map<String, HudElement> getElements()
	{
		return elements;
	}

	public boolean isElementEnabled(String elementId)
	{
		HudElementConfig config = layout.get(elementId);
		return config != null && config.isEnabled();
	}

	public void registerElement(HudElement element)
	{
		elements.put(element.getId(), element);
		elementDisplayNames.put(element.getId(), element.getName());
		layout.addElement(element);
	}

	private void registerDefaultElements()
	{
		registerElement(new FpsHudElement());
		registerElement(new CoordsHudElement());
		registerElement(new PingHudElement());
		registerElement(new TpsHudElement());
		registerElement(new SpeedHudElement());
		registerElement(new ServerHudElement());
		registerElement(new ClockHudElement());
		registerElement(new ArmorHudElement());
		registerElement(new InventoryHudElement());
		registerElement(new PotionHudElement());
		registerElement(new ComboHudElement());
		registerElement(new KeystrokesHudElement());
		registerElement(new TargetHudElement());
		registerElement(new ScoreboardHudElement());
		registerElement(new MinimapHudElement());
		registerElement(new MusicIslandHudElement());
		registerElement(new MusicLyricsHudElement());
		registerElement(new CpsHudElement());
		registerElement(new CompassHudElement());
		registerElement(new DayCounterHudElement());
		registerElement(new GameModeHudElement());
		registerElement(new HealthHudElement());
		registerElement(new MemoryHudElement());
		registerElement(new NameHudElement());
		registerElement(new PlayerCounterHudElement());
		registerElement(new PlayTimeHudElement());
		registerElement(new PotionCounterHudElement());
		registerElement(new ReachHudElement());
		registerElement(new WeatherHudElement());

		registerElement(new HudElement("hacklist", "Hack List")
		{
			@Override
			public int getWidth()
			{
				return WURST.getHud().getHackList().getWidth();
			}

			@Override
			public int getHeight()
			{
				return WURST.getHud().getHackList().getHeight();
			}

			@Override
			public void render(net.minecraft.client.gui.GuiGraphics graphics,
				int x, int y, float partialTicks)
			{
				HudElementConfig config = layout.get(getId());
				boolean right = config != null && config
					.getHorizontalAlignment()
					.equals(HudElementConfig.HORIZONTAL_RIGHT);
				if(ScreenRegistry.HUD_EDITOR.isOpen())
					WURST.getHud().getHackList()
						.renderPreview(graphics, x, y, right);
				else
					WURST.getHud().getHackList().renderAt(graphics,
						partialTicks, x, y, right);
			}

			@Override
			public HudElementConfig getDefaultLayout()
			{
				return new HudElementConfig(HudElementConfig.HORIZONTAL_LEFT,
					HudElementConfig.VERTICAL_TOP, 3, 28);
			}
		});

		registerElement(new HudElement("logo", "Logo")
		{
			@Override
			public int getWidth()
			{
				return WURST.getHud().getWurstLogo().getWidth();
			}

			@Override
			public int getHeight()
			{
				return WURST.getHud().getWurstLogo().getHeight();
			}

			@Override
			public void render(net.minecraft.client.gui.GuiGraphics graphics,
				int x, int y, float partialTicks)
			{
				WURST.getHud().getWurstLogo().renderAt(graphics, x, y);
			}

			@Override
			public HudElementConfig getDefaultLayout()
			{
				return new HudElementConfig(HudElementConfig.HORIZONTAL_LEFT,
					HudElementConfig.VERTICAL_TOP, 4, 4);
			}
		});

		registerElement(new HudElement("notifications", "Notifications")
		{
			@Override
			public int getWidth()
			{
				return 180;
			}
			@Override
			public int getHeight()
			{
				return 38;
			}
			@Override
			public void render(net.minecraft.client.gui.GuiGraphics graphics,
				int x, int y, float partialTicks)
			{
				if(ScreenRegistry.HUD_EDITOR.isOpen())
					notificationRenderer.renderPreview(graphics, x, y);
			}
			@Override
			public HudElementConfig getDefaultLayout()
			{
				return new HudElementConfig(
					HudElementConfig.HORIZONTAL_RIGHT,
					HudElementConfig.VERTICAL_BOTTOM, 15, 5);
			}
		});
	}

	private void migrateLegacyLayout()
	{
		HudElementConfig hackList = layout.get("hacklist");
		HudElementConfig logo = layout.get("logo");
		if(hackList == null || logo == null || !logo.isEnabled())
			return;
		if(hackList.getHorizontalOffset() == 3
			&& hackList.getVerticalOffset() == 3)
		{
			hackList.setVerticalOffset(28);
			saveLayout();
		}
	}

	private void loadLayout()
	{
		if(!Files.isRegularFile(layoutFile))
			return;

		try
		{
			JsonElement json = JsonUtils.parseFile(layoutFile);
			if(!json.isJsonObject())
				return;

			JsonObject root = json.getAsJsonObject();
			JsonArray elems = root.getAsJsonArray("elements");
			if(elems == null)
				return;

			for(JsonElement el : elems)
			{
				JsonObject obj = el.getAsJsonObject();
				String id = obj.get("id").getAsString();
				HudElementConfig config = layout.getOrCreate(id);
				if(obj.has("enabled"))
					config.setEnabled(obj.get("enabled").getAsBoolean());
				if(obj.has("horizontalAlignment"))
					config.setHorizontalAlignment(
						obj.get("horizontalAlignment").getAsString());
				if(obj.has("verticalAlignment"))
					config.setVerticalAlignment(
						obj.get("verticalAlignment").getAsString());
				if(obj.has("horizontalOffset"))
					config.setHorizontalOffset(
						obj.get("horizontalOffset").getAsInt());
				if(obj.has("verticalOffset"))
					config.setVerticalOffset(
						obj.get("verticalOffset").getAsInt());
				if(obj.has("scale"))
					config.setScale(obj.get("scale").getAsFloat());
			}
		}catch(IOException | JsonException e)
		{
			System.err.println(
				"[HUD] Failed to load layout: " + e.getMessage());
		}
	}

	void saveLayout()
	{
		if(layoutFile == null)
			return;

		JsonObject root = new JsonObject();
		JsonArray arr = new JsonArray();
		for(Map.Entry<String, HudElementConfig> entry : layout.getElements()
			.entrySet())
		{
			JsonObject el = new JsonObject();
			el.addProperty("id", entry.getKey());
			el.addProperty("enabled", entry.getValue().isEnabled());
			el.addProperty("horizontalAlignment",
				entry.getValue().getHorizontalAlignment());
			el.addProperty("verticalAlignment",
				entry.getValue().getVerticalAlignment());
			el.addProperty("horizontalOffset",
				entry.getValue().getHorizontalOffset());
			el.addProperty("verticalOffset",
				entry.getValue().getVerticalOffset());
			el.addProperty("scale", entry.getValue().getScale());
			arr.add(el);
		}
		root.add("elements", arr);

		try
		{
			JsonUtils.toJson(root, layoutFile);
		}catch(IOException | JsonException e)
		{
			System.err.println(
				"[HUD] Failed to save layout: " + e.getMessage());
		}
	}
}
