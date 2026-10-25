/*
 * Copyright (c) 2026 WurstB+ Plus contributors
 *
 * This source code is subject to the terms of the GNU Affero General Public
 * License, version 3. If a copy of the AGPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/agpl-3.0.txt
 */
// Ported from Soar Client v7.1.2 (LGPL-3.0).
package net.wurstclient.hacks;

import java.awt.Color;

import net.wurstclient.Category;
import net.wurstclient.SearchTags;
import net.wurstclient.clickgui2.FlatRenderer;
import net.wurstclient.events.GUIRenderListener;
import net.wurstclient.hack.Hack;
import net.wurstclient.settings.CheckboxSetting;
import net.wurstclient.settings.ColorSetting;
import net.wurstclient.settings.SliderSetting;
import net.wurstclient.settings.SliderSetting.ValueDisplay;
import net.minecraft.client.gui.GuiGraphics;

/**
 * HUD overlay showing the left and right mouse button state.
 *
 * <p>Simplification: Soar's MouseStrokes module visualised player head
 * rotation. This port follows the Wurst-style request and shows the actual
 * LMB/RMB button state instead.
 */
@SearchTags({"mouse strokes", "mouse", "lmb", "rmb", "left click",
	"right click", "cps"})
public final class MouseStrokesHack extends Hack implements GUIRenderListener
{
	private static final int BUTTON_SIZE = 28;
	private static final int GAP = 4;
	private static final int WIDTH = BUTTON_SIZE * 2 + GAP;

	private final SliderSetting scale = new SliderSetting("Scale",
		"Scale of the mouse strokes overlay.", 1.5, 0.5, 5, 0.1,
		ValueDisplay.DECIMAL);

	private final SliderSetting posX = new SliderSetting("Position X",
		"X position of the overlay.", 100, 0, 2500, 1, ValueDisplay.INTEGER);

	private final SliderSetting posY = new SliderSetting("Position Y",
		"Y position of the overlay.", 180, 0, 2500, 1, ValueDisplay.INTEGER);

	private final CheckboxSetting background = new CheckboxSetting(
		"Show background", "Draws a colored box behind the buttons.", true);

	private final ColorSetting bgColor = new ColorSetting("Background color",
		"Color of the background box.", new Color(0, 0, 0, 150));

	private final ColorSetting pressedColor = new ColorSetting(
		"Pressed color", "Color of a button while it is pressed.",
		new Color(0, 99, 102, 224));

	private final ColorSetting textColor = new ColorSetting("Text color",
		"Color of the LMB/RMB labels.", new Color(255, 255, 255));

	public MouseStrokesHack()
	{
		super("MouseStrokes");
		setCategory(Category.RENDER);
		addSetting(scale);
		addSetting(posX);
		addSetting(posY);
		addSetting(background);
		addSetting(bgColor);
		addSetting(pressedColor);
		addSetting(textColor);
		bgColor.visibleWhen(background::isChecked);
	}

	@Override
	protected void onEnable()
	{
		EVENTS.add(GUIRenderListener.class, this);
	}

	@Override
	protected void onDisable()
	{
		EVENTS.remove(GUIRenderListener.class, this);
	}

	@Override
	public void onRenderGUI(GuiGraphics context, float partialTicks)
	{
		if(MC.player == null)
			return;

		boolean inGame = MC.screen == null;

		var pose = context.pose();
		pose.pushPose();
		pose.translate((float)posX.getValue(), (float)posY.getValue(), 0F);
		pose.scale(scale.getValueF(), scale.getValueF(), 1F);

		if(background.isChecked())
			context.fill(-3, -3, WIDTH + 3, BUTTON_SIZE + 3,
				bgColor.getColor().getRGB());

		drawButton(context, "LMB", inGame && MC.options.keyAttack.isDown(), 0,
			0);
		drawButton(context, "RMB", inGame && MC.options.keyUse.isDown(),
			BUTTON_SIZE + GAP, 0);

		pose.popPose();
	}

	private void drawButton(GuiGraphics context, String text,
		boolean pressed, int x, int y)
	{
		int fill = pressed ? pressedColor.getColor().getRGB()
			: 0xB0070A0F;
		FlatRenderer.fillRoundedRect(context, x, y, x + BUTTON_SIZE,
			y + BUTTON_SIZE, 3, fill);
		FlatRenderer.drawRoundedOutline(context, x, y, x + BUTTON_SIZE,
			y + BUTTON_SIZE, 3, pressed ? 0xB000888C : 0x28FFFFFF);

		int textX = x + (BUTTON_SIZE - MC.font.width(text)) / 2;
		int textY = y + (BUTTON_SIZE - MC.font.lineHeight) / 2 + 1;
		context.drawString(MC.font, text, textX, textY,
			textColor.getColor().getRGB(), false);
	}
}
