/*
 * Copyright (c) 2014-2026 Wurst-Imperium and contributors.
 *
 * This source code is subject to the terms of the GNU Affero General Public
 * License, version 3. If a copy of the AGPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/agpl-3.0.txt
 *
 * Ported from InvincibleMachineGun (GPL-3.0).
 */
package net.wurstclient.hacks;

import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.network.protocol.game.ServerboundPlayerCommandPacket;
import net.minecraft.network.protocol.game.ServerboundPlayerCommandPacket.Action;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;
import net.wurstclient.Category;
import net.wurstclient.SearchTags;
import net.wurstclient.events.MouseButtonListener;
import net.wurstclient.events.UpdateListener;
import net.wurstclient.hack.Hack;
import net.wurstclient.settings.CheckboxSetting;
import net.wurstclient.settings.EnumSetting;
import net.wurstclient.settings.SliderSetting;
import net.wurstclient.settings.SliderSetting.ValueDisplay;

@SearchTags({"elytra follower", "elytra follow", "follow player", "wingman"})
public final class ElytraFollowerHack extends Hack
	implements UpdateListener, MouseButtonListener
{
	private final EnumSetting<Mode> mode = new EnumSetting<>("Mode",
		"How the target player is selected.", Mode.values(), Mode.CROSSHAIR);

	private final EnumSetting<ToggleBehavior> toggleBehavior = new EnumSetting<>(
		"Middle click behavior",
		"What happens when middle-clicking while a target is locked.",
		ToggleBehavior.values(), ToggleBehavior.STOP);

	private final CheckboxSetting lockAnytime = new CheckboxSetting(
		"Lock anytime", "Allows locking targets without wearing an elytra.",
		true);

	private final CheckboxSetting middleClickLock = new CheckboxSetting(
		"Middle click lock", "Middle-click to lock onto the player you look at.",
		true);

	private final SliderSetting range = new SliderSetting("Range",
		"Maximum distance at which players are followed.", 150, 1, 500, 1,
		ValueDisplay.INTEGER.withSuffix(" blocks"));

	private final SliderSetting speed = new SliderSetting("Speed",
		"Flight speed while following a target.", 1.8, 0.1, 5, 0.1,
		ValueDisplay.DECIMAL);

	private final SliderSetting stopDistance = new SliderSetting(
		"Stop distance", "Stops pushing forward this close to the target.", 2.5,
		0, 10, 0.1, ValueDisplay.DECIMAL.withSuffix(" blocks"));

	private final CheckboxSetting lookAt = new CheckboxSetting("Look at target",
		"Turns the camera towards the target while flying.", true);

	private final CheckboxSetting autoTakeoff = new CheckboxSetting(
		"Auto takeoff", "Automatically jumps and starts gliding.", true);

	private final SliderSetting takeoffDelay = new SliderSetting(
		"Takeoff delay", "Ticks between takeoff attempts.", 5, 0, 40, 1,
		ValueDisplay.INTEGER.withSuffix(" ticks"))
			.visibleWhen(autoTakeoff::isChecked);

	private final CheckboxSetting followSurvival =
		new CheckboxSetting("Follow survival", true);

	private final CheckboxSetting followAdventure =
		new CheckboxSetting("Follow adventure", true);

	private final CheckboxSetting followCreative =
		new CheckboxSetting("Follow creative", false);

	private final CheckboxSetting followSpectator =
		new CheckboxSetting("Follow spectator", false);

	private Player target;
	private int takeoffTimer;

	public ElytraFollowerHack()
	{
		super("ElytraFollower");
		setCategory(Category.MOVEMENT);
		addSetting(mode);
		addSetting(toggleBehavior);
		addSetting(lockAnytime);
		addSetting(middleClickLock);
		addSetting(range);
		addSetting(speed);
		addSetting(stopDistance);
		addSetting(lookAt);
		addSetting(autoTakeoff);
		addSetting(takeoffDelay);
		addSetting(followSurvival);
		addSetting(followAdventure);
		addSetting(followCreative);
		addSetting(followSpectator);
	}

	@Override
	protected void onEnable()
	{
		target = null;
		takeoffTimer = 0;
		EVENTS.add(UpdateListener.class, this);
		EVENTS.add(MouseButtonListener.class, this);
	}

	@Override
	protected void onDisable()
	{
		EVENTS.remove(UpdateListener.class, this);
		EVENTS.remove(MouseButtonListener.class, this);
		target = null;
		takeoffTimer = 0;
	}

	@Override
	public void onMouseButton(int button, int action)
	{
		if(!middleClickLock.isChecked() || button != 2 || action != 1
			|| MC.screen != null || MC.player == null)
			return;

		if(!lockAnytime.isChecked() && !MC.player
			.getItemBySlot(EquipmentSlot.CHEST).is(Items.ELYTRA))
			return;

		if(mode.getSelected() == Mode.LOCKED)
		{
			target = null;
			mode.setSelected(toggleBehavior.getSelected() == ToggleBehavior.STOP
				? Mode.OFF : Mode.CROSSHAIR);
			return;
		}

		Player lookedAt = getTargetByCrosshair();
		if(lookedAt != null)
		{
			target = lookedAt;
			mode.setSelected(Mode.LOCKED);
		}
	}

	@Override
	public void onUpdate()
	{
		if(MC.player == null || MC.level == null)
			return;

		if(mode.getSelected() == Mode.OFF)
		{
			target = null;
			return;
		}

		if(mode.getSelected() == Mode.LOCKED)
		{
			if(target != null && !isTargetValid(target))
			{
				target = null;
				mode.setSelected(
					toggleBehavior.getSelected() == ToggleBehavior.STOP
						? Mode.OFF : Mode.CROSSHAIR);
			}
		}else
			target = findBestTarget();

		ItemStack chest = MC.player.getItemBySlot(EquipmentSlot.CHEST);
		if(!chest.is(Items.ELYTRA))
			return;

		if(autoTakeoff.isChecked() && !MC.player.isFallFlying())
		{
			if(takeoffTimer > 0)
				takeoffTimer--;
			else if(MC.player.onGround())
			{
				MC.player.jumpFromGround();
				takeoffTimer = takeoffDelay.getValueI();
			}else if(!MC.player.isUnderWater())
			{
				sendStartFlying();
				takeoffTimer = takeoffDelay.getValueI();
			}
			return;
		}

		if(!MC.player.isFallFlying() || target == null)
			return;

		Vec3 targetPos = target.getBoundingBox().getCenter();
		Vec3 playerPos = MC.player.getEyePosition();
		double distance = playerPos.distanceTo(targetPos);

		if(distance > stopDistance.getValue())
		{
			Vec3 dir = targetPos.subtract(playerPos).normalize();
			MC.player.setDeltaMovement(dir.scale(speed.getValue()));

			if(lookAt.isChecked())
			{
				double diffX = targetPos.x - playerPos.x;
				double diffY = targetPos.y - playerPos.y;
				double diffZ = targetPos.z - playerPos.z;
				double diffXZ = Math.sqrt(diffX * diffX + diffZ * diffZ);
				MC.player.setYRot(
					(float)Math.toDegrees(Math.atan2(diffZ, diffX)) - 90F);
				MC.player.setXRot(
					(float)-Math.toDegrees(Math.atan2(diffY, diffXZ)));
			}
		}else
			MC.player
				.setDeltaMovement(MC.player.getDeltaMovement().scale(0.5));
	}

	private void sendStartFlying()
	{
		if(MC.getConnection() != null)
			MC.getConnection().send(
				new ServerboundPlayerCommandPacket(MC.player,
					Action.START_FALL_FLYING));
	}

	private Player findBestTarget()
	{
		if(mode.getSelected() == Mode.CROSSHAIR)
			return getTargetByCrosshair();

		Player best = null;
		double bestDist = Double.MAX_VALUE;
		for(Player player : MC.level.players())
		{
			if(!isTargetValid(player))
				continue;

			double dist = MC.player.distanceTo(player);
			if(dist < bestDist)
			{
				bestDist = dist;
				best = player;
			}
		}
		return best;
	}

	private Player getTargetByCrosshair()
	{
		Player best = null;
		double bestDiff = Double.MAX_VALUE;

		for(Player player : MC.level.players())
		{
			if(!isTargetValid(player))
				continue;

			double diffX = player.getX() - MC.player.getX();
			double diffZ = player.getZ() - MC.player.getZ();
			float targetYaw =
				(float)Math.toDegrees(Math.atan2(diffZ, diffX)) - 90;
			float diff =
				Math.abs(Mth.wrapDegrees(MC.player.getYRot() - targetYaw));
			if(diff < bestDiff)
			{
				bestDiff = diff;
				best = player;
			}
		}
		return best;
	}

	private boolean isTargetValid(Player player)
	{
		if(player == null || player == MC.player || !player.isAlive()
			|| MC.player.distanceTo(player) > range.getValue()
			|| WURST.getFriends().isFriend(player))
			return false;

		if(MC.getConnection() == null)
			return false;

		PlayerInfo info = MC.getConnection().getPlayerInfo(player.getUUID());
		if(info == null)
			return false;

		GameType gameMode = info.getGameMode();
		if(gameMode == null)
			return false;

		return switch(gameMode)
		{
			case SURVIVAL -> followSurvival.isChecked();
			case ADVENTURE -> followAdventure.isChecked();
			case CREATIVE -> followCreative.isChecked();
			case SPECTATOR -> followSpectator.isChecked();
			default -> false;
		};
	}

	private enum Mode
	{
		NEAREST("Nearest"),
		CROSSHAIR("Crosshair"),
		LOCKED("Locked"),
		OFF("Off");

		private final String name;

		private Mode(String name)
		{
			this.name = name;
		}

		@Override
		public String toString()
		{
			return name;
		}
	}

	private enum ToggleBehavior
	{
		RETURN_TO_CROSSHAIR("Return to crosshair"),
		STOP("Stop following");

		private final String name;

		private ToggleBehavior(String name)
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
