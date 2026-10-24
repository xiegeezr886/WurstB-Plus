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

import net.minecraft.network.protocol.game.ClientboundPlayerPositionPacket;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.world.phys.Vec3;
import net.wurstclient.Category;
import net.wurstclient.SearchTags;
import net.wurstclient.events.PacketInputListener;
import net.wurstclient.events.PacketOutputListener;
import net.wurstclient.events.UpdateListener;
import net.wurstclient.hack.Hack;
import net.wurstclient.settings.EnumSetting;

@SearchTags({"stuck", "freeze", "no packet", "cancel move"})
public final class StuckHack extends Hack
	implements UpdateListener, PacketOutputListener, PacketInputListener
{
	private final EnumSetting<Mode> mode = new EnumSetting<>("Mode",
		"\u00a7lNoPacket\u00a7r - Cancels all movement packets.\n"
			+ "\u00a7lCancelMove\u00a7r - Freezes movement for 19 out of 20 ticks.",
		Mode.values(), Mode.NO_PACKET);

	private static final int CYCLE_LENGTH = 20;
	private static final int UNFREEZE_START = 19;

	private int cycleTick;
	private float lastYaw;
	private float lastPitch;
	private boolean bypassPacket;
	private boolean pendingDisable;

	public StuckHack()
	{
		super("Stuck");
		setCategory(Category.MOVEMENT);
		addSetting(mode);
	}

	@Override
	protected void onEnable()
	{
		cycleTick = 0;
		bypassPacket = false;
		pendingDisable = false;

		if(MC.player != null)
		{
			lastYaw = MC.player.getYRot();
			lastPitch = MC.player.getXRot();
		}

		EVENTS.add(UpdateListener.class, this);
		EVENTS.add(PacketOutputListener.class, this);
		EVENTS.add(PacketInputListener.class, this);
	}

	@Override
	protected void onDisable()
	{
		EVENTS.remove(UpdateListener.class, this);
		EVENTS.remove(PacketOutputListener.class, this);
		EVENTS.remove(PacketInputListener.class, this);

		if(mode.getSelected() == Mode.NO_PACKET && MC.player != null
			&& MC.getConnection() != null && !MC.player.onGround())
		{
			bypassPacket = true;
			MC.getConnection().send(new ServerboundMovePlayerPacket.PosRot(
				MC.player.getX() + 1337, MC.player.getY(),
				MC.player.getZ() + 1337, MC.player.getYRot() + 0.01F,
				MC.player.getXRot(), MC.player.onGround()));
			bypassPacket = false;
		}

		bypassPacket = false;
		cycleTick = 0;
	}

	@Override
	public void onUpdate()
	{
		if(pendingDisable)
		{
			pendingDisable = false;
			setEnabled(false);
			return;
		}

		if(MC.player == null)
			return;

		cycleTick++;
		zeroInput();

		if(mode.getSelected() == Mode.NO_PACKET)
		{
			float yaw = MC.player.getYRot();
			float pitch = MC.player.getXRot();

			if((yaw != lastYaw || pitch != lastPitch)
				&& MC.getConnection() != null)
			{
				bypassPacket = true;
				MC.getConnection().send(new ServerboundMovePlayerPacket.Rot(
					yaw, pitch, MC.player.onGround()));
				bypassPacket = false;
			}

			lastYaw = yaw;
			lastPitch = pitch;
			MC.player.setDeltaMovement(Vec3.ZERO);
			return;
		}

		if(cycleTick % CYCLE_LENGTH < UNFREEZE_START)
			MC.player.setDeltaMovement(Vec3.ZERO);
	}

	@Override
	public void onSentPacket(PacketOutputEvent event)
	{
		if(bypassPacket)
			return;

		if(mode.getSelected() == Mode.NO_PACKET
			&& event.getPacket() instanceof ServerboundMovePlayerPacket)
			event.cancel();
	}

	@Override
	public void onReceivedPacket(PacketInputEvent event)
	{
		if(event.getPacket() instanceof ClientboundPlayerPositionPacket)
			pendingDisable = true;
	}

	private void zeroInput()
	{
		if(MC.player == null)
			return;

		var input = MC.player.input;
		input.up = false;
		input.down = false;
		input.left = false;
		input.right = false;
		input.jumping = false;
		input.shiftKeyDown = false;
		input.leftImpulse = 0;
		input.forwardImpulse = 0;
	}

	private enum Mode
	{
		NO_PACKET("NoPacket"),
		CANCEL_MOVE("CancelMove");

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
