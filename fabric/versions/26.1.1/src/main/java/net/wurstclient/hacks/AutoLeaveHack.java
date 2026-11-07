/*
 * Copyright (c) 2014-2025 Wurst-Imperium and contributors.
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 */
package net.wurstclient.hacks;

import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ServerboundInteractPacket;
import net.minecraft.network.protocol.game.ClientboundSetTimePacket;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.wurstclient.Category;
import net.wurstclient.SearchTags;
import net.wurstclient.events.UpdateListener;
import net.wurstclient.events.PacketInputListener;
import net.wurstclient.events.WorldChangeListener;
import net.wurstclient.hack.Hack;
import net.wurstclient.settings.CheckboxSetting;
import net.wurstclient.settings.EnumSetting;
import net.wurstclient.settings.SliderSetting;
import net.wurstclient.settings.SliderSetting.ValueDisplay;
import net.wurstclient.util.InventoryUtils;
import net.wurstclient.util.AutoLeaveTriggers;
import net.wurstclient.util.ChatUtils;

@SearchTags({"auto leave", "AutoDisconnect", "auto disconnect", "AutoQuit",
	"auto quit"})
public final class AutoLeaveHack extends Hack
	implements UpdateListener, PacketInputListener, WorldChangeListener
{
	private final SliderSetting health = new SliderSetting("Health",
		"Leaves the server when your health reaches this value or falls below it.",
		4, 0.5, 9.5, 0.5, ValueDisplay.DECIMAL.withSuffix(" hearts"));
	
	public final EnumSetting<Mode> mode = new EnumSetting<>("Mode",
		"\u00a7lQuit\u00a7r mode just quits the game normally.\n"
			+ "Bypasses NoCheat+ but not CombatLog.\n\n"
			+ "\u00a7lChars\u00a7r mode sends a special chat message that"
			+ " causes the server to kick you.\n"
			+ "Bypasses NoCheat+ and some versions of CombatLog.\n\n"
			+ "\u00a7lSelfHurt\u00a7r mode sends the packet for attacking"
			+ " another player, but with yourself as both the attacker and the"
			+ " target, causing the server to kick you.\n"
			+ "Bypasses both CombatLog and NoCheat+.",
		Mode.values(), Mode.QUIT);
	
	private final CheckboxSetting disableAutoReconnect = new CheckboxSetting(
		"Disable AutoReconnect", "Automatically turns off AutoReconnect when"
			+ " AutoLeave makes you leave the server.",
		true);
	
	private final SliderSetting totems = new SliderSetting("Totems",
		"Won't leave the server until the number of totems you have reaches"
			+ " this value or falls below it.\n\n"
			+ "11 = always able to leave",
		11, 0, 11, 1, ValueDisplay.INTEGER.withSuffix(" totems")
			.withLabel(1, "1 totem").withLabel(11, "ignore"));

	// FO AutoLogPlus conditions, merged without changing the existing health/totem gate.
	private final CheckboxSetting lowHealth = new CheckboxSetting("Low health", true);
	private final CheckboxSetting lowY = new CheckboxSetting("Low Y", false);
	private final SliderSetting yLevel = new SliderSetting("Y threshold",
		"低于这个 Y 坐标时下线。", 256, -2048, 2048, 1, ValueDisplay.INTEGER);
	private final CheckboxSetting armorDamage = new CheckboxSetting("Armor durability", false);
	private final CheckboxSetting ignoreElytra = new CheckboxSetting("Ignore elytra", false);
	private final SliderSetting armorPercent = new SliderSetting("Armor minimum",
		"护甲剩余耐久低于这个百分比时下线。", 5, 0, 100, 1, ValueDisplay.INTEGER);
	private final CheckboxSetting portalTimeout = new CheckboxSetting("Portal timeout", false);
	private final SliderSetting portalTicks = new SliderSetting("Portal ticks",
		"连续处于传送门超过这些 tick 时下线。", 30, 1, 200, 1, ValueDisplay.INTEGER);
	private final CheckboxSetting nearPosition = new CheckboxSetting("Near position", false);
	private final SliderSetting positionX = new SliderSetting("Target X", 0, -30000000, 30000000, 1, ValueDisplay.INTEGER);
	private final SliderSetting positionZ = new SliderSetting("Target Z", 0, -30000000, 30000000, 1, ValueDisplay.INTEGER);
	private final SliderSetting positionRange = new SliderSetting("Target distance",
		"距目标方块中心的水平距离小于此值时下线。", 100, 0, 1000, 1, ValueDisplay.INTEGER);
	private final CheckboxSetting lowTotems = new CheckboxSetting("Low totems",
		"独立于生命值：背包和副手图腾不足时下线。", false);
	private final SliderSetting minimumTotems = new SliderSetting("Minimum totems", 3, 0, 37, 1, ValueDisplay.INTEGER);
	private final CheckboxSetting serverTimeout = new CheckboxSetting("Server timeout", false);
	private final SliderSetting timeoutSeconds = new SliderSetting("Timeout seconds", 10, 1, 60, 1, ValueDisplay.INTEGER);
	private final CheckboxSetting timeoutReconnect = new CheckboxSetting("Reconnect after timeout", false);
	private final SliderSetting reconnectSeconds = new SliderSetting("Reconnect delay", 60, 10, 300, 1, ValueDisplay.INTEGER);
	private final CheckboxSetting disableAfterLeave = new CheckboxSetting("Disable after leave", true);
	private volatile long lastServerTick;
	private int ticksInPortal;
	
	public AutoLeaveHack()
	{
		super("AutoLeave");
		setCategory(Category.COMBAT);
		addSetting(health);
		addSetting(mode);
		addSetting(disableAutoReconnect);
		addSetting(totems);
		addSetting(lowHealth);
		addSetting(lowY);
		addSetting(yLevel);
		addSetting(armorDamage);
		addSetting(ignoreElytra);
		addSetting(armorPercent);
		addSetting(portalTimeout);
		addSetting(portalTicks);
		addSetting(nearPosition);
		addSetting(positionX);
		addSetting(positionZ);
		addSetting(positionRange);
		addSetting(lowTotems);
		addSetting(minimumTotems);
		addSetting(serverTimeout);
		addSetting(timeoutSeconds);
		addSetting(timeoutReconnect);
		addSetting(reconnectSeconds);
		addSetting(disableAfterLeave);
	}
	
	@Override
	public String getRenderName()
	{
		if(MC.player == null || MC.player.getAbilities().instabuild)
			return getName() + " (paused)";
		
		return getName() + " [" + mode.getSelected() + "]";
	}
	
	@Override
	protected void onEnable()
	{
		resetSession();
		EVENTS.add(UpdateListener.class, this);
		EVENTS.add(PacketInputListener.class, this);
		EVENTS.add(WorldChangeListener.class, this);
	}
	
	@Override
	protected void onDisable()
	{
		EVENTS.remove(UpdateListener.class, this);
		EVENTS.remove(PacketInputListener.class, this);
		EVENTS.remove(WorldChangeListener.class, this);
		resetSession();
	}
	
	@Override
	public void onUpdate()
	{
		if(MC.player == null || MC.level == null || MC.getConnection() == null)
			return;
		// check gamemode
		if(MC.player.getAbilities().instabuild)
			return;

		if(serverTimeout.isChecked() && System.nanoTime() - lastServerTick
			> timeoutSeconds.getValueI() * 1_000_000_000L)
		{
			leave("服务器长时间未发送时钟更新。", timeoutReconnect.isChecked());
			return;
		}
		ticksInPortal = portalTimeout.isChecked() && isInPortal() ? ticksInPortal + 1 : 0;
		if(portalTimeout.isChecked() && ticksInPortal > portalTicks.getValueI())
		{
			leave("传送门停留超时。", false);
			return;
		}
		if(lowY.isChecked() && MC.player.getY() < yLevel.getValue())
		{
			leave("低于设定高度。", false);
			return;
		}
		if(lowTotems.isChecked() && InventoryUtils.count(Items.TOTEM_OF_UNDYING, 36, true)
			< minimumTotems.getValueI())
		{
			leave("图腾数量不足。", false);
			return;
		}
		if(armorDamage.isChecked())
			for(EquipmentSlot slot : new EquipmentSlot[]{EquipmentSlot.HEAD,
				EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET})
			{
				ItemStack stack = MC.player.getItemBySlot(slot);
				if(ignoreElytra.isChecked() && stack.is(Items.ELYTRA))
					continue;
				if(AutoLeaveTriggers.damagedArmor(stack.getMaxDamage(), stack.getDamageValue(),
					stack.isDamageableItem(), armorPercent.getValue()))
				{
					leave("护甲剩余耐久不足。", false);
					return;
				}
			}
		if(nearPosition.isChecked() && AutoLeaveTriggers.nearPosition(MC.player.getX(),
			MC.player.getZ(), positionX.getValueI(), positionZ.getValueI(), positionRange.getValue()))
		{
			leave("已到达目标坐标附近。", false);
			return;
		}
		if(!lowHealth.isChecked())
			return;
		
		// check health
		float currentHealth = MC.player.getHealth();
		if(currentHealth <= 0F || currentHealth > health.getValueF() * 2F)
			return;
		
		// check totems
		if(totems.getValueI() < 11 && InventoryUtils
			.count(Items.TOTEM_OF_UNDYING, 40, true) > totems.getValueI())
			return;
		
		leave("生命值低于设定阈值。", false);
	}

	private boolean isInPortal()
	{
		return MC.player.portalProcess != null && MC.player.portalProcess.isInsidePortalThisTick();
	}

	private void leave(String reason, boolean reconnect)
	{
		ChatUtils.message("自动下线：" + reason);
		if(reconnect)
			WURST.getHax().autoReconnectHack.requestOnce(reconnectSeconds.getValueI());
		else if(disableAutoReconnect.isChecked())
			WURST.getHax().autoReconnectHack.setEnabled(false);
		mode.getSelected().leave.run();
		if(disableAfterLeave.isChecked())
			setEnabled(false);
	}

	private void resetSession()
	{
		lastServerTick = System.nanoTime();
		ticksInPortal = 0;
	}

	@Override
	public void onReceivedPacket(PacketInputEvent event)
	{
		if(event.getPacket() instanceof ClientboundSetTimePacket)
			lastServerTick = System.nanoTime();
	}

	@Override
	public void onWorldChange(ClientLevel world)
	{
		resetSession();
	}
	
	public static enum Mode
	{
		QUIT("Quit", () -> MC.getConnection().getConnection()
			.disconnect(Component.literal("Disconnected"))),
		
		CHARS("Chars", () -> MC.getConnection().sendChat("\u00a7")),
		
		SELFHURT("SelfHurt",
			() -> MC.gameMode.attack(MC.player, MC.player));
		
		private final String name;
		private final Runnable leave;
		
		private Mode(String name, Runnable leave)
		{
			this.name = name;
			this.leave = leave;
		}
		
		@Override
		public String toString()
		{
			return name;
		}
	}
}
