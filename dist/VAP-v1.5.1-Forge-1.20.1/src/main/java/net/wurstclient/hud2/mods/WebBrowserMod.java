/*
 * Copyright (c) 2026 WurstB+ Plus contributors
 *
 * This source code is subject to the terms of the GNU Affero General Public
 * License, version 3. If a copy of the AGPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/agpl-3.0.txt
 */
// Ported from Soar Client v7.1.2 (LGPL-3.0).
package net.wurstclient.hud2.mods;

import java.awt.Desktop;
import java.net.URI;

import net.wurstclient.hud2.HUDMod;
import net.wurstclient.hud2.nanovg.HudFonts;
import net.wurstclient.settings.CheckboxSetting;
import net.wurstclient.settings.TextFieldSetting;
import net.wurstclient.util.ChatUtils;

public final class WebBrowserMod extends HUDMod
{
	private final TextFieldSetting url = new TextFieldSetting("URL",
		"Page opened in the system browser.", "https://www.google.com/",
		s -> s.startsWith("http://") || s.startsWith("https://"));
	private final CheckboxSetting openOnEnable = new CheckboxSetting(
		"Open on enable", "Opens the URL when the module is enabled.", true);

	public WebBrowserMod()
	{
		super("WebBrowser", "Web Browser",
			"Opens a URL in the system browser.");
		setTranslationKey("web_browser");
		setDefaultPosition(350, 450);
		addSetting(url);
		addSetting(openOnEnable);
	}

	@Override
	protected void onEnable()
	{
		if(openOnEnable.isChecked() && MC.player != null && MC.gui != null)
			open();
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
	protected void draw()
	{
		drawBackground(230, 30);
		drawText("Web Browser (system)", 6, 5, 9, HudFonts.DEMIBOLD);
		drawText(url.getValue(), 6, 17, 8, HudFonts.REGULAR,
			getFontColor(200));
		setSize(230, 30);
	}
}
