package net.wurstclient.altmanager.screens;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;
import net.wurstclient.gui.CustomRenderedButton;
import net.wurstclient.hud2.nanovg.NanoVGUI;
import net.wurstclient.gui.IslandButtonStyle;

/** MoonMenuButton material, adapted to the existing Minecraft input dispatch. */
final class MoonAccountButton extends Button implements CustomRenderedButton
{
	static final int ACCENT = 0xFFB5BEF0;

	MoonAccountButton(int x, int y, int width, String text, OnPress action)
	{
		super(x, y, width, 20, Component.literal(text), action,
			DEFAULT_NARRATION);
	}

	@Override
	protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY,
		float delta) {}

	void draw(int mouseX, int mouseY)
	{
		if(!visible) return;
		boolean hover = isMouseOver(mouseX, mouseY) || isFocused();
		IslandButtonStyle.draw(getX(), getY(), width, height, active,
			hover ? 1 : 0, false, ACCENT);
		String label = NanoVGUI.plainSubstrByWidth(getMessage().getString(),
			width - 8, 8.25F);
		NanoVGUI.centeredText(label, getX() + width / 2F,
			getY() + (height - NanoVGUI.height(8.25F)) / 2F + .5F,
			active ? 0xFFF2F2F8 : 0xFF77798C, 8.25F);
	}
}
