/*
 * Copyright (c) 2026 WurstB+ Plus contributors
 *
 * This source code is subject to the terms of the GNU Affero General Public
 * License, version 3. If a copy of the AGPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/agpl-3.0.txt
 */
// Ported from Soar Client v7.1.2 (LGPL-3.0).
package net.wurstclient.hacks;

import java.awt.Desktop;
import java.net.URI;

import net.wurstclient.Category;
import net.wurstclient.SearchTags;
import net.wurstclient.events.GUIRenderListener;
import net.wurstclient.hack.Hack;
import net.wurstclient.settings.CheckboxSetting;
import net.wurstclient.settings.SliderSetting;
import net.wurstclient.settings.SliderSetting.ValueDisplay;
import net.wurstclient.settings.TextFieldSetting;
import net.wurstclient.util.ChatUtils;
import net.minecraft.client.gui.GuiGraphicsExtractor;

/**
 * Opens a URL in the operating system's browser and shows it on the HUD.
 *
 * <p>Soar embedded Chromium through MCEF. Bundling a browser engine for every
 * loader/version is not practical, so this port delegates to the system browser
 * and keeps the HUD panel.
 */
@SearchTags({"web browser", "webbrowser", "browser", "url", "website"})
public final class WebBrowserHack extends Hack implements GUIRenderListener
{
	private final TextFieldSetting url = new TextFieldSetting("URL",
		"Page opened in the system browser.", "https://www.google.com/",
		s -> s.startsWith("http://") || s.startsWith("https://"));

	private final CheckboxSetting openOnEnable = new CheckboxSetting(
		"Open on enable", "Opens the URL when the hack is enabled.", true);

	private final SliderSetting posX = new SliderSetting("Position X",
		"X position of the browser panel.", 10, 0, 2500, 1,
		ValueDisplay.INTEGER);

	private final SliderSetting posY = new SliderSetting("Position Y",
		"Y position of the browser panel.", 60, 0, 2500, 1,
		ValueDisplay.INTEGER);

	public WebBrowserHack()
	{
		super("WebBrowser");
		setCategory(Category.OTHER);
		addSetting(url);
		addSetting(openOnEnable);
		addSetting(posX);
		addSetting(posY);
	}

	@Override
	protected void onEnable()
	{
		EVENTS.add(GUIRenderListener.class, this);
		if(openOnEnable.isChecked())
			open();
	}

	@Override
	protected void onDisable()
	{
		EVENTS.remove(GUIRenderListener.class, this);
	}

	private void open()
	{
		try
		{
			if(!Desktop.isDesktopSupported()
				|| !Desktop.getDesktop().isSupported(Desktop.Action.BROWSE))
				throw new IllegalStateException("desktop browser unavailable");

			Desktop.getDesktop().browse(new URI(url.getValue()));
			ChatUtils.message("Opened in system browser: " + url.getValue());
		}catch(Exception e)
		{
			ChatUtils.error("Could not open the system browser: "
				+ e.getMessage());
		}
	}

	@Override
	public void onRenderGUI(GuiGraphicsExtractor context, float partialTicks)
	{
		if(uiHidden())
			return;

		int left = (int)posX.getValue();
		int top = (int)posY.getValue();
		context.fill(left, top, left + 230, top + 30, 0x90000000);
		text(context, "WebBrowser (system browser)", left + 5, top + 5,
			0xFFFFFFFF, true);
		text(context, url.getValue(), left + 5, top + 16, 0xFFB0B0B0, true);
	}

	private int argb(java.awt.Color color, int alpha)
	{
		int a = Math.max(0, Math.min(255, alpha));
		return a << 24 | color.getRGB() & 0xFFFFFF;
	}

	private boolean uiHidden()
	{
		if(net.wurstclient.hud2.HackHudElement.isPreviewRendering())
			return false;

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
