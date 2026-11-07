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
import net.wurstclient.hud2.HudLayout.HudElementConfig;
import net.wurstclient.settings.Setting;
import net.wurstclient.settings.SliderSetting;
import net.wurstclient.util.ScreenRegistry;
import net.minecraft.client.gui.GuiGraphicsExtractor;

/**
 * Adapts a {@link Hack} that draws its own HUD into a {@link HudElement}, so
 * that it can be toggled, dragged and aligned through the HUD editor.
 *
 * <p>
 * The hack keeps rendering itself, but its configured {@code Position X/Y}
 * sliders are treated as its origin. The adapter translates the GUI matrix so
 * that the hack appears at the position chosen in the HUD editor, and the
 * hack's own {@link GUIRenderListener} registration is suppressed to prevent
 * duplicate rendering.
 */
public final class HackHudElement extends HudElement
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
	private final SliderSetting scale;
	private int index;
	private int measuredWidth;
	private int measuredHeight;
	private int contentOffsetX;
	private int contentOffsetY;
	private int contentWidth;
	private int contentHeight;
	private boolean panelVisible;

	private HackHudElement(Hack hack, GUIRenderListener listener,
		SliderSetting positionX, SliderSetting positionY, SliderSetting scale,
		int index)
	{
		super("hack." + hack.getName().toLowerCase(Locale.ROOT),
			hack.getDisplayName());
		this.hack = hack;
		this.listener = listener;
		this.positionX = positionX;
		this.positionY = positionY;
		this.scale = scale;
		this.index = index;
		float scaleValue = scale == null ? 1 : (float)scale.getValue();
		contentWidth = Math.max(1,
			Math.round(defaultWidth(hack) * scaleValue));
		contentHeight = Math.max(1,
			Math.round(defaultHeight(hack) * scaleValue));
		measuredWidth = contentWidth + PANEL_PADDING * 2;
		measuredHeight = contentHeight + PANEL_PADDING * 2;
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
				findSlider(hack, "Scale"), elements.size()));
		}

		elements.sort(Comparator.comparing(e -> e.hack.getName()));
		for(int i = 0; i < elements.size(); i++)
			elements.get(i).index = i;
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
	public boolean renderEditorPreview()
	{
		return true;
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

	@Override
	public int getWidth()
	{
		return measuredWidth;
	}

	@Override
	public int getHeight()
	{
		return measuredHeight;
	}

	@Override
	public void render(GuiGraphicsExtractor graphics, int x, int y,
		float partialTicks)
	{
		int originX = (int)Math.round(positionX.getValue());
		int originY = (int)Math.round(positionY.getValue());

		if(panelVisible)
			SoarHudStyle.panel(graphics, x, y, x + measuredWidth,
				y + measuredHeight);

		boolean preview = ScreenRegistry.HUD_EDITOR.isOpen();
		previewRendering = preview;

		var pose = graphics.pose();
		pose.pushMatrix();
		pose.translate(x + PANEL_PADDING - originX - contentOffsetX,
			y + PANEL_PADDING - originY - contentOffsetY);
		try
		{
			listener.onRenderGUI(graphics, partialTicks);
		}finally
		{
			pose.popMatrix();
			previewRendering = false;
		}

		panelVisible = true;
	}

	/**
	 * Modules either draw at their {@code Position X/Y} directly or translate
	 * the pose by it first. Both are valid, so pick whichever offset keeps the
	 * measured content close to the configured origin.
	 */
	private static int relativeOffset(int minimum, int origin)
	{
		return Math.abs(minimum - origin) <= 24 ? minimum - origin
			: minimum;
	}

	@Override
	public HudElementConfig getDefaultLayout()
	{
		HudElementConfig config = new HudElementConfig(
			HudElementConfig.HORIZONTAL_LEFT, HudElementConfig.VERTICAL_TOP,
			3 + index % 5 * 110, 240 + index / 5 * 48);
		config.setEnabled(false);
		return config;
	}

	public Hack getHack()
	{
		return hack;
	}

	private static int defaultWidth(Hack hack)
	{
		return switch(hack.getName())
		{
			case "Keystrokes" -> 110;
			case "MouseStrokes" -> 80;
			case "Scoreboard" -> 180;
			case "TabEditor" -> 220;
			case "Skin3D" -> 100;
			case "Rearview" -> 180;
			case "WebBrowser" -> 320;
			case "ImageDisplay" -> 160;
			case "InventoryDisplay" -> 180;
			case "PotionStatus" -> 150;
			case "BossHealth" -> 200;
			case "UHCOverlay" -> 160;
			case "Waypoint" -> 160;
			case "PlayerDisplay" -> 160;
			case "HorseStats" -> 160;
			case "TntTimer" -> 180;
			case "AppleSkin" -> 100;
			case "MusicInfo" -> 160;
			default -> 130;
		};
	}

	private static int defaultHeight(Hack hack)
	{
		return switch(hack.getName())
		{
			case "Keystrokes" -> 100;
			case "MouseStrokes" -> 60;
			case "Scoreboard" -> 120;
			case "TabEditor" -> 160;
			case "Skin3D" -> 170;
			case "Rearview" -> 120;
			case "WebBrowser" -> 200;
			case "ImageDisplay" -> 100;
			case "InventoryDisplay" -> 100;
			case "PotionStatus" -> 100;
			case "BossHealth" -> 100;
			case "UHCOverlay" -> 120;
			case "Waypoint" -> 80;
			case "PlayerDisplay" -> 200;
			case "HorseStats" -> 80;
			case "TntTimer" -> 100;
			case "AppleSkin" -> 40;
			case "MusicInfo" -> 60;
			default -> 18;
		};
	}
}
