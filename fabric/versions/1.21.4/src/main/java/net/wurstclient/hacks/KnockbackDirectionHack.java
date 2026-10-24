/*
 * Copyright (c) 2026 WurstB+ Plus contributors
 *
 * This source code is subject to the terms of the GNU Affero General Public
 * License, version 3. If a copy of the AGPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/agpl-3.0.txt
 */
// Ported from InvincibleMachineGun (GPL-3.0).
package net.wurstclient.hacks;

import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.wurstclient.Category;
import net.wurstclient.SearchTags;
import net.wurstclient.events.MouseScrollListener;
import net.wurstclient.events.PlayerAttacksEntityListener;
import net.wurstclient.events.UpdateListener;
import net.wurstclient.hack.Hack;
import net.wurstclient.settings.CheckboxSetting;
import net.wurstclient.settings.SliderSetting;
import net.wurstclient.settings.SliderSetting.ValueDisplay;
import net.wurstclient.util.ChatUtils;

/**
 * Controls the direction of outgoing knockback by rotating the player right
 * before the attack packet is sent and snapping back afterwards.
 *
 * <p>
 * Wurst has no keybind settings, so the scroll modifier is "hold sneak"
 * instead of the upstream's configurable modifier key.
 */
@SearchTags({"knockback direction", "knockback", "hit direction",
	"attack rotation"})
public final class KnockbackDirectionHack extends Hack
	implements PlayerAttacksEntityListener, MouseScrollListener,
	UpdateListener
{
	private final SliderSetting yawOffset = new SliderSetting("Yaw offset",
		"Rotation relative to your view. 180 pulls the target towards you, "
			+ "90 pushes it to the left.",
		180, -180, 180, 1, ValueDisplay.DEGREES);

	private final SliderSetting recoilPercent = new SliderSetting(
		"Recoil amount", "How far the view twitches back after the attack.",
		0.3, 0, 1, 0.05, ValueDisplay.PERCENTAGE);

	private final SliderSetting scrollStep = new SliderSetting("Scroll step",
		"Degrees added or removed per scroll notch.", 15, 1, 90, 1,
		ValueDisplay.DEGREES);

	private final CheckboxSetting requireSneak = new CheckboxSetting(
		"Sneak to scroll", "Hold sneak while scrolling to change the offset.",
		true);

	private boolean pendingRecoil;
	private float pendingYaw;
	private float pendingPitch;
	private boolean pendingOnGround;
	private boolean pendingCollision;

	public KnockbackDirectionHack()
	{
		super("KnockbackDirection");
		setCategory(Category.COMBAT);
		addSetting(yawOffset);
		addSetting(recoilPercent);
		addSetting(scrollStep);
		addSetting(requireSneak);
	}

	@Override
	protected void onEnable()
	{
		EVENTS.add(PlayerAttacksEntityListener.class, this);
		EVENTS.add(MouseScrollListener.class, this);
		EVENTS.add(UpdateListener.class, this);
		pendingRecoil = false;
	}

	@Override
	protected void onDisable()
	{
		EVENTS.remove(PlayerAttacksEntityListener.class, this);
		EVENTS.remove(MouseScrollListener.class, this);
		EVENTS.remove(UpdateListener.class, this);
		pendingRecoil = false;
	}

	@Override
	public void onPlayerAttacksEntity(Entity target)
	{
		if(MC.player == null || target == null)
			return;

		float currentYaw = MC.player.getYRot();
		float pitch = MC.player.getXRot();
		boolean onGround = MC.player.onGround();

		float targetYaw =
			(float)Mth.wrapDegrees(currentYaw + yawOffset.getValue());

		// Sent before the vanilla attack packet, so the server calculates the
		// knockback direction from this yaw.
		MC.player.connection.send(new ServerboundMovePlayerPacket.Rot(
			targetYaw, pitch, onGround, MC.player.horizontalCollision));

		pendingYaw = (float)interpolate(currentYaw, targetYaw,
			recoilPercent.getValue());
		pendingPitch = pitch;
		pendingOnGround = onGround;
		pendingCollision = MC.player.horizontalCollision;
		pendingRecoil = true;
	}

	@Override
	public void onUpdate()
	{
		if(!pendingRecoil || MC.player == null)
			return;

		pendingRecoil = false;
		MC.player.connection.send(new ServerboundMovePlayerPacket.Rot(
			pendingYaw, pendingPitch, pendingOnGround, pendingCollision));
	}

	@Override
	public void onMouseScroll(double amount)
	{
		if(MC.screen != null || MC.player == null)
			return;

		if(requireSneak.isChecked() && !MC.options.keyShift.isDown())
			return;

		double newOffset = Mth
			.wrapDegrees(yawOffset.getValue() + amount * scrollStep.getValue());
		yawOffset.setValue(newOffset);
		ChatUtils.message(
			"Yaw offset: " + String.format("%.1f", newOffset));
	}

	private double interpolate(double start, double end, double factor)
	{
		double difference = end - start;
		while(difference < -180)
			difference += 360;
		while(difference >= 180)
			difference -= 360;
		return start + difference * factor;
	}
}
