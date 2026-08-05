/*
 * This file contains a Forge/Mojmap adaptation of LiquidBounce's Criticals
 * module and packet profiles.
 *
 * Copyright (c) 2015-2026 CCBlueX
 * Copyright (c) 2025 Penguin
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 */
package net.wurstclient.hacks;

import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket.Pos;
import net.minecraft.network.protocol.game.ServerboundPlayerCommandPacket;
import net.minecraft.network.protocol.game.ServerboundPlayerCommandPacket.Action;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.wurstclient.Category;
import net.wurstclient.SearchTags;
import net.wurstclient.events.PlayerAttacksEntityListener;
import net.wurstclient.hack.Hack;
import net.wurstclient.settings.CheckboxSetting;
import net.wurstclient.settings.EnumSetting;
import net.wurstclient.settings.SliderSetting;
import net.wurstclient.settings.SliderSetting.ValueDisplay;
import net.wurstclient.util.CombatActionPolicy;
import net.wurstclient.util.CombatActionPolicy.CriticalState;

@SearchTags({"Crits", "critical"})
public final class CriticalsHack extends Hack
	implements PlayerAttacksEntityListener
{
	private final EnumSetting<Mode> mode = new EnumSetting<>("Mode",
		Mode.values(), Mode.PACKET);

	private final EnumSetting<PacketProfile> packetProfile = new EnumSetting<>(
		"Packet profile", PacketProfile.values(), PacketProfile.NO_CHEAT_PLUS)
			.visibleWhen(() -> mode.getSelected() == Mode.PACKET);

	private final SliderSetting jumpHeight = new SliderSetting("Jump height",
		"Vertical motion used by Mini jump mode.", 0.1, 0.1, 0.42, 0.01,
		ValueDisplay.DECIMAL).visibleWhen(() -> mode.getSelected() == Mode.MINI_JUMP);

	private final CheckboxSetting onlyReady = new CheckboxSetting(
		"Only when ready", "Requires at least 90% attack cooldown.", true);

	private final CheckboxSetting stopSprinting = new CheckboxSetting(
		"Stop sprinting", "Stops sprinting before spoofing a critical hit.",
		true);

	private final CheckboxSetting particles = new CheckboxSetting(
		"Critical particles", "Shows the vanilla critical hit particles.", true);

	public CriticalsHack()
	{
		super("Criticals");
		setCategory(Category.COMBAT);
		addSetting(mode);
		addSetting(packetProfile);
		addSetting(jumpHeight);
		addSetting(onlyReady);
		addSetting(stopSprinting);
		addSetting(particles);
	}

	@Override
	public String getRenderName()
	{
		return getName() + " [" + mode.getSelected() + "]";
	}

	@Override
	protected void onEnable()
	{
		EVENTS.add(PlayerAttacksEntityListener.class, this);
	}

	@Override
	protected void onDisable()
	{
		EVENTS.remove(PlayerAttacksEntityListener.class, this);
	}

	@Override
	public void onPlayerAttacksEntity(Entity target)
	{
		if(!(target instanceof LivingEntity) || MC.player == null)
			return;

		CriticalState state = getState();
		if(!CombatActionPolicy.canCritical(state, true,
			stopSprinting.isChecked()))
			return;
		if(stopSprinting.isChecked() && MC.player.isSprinting())
		{
			MC.player.connection.send(new ServerboundPlayerCommandPacket(
				MC.player, Action.STOP_SPRINTING));
			MC.player.setSprinting(false);
		}

		switch(mode.getSelected())
		{
			case PACKET -> sendPacketProfile(packetProfile.getSelected());
			case NO_GROUND -> sendOffset(-0.000001, false);
			case MINI_JUMP -> {
				if(!MC.player.onGround())
					return;
				MC.player.push(0, jumpHeight.getValue(), 0);
				MC.player.fallDistance = 0.1F;
				MC.player.setOnGround(false);
				sendOffset(jumpHeight.getValue(), false);
				sendOffset(0.000001, false);
			}
			case JUMP -> {
				if(!MC.player.onGround())
					return;
				MC.player.jumpFromGround();
				sendOffset(0.42, false);
				sendOffset(0.000001, false);
			}
		}

		if(particles.isChecked())
			MC.player.crit(target);
	}

	private CriticalState getState()
	{
		float attackStrength = onlyReady.isChecked()
			? MC.player.getAttackStrengthScale(0.5F) : 1;
		return new CriticalState(MC.player.onGround(),
			MC.player.isInWater() || MC.player.isSwimming() || MC.player.isInLava(),
			MC.player.onClimbable(), MC.player.isPassenger(),
			MC.player.getAbilities().flying, MC.player.isFallFlying(),
			MC.player.isNoGravity(), MC.player.isHandsBusy(),
			MC.player.hasEffect(MobEffects.BLINDNESS),
			MC.player.hasEffect(MobEffects.LEVITATION),
			MC.player.hasEffect(MobEffects.SLOW_FALLING), attackStrength,
			MC.player.isSprinting());
	}

	private void sendPacketProfile(PacketProfile profile)
	{
		switch(profile)
		{
			case VANILLA -> {
				sendOffset(0.2, false);
				sendOffset(0.01, false);
			}
			case NO_CHEAT_PLUS -> {
				sendOffset(0.11, false);
				sendOffset(0.1100013579, false);
				sendOffset(0.0000013579, false);
			}
			case FALLING -> {
				sendOffset(0.0625, false);
				sendOffset(0.0625013579, false);
				sendOffset(0.0000013579, false);
			}
			case LOW -> {
				sendOffset(1.0E-9, false);
				sendOffset(0, false);
			}
			case DOWN -> sendOffset(-1.0E-9, false);
			case GRIM -> sendOffset(-0.000001, false);
		}
	}

	private void sendOffset(double offset, boolean onGround)
	{
		MC.player.connection.send(new Pos(MC.player.getX(),
			MC.player.getY() + offset, MC.player.getZ(), onGround, false));
	}

	private enum Mode
	{
		PACKET("Packet"),
		NO_GROUND("NoGround"),
		MINI_JUMP("Mini jump"),
		JUMP("Jump");

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

	private enum PacketProfile
	{
		VANILLA("Vanilla"),
		NO_CHEAT_PLUS("NoCheatPlus"),
		FALLING("Falling"),
		LOW("Low"),
		DOWN("Down"),
		GRIM("Grim");

		private final String name;

		PacketProfile(String name)
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
