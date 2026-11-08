/*
 * Copyright (c) 2026 WurstB+ Plus contributors
 *
 * This source code is subject to the terms of the GNU Affero General Public
 * License, version 3. If a copy of the AGPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/agpl-3.0.txt
 */
package net.wurstclient.hacks;


import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.network.protocol.game.ServerboundSetCarriedItemPacket;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.item.component.SwingAnimation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.PotionItem;
import net.minecraft.world.item.ItemUseAnimation;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;
import net.wurstclient.Category;
import net.wurstclient.opal.CPSProperty;
import net.wurstclient.opal.SwingDelay;
import net.wurstclient.opal.MouseHelper;
import net.wurstclient.events.PacketInputListener;
import net.minecraft.network.protocol.game.ClientboundDamageEventPacket;
import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket;
import net.wurstclient.events.PreMotionListener;
import net.wurstclient.events.PostMotionListener;
import net.wurstclient.events.RenderListener;
import net.wurstclient.events.UpdateListener;
import net.wurstclient.hack.Hack;
import net.wurstclient.hack.HackConflictGroup;
import net.wurstclient.hacks.scaffold.ScaffoldRotationModel;
import net.wurstclient.hacks.scaffold.ScaffoldRotationHandler;
import net.wurstclient.settings.SwingHandSetting.SwingHand;
import net.wurstclient.hacks.aura.OpenAura;
import net.wurstclient.hacks.aura.OpenAuraTargeting;
import net.wurstclient.util.MotionActionQueue;
import net.wurstclient.settings.CheckboxSetting;
import net.wurstclient.settings.EnumSetting;
import net.wurstclient.settings.SliderSetting;
import net.wurstclient.settings.SliderSetting.ValueDisplay;
import net.wurstclient.util.EntityUtils;
import net.wurstclient.util.EnchantmentUtils;
import net.wurstclient.util.RenderUtils;
import net.wurstclient.util.Rotation;
import net.wurstclient.util.RotationQueue;

/**
 * KillAura ported from OpenOpal's KillAuraModule
 * (Single/Switch targeting, raycasted rotations and attack windows).
 */
public final class KillauraHack extends Hack
	implements UpdateListener, PreMotionListener, PostMotionListener, RenderListener, PacketInputListener
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
		CpsMode.values(), CpsMode.MANUAL);
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

	private final OpenAuraTargeting targeting = new OpenAuraTargeting();
	private final MotionActionQueue<OpenAuraTargeting.Selection> actions =
		new MotionActionQueue<>();

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
		ValueDisplay.DEGREES);
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

	private final EnumSetting<ScaffoldRotationModel> rotationModel = new EnumSetting<>(
		"Rotation model", ScaffoldRotationModel.values(), ScaffoldRotationModel.INSTANT);
	private final SliderSetting maxAngle = new SliderSetting("Max angle", 90, 5, 360, 5, ValueDisplay.INTEGER);
	private final SliderSetting drift = new SliderSetting("Drift intensity", 1.2, 0.5, 2, 0.1, ValueDisplay.DECIMAL);
	private final SliderSetting jitter = new SliderSetting("Jitter intensity", 0.12, 0, 0.3, 0.01, ValueDisplay.DECIMAL);
	private final CheckboxSetting attackCooldown19 = new CheckboxSetting("1.9+ Attack Cooldown", false);
	private final ScaffoldRotationHandler rotationHandler = new ScaffoldRotationHandler();
	private int attacks;
	private long lastAttackTime;
	private RotationQueue rotationQueue;
	private OpenAura.Target currentTarget;
	private Entity renderTarget;
	private boolean fakeBlocking;
	private int grimKeepTicks, grimDamageKeepTicks, grimDamageWindowTicks;
    private Object statePlayer, stateWorld;
    private EntityHitResult lookaheadHit;
    private final CPSProperty attackTiming = new CPSProperty(attackCps::getValueI,
        () -> cpsMode.getSelected() == CpsMode.AUTO);
    private final CPSProperty swingTiming = new CPSProperty(swingCps::getValueI, () -> false);

	public KillauraHack()
	{
		super("Killaura");
		setCategory(Category.COMBAT);
		addConflictGroup(HackConflictGroup.COMBAT_TARGETING);

		addSetting(rotationModel);
		addSetting(maxAngle);
		addSetting(drift);
		addSetting(jitter);
		addSetting(attackCooldown19);
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
		actions.clear();
		fakeBlocking = false;
		targeting.reset();
		grimKeepTicks = grimDamageKeepTicks = grimDamageWindowTicks = 0;
		lookaheadHit = null;
		attacks = 0;
		rotationHandler.reset();
		rotationQueue = new RotationQueue(RotationQueue.Priority.COMBAT);
		rotationQueue.start();
		EVENTS.add(PacketInputListener.class, this);
		EVENTS.add(PreMotionListener.class, this);
		EVENTS.add(PostMotionListener.class, this);
		EVENTS.add(RenderListener.class, this);
	}

	@Override
	protected void onDisable()
	{
		EVENTS.remove(PacketInputListener.class, this);
		EVENTS.remove(PreMotionListener.class, this);
		EVENTS.remove(PostMotionListener.class, this);
		EVENTS.remove(RenderListener.class, this);
		if(rotationQueue != null)
		{
			rotationQueue.stop();
			rotationQueue = null;
		}
		stopBlocking();
		currentTarget = null;
		renderTarget = null;
		actions.clear();
		targeting.reset();
		grimKeepTicks = grimDamageKeepTicks = grimDamageWindowTicks = 0;
		lookaheadHit = null;
		attacks = 0;
		rotationHandler.reset();
	}

	@Override
	public void onUpdate()
	{
        if(statePlayer != MC.player || stateWorld != MC.level) {
            clearTarget();
            grimDamageKeepTicks = grimDamageWindowTicks = 0;
            statePlayer = MC.player;
            stateWorld = MC.level;
        }
        if(grimKeepTicks > 0) grimKeepTicks--;
        if(grimDamageKeepTicks > 0) grimDamageKeepTicks--;
        if(grimDamageWindowTicks > 0) grimDamageWindowTicks--;
		actions.clear();
		if(!requirementsMet())
		{
			clearTarget();
			return;
		}
		actions.begin(MC.player, MC.level, MC.player.tickCount);
		planTargets();
		updateAutoblock();
	}

	private void planTargets() { planTargets(true); }

	private void planTargets(boolean rotate)
	{
		OpenAuraTargeting.Selection selection = targeting.update(attackConfig(),
			rotationConfig(), mode.getSelected() == Mode.SWITCH);
		currentTarget = selection.attack();
		renderTarget = currentTarget == null ? null : currentTarget.entity;
		actions.schedule(selection);
        if(!rotate) return;
		if(selection.rotation() == null)
		{
			rotationQueue.clear();
			return;
		}
		Rotation planned = selection.rotation().rotation;
		rotationHandler.rotate(planned, heypixelBypass.isChecked()
			? ScaffoldRotationModel.HEYPIXEL : rotationModel.getSelected(),
			maxAngle.getValueF(), drift.getValue(), jitter.getValue());
		rotationQueue.setRotation(rotationHandler.getRotation());
	}

	@Override
	public void onPreMotion()
	{
		if(!requirementsMet()
			|| !actions.isCurrent(MC.player, MC.level, MC.player.tickCount))
		{
			clearTarget();
			return;
		}
		// Refresh after movement, before the shared queue chooses the packet aim.
		if(tickLookahead.isChecked()) {
			planTargets(false);
            if(currentTarget != null) rotationQueue.setRotation(rotationHandler.getRotation());
        }
	}

    @Override
    public void onPostMotion() {
        if(!tickLookahead.isChecked() || MC.player == null) return;
        lookaheadHit = currentTarget == null ? null : OpenAura.raycastEntity(
            currentTarget.entity, MC.player.getEyePosition(),
            rotationHandler.getRotation(), getRange());
    }

    /** KillAuraModule.onHandleInput: schedule vanilla clicks through MouseHelper. */
    public void onOpalMouseInput() {
        if(!requirementsMet()) return;
        OpenAura.Target target = currentTarget;
        if(target == null || MC.hitResult == null
            || MC.hitResult.getType() == net.minecraft.world.phys.HitResult.Type.MISS) {
            tryFakeSwing();
            return;
        }
        if(MC.player.isUsingItem() && !WURST.getHax().multitaskHack.attackingEntities()) return;
        target = targeting.revalidate(target, attackConfig());
        if(target == null) return;
        if(overrideRaycast.isChecked() && !heypixelBypass.isChecked()) {
            if(tickLookahead.isChecked()
                && (lookaheadHit == null || lookaheadHit.getEntity() != target.entity)) return;
            MC.hitResult = new EntityHitResult(target.entity, target.hitVec);
        }
        if(!(MC.hitResult instanceof EntityHitResult hit)) return;
        var velocityMode = WURST.getHax().antiKnockbackHack;
        if(hitSelect.isChecked() && velocityMode.isEnabled()) {
            if(velocityMode.getHitSelectSkips() > 0) { velocityMode.consumeHitSelectSkip(); return; }
            if(velocityMode.isAttacking()) return;
        }
        if(!isAttackSwingAvailable(target)) { attacks = 0; return; }
        if(hit.getEntity() != target.entity) return;
        if(smartWeapon.isChecked()) switchToAxe(target.entity);
        MouseHelper.getLeftButton().setPressed();
        targeting.onAttack(target, attacks == 0);
        if(grimKeepSprint.isChecked() && MC.player.isSprinting()) {
            grimKeepTicks = 2;
            Vec3 velocity = MC.player.getDeltaMovement();
            MC.player.setDeltaMovement(velocity.x * 0.6D, velocity.y, velocity.z * 0.6D);
            MC.player.setSprinting(true);
        }
        attackTiming.resetClick();
        SwingDelay.reset();
        if(heypixelBypass.isChecked()) lastAttackTime = System.currentTimeMillis();
        attacks = attacks > 0 ? attacks - 1 : 2;
    }

    public boolean shouldKeepSprint() {
        return isEnabled() && grimKeepSprint.isChecked()
            && (grimKeepTicks > 0 || grimDamageKeepTicks > 0);
    }

    @Override
    public void onReceivedPacket(PacketInputEvent event) {
        if(!grimKeepSprint.isChecked() || MC.player == null) return;
        var player = MC.player;
        var world = MC.level;
        boolean damage = event.getPacket() instanceof ClientboundDamageEventPacket p
            && p.entityId() == player.getId();
        boolean velocity = event.getPacket() instanceof ClientboundSetEntityMotionPacket p
            && p.id() == player.getId();
        if(!damage && !velocity) return;
        MC.execute(() -> {
            if(!isEnabled() || MC.player != player || MC.level != world) return;
            if(damage) grimDamageWindowTicks = 4;
            else if(grimDamageWindowTicks > 0) {
                grimDamageKeepTicks = Math.max(grimDamageKeepTicks, 6);
                grimDamageWindowTicks = 0;
            }
        });
    }

	@Override
	public void onRender(PoseStack matrices, float partialTicks)
	{
		if(!(renderTarget instanceof LivingEntity living) || !living.isAlive())
			return;

		Vec3 pos = EntityUtils.getLerpedPos(living, partialTicks)
			.subtract(RenderUtils.getCameraPos());
		int color = WURST.getGui().getTheme().accent(0.85F);

		if(visualsBox.isChecked())
			OpenAura.drawBox(matrices, pos, living, color);
		if(visualsHalo.isChecked())
			OpenAura.drawHalo(matrices, pos, living);
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
			targetFlags(), throughWalls.isChecked(), true);
	}

	private OpenAura.Config rotationConfig()
	{
		return new OpenAura.Config(rotationRange.getValue(), fov.getValue(),
			targetFlags(), throughWalls.isChecked(), true);
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

	public double getRange()
	{
		return range.getValue();
	}

	private boolean requirementsMet()
	{
		if(MC.player == null || MC.level == null || MC.gui.screen() != null)
			return false;
		if(MC.gameMode == null || !MC.player.isAlive()
			|| MC.player.isSpectator() || MC.player.isPassenger()
			|| WURST.getHax().scaffoldWalkHack.isEnabled())
			return false;
		if(isConsumingFoodOrPotion())
			return false;
		if(requireAttackKey.isChecked() && !MC.options.keyAttack.isDown())
			return false;
		if(requireWeapon.isChecked())
		{
			ItemStack held = net.wurstclient.opal.SlotHelper.getInstance().getMainHandStack(MC.player);
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
			&& (stack.has(net.minecraft.core.component.DataComponents.FOOD) || stack.getItem() instanceof PotionItem);
	}

	/** KillAuraModule.isAttackSwingAvailable, using the original burst window. */
	private boolean isAttackSwingAvailable(OpenAura.Target target)
	{
		boolean smartAttack = smartWeapon.isChecked() && isShielding(target.entity)
			&& hasAxe();
		if(attackCooldown19.isChecked() && !smartAttack)
			return MC.player.getAttackStrengthScale(0.5F) >= 1.0F;
		if(heypixelBypass.isChecked())
		{
			double baseDelay = 1000.0D / attackTiming.getCPS();
			long delay = (long)(baseDelay + (Math.random() - 0.5D) * baseDelay * 0.4D);
			return System.currentTimeMillis() - lastAttackTime >= delay;
		}
		return target.isAttackAvailable() || attacks > 0
			|| SwingDelay.isSwingAvailable(attackTiming, false);
	}

    private boolean isShielding(LivingEntity target) {
        return target.isUsingItem() && target.getUseItem().getItem() instanceof net.minecraft.world.item.ShieldItem;
    }

	private boolean hasAxe()
	{
		for(int slot = 0; slot < 9; slot++)
			if(MC.player.getInventory().getItem(slot).is(ItemTags.AXES))
				return true;
		return false;
	}

    private void tryFakeSwing() {
        if(MC.hitResult instanceof net.minecraft.world.phys.BlockHitResult
            && MC.hitResult.getType() == net.minecraft.world.phys.HitResult.Type.BLOCK) return;
        OpenAura.Config config = new OpenAura.Config(swingRange.getValue(),
            fov.getValue(), targetFlags(), throughWalls.isChecked(), true);
        if(!OpenAura.hasSwingTarget(config) || !SwingDelay.isSwingAvailable(swingTiming)) return;
        var left = MouseHelper.getLeftButton();
        left.setPressed(true, net.wurstclient.opal.RandomUtility.getRandomInt(2));
        if(hideFakeSwings.isChecked() && !(MC.hitResult instanceof EntityHitResult))
            left.setShowSwings(false);
        swingTiming.resetClick();
    }

    private void switchToAxe(LivingEntity target) {
        if(!isShielding(target)) return;
        for(int slot=0; slot<9; slot++) {
            if(MC.player.getInventory().getItem(slot).is(ItemTags.AXES)) {
                net.wurstclient.opal.SlotHelper.setCurrentItem(slot)
                    .silence(net.wurstclient.opal.SlotHelper.Silence.NONE);
                return;
            }
        }
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

        MouseHelper.getRightButton().setPressed(true, 2);
        MouseHelper.getRightButton().setShowSwings(false);
    }

	private void stopBlocking()
	{
		fakeBlocking = false;
		MouseHelper.getRightButton().setPressed(false, 0);
		if(MC.player != null && MC.gameMode != null && MC.player.isUsingItem()
			&& MC.player.getUseItem().getUseAnimation() == ItemUseAnimation.BLOCK)
			MC.gameMode.releaseUsingItem(MC.player);
	}

	private InteractionHand findBlockableHand()
	{
		if(MC.player == null)
			return null;
		for(InteractionHand hand : InteractionHand.values())
		{
			ItemStack stack = MC.player.getItemInHand(hand);
			if(stack.getUseAnimation() == ItemUseAnimation.BLOCK
				&& !MC.player.getCooldowns().isOnCooldown(stack))
				return hand;
		}
		return null;
	}

	private void clearTarget()
	{
		targeting.reset();
		grimKeepTicks = grimDamageKeepTicks = grimDamageWindowTicks = 0;
		lookaheadHit = null;
		attacks = 0;
		rotationHandler.reset();
		currentTarget = null;
		renderTarget = null;
		actions.clear();
		if(rotationQueue != null)
			rotationQueue.clear();
		stopBlocking();
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
