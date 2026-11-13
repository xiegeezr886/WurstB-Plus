/*
 * Copyright (c) 2014-2025 Wurst-Imperium and contributors.
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 */
package net.wurstclient.hacks;

import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.effect.MobEffects;
import net.wurstclient.Category;
import net.wurstclient.SearchTags;
import net.wurstclient.hack.Hack;
import net.wurstclient.settings.CheckboxSetting;
import net.wurstclient.settings.EnumSetting;

@SearchTags({"auto sprint"})
public final class AutoSprintHack extends Hack
{
	private final CheckboxSetting allDirections =
		new CheckboxSetting("Omnidirectional Sprint",
			"Sprint in all directions, not just forward.", false);
	
	private final CheckboxSetting hungry = new CheckboxSetting("Hungry Sprint",
		"Sprint even on low hunger.", false);

	private final CheckboxSetting ignoreCollision = new CheckboxSetting(
		"Ignore collision", "Keeps sprinting while touching a wall.", false);

	private final CheckboxSetting whileUsingItems = new CheckboxSetting(
		"While using items", "Keeps sprinting while using an item.", false);

	private final CheckboxSetting whileSneaking = new CheckboxSetting(
		"While sneaking", "Keeps sprinting while sneaking.", false);

	private final CheckboxSetting ignoreBlindness = new CheckboxSetting(
		"Ignore blindness", "Allows sprinting while blinded.", false);

	private LocalPlayer sprintOwner;
	private final EnumSetting<SprintMode> sprintMode = new EnumSetting<>("Sprint mode",
		"Wurst 使用单独设置；Strict 使用常规疾跑条件；Rage 允许全方向和低饥饿疾跑。",
		SprintMode.values(), SprintMode.WURST);
	private final CheckboxSetting stopInWater = new CheckboxSetting("Stop in water", true);
	private final CheckboxSetting stationary = new CheckboxSetting("Sprint while stationary",
		"Rage 模式下，停止移动时也保持疾跑状态。", false);
	
	public AutoSprintHack()
	{
		super("AutoSprint");
		setCategory(Category.MOVEMENT);
		addSetting(allDirections);
		addSetting(hungry);
		addSetting(ignoreCollision);
		addSetting(whileUsingItems);
		addSetting(whileSneaking);
		addSetting(ignoreBlindness);
		addSetting(sprintMode);
		addSetting(stopInWater);
		addSetting(stationary);
	}
	
	@Override
	protected void onEnable()
	{
		sprintOwner = null;
	}
	
	@Override
	protected void onDisable()
	{
		stopOwnedSprint();
	}
	
	public void applySprint()
	{
		LocalPlayer player = MC.player;
		if(sprintOwner != null && (sprintOwner != player
			|| MC.options.keySprint.isDown()))
			sprintOwner = null;
		if(!canSprint(player))
		{
			stopOwnedSprint();
			return;
		}

		if(!player.isSprinting())
		{
			player.setSprinting(true);
			sprintOwner = player;
		}
	}

	private boolean canSprint(LocalPlayer player)
	{
		if(!isEnabled() || player == null || player.isPassenger()
			|| player.isFallFlying())
			return false;
		if(stopInWater.isChecked() && (player.isInWaterOrBubble() || player.isUnderWater()))
			return false;
		if(sprintMode.getSelected() == SprintMode.RAGE)
			return stationary.isChecked() || MC.options.keyUp.isDown()
				|| MC.options.keyDown.isDown() || MC.options.keyLeft.isDown() || MC.options.keyRight.isDown();
		if(sprintMode.getSelected() == SprintMode.STRICT)
			return MC.options.keyUp.isDown() && !player.horizontalCollision
				&& !player.isShiftKeyDown() && !player.isUsingItem()
				&& !player.hasEffect(MobEffects.BLINDNESS)
				&& (player.getAbilities().mayfly || player.getFoodData().getFoodLevel() > 6);
		if(!ignoreCollision.isChecked() && player.horizontalCollision
			|| !whileSneaking.isChecked() && player.isShiftKeyDown()
			|| !whileUsingItems.isChecked() && player.isUsingItem()
			|| !ignoreBlindness.isChecked()
				&& player.hasEffect(MobEffects.BLINDNESS))
			return false;
		if(!hungry.isChecked() && !player.getAbilities().mayfly
			&& player.getFoodData().getFoodLevel() <= 6)
			return false;
		if(!allDirections.isChecked() && player.input.forwardImpulse <= 0)
			return false;
		return player.input.getMoveVector().length() > 1.0E-5F;
	}

	private void stopOwnedSprint()
	{
		if(sprintOwner != null && sprintOwner == MC.player
			&& !MC.options.keySprint.isDown())
			sprintOwner.setSprinting(false);
		sprintOwner = null;
	}
	
	public boolean shouldOmniSprint()
	{
		return isEnabled() && (sprintMode.getSelected() == SprintMode.RAGE
			|| sprintMode.getSelected() == SprintMode.WURST && allDirections.isChecked());
	}
	
	public boolean shouldSprintHungry()
	{
		return isEnabled() && (sprintMode.getSelected() == SprintMode.RAGE
			|| sprintMode.getSelected() == SprintMode.WURST && hungry.isChecked());
	}

	private enum SprintMode {WURST, STRICT, RAGE}
}
