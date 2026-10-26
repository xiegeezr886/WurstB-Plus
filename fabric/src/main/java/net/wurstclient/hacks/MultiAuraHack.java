/*
 * Copyright (c) 2026 WurstB+ Plus contributors
 *
 * This source code is subject to the terms of the GNU Affero General Public
 * License, version 3. If a copy of the AGPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/agpl-3.0.txt
 */
package net.wurstclient.hacks;

import java.util.List;

import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.wurstclient.Category;
import net.wurstclient.SearchTags;
import net.wurstclient.events.UpdateListener;
import net.wurstclient.hack.Hack;
import net.wurstclient.hack.HackConflictGroup;
import net.wurstclient.hacks.aura.OpenAura;
import net.wurstclient.settings.AttackSpeedSliderSetting;
import net.wurstclient.settings.CheckboxSetting;
import net.wurstclient.settings.PauseAttackOnContainersSetting;
import net.wurstclient.settings.SliderSetting;
import net.wurstclient.settings.SliderSetting.ValueDisplay;
import net.wurstclient.settings.SwingHandSetting;
import net.wurstclient.settings.SwingHandSetting.SwingHand;
import net.wurstclient.util.Rotation;

/**
 * Multi-target aura.
 *
 * <p>Behaviour matches Wurst's original MultiAura: every attack tick it hits
 * every attackable entity in range (FOV-limited, through walls), sending a
 * look packet to each target before attacking and swinging once. The target
 * search and rotation math are built on the OpenOpal port in
 * {@link OpenAura}.
 */
@SearchTags({"multi aura", "ForceField", "force field"})
public final class MultiAuraHack extends Hack implements UpdateListener
{
	private final SliderSetting range =
		new SliderSetting("Range", 5, 1, 6, 0.05, ValueDisplay.DECIMAL);

	private final AttackSpeedSliderSetting speed =
		new AttackSpeedSliderSetting();

	private final SliderSetting fov = new SliderSetting("FOV", 360, 30, 360,
		10, ValueDisplay.DEGREES);

	private final SwingHandSetting swingHand = new SwingHandSetting(
		SwingHandSetting.genericCombatDescription(this), SwingHand.CLIENT);

	private final PauseAttackOnContainersSetting pauseOnContainers =
		new PauseAttackOnContainersSetting(false);

	private final CheckboxSetting targetPlayers = new CheckboxSetting(
		"Target players", "Attacks other players.", true);
	private final CheckboxSetting targetHostile = new CheckboxSetting(
		"Target hostile", "Attacks hostile mobs.", true);
	private final CheckboxSetting targetPassive = new CheckboxSetting(
		"Target passive", "Attacks passive animals.", true);
	private final CheckboxSetting targetFriendly = new CheckboxSetting(
		"Target friendly",
		"Attacks other living entities (villagers, golems).", true);

	private Entity currentTarget;

	public MultiAuraHack()
	{
		super("MultiAura");
		setCategory(Category.COMBAT);
		addConflictGroup(HackConflictGroup.COMBAT_TARGETING);

		addSetting(range);
		addSetting(speed);
		addSetting(fov);
		addSetting(swingHand);
		addSetting(pauseOnContainers);
		addSetting(targetPlayers);
		addSetting(targetHostile);
		addSetting(targetPassive);
		addSetting(targetFriendly);
	}

	@Override
	protected void onEnable()
	{
		WURST.getHax().killauraHack.setEnabled(false);
		currentTarget = null;
		speed.resetTimer();
		EVENTS.add(UpdateListener.class, this);
	}

	@Override
	protected void onDisable()
	{
		EVENTS.remove(UpdateListener.class, this);
		currentTarget = null;
	}

	@Override
	public void onUpdate()
	{
		if(MC.player == null || MC.level == null)
			return;

		speed.updateTimer();
		if(!speed.isTimeToAttack())
			return;

		if(pauseOnContainers.shouldPause())
			return;

		// Wurst's MultiAura uses the total FOV angle, so convert it to the
		// half-angle expected by the OpenOpal targeting code.
		OpenAura.Config config = new OpenAura.Config(range.getValue(),
			fov.getValue() / 2.0D, targetFlags(), true);
		List<OpenAura.Target> targets = OpenAura.collect(config);
		if(targets.isEmpty())
		{
			currentTarget = null;
			return;
		}

		currentTarget = targets.get(0).entity;

		for(OpenAura.Target target : targets)
		{
			if(!target.entity.isAlive())
				continue;

			Rotation rotation = OpenAura.rotationTo(
				MC.player.getEyePosition(),
				target.entity.getBoundingBox().getCenter());
			rotation.sendPlayerLookPacket();
			MC.gameMode.attack(MC.player, target.entity);
		}

		swingHand.swing(InteractionHand.MAIN_HAND);
		speed.resetTimer();
	}

	public Entity getCurrentTarget()
	{
		return currentTarget;
	}

	public boolean shouldRenderFakeBlock()
	{
		return false;
	}

	public InteractionHand getFakeBlockingHand()
	{
		return null;
	}

	private int targetFlags()
	{
		int flags = 0;
		if(targetPlayers.isChecked())
			flags |= OpenAura.TARGET_PLAYERS;
		if(targetHostile.isChecked())
			flags |= OpenAura.TARGET_HOSTILE;
		if(targetPassive.isChecked())
			flags |= OpenAura.TARGET_PASSIVE;
		if(targetFriendly.isChecked())
			flags |= OpenAura.TARGET_FRIENDLY;
		return flags;
	}
}
