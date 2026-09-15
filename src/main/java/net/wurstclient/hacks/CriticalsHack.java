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
import net.wurstclient.hacks.criticals.CriticalsMode;
import net.wurstclient.hacks.criticals.JumpCriticalsMode;
import net.wurstclient.hacks.criticals.MiniJumpCriticalsMode;
import net.wurstclient.hacks.criticals.NoGroundCriticalsMode;
import net.wurstclient.hacks.criticals.PacketCriticalsMode;
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
		Mode selectedMode = mode.getSelected();
		if(!CombatActionPolicy.canStartSpoofedCritical(state,
			selectedMode.requiresGround(), stopSprinting.isChecked()))
			return;
		if(stopSprinting.isChecked() && MC.player.isSprinting())
		{
			MC.player.connection.send(new ServerboundPlayerCommandPacket(
				MC.player, Action.STOP_SPRINTING));
			MC.player.setSprinting(false);
		}

		// 具体打法交给模式类（见 net.wurstclient.hacks.criticals）：
		// 返回 false 表示这次不打暴击，跳过粒子。
		if(!selectedMode.doCriticals(this))
			return;

		if(particles.isChecked())
			MC.player.crit(target);
	}

	private CriticalState getState()
	{
		float attackStrength = onlyReady.isChecked()
			? MC.player.getAttackStrengthScale(0.5F) : 1;
		return new CriticalState(MC.player.onGround(),
			MC.player.isInWaterOrBubble() || MC.player.isInLava(),
			MC.player.onClimbable(), MC.player.isPassenger(),
			MC.player.getAbilities().flying, MC.player.isFallFlying(),
			MC.player.isNoGravity(), MC.player.isHandsBusy(),
			MC.player.hasEffect(MobEffects.BLINDNESS),
			MC.player.hasEffect(MobEffects.LEVITATION),
			MC.player.hasEffect(MobEffects.SLOW_FALLING), attackStrength,
			MC.player.isSprinting());
	}

	/** 供模式类读取当前选中的发包方案。 */
	public PacketProfile getPacketProfile()
	{
		return packetProfile.getSelected();
	}

	/** 供 Mini jump 模式读取跳跃高度。 */
	public double getJumpHeight()
	{
		return jumpHeight.getValue();
	}

	/** 供模式类发包：把玩家位置按 offset 抬一下。 */
	public void sendPacketProfile(PacketProfile profile)
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

	/** 供模式类发包：把玩家位置按 offset 抬一下。 */
	public void sendOffset(double offset, boolean onGround)
	{
		MC.player.connection.send(new Pos(MC.player.getX(),
			MC.player.getY() + offset, MC.player.getZ(), onGround));
	}

	private enum Mode
	{
		PACKET("Packet", new PacketCriticalsMode()),
		NO_GROUND("NoGround", new NoGroundCriticalsMode()),
		MINI_JUMP("Mini jump", new MiniJumpCriticalsMode()),
		JUMP("Jump", new JumpCriticalsMode());

		private final String name;
		private final CriticalsMode impl;

		Mode(String name, CriticalsMode impl)
		{
			this.name = name;
			this.impl = impl;
		}

		public boolean requiresGround()
		{
			return impl.requiresGround();
		}

		public boolean doCriticals(CriticalsHack hack)
		{
			return impl.doCriticals(hack);
		}

		@Override
		public String toString()
		{
			return name;
		}
	}

	/** 发包方案（供模式类使用，因此是 public 的嵌套枚举）。 */
	public enum PacketProfile
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
