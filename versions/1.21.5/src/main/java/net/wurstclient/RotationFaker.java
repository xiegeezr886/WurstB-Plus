/*
 * Copyright (c) 2014-2025 Wurst-Imperium and contributors.
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 */
package net.wurstclient;

import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import net.wurstclient.events.PacketOutputListener;
import net.wurstclient.events.PacketOutputListener.PacketOutputEvent;
import net.wurstclient.events.PostMotionListener;
import net.wurstclient.util.Rotation;
import net.wurstclient.util.RotationUtils;

public final class RotationFaker
	implements PostMotionListener, PacketOutputListener
{
	private LocalPlayer trackedPlayer;
	private Rotation currentRotation;
	private float serverYaw;
	private float serverPitch;
	private float previousServerYaw;
	private float previousServerPitch;
	private boolean serverRotationKnown;
	private boolean modelRotationActive;
	
	@Override
	public void onPostMotion()
	{
		ensureTrackedPlayer();
		currentRotation = null;
	}

	@Override
	public void onSentPacket(PacketOutputEvent event)
	{
		if(!(event.getPacket() instanceof ServerboundMovePlayerPacket))
			return;

		event.runAfterSend(() -> trackSentRotation(event.getPacket()));
	}
	
	public void faceVectorPacket(Vec3 vec)
	{
		Rotation needed = RotationUtils.getNeededRotations(vec);
		LocalPlayer player = WurstClient.MC.player;
		if(player == null)
			return;
		
		setRotationPacket(new Rotation(
			RotationUtils.limitAngleChange(getServerYaw(), needed.yaw()),
			needed.pitch()));
	}

	public void setRotationPacket(Rotation rotation)
	{
		ensureTrackedPlayer();
		if(trackedPlayer == null || rotation == null)
			return;

		currentRotation = normalize(rotation.yaw(), rotation.pitch());
	}

	public Rotation getCurrentRotation()
	{
		ensureTrackedPlayer();
		return currentRotation;
	}
	
	public void faceVectorClient(Vec3 vec)
	{
		Rotation needed = RotationUtils.getNeededRotations(vec);
		
		LocalPlayer player = WurstClient.MC.player;
		player.setYRot(
			RotationUtils.limitAngleChange(player.getYRot(), needed.yaw()));
		player.setXRot(needed.pitch());
	}
	
	public void faceVectorClientIgnorePitch(Vec3 vec)
	{
		Rotation needed = RotationUtils.getNeededRotations(vec);
		
		LocalPlayer player = WurstClient.MC.player;
		player.setYRot(
			RotationUtils.limitAngleChange(player.getYRot(), needed.yaw()));
		player.setXRot(0);
	}
	
	public float getServerYaw()
	{
		ensureTrackedPlayer();
		return serverRotationKnown ? serverYaw
			: trackedPlayer == null ? 0 : trackedPlayer.getYRot();
	}
	
	public float getServerPitch()
	{
		ensureTrackedPlayer();
		return serverRotationKnown ? serverPitch
			: trackedPlayer == null ? 0 : trackedPlayer.getXRot();
	}

	public boolean hasServerRotation()
	{
		ensureTrackedPlayer();
		return serverRotationKnown;
	}

	public boolean isModelRotationActive()
	{
		ensureTrackedPlayer();
		return modelRotationActive && serverRotationKnown;
	}

	public float getInterpolatedServerYaw(float partialTicks)
	{
		ensureTrackedPlayer();
		return serverRotationKnown ? Mth.rotLerp(partialTicks,
			previousServerYaw, serverYaw) : getServerYaw();
	}

	public float getInterpolatedServerPitch(float partialTicks)
	{
		ensureTrackedPlayer();
		return serverRotationKnown ? Mth.lerp(partialTicks,
			previousServerPitch, serverPitch) : getServerPitch();
	}

	private void trackSentRotation(Packet<?> sentPacket)
	{
		if(!(sentPacket instanceof ServerboundMovePlayerPacket movementPacket)
			|| !movementPacket.hasRotation())
			return;

		ensureTrackedPlayer();
		float fallbackYaw = serverRotationKnown ? serverYaw
			: trackedPlayer == null ? 0 : trackedPlayer.getYRot();
		float fallbackPitch = serverRotationKnown ? serverPitch
			: trackedPlayer == null ? 0 : trackedPlayer.getXRot();
		Rotation sentRotation = normalize(
			movementPacket.getYRot(fallbackYaw),
			movementPacket.getXRot(fallbackPitch));

		previousServerYaw = fallbackYaw;
		previousServerPitch = fallbackPitch;
		serverYaw = sentRotation.yaw();
		serverPitch = sentRotation.pitch();
		serverRotationKnown = true;
		modelRotationActive = currentRotation != null;
	}

	private void ensureTrackedPlayer()
	{
		LocalPlayer player = WurstClient.MC.player;
		if(player == trackedPlayer)
			return;

		trackedPlayer = player;
		currentRotation = null;
		serverRotationKnown = false;
		modelRotationActive = false;
		serverYaw = 0;
		serverPitch = 0;
		previousServerYaw = 0;
		previousServerPitch = 0;
	}

	private Rotation normalize(float yaw, float pitch)
	{
		return new Rotation(Mth.wrapDegrees(yaw),
			Mth.clamp(Mth.wrapDegrees(pitch), -90, 90));
	}
}
