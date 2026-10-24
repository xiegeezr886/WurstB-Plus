/*
 * Copyright (c) 2026 WurstB+ Plus contributors
 *
 * This source code is subject to the terms of the GNU Affero General Public
 * License, version 3. If a copy of the AGPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/agpl-3.0.txt
 *
 * Ported from InvincibleMachineGun (GPL-3.0).
 */
package net.wurstclient.hacks;

import org.lwjgl.glfw.GLFW;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;
import net.wurstclient.Category;
import net.wurstclient.SearchTags;
import net.wurstclient.events.MouseButtonPressListener;
import net.wurstclient.events.UpdateListener;
import net.wurstclient.hack.Hack;
import net.wurstclient.settings.CheckboxSetting;
import net.wurstclient.settings.EnumSetting;
import net.wurstclient.settings.SliderSetting;
import net.wurstclient.settings.SliderSetting.ValueDisplay;
import net.wurstclient.util.RotationUtils;

@SearchTags({"attract aura", "magnet", "pull players", "AttractAura"})
public final class AttractAuraHack extends Hack
	implements UpdateListener, MouseButtonPressListener
{
	private final EnumSetting<Mode> mode = new EnumSetting<>("Mode",
		"How AttractAura picks a target.", Mode.values(), Mode.CROSSHAIR);

	private final EnumSetting<ToggleBehavior> toggleBehavior =
		new EnumSetting<>("Middle click action",
			"What to do when middle-clicking while locked.",
			ToggleBehavior.values(), ToggleBehavior.STOP);

	private final SliderSetting range = new SliderSetting("Range",
		"Maximum distance for automatic target selection.", 30, 1, 100, 1,
		ValueDisplay.INTEGER);

	private final SliderSetting speed = new SliderSetting("Pull speed",
		"Velocity applied towards the target.", 1, 0, 5, 0.05,
		ValueDisplay.DECIMAL);

	private final SliderSetting stopDistance = new SliderSetting(
		"Stop distance", "Stops pulling once this close to the target.", 1.5, 0,
		10, 0.1, ValueDisplay.DECIMAL);

	private final CheckboxSetting lookAt = new CheckboxSetting("Look at target",
		"Turns the camera towards the target while pulling.", true);

	private final CheckboxSetting middleClickLock = new CheckboxSetting(
		"Middle click lock", "Middle-click a player to lock onto them.", true);

	private final EnumSetting<AntiKickMode> antiKick = new EnumSetting<>(
		"Anti-kick mode", "Movement pattern used to reduce rubber-banding.",
		AntiKickMode.values(), AntiKickMode.JITTER_DOWN);

	private final SliderSetting kickDelay = new SliderSetting("Anti-kick delay",
		"Ticks between anti-kick actions.", 25, 5, 100, 1,
		ValueDisplay.INTEGER.withSuffix(" ticks"));

	private final CheckboxSetting ignoreFriends = new CheckboxSetting(
		"Ignore friends", "Never pulls towards friends.", true);

	private final CheckboxSetting followSurvival = new CheckboxSetting(
		"Follow survival players", "Allow survival mode players as targets.",
		true);

	private final CheckboxSetting followAdventure = new CheckboxSetting(
		"Follow adventure players", "Allow adventure mode players as targets.",
		true);

	private final CheckboxSetting followCreative = new CheckboxSetting(
		"Follow creative players", "Allow creative mode players as targets.",
		false);

	private Player target;
	private int antiKickTimer;

	public AttractAuraHack()
	{
		super("AttractAura");
		setCategory(Category.COMBAT);
		addSetting(mode);
		addSetting(toggleBehavior);
		addSetting(range);
		addSetting(speed);
		addSetting(stopDistance);
		addSetting(lookAt);
		addSetting(middleClickLock);
		addSetting(antiKick);
		addSetting(kickDelay);
		addSetting(ignoreFriends);
		addSetting(followSurvival);
		addSetting(followAdventure);
		addSetting(followCreative);
	}

	@Override
	protected void onEnable()
	{
		target = null;
		antiKickTimer = 0;
		EVENTS.add(UpdateListener.class, this);
		EVENTS.add(MouseButtonPressListener.class, this);
	}

	@Override
	protected void onDisable()
	{
		EVENTS.remove(UpdateListener.class, this);
		EVENTS.remove(MouseButtonPressListener.class, this);
		target = null;
	}

	@Override
	public void onMouseButtonPress(MouseButtonPressEvent event)
	{
		if(!middleClickLock.isChecked() || MC.gui.screen() != null)
			return;
		if(event.getButton() != GLFW.GLFW_MOUSE_BUTTON_MIDDLE
			|| event.getAction() != GLFW.GLFW_PRESS)
			return;

		if(mode.getSelected() == Mode.LOCK)
		{
			target = null;
			mode.setSelected(
				toggleBehavior.getSelected() == ToggleBehavior.STOP ? Mode.OFF
					: Mode.CROSSHAIR);
			return;
		}

		Player lookedAt = getTargetByCrosshair();
		if(lookedAt != null)
		{
			target = lookedAt;
			mode.setSelected(Mode.LOCK);
		}
	}

	@Override
	public void onUpdate()
	{
		if(MC.player == null || MC.level == null)
			return;

		Mode currentMode = mode.getSelected();
		if(currentMode == Mode.OFF)
		{
			target = null;
			return;
		}

		if(currentMode == Mode.LOCK)
		{
			if(target != null && !isTargetValid(target))
			{
				target = null;
				mode.setSelected(
					toggleBehavior.getSelected() == ToggleBehavior.STOP
						? Mode.OFF : Mode.CROSSHAIR);
			}
		}else if(currentMode == Mode.NEAREST)
			target = getNearestTarget();
		else
			target = getTargetByCrosshair();

		if(target == null)
			return;

		Vec3 targetPos = target.getBoundingBox().getCenter();
		Vec3 eyesPos = RotationUtils.getEyesPos();
		double distance = eyesPos.distanceTo(targetPos);

		if(distance <= stopDistance.getValue())
		{
			MC.player.setDeltaMovement(Vec3.ZERO);
			return;
		}

		Vec3 direction = targetPos.subtract(eyesPos).normalize();
		double velocityX = direction.x * speed.getValue();
		double velocityY = direction.y * speed.getValue();
		double velocityZ = direction.z * speed.getValue();

		if(++antiKickTimer >= kickDelay.getValueI())
		{
			if(antiKick.getSelected() == AntiKickMode.JITTER_DOWN)
				velocityY = -0.05;
			else if(antiKick.getSelected() == AntiKickMode.FAKE_GROUND)
				MC.player.connection.send(new ServerboundMovePlayerPacket.StatusOnly(
					true, MC.player.horizontalCollision));
			antiKickTimer = 0;
		}

		MC.player.setDeltaMovement(velocityX, velocityY, velocityZ);

		if(lookAt.isChecked())
		{
			double diffX = targetPos.x - eyesPos.x;
			double diffY = targetPos.y - eyesPos.y;
			double diffZ = targetPos.z - eyesPos.z;
			double diffXZ = Math.sqrt(diffX * diffX + diffZ * diffZ);
			MC.player.setYRot(
				(float)Math.toDegrees(Math.atan2(diffZ, diffX)) - 90F);
			MC.player.setXRot(
				(float)-Math.toDegrees(Math.atan2(diffY, diffXZ)));
		}
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
				(float)Math.toDegrees(Math.atan2(diffZ, diffX)) - 90F;
			float diff = Math.abs(
				Mth.wrapDegrees(MC.player.getYRot() - targetYaw));
			if(diff < bestDiff)
			{
				bestDiff = diff;
				best = player;
			}
		}

		return best;
	}

	private Player getNearestTarget()
	{
		Player best = null;
		double bestDistance = Double.MAX_VALUE;

		for(Player player : MC.level.players())
		{
			if(!isTargetValid(player))
				continue;

			double distance = MC.player.distanceToSqr(player);
			if(distance < bestDistance)
			{
				bestDistance = distance;
				best = player;
			}
		}

		return best;
	}

	private boolean isTargetValid(Player player)
	{
		if(player == null || player == MC.player || !player.isAlive())
			return false;

		if(mode.getSelected() != Mode.LOCK
			&& MC.player.distanceTo(player) > range.getValue() + 30)
			return false;

		if(ignoreFriends.isChecked()
			&& WURST.getFriends().contains(player.getScoreboardName()))
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
			default -> false;
		};
	}

	private enum Mode
	{
		NEAREST("Nearest"),
		CROSSHAIR("Crosshair"),
		LOCK("Lock"),
		OFF("Off");

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

	private enum ToggleBehavior
	{
		BACK_TO_CROSSHAIR("Back to crosshair"),
		STOP("Stop pulling");

		private final String name;

		ToggleBehavior(String name)
		{
			this.name = name;
		}

		@Override
		public String toString()
		{
			return name;
		}
	}

	private enum AntiKickMode
	{
		NONE("None"),
		JITTER_DOWN("Jitter down"),
		FAKE_GROUND("Fake ground");

		private final String name;

		AntiKickMode(String name)
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
