/*
 * Copyright (c) 2014-2026 Wurst-Imperium and contributors.
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 */
// Ported from Meteor Client (GPL-3.0).
package net.wurstclient.hacks;

import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.wurstclient.Category;
import net.wurstclient.SearchTags;
import net.wurstclient.events.UpdateListener;
import net.wurstclient.hack.Hack;
import net.wurstclient.settings.CheckboxSetting;
import net.wurstclient.settings.SliderSetting;
import net.wurstclient.settings.SliderSetting.ValueDisplay;

@SearchTags({"auto clicker", "autoclicker", "auto click", "CPS", "clicker"})
public final class AutoClickerHack extends Hack implements UpdateListener
{
	private final SliderSetting leftCps = new SliderSetting("Left CPS",
		"Left clicks per second. 0 disables left clicking.", 10, 0, 20, 1,
		ValueDisplay.INTEGER.withLabel(0, "off"));

	private final SliderSetting rightCps = new SliderSetting("Right CPS",
		"Right clicks per second. 0 disables right clicking.", 10, 0, 20, 1,
		ValueDisplay.INTEGER.withLabel(0, "off"));

	private final CheckboxSetting onlyWhileHeld = new CheckboxSetting(
		"Only while held",
		"Only clicks while the corresponding mouse button is held down.", true);

	private final CheckboxSetting inScreens = new CheckboxSetting(
		"While in screens",
		"Whether to click while a screen is open.\n\n"
			+ "\u00a7c\u00a7lWARNING:\u00a7r This can make menus click themselves.",
		false);

	private long lastLeftClick;
	private long lastRightClick;

	public AutoClickerHack()
	{
		super("AutoClicker");
		setCategory(Category.COMBAT);
		addSetting(leftCps);
		addSetting(rightCps);
		addSetting(onlyWhileHeld);
		addSetting(inScreens);
	}

	@Override
	protected void onEnable()
	{
		lastLeftClick = 0;
		lastRightClick = 0;
		EVENTS.add(UpdateListener.class, this);
	}

	@Override
	protected void onDisable()
	{
		EVENTS.remove(UpdateListener.class, this);
	}

	@Override
	public void onUpdate()
	{
		if(MC.player == null || MC.level == null || MC.gameMode == null)
			return;

		if(!inScreens.isChecked() && MC.gui.screen() != null)
			return;

		LocalPlayer player = MC.player;
		long now = System.currentTimeMillis();

		if(leftCps.getValue() > 0 && player.isUsingItem())
			return;

		if(leftCps.getValue() > 0
			&& (!onlyWhileHeld.isChecked() || MC.options.keyAttack.isDown())
			&& now - lastLeftClick >= 1000.0 / leftCps.getValue())
		{
			leftClick(player);
			lastLeftClick = now;
		}

		if(rightCps.getValue() > 0
			&& (!onlyWhileHeld.isChecked() || MC.options.keyUse.isDown())
			&& now - lastRightClick >= 1000.0 / rightCps.getValue())
		{
			MC.startUseItem();
			lastRightClick = now;
		}
	}

	private void leftClick(LocalPlayer player)
	{
		if(player.isHandsBusy() || MC.hitResult == null)
			return;

		switch(MC.hitResult.getType())
		{
			case ENTITY:
			MC.gameMode.attack(player,
				((EntityHitResult)MC.hitResult).getEntity());
			break;

			case BLOCK:
			BlockHitResult blockHit = (BlockHitResult)MC.hitResult;
			MC.gameMode.startDestroyBlock(blockHit.getBlockPos(),
				blockHit.getDirection());
			break;

			case MISS:
			break;
		}

		if(!player.isSpectator())
			player.swing(InteractionHand.MAIN_HAND);
	}
}
