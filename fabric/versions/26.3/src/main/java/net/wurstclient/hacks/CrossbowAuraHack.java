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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.animal.equine.AbstractHorse;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;
import net.wurstclient.Category;
import net.wurstclient.SearchTags;
import net.wurstclient.events.MouseButtonPressListener;
import net.wurstclient.events.UpdateListener;
import net.wurstclient.hack.Hack;
import net.wurstclient.settings.CheckboxSetting;
import net.wurstclient.settings.EnumSetting;
import net.wurstclient.settings.SliderSetting;
import net.wurstclient.settings.SliderSetting.ValueDisplay;
import net.wurstclient.util.EnchantmentUtils;
import net.wurstclient.util.InventoryUtils;
import net.wurstclient.util.RotationUtils;

@SearchTags({"crossbow aura", "auto crossbow", "bow aura", "CrossbowAura",
	"projectile aura"})
public final class CrossbowAuraHack extends Hack
	implements UpdateListener, MouseButtonPressListener
{
	private final SliderSetting range = new SliderSetting("Range",
		"Maximum target distance.", 60, 5, 150, 1, ValueDisplay.INTEGER);

	private final EnumSetting<AimMode> aimMode = new EnumSetting<>("Aim mode",
		"Silent only sends rotation packets, Lock turns the camera.",
		AimMode.values(), AimMode.SILENT);

	private final CheckboxSetting autoSwitch = new CheckboxSetting(
		"Auto switch", "Switches to a ranged weapon when a target is found.",
		true);

	private final CheckboxSetting autoFire = new CheckboxSetting("Auto fire",
		"Automatically charges and fires at the target.", true);

	private final EnumSetting<PriorityMode> priority = new EnumSetting<>(
		"Priority", "How targets are selected.", PriorityMode.values(),
		PriorityMode.CLOSEST);

	private final CheckboxSetting middleClickLock = new CheckboxSetting(
		"Middle click lock", "Middle-click an entity to lock onto it.", true);

	private final CheckboxSetting resetOnMiss = new CheckboxSetting(
		"Reset on miss", "Middle-clicking nothing clears the lock.", true);

	private final CheckboxSetting targetPlayers = new CheckboxSetting(
		"Target players", "Allow players as targets.", true);

	private final CheckboxSetting targetHostile = new CheckboxSetting(
		"Target hostile mobs", "Allow hostile mobs as targets.", true);

	private final CheckboxSetting ignoreFriends = new CheckboxSetting(
		"Ignore friends", "Never targets friends.", true);

	private final CheckboxSetting ignorePets = new CheckboxSetting(
		"Ignore pets", "Never targets tamed animals.", true);

	private final CheckboxSetting ignoreNamed = new CheckboxSetting(
		"Ignore named mobs", "Never targets mobs with a custom name.", true);

	private final CheckboxSetting targetSurvival = new CheckboxSetting(
		"Target survival players", "Target players in survival mode.", true);

	private final CheckboxSetting targetAdventure = new CheckboxSetting(
		"Target adventure players", "Target players in adventure mode.", true);

	private final CheckboxSetting targetCreative = new CheckboxSetting(
		"Target creative players", "Target players in creative mode.", false);

	private final EnumSetting<CrossbowMode> crossbowMode = new EnumSetting<>(
		"Crossbow mode", "How crossbows are charged and released.",
		CrossbowMode.values(), CrossbowMode.NATIVE);

	private final SliderSetting delay = new SliderSetting("Shot delay",
		"Ticks between shots.", 0, 0, 10, 1,
		ValueDisplay.INTEGER.withSuffix(" ticks"));

	private final SliderSetting tolerance = new SliderSetting(
		"Charge tolerance", "Extra ticks to wait when charging a crossbow.", 6,
		0, 50, 1, ValueDisplay.INTEGER.withSuffix(" ticks"));

	private final CheckboxSetting predictMovement = new CheckboxSetting(
		"Predict movement", "Lead the target based on its velocity.", true);

	private final CheckboxSetting calculateAcceleration = new CheckboxSetting(
		"Predict acceleration", "Also account for target acceleration.", true);

	private final CheckboxSetting pingCompensation = new CheckboxSetting(
		"Ping compensation", "Include latency in the prediction.", true);

	private final SliderSetting predictionScale = new SliderSetting(
		"Prediction scale", "Multiplier applied to the predicted offset.", 1, 0,
		2, 0.05, ValueDisplay.DECIMAL);

	private Entity currentTarget;
	private Entity lockedTarget;
	private Vec3 lastPredictedPos;
	private int timer;
	private final Map<Integer, Vec3> previousVelocities = new HashMap<>();

	public CrossbowAuraHack()
	{
		super("CrossbowAura");
		setCategory(Category.COMBAT);
		addSetting(range);
		addSetting(aimMode);
		addSetting(autoSwitch);
		addSetting(autoFire);
		addSetting(priority);
		addSetting(middleClickLock);
		addSetting(resetOnMiss);
		addSetting(targetPlayers);
		addSetting(targetHostile);
		addSetting(ignoreFriends);
		addSetting(ignorePets);
		addSetting(ignoreNamed);
		addSetting(targetSurvival);
		addSetting(targetAdventure);
		addSetting(targetCreative);
		addSetting(crossbowMode);
		addSetting(delay);
		addSetting(tolerance);
		addSetting(predictMovement);
		addSetting(calculateAcceleration);
		addSetting(pingCompensation);
		addSetting(predictionScale);
	}

	@Override
	protected void onEnable()
	{
		timer = 0;
		currentTarget = null;
		lockedTarget = null;
		lastPredictedPos = null;
		previousVelocities.clear();
		EVENTS.add(UpdateListener.class, this);
		EVENTS.add(MouseButtonPressListener.class, this);
	}

	@Override
	protected void onDisable()
	{
		EVENTS.remove(UpdateListener.class, this);
		EVENTS.remove(MouseButtonPressListener.class, this);
		MC.options.keyUse.setDown(false);
		if(MC.player != null && MC.player.isUsingItem())
			MC.gameMode.releaseUsingItem(MC.player);
		lockedTarget = null;
		lastPredictedPos = null;
		previousVelocities.clear();
	}

	@Override
	public void onMouseButtonPress(MouseButtonPressEvent event)
	{
		if(!middleClickLock.isChecked() || MC.gui.screen() != null
			|| event.getButton() != InputConstants.MOUSE_BUTTON_MIDDLE
			|| event.getAction() != InputConstants.PRESS)
			return;

		Entity target = MC.hitResult instanceof EntityHitResult hit
			? hit.getEntity() : null;

		if(target != null && isValid(target))
		{
			lockedTarget = target;
			priority.setSelected(PriorityMode.LOCK_ONLY);
		}else if(resetOnMiss.isChecked())
			lockedTarget = null;
	}

	@Override
	public void onUpdate()
	{
		if(MC.player == null || MC.level == null)
			return;

		currentTarget = findTarget();
		lastPredictedPos = null;

		ItemStack mainStack = MC.player.getMainHandItem();
		ItemStack offStack = MC.player.getOffhandItem();
		boolean mainValid = isValidWeapon(mainStack.getItem());
		boolean offValid = isValidWeapon(offStack.getItem());

		if(currentTarget != null && autoSwitch.isChecked() && !mainValid
			&& !offValid)
		{
			int slot = InventoryUtils
				.indexOf(stack -> isValidWeapon(stack.getItem()), 9);
			if(slot != -1)
			{
				InventoryUtils.selectItem(slot);
				mainStack = MC.player.getMainHandItem();
				mainValid = isValidWeapon(mainStack.getItem());
			}
		}

		InteractionHand activeHand;
		ItemStack activeStack;
		if(mainValid)
		{
			activeHand = InteractionHand.MAIN_HAND;
			activeStack = mainStack;
		}else if(offValid)
		{
			activeHand = InteractionHand.OFF_HAND;
			activeStack = offStack;
		}else
		{
			currentTarget = null;
			previousVelocities.clear();
			return;
		}

		boolean shouldShoot =
			autoFire.isChecked() || MC.options.keyUse.isDown();

		if(currentTarget == null)
		{
			previousVelocities.clear();
			if(autoFire.isChecked() && MC.options.keyUse.isDown())
			{
				MC.options.keyUse.setDown(false);
				if(MC.player.isUsingItem())
					MC.gameMode.releaseUsingItem(MC.player);
			}
			return;
		}

		double distance = MC.player.distanceTo(currentTarget);
		float[] rotations =
			solveBallistic(currentTarget, activeStack.getItem());

		Vec3 velocity = new Vec3(
			currentTarget.getX() - currentTarget.xOld,
			currentTarget.getY() - currentTarget.yOld,
			currentTarget.getZ() - currentTarget.zOld);
		previousVelocities.put(currentTarget.getId(), velocity);

		if(rotations == null)
			return;

		if(aimMode.getSelected() == AimMode.LOCK)
		{
			MC.player.setYRot(rotations[0]);
			MC.player.setXRot(rotations[1]);
		}

		if(shouldShoot || aimMode.getSelected() == AimMode.LOCK)
		{
			MC.player.connection.send(new ServerboundMovePlayerPacket.Rot(
				rotations[0], rotations[1], MC.player.onGround(),
				MC.player.horizontalCollision));
			handleShooting(activeStack, activeHand, distance);
		}
	}

	private Entity findTarget()
	{
		if(priority.getSelected() == PriorityMode.LOCK_ONLY)
			return lockedTarget != null && isValid(lockedTarget) ? lockedTarget
				: null;

		List<Entity> candidates = new ArrayList<>();
		for(Entity entity : MC.level.entitiesForRendering())
			if(isValid(entity))
				candidates.add(entity);

		if(candidates.isEmpty())
			return null;

		candidates.sort(this::compareTargets);
		return candidates.get(0);
	}

	private boolean isValid(Entity entity)
	{
		if(entity == null || !(entity instanceof LivingEntity)
			|| entity == MC.player || !entity.isAlive())
			return false;

		if(MC.player.distanceTo(entity) > range.getValue())
			return false;

		if(entity instanceof Player player)
		{
			if(!targetPlayers.isChecked())
				return false;
			if(player.isCreative() && !targetCreative.isChecked())
				return false;
			if(ignoreFriends.isChecked()
				&& WURST.getFriends().contains(player.getScoreboardName()))
				return false;

			GameType gameMode = getGameMode(player);
			if(gameMode == GameType.SURVIVAL && !targetSurvival.isChecked())
				return false;
			if(gameMode == GameType.ADVENTURE && !targetAdventure.isChecked())
				return false;
			if(gameMode == GameType.SPECTATOR)
				return false;
		}else
		{
			if(!targetHostile.isChecked() || !(entity instanceof Enemy))
				return false;
			if(ignoreNamed.isChecked() && entity.hasCustomName())
				return false;
			if(ignorePets.isChecked() && isPet(entity))
				return false;
		}

		return MC.player.hasLineOfSight(entity);
	}

	private int compareTargets(Entity a, Entity b)
	{
		if(priority.getSelected() == PriorityMode.LOWEST_HEALTH)
			return Float.compare(((LivingEntity)a).getHealth(),
				((LivingEntity)b).getHealth());
		return Double.compare(MC.player.distanceTo(a),
			MC.player.distanceTo(b));
	}

	private void handleShooting(ItemStack stack, InteractionHand hand,
		double distance)
	{
		if(timer > 0)
		{
			timer--;
			if(crossbowMode.getSelected() == CrossbowMode.NATIVE
				|| autoFire.isChecked())
				MC.options.keyUse.setDown(false);
			return;
		}

		Item item = stack.getItem();
		if(item instanceof CrossbowItem)
			handleCrossbow(stack, hand);
		else if(item instanceof BowItem)
			handleBow(hand, distance);
		else if(item == Items.TRIDENT)
			handleTrident(hand);
		else
		{
			MC.gameMode.useItem(MC.player, hand);
			MC.player.swing(hand, net.minecraft.world.item.component.SwingAnimation.DEFAULT, false);
			timer = delay.getValueI();
		}
	}

	private void handleCrossbow(ItemStack stack, InteractionHand hand)
	{
		if(CrossbowItem.isCharged(stack))
		{
			MC.gameMode.useItem(MC.player, hand);
			MC.player.swing(hand, net.minecraft.world.item.component.SwingAnimation.DEFAULT, false);
			timer = delay.getValueI();
			return;
		}

		MC.options.keyUse.setDown(true);
		if(!MC.player.isUsingItem())
		{
			MC.gameMode.useItem(MC.player, hand);
			return;
		}

		if(crossbowMode.getSelected() == CrossbowMode.NATIVE)
			return;

		int pullTime = getPullTime(stack) + tolerance.getValueI();
		if(MC.player.getTicksUsingItem() >= pullTime)
			MC.gameMode.releaseUsingItem(MC.player);
	}

	private void handleBow(InteractionHand hand, double distance)
	{
		if(!MC.player.isUsingItem())
		{
			MC.options.keyUse.setDown(true);
			MC.gameMode.useItem(MC.player, hand);
			return;
		}

		int targetCharge = distance < 10 ? 12 : 20;
		if(MC.player.getTicksUsingItem() >= targetCharge)
		{
			MC.gameMode.releaseUsingItem(MC.player);
			MC.options.keyUse.setDown(false);
			timer = delay.getValueI();
		}
	}

	private void handleTrident(InteractionHand hand)
	{
		if(!MC.player.isUsingItem())
		{
			MC.options.keyUse.setDown(true);
			MC.gameMode.useItem(MC.player, hand);
			return;
		}

		if(MC.player.getTicksUsingItem() >= 12)
		{
			MC.gameMode.releaseUsingItem(MC.player);
			MC.options.keyUse.setDown(false);
			timer = delay.getValueI();
		}
	}

	private float[] solveBallistic(Entity target, Item weapon)
	{
		double velocity = 1.0;
		double gravity = 0.05;

		if(weapon instanceof CrossbowItem)
		{
			velocity = 3.15;
			gravity = 0.05;
		}else if(weapon instanceof BowItem)
		{
			velocity = 3.0;
			gravity = 0.05;
		}else if(weapon == Items.TRIDENT)
		{
			velocity = 2.5;
			gravity = 0.05;
		}else if(weapon == Items.SNOWBALL || weapon == Items.EGG)
		{
			velocity = 1.5;
			gravity = 0.03;
		}

		Vec3 playerPos = RotationUtils.getEyesPos();
		Vec3 targetPos = new Vec3(target.getX(), target.getY(), target.getZ())
			.add(0, target.getBbHeight() * 0.5, 0);

		Vec3 targetVelocity = new Vec3(target.getX() - target.xOld,
			target.getY() - target.yOld, target.getZ() - target.zOld);

		Vec3 targetAcceleration = Vec3.ZERO;
		Vec3 previousVelocity = previousVelocities.get(target.getId());
		if(calculateAcceleration.isChecked() && previousVelocity != null)
		{
			targetAcceleration = targetVelocity.subtract(previousVelocity);
			if(targetAcceleration.lengthSqr() > 1.0)
				targetAcceleration = Vec3.ZERO;
		}

		if(predictMovement.isChecked())
		{
			double time = playerPos.distanceTo(targetPos) / velocity;
			double pingTicks = 0;

			if(pingCompensation.isChecked() && MC.getConnection() != null)
			{
				PlayerInfo info = MC.getConnection()
					.getPlayerInfo(MC.player.getUUID());
				if(info != null)
					pingTicks = info.getLatency() / 50.0;
			}

			double scale = predictionScale.getValue();

			for(int i = 0; i < 5; i++)
			{
				double totalTime = time + pingTicks;
				Vec3 moveTerm = targetVelocity.scale(totalTime);
				Vec3 accelTerm = calculateAcceleration.isChecked()
					? targetAcceleration.scale(0.5 * totalTime * totalTime)
					: Vec3.ZERO;
				Vec3 futurePos = targetPos
					.add(moveTerm.add(accelTerm).scale(scale));
				time = playerPos.distanceTo(futurePos) / velocity;
			}

			double finalTime = time + pingTicks;
			Vec3 moveTerm = targetVelocity.scale(finalTime);
			Vec3 accelTerm = calculateAcceleration.isChecked()
				? targetAcceleration.scale(0.5 * finalTime * finalTime)
				: Vec3.ZERO;
			targetPos =
				targetPos.add(moveTerm.add(accelTerm).scale(scale));
		}

		lastPredictedPos = targetPos;

		double diffX = targetPos.x - playerPos.x;
		double diffY = targetPos.y - playerPos.y;
		double diffZ = targetPos.z - playerPos.z;
		double horizontalDistance =
			Math.sqrt(diffX * diffX + diffZ * diffZ);

		double velocitySq = velocity * velocity;
		double velocityPow4 = velocitySq * velocitySq;
		double horizontalSq = horizontalDistance * horizontalDistance;
		double root = velocityPow4
			- gravity * (gravity * horizontalSq + 2 * diffY * velocitySq);

		if(root < 0)
			return null;

		double angle = Math.atan2(velocitySq - Math.sqrt(root),
			gravity * horizontalDistance);
		float yaw = (float)Math.toDegrees(Math.atan2(diffZ, diffX)) - 90;
		float pitch = (float)-Math.toDegrees(angle);

		return new float[]{yaw, pitch};
	}

	private int getPullTime(ItemStack stack)
	{
		int level = EnchantmentUtils.getLevel(Enchantments.QUICK_CHARGE,
			stack);
		return Math.max(0, 25 - 5 * level);
	}

	private boolean isValidWeapon(Item item)
	{
		return item == Items.SNOWBALL || item == Items.EGG
			|| item == Items.TRIDENT || item instanceof BowItem
			|| item instanceof CrossbowItem;
	}

	private boolean isPet(Entity entity)
	{
		if(entity instanceof TamableAnimal tameable && tameable.isTame())
			return true;
		return entity instanceof AbstractHorse horse && horse.isTamed();
	}

	private GameType getGameMode(Player player)
	{
		if(MC.getConnection() == null)
			return GameType.SURVIVAL;

		PlayerInfo info = MC.getConnection().getPlayerInfo(player.getUUID());
		if(info == null)
			return GameType.SURVIVAL;

		GameType gameMode = info.getGameMode();
		return gameMode == null ? GameType.SURVIVAL : gameMode;
	}

	private enum AimMode
	{
		SILENT("Silent"),
		LOCK("Lock");

		private final String name;

		AimMode(String name)
		{
			this.name = name;
		}

		@Override
		public String toString()
		{
			return name;
		}
	}

	private enum PriorityMode
	{
		CLOSEST("Closest"),
		LOWEST_HEALTH("Lowest health"),
		LOCK_ONLY("Lock only");

		private final String name;

		PriorityMode(String name)
		{
			this.name = name;
		}

		@Override
		public String toString()
		{
			return name;
		}
	}

	private enum CrossbowMode
	{
		NATIVE("Native"),
		CONTROL("Control"),
		PACKET("Packet");

		private final String name;

		CrossbowMode(String name)
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
