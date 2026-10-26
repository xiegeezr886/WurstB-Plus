/*
 * Copyright (c) 2026 WurstB+ Plus contributors
 *
 * This source code is subject to the terms of the GNU Affero General Public
 * License, version 3. If a copy of the AGPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/agpl-3.0.txt
 */
package net.wurstclient.hacks;

import java.util.List;

import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.network.protocol.game.ServerboundSetCarriedItemPacket;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.AxeItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.PotionItem;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;
import net.wurstclient.Category;
import net.wurstclient.events.HandleInputListener;
import net.wurstclient.events.RenderListener;
import net.wurstclient.events.UpdateListener;
import net.wurstclient.hack.Hack;
import net.wurstclient.hack.HackConflictGroup;
import net.wurstclient.hacks.aura.OpenAura;
import net.wurstclient.settings.CheckboxSetting;
import net.wurstclient.settings.EnumSetting;
import net.wurstclient.settings.SliderSetting;
import net.wurstclient.settings.SliderSetting.ValueDisplay;
import net.wurstclient.util.EntityUtils;
import net.wurstclient.util.RenderUtils;
import net.wurstclient.util.RotationQueue;

/**
 * KillAura ported from OpenOpal's KillAuraModule
 * (Single/Switch targeting, raycasted rotations and attack windows).
 */
public final class KillauraHack extends Hack
	implements UpdateListener, HandleInputListener, RenderListener
{
	private final EnumSetting<Mode> mode =
		new EnumSetting<>("Mode", Mode.values(), Mode.SWITCH);

	private final SliderSetting range = new SliderSetting("Range",
		"How far the aura can attack.", 3, 3, 6, 0.1, ValueDisplay.DECIMAL);
	private final SliderSetting rotationRange = new SliderSetting(
		"Rotation range",
		"How far the aura will look at targets without attacking.", 5, 3, 8,
		0.1, ValueDisplay.DECIMAL);
	private final SliderSetting swingRange = new SliderSetting("Swing range",
		"Range used for fake swings when nothing is being attacked.", 5, 3, 8,
		0.1, ValueDisplay.DECIMAL);

	private final EnumSetting<CpsMode> cpsMode = new EnumSetting<>("CPS mode",
		"Auto follows the weapon's attack speed; Manual uses a fixed CPS.",
		CpsMode.values(), CpsMode.AUTO);
	private final SliderSetting attackCps = new SliderSetting("Attack CPS",
		"Attacks per second in manual mode.", 10, 1, 20, 1,
		ValueDisplay.INTEGER)
			.visibleWhen(() -> cpsMode.getSelected() == CpsMode.MANUAL);
	private final SliderSetting swingCps = new SliderSetting("Swing CPS",
		"Fake swings per second.", 10, 1, 20, 1, ValueDisplay.INTEGER);
	private final CheckboxSetting hideFakeSwings =
		new CheckboxSetting("Hide fake swings", true);

	private final CheckboxSetting requireWeapon = new CheckboxSetting(
		"Require weapon",
		"Only attacks while holding a sword, axe or pickaxe.", false);
	private final CheckboxSetting requireAttackKey = new CheckboxSetting(
		"Require attack key", "Only attacks while the attack key is held.",
		false);
	private final CheckboxSetting hitSelect = new CheckboxSetting("Hit Select",
		"Skips attacks while you are taking knockback.", false);

	private final CheckboxSetting targetPlayers = new CheckboxSetting(
		"Target players", "Attacks other players.", true);
	private final CheckboxSetting targetHostile = new CheckboxSetting(
		"Target hostile", "Attacks hostile mobs.", false);
	private final CheckboxSetting targetPassive = new CheckboxSetting(
		"Target passive", "Attacks passive animals.", false);
	private final CheckboxSetting targetFriendly = new CheckboxSetting(
		"Target friendly",
		"Attacks other living entities (villagers, golems).", true);

	private final SliderSetting fov = new SliderSetting("FOV",
		"Only targets inside this field of view.", 180, 1, 180, 1,
		ValueDisplay.INTEGER);
	private final CheckboxSetting overrideRaycast = new CheckboxSetting(
		"Override raycast", "Aims exactly at the target before attacking.",
		true);
	private final CheckboxSetting tickLookahead = new CheckboxSetting(
		"Tick lookahead", "Uses one tick of look-ahead for moving targets.",
		false);
	private final CheckboxSetting throughWalls = new CheckboxSetting(
		"Through Walls", "Attacks targets without line of sight.", false);
	private final CheckboxSetting heypixelBypass = new CheckboxSetting(
		"Heypixel Bypass", "Uses randomised attack timing for Heypixel.",
		false);

	private final EnumSetting<AutoblockMode> autoblock =
		new EnumSetting<>("Autoblock", AutoblockMode.values(),
			AutoblockMode.OFF);
	private final CheckboxSetting keepSprintFov = new CheckboxSetting(
		"Keep Sprint FOV", "Keeps sprinting after an attack.", false);
	private final CheckboxSetting grimKeepSprint = new CheckboxSetting(
		"Grim Keep Sprint", "Keeps sprinting after an attack (Grim).", false);
	private final CheckboxSetting smartWeapon = new CheckboxSetting(
		"SmartWeapon", "Switches to an axe when the target is shielding.",
		false);

	private final CheckboxSetting visualsBox = new CheckboxSetting("Box",
		"Draws a box around the target.", false);
	private final CheckboxSetting visualsHalo = new CheckboxSetting("Halo",
		"Draws a ring around the target.", true);

	private RotationQueue rotationQueue;
	private OpenAura.Target currentTarget;
	private Entity renderTarget;
	private long nextAttackTime;
	private long nextSwingTime;
	private boolean pendingAttack;
	private boolean fakeBlocking;

	public KillauraHack()
	{
		super("Killaura");
		setCategory(Category.COMBAT);
		addConflictGroup(HackConflictGroup.COMBAT_TARGETING);

		addSetting(mode);
		addSetting(range);
		addSetting(rotationRange);
		addSetting(swingRange);
		addSetting(cpsMode);
		addSetting(attackCps);
		addSetting(swingCps);
		addSetting(hideFakeSwings);
		addSetting(requireWeapon);
		addSetting(requireAttackKey);
		addSetting(hitSelect);
		addSetting(targetPlayers);
		addSetting(targetHostile);
		addSetting(targetPassive);
		addSetting(targetFriendly);
		addSetting(fov);
		addSetting(overrideRaycast);
		addSetting(tickLookahead);
		addSetting(throughWalls);
		addSetting(heypixelBypass);
		addSetting(autoblock);
		addSetting(keepSprintFov);
		addSetting(grimKeepSprint);
		addSetting(smartWeapon);
		addSetting(visualsBox);
		addSetting(visualsHalo);
	}

	@Override
	protected void onEnable()
	{
		currentTarget = null;
		renderTarget = null;
		pendingAttack = false;
		fakeBlocking = false;
		nextAttackTime = 0;
		nextSwingTime = 0;
		rotationQueue = new RotationQueue(RotationQueue.Priority.COMBAT);
		rotationQueue.start();
		EVENTS.add(UpdateListener.class, this);
		EVENTS.add(HandleInputListener.class, this);
		EVENTS.add(RenderListener.class, this);
	}

	@Override
	protected void onDisable()
	{
		EVENTS.remove(UpdateListener.class, this);
		EVENTS.remove(HandleInputListener.class, this);
		EVENTS.remove(RenderListener.class, this);
		if(rotationQueue != null)
		{
			rotationQueue.stop();
			rotationQueue = null;
		}
		stopBlocking();
		currentTarget = null;
		renderTarget = null;
		pendingAttack = false;
	}

	@Override
	public void onUpdate()
	{
		pendingAttack = false;
		if(!requirementsMet())
		{
			clearTarget();
			return;
		}

		List<OpenAura.Target> targets = OpenAura.collect(attackConfig());
		currentTarget = OpenAura.select(targets,
			mode.getSelected() == Mode.SWITCH);
		renderTarget = currentTarget == null ? null : currentTarget.entity;

		if(currentTarget == null)
		{
			OpenAura.Target rotationTarget =
				OpenAura.selectNearest(OpenAura.collect(rotationConfig()));
			if(rotationTarget != null)
				rotationQueue.setRotation(rotationTarget.rotation);
			else
				rotationQueue.clear();
			updateAutoblock();
			tryFakeSwing();
			return;
		}

		rotationQueue.setRotation(currentTarget.rotation);
		updateAutoblock();

		if(!isClickReady() || !currentTarget.isAttackAvailable())
			return;
		if(hitSelect.isChecked() && MC.player.hurtTime > 0)
			return;

		pendingAttack = true;
	}

	@Override
	public void onHandleInput()
	{
		if(!pendingAttack || currentTarget == null || MC.player == null)
			return;

		OpenAura.Target target = currentTarget;
		pendingAttack = false;
		if(!target.entity.isAlive())
			return;

		if(overrideRaycast.isChecked())
		{
			EntityHitResult hit = OpenAura.raycastEntity(target.entity,
				MC.player.getEyePosition(), target.rotation, getRange());
			if(hit == null)
				return;
			if(!throughWalls.isChecked() && !OpenAura.hasLineOfSight(
				MC.player.getEyePosition(), hit.getLocation()))
				return;
		}

		if(smartWeapon.isChecked())
			switchToAxe(target.entity);

		if(MC.player.isUsingItem())
			MC.gameMode.releaseUsingItem(MC.player);

		rotationQueue.setRotation(target.rotation);

		boolean wasSprinting = MC.player.isSprinting();
		MC.gameMode.attack(MC.player, target.entity);
		MC.player.swing(InteractionHand.MAIN_HAND);

		if(keepSprintFov.isChecked() || grimKeepSprint.isChecked())
			keepSprint(wasSprinting);

		target.onAttack();
		nextAttackTime = System.currentTimeMillis() + clickDelay();
	}

	@Override
	public void onRender(PoseStack matrices, float partialTicks)
	{
		if(renderTarget == null || !renderTarget.isAlive())
			return;

		Vec3 pos = EntityUtils.getLerpedPos(renderTarget, partialTicks)
			.subtract(RenderUtils.getCameraPos());
		int color = WURST.getGui().getTheme().accent(0.85F);

		if(visualsBox.isChecked())
		{
			double half = renderTarget.getBbWidth() / 2.0D;
			drawBox(matrices, pos.x - half, pos.y, pos.z - half,
				pos.x + half, pos.y + renderTarget.getBbHeight(),
				pos.z + half, color);
		}

		if(visualsHalo.isChecked())
			drawHalo(matrices, pos, renderTarget, color);
	}

	public Entity getCurrentTarget()
	{
		return renderTarget;
	}

	public boolean shouldRenderFakeBlock()
	{
		return isEnabled() && autoblock.getSelected() == AutoblockMode.FAKE
			&& fakeBlocking && findBlockableHand() != null;
	}

	public InteractionHand getFakeBlockingHand()
	{
		return shouldRenderFakeBlock() ? findBlockableHand() : null;
	}

	private OpenAura.Config attackConfig()
	{
		return new OpenAura.Config(getRange(), fov.getValue(),
			targetFlags(), throughWalls.isChecked());
	}

	private OpenAura.Config rotationConfig()
	{
		return new OpenAura.Config(rotationRange.getValue(), fov.getValue(),
			targetFlags(), throughWalls.isChecked());
	}

	private int targetFlags()
	{
		int flags = 0;
		if(targetPlayers.isChecked())
			flags |= OpenAura.TARGET_PLAYERS;
		if(targetHostile.isChecked())
			flags |= OpenAura.TARGET_HOSTILE;
		if(targetPassive.isChecked())
			flags |= OpenAura.TARGET_PASSIVE;
		if(targetFriendly.isChecked())
			flags |= OpenAura.TARGET_FRIENDLY;
		return flags;
	}

	private double getRange()
	{
		return range.getValue();
	}

	private boolean requirementsMet()
	{
		if(MC.player == null || MC.level == null || MC.screen != null)
			return false;
		if(isConsumingFoodOrPotion())
			return false;
		if(requireAttackKey.isChecked() && !MC.options.keyAttack.isDown())
			return false;
		if(requireWeapon.isChecked())
		{
			ItemStack held = MC.player.getMainHandItem();
			if(!held.is(ItemTags.SWORDS) && !held.is(ItemTags.AXES)
				&& !held.is(ItemTags.PICKAXES))
				return false;
		}
		return true;
	}

	private boolean isConsumingFoodOrPotion()
	{
		if(MC.player == null || !MC.player.isUsingItem())
			return false;
		ItemStack stack = MC.player.getUseItem();
		return !stack.isEmpty()
			&& (stack.isEdible() || stack.getItem() instanceof PotionItem);
	}

	private boolean isClickReady()
	{
		// Auto mode waits for the held weapon's attack cooldown, so the
		// effective CPS follows the weapon's attack speed.
		if(cpsMode.getSelected() == CpsMode.AUTO)
			return MC.player != null
				&& MC.player.getAttackStrengthScale(0.5F) >= 1.0F;
		return System.currentTimeMillis() >= nextAttackTime;
	}

	private long clickDelay()
	{
		return Math.max(1, 1000 / attackCps.getValueI());
	}

	private void tryFakeSwing()
	{
		if(hideFakeSwings.isChecked()
			|| System.currentTimeMillis() < nextSwingTime)
			return;

		OpenAura.Config config = new OpenAura.Config(swingRange.getValue(),
			fov.getValue(), targetFlags(), throughWalls.isChecked());
		if(OpenAura.collect(config).isEmpty())
			return;

		MC.player.swing(InteractionHand.MAIN_HAND);
		nextSwingTime = System.currentTimeMillis()
			+ Math.max(1, 1000 / swingCps.getValueI());
	}

	private void switchToAxe(LivingEntity target)
	{
		if(!target.isBlocking())
			return;

		for(int i = 0; i < 9; i++)
		{
			ItemStack stack = MC.player.getInventory().getItem(i);
			if(!(stack.getItem() instanceof AxeItem))
				continue;
			if(MC.player.getInventory().selected == i)
				return;

			MC.player.getInventory().selected = i;
			MC.player.connection
				.send(new ServerboundSetCarriedItemPacket(i));
			return;
		}
	}

	private void keepSprint(boolean wasSprinting)
	{
		if(!wasSprinting || MC.player.hasEffect(MobEffects.BLINDNESS))
			return;

		Vec3 velocity = MC.player.getDeltaMovement();
		MC.player.setDeltaMovement(velocity.x * 0.6D, velocity.y,
			velocity.z * 0.6D);
		MC.player.setSprinting(true);
	}

	private void updateAutoblock()
	{
		AutoblockMode selected = autoblock.getSelected();
		if(selected == AutoblockMode.OFF || currentTarget == null)
		{
			stopBlocking();
			return;
		}

		if(selected == AutoblockMode.FAKE)
		{
			fakeBlocking = findBlockableHand() != null;
			return;
		}

		fakeBlocking = false;
		if(!MC.player.getMainHandItem().is(ItemTags.SWORDS))
		{
			stopBlocking();
			return;
		}

		InteractionHand hand = findBlockableHand();
		if(hand != null && !MC.player.isUsingItem())
			MC.gameMode.useItem(MC.player, hand);
	}

	private void stopBlocking()
	{
		fakeBlocking = false;
		if(MC.player != null && MC.player.isUsingItem()
			&& MC.player.getUseItem().getUseAnimation() == UseAnim.BLOCK)
			MC.gameMode.releaseUsingItem(MC.player);
	}

	private InteractionHand findBlockableHand()
	{
		if(MC.player == null)
			return null;
		for(InteractionHand hand : InteractionHand.values())
		{
			ItemStack stack = MC.player.getItemInHand(hand);
			if(stack.getUseAnimation() == UseAnim.BLOCK
				&& !MC.player.getCooldowns().isOnCooldown(stack.getItem()))
				return hand;
		}
		return null;
	}

	private void clearTarget()
	{
		currentTarget = null;
		renderTarget = null;
		pendingAttack = false;
		if(rotationQueue != null)
			rotationQueue.clear();
		stopBlocking();
	}

	private void drawBox(PoseStack matrices, double x1, double y1, double z1,
		double x2, double y2, double z2, int color)
	{
		Vec3[] corners = {new Vec3(x1, y1, z1), new Vec3(x2, y1, z1),
			new Vec3(x2, y1, z2), new Vec3(x1, y1, z2),
			new Vec3(x1, y2, z1), new Vec3(x2, y2, z1),
			new Vec3(x2, y2, z2), new Vec3(x1, y2, z2)};
		int[][] edges = {{0, 1}, {1, 2}, {2, 3}, {3, 0}, {4, 5}, {5, 6},
			{6, 7}, {7, 4}, {0, 4}, {1, 5}, {2, 6}, {3, 7}};

		for(int[] edge : edges)
			RenderUtils.drawLine(matrices, corners[edge[0]],
				corners[edge[1]], color, false);
	}

	private void drawHalo(PoseStack matrices, Vec3 pos, Entity target,
		int color)
	{
		double now = System.currentTimeMillis() / 1000.0D;
		double radius = Math.max(0.52D, target.getBbWidth() * 0.85D);
		double y = pos.y + target.getBbHeight() * 0.5D
			+ Math.sin(now * 4.0D) * 0.45D + 0.22D;
		int segments = 48;

		for(int i = 0; i < segments; i++)
		{
			double a1 = Math.PI * 2 * i / segments;
			double a2 = Math.PI * 2 * (i + 1) / segments;
			Vec3 start = new Vec3(pos.x + Math.sin(a1) * radius, y,
				pos.z + Math.cos(a1) * radius);
			Vec3 end = new Vec3(pos.x + Math.sin(a2) * radius, y,
				pos.z + Math.cos(a2) * radius);
			RenderUtils.drawLine(matrices, start, end, color, false);
		}
	}

	public enum CpsMode
	{
		AUTO("Auto"),
		MANUAL("Manual");

		private final String name;

		private CpsMode(String name)
		{
			this.name = name;
		}

		@Override
		public String toString()
		{
			return name;
		}
	}

	public enum Mode
	{
		SINGLE("Single"),
		SWITCH("Switch");

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

	public enum AutoblockMode
	{
		OFF("Off"),
		VANILLA("Vanilla"),
		FAKE("Fake");

		private final String name;

		private AutoblockMode(String name)
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
