/*
 * Copyright (c) 2026 WurstB+ Plus contributors
 *
 * This source code is subject to the terms of the GNU Affero General Public
 * License, version 3. If a copy of the AGPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/agpl-3.0.txt
 */
package net.wurstclient.hacks;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.PriorityQueue;
import java.util.Set;

import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.protocol.game.ServerboundSetCarriedItemPacket;
import net.minecraft.network.protocol.game.ServerboundUseItemOnPacket;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.FallingBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.wurstclient.Category;
import net.wurstclient.SearchTags;
import net.wurstclient.events.RenderListener;
import net.wurstclient.events.UpdateListener;
import net.wurstclient.hack.Hack;
import net.wurstclient.hacks.aura.OpenAura;
import net.wurstclient.settings.CheckboxSetting;
import net.wurstclient.settings.EnumSetting;
import net.wurstclient.settings.SliderSetting;
import net.wurstclient.settings.SliderSetting.ValueDisplay;
import net.wurstclient.settings.SwingHandSetting;
import net.wurstclient.settings.SwingHandSetting.SwingHand;
import net.wurstclient.util.BlockUtils;
import net.wurstclient.util.Rotation;
import net.wurstclient.util.RotationQueue;

/**
 * Scaffold ported from OpenOpal's ScaffoldModule core (candidate search,
 * support raytracing and placement), exposed with Wurst's settings system.
 */
@SearchTags({"scaffold walk", "BridgeWalk", "bridge walk", "AutoBridge",
	"auto bridge", "tower"})
public final class ScaffoldWalkHack extends Hack
	implements UpdateListener, RenderListener
{
	private static final Direction[] SUPPORT_DIRECTIONS = {Direction.DOWN,
		Direction.EAST, Direction.WEST, Direction.NORTH, Direction.SOUTH,
		Direction.UP};
	private static final double[] FOOT_OFFSETS = {0, 0.301D, -0.301D};
	private static final double[] LOOKAHEAD_STEPS = {0, 0.35D, 0.7D, 1.05D,
		1.4D};
	private static final double[] HIT_OFFSETS = {0, 0.24D, -0.24D};
	private static final double MAX_PLACEMENT_DISTANCE_SQ = 20.25D;

	private final EnumSetting<Mode> mode = new EnumSetting<>("Mode",
		"Server-specific bridging behaviour.", Mode.values(),
		Mode.WATCHDOG);

	private final SliderSetting tellyTick = new SliderSetting("Telly ticks",
		"Rotation smoothing ticks used by telly modes.", 5, 0, 10, 1,
		ValueDisplay.INTEGER);

	private final CheckboxSetting stNoSwing = new CheckboxSetting(
		"ST No Swing", "SilenceTelly: skips the swing animation.", false);

	private final CheckboxSetting tower = new CheckboxSetting("Tower",
		"Hold jump to tower up.", false);

	private final EnumSetting<SwitchMode> switchMode =
		new EnumSetting<>("Switch mode", SwitchMode.values(),
			SwitchMode.HOTBAR);

	private final SwingHandSetting swingHand =
		new SwingHandSetting(this, SwingHand.CLIENT);

	private final CheckboxSetting snapRotations =
		new CheckboxSetting("Snap rotations", false);

	private final CheckboxSetting sameY = new CheckboxSetting("Same Y",
		"Keeps placing at the same Y level while bridging.", true);

	private final CheckboxSetting autoJump = new CheckboxSetting("Auto jump",
		"Jumps automatically to keep the same Y.", true);

	private final CheckboxSetting overrideRaycast = new CheckboxSetting(
		"Override raycast", "Validates every placement with a block raycast.",
		true);

	private final CheckboxSetting interactBeforePlace = new CheckboxSetting(
		"Interact before place",
		"Sends an extra interact packet before placing.", false);

	private final CheckboxSetting blockOverlay = new CheckboxSetting(
		"Block overlay", "Outlines the block that is about to be placed.",
		true);

	private final CheckboxSetting safeWalk = new CheckboxSetting("SafeWalk",
		"Prevents walking off the scaffold edge.", true);

	private final CheckboxSetting keepSprint = new CheckboxSetting(
		"Keep Sprint FOV", "Keeps sprinting after a placement.", false);

	private final SliderSetting range = new SliderSetting("Range",
		"How far to reach when placing blocks.", 4.5, 3, 6, 0.05,
		ValueDisplay.DECIMAL);

	private final SliderSetting placeDelay = new SliderSetting("Place delay",
		"Delay between block placements in ticks.", 0, 0, 5, 1,
		ValueDisplay.INTEGER);

	private RotationQueue rotationQueue;
	private int oldSlot = -1;
	private int placementCooldown;
	private int sameYPos;
	private int groundTicks;
	private int airTicks;
	private BlockPos lastTarget;
	private final ArrayDeque<BlockPos> lastPlaced = new ArrayDeque<>();

	public ScaffoldWalkHack()
	{
		super("ScaffoldWalk");
		setCategory(Category.BLOCKS);
		addSetting(mode);
		addSetting(tower);
		addSetting(switchMode);
		addSetting(swingHand);
		addSetting(tellyTick);
		addSetting(stNoSwing);
		addSetting(snapRotations);
		addSetting(sameY);
		addSetting(autoJump);
		addSetting(overrideRaycast);
		addSetting(interactBeforePlace);
		addSetting(blockOverlay);
		addSetting(safeWalk);
		addSetting(keepSprint);
		addSetting(range);
		addSetting(placeDelay);
	}

	@Override
	public String getRenderName()
	{
		return getName() + " [" + mode.getSelected() + "]";
	}

	@Override
	protected void onEnable()
	{
		oldSlot = -1;
		placementCooldown = 0;
		lastTarget = null;
		lastPlaced.clear();
		sameYPos = MC.player == null ? 0 : Mth.floor(MC.player.getY());
		rotationQueue = new RotationQueue(
			RotationQueue.Priority.BLOCK_PLACEMENT);
		rotationQueue.start();
		EVENTS.add(UpdateListener.class, this);
		EVENTS.add(RenderListener.class, this);
	}

	@Override
	protected void onDisable()
	{
		EVENTS.remove(UpdateListener.class, this);
		EVENTS.remove(RenderListener.class, this);
		resetSlot();
		lastPlaced.clear();
		if(rotationQueue != null)
		{
			rotationQueue.stop();
			rotationQueue = null;
		}
	}

	@Override
	public void onUpdate()
	{
		LocalPlayer player = MC.player;
		if(player == null)
		{
			oldSlot = -1;
			return;
		}
		if(MC.level == null || MC.gameMode == null || player.isSpectator()
			|| player.isFallFlying())
		{
			resetSlot();
			return;
		}

		if(placementCooldown > 0)
		{
			placementCooldown--;
			return;
		}

		if(player.onGround())
		{
			groundTicks++;
			airTicks = 0;
		}else
		{
			airTicks++;
			groundTicks = 0;
		}

		if(!updateHeldBlockSlot())
			return;

		boolean towerActive = tower.isChecked() && MC.options.keyJump.isDown();
		if(towerActive)
			updateTowerMotion(player);

		updateSameY(player, towerActive);
		if(!towerActive && isTellyMode() && sameY.isChecked()
			&& autoJump.isChecked())
			tryAutoJump(player);

		BlockData data = towerActive ? findTowerData() : findBlockData();
		if(data == null)
		{
			lastTarget = null;
			resetSlot();
			return;
		}
		lastTarget = data.target();

		Rotation rotation = adjustRotation(data.rotation());
		boolean snap = snapRotations.isChecked()
			|| mode.getSelected() == Mode.SILENCE_TELLY;
		if(snap)
			rotationQueue.setRotation(rotation);
		else
			rotationQueue.add(rotation,
				isTellyMode() ? tellyTick.getValueI() : 1);

		boolean wasSprinting = player.isSprinting();
		InteractionHand hand = isBlockItem(player.getMainHandItem())
			? InteractionHand.MAIN_HAND : InteractionHand.OFF_HAND;
		if(interactBeforePlace.isChecked())
			player.connection.send(
				new ServerboundUseItemOnPacket(hand, data.hit(), 0));
		InteractionResult result = MC.gameMode.useItemOn(player, hand,
			data.hit());
		if(!result.consumesAction())
		{
			resetSlot();
			return;
		}

		if(mode.getSelected() != Mode.SILENCE_TELLY
			|| !stNoSwing.isChecked())
			swingHand.swing(hand);
		((net.wurstclient.mixin.MinecraftAccessor)(Object)MC)
			.setRightClickDelay(4);
		onTowerJump(data, hand);

		lastPlaced.addLast(data.target());
		while(lastPlaced.size() > 4)
			lastPlaced.removeFirst();
		if(keepSprint.isChecked() && wasSprinting)
			player.setSprinting(true);

		placementCooldown = placeDelay.getValueI();
		resetSlot();
	}

	private void updateSameY(LocalPlayer player, boolean towerActive)
	{
		if(!sameY.isChecked() || towerActive || player.onGround()
			|| Math.abs(Mth.floor(player.getY()) - sameYPos) > 3)
			sameYPos = Mth.floor(player.getY());
	}

	private boolean isTellyMode()
	{
		Mode selected = mode.getSelected();
		return selected == Mode.TELLY || selected == Mode.HEYPIXEL
			|| selected == Mode.HYPIXEL || selected == Mode.SILENCE_TELLY;
	}

	/**
	 * OpenOpal's telly rotation rules: look almost straight down on the
	 * ground and limit the yaw change in the air.
	 */
	private Rotation adjustRotation(Rotation rotation)
	{
		if(!isTellyMode())
			return rotation;

		float yaw = rotation.yaw();
		float pitch = rotation.pitch();
		if(MC.player.onGround())
			pitch = 75.5F;
		else
		{
			float limit = airTicks == 1 ? 90F : 50F;
			float delta = Mth.wrapDegrees(yaw - MC.player.getYRot());
			yaw = MC.player.getYRot() + Mth.clamp(delta, -limit, limit);
		}
		pitch = Mth.clamp(pitch, -89.5F, 89.5F);

		// OpenOpal's patchConstantRotation: quantise to the sensitivity step
		// so the rotation matches what a real mouse movement can produce.
		double sensitivity = MC.options.sensitivity().get() * 0.6D + 0.2D;
		double multiplier = sensitivity * sensitivity * sensitivity * 8.0D;
		double divisor = multiplier * 0.15D;
		if(divisor > 1.0E-5D)
		{
			float refYaw = MC.player.getYRot();
			float refPitch = MC.player.getXRot();
			yaw = refYaw + (float)(Math
				.round((yaw - refYaw) / divisor) * divisor);
			pitch = refPitch + (float)(Math
				.round((pitch - refPitch) / divisor) * divisor);
		}
		pitch = Mth.clamp(pitch, -89.5F, 89.5F);
		return Rotation.wrapped(yaw, pitch);
	}

	private void onTowerJump(BlockData data, InteractionHand hand)
	{
		if(!tower.isChecked() || !MC.options.keyJump.isDown()
			|| MC.options.keyUse.isDown()
			|| MC.player.hasEffect(MobEffects.JUMP))
			return;

		if(mode.getSelected() == Mode.BLOXD)
		{
			BlockPos above = data.hit().getBlockPos().above();
			MC.player.connection.send(new net.minecraft.network.protocol.game.ServerboundUseItemOnPacket(
				hand, new BlockHitResult(
					data.hit().getLocation().add(0, 1, 0),
					data.hit().getDirection(), above, false),
				0));
			return;
		}

		Vec3 velocity = MC.player.getDeltaMovement();
		MC.player.setDeltaMovement(velocity.x, 0.42D, velocity.z);
	}

	private void tryAutoJump(LocalPlayer player)
	{
		if(!player.onGround() || !MC.options.keyUp.isDown())
			return;
		if(player.input.forwardImpulse == 0 && player.input.leftImpulse == 0)
			return;

		Vec3 velocity = player.getDeltaMovement();
		if(Math.abs(velocity.x) + Math.abs(velocity.z) < 0.02D)
			return;
		player.setDeltaMovement(velocity.x, 0.42D, velocity.z);
	}

	private void updateTowerMotion(LocalPlayer player)
	{
		Vec3 velocity = player.getDeltaMovement();
		if(player.onGround())
			player.setDeltaMovement(velocity.x, 0.42D, velocity.z);
		else if(velocity.y < 0 && BlockUtils
			.getState(BlockPos.containing(player.position()).below())
			.canBeReplaced())
			player.setDeltaMovement(velocity.x, -0.15D, velocity.z);
	}

	private BlockData findBlockData()
	{
		BlockPos base = new BlockPos(Mth.floor(MC.player.getX()), sameYPos,
			Mth.floor(MC.player.getZ())).below();
		for(BlockPos candidate : targetCandidates(base))
		{
			if(!isReplaceable(candidate))
				continue;
			BlockData data = searchPlacement(candidate);
			if(data != null)
				return data;
		}
		return null;
	}

	private BlockData findTowerData()
	{
		BlockPos base = BlockPos.containing(MC.player.position()).below();
		if(!isReplaceable(base))
			return null;
		return searchPlacement(base);
	}

	private List<BlockPos> targetCandidates(BlockPos base)
	{
		LinkedHashSet<BlockPos> candidates = new LinkedHashSet<>();
		candidates.add(base.immutable());
		addLastPlacedCandidate(candidates, base);

		Vec3 velocity = MC.player.getDeltaMovement();
		for(double step : LOOKAHEAD_STEPS)
			addFootCandidates(candidates, MC.player.position()
				.add(velocity.x * step, 0, velocity.z * step), base.getY());

		double speed = Math.hypot(velocity.x, velocity.z);
		if(speed > 0.01D)
		{
			double dirX = velocity.x / speed;
			double dirZ = velocity.z / speed;
			addFootCandidates(candidates, MC.player.position()
				.add(dirX * 0.35D, 0, dirZ * 0.35D), base.getY());
			addFootCandidates(candidates, MC.player.position()
				.add(dirX * 0.65D, 0, dirZ * 0.65D), base.getY());
			addFootCandidates(candidates, MC.player.position()
				.add(dirX * 1.05D, 0, dirZ * 1.05D), base.getY());
			addFootCandidates(candidates, MC.player.position()
				.add(dirX * 1.45D, 0, dirZ * 1.45D), base.getY());

			double sideX = -dirZ;
			double sideZ = dirX;
			addFootCandidates(candidates, MC.player.position().add(
				dirX * 0.85D + sideX * 0.32D, 0,
				dirZ * 0.85D + sideZ * 0.32D), base.getY());
			addFootCandidates(candidates, MC.player.position().add(
				dirX * 0.85D - sideX * 0.32D, 0,
				dirZ * 0.85D - sideZ * 0.32D), base.getY());
		}
		return new ArrayList<>(candidates);
	}

	private void addLastPlacedCandidate(Set<BlockPos> candidates,
		BlockPos base)
	{
		if(lastPlaced.size() < 2)
			return;

		var iterator = lastPlaced.descendingIterator();
		BlockPos last = iterator.next();
		BlockPos previous = iterator.next();
		int dx = Integer.compare(last.getX() - previous.getX(), 0);
		int dz = Integer.compare(last.getZ() - previous.getZ(), 0);
		if(dx != 0 || dz != 0)
			candidates.add(new BlockPos(last.getX() + dx, base.getY(),
				last.getZ() + dz));
	}

	private void addFootCandidates(Set<BlockPos> candidates, Vec3 pos, int y)
	{
		for(double xOffset : FOOT_OFFSETS)
			for(double zOffset : FOOT_OFFSETS)
				candidates.add(BlockPos.containing(pos.x + xOffset, y,
					pos.z + zOffset));
	}

	private BlockData searchPlacement(BlockPos origin)
	{
		int maxDepth = mode.getSelected() == Mode.TELLY ? 3 : 4;
		int visitBudget = mode.getSelected() == Mode.TELLY ? 96 : 160;
		int raytraceBudget = mode.getSelected() == Mode.TELLY ? 24 : 48;

		PriorityQueue<PlacementCandidate> queue = new PriorityQueue<>(
			Comparator
				.comparingDouble((PlacementCandidate c) -> c.pos()
					.distSqr(origin))
				.thenComparingInt(PlacementCandidate::depth));
		Set<BlockPos> visited = new HashSet<>();
		queue.add(new PlacementCandidate(origin, 0));
		visited.add(origin);

		int visitedCount = 0;
		int raytraceCount = 0;
		while(!queue.isEmpty() && visitedCount++ < visitBudget)
		{
			PlacementCandidate candidate = queue.poll();
			if(candidate.depth() > maxDepth
				|| candidate.pos().distSqr(origin) > MAX_PLACEMENT_DISTANCE_SQ
				|| !isReplaceable(candidate.pos()))
				continue;

			BlockData data = placementAt(candidate.pos(), raytraceBudget
				- raytraceCount);
			raytraceCount += 16;
			if(data != null)
				return data;

			if(candidate.depth() >= maxDepth)
				continue;
			for(Direction direction : SUPPORT_DIRECTIONS)
			{
				BlockPos next = candidate.pos().relative(direction);
				if(visited.size() >= visitBudget
					|| next.distSqr(origin) > MAX_PLACEMENT_DISTANCE_SQ
					|| !visited.add(next) || !isReplaceable(next))
					continue;
				queue.add(new PlacementCandidate(next,
					candidate.depth() + 1));
			}
		}
		return null;
	}

	private BlockData placementAt(BlockPos target, int raytraceBudget)
	{
		if(raytraceBudget <= 0 || collidesWithPlayer(target))
			return null;

		BlockData best = null;
		double bestAngle = Double.MAX_VALUE;
		Rotation current = new Rotation(MC.player.getYRot(),
			MC.player.getXRot());
		Vec3 eye = MC.player.getEyePosition();

		for(Direction direction : SUPPORT_DIRECTIONS)
		{
			BlockPos support = target.relative(direction);
			if(!isValidSupport(support))
				continue;
			Direction side = direction.getOpposite();

			for(BlockHitResult hit : hitCandidates(target, support, side))
			{
				if(eye.distanceTo(hit.getLocation()) > range.getValue())
					continue;

				Rotation rotation = OpenAura.rotationTo(eye,
					hit.getLocation());
				if(overrideRaycast.isChecked() && !isValidRaycast(support,
					side, hit.getLocation()))
					continue;

				double angle = rotation.getAngleTo(current);
				if(angle < bestAngle)
				{
					bestAngle = angle;
					best = new BlockData(target, hit, rotation);
				}
				break;
			}
		}
		return best;
	}

	private boolean collidesWithPlayer(BlockPos target)
	{
		AABB blockBox = new AABB(target);
		AABB playerBox = MC.player.getBoundingBox();
		Vec3 velocity = MC.player.getDeltaMovement();
		AABB predicted = playerBox
			.expandTowards(velocity.x, velocity.y, velocity.z).inflate(0.001D);
		if(!blockBox.intersects(predicted))
			return false;

		// Placing the block directly under the feet is the landing case.
		return blockBox.maxY > playerBox.minY + 0.01D;
	}

	@Override
	public void onRender(PoseStack matrices, float partialTicks)
	{
		if(!blockOverlay.isChecked() || lastTarget == null || MC.level == null)
			return;
		OpenAura.drawBox(matrices, new AABB(lastTarget),
			WURST.getGui().getTheme().accent(0.75F));
	}

	private List<BlockHitResult> hitCandidates(BlockPos target,
		BlockPos support, Direction side)
	{
		Vec3 center = Vec3.atCenterOf(target);
		Vec3 normal = new Vec3(side.getStepX(), side.getStepY(),
			side.getStepZ());
		Vec3 face = center.add(normal.scale(0.5D));

		Vec3 tangentA;
		Vec3 tangentB;
		if(side.getAxis() == Direction.Axis.Y)
		{
			tangentA = new Vec3(1, 0, 0);
			tangentB = new Vec3(0, 0, 1);
		}else
		{
			tangentA = new Vec3(0, 1, 0);
			tangentB = new Vec3(-normal.z, 0, normal.x);
		}

		List<BlockHitResult> hits = new ArrayList<>();
		for(double a : HIT_OFFSETS)
			for(double b : HIT_OFFSETS)
				hits.add(new BlockHitResult(
					face.add(tangentA.scale(a)).add(tangentB.scale(b)),
					side, support, false));
		return hits;
	}

	private boolean isValidRaycast(BlockPos support, Direction side,
		Vec3 point)
	{
		BlockHitResult hit = MC.level.clip(
			new ClipContext(MC.player.getEyePosition(), point,
				ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE,
				MC.player));
		return hit.getType() != HitResult.Type.MISS
			&& hit.getBlockPos().equals(support)
			&& hit.getDirection() == side;
	}

	private boolean isReplaceable(BlockPos pos)
	{
		return MC.level.isInWorldBounds(pos)
			&& BlockUtils.getState(pos).canBeReplaced();
	}

	private boolean isValidSupport(BlockPos pos)
	{
		if(!MC.level.isInWorldBounds(pos))
			return false;

		BlockState state = BlockUtils.getState(pos);
		return !state.isAir() && !state.canBeReplaced()
			&& state.getFluidState().isEmpty()
			&& !state.getCollisionShape(MC.level, pos).isEmpty()
			&& state.getMenuProvider(MC.level, pos) == null;
	}

	private boolean updateHeldBlockSlot()
	{
		if(isBlockItem(MC.player.getMainHandItem()))
			return true;

		int slot = findBlockSlot();
		if(slot == -1)
			return isBlockItem(MC.player.getOffhandItem());

		if(slot != MC.player.getInventory().selected)
		{
			if(switchMode.getSelected() != SwitchMode.NORMAL)
				oldSlot = MC.player.getInventory().selected;
			MC.player.getInventory().selected = slot;
			MC.player.connection
				.send(new ServerboundSetCarriedItemPacket(slot));
		}
		return true;
	}

	private int findBlockSlot()
	{
		int bestSlot = -1;
		int bestCount = -1;
		for(int i = 0; i < 9; i++)
		{
			ItemStack stack = MC.player.getInventory().getItem(i);
			if(!isBlockItem(stack))
				continue;

			Block block = Block.byItem(stack.getItem());
			BlockState state = block.defaultBlockState();
			if(!state.isCollisionShapeFullBlock(EmptyBlockGetter.INSTANCE,
				BlockPos.ZERO))
				continue;
			if(block instanceof FallingBlock && FallingBlock
				.isFree(BlockUtils.getState(
					BlockPos.containing(MC.player.position()).below(2))))
				continue;

			if(stack.getCount() > bestCount)
			{
				bestSlot = i;
				bestCount = stack.getCount();
			}
		}
		return bestSlot;
	}

	private boolean isBlockItem(ItemStack stack)
	{
		return !stack.isEmpty() && stack.getItem() instanceof BlockItem;
	}

	private void resetSlot()
	{
		if(oldSlot == -1)
			return;
		if(MC.player == null)
		{
			oldSlot = -1;
			return;
		}

		MC.player.getInventory().selected = oldSlot;
		MC.player.connection.send(new ServerboundSetCarriedItemPacket(oldSlot));
		oldSlot = -1;
	}

	public boolean shouldSafeWalk()
	{
		return isEnabled() && safeWalk.isChecked() && !tower.isChecked()
			&& MC.player != null && MC.player.onGround()
			&& !MC.player.input.jumping;
	}

	private record BlockData(BlockPos target, BlockHitResult hit,
		Rotation rotation)
	{}

	private record PlacementCandidate(BlockPos pos, int depth)
	{}

	public enum Mode
	{
		VANILLA("Vanilla"),
		ANTI_GAMING_CHAIR("Anti Gaming Chair"),
		WATCHDOG("Watchdog"),
		BLOXD("Bloxd"),
		TELLY("Telly"),
		HEYPIXEL("Heypixel"),
		HYPIXEL("Hypixel"),
		SILENCE_TELLY("SilenceTelly");

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

	public enum SwitchMode
	{
		NORMAL("Normal"),
		HOTBAR("Hotbar"),
		FULL("Full");

		private final String name;

		private SwitchMode(String name)
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
