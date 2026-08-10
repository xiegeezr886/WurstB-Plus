/*
 * Copyright (c) 2015-2026 CCBlueX
 * Copyright (c) 2025-2026 Penguin
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 */
package net.wurstclient.hacks;

import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.protocol.game.ServerboundPlayerCommandPacket;
import net.minecraft.network.protocol.game.ServerboundPlayerCommandPacket.Action;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.wurstclient.Category;
import net.wurstclient.SearchTags;
import net.wurstclient.events.PlayerAttacksEntityListener;
import net.wurstclient.events.UpdateListener;
import net.wurstclient.hack.Hack;
import net.wurstclient.settings.CheckboxSetting;
import net.wurstclient.settings.EnumSetting;
import net.wurstclient.settings.SliderSetting;
import net.wurstclient.settings.SliderSetting.ValueDisplay;
import net.wurstclient.util.KnockbackBoostPolicy;
import net.wurstclient.util.KnockbackBoostPolicy.State;

@SearchTags({"super knockback", "MoreKB", "more kb", "knockback boost"})
public final class SuperKnockbackHack extends Hack
	implements PlayerAttacksEntityListener, UpdateListener
{
	private final EnumSetting<Mode> mode =
		new EnumSetting<>("Mode", Mode.values(), Mode.PACKET);
	private final SliderSetting hurtTime = new SliderSetting("Hurt time", 10, 0,
		10, 1, ValueDisplay.INTEGER);
	private final SliderSetting chance = new SliderSetting("Chance", 100, 0,
		100, 1, ValueDisplay.PERCENTAGE);
	private final CheckboxSetting onlyMoving =
		new CheckboxSetting("Only while moving", true);
	private final CheckboxSetting onlyForward =
		new CheckboxSetting("Only forward", true);
	private final CheckboxSetting onlyGround =
		new CheckboxSetting("Only on ground", false);
	private final CheckboxSetting rejectFluids =
		new CheckboxSetting("Not in fluids", true);
	private final CheckboxSetting skipCriticals = new CheckboxSetting(
		"Skip criticals",
		"Does not reset sprint for naturally airborne critical attacks.", true);
	private final SliderSetting reSprintDelay = new SliderSetting(
		"Re-sprint delay", 1, 0, 10, 1,
		ValueDisplay.INTEGER.withSuffix(" ticks")).visibleWhen(
			() -> mode.getSelected() == Mode.SPRINT_TAP);

	private final RandomSource random = RandomSource.createNewThreadLocalInstance();
	private int pendingSprintTicks = -1;
	private boolean sprintTapNeedsStop;
	private LocalPlayer sprintTapPlayer;

	public SuperKnockbackHack()
	{
		super("SuperKnockback");
		setCategory(Category.COMBAT);
		addSetting(mode);
		addSetting(hurtTime);
		addSetting(chance);
		addSetting(onlyMoving);
		addSetting(onlyForward);
		addSetting(onlyGround);
		addSetting(rejectFluids);
		addSetting(skipCriticals);
		addSetting(reSprintDelay);
	}

	@Override
	protected void onEnable()
	{
		pendingSprintTicks = -1;
		sprintTapNeedsStop = false;
		sprintTapPlayer = null;
		EVENTS.add(PlayerAttacksEntityListener.class, this);
		EVENTS.add(UpdateListener.class, this);
	}

	@Override
	protected void onDisable()
	{
		EVENTS.remove(PlayerAttacksEntityListener.class, this);
		EVENTS.remove(UpdateListener.class, this);
		restoreSprint();
		sprintTapNeedsStop = false;
		sprintTapPlayer = null;
	}

	@Override
	public void onPlayerAttacksEntity(Entity target)
	{
		if(MC.player == null || !(target instanceof LivingEntity living))
			return;

		boolean sideways = Math.abs(MC.player.input.leftImpulse) > 1.0E-5F;
		boolean moving = MC.player.input.getMoveVector().length() > 1.0E-5F;
		boolean naturalCritical = MC.player.fallDistance > 0
			&& !MC.player.onGround() && !MC.player.onClimbable()
			&& !MC.player.isInWaterOrBubble() && !MC.player.isInLava();
		boolean critical = skipCriticals.isChecked() && naturalCritical;
		State state = new State(true, living.hurtTime, moving, sideways,
			MC.player.onGround(), MC.player.isInWaterOrBubble()
				|| MC.player.isInLava(),
			MC.player.isSprinting(), critical, onlyMoving.isChecked(),
			onlyForward.isChecked(), onlyGround.isChecked(),
			rejectFluids.isChecked(), mode.getSelected() == Mode.SPRINT_TAP);
		if(!KnockbackBoostPolicy.shouldBoost(state, hurtTime.getValueI(),
			chance.getValueI(), random.nextInt(100)))
			return;

		if(mode.getSelected() == Mode.PACKET)
			sendPacketSequence();
		else
			beginSprintTap();
	}

	@Override
	public void onUpdate()
	{
		if(pendingSprintTicks < 0)
			return;
		if(MC.player == null || MC.player != sprintTapPlayer)
		{
			pendingSprintTicks = -1;
			sprintTapNeedsStop = false;
			sprintTapPlayer = null;
			return;
		}
		if(sprintTapNeedsStop)
		{
			sendSprint(Action.STOP_SPRINTING);
			MC.player.setSprinting(false);
			sprintTapNeedsStop = false;
		}
		if(pendingSprintTicks > 0)
		{
			pendingSprintTicks--;
			return;
		}
		restoreSprint();
	}

	private void sendPacketSequence()
	{
		if(MC.player.isSprinting())
			sendSprint(Action.STOP_SPRINTING);
		sendSprint(Action.START_SPRINTING);
		sendSprint(Action.STOP_SPRINTING);
		sendSprint(Action.START_SPRINTING);
		MC.player.setSprinting(true);
	}

	private void beginSprintTap()
	{
		pendingSprintTicks = reSprintDelay.getValueI();
		sprintTapNeedsStop = true;
		sprintTapPlayer = MC.player;
	}

	private void restoreSprint()
	{
		if(pendingSprintTicks >= 0 && MC.player == sprintTapPlayer
			&& MC.player.input.getMoveVector().length() > 1.0E-5F)
		{
			sendSprint(Action.START_SPRINTING);
			MC.player.setSprinting(true);
		}
		pendingSprintTicks = -1;
		sprintTapPlayer = null;
	}

	private void sendSprint(Action action)
	{
		MC.player.connection.send(
			new ServerboundPlayerCommandPacket(MC.player, action));
	}

	private enum Mode
	{
		PACKET("Packet"),
		SPRINT_TAP("Sprint tap");

		private final String name;

		Mode(String name)
		{
			this.name = name;
		}

		@Override
		public String toString()
		{
			return name;
		}
	}
}
