/*
 * Copyright (c) 2026 WurstB+ Plus contributors
 *
 * This source code is subject to the terms of the GNU Affero General Public
 * License, version 3. If a copy of the AGPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/agpl-3.0.txt
 */
// Ported from Soar Client v7.1.2 (LGPL-3.0).
package net.wurstclient.hud2.mods;

import java.io.File;

import net.wurstclient.hud2.HUDMod;
import net.wurstclient.hud2.nanovg.HudFonts;
import net.wurstclient.settings.TextFieldSetting;

public final class ImageDisplayMod extends HUDMod
{
	private final TextFieldSetting path = new TextFieldSetting("Image path",
		"Absolute path to a PNG/JPG image.", "",
		s -> s.isEmpty() || new File(s).isFile());

	public ImageDisplayMod()
	{
		super("ImageDisplay", "Image Display",
			"Shows a local image on the HUD.");
		setTranslationKey("image_display");
		setDefaultPosition(350, 670);
		addSetting(path);
	}

	@Override
	protected void draw()
	{
		String value = path.getValue();
		File file = value.isEmpty() ? null : new File(value);
		if(file == null || !file.isFile())
		{
			drawBackground(180, 28);
			drawText("No image selected", 6, 9, 9, HudFonts.REGULAR,
				getFontColor(200));
			setSize(180, 28);
			return;
		}

		drawBackground(168, 128);
		drawRoundedImage(file, 4, 4, 160, 120, 5);
		setSize(168, 128);
	}
}
