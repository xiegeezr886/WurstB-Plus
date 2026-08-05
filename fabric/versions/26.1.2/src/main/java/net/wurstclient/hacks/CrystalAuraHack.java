/*
 * Copyright (c) 2014-2025 Wurst-Imperium and contributors.
 * Copyright (c) 2025 Penguin
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 */
package net.wurstclient.hacks;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.boss.enderdragon.EndCrystal;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.wurstclient.Category;
import net.wurstclient.SearchTags;
import net.wurstclient.events.UpdateListener;
import net.wurstclient.hack.Hack;
import net.wurstclient.hack.HackConflictGroup;
import net.wurstclient.settings.CheckboxSetting;
import net.wurstclient.settings.EnumSetting;
import net.wurstclient.settings.FacingSetting;
import net.wurstclient.settings.FacingSetting.Facing;
import net.wurstclient.settings.SliderSetting;
import net.wurstclient.settings.SliderSetting.ValueDisplay;
import net.wurstclient.settings.SwingHandSetting;
import net.wurstclient.settings.SwingHandSetting.SwingHand;
import net.wurstclient.settings.filterlists.CrystalAuraFilterList;
import net.wurstclient.settings.filterlists.EntityFilterList;
import net.wurstclient.util.BlockUtils;
import net.wurstclient.util.CrystalAuraPlanner;
import net.wurstclient.util.DamageUtils;
import net.wurstclient.util.FakePlayerEntity;
import net.wurstclient.util.InventoryUtils;
import net.wurstclient.util.RotationQueue;
import net.wurstclient.util.RotationUtils;

@SearchTags({"crystal aura"})
public final class CrystalAuraHack extends Hack implements UpdateListener
{
	private final SliderSetting range = new SliderSetting("Range",
		"Determines how far CrystalAura will reach to place and detonate crystals.",
		6, 1, 10, 0.05, ValueDisplay.DECIMAL);

	private final CheckboxSetting autoPlace = new CheckboxSetting(
		"Auto-place crystals",
		"When enabled, CrystalAura will automatically place crystals near valid entities.\n"
			+ "When disabled, CrystalAura will only detonate manually placed crystals.",
		true);

	private final FacingSetting faceBlocks =
		FacingSetting.withPacketSpam("Face crystals",
			"Whether or not CrystalAura should face the correct direction when"
				+ " placing and left-clicking end crystals.\n\n"
				+ "Slower but can help with anti-cheat plugins.",
			Facing.OFF);

	private final CheckboxSetting checkLOS = new CheckboxSetting(
		"Check line of sight",
		"Ensures that you don't reach through blocks when placing or left-clicking end crystals.\n\n"
			+ "Slower but can help with anti-cheat plugins.",
		false);

	private final SwingHandSetting swingHand =
		new SwingHandSetting(this, SwingHand.CLIENT);

	private final EnumSetting<TakeItemsFrom> takeItemsFrom =
		new EnumSetting<>("Take items from", "Where to look for end crystals.",
			TakeItemsFrom.values(), TakeItemsFrom.INVENTORY);

	private final SliderSetting breakDelay = new SliderSetting("Break delay",
		"Ticks between each crystal detonation.\nHigher = slower but harder to detect.",
		0, 0, 5, 1, ValueDisplay.INTEGER);

	private final SliderSetting minDamage = new SliderSetting("Min damage",
		"Minimum damage a crystal must deal to be worth detonating.\nRange: 0=any, 36=max",
		4, 0, 36, 0.5, ValueDisplay.DECIMAL);

	private final SliderSetting maxSelfDamage = new SliderSetting("Max self damage",
		"Maximum damage you are willing to take from your own crystal.",
		8, 0, 36, 0.5, ValueDisplay.DECIMAL);

	private final SliderSetting minDamageAdvantage = new SliderSetting(
		"Min damage advantage",
		"Minimum target damage minus self damage required for an action.", 0,
		-20, 20, 0.5, ValueDisplay.DECIMAL);

	private final CheckboxSetting antiSuicide = new CheckboxSetting(
		"Anti suicide", "Rejects crystals that could kill the local player.",
		true);

	private final SliderSetting minCrystalAge = new SliderSetting(
		"Min crystal age", "Ticks a crystal must exist before detonation.", 0,
		0, 10, 1, ValueDisplay.INTEGER.withSuffix(" ticks"));

	private final SliderSetting placeSpeed = new SliderSetting("Place speed",
		"Ticks between each crystal placement.\n0 = instant.",
		0, 0, 10, 1, ValueDisplay.INTEGER);

	private final EntityFilterList entityFilters =
		CrystalAuraFilterList.create();

	private int breakTimer;
	private int placeTimer;
	private RotationQueue rotationQueue;

	public CrystalAuraHack()
	{
		super("CrystalAura");

		setCategory(Category.COMBAT);
		addConflictGroup(HackConflictGroup.COMBAT_TARGETING);
		addSetting(range);
		addSetting(autoPlace);
		addSetting(faceBlocks);
		addSetting(checkLOS);
		addSetting(swingHand);
		addSetting(takeItemsFrom);
		addSetting(breakDelay);
		addSetting(minDamage);
		addSetting(maxSelfDamage);
		addSetting(minDamageAdvantage);
		addSetting(antiSuicide);
		addSetting(minCrystalAge);
		addSetting(placeSpeed);

		entityFilters.forEach(this::addSetting);
	}

	@Override
	public String getRenderName()
	{
		return getName() + (autoPlace.isChecked() ? " [Auto]" : " [Manual]");
	}

	@Override
	protected void onEnable()
	{

		breakTimer = 0;
		placeTimer = 0;
		rotationQueue =
			new RotationQueue(RotationQueue.Priority.COMBAT);
		rotationQueue.start();
		EVENTS.add(UpdateListener.class, this);
	}

	@Override
	protected void onDisable()
	{
		EVENTS.remove(UpdateListener.class, this);
		rotationQueue.stop();
		rotationQueue = null;
	}

	@Override
	public void onUpdate()
	{
		if(breakTimer > 0)
			breakTimer--;
		if(placeTimer > 0)
			placeTimer--;

		ArrayList<Entity> targets = getNearbyTargets();
		if(targets.isEmpty())
			return;

		ArrayList<Entity> crystals = getNearbyCrystals();
		CrystalAuraPlanner.Candidate<Entity> crystal = findBestCrystal(crystals,
			targets, minCrystalAge.getValueI());
		if(crystal != null)
		{
			if(breakTimer == 0)
			{
				detonate(crystal.value());
				breakTimer = breakDelay.getValueI() + 1;
			}
			return;
		}
		if(minCrystalAge.getValueI() > 0
			&& findBestCrystal(crystals, targets, 0) != null)
			return;

		if(!autoPlace.isChecked())
			return;

		if(InventoryUtils.indexOf(Items.END_CRYSTAL,
			takeItemsFrom.getSelected().maxInvSlot) == -1)
			return;

		if(placeTimer > 0)
			return;

		CrystalAuraPlanner.Candidate<BlockPos> placement =
			findBestPlacement(targets);
		if(placement != null && placeCrystal(placement.value()))
		{
			swingHand.swing(InteractionHand.MAIN_HAND);
			placeTimer = placeSpeed.getValueI() + 1;
		}
	}

	private CrystalAuraPlanner.Candidate<Entity> findBestCrystal(
		ArrayList<Entity> crystals, ArrayList<Entity> targets, int minAge)
	{
		ArrayList<CrystalAuraPlanner.Candidate<Entity>> candidates =
			new ArrayList<>();
		for(Entity crystal : crystals)
		{
			Vec3 crystalPos = crystal.position();
			float targetDamage = getBestTargetDamage(crystalPos, targets);
			float selfDamage = DamageUtils.calculateDamage(crystalPos, MC.player);
			candidates.add(new CrystalAuraPlanner.Candidate<>(crystal,
				targetDamage, selfDamage, MC.player.distanceToSqr(crystal),
				crystal.tickCount));
		}

		return selectBest(candidates, minAge);
	}

	private void detonate(Entity crystal)
	{
		face(crystal.getBoundingBox().getCenter());
		MC.gameMode.attack(MC.player, crystal);
		swingHand.swing(InteractionHand.MAIN_HAND);
	}

	private CrystalAuraPlanner.Candidate<BlockPos> findBestPlacement(
		ArrayList<Entity> targets)
	{
		HashSet<BlockPos> positions = new HashSet<>();
		for(Entity target : targets)
			positions.addAll(getFreeBlocksNear(target));

		ArrayList<CrystalAuraPlanner.Candidate<BlockPos>> candidates =
			new ArrayList<>();
		Vec3 eyesPos = RotationUtils.getEyesPos();
		for(BlockPos pos : positions)
		{
			Vec3 crystalPos = new Vec3(pos.getX() + 0.5, pos.getY(),
				pos.getZ() + 0.5);
			float targetDamage = getBestTargetDamage(crystalPos, targets);
			float selfDamage = DamageUtils.calculateDamage(crystalPos, MC.player);
			candidates.add(new CrystalAuraPlanner.Candidate<>(pos.immutable(),
				targetDamage, selfDamage, eyesPos.distanceToSqr(crystalPos),
				Integer.MAX_VALUE));
		}

		return selectBest(candidates, 0);
	}

	private <T> CrystalAuraPlanner.Candidate<T> selectBest(
		ArrayList<CrystalAuraPlanner.Candidate<T>> candidates, int minAge)
	{
		return CrystalAuraPlanner.selectBest(candidates, minDamage.getValueF(),
			maxSelfDamage.getValueF(), minDamageAdvantage.getValueF(),
			MC.player.getHealth() + MC.player.getAbsorptionAmount(),
			antiSuicide.isChecked(), minAge);
	}

	private float getBestTargetDamage(Vec3 crystalPos,
		ArrayList<Entity> targets)
	{
		float bestDamage = 0;
		for(Entity target : targets)
			if(target instanceof LivingEntity living)
				bestDamage = Math.max(bestDamage,
					DamageUtils.calculateDamage(crystalPos, living));
		return bestDamage;
	}

	private boolean placeCrystal(BlockPos pos)
	{
		Vec3 eyesPos = RotationUtils.getEyesPos();
		double rangeSq = Math.pow(range.getValue(), 2);
		Vec3 posVec = Vec3.atCenterOf(pos);
		double distanceSqPosVec = eyesPos.distanceToSqr(posVec);

		for(Direction side : Direction.values())
		{
			BlockPos neighbor = pos.relative(side);

			if(!isClickableNeighbor(neighbor))
				continue;

			Vec3 dirVec = Vec3.atLowerCornerOf(side.getUnitVec3i());
			Vec3 hitVec = posVec.add(dirVec.scale(0.5));

			if(eyesPos.distanceToSqr(hitVec) > rangeSq)
				continue;

			if(distanceSqPosVec > eyesPos.distanceToSqr(posVec.add(dirVec)))
				continue;

			if(checkLOS.isChecked()
				&& !BlockUtils.hasLineOfSight(eyesPos, hitVec))
				continue;

			InventoryUtils.selectItem(Items.END_CRYSTAL,
				takeItemsFrom.getSelected().maxInvSlot);
			if(!MC.player.isHolding(Items.END_CRYSTAL))
				return false;

			face(hitVec);

			IMC.getInteractionManager().rightClickBlock(neighbor,
				side.getOpposite(), hitVec);

			return true;
		}

		return false;
	}

	private ArrayList<Entity> getNearbyCrystals()
	{
		LocalPlayer player = MC.player;
		double rangeSq = Math.pow(range.getValue(), 2);

		return StreamSupport.stream(MC.level.entitiesForRendering().spliterator(), false)
			.filter(EndCrystal.class::isInstance)
			.filter(e -> !e.isRemoved())
			.filter(e -> player.distanceToSqr(e) <= rangeSq)
			.collect(Collectors.toCollection(ArrayList::new));
	}

	private ArrayList<Entity> getNearbyTargets()
	{
		double rangeSq = Math.pow(range.getValue(), 2);

		Comparator<Entity> closestToPlayer = Comparator
			.comparingDouble(e -> MC.player.distanceToSqr(e));

		Stream<Entity> stream =
			StreamSupport.stream(MC.level.entitiesForRendering().spliterator(), false)
				.filter(e -> !e.isRemoved())
				.filter(e -> e instanceof LivingEntity
					&& ((LivingEntity)e).getHealth() > 0)
				.filter(e -> e != MC.player)
				.filter(e -> !(e instanceof FakePlayerEntity))
				.filter(e -> !WURST.getFriends().contains(e.getScoreboardName()))
				.filter(e -> MC.player.distanceToSqr(e) <= rangeSq);

		stream = entityFilters.applyTo(stream);

		return stream.sorted(closestToPlayer)
			.collect(Collectors.toCollection(ArrayList::new));
	}

	private ArrayList<BlockPos> getFreeBlocksNear(Entity target)
	{
		Vec3 eyesVec = RotationUtils.getEyesPos().subtract(0.5, 0.5, 0.5);
		double rangeD = range.getValue();
		double rangeSq = Math.pow(rangeD + 0.5, 2);
		int rangeI = 2;

		BlockPos center = target.blockPosition();
		BlockPos min = center.offset(-rangeI, -rangeI, -rangeI);
		BlockPos max = center.offset(rangeI, rangeI, rangeI);
		Vec3 targetEyesVec =
			target.position().add(0, target.getEyeHeight(target.getPose()), 0);

		Comparator<BlockPos> closestToTarget =
			Comparator.<BlockPos>comparingDouble(
				pos -> targetEyesVec.distanceToSqr(Vec3.atCenterOf(pos)));

		return BlockUtils.getAllInBoxStream(min, max)
			.filter(pos -> eyesVec.distanceToSqr(Vec3.atLowerCornerOf(pos)) <= rangeSq)
			.filter(this::isReplaceable).filter(this::hasCrystalBase)
			.filter(this::isCrystalSpaceClear)
			.sorted(closestToTarget)
			.collect(Collectors.toCollection(ArrayList::new));
	}

	private boolean isCrystalSpaceClear(BlockPos pos)
	{
		AABB box = new AABB(pos.getX(), pos.getY(), pos.getZ(),
			pos.getX() + 1, pos.getY() + 2, pos.getZ() + 1);
		return MC.level.getEntities((Entity)null, box).isEmpty();
	}

	private boolean isReplaceable(BlockPos pos)
	{
		return BlockUtils.getState(pos).canBeReplaced();
	}

	private boolean hasCrystalBase(BlockPos pos)
	{
		Block block = BlockUtils.getBlock(pos.below());
		return block == Blocks.BEDROCK || block == Blocks.OBSIDIAN;
	}

	private boolean isClickableNeighbor(BlockPos pos)
	{
		return BlockUtils.canBeClicked(pos)
			&& !BlockUtils.getState(pos).canBeReplaced();
	}

	private void face(Vec3 vec)
	{
		Facing mode = faceBlocks.getSelected();
		if(mode == Facing.SERVER)
			rotationQueue.setRotation(
				RotationUtils.getNeededRotations(vec));
		else
			mode.face(vec);
	}

	private enum TakeItemsFrom
	{
		HOTBAR("Hotbar", 9),

		INVENTORY("Inventory", 36);

		private final String name;
		private final int maxInvSlot;

		private TakeItemsFrom(String name, int maxInvSlot)
		{
			this.name = name;
			this.maxInvSlot = maxInvSlot;
		}

		@Override
		public String toString()
		{
			return name;
		}
	}
}
