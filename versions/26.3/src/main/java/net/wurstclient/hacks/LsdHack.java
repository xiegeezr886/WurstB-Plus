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
		
		// The post-effect path this hack used was removed back in 26.1.2, and 26.3
		// removed GameRenderer.currentPostEffect()/clearPostEffect() outright -
		// post effects are now driven by Player#getActivePostEffects(). This hack
		// stays disabled rather than being rewired to the new API, since restoring
		// the feature is a separate decision from porting it. EFFECT and the
		// shader asset are kept for whoever does that.
		setEnabled(false);
	}
}
