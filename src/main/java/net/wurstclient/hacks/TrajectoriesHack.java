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
import java.util.ArrayList;
import java.util.function.Predicate;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.EggItem;
import net.minecraft.world.item.EnderpearlItem;
import net.minecraft.world.item.ExperienceBottleItem;
import net.minecraft.world.item.FishingRodItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ProjectileWeaponItem;
import net.minecraft.world.item.SnowballItem;
import net.minecraft.world.item.ThrowablePotionItem;
import net.minecraft.world.item.TridentItem;
import net.minecraft.world.level.ClipContext.Fluid;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.wurstclient.Category;
import net.wurstclient.SearchTags;
import net.wurstclient.events.RenderListener;
import net.wurstclient.hack.Hack;
import net.wurstclient.settings.ColorSetting;
import net.wurstclient.util.BlockUtils;
import net.wurstclient.util.EntityUtils;
import net.wurstclient.util.ProjectilePhysics;
import net.wurstclient.util.ProjectilePhysics.Velocity;
import net.wurstclient.util.RenderUtils;

@SearchTags({"ArrowTrajectories", "ArrowPrediction", "aim assist",
	"arrow trajectories", "bow trajectories"})
public final class TrajectoriesHack extends Hack implements RenderListener
{
	private final ColorSetting missColor = new ColorSetting("Miss Color",
		"Color of the trajectory when it doesn't hit anything.", Color.GRAY);
	
	private final ColorSetting entityHitColor =
		new ColorSetting("Entity Hit Color",
			"Color of the trajectory when it hits an entity.", Color.RED);
	
	private final ColorSetting blockHitColor =
		new ColorSetting("Block Hit Color",
			"Color of the trajectory when it hits a block.", Color.GREEN);
	
	public TrajectoriesHack()
	{
		super("Trajectories");
		setCategory(Category.RENDER);
		addSetting(missColor);
		addSetting(entityHitColor);
		addSetting(blockHitColor);
	}
	
	@Override
	protected void onEnable()
	{
		EVENTS.add(RenderListener.class, this);
	}
	
	@Override
	protected void onDisable()
	{
		EVENTS.remove(RenderListener.class, this);
	}
	
	@Override
	public void onRender(PoseStack matrixStack, float partialTicks)
	{
		Trajectory trajectory = getTrajectory(partialTicks);
		if(trajectory.isEmpty())
			return;
		
		ColorSetting color = getColor(trajectory);
		int lineColor = color.getColorI(0xC0);
		int quadColor = color.getColorI(0x40);
		
		AABB endBox = trajectory.getEndBox();
		ArrayList<Vec3> path = trajectory.path();
		
		RenderUtils.drawSolidBox(matrixStack, endBox, quadColor, false);
		RenderUtils.drawOutlinedBox(matrixStack, endBox, lineColor, false);
		
		RenderUtils.drawCurvedLine(matrixStack, path, lineColor, false);
	}
	
	private Trajectory getTrajectory(float partialTicks)
	{
		LocalPlayer player = MC.player;
		ArrayList<Vec3> path = new ArrayList<>();
		HitResult.Type type = HitResult.Type.MISS;
		
		// Find the hand with a throwable item
		ItemStack stack = player.getMainHandItem();
		if(!isThrowable(stack))
		{
			stack = player.getOffhandItem();
			
			// If neither hand has a throwable item, return empty path
			if(!isThrowable(stack))
				return new Trajectory(path, type);
		}
		
		// Calculate item-specific values
		Item item = stack.getItem();
		double throwPower = getThrowPower(stack);
		double gravity = getProjectileGravity(stack);
		double drag = getProjectileDrag(stack);
		Fluid fluidHandling = getFluidHandling(item);
		
		// Prepare yaw and pitch (药水与经验瓶原版抬头 20 度)
		double yaw = Math.toRadians(player.getYRot());
		double pitch = Math.toRadians(player.getXRot() + getPitchOffset(stack));
		
		// Calculate starting position
		Vec3 arrowPos = EntityUtils.getLerpedPos(player, partialTicks)
			.add(getHandOffset(stack, yaw));
		
		// Calculate starting motion
		Vec3 arrowMotion = getStartingMotion(yaw, pitch, throwPower);
		Velocity velocity = new Velocity(arrowMotion.x, arrowMotion.y,
			arrowMotion.z);
		
		// 鱼漂的每 tick 顺序不同：先减重力，再推进位置，最后才乘阻力
		boolean gravityFirst = item instanceof FishingRodItem;
		
		// Build the path
		path.add(arrowPos);
		for(int tick = 0; tick < 100; tick++)
		{
			if(gravityFirst)
				velocity = ProjectilePhysics.gravity(velocity, gravity);
			
			// 原版用"上一 tick 末的速度"推进整个 tick 的位移
			Vec3 nextPos = arrowPos.add(velocity.x(), velocity.y(), velocity.z());
			Vec3 tickMotion = nextPos.subtract(arrowPos);
			
			// 细分只发生在这一 tick 的直线段上，位置仍严格落在原版轨迹上；
			// 细分是为了折线更平滑、碰撞检测更细
			for(int step = 1; step <= ProjectilePhysics.SUB_STEPS; step++)
			{
				Vec3 subPos = arrowPos.add(tickMotion
					.scale((double)step / ProjectilePhysics.SUB_STEPS));
				Vec3 lastPos = path.get(path.size() - 1);
				
				// Check for block collision
				BlockHitResult bResult =
					BlockUtils.raycast(lastPos, subPos, fluidHandling);
				if(bResult.getType() != HitResult.Type.MISS)
				{
					type = HitResult.Type.BLOCK;
					path.add(bResult.getLocation());
					return new Trajectory(path, type);
				}
				
				// Check for entity collision
				AABB box = new AABB(lastPos, subPos);
				Predicate<Entity> predicate =
					e -> !e.isSpectator() && e.isPickable();
				double maxDistSq = 64 * 64;
				EntityHitResult eResult = ProjectileUtil.getEntityHitResult(
					player, lastPos, subPos, box, predicate, maxDistSq);
				if(eResult != null && eResult.getType() != HitResult.Type.MISS)
				{
					type = HitResult.Type.ENTITY;
					path.add(eResult.getLocation());
					return new Trajectory(path, type);
				}
				
				path.add(subPos);
			}
			
			arrowPos = nextPos;
			if(gravityFirst)
				velocity = ProjectilePhysics.drag(velocity, drag);
			else
				velocity =
					ProjectilePhysics.dragAndGravity(velocity, drag, gravity);
		}
		
		return new Trajectory(path, type);
	}
	
	private boolean isThrowable(ItemStack stack)
	{
		if(stack.isEmpty())
			return false;
		
		Item item = stack.getItem();
		return item instanceof ProjectileWeaponItem || item instanceof SnowballItem
			|| item instanceof EggItem || item instanceof EnderpearlItem
			|| item instanceof ThrowablePotionItem
			|| item instanceof ExperienceBottleItem
			|| item instanceof FishingRodItem || item instanceof TridentItem;
	}
	
	/**
	 * 出手速度。每一项都对照 1.20.2 真源，证据见
	 * docs/openeepsilon-verdicts/Trajectories.md。
	 *
	 * <p>旧实现给所有非投掷武器一律 1.5（药水实际是 0.5、三叉戟是 2.5、鱼漂约 0.3），
	 * 并且把弩也当成弓来按蓄力公式算。
	 */
	private double getThrowPower(ItemStack stack)
	{
		Item item = stack.getItem();
		
		// 弩：CrossbowItem.java:43,80-81，ARROW_POWER = 3.15
		if(item instanceof CrossbowItem)
			return 3.15;
		
		// 弓：BowItem.java:40-41,47，getPowerForTime(ticks) * 3.0
		if(item instanceof ProjectileWeaponItem)
		{
			// 没在拉弓时 getUseItemRemainingTicks() 为 0，直接代入会算出 3600 这种
			// 荒谬的蓄力值；此时沿用旧行为显示"满蓄力"的假设弧线
			if(!MC.player.isUsingItem()
				|| MC.player.getUseItem().getItem() != item)
				return 3;
			
			float bowPower =
				(72000 - MC.player.getUseItemRemainingTicks()) / 20F;
			bowPower = bowPower * bowPower + bowPower * 2F;
			
			// 旧实现在 bowPower <= 0.3 时硬改成 3（满蓄力）。原版
			// getPowerForTime 归一化后 < 0.1（即这里 < 0.3）时**根本不会发射**，
			// 所以那样画出来的满蓄力弧线与实际完全不符；这里如实使用当前蓄力。
			return Math.min(bowPower, 3F);
		}
		
		// 三叉戟：TridentItem.java:69，投掷分支 j == 0，所以就是 2.5
		if(item instanceof TridentItem)
			return 2.5;
		
		// 喷溅/滞留药水：ThrowablePotionItem.java:21
		if(item instanceof ThrowablePotionItem)
			return 0.5;
		
		// 经验瓶：ExperienceBottleItem.java:38
		if(item instanceof ExperienceBottleItem)
			return 0.7;
		
		// 鱼漂：FishingHook.java:92-97，先归一化到 0.6 再乘均值 0.5 的随机因子
		if(item instanceof FishingRodItem)
			return 0.3;
		
		// 雪球/鸡蛋/末影珍珠：SnowballItem.java:33、EggItem.java:33、
		// EnderpearlItem.java:34 都是 1.5
		return 1.5;
	}
	
	/**
	 * 重力。箭与三叉戟是 {@code AbstractArrow.java:241,254} 的字面量 0.05；
	 * 雪球/鸡蛋/末影珍珠/鱼漂是 {@code ThrowableProjectile.java:94-95} 与
	 * {@code FishingHook.java:223} 的 0.03；药水 {@code ThrownPotion.java:55-56} 是 0.05；
	 * 经验瓶 {@code ThrownExperienceBottle.java:33-34} 是 0.07。
	 *
	 * <p>旧实现写的是药水 0.4、鱼漂 0.15、三叉戟 0.015，与真源都不符。
	 */
	private double getProjectileGravity(ItemStack stack)
	{
		Item item = stack.getItem();
		
		if(item instanceof ThrowablePotionItem)
			return 0.05;
		
		if(item instanceof ExperienceBottleItem)
			return 0.07;
		
		if(item instanceof ProjectileWeaponItem || item instanceof TridentItem)
			return 0.05;
		
		return 0.03;
	}
	
	/** 空气阻力：只有鱼漂是 {@code FishingHook.java:232-233} 的 0.92，其余都是 0.99。 */
	private double getProjectileDrag(ItemStack stack)
	{
		return stack.getItem() instanceof FishingRodItem
			? ProjectilePhysics.FISHING_DRAG : ProjectilePhysics.DRAG;
	}
	
	/**
	 * 原版把药水与经验瓶的俯仰角抬高 20 度（{@code ThrowablePotionItem.java:21} 与
	 * {@code ExperienceBottleItem.java:38} 里的 {@code -20.0F} 会加到俯仰角上）。
	 */
	private double getPitchOffset(ItemStack stack)
	{
		Item item = stack.getItem();
		return item instanceof ThrowablePotionItem
			|| item instanceof ExperienceBottleItem ? -20 : 0;
	}
	
	private Fluid getFluidHandling(Item item)
	{
		if(item instanceof FishingRodItem)
			return Fluid.ANY;
		
		return Fluid.NONE;
	}
	
	/**
	 * 出手点。箭与投掷物是 {@code (getX(), getEyeY() - 0.1, getZ())}
	 * （{@code AbstractArrow.java:79}、{@code ThrowableProjectile.java:27}，注意原版
	 * <b>没有</b>横向偏移）；鱼漂是 {@code (getX() - sin(yaw) * 0.3, getEyeY(),
	 * getZ() + cos(yaw) * 0.3)}（{@code FishingHook.java:88-91}）。
	 *
	 * <p>旧实现给所有投射物加了 ±0.16 的横向手部偏移，原版并不存在这个偏移。
	 */
	private Vec3 getHandOffset(ItemStack stack, double yaw)
	{
		double eyeHeight = MC.player.getEyeHeight();
		
		if(!(stack.getItem() instanceof FishingRodItem))
			return new Vec3(0, eyeHeight - 0.1, 0);
		
		return new Vec3(-Math.sin(yaw) * 0.3, eyeHeight,
			Math.cos(yaw) * 0.3);
	}
	
	private Vec3 getStartingMotion(double yaw, double pitch, double throwPower)
	{
		double cosOfPitch = Math.cos(pitch);
		
		double arrowMotionX = -Math.sin(yaw) * cosOfPitch;
		double arrowMotionY = -Math.sin(pitch);
		double arrowMotionZ = Math.cos(yaw) * cosOfPitch;
		
		return new Vec3(arrowMotionX, arrowMotionY, arrowMotionZ).normalize()
			.scale(throwPower);
	}
	
	private ColorSetting getColor(Trajectory trajectory)
	{
		return switch(trajectory.type())
		{
			case MISS -> missColor;
			case ENTITY -> entityHitColor;
			case BLOCK -> blockHitColor;
		};
	}
	
	private record Trajectory(ArrayList<Vec3> path, HitResult.Type type)
	{
		public boolean isEmpty()
		{
			return path.isEmpty();
		}
		
		public AABB getEndBox()
		{
			Vec3 end = path.get(path.size() - 1);
			return new AABB(end.subtract(0.5, 0.5, 0.5), end.add(0.5, 0.5, 0.5));
		}
	}
}
