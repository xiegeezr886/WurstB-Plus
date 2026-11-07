$ErrorActionPreference = 'Stop'
$repo = 'C:\Users\ui863\Documents\trae_projects\VAP'
$dir = Join-Path $repo 'fabric\src\main\java\net\wurstclient\hud2\mods'
New-Item -ItemType Directory -Force -Path $dir | Out-Null

$template = @'
/*
 * Copyright (c) 2026 WurstB+ Plus contributors
 *
 * This source code is subject to the terms of the GNU Affero General Public
 * License, version 3. If a copy of the AGPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/agpl-3.0.txt
 */
// Ported from Soar Client v7.1.2 (LGPL-3.0).
package net.wurstclient.hud2.mods;

import net.wurstclient.hud2.HudIcon;
import net.wurstclient.hud2.SimpleHUDMod;
import net.wurstclient.hud2.compat.HudCompat;
import net.wurstclient.settings.CheckboxSetting;

public final class __CLASS__ extends SimpleHUDMod
{
	private final CheckboxSetting iconSetting =
		new CheckboxSetting("Icon", "Shows an icon next to the text.", true);

	public __CLASS__()
	{
		super("__ID__", "__NAME__", "__DESC__");
		setTranslationKey("__KEY__");
		setDefaultPosition(__X__, __Y__);
		addSetting(iconSetting);
	}

	@Override
	public String getText()
	{
		return __TEXT__;
	}

	@Override
	public String getIcon()
	{
		return iconSetting.isChecked() ? HudIcon.__ICON__ : null;
	}
}
'@

$specs = @(
    @{Class='FPSDisplayMod';Id='FPSDisplay';Name='FPS Display';Key='fps_display';X=10;Y=10;Icon='MONITOR';Text='" + "HudCompat.fps() + " FPS";'}
)

Write-Output 'Specs are defined inline in the caller.'
