/*
 * Copyright (c) 2014-2025 Wurst-Imperium and contributors.
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 */
package net.wurstclient.hacks;

import com.mojang.blaze3d.vertex.PoseStack;
import java.awt.Color;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Options;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec2;
import net.minecraft.world.phys.Vec3;
import net.wurstclient.Category;
import net.wurstclient.SearchTags;
import net.wurstclient.events.*;
import net.wurstclient.hack.DontSaveState;
import net.wurstclient.hack.Hack;
import net.wurstclient.mixinterface.IKeyBinding;
import net.wurstclient.settings.CheckboxSetting;
import net.wurstclient.settings.ColorSetting;
import net.wurstclient.settings.SliderSetting;
import net.wurstclient.settings.SliderSetting.ValueDisplay;
import net.wurstclient.util.FakePlayerEntity;
import net.wurstclient.util.MovementPlanner;
import net.wurstclient.util.RenderUtils;

@DontSaveState
@SearchTags({"free camera", "spectator"})
public final class FreecamHack extends Hack implements UpdateListener,
	PacketOutputListener, IsPlayerInWaterListener, AirStrafingSpeedListener,
	IsPlayerInLavaListener, CameraTransformViewBobbingListener,
	IsNormalCubeListener, SetOpaqueCubeListener, RenderListener
{
	private final SliderSetting speed =
		new SliderSetting("Speed", 1, 0.05, 10, 0.05, ValueDisplay.DECIMAL);
	
	private final CheckboxSetting tracer = new CheckboxSetting("Tracer",
		"Draws a line to your character's actual position.", false);
	
	private final ColorSetting color =
		new ColorSetting("Tracer color", Color.WHITE);
	
	private FakePlayerEntity fakePlayer;
	private Vec3 camPos;
	private Vec3 prevCamPos;
	private float camYaw;
	private float camPitch;
	
	public FreecamHack()
	{
		super("Freecam");
		setCategory(Category.RENDER);
		addSetting(speed);
		addSetting(tracer);
		addSetting(color);
	}
	
	@Override
	protected void onEnable()
	{
		EVENTS.add(UpdateListener.class, this);
		EVENTS.add(PacketOutputListener.class, this);
		EVENTS.add(IsPlayerInWaterListener.class, this);
		EVENTS.add(IsPlayerInLavaListener.class, this);
		EVENTS.add(AirStrafingSpeedListener.class, this);
		EVENTS.add(CameraTransformViewBobbingListener.class, this);
		EVENTS.add(IsNormalCubeListener.class, this);
		EVENTS.add(SetOpaqueCubeListener.class, this);
		EVENTS.add(RenderListener.class, this);
		
		fakePlayer = new FakePlayerEntity();
		LocalPlayer player = MC.player;
		camPos = player.getEyePosition();
		prevCamPos = camPos;
		camYaw = player.getYRot();
		camPitch = player.getXRot();
		
		Options opt = MC.options;
		KeyMapping[] bindings = {opt.keyUp, opt.keyDown, opt.keyLeft,
			opt.keyRight, opt.keyJump, opt.keyShift};
		
		for(KeyMapping binding : bindings)
			IKeyBinding.get(binding).resetPressedState();
	}
	
	@Override
	protected void onDisable()
	{
		EVENTS.remove(UpdateListener.class, this);
		EVENTS.remove(PacketOutputListener.class, this);
		EVENTS.remove(IsPlayerInWaterListener.class, this);
		EVENTS.remove(IsPlayerInLavaListener.class, this);
		EVENTS.remove(AirStrafingSpeedListener.class, this);
		EVENTS.remove(CameraTransformViewBobbingListener.class, this);
		EVENTS.remove(IsNormalCubeListener.class, this);
		EVENTS.remove(SetOpaqueCubeListener.class, this);
		EVENTS.remove(RenderListener.class, this);
		
		fakePlayer.resetPlayerPosition();
		fakePlayer.despawn();
		
		LocalPlayer player = MC.player;
		player.setDeltaMovement(Vec3.ZERO);
		
		MC.levelExtractor.allChanged();
	}
	
	@Override
	public void onUpdate()
	{
		LocalPlayer player = MC.player;
		if(player == null)
			return;

		player.setDeltaMovement(Vec3.ZERO);
		player.getAbilities().flying = false;
		
		if(!isMovingCamera() || MC.gui.screen() != null)
		{
			prevCamPos = camPos;
			return;
		}

		Vec2 moveVector = MovementPlanner.getMoveVector(player.input);
		double yawRad = Math.toRadians(camYaw);
		double sinYaw = Math.sin(yawRad);
		double cosYaw = Math.cos(yawRad);
		double offsetX = moveVector.x * cosYaw - moveVector.y * sinYaw;
		double offsetZ = moveVector.x * sinYaw + moveVector.y * cosYaw;
		double offsetY = 0;
		if(MC.options.keyJump.isDown())
			offsetY += 1;
		if(MC.options.keyShift.isDown())
			offsetY -= 1;

		prevCamPos = camPos;
		camPos = camPos.add(new Vec3(offsetX, offsetY, offsetZ)
			.scale(speed.getValue()));
	}
	
	@Override
	public void onGetAirStrafingSpeed(AirStrafingSpeedEvent event)
	{
		event.setSpeed(speed.getValueF());
	}
	
	@Override
	public void onSentPacket(PacketOutputEvent event)
	{
		if(event.getPacket() instanceof ServerboundMovePlayerPacket)
			event.cancel();
	}

	public boolean isControllingScrollEvents()
	{
		return false;
	}

	public boolean isMovingCamera()
	{
		return isEnabled();
	}

	public boolean isClickingFromCamera()
	{
		return isEnabled();
	}

	public boolean shouldHideHand()
	{
		return isEnabled();
	}

	public Vec3 getCamPos(float partialTicks)
	{
		if(prevCamPos == null || camPos == null)
			return MC.player.getEyePosition(partialTicks);
		
		return Mth.lerp(partialTicks, prevCamPos, camPos);
	}

	public void turn(double deltaYaw, double deltaPitch)
	{
		camYaw += (float)(deltaYaw * 0.15);
		camPitch += (float)(deltaPitch * 0.15);
		camPitch = Mth.clamp(camPitch, -90, 90);
	}

	public float getCamYaw()
	{
		return camYaw;
	}

	public float getCamPitch()
	{
		return camPitch;
	}

	public Vec3 getScaledCamDir(double scale)
	{
		return Vec3.directionFromRotation(camPitch, camYaw).scale(scale);
	}
	
	@Override
	public void onIsPlayerInWater(IsPlayerInWaterEvent event)
	{
		event.setInWater(false);
	}
	
	@Override
	public void onIsPlayerInLava(IsPlayerInLavaEvent event)
	{
		event.setInLava(false);
	}
	
	@Override
	public void onCameraTransformViewBobbing(
		CameraTransformViewBobbingEvent event)
	{
		if(tracer.isChecked())
			event.cancel();
	}
	
	@Override
	public void onIsNormalCube(IsNormalCubeEvent event)
	{
		event.cancel();
	}
	
	@Override
	public void onSetOpaqueCube(SetOpaqueCubeEvent event)
	{
		event.cancel();
	}
	
	@Override
	public void onRender(PoseStack matrixStack, float partialTicks)
	{
		if(fakePlayer == null || !tracer.isChecked())
			return;
		
		int colorI = color.getColorI(0x80);
		
		// box
		double extraSize = 0.05;
		AABB box = fakePlayer.getBoundingBox().move(0, extraSize, 0)
			.inflate(extraSize);
		RenderUtils.drawOutlinedBox(matrixStack, box, colorI, false);
		
		// line
		RenderUtils.drawTracer(matrixStack, partialTicks,
			fakePlayer.getBoundingBox().getCenter(), colorI, false);
	}
}
