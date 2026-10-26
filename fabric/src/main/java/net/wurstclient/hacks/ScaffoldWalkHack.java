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
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;

import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.protocol.game.ClientboundTakeItemEntityPacket;
import net.minecraft.network.protocol.game.ServerboundSetCarriedItemPacket;
import net.minecraft.network.protocol.game.ServerboundUseItemOnPacket;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.wurstclient.Category;
import net.wurstclient.SearchTags;
import net.wurstclient.events.HandleInputListener;
import net.wurstclient.events.MoveInputListener;
import net.wurstclient.events.PacketInputListener;
import net.wurstclient.events.PacketInputListener.PacketInputEvent;
import net.wurstclient.events.PostMotionListener;
import net.wurstclient.events.PreMotionListener;
import net.wurstclient.events.RenderListener;
import net.wurstclient.events.RightClickListener;
import net.wurstclient.events.RightClickListener.RightClickEvent;
import net.wurstclient.hack.Hack;
import net.wurstclient.hacks.aura.OpenAura;
import net.wurstclient.hacks.scaffold.HeypixelScaffoldMode;
import net.wurstclient.hacks.scaffold.HypixelScaffoldMode;
import net.wurstclient.hacks.scaffold.PlayerSimulation;
import net.wurstclient.hacks.scaffold.ScaffoldMode;
import net.wurstclient.hacks.scaffold.ScaffoldRotationHandler;
import net.wurstclient.hacks.scaffold.ScaffoldRotationModel;
import net.wurstclient.hacks.scaffold.ScaffoldUtils;
import net.wurstclient.hacks.scaffold.SilenceTellyScaffoldMode;
import net.wurstclient.hacks.scaffold.SimpleTowerMode;
import net.wurstclient.hacks.scaffold.TellyScaffoldMode;
import net.wurstclient.settings.CheckboxSetting;
import net.wurstclient.settings.EnumSetting;
import net.wurstclient.settings.SliderSetting;
import net.wurstclient.settings.SliderSetting.ValueDisplay;
import net.wurstclient.settings.SwingHandSetting;
import net.wurstclient.settings.SwingHandSetting.SwingHand;
import net.wurstclient.util.Rotation;

/**
 * Scaffold ported from OpenOpal's {@code ScaffoldModule} and its eight mode
 * classes. The shared machinery (candidate search, support raytracing, placement
 * validation, same-Y auto jump, movement intelligence) lives here; each mode
 * owns its per-mode state and control flow in {@code hacks.scaffold}.
 */
@SearchTags({"scaffold walk", "BridgeWalk", "bridge walk", "AutoBridge",
	"auto bridge", "tower", "telly"})
public final class ScaffoldWalkHack extends Hack
	implements HandleInputListener, MoveInputListener, PreMotionListener,
	PostMotionListener, RenderListener, RightClickListener,
	PacketInputListener
{
	private static final Direction[] DIRECTIONS = Direction.values();
	private static final Direction[] PLACEMENT_DIRECTIONS = {Direction.DOWN,
		Direction.EAST, Direction.WEST, Direction.NORTH, Direction.SOUTH,
		Direction.UP};
	private static final double MAX_PLACEMENT_DISTANCE_SQ = 20.25D;
	private static final int NORMAL_SEARCH_DEPTH = 0;
	private static final int TELLY_SEARCH_DEPTH = 3;
	private static final int NORMAL_VISIT_BUDGET = 160;
	private static final int TELLY_VISIT_BUDGET = 96;
	private static final int NORMAL_RAYTRACE_BUDGET = 48;
	private static final int TELLY_RAYTRACE_BUDGET = 24;
	private static final int TELLY_TARGET_CANDIDATE_BUDGET = 48;
	private static final int RECENT_SUPPORT_QUARANTINE_TICKS = 1;
	private static final int RECENT_PLACED_BLOCK_TTL = 6;
	private static final int MAX_LAST_PLACED_BLOCKS = 4;
	private static final double[] FOOT_OFFSETS = {0.0D, 0.301D, -0.301D};
	private static final double[] LOOKAHEAD_STEPS = {0.0D, 0.35D, 0.7D, 1.05D,
		1.4D};
	private static final double[] HIT_OFFSETS = {0.0D, 0.24D, -0.24D};

	public final EnumSetting<Mode> mode = new EnumSetting<>("Mode",
		"Server-specific bridging behaviour.", Mode.values(), Mode.WATCHDOG);

	public final EnumSetting<ScaffoldRotationModel> rotationModel =
		new EnumSetting<>("Rotation model",
			"Rotation smoothing model ported from OpenOpal.",
			ScaffoldRotationModel.values(), ScaffoldRotationModel.INSTANT);
	public final SliderSetting maxAngle = new SliderSetting("Max angle",
		"Degrees per tick used by the non-instant rotation models.", 90, 5,
		360, 5, ValueDisplay.INTEGER)
			.visibleWhen(() -> rotationModel.getSelected()
				!= ScaffoldRotationModel.INSTANT);
	public final SliderSetting interactCps = new SliderSetting("Interact CPS",
		"Click speed used when no placement target is cached.", 10, 1, 20, 1,
		ValueDisplay.INTEGER);

	public final CheckboxSetting tower = new CheckboxSetting("Tower",
		"Hold jump to tower up.", false);
	public final EnumSetting<SwitchMode> switchMode =
		new EnumSetting<>("Switch mode", SwitchMode.values(),
			SwitchMode.HOTBAR);
	public final SwingHandSetting swingHand =
		new SwingHandSetting(this, SwingHand.CLIENT);
	public final CheckboxSetting snapRotations =
		new CheckboxSetting("Snap rotations", false);
	public final CheckboxSetting sameY = new CheckboxSetting("Same Y",
		"Keeps placing at the same Y level while bridging.", true);
	public final CheckboxSetting autoJump = new CheckboxSetting("Auto jump",
		"Jumps automatically to keep the same Y.", true)
			.visibleWhen(() -> sameY.isChecked());
	public final CheckboxSetting tellyHeypixel = new CheckboxSetting(
		"Heypixel", "Uses the Heypixel rotation model while telly bridging.",
		false).visibleWhen(this::isTellySetting);
	public final CheckboxSetting keepSprintFov = new CheckboxSetting(
		"Keep Sprint FOV", "Keeps sprinting after a placement.", false);
	public final CheckboxSetting duplicateRotPlace = new CheckboxSetting(
		"Duplicate Rot Place",
		"Jitters duplicate placement rotations so they are not identical.",
		false);
	public final CheckboxSetting blockOverlay = new CheckboxSetting(
		"Block overlay", "Outlines the block that is about to be placed.",
		true);
	public final CheckboxSetting overrideRaycast = new CheckboxSetting(
		"Override raycast", "Validates every placement with a block raycast.",
		true);
	public final CheckboxSetting interactBeforePlace = new CheckboxSetting(
		"Interact before place",
		"Sends an extra interact packet before placing.", false);

	public final CheckboxSetting movementIntelligence = new CheckboxSetting(
		"Movement intelligence",
		"Biases the placement rotation towards the movement direction.", false);
	public final CheckboxSetting diagonalMovement = new CheckboxSetting(
		"Diagonal movement", "Allows diagonal movement angles.", false)
			.visibleWhen(movementIntelligence::isChecked);
	public final CheckboxSetting movementSnapping = new CheckboxSetting(
		"Snap movement", "Rounds the movement angle to a grid.", true)
			.visibleWhen(movementIntelligence::isChecked);
	public final SliderSetting movementSteps = new SliderSetting("Steps",
		"Movement snapping resolution.", 3, 1, 3, 1, ValueDisplay.INTEGER)
			.visibleWhen(() -> movementIntelligence.isChecked()
				&& movementSnapping.isChecked());

	public final CheckboxSetting uitemsTelly = new CheckboxSetting(
		"Uitems Telly", "Enables telly bridging for Heypixel/Hypixel modes.",
		true).visibleWhen(this::isUitemsSetting);
	public final CheckboxSetting upTellyBypass = new CheckboxSetting(
		"UpTellyBypass", "Uses the up-telly bypass at the start of a jump.",
		false).visibleWhen(() -> getEffectiveMode() == Mode.HEYPIXEL
			&& uitemsTelly.isChecked());
	public final CheckboxSetting snap = new CheckboxSetting("Snap",
		"Snaps the placement rotation while bridging.", false)
			.visibleWhen(() -> isUitemsSetting() && !uitemsTelly.isChecked());
	public final SliderSetting rotateSpeed = new SliderSetting(
		"Rotation Speed", "Degrees per tick used when rotating to a face.",
		180, 1, 180, 1, ValueDisplay.INTEGER).visibleWhen(this::isUitemsSetting);
	public final SliderSetting rotateBackSpeed = new SliderSetting(
		"Rotation Back Speed", "Degrees per tick used when rotating back.",
		180, 1, 180, 1, ValueDisplay.INTEGER)
			.visibleWhen(() -> isUitemsSetting() && uitemsTelly.isChecked());
	public final SliderSetting tellyTick = new SliderSetting("Telly Ticks",
		"Air ticks spent looking forward before placing.", 5, 0, 10, 1,
		ValueDisplay.INTEGER)
			.visibleWhen(() -> isUitemsSetting() && uitemsTelly.isChecked());
	public final CheckboxSetting safeWalk = new CheckboxSetting("SafeWalk",
		"Prevents walking off the scaffold edge.", true)
			.visibleWhen(() -> isUitemsSetting() && !uitemsTelly.isChecked());

	public final EnumSetting<SilenceTellyMode> silenceTellyMode =
		new EnumSetting<>("SilenceTelly Mode", SilenceTellyMode.values(),
			SilenceTellyMode.TELLY).visibleWhen(this::isSilenceTellySetting);
	public final CheckboxSetting silenceAlwaysUpdateRotation =
		new CheckboxSetting("ST Always Update Rotation",
			"Always sends a fresh placement rotation.",
			false).visibleWhen(this::isSilenceTellySetting);
	public final SliderSetting silencePlaceTick = new SliderSetting(
		"ST PlaceTick", "Air ticks before placing in telly mode.", 1, 1, 5, 1,
		ValueDisplay.INTEGER).visibleWhen(this::isSilenceTellyTellySetting);
	public final SliderSetting silenceRotationTick = new SliderSetting(
		"ST RotationTick", "Ticks spent on the up-telly rotation.", 1, 1, 5,
		1, ValueDisplay.INTEGER).visibleWhen(this::isSilenceTellySetting);
	public final CheckboxSetting silenceSpoofItem = new CheckboxSetting(
		"ST Spoof Item", "Switches the server-side item silently.", true)
			.visibleWhen(this::isSilenceTellySetting);
	public final CheckboxSetting silenceNoSwing = new CheckboxSetting(
		"ST No Swing", "Skips the swing animation.", false)
			.visibleWhen(this::isSilenceTellySetting);
	public final CheckboxSetting silenceEagle = new CheckboxSetting("ST Eagle",
		"Sneaks around the edge after a jump.", false)
			.visibleWhen(this::isSilenceTellyTellySetting);
	public final SliderSetting silenceEagleTick = new SliderSetting(
		"ST EagleTick", "Ticks after a jump before sneaking.", 1, 1, 5, 1,
		ValueDisplay.INTEGER)
			.visibleWhen(() -> isSilenceTellySetting()
				&& silenceEagle.isChecked());
	public final SliderSetting silenceKeepEagleTick = new SliderSetting(
		"ST KeepEagleTick", "Ticks to keep sneaking.", 1, 1, 5, 1,
		ValueDisplay.INTEGER)
			.visibleWhen(() -> isSilenceTellySetting()
				&& silenceEagle.isChecked());
	public final CheckboxSetting silenceSnap = new CheckboxSetting("ST Snap",
		"Snaps the placement rotation instead of looking down.", false)
			.visibleWhen(this::isSilenceTellyTellySetting);
	public final CheckboxSetting silenceNoUptelly = new CheckboxSetting(
		"ST No Uptelly", "Never looks up while holding jump.", true)
			.visibleWhen(this::isSilenceTellyTellySetting);
	public final CheckboxSetting silenceGodBridge = new CheckboxSetting(
		"ST GodBridge", "Bridges with a fixed 75.5 degree pitch.", false)
			.visibleWhen(() -> isSilenceTellySetting()
				&& silenceTellyMode.getSelected() == SilenceTellyMode.NORMAL);
	public final CheckboxSetting silenceHeypixelUpTelly =
		new CheckboxSetting("ST Heypixel UpTelly",
			"Uses the Heypixel up-telly rotation sequence.", true)
			.visibleWhen(this::isSilenceTellyTellySetting);
	public final CheckboxSetting silenceSafeMode = new CheckboxSetting(
		"ST Safe Mode", "Uses the ground/air rescue rotation sequence.",
		false).visibleWhen(() -> isSilenceTellyTellySetting()
			&& silenceHeypixelUpTelly.isChecked());
	public final CheckboxSetting silenceTestOnGround = new CheckboxSetting(
		"ST Test OnGround", "Tests the ground jump before placing.", false)
			.visibleWhen(() -> isSilenceTellyTellySetting()
				&& silenceSafeMode.isChecked());
	public final CheckboxSetting silenceFixRotation = new CheckboxSetting(
		"ST Fix Rotation",
		"Quantises rotation changes to the mouse sensitivity grid.", true)
			.visibleWhen(this::isSilenceTellySetting);
	public final CheckboxSetting silenceSlowUpTelly = new CheckboxSetting(
		"ST SlowUpTelly", "Slows the up-telly rotation.", false)
			.visibleWhen(this::isSilenceTellyTellySetting);
	public final CheckboxSetting silenceBlockFly = new CheckboxSetting(
		"ST Block Fly", "Sends the offhand swap block-fly packet.", false)
			.visibleWhen(this::isSilenceTellySetting);
	public final CheckboxSetting silenceAbuseRotation = new CheckboxSetting(
		"ST Abuse Rotation", "Sends extra interact packets while rotating.",
		false).visibleWhen(this::isSilenceTellySetting);
	public final EnumSetting<SilenceBlockSlotMode> silenceBlockSlotMode =
		new EnumSetting<>("ST Block Slot Mode", SilenceBlockSlotMode.values(),
			SilenceBlockSlotMode.FARTHEST).visibleWhen(this::isSilenceTellySetting);
	public final EnumSetting<SilenceJumpMode> silenceJumpMode =
		new EnumSetting<>("ST Jump Mode", SilenceJumpMode.values(),
			SilenceJumpMode.NORMAL).visibleWhen(this::isSilenceTellyTellySetting);
	public final SliderSetting silenceSafeDistance = new SliderSetting(
		"ST Clutch Safe Distance", "Distance used by the falling rescue.",
		4.5, 1, 5, 0.25, ValueDisplay.DECIMAL)
			.visibleWhen(this::isSilenceTellySetting);
	public final CheckboxSetting silenceMark = new CheckboxSetting("ST Mark",
		"Marks the last placed block.", true)
			.visibleWhen(this::isSilenceTellySetting);
	public final CheckboxSetting silenceDuplicateRotPlace =
		new CheckboxSetting("ST DuplicateRotPlace",
			"Jitters duplicate placement rotations.", true)
			.visibleWhen(this::isSilenceTellySetting);
	public final CheckboxSetting silenceInteractBeforePlace =
		new CheckboxSetting("ST Interact Item Before Place",
			"Uses the held item before placing.", false)
			.visibleWhen(this::isSilenceTellySetting);

	public final SliderSetting range = new SliderSetting("Range",
		"How far to reach when placing blocks.", 4.5, 3, 6, 0.05,
		ValueDisplay.DECIMAL);

	private final Map<Mode, ScaffoldMode> modes =
		new EnumMap<>(Mode.class);
	private final ScaffoldRotationHandler rotationHandler =
		new ScaffoldRotationHandler();
	private Map<Integer, Integer> realStackSizeMap;

	private int sameYPos;
	private int groundTicks;
	private int airTicks;
	private int placeTick = -1;
	private long nextSimulatedClick;

	private BlockData blockCache;
	private BlockData lastOverlayData;
	private Rotation rotation;
	private Rotation lastRotation;
	private Rotation requestedRotation;
	private Rotation packetRotation;
	private int packetRotationTicks;
	private Rotation intelligentRotation;

	private final Map<BlockPos, Integer> recentPlacedBlocks = new HashMap<>();
	private final ArrayDeque<BlockPos> lastPlacedBlocks = new ArrayDeque<>();
	private BlockPos lastSearchOrigin;
	private BlockData lastSearchData;
	private int lastSearchAge = -1;

	private int autoJumpTicks;
	private int autoJumpGroundTicks;
	private int autoJumpAirTicks;
	private int lastAutoJumpAge = -20;
	private int lastAutoJumpStateAge = -1;
	private boolean simulatedJump;
	private boolean simulatedSneak;

	private int oldSlot = -1;
	private int silentSlot = -1;
	private Mode lastActiveMode;

	public ScaffoldWalkHack()
	{
		super("ScaffoldWalk");
		setCategory(Category.BLOCKS);

		modes.put(Mode.VANILLA, new SimpleTowerMode(this, Mode.VANILLA));
		modes.put(Mode.ANTI_GAMING_CHAIR,
			new SimpleTowerMode(this, Mode.ANTI_GAMING_CHAIR));
		modes.put(Mode.WATCHDOG, new SimpleTowerMode(this, Mode.WATCHDOG));
		modes.put(Mode.BLOXD, new SimpleTowerMode(this, Mode.BLOXD));
		modes.put(Mode.TELLY, new TellyScaffoldMode(this));
		modes.put(Mode.HEYPIXEL, new HeypixelScaffoldMode(this));
		modes.put(Mode.HYPIXEL, new HypixelScaffoldMode(this));
		modes.put(Mode.SILENCE_TELLY,
			new SilenceTellyScaffoldMode(this));

		addSetting(mode);
		addSetting(rotationModel);
		addSetting(maxAngle);
		addSetting(interactCps);
		addSetting(tower);
		addSetting(switchMode);
		addSetting(swingHand);
		addSetting(snapRotations);
		addSetting(sameY);
		addSetting(autoJump);
		addSetting(tellyHeypixel);
		addSetting(keepSprintFov);
		addSetting(duplicateRotPlace);
		addSetting(blockOverlay);
		addSetting(overrideRaycast);
		addSetting(interactBeforePlace);
		addSetting(movementIntelligence);
		addSetting(diagonalMovement);
		addSetting(movementSnapping);
		addSetting(movementSteps);
		addSetting(uitemsTelly);
		addSetting(upTellyBypass);
		addSetting(snap);
		addSetting(rotateSpeed);
		addSetting(rotateBackSpeed);
		addSetting(tellyTick);
		addSetting(safeWalk);
		addSetting(silenceTellyMode);
		addSetting(silenceAlwaysUpdateRotation);
		addSetting(silencePlaceTick);
		addSetting(silenceRotationTick);
		addSetting(silenceSpoofItem);
		addSetting(silenceNoSwing);
		addSetting(silenceEagle);
		addSetting(silenceEagleTick);
		addSetting(silenceKeepEagleTick);
		addSetting(silenceSnap);
		addSetting(silenceNoUptelly);
		addSetting(silenceGodBridge);
		addSetting(silenceHeypixelUpTelly);
		addSetting(silenceSafeMode);
		addSetting(silenceTestOnGround);
		addSetting(silenceFixRotation);
		addSetting(silenceSlowUpTelly);
		addSetting(silenceBlockFly);
		addSetting(silenceAbuseRotation);
		addSetting(silenceBlockSlotMode);
		addSetting(silenceJumpMode);
		addSetting(silenceSafeDistance);
		addSetting(silenceMark);
		addSetting(silenceDuplicateRotPlace);
		addSetting(silenceInteractBeforePlace);
		addSetting(range);
	}

	@Override
	public String getRenderName()
	{
		return getName() + " [" + mode.getSelected() + "]";
	}

	@Override
	protected void onEnable()
	{
		sameYPos = MC.player == null ? 0 : Mth.floor(MC.player.getY());
		groundTicks = 0;
		airTicks = 0;
		placeTick = -1;
		blockCache = null;
		lastOverlayData = null;
		rotation = null;
		lastRotation = null;
		requestedRotation = null;
		packetRotation = null;
		packetRotationTicks = 0;
		intelligentRotation = null;
		recentPlacedBlocks.clear();
		lastPlacedBlocks.clear();
		lastSearchOrigin = null;
		lastSearchData = null;
		lastSearchAge = -1;
		autoJumpTicks = 0;
		autoJumpGroundTicks = 0;
		autoJumpAirTicks = 0;
		lastAutoJumpAge = -20;
		lastAutoJumpStateAge = -1;
		oldSlot = -1;
		silentSlot = -1;
		realStackSizeMap = new HashMap<>();
		rotationHandler.reset();
		lastActiveMode = mode.getSelected();

		EVENTS.add(HandleInputListener.class, this);
		EVENTS.add(MoveInputListener.class, this);
		EVENTS.add(PreMotionListener.class, this);
		EVENTS.add(PostMotionListener.class, this);
		EVENTS.add(RenderListener.class, this);
		EVENTS.add(RightClickListener.class, this);
		EVENTS.add(PacketInputListener.class, this);
		activeMode().onEnable();
	}

	@Override
	protected void onDisable()
	{
		EVENTS.remove(HandleInputListener.class, this);
		EVENTS.remove(MoveInputListener.class, this);
		EVENTS.remove(PreMotionListener.class, this);
		EVENTS.remove(PostMotionListener.class, this);
		EVENTS.remove(RenderListener.class, this);
		EVENTS.remove(RightClickListener.class, this);
		EVENTS.remove(PacketInputListener.class, this);
		// Disable the mode that was actually ticking, not the one currently
		// selected: the setting may have changed since the last update.
		if(lastActiveMode != null)
			modes.get(lastActiveMode).onDisable();
		lastActiveMode = null;
		rotationHandler.reset();
		realStackSizeMap = null;
		resetSlot();
		releaseSimulatedInput();
		blockCache = null;
		lastOverlayData = null;
		rotation = null;
		requestedRotation = null;
		packetRotation = null;
		packetRotationTicks = 0;
		recentPlacedBlocks.clear();
		lastPlacedBlocks.clear();
		lastSearchOrigin = null;
		lastSearchData = null;
		lastSearchAge = -1;
	}

	/**
	 * OpenOpal's {@code PreGameTickEvent} + {@code MouseHandleInputEvent} in
	 * one hook: both run before the player tick, and Wurst's
	 * {@code HandleInputEvent} is fired at exactly that point.
	 */
	@Override
	public void onHandleInput()
	{
		if(MC.player == null || MC.level == null || MC.gameMode == null
			|| MC.player.isSpectator() || MC.player.isFallFlying())
		{
			releaseSimulatedInput();
			return;
		}

		if(mode.getSelected() != lastActiveMode)
		{
			if(lastActiveMode != null)
				modes.get(lastActiveMode).onDisable();
			lastActiveMode = mode.getSelected();
			blockCache = null;
				rotation = null;
			requestedRotation = null;
			packetRotation = null;
			packetRotationTicks = 0;
			rotationHandler.reset();
			invalidateSearchCache();
			modes.get(lastActiveMode).onEnable();
		}

		releaseSimulatedJump();
		updateGroundTicks();
		pruneRecentPlacedBlocks();
		rotationHandler.tickReverse();

		ScaffoldMode impl = activeMode();
		Mode effective = getEffectiveMode();

		// These modes drive the whole tick (and their own placement).
		if(effective == Mode.HEYPIXEL || effective == Mode.HYPIXEL
			|| effective == Mode.SILENCE_TELLY)
		{
			impl.onTick();
			return;
		}

		if(isTellyMode())
		{
			if(!updateHeldBlockSlot())
				return;
			impl.onTick();
			trySharedPlacement(true);
			return;
		}

		updateAutoJumpTicks();
		if(!updateHeldBlockSlot())
			return;
		runSharedPreTick(effective);
		trySharedPlacement(false);
	}

	@Override
	public void onMoveInput(MoveInputListener.MoveInputEvent event)
	{
		activeMode().onMoveInput(event);

		if(isUitemsMode() || isSilenceTellyMode()
			|| mode.getSelected() == Mode.TELLY)
			return;

		updateAutoJumpTicks();
		if(shouldAutoJump(event))
		{
			((net.wurstclient.mixin.LivingEntityAccessor)MC.player)
				.wurst_setNoJumpDelay(0);
			event.setJump(true);
			lastAutoJumpAge = MC.player.tickCount;
		}
	}

	@Override
	public void onRightClick(RightClickEvent event)
	{
		if(isEnabled() && MC.player != null && MC.screen == null)
			event.cancel();
	}

	@Override
	public void onReceivedPacket(PacketInputEvent event)
	{
		if(MC.player == null || realStackSizeMap == null)
			return;
		if(event.getPacket() instanceof ClientboundTakeItemEntityPacket pickup
			&& pickup.getPlayerId() == MC.player.getId())
		{
			int slot = MC.player.getInventory().selected;
			int current = MC.player.getMainHandItem().getCount();
			realStackSizeMap.put(slot, realStackSizeMap.getOrDefault(slot,
				current - pickup.getAmount()) + pickup.getAmount());
		}
	}

	@Override
	public void onPreMotion()
	{
		if(MC.player == null)
			return;

		Rotation out = requestedRotation;
		if(rotationHandler.isActive())
			out = rotationHandler.getRotation();

		Rotation modeRotation = null;
		if(packetRotationTicks > 0 && packetRotation != null)
			modeRotation = packetRotation;
		else
		{
			RotationRequest request = activeMode().getPacketRotation();
			if(request != null && request.rotation() != null)
				modeRotation = request.rotation();
		}

		if(modeRotation != null)
			out = modeRotation;

		if(out != null)
			WURST.getRotationFaker().setRotationPacket(out);

		if(packetRotationTicks > 0)
			packetRotationTicks--;
	}

	@Override
	public void onPostMotion()
	{
		activeMode().onPostMotion();
	}

	@Override
	public void onRender(PoseStack matrices, float partialTicks)
	{
		if(!blockOverlay.isChecked() || MC.level == null)
			return;

		BlockPos mark;
		if(activeMode() instanceof SilenceTellyScaffoldMode silence)
			mark = silenceMark.isChecked() ? silence.getMarkPosition() : null;
		else
			mark = blockCache == null ? null : blockCache.placementPos();
		if(mark == null)
			return;

		OpenAura.drawBox(matrices, new AABB(mark),
			WURST.getGui().getTheme().accent(0.75F));
	}

	// ------------------------------------------------------------- shared tick
	/**
	 * OpenOpal's {@code ScaffoldModule#onPreGameTick} shared path.
	 */
	private void runSharedPreTick(Mode effective)
	{
		boolean autoJumpActive = isSameYAutoJumpActive();
		boolean manualJump = MC.options.keyJump.isDown();
		boolean updateY = !sameY.isChecked()
			|| autoJump.isChecked() && manualJump && !isRecentAutoJump()
			|| MC.player.onGround() && !autoJumpActive
			|| Math.abs(Math.floor(MC.player.getY()) - sameYPos) > 3
			|| WURST.getHax().longJumpHack.isEnabled()
			|| WURST.getHax().flightHack.isEnabled();
		if(updateY)
			sameYPos = Mth.floor(MC.player.getY());

		intelligentRotation = null;

		boolean watchdog = effective == Mode.WATCHDOG;
		if(autoJumpActive || !watchdog || !MC.options.keyJump.isDown()
			|| !MC.player.onGround()
				&& (MC.player.getDeltaMovement().y >= 0.0D
					|| ScaffoldUtils.isBoxEmpty(MC.player,
						MC.player.getBoundingBox().move(0.0D,
							MC.player.getDeltaMovement().y, 0.0D))))
		{
			updateMovementIntelligence();
			updateData();
			updateMovementIntelligence();
		}else
		{
			blockCache = null;
			rotation = null;
			requestedRotation = null;
		}

		if(rotation != null)
			applyRotation(autoJumpActive, effective);
	}

	private void applyRotation(boolean autoJumpActive, Mode effective)
	{
		if(snapRotations.isChecked() && blockCache == null)
			return;

		Rotation target =
			autoJumpActive ? getAutoJumpRotation(rotation) : rotation;
		Mode selected = mode.getSelected();

		if(effective == selected && effective == Mode.WATCHDOG
			&& isHypixelServer())
			rotationHandler.rotateHypixel(target, isDiagonalYaw(), airTicks);
		else
		{
			ScaffoldRotationModel model = effective == selected
				? rotationModel.getSelected() : ScaffoldRotationModel.INSTANT;
			rotationHandler.rotate(target, model, maxAngle.getValueF());
		}

		requestedRotation = rotationHandler.getRotation();
	}

	/**
	 * OpenOpal's {@code MouseHandleInputEvent} placement branch.
	 */
	private void trySharedPlacement(boolean telly)
	{
		boolean isBlock = hasHeldPlaceableBlock();
		if(blockCache == null)
		{
			if(isBlock)
				simulateClick();
			return;
		}

		boolean useOverride = rotation != null && overrideRaycast.isChecked();
		BlockHitResult hitResult;
		if(telly)
			hitResult = getCurrentTellyHitResult();
		else if(useOverride)
			// Do not mutate MC.hitResult: the vanilla crosshair is recomputed
			// on its own schedule and a stale override would make the CPS
			// fallback place blocks at a target the player never looked at.
			hitResult = blockCache.hit();
		else
			hitResult =
				MC.hitResult instanceof BlockHitResult blockHit ? blockHit
					: null;

		boolean canPlaceCache = telly
			? hitResult != null && isValidPlacementHit(hitResult, true)
			: hitResult != null && (useOverride || isLookingAtBlockCache())
				&& canPlaceCurrentCache(hitResult);

		if(shouldDelayAutoJumpPlacement())
			return;
		if(!isBlock || !canPlaceCache)
			return;

		Block blockOver = useOverride && !telly
			? MC.level.getBlockState(hitResult.getBlockPos()).getBlock()
			: getBlockOver();
		if(ScaffoldUtils.isBlockInteractable(blockOver))
			return;

		placeBlock(hitResult);
	}

	private boolean simulateClick()
	{
		long now = System.currentTimeMillis();
		if(now < nextSimulatedClick)
			return false;
		if(!(MC.hitResult instanceof BlockHitResult hitResult))
			return false;
		if(!isValidPlacementHit(hitResult, false) || !placeBlock(hitResult))
			return false;
		nextSimulatedClick =
			now + 1000L / Math.max(1, interactCps.getValueI());
		return true;
	}

	private boolean isLookingAtBlockCache()
	{
		if(blockCache == null
			|| !(MC.hitResult instanceof BlockHitResult hitResult))
			return false;
		return hitResult.getType() == net.minecraft.world.phys.HitResult.Type.BLOCK
			&& hitResult.getBlockPos().equals(blockCache.block().pos())
			&& hitResult.getDirection() == blockCache.block().face();
	}

	private boolean canPlaceCurrentCache(BlockHitResult hitResult)
	{
		if(!isTellyMode())
			return hitResult != null
				&& isValidPlacementHit(hitResult, false);
		if(rotation == null || blockCache == null || MC.player == null
			|| MC.player.onGround())
			return false;
		return hitResult != null && isValidPlacementHit(hitResult, true);
	}

	private Block getBlockOver()
	{
		if(MC.hitResult instanceof BlockHitResult hitResult
			&& hitResult.getType() == net.minecraft.world.phys.HitResult.Type.BLOCK)
			return MC.level.getBlockState(hitResult.getBlockPos()).getBlock();
		return null;
	}

	private boolean shouldAutoJump(MoveInputListener.MoveInputEvent event)
	{
		if(!sameY.isChecked() || !autoJump.isChecked() || MC.player == null
			|| MC.level == null)
			return false;
		if(!MC.player.onGround() || MC.player.isShiftKeyDown()
			|| event.isSneak() || MC.player.hasEffect(MobEffects.JUMP))
			return false;
		if(MC.options.keyUse.isDown())
			return false;
		if(getPlaceableBlock() == -1
			&& !ScaffoldUtils.isPlaceableStack(MC.player.getOffhandItem()))
			return false;
		return Math.abs(event.getForward()) > 1.0E-4F
			|| Math.abs(event.getSideways()) > 1.0E-4F
			|| ScaffoldUtils.isMoving();
	}

	private boolean isUitemsMode()
	{
		Mode effective = getEffectiveMode();
		return effective == Mode.HEYPIXEL || effective == Mode.HYPIXEL;
	}

	private boolean isSilenceTellyMode()
	{
		return getEffectiveMode() == Mode.SILENCE_TELLY;
	}

	private boolean isDiagonalYaw()
	{
		float direction = Math.abs(ScaffoldUtils.moveDirectionDegrees() % 90);
		return direction > 15 && direction < 75;
	}

	private boolean isHypixelServer()
	{
		var server = MC.getCurrentServer();
		if(server == null || server.ip == null)
			return false;
		String ip = server.ip.toLowerCase(java.util.Locale.ROOT);
		return ip.contains("hypixel.net") || ip.contains("hypixel");
	}

	public boolean hasHeldPlaceableBlock()
	{
		if(MC.player == null)
			return false;
		if(isTellyMode())
			return hasMainHandPlaceableBlock();
		return hasMainHandPlaceableBlock()
			|| ScaffoldUtils.isPlaceableStack(MC.player.getOffhandItem());
	}

	public boolean shouldSafeWalk()
	{
		return isEnabled() && safeWalk.isChecked() && !isTellyMode()
			&& MC.player != null && MC.player.onGround()
			&& !MC.player.input.jumping;
	}

	public boolean hasMainHandPlaceableBlock()
	{
		return MC.player != null
			&& ScaffoldUtils.isPlaceableStack(MC.player.getMainHandItem());
	}

	public boolean hasHotbarPlaceableBlock()
	{
		return getPlaceableBlock() != -1;
	}

	public int getPlaceableBlock()
	{
		for(int i = 0; i < 9; i++)
		{
			ItemStack stack = MC.player.getInventory().getItem(i);
			int count = realStackSizeMap == null ? stack.getCount()
				: realStackSizeMap.getOrDefault(i, stack.getCount());
			if(count > 0 && ScaffoldUtils.isPlaceableStack(stack))
				return i;
		}
		return -1;
	}

	public boolean updateHeldBlockSlot()
	{
		if(MC.player == null)
			return false;

		int slot = getPlaceableBlock();
		if(slot == -1)
			return !isTellyMode() && ScaffoldUtils
				.isPlaceableStack(MC.player.getOffhandItem());

		if(MC.player.getInventory().selected != slot)
		{
			if(oldSlot == -1)
				oldSlot = MC.player.getInventory().selected;
			if(switchMode.getSelected() == SwitchMode.FULL)
				silentSlot = slot;
			MC.player.getInventory().selected = slot;
			MC.player.connection
				.send(new ServerboundSetCarriedItemPacket(slot));
		}
		return true;
	}

	public void resetSlot()
	{
		if(MC.player == null)
		{
			oldSlot = -1;
			silentSlot = -1;
			return;
		}
		if(oldSlot != -1 && oldSlot != MC.player.getInventory().selected)
		{
			MC.player.getInventory().selected = oldSlot;
			if(MC.getConnection() != null)
				MC.getConnection()
					.send(new ServerboundSetCarriedItemPacket(oldSlot));
		}
		oldSlot = -1;
		silentSlot = -1;
	}

	// ------------------------------------------------------- placement engine
	public BlockHitResult getCurrentTellyHitResult()
	{
		if(!isTellyMode() || rotation == null || blockCache == null
			|| MC.player == null)
			return null;

		Rotation current = requestedRotation != null ? requestedRotation
			: rotationHandler.getRotation();
		BlockHitResult hitResult = ScaffoldUtils.raycastBlock(current,
			range.getValue());
		if(hitResult == null)
			return null;
		BlockWithDirection block = blockCache.block();
		return hitResult.getBlockPos().equals(block.pos())
			&& hitResult.getDirection() == block.face() ? hitResult : null;
	}

	public boolean isValidPlacementHit(BlockHitResult hitResult,
		boolean checkPredictedCollision)
	{
		return isValidPlacementHit(hitResult, checkPredictedCollision,
			findPlacementHand(!isTellyMode()));
	}

	public boolean isValidPlacementHit(BlockHitResult hitResult,
		boolean checkPredictedCollision, PlacementHand hand)
	{
		if(MC.player == null || MC.level == null || hitResult == null
			|| hand == null)
			return false;
		if(!isValidSupportBlock(hitResult))
			return false;

		BlockPos support = hitResult.getBlockPos();
		if(ScaffoldUtils
			.isBlockInteractable(MC.level.getBlockState(support).getBlock()))
			return false;

		UseOnContext useOnContext = new UseOnContext(MC.player, hand.hand(),
			hitResult);
		BlockPlaceContext placeContext = hand.blockItem()
			.updatePlacementContext(new BlockPlaceContext(useOnContext));
		if(placeContext == null)
			return false;

		BlockPos placePos = support.relative(hitResult.getDirection());
		if(!MC.level.isInWorldBounds(placePos)
			|| !MC.level.getBlockState(placePos).canBeReplaced())
			return false;

		BlockState placementState = hand.blockItem().getBlock()
			.getStateForPlacement(placeContext);
		if(placementState == null
			|| !placementState.canSurvive(MC.level, placePos))
			return false;

		VoxelShape shape = placementState.getCollisionShape(MC.level,
			placePos);
		if(!shape.isEmpty())
		{
			AABB blockBox = shape.bounds().move(placePos);
			if(MC.player.getBoundingBox().intersects(blockBox))
				return false;
			if(checkPredictedCollision
				&& MC.player.getBoundingBox()
					.move(MC.player.getDeltaMovement()).intersects(blockBox)
				&& !isLandingPlacementCollisionAllowed(blockBox))
				return false;
		}
		return true;
	}

	private boolean isLandingPlacementCollisionAllowed(AABB blockBox)
	{
		AABB currentBox = MC.player.getBoundingBox();
		return MC.player.getDeltaMovement().y <= 0.0D
			&& currentBox.minY >= blockBox.maxY - 1.0E-3D;
	}

	public boolean placeBlock(BlockHitResult hitResult)
	{
		if(MC.player == null || MC.level == null || MC.gameMode == null
			|| hitResult == null)
			return false;
		if(placeTick == MC.player.tickCount)
			return false;

		PlacementHand hand = findPlacementHand(!isTellyMode());
		if(hand == null
			|| !isValidPlacementHit(hitResult, isTellyMode(), hand))
			return false;

		if(interactBeforePlace.isChecked())
			MC.player.connection
				.send(new ServerboundUseItemOnPacket(hand.hand(), hitResult, 0));

		boolean wasSprinting = MC.player.isSprinting();
		InteractionResult result = MC.gameMode.useItemOn(MC.player,
			hand.hand(), hitResult);
		if(!result.consumesAction())
			return false;

		swingHand.swing(hand.hand());
		placeTick = MC.player.tickCount;

		if(!MC.player.isCreative() && realStackSizeMap != null)
		{
			int slot = MC.player.getInventory().selected;
			realStackSizeMap.put(slot, realStackSizeMap.getOrDefault(slot,
				MC.player.getMainHandItem().getCount() + 1) - 1);
		}

		BlockPos placed = hitResult.getBlockPos()
			.relative(hitResult.getDirection());
		if(isTellyMode())
			recentPlacedBlocks.put(placed, MC.player.tickCount);
		trackPlacedBlock(placed);

		if(keepSprintFov.isChecked() && wasSprinting)
			MC.player.setSprinting(true);

		if(switchMode.getSelected() == SwitchMode.FULL)
			restoreSlotAfterPlacement();

		activeMode().onBlockPlaced(hitResult);
		return true;
	}

	private void restoreSlotAfterPlacement()
	{
		if(silentSlot == -1 || MC.player == null)
			return;
		silentSlot = -1;
		if(oldSlot != MC.player.getInventory().selected)
		{
			MC.player.getInventory().selected = oldSlot;
			MC.player.connection
				.send(new ServerboundSetCarriedItemPacket(oldSlot));
		}
	}

	public boolean canPlaceAtHit(BlockHitResult hitResult,
		boolean checkPredictedCollision)
	{
		return isValidPlacementHit(hitResult, checkPredictedCollision);
	}

	/**
	 * @return true once per player tick. OpenOpal's placement code refuses a
	 *         second placement in the same tick.
	 */
	public boolean canPlaceThisTick()
	{
		return MC.player != null && placeTick != MC.player.tickCount;
	}

	/**
	 * Bookkeeping for placements performed by a mode's own placement flow.
	 */
	public void notePlaced(BlockPos placed)
	{
		if(MC.player == null || placed == null)
			return;
		placeTick = MC.player.tickCount;
		recentPlacedBlocks.put(placed, MC.player.tickCount);
		trackPlacedBlock(placed);
	}

	public PlacementHand findPlacementHand(boolean allowOffhand)
	{
		if(MC.player == null)
			return null;
		if(ScaffoldUtils.isPlaceableStack(MC.player.getMainHandItem()))
			return new PlacementHand(InteractionHand.MAIN_HAND,
				(BlockItem)MC.player.getMainHandItem().getItem());
		if(allowOffhand
			&& ScaffoldUtils.isPlaceableStack(MC.player.getOffhandItem()))
			return new PlacementHand(InteractionHand.OFF_HAND,
				(BlockItem)MC.player.getOffhandItem().getItem());
		return null;
	}

	// ---------------------------------------------------------- target search
	public void updateData()
	{
		if(blockCache != null && canReuseCachedBlockData(blockCache))
		{
			rotation = blockCache.rotation();
			return;
		}

		blockCache = findBlockData();
		// The simulated fallback predicts where the player will land. Running
		// it while the player is still rising would place blocks at the jump
		// apex, on top of the bridge, which then turns into a staircase.
		if(blockCache == null && !MC.player.onGround()
			&& MC.player.getDeltaMovement().y <= 0.0D)
		{
			PlayerSimulation simulation = new PlayerSimulation(MC.player);
			if(simulation.getSimulatedEntity() != null)
				for(int i = 0; i < 10; i++)
				{
					simulation.simulateTick();
					BlockPos target = simulation.getSimulatedEntity()
						.blockPosition().below();
					if(target.getY() > sameYPos - 1)
						continue;
					BlockData data = getBlockData(target,
						simulation.getSimulatedEntity());
					if(data != null)
					{
						blockCache = data;
						break;
					}
				}
		}
		rotation = blockCache == null ? null : blockCache.rotation();
	}

	private boolean canReuseCachedBlockData(BlockData data)
	{
		if(data == null || data.rotation() == null || data.block() == null)
			return false;
		// A cache computed at the old sameY level must not survive a level
		// change; otherwise a single high block turns into a staircase.
		if(!isTellyMode() && data.placementPos().getY() != sameYPos - 1)
			return false;
		BlockHitResult hitResult = ScaffoldUtils
			.raycastBlock(data.rotation(), range.getValue());
		return hitResult != null
			&& hitResult.getBlockPos().equals(data.block().pos())
			&& hitResult.getDirection() == data.block().face()
			&& isValidSupport(hitResult.getBlockPos());
	}

	public BlockData findBlockData()
	{
		BlockPos base = BlockPos
			.containing(MC.player.getX(), sameYPos, MC.player.getZ()).below();
		for(BlockPos candidate : getTargetBlockCandidates(base,
			MC.player))
		{
			BlockData data = getBlockData(candidate, MC.player);
			if(data != null)
				return data;
		}
		return null;
	}

	public BlockData findBlockDataFor(int x, int y, int z)
	{
		return getBlockData(new BlockPos(x, y, z), MC.player);
	}

	public BlockData findBestTellyBlockData(int y)
	{
		if(MC.player == null || MC.level == null)
			return null;

		BlockPos base = BlockPos.containing(MC.player.getX(), y,
			MC.player.getZ());
		Vec3 eyePos = MC.player.getEyePosition();
		int checked = 0;
		for(BlockPos target : getTargetBlockCandidates(base, MC.player))
		{
			if(checked++ >= TELLY_TARGET_CANDIDATE_BUDGET)
				break;
			BlockData data = getDirectBlockData(target, eyePos);
			if(data != null)
				return data;
		}
		return getBlockData(base, MC.player);
	}

	private List<BlockPos> getTargetBlockCandidates(BlockPos base,
		net.minecraft.world.entity.player.Player entity)
	{
		if(!isTellyMode())
			return getBridgeCandidates(base, entity);

		LinkedHashSet<BlockPos> candidates = new LinkedHashSet<>();
		candidates.add(base.immutable());
		addLastPlacedCandidate(candidates, base);

		Vec3 velocity = entity.getDeltaMovement();
		for(double step : LOOKAHEAD_STEPS)
			addFootCandidates(candidates, entity.position()
				.add(velocity.x * step, 0.0D, velocity.z * step),
				base.getY());

		if(ScaffoldUtils.isMoving())
		{
			float direction = ScaffoldUtils.moveDirectionDegrees();
			double radians = Math.toRadians(direction);
			double dirX = -Mth.sin((float)radians);
			double dirZ = Mth.cos((float)radians);
			addFootCandidates(candidates, entity.position()
				.add(dirX * 0.35D, 0.0D, dirZ * 0.35D), base.getY());
			addFootCandidates(candidates, entity.position()
				.add(dirX * 0.65D, 0.0D, dirZ * 0.65D), base.getY());
			addFootCandidates(candidates, entity.position()
				.add(dirX * 1.05D, 0.0D, dirZ * 1.05D), base.getY());
			addFootCandidates(candidates, entity.position()
				.add(dirX * 1.45D, 0.0D, dirZ * 1.45D), base.getY());

			double sideX = Mth.cos((float)radians);
			double sideZ = Mth.sin((float)radians);
			addFootCandidates(candidates, entity.position().add(
				dirX * 0.85D + sideX * 0.32D, 0.0D,
				dirZ * 0.85D + sideZ * 0.32D), base.getY());
			addFootCandidates(candidates, entity.position().add(
				dirX * 0.85D - sideX * 0.32D, 0.0D,
				dirZ * 0.85D - sideZ * 0.32D), base.getY());
		}
		return new ArrayList<>(candidates);
	}

	/**
	 * Normal (non-telly) bridging must stay on the block column the player is
	 * walking through. OpenOpal's 3x3 foot candidates plus BFS can legally
	 * place several blocks to the side, which users see as "random" bridging,
	 * so the non-telly path only considers the current column and the column
	 * ahead in the movement direction.
	 */
	private List<BlockPos> getBridgeCandidates(BlockPos base,
		net.minecraft.world.entity.player.Player entity)
	{
		LinkedHashSet<BlockPos> candidates = new LinkedHashSet<>();
		candidates.add(base.immutable());
		candidates.add(BlockPos.containing(entity.getX(), base.getY(),
			entity.getZ()));

		if(!ScaffoldUtils.isMoving())
			return new ArrayList<>(candidates);

		float direction = ScaffoldUtils.moveDirectionDegrees();
		double radians = Math.toRadians(direction);
		double dirX = -Mth.sin((float)radians);
		double dirZ = Mth.cos((float)radians);
		for(int i = 1; i <= 3; i++)
			candidates.add(BlockPos.containing(entity.getX() + dirX * i,
				base.getY(), entity.getZ() + dirZ * i));
		return new ArrayList<>(candidates);
	}

	private void addLastPlacedCandidate(Set<BlockPos> candidates,
		BlockPos base)
	{
		if(lastPlacedBlocks.size() < 2)
			return;
		Iterator<BlockPos> iterator = lastPlacedBlocks.descendingIterator();
		BlockPos last = iterator.next();
		BlockPos previous = iterator.next();
		int dx = Integer.compare(last.getX() - previous.getX(), 0);
		int dz = Integer.compare(last.getZ() - previous.getZ(), 0);
		if(dx != 0 || dz != 0)
			candidates.add(new BlockPos(last.getX() + dx, base.getY(),
				last.getZ() + dz));
	}

	private void addFootCandidates(Set<BlockPos> candidates, Vec3 pos,
		int y)
	{
		for(double xOffset : FOOT_OFFSETS)
			for(double zOffset : FOOT_OFFSETS)
				candidates.add(BlockPos.containing(pos.x + xOffset, y,
					pos.z + zOffset));
	}

	public BlockData getBlockData(BlockPos target,
		net.minecraft.world.entity.player.Player entity)
	{
		if(!ScaffoldUtils.isReplaceable(target))
			return null;

		boolean canUseCache = entity == MC.player;
		if(canUseCache && lastSearchAge == entity.tickCount
			&& target.equals(lastSearchOrigin)
			&& isCachedBlockDataUsable(lastSearchData, target))
			return lastSearchData;

		Vec3 eyePos = entity.getEyePosition();
		BlockData data = searchPlacementTarget(target, eyePos);
		if(canUseCache)
		{
			lastSearchOrigin = target.immutable();
			lastSearchData = data;
			lastSearchAge = entity.tickCount;
		}
		return data;
	}

	private boolean isCachedBlockDataUsable(BlockData data,
		BlockPos target)
	{
		if(data == null || MC.level == null)
			return false;
		BlockWithDirection block = data.block();
		return block.pos().relative(block.face()).equals(target)
			&& ScaffoldUtils.isReplaceable(target)
			&& isValidSupport(block.pos());
	}

	private BlockData searchPlacementTarget(BlockPos origin, Vec3 eyePos)
	{
		Queue<PlacementCandidate> queue = new PriorityQueue<>(
			Comparator
				.comparingDouble((PlacementCandidate c) -> c.pos()
					.distSqr(origin))
				.thenComparingInt(c -> c.depth));
		Set<BlockPos> visited = new HashSet<>();
		Set<BlockWithDirection> checkedTargets = new HashSet<>();
		boolean telly = isTellyMode();
		int maxDepth = telly ? TELLY_SEARCH_DEPTH : NORMAL_SEARCH_DEPTH;
		int visitBudget = telly ? TELLY_VISIT_BUDGET
			: NORMAL_VISIT_BUDGET;
		int raytraceBudget = telly ? TELLY_RAYTRACE_BUDGET
			: NORMAL_RAYTRACE_BUDGET;
		int visitedCount = 0;
		int raytraceCount = 0;

		queue.add(new PlacementCandidate(origin, 0));
		visited.add(origin);

		while(!queue.isEmpty() && visitedCount++ < visitBudget)
		{
			PlacementCandidate candidate = queue.poll();
			if(candidate.depth() > maxDepth
				|| candidate.pos().distSqr(origin) > MAX_PLACEMENT_DISTANCE_SQ
				// Bridging must never build a second layer above the target
				// level. Without this, a solid block under the player makes
				// the BFS climb upwards and turns the bridge into a staircase.
				|| !telly && candidate.pos().getY() > origin.getY()
				|| !ScaffoldUtils.isReplaceable(candidate.pos()))
				continue;

			for(BlockWithDirection block : getSortedSupports(
				candidate.pos(), origin))
			{
				if(!checkedTargets.add(block))
					continue;
				if(raytraceCount++ >= raytraceBudget)
					return null;

				BlockData data = getRotation(block, eyePos);
				if(data != null)
					return data;
			}

			if(candidate.depth() >= maxDepth)
				continue;

			for(Direction direction : PLACEMENT_DIRECTIONS)
			{
				BlockPos next = candidate.pos().relative(direction);
				if(visited.size() >= visitBudget
					|| next.distSqr(origin) > MAX_PLACEMENT_DISTANCE_SQ
					|| !telly && next.getY() > origin.getY()
					|| !visited.add(next)
					|| !ScaffoldUtils.isReplaceable(next))
					continue;
				queue.add(new PlacementCandidate(next,
					candidate.depth() + 1));
			}
		}
		return null;
	}

	private List<BlockWithDirection> getSortedSupports(BlockPos targetPos,
		BlockPos origin)
	{
		List<BlockWithDirection> supports = new ArrayList<>();
		for(Direction direction : PLACEMENT_DIRECTIONS)
		{
			BlockPos support = targetPos.relative(direction);
			if(isValidSupport(support))
				supports.add(new BlockWithDirection(support,
					direction.getOpposite()));
		}
		supports.sort(Comparator
			.comparingDouble((BlockWithDirection data) -> data.pos()
				.relative(data.face()).distSqr(origin))
			.thenComparingInt(data -> directionPriority(data.face())));
		return supports;
	}

	private int directionPriority(Direction direction)
	{
		return switch(direction)
		{
			case UP -> 0;
			case WEST, EAST, NORTH, SOUTH -> 1;
			case DOWN -> 2;
		};
	}

	private BlockData getDirectBlockData(BlockPos target, Vec3 eyePos)
	{
		if(!ScaffoldUtils.isReplaceable(target))
			return null;

		int raytraceCount = 0;
		Rotation sortingAngle = getSortingAngle();
		for(BlockWithDirection block : getSortedSupports(target, target))
		{
			if(raytraceCount++ >= TELLY_RAYTRACE_BUDGET)
				break;
			BlockData data = getFastRotation(block, sortingAngle, eyePos);
			if(data != null)
				return data;
		}
		return null;
	}

	// --------------------------------------------------------- raycast rotation
	private BlockData getRotation(BlockWithDirection block, Vec3 eyePos)
	{
		Rotation sortingAngle = getSortingAngle();
		BlockData fast = getFastRotation(block, sortingAngle, eyePos);
		return fast != null ? fast
			: getRotationFromRaycastedBlock(block, sortingAngle, eyePos);
	}

	private Rotation getSortingAngle()
	{
		if(intelligentRotation != null)
			return intelligentRotation;
		return rotation != null ? rotation : ScaffoldUtils.getRotation();
	}

	private BlockData getFastRotation(BlockWithDirection block,
		Rotation sortingAngle, Vec3 start)
	{
		BlockData best = null;
		double bestDifference = Double.MAX_VALUE;

		for(double firstOffset : HIT_OFFSETS)
			for(double secondOffset : HIT_OFFSETS)
			{
				Vec3 hitVec = getFaceHitVec(block, firstOffset,
					secondOffset);
				Rotation trial = ScaffoldUtils.getVanillaRotation(
					ScaffoldUtils.getRotationFromPosition(start, hitVec));
				BlockHitResult result = ScaffoldUtils.raycastBlock(start,
					trial, range.getValue());
				if(result == null || !result.getBlockPos()
					.equals(block.pos())
					|| result.getDirection() != block.face()
					|| !isValidSupport(result.getBlockPos()))
					continue;

				double difference = ScaffoldUtils
					.getRotationDifference(trial, sortingAngle);
				if(difference < bestDifference)
				{
					bestDifference = difference;
					best = new BlockData(block, trial, result);
				}
			}
		return best;
	}

	private Vec3 getFaceHitVec(BlockWithDirection block,
		double firstOffset, double secondOffset)
	{
		Direction direction = block.face();
		double x = block.pos().getX() + 0.5D + direction.getStepX() * 0.5D;
		double y = block.pos().getY() + 0.5D + direction.getStepY() * 0.5D;
		double z = block.pos().getZ() + 0.5D + direction.getStepZ() * 0.5D;

		switch(direction.getAxis())
		{
			case X:
			y += firstOffset;
			z += secondOffset;
			break;
			case Y:
			x += firstOffset;
			z += secondOffset;
			break;
			case Z:
			x += firstOffset;
			y += secondOffset;
			break;
		}
		return new Vec3(x, y, z);
	}

	private BlockData getRotationFromRaycastedBlock(
		BlockWithDirection block, Rotation priority, Vec3 start)
	{
		AABB box = new AABB(block.pos());
		Vec3 center = box.getCenter();
		double widthX = box.getXsize();
		double height = box.getYsize();
		double widthZ = box.getZsize();

		List<BlockData> rotations = new ArrayList<>();
		// Matches OpenOpal's RotationUtility.getRotationFromRaycastedBlock.
		float step = 12.0F;
		for(double x = -widthX; x < widthX; x += widthX / step)
			for(double y = -height; y < height; y += height / step)
				for(double z = -widthZ; z < widthZ; z += widthZ / step)
				{
					Vec3 point = center.add(x, y, z);
					Rotation trial = ScaffoldUtils.getVanillaRotation(
						ScaffoldUtils.getRotationFromPosition(start, point));
					BlockHitResult result = ScaffoldUtils
						.raycastBlock(start, trial, range.getValue());
					if(result == null
						|| !result.getBlockPos().equals(block.pos())
						|| result.getDirection() != block.face())
						continue;
					rotations.add(new BlockData(block, trial, result));
				}

		if(rotations.isEmpty())
			return null;
		rotations.sort(Comparator.comparingDouble(data -> ScaffoldUtils
			.getRotationDifference(data.rotation(), priority)));
		return rotations.get(0);
	}

	/**
	 * OpenOpal's exhaustive face raytrace fallback, exposed for the Heypixel
	 * mode. Returns the rotation closest to {@code priority} that actually hits
	 * the requested block face.
	 */
	public Rotation getRotationFromRaycastedBlock(BlockPos pos, Direction face,
		Rotation priority, Vec3 start)
	{
		BlockData data = getRotationFromRaycastedBlock(
			new BlockWithDirection(pos, face), priority, start);
		return data == null ? null : data.rotation();
	}

	// ------------------------------------------------------------ source facts
	private void updateGroundTicks()
	{
		if(MC.player.onGround())
		{
			groundTicks++;
			airTicks = 0;
		}else
		{
			airTicks++;
			groundTicks = 0;
		}
	}

	private void updateMovementIntelligence()
	{
		if(!movementIntelligence.isChecked())
			return;
		boolean allowed = getMode() != Mode.WATCHDOG || MC.player.onGround()
			|| !ScaffoldUtils.isBoxEmpty(MC.player, MC.player
				.getBoundingBox().move(0.0D,
					MC.player.getDeltaMovement().y, 0.0D));
		if(!allowed)
			return;

		Rotation current = rotation != null ? rotation
			: new Rotation(MC.player.getYRot(), MC.player.getXRot());
		intelligentRotation = ScaffoldUtils.getPriorityAngle(current,
			movementSteps.getValueI(), movementSnapping.isChecked(),
			diagonalMovement.isChecked());
	}

	private void updateAutoJumpTicks()
	{
		if(lastAutoJumpStateAge == MC.player.tickCount)
			return;
		lastAutoJumpStateAge = MC.player.tickCount;
		if(!isSameYAutoJumpActive())
		{
			autoJumpTicks = 0;
			autoJumpGroundTicks = 0;
			autoJumpAirTicks = 0;
			return;
		}
		autoJumpTicks++;
		if(MC.player.onGround())
		{
			autoJumpGroundTicks++;
			autoJumpAirTicks = 0;
		}else
		{
			autoJumpAirTicks++;
			autoJumpGroundTicks = 0;
		}
	}

	private boolean isSameYAutoJumpActive()
	{
		return sameY.isChecked() && autoJump.isChecked()
			&& (ScaffoldUtils.isMoving() || isRecentAutoJump());
	}

	private boolean isRecentAutoJump()
	{
		return MC.player != null
			&& MC.player.tickCount - lastAutoJumpAge <= 4;
	}

	private boolean shouldDelayAutoJumpPlacement()
	{
		return isSameYAutoJumpActive() && MC.player != null
			&& MC.player.onGround() && autoJumpGroundTicks <= 2
			&& ScaffoldUtils.isMoving();
	}

	private Rotation getAutoJumpRotation(Rotation blockRotation)
	{
		int ground = Math.max(autoJumpGroundTicks, groundTicks);
		int air = Math.max(autoJumpAirTicks, airTicks);
		if(MC.player.onGround() && ground <= 2)
			return new Rotation(ScaffoldUtils
				.moveDirectionDegrees(MC.player.getYRot()), 75.5F);
		if(!MC.player.onGround() && air == 1)
		{
			float yawDelta = Mth.wrapDegrees(
				blockRotation.yaw() - MC.player.getYRot());
			return new Rotation(
				MC.player.getYRot() + Mth.clamp(yawDelta, -90.0F, 90.0F),
				blockRotation.pitch());
		}
		return blockRotation;
	}

	// --------------------------------------------------------------- rotation
	public void applyModeRotation(Rotation target,
		ScaffoldRotationModel model, float speed)
	{
		if(target == null)
			return;
		rotationHandler.rotate(target, model, speed);
		requestedRotation = rotationHandler.getRotation();
		lastRotation = requestedRotation;
	}

	public void requestRotation(Rotation target, boolean instant)
	{
		if(target == null)
			return;

		if(instant
			|| rotationModel.getSelected() == ScaffoldRotationModel.INSTANT)
			requestedRotation = target;
		else
		{
			Rotation from = new Rotation(
				WURST.getRotationFaker().getServerYaw(),
				WURST.getRotationFaker().getServerPitch());
			requestedRotation = rotationModel.getSelected().tick(from,
				target, maxAngle.getValueF());
		}
		lastRotation = requestedRotation;
	}

	public void requestRotation(Rotation target)
	{
		requestRotation(target, snapRotations.isChecked());
	}

	public void setPacketRotation(Rotation target, int ticks)
	{
		packetRotation = target;
		packetRotationTicks = Math.max(1, ticks);
	}

	public void setRotation(Rotation target)
	{
		rotation = target;
	}

	public void setBlockCache(BlockData data)
	{
		blockCache = data;
	}

	public BlockData getBlockCache()
	{
		return blockCache;
	}

	public Rotation getRotation()
	{
		return rotation;
	}

	public Rotation getLastRotation()
	{
		return lastRotation;
	}

	public int getGroundTicks()
	{
		return groundTicks;
	}

	public int getAirTicks()
	{
		return airTicks;
	}

	public void requestJump()
	{
		if(MC.player == null || MC.options.keyJump.isDown())
			return;
		MC.options.keyJump.setDown(true);
		simulatedJump = true;
		lastAutoJumpAge = MC.player.tickCount;
	}

	private void releaseSimulatedJump()
	{
		if(simulatedJump)
		{
			MC.options.keyJump.setDown(false);
			simulatedJump = false;
		}
	}

	public void requestSneak(boolean sneak)
	{
		if(sneak)
		{
			if(!MC.options.keyShift.isDown())
			{
				MC.options.keyShift.setDown(true);
				simulatedSneak = true;
			}
		}else if(simulatedSneak)
		{
			MC.options.keyShift.setDown(false);
			simulatedSneak = false;
		}
	}

	private void releaseSimulatedInput()
	{
		releaseSimulatedJump();
		requestSneak(false);
	}

	// ------------------------------------------------------ recent placement
	public void pruneRecentPlacedBlocks()
	{
		if(MC.player == null || MC.level == null
			|| recentPlacedBlocks.isEmpty())
			return;
		recentPlacedBlocks.entrySet().removeIf(entry ->
			MC.player.tickCount - entry.getValue() > RECENT_PLACED_BLOCK_TTL
				|| MC.level.getBlockState(entry.getKey()).canBeReplaced());
	}

	public boolean isRecentlyPlacedTellySupport(BlockPos pos)
	{
		if(!isTellyMode())
			return false;
		Integer age = recentPlacedBlocks.get(pos);
		return age != null
			&& MC.player.tickCount - age <= RECENT_SUPPORT_QUARANTINE_TICKS;
	}

	public void trackPlacedBlock(BlockPos pos)
	{
		if(pos == null)
			return;
		lastPlacedBlocks.remove(pos);
		lastPlacedBlocks.addLast(pos.immutable());
		while(lastPlacedBlocks.size() > MAX_LAST_PLACED_BLOCKS)
			lastPlacedBlocks.removeFirst();
		invalidateSearchCache();
	}

	private void invalidateSearchCache()
	{
		lastSearchOrigin = null;
		lastSearchData = null;
		lastSearchAge = -1;
	}

	public List<BlockPos> getLastPlacedBlocks()
	{
		return new ArrayList<>(lastPlacedBlocks);
	}

	// ------------------------------------------------------------- predicates
	public boolean isValidSupportBlock(BlockHitResult hitResult)
	{
		return hitResult != null && isValidSupport(hitResult.getBlockPos());
	}

	private boolean isValidSupport(BlockPos pos)
	{
		return ScaffoldUtils.isValidSupportBlock(pos)
			&& !isRecentlyPlacedTellySupport(pos);
	}

	public boolean isTellyMode()
	{
		return getEffectiveMode() == Mode.TELLY;
	}

	public boolean isTellySetting()
	{
		return mode.getSelected() == Mode.TELLY;
	}

	public boolean isUitemsSetting()
	{
		return mode.getSelected() == Mode.HEYPIXEL
			|| mode.getSelected() == Mode.HYPIXEL;
	}

	public boolean isSilenceTellySetting()
	{
		return mode.getSelected() == Mode.SILENCE_TELLY;
	}

	public boolean isSilenceTellyTellySetting()
	{
		return isSilenceTellySetting()
			&& silenceTellyMode.getSelected() == SilenceTellyMode.TELLY;
	}

	public Mode getMode()
	{
		return mode.getSelected();
	}

	public Mode getEffectiveMode()
	{
		if(activeMode() instanceof HeypixelScaffoldMode heypixel
			&& heypixel.isUpTellyBypassActive())
			return Mode.VANILLA;
		return mode.getSelected();
	}

	public ScaffoldMode activeMode()
	{
		return modes.get(mode.getSelected());
	}

	public double getRange()
	{
		return range.getValue();
	}

	// ------------------------------------------------------------- data types
	public record BlockWithDirection(BlockPos pos, Direction face)
	{}

	public record BlockData(BlockWithDirection block, Rotation rotation,
		BlockHitResult hit)
	{
		public BlockPos placementPos()
		{
			return block.pos().relative(block.face());
		}
	}

	public record PlacementHand(InteractionHand hand, BlockItem blockItem)
	{}

	public record RotationRequest(Rotation rotation, boolean smooth)
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

		Mode(String name)
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

		SwitchMode(String name)
		{
			this.name = name;
		}

		@Override
		public String toString()
		{
			return name;
		}
	}

	public enum SilenceTellyMode
	{
		TELLY("Telly"),
		SNAP("Snap"),
		NORMAL("Normal");

		private final String name;

		SilenceTellyMode(String name)
		{
			this.name = name;
		}

		@Override
		public String toString()
		{
			return name;
		}
	}

	public enum SilenceBlockSlotMode
	{
		FARTHEST("Farthest"),
		MOST_BLOCKS("Most Blocks");

		private final String name;

		SilenceBlockSlotMode(String name)
		{
			this.name = name;
		}

		@Override
		public String toString()
		{
			return name;
		}
	}

	public enum SilenceJumpMode
	{
		PARKOUR("Parkour"),
		NORMAL("Normal"),
		NONE("None");

		private final String name;

		SilenceJumpMode(String name)
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
