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
import net.wurstclient.hud2.elements.FpsHudElement;
import net.wurstclient.hud2.elements.InventoryHudElement;
import net.wurstclient.hud2.elements.KeystrokesHudElement;
import net.wurstclient.hud2.elements.MinimapHudElement;
import net.wurstclient.hud2.elements.PingHudElement;
import net.wurstclient.hud2.elements.PotionHudElement;
import net.wurstclient.hud2.elements.TargetHudElement;
import net.wurstclient.hud2.elements.ServerHudElement;
import net.wurstclient.hud2.elements.SpeedHudElement;
import net.wurstclient.hud2.elements.TpsHudElement;
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
	}

	@Override
	public void onRenderGUI(net.minecraft.client.gui.GuiGraphicsExtractor graphics,
		float partialTicks)
	{
		if(ScreenRegistry.HUD_EDITOR.isOpen())
			return;

		for(HudElement element : elements.values())
		{
			HudElementConfig config = layout.get(element.getId());
			if(config == null || !config.isEnabled()
				|| element.getId().equals("notifications"))
				continue;
			int x = getElementX(config, graphics.guiWidth(), element.getWidth());
			int y = getElementY(config, graphics.guiHeight(), element.getHeight());
			element.render(graphics, x, y, partialTicks);
		}
		notificationRenderer.onRenderGUI(graphics, partialTicks);
	}

	public int getElementX(HudElementConfig config, int screenWidth,
		int elementWidth)
	{
		if(config.getHorizontalAlignment()
			.equals(HudElementConfig.HORIZONTAL_RIGHT))
			return screenWidth - elementWidth - config.getHorizontalOffset();
		return config.getHorizontalOffset();
	}

	public int getElementY(HudElementConfig config, int screenHeight,
		int elementHeight)
	{
		if(config.getVerticalAlignment().equals(HudElementConfig.VERTICAL_BOTTOM))
			return screenHeight - elementHeight - config.getVerticalOffset();
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
		registerElement(new MinimapHudElement());

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
			public void render(net.minecraft.client.gui.GuiGraphicsExtractor graphics,
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
			public void render(net.minecraft.client.gui.GuiGraphicsExtractor graphics,
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
			public void render(net.minecraft.client.gui.GuiGraphicsExtractor graphics,
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
			}
		}catch(IOException | JsonException e)
		{
			System.err.println(
				"[HUD] Failed to load layout: " + e.getMessage());
		}
	}

	private void saveLayout()
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
