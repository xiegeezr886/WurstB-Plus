package net.wurstclient.hud2.elements;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Locale;

import org.lwjgl.glfw.GLFW;

import net.minecraft.client.KeyMapping;
import net.wurstclient.WurstClient;
import net.wurstclient.events.MouseButtonListener;
import net.wurstclient.hud2.HUDMod;
import net.wurstclient.hud2.HudManager;

/**
 * Keystrokes module using Soar Client v7.1.2's 28px key tiles, 6px radius and
 * animated accent press overlay.
 */
public final class KeystrokesHudElement extends HUDMod
	implements MouseButtonListener
{
	private static final int KEY = 28;
	private static final int STEP = 32;
	private static final int WIDE = 92;
	private static final int SMALL = 22;
	private static final int ROW_GAP = 4;

	private final float[] animation = new float[9];
	private final CpsCounter leftCps = new CpsCounter();
	private final CpsCounter rightCps = new CpsCounter();
	private long lastRenderNanos;

	public KeystrokesHudElement()
	{
		super("keystrokes", "Keystrokes");
		setDefaultPosition(600, 300);
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
		java.util.Arrays.fill(animation, 0);
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
	protected void draw()
	{
		float delta = getFrameDelta();
		var options = WurstClient.MC.options;
		boolean openGui = WurstClient.MC.screen != null;

		float wPressed = update(0, !openGui && options.keyUp.isDown(), delta);
		float aPressed = update(1, !openGui && options.keyLeft.isDown(), delta);
		float sPressed = update(2, !openGui && options.keyDown.isDown(), delta);
		float dPressed = update(3, !openGui && options.keyRight.isDown(), delta);
		float spacePressed = update(4,
			!openGui && options.keyJump.isDown(), delta);
		float shiftPressed = update(5,
			!openGui && options.keyShift.isDown(), delta);
		float sprintPressed = update(6,
			!openGui && options.keySprint.isDown(), delta);
		float lmbPressed = update(7,
			!openGui && options.keyAttack.isDown(), delta);
		float rmbPressed = update(8,
			!openGui && options.keyUse.isDown(), delta);

		drawKey("W", STEP, 0, KEY, KEY, wPressed);
		drawKey("A", 0, STEP, KEY, KEY, aPressed);
		drawKey("S", STEP, STEP, KEY, KEY, sPressed);
		drawKey("D", STEP * 2, STEP, KEY, KEY, dPressed);
		drawKey("SPACE", 0, STEP * 2, WIDE, SMALL, spacePressed);

		int row = STEP * 2 + SMALL + ROW_GAP;
		drawKey("SHIFT", 0, row, WIDE / 2 - 2, SMALL, shiftPressed);
		drawKey("SPRINT", WIDE / 2 + 2, row, WIDE / 2 - 2, SMALL,
			sprintPressed);

		row += SMALL + ROW_GAP;
		drawKey("L " + leftCps.getCps(), 0, row, WIDE / 2 - 2, SMALL,
			lmbPressed);
		drawKey("R " + rightCps.getCps(), WIDE / 2 + 2, row,
			WIDE / 2 - 2, SMALL, rmbPressed);

		setSize(WIDE, row + SMALL);
	}

	private void drawKey(String label, int x, int y, int width, int height,
		float pressed)
	{
		drawBackground(x, y, width, height);
		if(pressed > 0.01F)
			drawRoundedRect(x, y, width, height, 6,
				getFontColor(Math.round(120 * pressed)));
		drawCenteredText(label, x + width / 2F, y + (height - 9) / 2F, 9,
			getFontColor());
	}

	private float update(int index, boolean pressed, float delta)
	{
		float target = pressed ? 1 : 0;
		float speed = pressed ? 22 : 14;
		animation[index] += (target - animation[index])
			* (1F - (float)Math.exp(-speed * delta));
		if(Math.abs(target - animation[index]) < 0.002F)
			animation[index] = target;
		return animation[index];
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
