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
import java.util.ArrayList;
import java.util.List;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket;
import net.minecraft.network.protocol.game.ServerboundPlayerCommandPacket;
import net.minecraft.network.protocol.game.ServerboundUseItemPacket;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.wurstclient.Category;
import net.wurstclient.SearchTags;
import net.wurstclient.events.PacketOutputListener;
import net.wurstclient.events.RenderListener;
import net.wurstclient.events.UpdateListener;
import net.wurstclient.hack.Hack;
import net.wurstclient.settings.CheckboxSetting;
import net.wurstclient.settings.ColorSetting;
import net.wurstclient.settings.EnumSetting;
import net.wurstclient.settings.ItemListSetting;
import net.wurstclient.settings.SliderSetting;
import net.wurstclient.settings.SliderSetting.ValueDisplay;
import net.wurstclient.util.RenderUtils;
import net.wurstclient.util.Rotation;
import net.wurstclient.util.RotationUtils;

/**
 * Teleports the player backwards (or forwards) right before a projectile is
 * released, so the server calculates the projectile's damage from the spoofed
 * position while the player snaps back to their real position.
 *
 * <p>
 * Ported from InvincibleMachineGun's Pitcher. The aimbot, auto-shoot and
 * totem-bypass state machine are kept. Meteor-specific packet tags, the
 * configurable entity-type list and the meteor renderer are replaced with
 * Wurst equivalents; the entity list is reduced to player/mob switches.
 */
@SearchTags({"pitcher", "projectile launcher", "bow boost", "arrow boost",
	"teleport projectile"})
public final class PitcherHack extends Hack
	implements UpdateListener, PacketOutputListener, RenderListener
{
	private enum Mode
	{
		VANILLA,
		PAPER
	}

	private enum TpDirection
	{
		REVERSE,
		FORWARD
	}

	private enum TargetPriority
	{
		ANGLE,
		DISTANCE,
		HEALTH
	}

	private final EnumSetting<Mode> mode = new EnumSetting<>(
		"Compatibility mode",
		"Vanilla caps the spoof at 21.9 blocks, Paper allows up to 149.",
		Mode.values(), Mode.PAPER);

	private final EnumSetting<TpDirection> tpDirection = new EnumSetting<>(
		"TP direction",
		"Backwards deals more damage, forwards throws the projectile further.",
		TpDirection.values(), TpDirection.REVERSE);

	private final ItemListSetting projectileItems = new ItemListSetting(
		"Projectile items", "Items that trigger the position spoof.",
		"minecraft:bow", "minecraft:trident", "minecraft:ender_pearl",
		"minecraft:splash_potion", "minecraft:experience_bottle",
		"minecraft:snowball", "minecraft:egg");

	private final SliderSetting paperDistance = new SliderSetting(
		"Paper max distance", "Maximum spoof distance in Paper mode.", 149, 10,
		169, 1, ValueDisplay.DECIMAL);

	private final SliderSetting paperPackets = new SliderSetting(
		"Paper packets", "Position packets sent before the shot in Paper mode.",
		15, 1, 20, 1, ValueDisplay.INTEGER);

	private final SliderSetting strength = new SliderSetting("Strength",
		"Spoof distance multiplier. 10 is the theoretical vanilla maximum.",
		10, 0.1, 20, 0.1, ValueDisplay.DECIMAL);

	private final CheckboxSetting vertical = new CheckboxSetting(
		"Vertical correction", "Include the Y component in the spoof offset.",
		true);

	private final CheckboxSetting useOffset = new CheckboxSetting(
		"Fall protection", "Adds a small upward packet to reduce fall damage.",
		true);

	private final CheckboxSetting smartStrength = new CheckboxSetting(
		"Smart strength",
		"Raycasts behind the player and shortens the spoof to avoid blocks.",
		true);

	private final CheckboxSetting autoShoot = new CheckboxSetting(
		"Auto shoot", "Automatically charges and releases the projectile.",
		false);

	private final SliderSetting charge = new SliderSetting("Charge time",
		"Ticks to charge before releasing automatically.", 4, 1, 20, 1,
		ValueDisplay.INTEGER.withSuffix(" ticks"));

	private final CheckboxSetting onlyWhenHoldingRightClick = new CheckboxSetting(
		"Only while holding use",
		"Auto shoot only while the use key is held down.", true);

	private final CheckboxSetting totemBypass = new CheckboxSetting(
		"Totem bypass", "Fires a second, stronger shot after the first one.",
		false);

	private final SliderSetting bypassStrength = new SliderSetting(
		"Second shot strength", "Strength multiplier of the second shot.", 20,
		0.1, 30, 0.1, ValueDisplay.DECIMAL);

	private final SliderSetting bypassDelay = new SliderSetting(
		"Second shot delay", "Ticks to charge before the second shot.", 4, 1,
		10, 1, ValueDisplay.INTEGER.withSuffix(" ticks"));

	private final CheckboxSetting aimbot = new CheckboxSetting("Aimbot",
		"Locks the client view onto a nearby target.", false);

	private final EnumSetting<TargetPriority> priority = new EnumSetting<>(
		"Target priority", "How the best aim target is chosen.",
		TargetPriority.values(), TargetPriority.ANGLE);

	private final SliderSetting aimRange = new SliderSetting("Aim range",
		"Maximum distance for aimbot targets.", 40, 1, 100, 1,
		ValueDisplay.DECIMAL);

	private final CheckboxSetting aimOnlyWhenHoldingRightClick =
		new CheckboxSetting("Aim only while using",
			"Only aim while the use key is held or auto shoot is active.",
			true);

	private final CheckboxSetting ignoreWalls = new CheckboxSetting(
		"Ignore behind walls", "Require line of sight to the target.", true);

	private final CheckboxSetting targetPlayers = new CheckboxSetting(
		"Target players", "Allow players as aimbot targets.", true);

	private final CheckboxSetting targetMobs = new CheckboxSetting(
		"Target mobs", "Allow non-player living entities as targets.", false);

	private final CheckboxSetting ignoreFriends = new CheckboxSetting(
		"Ignore friends", "Never targets friends.", true);

	private final CheckboxSetting renderPath = new CheckboxSetting(
		"Render path", "Draws a predicted projectile path while holding an item.",
		true);

	private final ColorSetting pathColor = new ColorSetting("Path color",
		new Color(0, 255, 0, 200));

	private final ColorSetting targetColor = new ColorSetting("Target color",
		new Color(255, 0, 0, 200));

	private boolean isShooting;
	private boolean forcingUse;
	private int totemStep;
	private int bypassTimer = -1;
	private Entity currentTarget;

	public PitcherHack()
	{
		super("Pitcher");
		setCategory(Category.COMBAT);
		addSetting(mode);
		addSetting(tpDirection);
		addSetting(projectileItems);
		addSetting(paperDistance);
		addSetting(paperPackets);
		addSetting(strength);
		addSetting(vertical);
		addSetting(useOffset);
		addSetting(smartStrength);
		addSetting(autoShoot);
		addSetting(charge);
		addSetting(onlyWhenHoldingRightClick);
		addSetting(totemBypass);
		addSetting(bypassStrength);
		addSetting(bypassDelay);
		addSetting(aimbot);
		addSetting(priority);
		addSetting(aimRange);
		addSetting(aimOnlyWhenHoldingRightClick);
		addSetting(ignoreWalls);
		addSetting(targetPlayers);
		addSetting(targetMobs);
		addSetting(ignoreFriends);
		addSetting(renderPath);
		addSetting(pathColor);
		addSetting(targetColor);

		paperDistance.visibleWhen(() -> mode.getSelected() == Mode.PAPER);
		paperPackets.visibleWhen(() -> mode.getSelected() == Mode.PAPER);
		charge.visibleWhen(autoShoot::isChecked);
		onlyWhenHoldingRightClick.visibleWhen(autoShoot::isChecked);
		bypassStrength.visibleWhen(totemBypass::isChecked);
		bypassDelay.visibleWhen(totemBypass::isChecked);
		priority.visibleWhen(aimbot::isChecked);
		aimRange.visibleWhen(aimbot::isChecked);
		aimOnlyWhenHoldingRightClick.visibleWhen(aimbot::isChecked);
		ignoreWalls.visibleWhen(aimbot::isChecked);
		targetPlayers.visibleWhen(aimbot::isChecked);
		targetMobs.visibleWhen(aimbot::isChecked);
		ignoreFriends.visibleWhen(aimbot::isChecked);
		pathColor.visibleWhen(renderPath::isChecked);
		targetColor.visibleWhen(renderPath::isChecked);
	}

	@Override
	protected void onEnable()
	{
		isShooting = false;
		forcingUse = false;
		totemStep = 0;
		bypassTimer = -1;
		currentTarget = null;
		EVENTS.add(UpdateListener.class, this);
		EVENTS.add(PacketOutputListener.class, this);
		EVENTS.add(RenderListener.class, this);
	}

	@Override
	protected void onDisable()
	{
		EVENTS.remove(UpdateListener.class, this);
		EVENTS.remove(PacketOutputListener.class, this);
		EVENTS.remove(RenderListener.class, this);
		isShooting = false;
		forcingUse = false;
		totemStep = 0;
		bypassTimer = -1;
		currentTarget = null;
	}

	@Override
	public void onUpdate()
	{
		if(MC.player == null || MC.level == null || MC.gameMode == null)
			return;

		InteractionHand hand = getValidHand();
		boolean hasValidItem = hand != null;

		if(totemBypass.isChecked() && totemStep == 1)
		{
			updateTotemSecondShot(hand);
			return;
		}

		if(aimbot.isChecked() && hasValidItem)
			updateAimbot();

		if(autoShoot.isChecked() && hasValidItem)
			updateAutoShoot(hand);
	}

	private InteractionHand getValidHand()
	{
		if(isValidItem(MC.player.getMainHandItem()))
			return InteractionHand.MAIN_HAND;
		if(isValidItem(MC.player.getOffhandItem()))
			return InteractionHand.OFF_HAND;
		return null;
	}

	private boolean isValidItem(ItemStack stack)
	{
		if(stack == null || stack.isEmpty())
			return false;

		String id = BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
		return projectileItems.getItemNames().contains(id);
	}

	private void updateTotemSecondShot(InteractionHand hand)
	{
		if(hand == null)
			return;

		if(bypassTimer == bypassDelay.getValueI())
		{
			// Do not treat the forced charge-start packet as the second shot.
			forcingUse = true;
			try
			{
				MC.gameMode.useItem(MC.player, hand);
			}finally
			{
				forcingUse = false;
			}
		}

		if(bypassTimer > 0)
		{
			bypassTimer--;
			return;
		}

		// Releasing sends the packet that processShoot() turns into shot #2.
		MC.gameMode.releaseUsingItem(MC.player);
		totemStep = 0;
		bypassTimer = -1;
	}

	private void updateAutoShoot(InteractionHand hand)
	{
		if(hand == null || MC.player.isUsingItem()
			&& !isValidItem(MC.player.getUseItem()))
			return;

		if(MC.player.isUsingItem() && isValidItem(MC.player.getUseItem()))
		{
			if(MC.player.getTicksUsingItem() >= charge.getValueI())
				MC.gameMode.releaseUsingItem(MC.player);
			return;
		}

		if(!onlyWhenHoldingRightClick.isChecked())
			MC.gameMode.useItem(MC.player, hand);
	}

	private void updateAimbot()
	{
		boolean pressingUse = MC.options.keyUse.isDown()
			|| autoShoot.isChecked() && !onlyWhenHoldingRightClick.isChecked();
		if(aimOnlyWhenHoldingRightClick.isChecked() && !pressingUse)
			return;

		currentTarget = findAimTarget();
		if(currentTarget == null)
			return;

		Rotation rotation = RotationUtils
			.getNeededRotations(currentTarget.getBoundingBox().getCenter());
		MC.player.setYRot(rotation.yaw());
		MC.player.setXRot(rotation.pitch());
		rotation.sendPlayerLookPacket();
	}

	private Entity findAimTarget()
	{
		double range = aimRange.getValue();
		Entity best = null;
		double bestScore = Double.MAX_VALUE;

		for(Entity entity : MC.level.getEntities(MC.player,
			MC.player.getBoundingBox().inflate(range), this::isValidAimTarget))
		{
			double distance = MC.player.distanceTo(entity);
			if(distance > range)
				continue;

			if(ignoreWalls.isChecked()
				&& !MC.player.hasLineOfSight(entity))
				continue;

			double score;
			switch(priority.getSelected())
			{
				case DISTANCE:
				score = distance;
				break;

				case HEALTH:
				score = ((LivingEntity)entity).getHealth();
				break;

				default:
				score = RotationUtils
					.getAngleToLookVec(entity.getBoundingBox().getCenter());
				break;
			}

			if(score < bestScore)
			{
				bestScore = score;
				best = entity;
			}
		}

		return best;
	}

	private boolean isValidAimTarget(Entity entity)
	{
		if(entity == MC.player || !entity.isAlive()
			|| !(entity instanceof LivingEntity living))
			return false;

		if(living.getHealth() <= 0)
			return false;

		if(entity instanceof Player player)
		{
			if(!targetPlayers.isChecked() || player.isCreative()
				|| player.isSpectator())
				return false;
			if(ignoreFriends.isChecked()
				&& WURST.getFriends().contains(player.getScoreboardName()))
				return false;
			return true;
		}

		return targetMobs.isChecked();
	}

	@Override
	public void onSentPacket(PacketOutputEvent event)
	{
		if(isShooting || MC.player == null)
			return;

		Packet<?> packet = event.getPacket();

		if(packet instanceof ServerboundPlayerActionPacket action)
		{
			if(action.getAction() != ServerboundPlayerActionPacket.Action.RELEASE_USE_ITEM)
				return;
			if(!isValidItem(MC.player.getUseItem()))
				return;

			event.cancel();
			processShoot(packet, true);
			return;
		}

		if(packet instanceof ServerboundUseItemPacket use)
		{
			if(forcingUse
				|| !isValidItem(MC.player.getItemInHand(use.getHand())))
				return;

			event.cancel();
			processShoot(packet, false);
		}
	}

	private void processShoot(Packet<?> original, boolean released)
	{
		if(MC.player == null || MC.getConnection() == null)
			return;

		isShooting = true;
		try
		{
			double currentStrength =
				totemStep == 1 ? bypassStrength.getValue() : strength.getValue();

			MC.getConnection().send(new ServerboundPlayerCommandPacket(MC.player,
				ServerboundPlayerCommandPacket.Action.START_SPRINTING));

			Vec3 start = MC.player.position();
			Vec3 look = MC.player.getLookAngle();
			double maxDistance =
				mode.getSelected() == Mode.VANILLA ? 21.9
					: paperDistance.getValue();
			double adjustedStrength = Math.min(
				currentStrength / 10.0 * Math.sqrt(500.0), maxDistance);

			Vec3 direction = tpDirection.getSelected() == TpDirection.REVERSE
				? look.scale(-1) : look;
			Vec3 offset = new Vec3(direction.x * adjustedStrength,
				vertical.isChecked() ? direction.y * adjustedStrength : 0,
				direction.z * adjustedStrength);

			if(smartStrength.isChecked())
			{
				double safeDistance = getSafeSpoofDistance(start, offset);
				double adjustedDistance =
					Math.max(0.01, safeDistance - 0.5);
				if(adjustedDistance < offset.length())
					offset = offset.normalize().scale(adjustedDistance);
			}

			Vec3 target = start.add(offset);
			int spam = mode.getSelected() == Mode.VANILLA ? 4
				: paperPackets.getValueI();

			for(int i = 0; i < spam; i++)
				MC.getConnection()
					.send(new ServerboundMovePlayerPacket.StatusOnly(true));

			sendPos(target);
			if(tpDirection.getSelected() == TpDirection.FORWARD)
				MC.getConnection().send(original);

			sendPos(start);
			if(tpDirection.getSelected() == TpDirection.REVERSE)
				MC.getConnection().send(original);

			Vec3 sync = start.add((Math.random() - 0.5) * 0.05, 0.001,
				(Math.random() - 0.5) * 0.05);
			sendPos(sync);
			MC.player.setPos(sync.x, sync.y, sync.z);

			if(vertical.isChecked() && useOffset.isChecked() && offset.y > 0)
				sendPos(start.add(0, 0.01, 0));
		}finally
		{
			isShooting = false;
		}

		if(totemBypass.isChecked() && released)
		{
			if(totemStep == 0)
			{
				totemStep = 1;
				bypassTimer = bypassDelay.getValueI();
			}else
			{
				totemStep = 0;
				bypassTimer = -1;
			}
		}
	}

	private void sendPos(Vec3 pos)
	{
		MC.getConnection()
			.send(new ServerboundMovePlayerPacket.Pos(pos.x, pos.y, pos.z,
				false));
	}

	private double getSafeSpoofDistance(Vec3 start, Vec3 offset)
	{
		Vec3 end = start.add(offset);
		double maxDistance = offset.length();

		BlockHitResult footHit = MC.level
			.clip(new ClipContext(start, end, ClipContext.Block.COLLIDER,
				ClipContext.Fluid.NONE, MC.player));
		Vec3 headStart = start.add(0, 1.8, 0);
		Vec3 headEnd = end.add(0, 1.8, 0);
		BlockHitResult headHit = MC.level
			.clip(new ClipContext(headStart, headEnd, ClipContext.Block.COLLIDER,
				ClipContext.Fluid.NONE, MC.player));

		double safeDistance = maxDistance;
		if(footHit.getType() == HitResult.Type.BLOCK)
			safeDistance = Math.min(safeDistance,
				start.distanceTo(footHit.getLocation()));
		if(headHit.getType() == HitResult.Type.BLOCK)
			safeDistance = Math.min(safeDistance,
				headStart.distanceTo(headHit.getLocation()));

		return safeDistance;
	}

	@Override
	public void onRender(PoseStack matrixStack, float partialTicks)
	{
		if(!renderPath.isChecked() || MC.player == null || MC.level == null)
			return;
		if(getValidHand() == null)
			return;

		double maxDistance =
			mode.getSelected() == Mode.VANILLA ? 21.9 : paperDistance.getValue();
		double currentStrength =
			totemStep == 1 ? bypassStrength.getValue() : strength.getValue();
		double spoofDistance =
			Math.min(currentStrength / 10.0 * Math.sqrt(500.0), maxDistance);

		Vec3 look = MC.player.getLookAngle();
		Vec3 offset = new Vec3(-look.x * spoofDistance,
			vertical.isChecked() ? -look.y * spoofDistance : 0,
			-look.z * spoofDistance);
		double velocityAdd = offset.length();

		if(smartStrength.isChecked())
		{
			double safeDistance = getSafeSpoofDistance(
				MC.player.position(), offset);
			velocityAdd = Math.max(0.01, Math.min(velocityAdd, safeDistance - 0.5));
		}

		Vec3 position = MC.player.getEyePosition().add(0, -0.1, 0);
		Vec3 velocity = look.normalize().scale(3.0 + velocityAdd);
		List<Vec3> points = new ArrayList<>();
		points.add(position);

		for(int step = 0; step < 150; step++)
		{
			Vec3 next = position.add(velocity);
			BlockHitResult hit = MC.level
				.clip(new ClipContext(position, next, ClipContext.Block.COLLIDER,
					ClipContext.Fluid.NONE, MC.player));
			if(hit.getType() != HitResult.Type.MISS)
			{
				points.add(hit.getLocation());
				break;
			}

			points.add(next);
			position = next;
			velocity = velocity.scale(0.99).subtract(0, 0.05, 0);
		}

		if(points.size() >= 2)
			RenderUtils.drawCurvedLine(matrixStack, points,
				pathColor.getColorI(), false);

		if(currentTarget != null && currentTarget.isAlive())
			RenderUtils.drawOutlinedBox(matrixStack,
				currentTarget.getBoundingBox(), targetColor.getColorI(), false);
	}
}
