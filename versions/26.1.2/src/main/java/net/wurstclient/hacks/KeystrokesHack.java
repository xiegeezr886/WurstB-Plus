/*
 * Copyright (c) 2026 WurstB+ Plus contributors
 *
 * This source code is subject to the terms of the GNU Affero General Public
 * License, version 3. If a copy of the AGPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/agpl-3.0.txt
 */
// Ported from Soar Client v7.1.2 (LGPL-3.0).
package net.wurstclient.hacks;

import java.util.Locale;

import net.minecraft.client.KeyMapping;
import net.wurstclient.Category;
import net.wurstclient.SearchTags;
import net.wurstclient.clickgui2.FlatRenderer;
import net.wurstclient.events.GUIRenderListener;
import net.wurstclient.hack.Hack;
import net.wurstclient.settings.CheckboxSetting;
import net.wurstclient.settings.SliderSetting;
import net.wurstclient.settings.SliderSetting.ValueDisplay;
import net.minecraft.client.gui.GuiGraphicsExtractor;

/**
 * HUD overlay showing the movement keys, space, shift and mouse buttons.
 *
 * <p>Simplification: Soar drew easing animations with NanoVG. This port uses
 * the flat renderer already available in WurstB+ Plus and shows presses
 * instantly.
 */
@SearchTags({"keystrokes", "keys", "wasd", "key display", "keypresses",
	"cps"})
public final class KeystrokesHack extends Hack implements GUIRenderListener
{
	private static final int KEY_SIZE = 21;
	private static final int GAP = 2;
	private static final int WIDTH = KEY_SIZE * 3 + GAP * 2;
	private static final int HALF = (WIDTH - GAP) / 2;
	private static final int SMALL = 12;
	private static final int IDLE_BACKGROUND = 0xB0070A0F;
	private static final int PRESSED_BACKGROUND = 0xE0006366;
	private static final int IDLE_BORDER = 0x28FFFFFF;
	private static final int PRESSED_BORDER = 0xB000888C;
	private static final int IDLE_TEXT = 0xFFE4E8ED;
	private static final int PRESSED_TEXT = 0xFFFFFFFF;

	private final SliderSetting scale = new SliderSetting("Scale",
		"Scale of the keystrokes overlay.", 1.5, 0.5, 5, 0.1,
		ValueDisplay.DECIMAL);

	private final SliderSetting posX = new SliderSetting("Position X",
		"X position of the overlay.", 100, 0, 2500, 1, ValueDisplay.INTEGER);

	private final SliderSetting posY = new SliderSetting("Position Y",
		"Y position of the overlay.", 100, 0, 2500, 1, ValueDisplay.INTEGER);

	private final CheckboxSetting showSpace = new CheckboxSetting("Show space",
		"Shows a wide space bar below WASD.", true);

	private final CheckboxSetting showShift = new CheckboxSetting("Show shift",
		"Shows the sneak/shift key.", true);

	private final CheckboxSetting showMouse = new CheckboxSetting("Show mouse",
		"Shows the left and right mouse buttons.", true);

	public KeystrokesHack()
	{
		super("Keystrokes");
		setCategory(Category.RENDER);
		addSetting(scale);
		addSetting(posX);
		addSetting(posY);
		addSetting(showSpace);
		addSetting(showShift);
		addSetting(showMouse);
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
	public void onRenderGUI(GuiGraphicsExtractor context, float partialTicks)
	{
		if(MC.player == null)
			return;

		var pose = context.pose();
		pose.pushMatrix();
		pose.translate((float)posX.getValue(), (float)posY.getValue());
		pose.scale(scale.getValueF(), scale.getValueF());

		boolean inGame = MC.screen == null;

		// W
		drawKey(context, label(MC.options.keyUp, "W", KEY_SIZE),
			inGame && MC.options.keyUp.isDown(), KEY_SIZE + GAP, 0, KEY_SIZE,
			KEY_SIZE);

		// A S D
		int row = KEY_SIZE + GAP;
		drawKey(context, label(MC.options.keyLeft, "A", KEY_SIZE),
			inGame && MC.options.keyLeft.isDown(), 0, row, KEY_SIZE, KEY_SIZE);
		drawKey(context, label(MC.options.keyDown, "S", KEY_SIZE),
			inGame && MC.options.keyDown.isDown(), KEY_SIZE + GAP, row,
			KEY_SIZE, KEY_SIZE);
		drawKey(context, label(MC.options.keyRight, "D", KEY_SIZE),
			inGame && MC.options.keyRight.isDown(), (KEY_SIZE + GAP) * 2, row,
			KEY_SIZE, KEY_SIZE);

		int smallRow = row + KEY_SIZE + GAP;
		if(showSpace.isChecked())
		{
			drawKey(context, label(MC.options.keyJump, "SPACE", WIDTH),
				inGame && MC.options.keyJump.isDown(), 0, smallRow, WIDTH,
				SMALL);
			smallRow += SMALL + GAP;
		}

		if(showShift.isChecked())
		{
			drawKey(context, label(MC.options.keyShift, "SHIFT", WIDTH),
				inGame && MC.options.keyShift.isDown(), 0, smallRow, WIDTH,
				SMALL);
			smallRow += SMALL + GAP;
		}

		if(showMouse.isChecked())
		{
			drawKey(context, "LMB", inGame && MC.options.keyAttack.isDown(), 0,
				smallRow, HALF, SMALL);
			drawKey(context, "RMB", inGame && MC.options.keyUse.isDown(),
				HALF + GAP, smallRow, WIDTH - HALF - GAP, SMALL);
		}

		pose.popMatrix();
	}

	private void drawKey(GuiGraphicsExtractor context, String text,
		boolean pressed, int x, int y, int width, int height)
	{
		FlatRenderer.fillRoundedRect(context, x, y, x + width, y + height, 3,
			pressed ? PRESSED_BACKGROUND : IDLE_BACKGROUND);
		FlatRenderer.drawRoundedOutline(context, x, y, x + width, y + height, 3,
			pressed ? PRESSED_BORDER : IDLE_BORDER);

		int textX = x + (width - MC.font.width(text)) / 2;
		int textY = y + (height - MC.font.lineHeight) / 2 + 1;
		context.text(MC.font, text, textX, textY,
			pressed ? PRESSED_TEXT : IDLE_TEXT, false);
	}

	private String label(KeyMapping mapping, String fallback, int maxWidth)
	{
		String label = mapping.getTranslatedKeyMessage().getString()
			.toUpperCase(Locale.ROOT);

		if(label.isEmpty() || label.equals("UNKNOWN"))
			label = fallback;

		return MC.font.plainSubstrByWidth(label, Math.max(1, maxWidth - 4));
	}
}
