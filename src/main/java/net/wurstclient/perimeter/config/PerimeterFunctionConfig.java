/*
 * Copyright (c) 2014-2025 Wurst-Imperium and contributors.
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 */
package net.wurstclient.perimeter.config;

/**
 * Per-function switches. Field names and defaults match the reference mod.
 */
public final class PerimeterFunctionConfig
{
	public boolean collectDrops = true;
	public boolean unload = true;
	public boolean eat = true;
	public boolean durabilityRecovery = true;
	public boolean crossDimensionRepair = true;
	public boolean resupply = true;
	public boolean elytraNavigation = true;
	public boolean sleep = false;
}
