package net.wurstclient.hud2.elements;

import java.util.ArrayDeque;

import org.lwjgl.glfw.GLFW;

import net.wurstclient.WurstClient;
import net.wurstclient.events.MouseButtonListener;
import net.wurstclient.hud2.HudLayout.HudElementConfig;
import net.wurstclient.hud2.HudManager;

public final class CpsHudElement extends SoarTextHudElement
	implements MouseButtonListener
{
	private final ArrayDeque<Long> leftClicks = new ArrayDeque<>();
	private final ArrayDeque<Long> rightClicks = new ArrayDeque<>();

	public CpsHudElement()
	{
		super("cps", "CPS", HudElementConfig.HORIZONTAL_LEFT, 110, 8);
	}

	@Override
	public void onEnable(HudManager manager)
	{
		WurstClient.INSTANCE.getEventManager().add(MouseButtonListener.class,
			this);
	}

	@Override
	public void onDisable(HudManager manager)
	{
		WurstClient.INSTANCE.getEventManager().remove(MouseButtonListener.class,
			this);
		leftClicks.clear();
		rightClicks.clear();
	}

	@Override
	public void onMouseButton(int button, int action)
	{
		if(action != GLFW.GLFW_PRESS)
			return;
		long now = System.nanoTime();
		if(button == GLFW.GLFW_MOUSE_BUTTON_LEFT)
			leftClicks.addLast(now);
		else if(button == GLFW.GLFW_MOUSE_BUTTON_RIGHT)
			rightClicks.addLast(now);
	}

	@Override
	protected String getText()
	{
		long cutoff = System.nanoTime() - 1_000_000_000L;
		prune(leftClicks, cutoff);
		prune(rightClicks, cutoff);
		return leftClicks.size() + " | " + rightClicks.size() + " CPS";
	}

	private void prune(ArrayDeque<Long> clicks, long cutoff)
	{
		while(!clicks.isEmpty() && clicks.peekFirst() < cutoff)
			clicks.removeFirst();
	}
}
