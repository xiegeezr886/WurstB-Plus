package net.wurstclient.hud2.elements;

import net.wurstclient.hud2.HudLayout.HudElementConfig;

public final class MemoryHudElement extends SoarTextHudElement
{
	public MemoryHudElement()
	{
		super("memory", "\u5185\u5b58\u5360\u7528", HudElementConfig.HORIZONTAL_LEFT,
			110, 248);
	}

	@Override
	protected String getText()
	{
		Runtime runtime = Runtime.getRuntime();
		long used = runtime.totalMemory() - runtime.freeMemory();
		long percentage = used * 100L / Math.max(1, runtime.maxMemory());
		return "Mem: " + percentage + "%";
	}
}
