/*
 * Copyright (c) 2014-2025 Wurst-Imperium and contributors.
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 */
package net.wurstclient.hacks;

import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.phys.AABB;
import net.wurstclient.Category;
import net.wurstclient.SearchTags;
import net.wurstclient.hack.Hack;
import net.wurstclient.mixinterface.IKeyBinding;
import net.wurstclient.settings.CheckboxSetting;
import net.wurstclient.settings.SliderSetting;
import net.wurstclient.settings.SliderSetting.ValueDisplay;

@SearchTags({"safe walk", "SneakSafety", "sneak safety", "SpeedBridgeHelper",
	"speed bridge helper"})
public final class SafeWalkHack extends Hack
{
	private final CheckboxSetting sneak =
		new CheckboxSetting("Sneak at edges", "Visibly sneak at edges.", false);
	
	private final SliderSetting edgeDistance = new SliderSetting(
		"Sneak edge distance",
		"How close SafeWalk will let you get to the edge before sneaking.\n\n"
			+ "This setting is only used when \"Sneak at edges\" is enabled.",
		0.05, 0.05, 0.25, 0.001, ValueDisplay.DECIMAL.withSuffix("m"));

	private final CheckboxSetting onlyGround = new CheckboxSetting(
		"Only on ground", "Only clips movement while standing on a block.", true);

	private final CheckboxSetting whileJumping = new CheckboxSetting(
		"While jumping", "Keeps edge clipping active while jump is held.", false);
	
	private boolean sneaking;
	
	public SafeWalkHack()
	{
		super("SafeWalk");
		setCategory(Category.MOVEMENT);
		addSetting(sneak);
		addSetting(edgeDistance);
		addSetting(onlyGround);
		addSetting(whileJumping);
	}
	
	@Override
	protected void onEnable()
	{
		WURST.getHax().parkourHack.setEnabled(false);
		sneaking = false;
	}
	
	@Override
	protected void onDisable()
	{
		if(sneaking)
			setSneaking(false);
	}
	
	public void onClipAtLedge(boolean clipping)
	{
		LocalPlayer player = MC.player;
		
		if(!shouldClipEdges() || !sneak.isChecked())
		{
			if(sneaking)
				setSneaking(false);
			
			return;
		}
		
		AABB box = player.getBoundingBox();
		AABB adjustedBox = box.expandTowards(0, -player.maxUpStep, 0)
			.inflate(-edgeDistance.getValue(), 0, -edgeDistance.getValue());
		
		if(MC.level.noCollision(player, adjustedBox))
			clipping = true;
		
		setSneaking(clipping);
	}

	public boolean shouldClipEdges()
	{
		LocalPlayer player = MC.player;
		return isEnabled() && player != null && !player.isSpectator()
			&& !player.getAbilities().flying && !player.isFallFlying()
			&& (!onlyGround.isChecked() || player.onGround())
			&& (whileJumping.isChecked() || !player.input.jumping);
	}
	
	private void setSneaking(boolean sneaking)
	{
		IKeyBinding sneakKey = IKeyBinding.get(MC.options.keyShift);
		
		if(sneaking)
			sneakKey.setPressed(true);
		else
			sneakKey.resetPressedState();
		
		this.sneaking = sneaking;
	}
	
	// See ClientPlayerEntityMixin
}
