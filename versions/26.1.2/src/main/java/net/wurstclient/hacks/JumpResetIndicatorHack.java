/*
 * Copyright (c) 2026 WurstB+ Plus contributors
 *
 * This source code is subject to the terms of the GNU Affero General Public
 * License, version 3. If a copy of the AGPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/agpl-3.0.txt
 */
// Ported from Soar Client v7.1.2 (LGPL-3.0).
package net.wurstclient.hacks;

import net.wurstclient.Category;
import net.wurstclient.SearchTags;
import net.wurstclient.events.GUIRenderListener;
import net.wurstclient.events.UpdateListener;
import net.wurstclient.hack.Hack;
import net.wurstclient.settings.CheckboxSetting;
import net.wurstclient.settings.SliderSetting;
import net.wurstclient.settings.SliderSetting.ValueDisplay;
import net.minecraft.client.gui.GuiGraphicsExtractor;

/**
 * Shows whether the player jumped at the right tick after taking knockback.
 */
@SearchTags({"jump reset", "jumpreset", "jump reset indicator", "knockback"})
public final class JumpResetIndicatorHack extends Hack
	implements UpdateListener, GUIRenderListener
{
	private final SliderSetting maxTick = new SliderSetting("Max tick",
		"Maximum tick difference considered a jump reset.", 10, 1, 100, 1,
		ValueDisplay.INTEGER.withSuffix(" ticks"));

	private final SliderSetting posX = new SliderSetting("Position X",
		"X position of the indicator.", 30, 0, 2500, 1,
		ValueDisplay.INTEGER);

	private final SliderSetting posY = new SliderSetting("Position Y",
		"Y position of the indicator.", 120, 0, 2500, 1,
		ValueDisplay.INTEGER);

	private final CheckboxSetting background = new CheckboxSetting(
		"Background", "Draws a background behind the indicator.", true);

	private int hurtAge;
	private int jumpAge;
	private int lastHurtTime;
	private boolean wasOnGround;
	private long lastTime;

	public JumpResetIndicatorHack()
	{
		super("JumpResetIndicator");
		setCategory(Category.RENDER);
		addSetting(maxTick);
		addSetting(posX);
		addSetting(posY);
		addSetting(background);
	}

	@Override
	protected void onEnable()
	{
		hurtAge = 0;
		jumpAge = 0;
		lastTime = 0;
		if(MC.player != null)
		{
			lastHurtTime = MC.player.hurtTime;
			wasOnGround = MC.player.onGround();
		}else
		{
			lastHurtTime = 0;
			wasOnGround = true;
		}

		EVENTS.add(UpdateListener.class, this);
		EVENTS.add(GUIRenderListener.class, this);
	}

	@Override
	protected void onDisable()
	{
		EVENTS.remove(UpdateListener.class, this);
		EVENTS.remove(GUIRenderListener.class, this);
	}

	@Override
	public void onUpdate()
	{
		if(MC.player == null)
			return;

		long now = System.currentTimeMillis();
		if(MC.player.hurtTime > lastHurtTime)
		{
			hurtAge = MC.player.tickCount;
			lastTime = now;
		}
		lastHurtTime = MC.player.hurtTime;

		boolean onGround = MC.player.onGround();
		if(wasOnGround && !onGround
			&& MC.player.getDeltaMovement().y > 0.05)
		{
			jumpAge = MC.player.tickCount;
			lastTime = now;
		}
		wasOnGround = onGround;
	}

	@Override
	public void onRenderGUI(GuiGraphicsExtractor context, float partialTicks)
	{
		if(MC.player == null || uiHidden())
			return;

		String text = getStatus();
		int color = getStatusColor();
		int x = (int)posX.getValue();
		int y = (int)posY.getValue();
		int width = MC.font.width(text) + 8;

		if(background.isChecked())
			context.fill(x - 4, y - 3, x + width, y + 12, 0x90000000);

		text(context, text, x, y, color, true);
	}

	private String getStatus()
	{
		if(System.currentTimeMillis() - lastTime > 2500
			|| Math.abs(jumpAge - hurtAge) >= maxTick.getValueI())
			return "No Jump";

		if(jumpAge == hurtAge + 1)
			return "Perfect!";
		if(hurtAge + 1 < jumpAge)
			return "Late: " + (jumpAge - hurtAge + 1) + " Tick";
		return "Early: " + (hurtAge + 1 - jumpAge) + " Tick";
	}

	private int getStatusColor()
	{
		String status = getStatus();
		if(status.equals("Perfect!"))
			return 0xFF55FF55;
		if(status.startsWith("Early") || status.startsWith("Late"))
			return 0xFFFFAA00;
		return 0xFFFFFFFF;
	}

	private int argb(java.awt.Color color, int alpha)
	{
		int a = Math.max(0, Math.min(255, alpha));
		return a << 24 | color.getRGB() & 0xFFFFFF;
	}

	private boolean uiHidden()
	{
		try
		{
			Object gui = MC.getClass().getField("gui").get(MC);
			if(gui.getClass().getMethod("screen").invoke(gui) != null)
				return true;
		}catch(Exception ignored)
		{}

		try
		{
			return Boolean.TRUE.equals(MC.options.getClass()
				.getField("hideGui").get(MC.options));
		}catch(Exception ignored)
		{
			return false;
		}
	}

	private void text(GuiGraphicsExtractor context, String text, int x,
		int y, int color, boolean shadow)
	{
		context.text(MC.font, text, x, y, color, shadow);
	}

	private void centeredText(GuiGraphicsExtractor context, String text,
		int x, int y, int color)
	{
		context.centeredText(MC.font, text, x, y, color);
	}

}
