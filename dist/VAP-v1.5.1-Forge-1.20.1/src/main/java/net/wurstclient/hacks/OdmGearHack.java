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

import java.awt.Color;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.level.ClipContext;
import net.wurstclient.Category;
import net.wurstclient.SearchTags;
import net.wurstclient.events.RenderListener;
import net.wurstclient.events.UpdateListener;
import net.wurstclient.hack.Hack;
import net.wurstclient.settings.CheckboxSetting;
import net.wurstclient.settings.ColorSetting;
import net.wurstclient.settings.SliderSetting;
import net.wurstclient.settings.SliderSetting.ValueDisplay;
import net.wurstclient.util.RenderUtils;

/**
 * Dual grappling hooks that pull the player towards whatever they are looking
 * at, with a little orbit control on A/D.
 *
 * <p>
 * Ported from InvincibleMachineGun's ODMGear. Wurst has no per-hack keybind
 * setting, so the two hooks use the attack (left) and use (right) keys as a
 * simplification. Note that holding the use key can also interact with blocks
 * or items.
 */
@SearchTags({"odm gear", "grappling hook", "spiderman", "hook", "attack on titan"})
public final class OdmGearHack extends Hack
	implements UpdateListener, RenderListener
{
	private final SliderSetting range = new SliderSetting("Range",
		"Maximum hook distance.", 64, 10, 128, 1, ValueDisplay.DECIMAL);

	private final CheckboxSetting requireGear = new CheckboxSetting(
		"Require gear", "Only works while wearing an ODM gear or an elytra.",
		true);

	private final SliderSetting pullSpeed = new SliderSetting("Pull speed",
		"Base pull acceleration per tick.", 0.15, 0.02, 0.5, 0.01,
		ValueDisplay.DECIMAL);

	private final SliderSetting orbitSpeed = new SliderSetting("Orbit speed",
		"Sideways acceleration while holding A or D.", 0.25, 0.05, 1, 0.05,
		ValueDisplay.DECIMAL);

	private final SliderSetting lift = new SliderSetting("Upward lift",
		"Extra lift when the hook is above the player.", 0.08, 0, 0.5, 0.01,
		ValueDisplay.DECIMAL);

	private final SliderSetting dualMultiplier = new SliderSetting(
		"Dual multiplier", "Pull speed multiplier while both hooks are active.",
		1.5, 1, 3, 0.1, ValueDisplay.DECIMAL);

	private final SliderSetting drag = new SliderSetting("Drag",
		"Air drag applied to the current velocity each tick.", 0.98, 0.8, 1,
		0.01, ValueDisplay.DECIMAL);

	private final CheckboxSetting renderLines = new CheckboxSetting(
		"Render hooks", "Draws a line from the player to each active hook.",
		true);

	private final ColorSetting lineColor = new ColorSetting("Line color",
		new Color(200, 220, 255));

	private final HookState leftHook = new HookState();
	private final HookState rightHook = new HookState();

	public OdmGearHack()
	{
		super("OdmGear");
		setCategory(Category.MOVEMENT);
		addSetting(range);
		addSetting(requireGear);
		addSetting(pullSpeed);
		addSetting(orbitSpeed);
		addSetting(lift);
		addSetting(dualMultiplier);
		addSetting(drag);
		addSetting(renderLines);
		addSetting(lineColor);
	}

	@Override
	protected void onEnable()
	{
		leftHook.reset();
		rightHook.reset();
		EVENTS.add(UpdateListener.class, this);
		EVENTS.add(RenderListener.class, this);
	}

	@Override
	protected void onDisable()
	{
		EVENTS.remove(UpdateListener.class, this);
		EVENTS.remove(RenderListener.class, this);
		leftHook.reset();
		rightHook.reset();
	}

	@Override
	public void onUpdate()
	{
		if(MC.player == null || MC.level == null)
			return;

		if(requireGear.isChecked() && !hasGear())
		{
			leftHook.reset();
			rightHook.reset();
			return;
		}

		updateHook(leftHook, MC.options.keyAttack.isDown());
		updateHook(rightHook, MC.options.keyUse.isDown());
		applyPhysics();
	}

	private boolean hasGear()
	{
		String id = BuiltInRegistries.ITEM
			.getKey(MC.player.getItemBySlot(EquipmentSlot.CHEST).getItem())
			.toString();
		return id.contains("odm_gear") || id.contains("elytra");
	}

	private void updateHook(HookState hook, boolean pressed)
	{
		if(pressed && !hook.active)
			fireHook(hook);
		else if(!pressed && hook.active)
			hook.reset();

		if(!hook.active || hook.entity == null)
			return;

		if(!hook.entity.isAlive())
		{
			hook.reset();
			return;
		}

		hook.pos = hook.entity.position()
			.add(0, hook.entity.getBbHeight() * 0.7, 0);
	}

	private void fireHook(HookState hook)
	{
		double distance = range.getValue();
		Vec3 eyes = MC.player.getEyePosition();
		Vec3 look = MC.player.getLookAngle();
		Vec3 end = eyes.add(look.scale(distance));

		AABB searchBox =
			MC.player.getBoundingBox().expandTowards(look.scale(distance))
				.inflate(1.0);
		EntityHitResult entityHit = ProjectileUtil.getEntityHitResult(
			MC.player, eyes, end, searchBox,
			entity -> !entity.isSpectator() && entity.isPickable(),
			distance * distance);

		BlockHitResult blockHit = MC.level
			.clip(new ClipContext(eyes, end, ClipContext.Block.COLLIDER,
				ClipContext.Fluid.NONE, MC.player));

		boolean hitEntity = entityHit != null;
		boolean hitBlock = blockHit.getType() != HitResult.Type.MISS;

		if(hitEntity && hitBlock)
		{
			if(eyes.distanceToSqr(entityHit.getLocation()) < eyes
				.distanceToSqr(blockHit.getLocation()))
				setHook(hook, entityHit.getEntity(), entityHit.getLocation());
			else
				setHook(hook, null, blockHit.getLocation());
			return;
		}

		if(hitEntity)
			setHook(hook, entityHit.getEntity(), entityHit.getLocation());
		else if(hitBlock)
			setHook(hook, null, blockHit.getLocation());
	}

	private void setHook(HookState hook, Entity entity, Vec3 pos)
	{
		hook.active = true;
		hook.entity = entity;
		hook.pos = pos;
	}

	private void applyPhysics()
	{
		if(!leftHook.active && !rightHook.active)
			return;

		Vec3 playerPos = MC.player.position();
		boolean dual = leftHook.active && rightHook.active;
		Vec3 targetPos;

		if(dual)
			targetPos = leftHook.pos.add(rightHook.pos).scale(0.5);
		else if(leftHook.active)
			targetPos = leftHook.pos;
		else
			targetPos = rightHook.pos;

		Vec3 toHook = targetPos.subtract(playerPos);
		if(toHook.lengthSqr() < 1.0E-6)
			return;
		Vec3 direction = toHook.normalize();

		boolean pressingForward = MC.options.keyUp.isDown();
		boolean pressingA = MC.options.keyLeft.isDown();
		boolean pressingD = MC.options.keyRight.isDown();
		boolean orbiting = (pressingA || pressingD) && !dual;

		double currentPull = pullSpeed.getValue();
		if(dual)
			currentPull *= dualMultiplier.getValue();
		if(pressingForward)
			currentPull *= 1.2;

		Vec3 pull = direction.scale(currentPull);
		if(playerPos.y < targetPos.y)
			pull = pull.add(0, lift.getValue(), 0);

		Vec3 orbit = Vec3.ZERO;
		if(orbiting)
		{
			Vec3 up = new Vec3(0, 1, 0);
			Vec3 right = direction.cross(up).normalize();
			if(right.lengthSqr() < 0.01)
				right = direction.cross(new Vec3(1, 0, 0)).normalize();

			if(pressingD)
				orbit = right.scale(orbitSpeed.getValue());
			if(pressingA)
				orbit = right.scale(-orbitSpeed.getValue());
		}

		Vec3 velocity = MC.player.getDeltaMovement().scale(drag.getValue())
			.add(pull).add(orbit);
		MC.player.setDeltaMovement(velocity);
	}

	@Override
	public void onRender(PoseStack matrixStack, float partialTicks)
	{
		if(!renderLines.isChecked() || MC.player == null)
			return;

		Vec3 start = MC.player.position().add(0, 0.8, 0);
		int color = lineColor.getColorI();

		if(leftHook.active)
			RenderUtils.drawLine(matrixStack, start, leftHook.pos, color, false);
		if(rightHook.active)
			RenderUtils.drawLine(matrixStack, start, rightHook.pos, color, false);
	}

	private static class HookState
	{
		private boolean active;
		private Vec3 pos = Vec3.ZERO;
		private Entity entity;

		private void reset()
		{
			active = false;
			pos = Vec3.ZERO;
			entity = null;
		}
	}
}
