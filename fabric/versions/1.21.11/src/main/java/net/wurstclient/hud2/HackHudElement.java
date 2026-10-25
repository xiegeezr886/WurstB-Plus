package net.wurstclient.hud2;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import net.wurstclient.WurstClient;
import net.wurstclient.events.GUIRenderListener;
import net.wurstclient.hack.Hack;
import net.wurstclient.hack.HackList;
import net.wurstclient.settings.Setting;
import net.wurstclient.settings.SliderSetting;
import net.wurstclient.util.render.GuiGraphicsExtractor;

/**
 * Adapts a Wurst hack that draws its own HUD into a Soar v7 style
 * {@link HUDMod}, so it can be dragged and zoomed in the Soar HUD editor.
 */
public final class HackHudElement extends HUDMod
{
	private static final WurstClient WURST = WurstClient.INSTANCE;
	private static final Set<GUIRenderListener> MANAGED =
		Collections.newSetFromMap(new IdentityHashMap<>());
	private static final int PANEL_PADDING = 4;
	private static boolean previewRendering;

	private final Hack hack;
	private final GUIRenderListener listener;
	private final SliderSetting positionX;
	private final SliderSetting positionY;

	private HackHudElement(Hack hack, GUIRenderListener listener,
		SliderSetting positionX, SliderSetting positionY, int index)
	{
		super("hack." + hack.getName().toLowerCase(Locale.ROOT),
			hack.getDisplayName());
		this.hack = hack;
		this.listener = listener;
		this.positionX = positionX;
		this.positionY = positionY;
		setSize(defaultWidth(hack) + PANEL_PADDING * 2,
			defaultHeight(hack) + PANEL_PADDING * 2);
		setDefaultPosition(3 + index % 5 * 170, 100 + index / 5 * 44);
		setDefaultEnabled(false);
		MANAGED.add(listener);
	}

	public static List<HackHudElement> createAll()
	{
		List<HackHudElement> elements = new ArrayList<>();
		HackList hax = WURST.getHax();
		if(hax == null)
			return elements;

		for(Hack hack : hax.getAllHax())
		{
			if(!(hack instanceof GUIRenderListener listener))
				continue;

			SliderSetting x = findSlider(hack, "Position X");
			SliderSetting y = findSlider(hack, "Position Y");
			if(x == null || y == null)
				continue;

			elements.add(new HackHudElement(hack, listener, x, y,
				elements.size()));
		}

		elements.sort(Comparator.comparing(e -> e.hack.getName()));
		return elements;
	}

	private static SliderSetting findSlider(Hack hack, String name)
	{
		for(Setting setting : hack.getSettings().values())
			if(setting instanceof SliderSetting slider
				&& slider.getName().equalsIgnoreCase(name))
				return slider;
		return null;
	}

	public static boolean isSuppressed(GUIRenderListener listener)
	{
		return MANAGED.contains(listener);
	}

	public static void clearManaged()
	{
		MANAGED.clear();
	}

	public static boolean isPreviewRendering()
	{
		return previewRendering;
	}

	@Override
	public void onEnable(HudManager manager)
	{
		hack.setEnabled(true);
	}

	@Override
	public void onDisable(HudManager manager)
	{
		if(!manager.isStopping())
			hack.setEnabled(false);
	}

	public void syncFromHack()
	{
		if(isEnabled() != hack.isEnabled())
			setEnabledSilently(hack.isEnabled());
	}

	@Override
	protected void draw()
	{
		int originX = (int)Math.round(positionX.getValue());
		int originY = (int)Math.round(positionY.getValue());

		drawBackground(getUnscaledWidth(), getUnscaledHeight());
		if(!hack.isEnabled())
			return;

		previewRendering = isEditing();
		graphics.pose().pushMatrix();
		graphics.pose().translate(
			getX() + PANEL_PADDING * getScale(),
			getY() + PANEL_PADDING * getScale());
		graphics.pose().scale(getScale(), getScale());
		graphics.pose().translate(-originX, -originY);
		try
		{
			listener.onRenderGUI(graphics, partialTicks);
		}finally
		{
			graphics.pose().popMatrix();
			previewRendering = false;
		}
	}

	public Hack getHack()
	{
		return hack;
	}

	private static int defaultWidth(Hack hack)
	{
		return switch(hack.getName())
		{
			case "Keystrokes" -> 120;
			case "MouseStrokes" -> 90;
			case "Scoreboard" -> 220;
			case "TabEditor" -> 240;
			case "Skin3D" -> 120;
			case "Rearview" -> 200;
			case "WebBrowser" -> 420;
			case "ImageDisplay" -> 180;
			case "InventoryDisplay" -> 200;
			case "PotionStatus" -> 220;
			case "BossHealth" -> 220;
			case "UHCOverlay" -> 180;
			case "Waypoint" -> 180;
			case "PlayerDisplay" -> 180;
			case "HorseStats" -> 180;
			case "TntTimer" -> 200;
			case "AppleSkin" -> 120;
			case "MusicInfo" -> 180;
			case "CpsDisplay" -> 160;
			case "ComboCounter" -> 180;
			case "PitchDisplay" -> 150;
			case "YawDisplay" -> 150;
			case "ArmorStatus" -> 170;
			case "PotionCounter" -> 170;
			case "TargetInfo" -> 180;
			default -> 220;
		};
	}

	private static int defaultHeight(Hack hack)
	{
		return switch(hack.getName())
		{
			case "Keystrokes" -> 120;
			case "MouseStrokes" -> 70;
			case "Scoreboard" -> 140;
			case "TabEditor" -> 180;
			case "Skin3D" -> 190;
			case "Rearview" -> 140;
			case "WebBrowser" -> 260;
			case "ImageDisplay" -> 120;
			case "InventoryDisplay" -> 120;
			case "PotionStatus" -> 180;
			case "BossHealth" -> 120;
			case "UHCOverlay" -> 140;
			case "Waypoint" -> 100;
			case "PlayerDisplay" -> 220;
			case "HorseStats" -> 100;
			case "TntTimer" -> 120;
			case "AppleSkin" -> 50;
			case "MusicInfo" -> 70;
			default -> 22;
		};
	}
}
