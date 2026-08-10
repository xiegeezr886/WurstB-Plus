/*
 * Copyright (c) 2014-2025 Wurst-Imperium and contributors.
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 */
package net.wurstclient.hacks;

import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Player;
import net.wurstclient.Category;
import net.wurstclient.hack.DontSaveState;
import net.wurstclient.hack.Hack;

@DontSaveState
public final class LsdHack extends Hack
{
	private static final Identifier EFFECT = Identifier.tryBuild(
		"wurst", "shaders/post/lsd_wobble.json");

	public LsdHack()
	{
		super("LSD");
		setCategory(Category.FUN);
	}
	
	@Override
	protected void onEnable()
	{
		if(!(MC.getCameraEntity() instanceof Player))
		{
			setEnabled(false);
			return;
		}
		
		if(MC.gameRenderer.currentPostEffect() != null)
			MC.gameRenderer.clearPostEffect();
		
		// 26.1.2 removed the old loadEffect path; keep the hack disabled.
		setEnabled(false);
	}
	
	@Override
	protected void onDisable()
	{
		if(MC.gameRenderer.currentPostEffect() != null)
			MC.gameRenderer.clearPostEffect();
	}
}
