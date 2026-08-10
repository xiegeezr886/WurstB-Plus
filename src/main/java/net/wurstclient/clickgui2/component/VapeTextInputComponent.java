package net.wurstclient.clickgui2.component;

import net.minecraft.client.gui.GuiGraphics;
import net.wurstclient.clickgui2.FlatRenderer;
import net.wurstclient.clickgui2.supersoft.SuperSoftTheme;
import net.wurstclient.clickgui2.supersoft.UiTween;
import net.wurstclient.settings.TextFieldSetting;

final class VapeTextInputComponent extends ValueRowComponent
	implements GuiTextInput
{
	private final TextFieldSetting textSetting;
	private String draft;
	private boolean focused;
	private final UiTween borderMotion = new UiTween(0, 150);

	VapeTextInputComponent(TextFieldSetting setting)
	{
		super(setting);
		textSetting = setting;
		draft = setting.getValue();
		height = 25;
	}

	@Override
	protected void renderSelf(GuiGraphics graphics, int mouseX, int mouseY,
		float partialTicks)
	{
		if(usesSuperSoftTheme())
		{
			renderSuperSoft(graphics);
			return;
		}
		int left = (int)x + 5;
		int top = (int)y + 10;
		int right = (int)(x + getWidth()) - 5;
		graphics.drawString(font(), textSetting.getName(), left, (int)y + 2,
			focused ? VapePalette.TEXT_HOVER : VapePalette.TEXT, false);
		graphics.fill(left, top, right, top + 13,
			focused ? VapePalette.ROW_HOVER : VapePalette.FRAME);
		graphics.fill(left, top, right, top + 1,
			focused ? VapePalette.ACCENT : VapePalette.BORDER);
		String visible = draft.isEmpty() ? "Click to set" : draft;
		graphics.drawString(font(), visible, left + 4, top + 3,
			draft.isEmpty() ? VapePalette.TEXT_HIDDEN : VapePalette.TEXT, false);
		if(focused && System.currentTimeMillis() / 500 % 2 == 0)
		{
			int cursor = left + 4 + font().width(draft);
			graphics.fill(cursor, top + 2, cursor + 1, top + 12,
				VapePalette.TEXT_HOVER);
		}
	}

	private void renderSuperSoft(GuiGraphics graphics)
	{
		int left = (int)x + 4;
		int top = (int)y + 17;
		int right = (int)(x + getWidth()) - 4;
		graphics.drawString(font(), textSetting.getName(), left, (int)y + 2,
			SuperSoftTheme.TEXT_SECONDARY, false);
		String description = font().plainSubstrByWidth(
			textSetting.getDescription(), Math.max(20, right - left));
		graphics.pose().pushPose();
		graphics.pose().translate(left, (int)y + 11, 0);
		graphics.pose().scale(0.65F, 0.65F, 1);
		graphics.drawString(font(), description, 0, 0, 0x80FFFFFF, false);
		graphics.pose().popPose();
		FlatRenderer.fillRoundedRect(graphics, left, top, right, top + 15, 4,
			SuperSoftTheme.SETTING);
		FlatRenderer.drawRoundedOutline(graphics, left, top, right, top + 15, 4,
			SuperSoftTheme.mix(0x66FFFFFF, SuperSoftTheme.TEXT,
				borderMotion.update(focused || hovered ? 1 : 0)));
		String visible = draft.isEmpty() ? "value" : draft;
		graphics.drawString(font(), visible, left + 5, top + 3,
			draft.isEmpty() ? 0x66FFFFFF : SuperSoftTheme.TEXT, false);
		if(focused && System.currentTimeMillis() / 500 % 2 == 0)
		{
			int cursor = left + 5 + font().width(draft);
			graphics.fill(cursor, top + 2, cursor + 1, top + 13,
				SuperSoftTheme.TEXT);
		}
	}

	@Override
	public double getHeight()
	{
		return usesSuperSoftTheme() ? 36 : super.getHeight();
	}

	private net.minecraft.client.gui.Font font()
	{
		return net.minecraft.client.Minecraft.getInstance().font;
	}

	@Override
	protected boolean onClick(double mouseX, double mouseY, int button)
	{
		if(button != 0)
			return false;
		focused = true;
		draft = textSetting.getValue();
		if(context() != null)
			context().beginTextInput(this);
		return true;
	}

	private VapeGuiContext context()
	{
		return currentContext();
	}

	static VapeGuiContext currentContext()
	{
		return VapeTextInputContextHolder.context;
	}

	@Override
	public void acceptChar(char codePoint)
	{
		if(focused && !Character.isISOControl(codePoint) && draft.length() < 256)
		{
			draft += codePoint;
			textSetting.setValue(draft);
		}
	}

	@Override
	public boolean acceptKey(int keyCode)
	{
		if(!focused)
			return false;
		if(keyCode == 256 || keyCode == 257)
		{
			if(keyCode == 257)
				textSetting.setValue(draft);
			focused = false;
			if(context() != null)
				context().endTextInput(this);
			return true;
		}
		if(keyCode == 259 && !draft.isEmpty())
		{
			draft = draft.substring(0, draft.length() - 1);
			textSetting.setValue(draft);
		}
		return keyCode == 259;
	}

	@Override
	public void loseFocus()
	{
		focused = false;
		if(context() != null)
			context().endTextInput(this);
	}

	private static final class VapeTextInputContextHolder
	{
		private static VapeGuiContext context;
	}

	static void setContext(VapeGuiContext context)
	{
		VapeTextInputContextHolder.context = context;
	}
}
