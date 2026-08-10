package net.wurstclient.hud2.elements;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.IdentityHashMap;
import java.util.Locale;
import java.util.Map;

import org.lwjgl.glfw.GLFW;

import net.minecraft.client.KeyMapping;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.wurstclient.WurstClient;
import net.wurstclient.clickgui2.FlatRenderer;
import net.wurstclient.events.MouseButtonListener;
import net.wurstclient.hud2.HudElement;
import net.wurstclient.hud2.HudLayout.HudElementConfig;
import net.wurstclient.hud2.HudManager;
import net.wurstclient.gui.visual.VisualTheme;

public final class KeystrokesHudElement extends HudElement
	implements MouseButtonListener
{
	private static final int KEY_SIZE = 21;
	private static final int HALF_HEIGHT = 15;
	private static final int GAP = 2;
	private static final int WIDTH = KEY_SIZE * 3 + GAP * 2;
	private static final int HEIGHT = KEY_SIZE * 2 + HALF_HEIGHT * 3 + GAP * 4;
	private static final int IDLE_BACKGROUND = VisualTheme.SURFACE_68;
	private static final int PRESSED_BACKGROUND = VisualTheme.ACCENT;
	private static final int IDLE_TEXT = VisualTheme.TEXT_DIMMED;
	private static final int PRESSED_TEXT = VisualTheme.TEXT;
	private static final int IDLE_BORDER = VisualTheme.BORDER;
	private static final int PRESSED_BORDER = VisualTheme.ACCENT_HOVER;

	private final Map<KeyMapping, Float> animation = new IdentityHashMap<>();
	private final CpsCounter leftCps = new CpsCounter();
	private final CpsCounter rightCps = new CpsCounter();
	private long lastRenderNanos;

	public KeystrokesHudElement()
	{
		super("keystrokes", "键位");
	}

	@Override
	public void onEnable(HudManager manager)
	{
		WurstClient.INSTANCE.getEventManager()
			.add(MouseButtonListener.class, this);
	}

	@Override
	public void onDisable(HudManager manager)
	{
		WurstClient.INSTANCE.getEventManager()
			.remove(MouseButtonListener.class, this);
		animation.clear();
		leftCps.clear();
		rightCps.clear();
		lastRenderNanos = 0;
	}

	@Override
	public void onMouseButton(int button, int action)
	{
		if(action != GLFW.GLFW_PRESS || WurstClient.MC.player == null
			|| WurstClient.MC.screen != null)
			return;

		if(button == GLFW.GLFW_MOUSE_BUTTON_LEFT)
			leftCps.registerClick();
		else if(button == GLFW.GLFW_MOUSE_BUTTON_RIGHT)
			rightCps.registerClick();
	}

	@Override
	public int getWidth()
	{
		return WIDTH;
	}

	@Override
	public int getHeight()
	{
		return HEIGHT;
	}

	@Override
	public boolean renderEditorPreview()
	{
		return true;
	}

	@Override
	public void render(GuiGraphics graphics, int x, int y, float partialTicks)
	{
		float delta = getFrameDelta();
		var options = WurstClient.MC.options;
		int column0 = x;
		int column1 = x + KEY_SIZE + GAP;
		int column2 = x + (KEY_SIZE + GAP) * 2;
		int rowW = y;
		int rowAsd = rowW + KEY_SIZE + GAP;
		int rowSpace = rowAsd + KEY_SIZE + GAP;
		int rowModes = rowSpace + HALF_HEIGHT + GAP;
		int rowMouse = rowModes + HALF_HEIGHT + GAP;

		drawKey(graphics, options.keyUp, column1, rowW, KEY_SIZE, KEY_SIZE,
			delta);
		drawKey(graphics, options.keyLeft, column0, rowAsd, KEY_SIZE,
			KEY_SIZE, delta);
		drawKey(graphics, options.keyDown, column1, rowAsd, KEY_SIZE,
			KEY_SIZE, delta);
		drawKey(graphics, options.keyRight, column2, rowAsd, KEY_SIZE,
			KEY_SIZE, delta);
		drawKey(graphics, options.keyJump, x, rowSpace, WIDTH, HALF_HEIGHT,
			delta);

		int halfWidth = (WIDTH - GAP) / 2;
		drawKey(graphics, options.keyShift, x, rowModes, halfWidth,
			HALF_HEIGHT, delta);
		drawKey(graphics, options.keySprint, x + halfWidth + GAP, rowModes,
			WIDTH - halfWidth - GAP, HALF_HEIGHT, delta);

		drawMouseKey(graphics, "L " + leftCps.getCps(), options.keyAttack,
			x, rowMouse, halfWidth, delta);
		drawMouseKey(graphics, "R " + rightCps.getCps(), options.keyUse,
			x + halfWidth + GAP, rowMouse, WIDTH - halfWidth - GAP, delta);
	}

	private void drawKey(GuiGraphics graphics, KeyMapping mapping, int x,
		int y, int width, int height, float delta)
	{
		drawKey(graphics, shortLabel(mapping, width), mapping, x, y, width,
			height, delta);
	}

	private void drawMouseKey(GuiGraphics graphics, String label,
		KeyMapping mapping, int x, int y, int width, float delta)
	{
		drawKey(graphics, label, mapping, x, y, width, HALF_HEIGHT, delta);
	}

	private void drawKey(GuiGraphics graphics, String label,
		KeyMapping mapping, int x, int y, int width, int height, float delta)
	{
		float progress = updateAnimation(mapping, mapping.isDown(), delta);
		int background = mixColor(IDLE_BACKGROUND, PRESSED_BACKGROUND,
			progress);
		int border = mixColor(IDLE_BORDER, PRESSED_BORDER, progress);
		int textColor = mixColor(IDLE_TEXT, PRESSED_TEXT, progress);

		FlatRenderer.fillRoundedRect(graphics, x, y, x + width, y + height,
			3, background);
		FlatRenderer.drawRoundedOutline(graphics, x, y, x + width,
			y + height, 3, border);

		Font font = WurstClient.MC.font;
		int textX = x + (width - font.width(label)) / 2;
		int textY = y + (height - font.lineHeight) / 2 + 1;
		graphics.drawString(font, label, textX, textY, textColor, false);
	}

	private String shortLabel(KeyMapping mapping, int width)
	{
		String label = mapping.getTranslatedKeyMessage().getString()
			.toUpperCase(Locale.ROOT);
		if(mapping == WurstClient.MC.options.keyJump)
			label = "SPACE";
		else if(mapping == WurstClient.MC.options.keyShift)
			label = "SNK";
		else if(mapping == WurstClient.MC.options.keySprint)
			label = "SPR";
		return WurstClient.MC.font.plainSubstrByWidth(label,
			Math.max(1, width - 4));
	}

	private float updateAnimation(KeyMapping mapping, boolean pressed,
		float delta)
	{
		float current = animation.getOrDefault(mapping, 0F);
		float target = pressed ? 1F : 0F;
		float speed = pressed ? 22F : 14F;
		float next = current + (target - current)
			* (1F - (float)Math.exp(-speed * delta));
		if(Math.abs(target - next) < 0.002F)
			next = target;
		animation.put(mapping, next);
		return next;
	}

	private float getFrameDelta()
	{
		long now = System.nanoTime();
		if(lastRenderNanos == 0)
		{
			lastRenderNanos = now;
			return 0;
		}
		float delta = Math.min(0.05F,
			(now - lastRenderNanos) / 1_000_000_000F);
		lastRenderNanos = now;
		return delta;
	}

	private static int mixColor(int from, int to, float progress)
	{
		int alpha = mixChannel(from >>> 24, to >>> 24, progress);
		int red = mixChannel(from >>> 16 & 0xFF, to >>> 16 & 0xFF,
			progress);
		int green = mixChannel(from >>> 8 & 0xFF, to >>> 8 & 0xFF,
			progress);
		int blue = mixChannel(from & 0xFF, to & 0xFF, progress);
		return alpha << 24 | red << 16 | green << 8 | blue;
	}

	private static int mixChannel(int from, int to, float progress)
	{
		return Math.round(from + (to - from) * progress);
	}

	@Override
	public HudElementConfig getDefaultLayout()
	{
		return new HudElementConfig(HudElementConfig.HORIZONTAL_RIGHT,
			HudElementConfig.VERTICAL_TOP, 6, 54);
	}

	private static final class CpsCounter
	{
		private final Deque<Long> clicks = new ArrayDeque<>();

		void registerClick()
		{
			clicks.addLast(System.nanoTime());
		}

		int getCps()
		{
			long cutoff = System.nanoTime() - 1_000_000_000L;
			while(!clicks.isEmpty() && clicks.peekFirst() < cutoff)
				clicks.removeFirst();
			return clicks.size();
		}

		void clear()
		{
			clicks.clear();
		}
	}
}
