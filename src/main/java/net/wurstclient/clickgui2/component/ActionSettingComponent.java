package net.wurstclient.clickgui2.component;

import net.minecraft.client.gui.GuiGraphics;
import net.wurstclient.settings.Setting;

public final class ActionSettingComponent extends ValueRowComponent
{
	private final Runnable action;

	public ActionSettingComponent(Setting setting, Runnable action)
	{
		super(setting);
		this.action = action;
	}

	@Override
	protected void renderSelf(GuiGraphics graphics, int mouseX, int mouseY,
		float partialTicks)
	{
		drawLabel(graphics, mouseX, mouseY);
		drawValue(graphics, "Edit");
	}

	@Override
	protected boolean onClick(double mouseX, double mouseY, int button)
	{
		if(button != 0)
			return false;
		action.run();
		return true;
	}
}
