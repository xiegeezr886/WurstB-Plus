/*
 * Port of Rise 6.1.30's ModuleComponent for Wurst Feature instances.
 */
package net.wurstclient.clickgui2;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.wurstclient.Feature;
import net.wurstclient.clickgui2.theme.FlatTheme;

final class RiseModuleComponent
{
	static final int HEIGHT = 38;
	private static final float NAME_SCALE = 1F;
	private static final float DESCRIPTION_SCALE = 0.74F;

	private final Feature feature;
	private final RiseAnimation hover =
		new RiseAnimation(RiseAnimation.Easing.LINEAR, 50);
	private final RiseAnimation opening =
		new RiseAnimation(RiseAnimation.Easing.EASE_OUT_EXPO, 200);
	private final RiseAnimation settingOpacity =
		new RiseAnimation(RiseAnimation.Easing.LINEAR, 200);
	private NavigatorSettingsPanel settingsPanel;
	private boolean expanded;
	private boolean mouseDown;
	private int renderedHeight = HEIGHT;

	RiseModuleComponent(Feature feature)
	{
		this.feature = feature;
	}

	Feature feature()
	{
		return feature;
	}

	int updateHeight(int width)
	{
		int preferredHeight = 0;
		if(expanded)
			ensureSettingsPanel();
		if(settingsPanel != null)
			preferredHeight = settingsPanel.getPreferredContentHeight(
				Math.max(1, width - 12));
		int openingDuration = Math.min(
			Math.max(1, HEIGHT + preferredHeight) * 3, 450);
		opening.setDuration(openingDuration);
		renderedHeight = HEIGHT
			+ Math.round(opening.run(expanded ? preferredHeight : 0));
		settingOpacity.setDuration(Math.max(1,
			expanded ? openingDuration / 2 : openingDuration / 3));
		settingOpacity.run(expanded ? 1 : 0);
		return renderedHeight;
	}

	void render(GuiGraphics graphics, int x, int y, int width, int mouseX,
		int mouseY, boolean searchResult, int accent, FlatTheme theme,
		float partialTicks, boolean clip)
	{
		updateHeight(width);
		boolean hovered = mouseX >= x && mouseX < x + width && mouseY >= y
			&& mouseY < y + renderedHeight;
		int hoverAlpha = Math.round(hover.run(hovered ? mouseDown ? 35 : 20 : 0));
		FlatUiRenderer.fill(graphics, x, y, x + width, y + renderedHeight, 6,
			RiseColors.OVERLAY.argb());
		if(hoverAlpha > 0)
			FlatUiRenderer.fill(graphics, x, y, x + width, y + renderedHeight,
				6, RiseColors.isRiseMode() ? hoverAlpha << 24
					: hoverAlpha << 24 | PvPUtilsTheme.HOVER_PILL & 0xFFFFFF);

		Font font = Minecraft.getInstance().font;
		String category = searchResult && feature.getCategory() != null
			? "(" + feature.getCategory().getName() + ")" : "";

		int available = Math.max(30, width - 14);
		String name = RiseFont.trim(font, feature.getDisplayName(),
			Math.round(available / NAME_SCALE));
		String description = RiseFont.trim(font,
			FeatureMenuSupport.getOneLineDescription(feature),
			Math.round((width - 20) / DESCRIPTION_SCALE));
		if(!category.isEmpty())
			drawText(graphics, font, category,
				x + 10 + Math.round(RiseFont.width(font, name) * NAME_SCALE),
				y + 10, RiseColors.isRiseMode() ? 0x40FFFFFF : 0xAA888888,
				DESCRIPTION_SCALE);
		drawText(graphics, font, name, x + 6, y + 8,
			feature.isEnabled() ? accent : RiseColors.isRiseMode()
				? 0xC8FFFFFF : 0xFF9A9A9A,
			NAME_SCALE);
		drawText(graphics, font, description, x + 6, y + 25,
			RiseColors.isRiseMode() ? 0x46FFFFFF : PvPUtilsTheme.TEXT_MUTED,
			DESCRIPTION_SCALE);

		int settingsHeight = Math.max(0, renderedHeight - HEIGHT);
		if(settingsPanel != null && settingsHeight > 0)
		{
			if(clip)
				graphics.enableScissor(x + 2, y + HEIGHT, x + width - 2,
					y + HEIGHT + settingsHeight);
			settingsPanel.layoutInline(x + 6, y + HEIGHT, width - 12,
				settingsHeight);
			graphics.flush();
			float opacity = settingOpacity.run(expanded ? 1 : 0);
			float[] previousColor = RenderSystem.getShaderColor().clone();
			RenderSystem.setShaderColor(previousColor[0], previousColor[1],
				previousColor[2], previousColor[3] * opacity);
			settingsPanel.renderContent(graphics, mouseX, mouseY, partialTicks,
				theme, clip);
			graphics.flush();
			RenderSystem.setShaderColor(previousColor[0], previousColor[1],
				previousColor[2], previousColor[3]);
			if(clip)
				graphics.disableScissor();
		}
	}

	boolean mouseClicked(double mouseX, double mouseY, int button, int x, int y,
		int width)
	{
		if(mouseX >= x && mouseX < x + width && mouseY >= y
			&& mouseY < y + HEIGHT - 3)
		{
			mouseDown = true;
			if(button == 0)
				FeatureMenuSupport.runPrimaryAction(feature);
			else if(button == 1 && !feature.getSettings().isEmpty())
			{
				expanded = !expanded;
				if(expanded)
					ensureSettingsPanel();
				else if(settingsPanel != null)
					settingsPanel.closePopups();
			}
			return true;
		}

		if(expanded && settingsPanel != null && renderedHeight > HEIGHT
			&& settingsPanel.mouseClicked(mouseX, mouseY, button))
			return true;
		return false;
	}

	int height()
	{
		return renderedHeight;
	}

	void dispose()
	{
		if(settingsPanel != null)
		{
			settingsPanel.dispose();
			settingsPanel = null;
		}
	}

	void closePopups()
	{
		if(settingsPanel != null)
			settingsPanel.closePopups();
	}

	boolean charTyped(char codePoint)
	{
		return expanded && settingsPanel != null
			&& settingsPanel.charTyped(codePoint);
	}

	boolean keyPressed(int keyCode, int modifiers)
	{
		return expanded && settingsPanel != null
			&& settingsPanel.keyPressed(keyCode, modifiers);
	}

	void mouseReleased(int button)
	{
		mouseDown = false;
		if(settingsPanel != null)
			settingsPanel.mouseReleased(button);
	}

	boolean hasActiveText()
	{
		return expanded && settingsPanel != null
			&& settingsPanel.hasActiveText();
	}

	private void ensureSettingsPanel()
	{
		if(settingsPanel == null)
			settingsPanel = new NavigatorSettingsPanel(feature);
	}

	private static void drawText(GuiGraphics graphics, Font font, String text,
		int x, int y, int color, float scale)
	{
		graphics.pose().pushPose();
		graphics.pose().translate(x, y, 0);
		graphics.pose().scale(scale, scale, 1);
		RiseFont.draw(graphics, font, text, 0, 0, color);
		graphics.pose().popPose();
	}
}
