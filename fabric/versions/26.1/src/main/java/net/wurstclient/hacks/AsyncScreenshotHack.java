/*
 * Copyright (c) 2026 WurstB+ Plus contributors
 *
 * This source code is subject to the terms of the GNU Affero General Public
 * License, version 3. If a copy of the AGPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/agpl-3.0.txt
 */
// Ported from Soar Client v7.1.2 (LGPL-3.0).
package net.wurstclient.hacks;

import net.minecraft.client.Screenshot;
import net.wurstclient.Category;
import net.wurstclient.SearchTags;
import net.wurstclient.hack.Hack;
import net.wurstclient.settings.CheckboxSetting;
import net.wurstclient.util.ChatUtils;

@SearchTags({"async screenshot", "screenshot", "screenshot async", "capture",
	"screen shot"})
public final class AsyncScreenshotHack extends Hack
{
	private final CheckboxSetting showMessage = new CheckboxSetting(
		"Show message", "Shows a clickable chat message after the screenshot"
			+ " has been saved.",
		true);

	public AsyncScreenshotHack()
	{
		super("AsyncScreenshot");
		setCategory(Category.OTHER);
		addSetting(showMessage);
	}

	@Override
	protected void onEnable()
	{
		// Screenshot.grab() reads the render target on the render thread and
		// then writes the PNG on the game's I/O thread. This hack is a
		// one-shot action, so it disables itself once the capture is queued.
		MC.execute(() -> {
			Screenshot.grab(MC.gameDirectory, MC.getMainRenderTarget(),
				component -> {
					if(showMessage.isChecked() && MC.gui != null)
						MC.execute(() -> ChatUtils.component(component));
				});
			setEnabled(false);
		});
	}
}
