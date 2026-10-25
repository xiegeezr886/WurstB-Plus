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
import java.util.ArrayDeque;

import org.lwjgl.glfw.GLFW;

import net.wurstclient.Category;
import net.wurstclient.SearchTags;
import net.wurstclient.events.GUIRenderListener;
import net.wurstclient.events.MouseButtonPressListener;
import net.wurstclient.events.UpdateListener;
import net.wurstclient.hack.Hack;
import net.wurstclient.settings.CheckboxSetting;
import net.wurstclient.settings.ColorSetting;
import net.wurstclient.settings.SliderSetting;
import net.wurstclient.settings.SliderSetting.ValueDisplay;
import net.wurstclient.util.render.GuiGraphicsExtractor;

/**
 * HUD overlay showing left and right clicks per second.
 *
 * <p>Simplification: Soar stored press timestamps in array lists and cleared
 * them every tick. This port uses deques and the same one-second window through
 * the existing update and mouse button listeners.
 */
@SearchTags({"cps", "cps display", "clicks per second", "left click",
	"right click", "click speed"})
public final class CpsDisplayHack extends Hack
	implements MouseButtonPressListener, UpdateListener, GUIRenderListener
{
	private static final long WINDOW_MILLIS = 1000;
	private static final int PADDING = 4;

	private final CheckboxSetting showRight = new CheckboxSetting("Show right",
		"Also shows right clicks per second.", true);

	private final SliderSetting scale = new SliderSetting("Scale",
		"Scale of the CPS text.", 1.5, 0.5, 5, 0.1, ValueDisplay.DECIMAL);

	private final SliderSetting posX = new SliderSetting("Position X",
		"X position of the overlay.", 500, 0, 2500, 1, ValueDisplay.INTEGER);

	private final SliderSetting posY = new SliderSetting("Position Y",
		"Y position of the overlay.", 300, 0, 2500, 1, ValueDisplay.INTEGER);

	private final CheckboxSetting background = new CheckboxSetting(
		"Show background", "Draws a colored box behind the text.", true);

	private final ColorSetting bgColor = new ColorSetting("Background color",
		"Color of the background box.", new Color(0, 0, 0, 150));

	private final ColorSetting textColor = new ColorSetting("Text color",
		"Color of the CPS text.", Color.WHITE);

	private final ArrayDeque<Long> leftClicks = new ArrayDeque<>();
	private final ArrayDeque<Long> rightClicks = new ArrayDeque<>();

	public CpsDisplayHack()
	{
		super("CpsDisplay");
		setCategory(Category.RENDER);
		addSetting(showRight);
		addSetting(scale);
		addSetting(posX);
		addSetting(posY);
		addSetting(background);
		addSetting(bgColor);
		addSetting(textColor);
		bgColor.visibleWhen(background::isChecked);
	}

	@Override
	protected void onEnable()
	{
		leftClicks.clear();
		rightClicks.clear();

		EVENTS.add(MouseButtonPressListener.class, this);
		EVENTS.add(UpdateListener.class, this);
		EVENTS.add(GUIRenderListener.class, this);
	}

	@Override
	protected void onDisable()
	{
		EVENTS.remove(MouseButtonPressListener.class, this);
		EVENTS.remove(UpdateListener.class, this);
		EVENTS.remove(GUIRenderListener.class, this);

		leftClicks.clear();
		rightClicks.clear();
	}

	@Override
	public void onMouseButtonPress(MouseButtonPressEvent event)
	{
		if(event.getAction() != GLFW.GLFW_PRESS)
			return;

		if(MC.screen != null || MC.player == null)
			return;

		long now = System.currentTimeMillis();

		if(event.getButton() == GLFW.GLFW_MOUSE_BUTTON_LEFT)
			leftClicks.addLast(now);
		else if(event.getButton() == GLFW.GLFW_MOUSE_BUTTON_RIGHT)
			rightClicks.addLast(now);
	}

	@Override
	public void onUpdate()
	{
		long cutoff = System.currentTimeMillis() - WINDOW_MILLIS;

		while(!leftClicks.isEmpty() && leftClicks.peekFirst() < cutoff)
			leftClicks.removeFirst();

		while(!rightClicks.isEmpty() && rightClicks.peekFirst() < cutoff)
			rightClicks.removeFirst();
	}

	@Override
	public void onRenderGUI(GuiGraphicsExtractor context, float partialTicks)
	{
		if(MC.player == null)
			return;

		String text = showRight.isChecked()
			? leftClicks.size() + " | " + rightClicks.size() + " CPS"
			: leftClicks.size() + " CPS";

		int width = MC.font.width(text);
		int height = MC.font.lineHeight;

		var pose = context.pose();
		pose.pushMatrix();
		pose.translate((float)posX.getValue(), (float)posY.getValue());
		pose.scale(scale.getValueF(), scale.getValueF());

		if(background.isChecked())
			context.fill(-PADDING, -PADDING, width + PADDING,
				height + PADDING, bgColor.getColor().getRGB());

		context.text(MC.font, text, 0, 0, textColor.getColorI(), true);

		pose.popMatrix();
	}
}
