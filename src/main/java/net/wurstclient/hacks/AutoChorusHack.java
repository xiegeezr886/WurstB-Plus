/*
 * Copyright (c) 2026 WurstB+ Plus contributors
 *
 * This source code is subject to the terms of the GNU Affero General Public
 * License, version 3. If a copy of the AGPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/agpl-3.0.txt
 */
// Ported from InvincibleMachineGun (GPL-3.0).
package net.wurstclient.hacks;

import java.awt.Color;
import java.util.Comparator;
import java.util.List;
import java.util.stream.StreamSupport;

import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.wurstclient.Category;

import net.wurstclient.SearchTags;
import net.wurstclient.events.RenderListener;
import net.wurstclient.events.UpdateListener;
import net.wurstclient.hack.Hack;
import net.wurstclient.settings.CheckboxSetting;
import net.wurstclient.settings.ColorSetting;
import net.wurstclient.settings.SliderSetting;
import net.wurstclient.settings.SliderSetting.ValueDisplay;
import net.wurstclient.util.InventoryUtils;
import net.wurstclient.util.RenderUtils;
import net.wurstclient.util.RotationUtils;

/**
 * Automatically shoots chorus flowers with a bow, crossbow, trident or
 * throwable projectile.
 */
@SearchTags({"auto chorus", "chorus flower", "chorus fruit", "auto chorus fruit"})
public final class AutoChorusHack extends Hack
	implements UpdateListener, RenderListener
{
	private final SliderSetting range = new SliderSetting("Range",
		"Maximum distance at which chorus flowers are searched.", 50, 10, 100, 1,
		ValueDisplay.INTEGER.withSuffix(" blocks"));

	private final CheckboxSetting autoSwitch = new CheckboxSetting("Auto switch",
		"Automatically selects a projectile weapon from the inventory.", true);

	private final CheckboxSetting rotate = new CheckboxSetting("Rotate",
		"Faces the target before shooting.", true);

	private final SliderSetting delay = new SliderSetting("Delay",
		"Ticks to wait between shots.", 2, 0, 20, 1,
		ValueDisplay.INTEGER.withSuffix(" ticks"));

	private final CheckboxSetting highlight = new CheckboxSetting("Highlight",
		"Draws a box around the current target.", true);

	private final ColorSetting color = new ColorSetting("Highlight color",
		"Color of the target box.", new Color(255, 0, 255, 200));

	private BlockPos currentTarget;
	private int timer;

	public AutoChorusHack()
	{
		super("AutoChorus");
		setCategory(Category.COMBAT);
		addSetting(range);
		addSetting(autoSwitch);
		addSetting(rotate);
		addSetting(delay);
		addSetting(highlight);
		addSetting(color);
	}

	@Override
	protected void onEnable()
	{
		currentTarget = null;
		timer = 0;
		EVENTS.add(UpdateListener.class, this);
		EVENTS.add(RenderListener.class, this);
	}

	@Override
	protected void onDisable()
	{
		EVENTS.remove(UpdateListener.class, this);
		EVENTS.remove(RenderListener.class, this);
		currentTarget = null;

		if(MC.player != null && MC.player.isUsingItem())
			MC.gameMode.releaseUsingItem(MC.player);
	}

	@Override
	public void onUpdate()
	{
		if(MC.player == null || MC.level == null || MC.gameMode == null)
			return;

		currentTarget = findTarget();

		if(currentTarget == null)
		{
			if(MC.player.isUsingItem()
				&& MC.player.getUseItem().getItem() instanceof BowItem)
				MC.gameMode.releaseUsingItem(MC.player);
			return;
		}

		if(autoSwitch.isChecked() && !isValidWeapon(MC.player.getMainHandItem()
			.getItem()))
			InventoryUtils.selectItem(stack -> isValidWeapon(stack.getItem()),
				36);

		Item weapon = MC.player.getMainHandItem().getItem();
		if(!isValidWeapon(weapon))
			return;

		Vec3 targetVec = Vec3.atCenterOf(currentTarget);
		double distance = Math.sqrt(MC.player.getEyePosition()
			.distanceToSqr(targetVec));
		float[] rotations = solveBallistic(targetVec, weapon, distance);
		if(rotations == null)
			return;

		if(rotate.isChecked())
			RotationUtils.getNeededRotations(targetVec).sendPlayerLookPacket();

		shoot(weapon, distance);
	}

	private void shoot(Item item, double distance)
	{
		if(timer > 0)
		{
			timer--;
			return;
		}

		ItemStack stack = MC.player.getMainHandItem();

		if(item instanceof BowItem)
		{
			if(!MC.player.isUsingItem())
			{
				MC.startUseItem();
				return;
			}

			int useTicks = 72000 - MC.player.getUseItemRemainingTicks();
			if(useTicks >= getOptimalBowCharge(distance))
			{
				MC.gameMode.releaseUsingItem(MC.player);
				timer = delay.getValueI();
			}
			return;
		}

		if(item instanceof CrossbowItem)
		{
			if(CrossbowItem.isCharged(stack))
			{
				MC.gameMode.useItem(MC.player, InteractionHand.MAIN_HAND);
				MC.player.swing(InteractionHand.MAIN_HAND);
				timer = delay.getValueI();
			}else if(!MC.player.isUsingItem())
				MC.startUseItem();
			return;
		}

		MC.gameMode.useItem(MC.player, InteractionHand.MAIN_HAND);
		MC.player.swing(InteractionHand.MAIN_HAND);
		timer = delay.getValueI();
	}

	private int getOptimalBowCharge(double distance)
	{
		return distance < 10 ? 12 : 20;
	}

	private float getBowVelocity(int chargeTicks)
	{
		float f = (float)chargeTicks / 20.0F;
		f = (f * f + f * 2.0F) / 3.0F;
		if(f > 1.0F)
			f = 1.0F;
		return f * 3.0F;
	}

	private float[] solveBallistic(Vec3 target, Item weapon, double distance)
	{
		Vec3 playerPos = MC.player.getEyePosition();
		double dx = target.x - playerPos.x;
		double dy = target.y - playerPos.y;
		double dz = target.z - playerPos.z;
		double distH = Math.sqrt(dx * dx + dz * dz);

		double v = 1.0;
		double g = 0.05;

		if(weapon instanceof BowItem)
		{
			v = getBowVelocity(getOptimalBowCharge(distance));
			g = 0.05;
			dy += 0.25;
		}else if(weapon instanceof CrossbowItem)
		{
			v = 3.15;
			g = 0.05;
			dy += 0.25;
		}else if(weapon == Items.SNOWBALL || weapon == Items.EGG)
		{
			v = 1.5;
			g = 0.03;
		}else if(weapon == Items.TRIDENT)
		{
			v = 2.5;
			g = 0.05;
		}

		double v2 = v * v;
		double v4 = v2 * v2;
		double x2 = distH * distH;
		double root = v4 - g * (g * x2 + 2 * dy * v2);

		if(root < 0)
			return null;

		double angleRad = Math.atan2(v2 - Math.sqrt(root), g * distH);
		float yaw = (float)Math.toDegrees(Math.atan2(dz, dx)) - 90;
		float pitch = (float)-Math.toDegrees(angleRad);
		return new float[]{yaw, pitch};
	}

	private BlockPos findTarget()
	{
		BlockPos playerPos = MC.player.blockPosition();
		int r = range.getValueCeil();
		double rSq = (double)r * r;

		var stream = StreamSupport
			.stream(BlockPos.betweenClosed(playerPos.offset(-r, -10, -r),
				playerPos.offset(r, r, r)).spliterator(), false)
			.filter(pos -> pos.distSqr(playerPos) <= rSq)
			.filter(pos -> MC.level.getBlockState(pos)
				.getBlock() == Blocks.CHORUS_FLOWER)
			.filter(this::canSee)
			.map(BlockPos::immutable);

		List<BlockPos> candidates = stream.toList();
		if(candidates.isEmpty())
			return null;

		return candidates.stream()
			.min(Comparator.comparingDouble(pos -> MC.player
				.distanceToSqr(Vec3.atCenterOf(pos))))
			.orElse(null);
	}

	private boolean canSee(BlockPos pos)
	{
		Vec3 start = MC.player.getEyePosition();
		Vec3 end = Vec3.atCenterOf(pos);
		BlockHitResult hit = MC.level.clip(new ClipContext(start, end,
			ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, MC.player));

		return hit.getType() == HitResult.Type.MISS
			|| hit.getBlockPos().equals(pos);
	}

	private boolean isValidWeapon(Item item)
	{
		return item == Items.SNOWBALL || item == Items.EGG
			|| item == Items.TRIDENT
			|| item instanceof BowItem || item instanceof CrossbowItem;
	}

	@Override
	public void onRender(PoseStack matrixStack, float partialTicks)
	{
		if(!highlight.isChecked() || currentTarget == null)
			return;

		RenderUtils.drawOutlinedBox(matrixStack, new AABB(currentTarget),
			color.getColorI(), false);
	}
}
