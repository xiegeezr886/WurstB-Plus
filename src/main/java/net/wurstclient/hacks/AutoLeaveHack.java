/*
 * Copyright (c) 2014-2025 Wurst-Imperium and contributors.
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 */
package net.wurstclient.hacks;

import java.util.stream.StreamSupport;
import net.minecraft.network.protocol.game.ServerboundInteractPacket;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Items;
import net.wurstclient.Category;
import net.wurstclient.SearchTags;
import net.wurstclient.events.UpdateListener;
import net.wurstclient.hack.Hack;
import net.wurstclient.settings.CheckboxSetting;
import net.wurstclient.settings.EnumSetting;
import net.wurstclient.settings.SliderSetting;
import net.wurstclient.settings.SliderSetting.ValueDisplay;
import net.wurstclient.util.FakePlayerEntity;
import net.wurstclient.util.InventoryUtils;

@SearchTags({"auto leave", "AutoDisconnect", "auto disconnect", "AutoQuit",
	"auto quit"})
public final class AutoLeaveHack extends Hack implements UpdateListener
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
	
	private final CheckboxSetting creepers = new CheckboxSetting("Creepers",
		"Leaves the server when a creeper gets close.\n\n"
			+ "Default off (the reference has it on): this hack used to be"
			+ " health-only, and an extra trigger that is on by default would"
			+ " silently change when it fires.",
		false);
	
	private final SliderSetting creeperDistance = new SliderSetting(
		"Creeper distance", "How close a creeper has to get.", 5, 1, 10, 1,
		ValueDisplay.INTEGER.withSuffix(" blocks"))
		.visibleWhen(creepers::isChecked);
	
	private final CheckboxSetting players = new CheckboxSetting("Players",
		"Leaves the server when another player gets close.", false);
	
	private final SliderSetting playerDistance = new SliderSetting(
		"Player distance", "How close another player has to get.", 64, 32,
		128, 4, ValueDisplay.INTEGER.withSuffix(" blocks"))
		.visibleWhen(players::isChecked);
	
	private final CheckboxSetting ignoreFriends = new CheckboxSetting(
		"Ignore friends", "Doesn't leave the server because of your friends.",
		true).visibleWhen(players::isChecked);
	
	public AutoLeaveHack()
	{
		super("AutoLeave");
		setCategory(Category.COMBAT);
		addSetting(health);
		addSetting(mode);
		addSetting(disableAutoReconnect);
		addSetting(totems);
		addSetting(creepers);
		addSetting(creeperDistance);
		addSetting(players);
		addSetting(playerDistance);
		addSetting(ignoreFriends);
	}
	
	@Override
	public String getRenderName()
	{
		if(MC.player.getAbilities().instabuild)
			return getName() + " (paused)";
		
		return getName() + " [" + mode.getSelected() + "]";
	}
	
	@Override
	protected void onEnable()
	{
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
		// check gamemode
		if(MC.player.getAbilities().instabuild)
			return;
		
		// check health
		float currentHealth = MC.player.getHealth();
		if(currentHealth <= 0F)
			return;
		
		boolean inDanger = currentHealth <= health.getValueF() * 2F
			|| creepers.isChecked() && isCreeperNear()
			|| players.isChecked() && isPlayerNear();
		if(!inDanger)
			return;
		
		// check totems
		if(totems.getValueI() < 11 && InventoryUtils
			.count(Items.TOTEM_OF_UNDYING, 40, true) > totems.getValueI())
			return;
		
		// leave server
		mode.getSelected().leave.run();
		
		// disable
		setEnabled(false);
		
		if(disableAutoReconnect.isChecked())
			WURST.getHax().autoReconnectHack.setEnabled(false);
	}
	
	/**
	 * 对应参考 {@code module/combat/AutoLog.kt:83-91} 的 checkCreeper()。
	 */
	private boolean isCreeperNear()
	{
		double maxDistSq = Math.pow(creeperDistance.getValue(), 2);
		return StreamSupport
			.stream(MC.level.entitiesForRendering().spliterator(), false)
			.filter(e -> e instanceof Creeper && e.isAlive())
			.anyMatch(e -> MC.player.distanceToSqr(e) <= maxDistSq);
	}
	
	/**
	 * 对应参考 {@code module/combat/AutoLog.kt:93-104} 的 checkPlayers()，
	 * 排除项照搬：自己、假人、AntiBot 判定出的机器人、可选的好友。
	 */
	private boolean isPlayerNear()
	{
		double maxDistSq = Math.pow(playerDistance.getValue(), 2);
		return StreamSupport
			.stream(MC.level.entitiesForRendering().spliterator(), false)
			.filter(e -> e instanceof Player && e != MC.player)
			.filter(e -> !(e instanceof FakePlayerEntity))
			.filter(e -> !WURST.getHax().antiBotHack.isBot((Player)e))
			.filter(e -> !ignoreFriends.isChecked()
				|| !WURST.getFriends().isFriend(e))
			.anyMatch(e -> MC.player.distanceToSqr(e) <= maxDistSq);
	}
	
	public static enum Mode
	{
		QUIT("Quit", () -> MC.level.disconnect()),
		
		CHARS("Chars", () -> MC.getConnection().sendChat("\u00a7")),
		
		SELFHURT("SelfHurt",
			() -> MC.getConnection()
				.send(ServerboundInteractPacket.createAttackPacket(MC.player,
					MC.player.isShiftKeyDown())));
		
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
