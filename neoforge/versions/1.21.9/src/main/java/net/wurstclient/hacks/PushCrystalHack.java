/*
 * Copyright (c) 2026 Wurst-Imperium and contributors.
 *
 * This source code is subject to the terms of the GNU Affero General Public
 * License, version 3. If a copy of the AGPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/agpl-3.0.txt
 */

/* Ported from LeavesHack (AGPL-3.0). */
package net.wurstclient.hacks;

import java.awt.Color;
import java.util.Comparator;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.boss.enderdragon.EndCrystal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.wurstclient.Category;
import net.wurstclient.SearchTags;
import net.wurstclient.events.RenderListener;
import net.wurstclient.events.UpdateListener;
import net.wurstclient.hack.Hack;
import net.wurstclient.hack.HackConflictGroup;
import net.wurstclient.settings.CheckboxSetting;
import net.wurstclient.settings.ColorSetting;
import net.wurstclient.settings.SliderSetting;
import net.wurstclient.settings.SliderSetting.ValueDisplay;
import net.wurstclient.settings.SwingHandSetting;
import net.wurstclient.settings.SwingHandSetting.SwingHand;
import net.wurstclient.util.BlockBreaker;
import net.wurstclient.util.BlockPlacer;
import net.wurstclient.util.BlockUtils;
import net.wurstclient.util.DamageUtils;
import net.wurstclient.util.FakePlayerEntity;
import net.wurstclient.util.InventoryUtils;
import net.wurstclient.util.RenderUtils;
import net.wurstclient.util.Rotation;
import net.wurstclient.util.RotationUtils;

/**
 * Simplified port of LeavesHack's PushCrystal. It places a crystal on an
 * existing obsidian/bedrock base near the target, then optionally pushes it
 * one block towards the target with a piston and a redstone block before
 * detonating it. The original's full build solver and Litematica integration
 * are not ported.
 */
@SearchTags({"push crystal", "PushCrystal", "piston crystal"})
public final class PushCrystalHack extends Hack
	implements UpdateListener, RenderListener
{
	private final SliderSetting targetRange = new SliderSetting(
		"Target range", "How far to search for a target.", 6, 1, 16, 0.5,
		ValueDisplay.DECIMAL);

	private final SliderSetting searchRange = new SliderSetting(
		"Search range", "How far around the target to search for a base.",
		5, 1, 8, 0.5, ValueDisplay.DECIMAL);

	private final SliderSetting placeRange = new SliderSetting("Place range",
		"How far to reach when placing blocks.", 4.5, 1, 6, 0.05,
		ValueDisplay.DECIMAL);

	private final SliderSetting minDamage = new SliderSetting("Min damage",
		"Minimum damage the crystal must deal to the target.", 4, 0, 36, 0.5,
		ValueDisplay.DECIMAL);

	private final SliderSetting maxSelfDamage = new SliderSetting(
		"Max self damage", "Maximum allowed damage to the local player.", 12, 0,
		36, 0.5, ValueDisplay.DECIMAL);

	private final CheckboxSetting antiSuicide = new CheckboxSetting(
		"Anti suicide", "Rejects crystals that could kill the local player.",
		true);

	private final SliderSetting actionDelay = new SliderSetting("Action delay",
		"Ticks to wait between placement steps.", 3, 0, 20, 1,
		ValueDisplay.INTEGER.withSuffix(" ticks"));

	private final CheckboxSetting pushWithPiston = new CheckboxSetting(
		"Piston push",
		"Push the crystal towards the target with a piston and redstone block.",
		true);

	private final CheckboxSetting breakPiston = new CheckboxSetting(
		"Break piston", "Break the piston after detonating the crystal.", true)
			.visibleWhen(pushWithPiston::isChecked);

	private final CheckboxSetting rotate = new CheckboxSetting("Rotate",
		"Rotates the player when placing the piston and crystal.", true);

	private final CheckboxSetting pauseOnUse = new CheckboxSetting(
		"Pause while using", "Pauses while you are using an item.", true);

	private final SwingHandSetting swingHand =
		new SwingHandSetting(this, SwingHand.CLIENT);

	private final CheckboxSetting render = new CheckboxSetting("Render",
		"Shows the planned crystal and piston positions.", true);

	private final ColorSetting crystalColor = new ColorSetting("Crystal color",
		"Color of the planned crystal position.", new Color(255, 0, 0, 80));

	private final ColorSetting pistonColor = new ColorSetting("Piston color",
		"Color of the planned piston position.", new Color(255, 255, 255, 80));

	private final ColorSetting redstoneColor = new ColorSetting(
		"Redstone color", "Color of the planned redstone block position.",
		new Color(255, 100, 0, 80));

	private Plan plan;
	private Phase phase = Phase.NONE;
	private int timer;
	private int waitTicks;

	public PushCrystalHack()
	{
		super("PushCrystal");
		setCategory(Category.COMBAT);
		addConflictGroup(HackConflictGroup.COMBAT_TARGETING);
		addSetting(targetRange);
		addSetting(searchRange);
		addSetting(placeRange);
		addSetting(minDamage);
		addSetting(maxSelfDamage);
		addSetting(antiSuicide);
		addSetting(actionDelay);
		addSetting(pushWithPiston);
		addSetting(breakPiston);
		addSetting(rotate);
		addSetting(pauseOnUse);
		addSetting(swingHand);
		addSetting(render);
		addSetting(crystalColor);
		addSetting(pistonColor);
		addSetting(redstoneColor);
	}

	@Override
	protected void onEnable()
	{
		reset();
		timer = 0;
		EVENTS.add(UpdateListener.class, this);
		EVENTS.add(RenderListener.class, this);
	}

	@Override
	protected void onDisable()
	{
		EVENTS.remove(UpdateListener.class, this);
		EVENTS.remove(RenderListener.class, this);
		reset();
	}

	@Override
	public void onUpdate()
	{
		if(MC.player == null || MC.level == null)
		{
			reset();
			return;
		}

		if(pauseOnUse.isChecked() && (MC.player.isUsingItem()
			|| MC.options.keyUse.isDown()))
		{
			reset();
			return;
		}

		if(timer > 0)
		{
			timer--;
			return;
		}

		LivingEntity target = findTarget();
		if(target == null)
		{
			reset();
			return;
		}

		if(plan == null)
		{
			plan = findPlan(target);
			if(plan == null)
				return;
			phase = Phase.CRYSTAL;
			waitTicks = 0;
		}

		switch(phase)
		{
			case CRYSTAL:
			updateCrystal();
			break;

			case PISTON:
			updatePiston();
			break;

			case REDSTONE:
			updateRedstone();
			break;

			case DETONATE:
			updateDetonate();
			break;

			case CLEANUP:
			updateCleanup();
			break;

			default:
			break;
		}
	}

	private void updateCrystal()
	{
		if(crystalExists(plan.crystalPos))
		{
			phase = pushWithPiston.isChecked() ? Phase.PISTON : Phase.DETONATE;
			return;
		}

		if(!isReplaceable(plan.crystalPos)
			|| !isReplaceable(plan.crystalPos.above()))
		{
			reset();
			return;
		}

		if(!placeCrystal(plan))
			return;

		timer = actionDelay.getValueI() + 1;
		phase = pushWithPiston.isChecked() ? Phase.PISTON : Phase.DETONATE;
	}

	private void updatePiston()
	{
		if(!pushWithPiston.isChecked() || plan.pistonPos == null)
		{
			phase = Phase.DETONATE;
			return;
		}

		BlockState state = BlockUtils.getState(plan.pistonPos);
		if(!state.canBeReplaced())
		{
			if(isPiston(state.getBlock()))
				phase = Phase.REDSTONE;
			else
				reset();
			return;
		}

		if(!placePiston(plan))
			return;

		timer = actionDelay.getValueI() + 1;
		phase = Phase.REDSTONE;
	}

	private void updateRedstone()
	{
		if(plan.redstonePos == null)
		{
			reset();
			return;
		}

		if(BlockUtils.getBlock(plan.redstonePos) == Blocks.REDSTONE_BLOCK)
		{
			phase = Phase.DETONATE;
			waitTicks = 0;
			timer = actionDelay.getValueI() + 1;
			return;
		}

		if(!BlockUtils.getState(plan.redstonePos).canBeReplaced())
		{
			reset();
			return;
		}

		if(!placeRedstone(plan))
			return;

		timer = actionDelay.getValueI() + 1;
		waitTicks = 0;
		phase = Phase.DETONATE;
	}

	private void updateDetonate()
	{
		// Give the piston a few ticks to extend before attacking.
		if(waitTicks < 3)
		{
			waitTicks++;
			timer = 1;
			return;
		}

		attackCrystal(plan);

		if(breakPiston.isChecked() && plan.pistonPos != null)
		{
			phase = Phase.CLEANUP;
			timer = 1;
			return;
		}

		reset();
		timer = actionDelay.getValueI() + 1;
	}

	private void updateCleanup()
	{
		if(plan.pistonPos != null
			&& isPiston(BlockUtils.getBlock(plan.pistonPos)))
		{
			BlockBreaker.breakOneBlock(plan.pistonPos);
			timer = 1;
			return;
		}

		reset();
		timer = actionDelay.getValueI() + 1;
	}

	private LivingEntity findTarget()
	{
		double rangeSq = Math.pow(targetRange.getValue(), 2);
		Comparator<Entity> closestToPlayer =
			Comparator.comparingDouble(e -> MC.player.distanceToSqr(e));

		Stream<Entity> stream = StreamSupport
			.stream(MC.level.entitiesForRendering().spliterator(), false)
			.filter(e -> !e.isRemoved())
			.filter(e -> e instanceof LivingEntity
				&& ((LivingEntity)e).getHealth() > 0)
			.filter(e -> e != MC.player).filter(e -> !(e instanceof FakePlayerEntity))
			.filter(e -> !(e instanceof Player player
				&& player.getAbilities().instabuild))
			.filter(e -> !WURST.getFriends().contains(e.getScoreboardName()))
			.filter(e -> MC.player.distanceToSqr(e) <= rangeSq);

		return (LivingEntity)stream.sorted(closestToPlayer).findFirst()
			.orElse(null);
	}

	private Plan findPlan(LivingEntity target)
	{
		double search = searchRange.getValue();
		double searchSq = Math.pow(search, 2);
		float selfHealth =
			MC.player.getHealth() + MC.player.getAbsorptionAmount();
		Vec3 targetCenter = target.position().add(0,
			target.getEyeHeight(target.getPose()) * 0.5, 0);
		Plan best = null;
		double bestScore = Double.NEGATIVE_INFINITY;

		for(BlockPos crystalPos : BlockUtils.getAllInBox(
			target.blockPosition(), (int)Math.ceil(search)))
		{
			if(targetCenter.distanceToSqr(Vec3.atCenterOf(crystalPos)) > searchSq)
				continue;

			BlockPos basePos = crystalPos.below();
			if(!isCrystalBase(basePos))
				continue;
			if(!isReplaceable(crystalPos)
				|| !isReplaceable(crystalPos.above()))
				continue;
			if(!isWithinPlaceRange(crystalPos)
				|| !isWithinPlaceRange(basePos))
				continue;
			if(!hasFreeCrystalSpace(crystalPos))
				continue;

			Vec3 explosion = new Vec3(crystalPos.getX() + 0.5,
				crystalPos.getY(), crystalPos.getZ() + 0.5);
			float damage = DamageUtils.calculateDamage(explosion, target);
			float selfDamage =
				DamageUtils.calculateDamage(explosion, MC.player);
			if(damage < minDamage.getValueF()
				|| selfDamage > maxSelfDamage.getValueF())
				continue;
			if(antiSuicide.isChecked() && selfDamage >= selfHealth)
				continue;

			Direction pushDir = getPushDirection(crystalPos, target);
			BlockPos pistonPos = null;
			BlockPos redstonePos = null;

			if(pushWithPiston.isChecked())
			{
				pistonPos = crystalPos.relative(pushDir.getOpposite());
				if(!isReplaceable(pistonPos)
					|| !isWithinPlaceRange(pistonPos)
					|| !hasFreePistonSpace(pistonPos))
					continue;

				redstonePos = findRedstonePos(pistonPos, crystalPos, pushDir);
				if(redstonePos == null)
					continue;
			}

			double score = damage - selfDamage * 0.25;
			if(score > bestScore)
			{
				bestScore = score;
				best = new Plan(basePos.immutable(), crystalPos.immutable(),
					pushDir, pistonPos, redstonePos, damage, selfDamage);
			}
		}

		return best;
	}

	private Direction getPushDirection(BlockPos crystalPos, LivingEntity target)
	{
		BlockPos targetPos = target.blockPosition();
		int dx = targetPos.getX() - crystalPos.getX();
		int dz = targetPos.getZ() - crystalPos.getZ();

		if(Math.abs(dx) >= Math.abs(dz))
			return dx >= 0 ? Direction.EAST : Direction.WEST;
		return dz >= 0 ? Direction.SOUTH : Direction.NORTH;
	}

	private BlockPos findRedstonePos(BlockPos pistonPos, BlockPos crystalPos,
		Direction pushDir)
	{
		for(Direction side : Direction.values())
		{
			if(side == pushDir)
				continue;

			BlockPos pos = pistonPos.relative(side);
			if(pos.equals(crystalPos) || pos.equals(crystalPos.above()))
				continue;
			if(!isReplaceable(pos) || !isWithinPlaceRange(pos))
				continue;

			return pos.immutable();
		}

		return null;
	}

	private boolean placeCrystal(Plan plan)
	{
		InventoryUtils.selectItem(Items.END_CRYSTAL, 36);
		if(!MC.player.getMainHandItem().is(Items.END_CRYSTAL))
			return false;

		Vec3 hitVec = Vec3.atCenterOf(plan.basePos).add(0, 0.5, 0);
		face(hitVec);
		IMC.getInteractionManager().rightClickBlock(plan.basePos,
			Direction.UP, hitVec);
		swingHand.swing(InteractionHand.MAIN_HAND);
		return true;
	}

	private boolean placePiston(Plan plan)
	{
		InventoryUtils.selectItem(stack -> stack.is(Items.PISTON)
			|| stack.is(Items.STICKY_PISTON), 36);
		if(!MC.player.getMainHandItem().is(Items.PISTON)
			&& !MC.player.getMainHandItem().is(Items.STICKY_PISTON))
			return false;

		BlockPlacer.BlockPlacingParams params =
			BlockPlacer.getBlockPlacingParams(plan.pistonPos);
		if(params == null)
			return false;

		// Pistons face away from the player, so look opposite to the push.
		if(rotate.isChecked())
			WURST.getRotationFaker().setRotationPacket(
				new Rotation(getYaw(plan.pushDir.getOpposite()), 5));

		IMC.getInteractionManager().rightClickBlock(params.neighbor(),
			params.side(), params.hitVec());
		swingHand.swing(InteractionHand.MAIN_HAND);
		return true;
	}

	private boolean placeRedstone(Plan plan)
	{
		InventoryUtils.selectItem(Items.REDSTONE_BLOCK, 36);
		if(!MC.player.getMainHandItem().is(Items.REDSTONE_BLOCK))
			return false;

		if(!BlockPlacer.placeOneBlock(plan.redstonePos))
			return false;

		swingHand.swing(InteractionHand.MAIN_HAND);
		return true;
	}

	private void attackCrystal(Plan plan)
	{
		BlockPos pushed =
			plan.pistonPos == null ? plan.crystalPos
				: plan.crystalPos.relative(plan.pushDir);
		int minX = Math.min(plan.crystalPos.getX(), pushed.getX());
		int minZ = Math.min(plan.crystalPos.getZ(), pushed.getZ());
		int maxX = Math.max(plan.crystalPos.getX(), pushed.getX());
		int maxZ = Math.max(plan.crystalPos.getZ(), pushed.getZ());
		AABB box = new AABB(minX, plan.crystalPos.getY(), minZ, maxX + 1,
			plan.crystalPos.getY() + 2, maxZ + 1);

		for(Entity entity : MC.level.getEntities((Entity)null, box))
		{
			if(!(entity instanceof EndCrystal) || entity.isRemoved())
				continue;

			face(entity.getBoundingBox().getCenter());
			MC.gameMode.attack(MC.player, entity);
			swingHand.swing(InteractionHand.MAIN_HAND);
			return;
		}
	}

	private boolean hasFreeCrystalSpace(BlockPos crystalPos)
	{
		AABB box = new AABB(crystalPos.getX(), crystalPos.getY(),
			crystalPos.getZ(), crystalPos.getX() + 1, crystalPos.getY() + 2,
			crystalPos.getZ() + 1);

		for(Entity entity : MC.level.getEntities((Entity)null, box))
			if(!(entity instanceof EndCrystal))
				return false;

		return true;
	}

	private boolean hasFreePistonSpace(BlockPos pos)
	{
		AABB box = new AABB(pos);
		if(MC.player.getBoundingBox().intersects(box))
			return false;

		for(Entity entity : MC.level.getEntities((Entity)null, box))
			if(!(entity instanceof EndCrystal))
				return false;

		return true;
	}

	private boolean crystalExists(BlockPos crystalPos)
	{
		AABB box = new AABB(crystalPos.getX(), crystalPos.getY(),
			crystalPos.getZ(), crystalPos.getX() + 1, crystalPos.getY() + 2,
			crystalPos.getZ() + 1);

		for(Entity entity : MC.level.getEntities((Entity)null, box))
			if(entity instanceof EndCrystal && !entity.isRemoved())
				return true;

		return false;
	}

	private boolean isCrystalBase(BlockPos pos)
	{
		Block block = BlockUtils.getBlock(pos);
		return block == Blocks.OBSIDIAN || block == Blocks.BEDROCK;
	}

	private boolean isReplaceable(BlockPos pos)
	{
		return BlockUtils.getState(pos).canBeReplaced();
	}

	private boolean isWithinPlaceRange(BlockPos pos)
	{
		return RotationUtils.getEyesPos()
			.distanceToSqr(Vec3.atCenterOf(pos)) <= placeRange.getValueSq();
	}

	private static boolean isPiston(Block block)
	{
		return block == Blocks.PISTON || block == Blocks.STICKY_PISTON;
	}

	private void face(Vec3 vec)
	{
		if(rotate.isChecked())
			WURST.getRotationFaker().faceVectorPacket(vec);
	}

	private static float getYaw(Direction direction)
	{
		return switch(direction)
		{
			case SOUTH -> 0F;
			case WEST -> 90F;
			case NORTH -> 180F;
			case EAST -> -90F;
			default -> 0F;
		};
	}

	private void reset()
	{
		plan = null;
		phase = Phase.NONE;
		waitTicks = 0;
	}

	@Override
	public void onRender(PoseStack matrixStack, float partialTicks)
	{
		if(!render.isChecked() || plan == null)
			return;

		boolean depthTest = false;
		int crystal = crystalColor.getColorI(120);
		int piston = pistonColor.getColorI(120);
		int redstone = redstoneColor.getColorI(120);

		AABB crystalBox = new AABB(plan.crystalPos.getX(),
			plan.crystalPos.getY(), plan.crystalPos.getZ(),
			plan.crystalPos.getX() + 1, plan.crystalPos.getY() + 2,
			plan.crystalPos.getZ() + 1);
		RenderUtils.drawSolidBox(matrixStack, crystalBox, crystal, depthTest);
		RenderUtils.drawOutlinedBox(matrixStack, crystalBox, crystal,
			depthTest);

		if(plan.pistonPos != null)
		{
			AABB pistonBox = new AABB(plan.pistonPos);
			RenderUtils.drawSolidBox(matrixStack, pistonBox, piston,
				depthTest);
			RenderUtils.drawOutlinedBox(matrixStack, pistonBox, piston,
				depthTest);
		}

		if(plan.redstonePos != null)
		{
			AABB redstoneBox = new AABB(plan.redstonePos);
			RenderUtils.drawSolidBox(matrixStack, redstoneBox, redstone,
				depthTest);
			RenderUtils.drawOutlinedBox(matrixStack, redstoneBox, redstone,
				depthTest);
		}
	}

	private enum Phase
	{
		NONE,
		CRYSTAL,
		PISTON,
		REDSTONE,
		DETONATE,
		CLEANUP
	}

	private static final class Plan
	{
		private final BlockPos basePos;
		private final BlockPos crystalPos;
		private final Direction pushDir;
		private final BlockPos pistonPos;
		private final BlockPos redstonePos;
		@SuppressWarnings("unused")
		private final float targetDamage;
		@SuppressWarnings("unused")
		private final float selfDamage;

		private Plan(BlockPos basePos, BlockPos crystalPos, Direction pushDir,
			BlockPos pistonPos, BlockPos redstonePos, float targetDamage,
			float selfDamage)
		{
			this.basePos = basePos;
			this.crystalPos = crystalPos;
			this.pushDir = pushDir;
			this.pistonPos = pistonPos;
			this.redstonePos = redstonePos;
			this.targetDamage = targetDamage;
			this.selfDamage = selfDamage;
		}
	}
}
