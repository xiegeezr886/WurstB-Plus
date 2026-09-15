/*
 * Copyright (c) 2014-2025 Wurst-Imperium and contributors.
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 */
package net.wurstclient.other_features;

import net.wurstclient.SearchTags;
import net.wurstclient.other_feature.OtherFeature;
import net.wurstclient.settings.CheckboxSetting;

@SearchTags({"rotation grid", "sensitivity grid", "gcd", "mouse sensitivity",
	"silent aim", "anticheat", "anti cheat"})
public final class RotationGridOtf extends OtherFeature
{
	private final CheckboxSetting sensitivityGrid = new CheckboxSetting(
		"Sensitivity grid",
		"Snaps every rotation that gets sent to the server onto the mouse"
			+ " sensitivity grid, so the server sees the same kind of rotation"
			+ " a real mouse could have produced.\n"
			+ "Grid step = (sensitivity * 0.6 + 0.2)^3 * 8 * 0.15 degrees per"
			+ " mouse pixel, anchored at the last rotation the server saw.\n"
			+ "Off by default because it changes what the server sees.",
		false);

	public RotationGridOtf()
	{
		super("Rotation Grid",
			"Makes aim hacks' rotations look like real mouse movement.");
		addSetting(sensitivityGrid);
	}

	public boolean isSensitivityGridEnabled()
	{
		return sensitivityGrid.isChecked();
	}
}
