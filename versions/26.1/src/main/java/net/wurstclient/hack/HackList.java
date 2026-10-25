/*
 * Copyright (c) 2014-2025 Wurst-Imperium and contributors.
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 */
package net.wurstclient.hack;

import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.TreeMap;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import net.minecraft.CrashReport;
import net.minecraft.ReportedException;
import net.wurstclient.WurstClient;
import net.wurstclient.event.EventManager;
import net.wurstclient.events.UpdateListener;
import net.wurstclient.hacks.*;
import net.wurstclient.util.json.JsonException;

public final class HackList implements UpdateListener
{
	public final AimAssistHack aimAssistHack = new AimAssistHack();
	public final AirPlaceHack airPlaceHack = new AirPlaceHack();
	public final AnchorAuraHack anchorAuraHack = new AnchorAuraHack();
	public final AnchorHack anchorHack = new AnchorHack();
	public final AntiAfkHack antiAfkHack = new AntiAfkHack();
	public final AntiAimHack antiAimHack = new AntiAimHack();
	public final AntiBlindHack antiBlindHack = new AntiBlindHack();
	public final AntiCactusHack antiCactusHack = new AntiCactusHack();
	public final AntiEntityPushHack antiEntityPushHack =
		new AntiEntityPushHack();
	public final AntiHungerHack antiHungerHack = new AntiHungerHack();
	public final AntiKnockbackHack antiKnockbackHack = new AntiKnockbackHack();
	public final AntiSpamHack antiSpamHack = new AntiSpamHack();
	public final AntiWaterPushHack antiWaterPushHack = new AntiWaterPushHack();
	public final AntiWobbleHack antiWobbleHack = new AntiWobbleHack();
	public final AntiVoidHack antiVoidHack = new AntiVoidHack();
	public final ArrowDmgHack arrowDmgHack = new ArrowDmgHack();
	public final AttributeSwapHack attributeSwapHack = new AttributeSwapHack();
	public final AutoArmorHack autoArmorHack = new AutoArmorHack();
	public final AutoBuildHack autoBuildHack = new AutoBuildHack();
	public final AutoCityHack autoCityHack = new AutoCityHack();
	public final AutoCompleteHack autoCompleteHack = new AutoCompleteHack();
	public final AutoDropHack autoDropHack = new AutoDropHack();
	public final AutoLeaveHack autoLeaveHack = new AutoLeaveHack();
	public final AutoLibrarianHack autoLibrarianHack = new AutoLibrarianHack();
	public final AutoEatHack autoEatHack = new AutoEatHack();
	public final EXPThrowerHack expThrowerHack = new EXPThrowerHack();
	public final AutoEXPHack autoExpHack = new AutoEXPHack();
	public final AutoFarmHack autoFarmHack = new AutoFarmHack();
	public final AutoFishHack autoFishHack = new AutoFishHack();
	public final AutoMineHack autoMineHack = new AutoMineHack();
	public final AutoPotionHack autoPotionHack = new AutoPotionHack();
	public final AutoReconnectHack autoReconnectHack = new AutoReconnectHack();
	public final AutoRespawnHack autoRespawnHack = new AutoRespawnHack();
	public final AutoSignHack autoSignHack = new AutoSignHack();
	public final AutoSoupHack autoSoupHack = new AutoSoupHack();
	public final AutoSprintHack autoSprintHack = new AutoSprintHack();
	public final AutoStealHack autoStealHack = new AutoStealHack();
	public final AutoSwimHack autoSwimHack = new AutoSwimHack();
	public final AutoSwitchHack autoSwitchHack = new AutoSwitchHack();
	public final AutoSwordHack autoSwordHack = new AutoSwordHack();
	public final AutoToolHack autoToolHack = new AutoToolHack();
	public final AutoTotemHack autoTotemHack = new AutoTotemHack();
	public final AutoTrapHack autoTrapHack = new AutoTrapHack();
	public final AutoWalkHack autoWalkHack = new AutoWalkHack();
	public final AutoWebHack autoWebHack = new AutoWebHack();
	public final BarrierEspHack barrierEspHack = new BarrierEspHack();
	public final BaritoneClearAreaHack baritoneClearAreaHack =
		new BaritoneClearAreaHack();
	public final BaritoneMineHack baritoneMineHack = new BaritoneMineHack();
	public final BaritoneTreeBotHack baritoneTreeBotHack =
		new BaritoneTreeBotHack();
	public final BaritoneWalkHack baritoneWalkHack = new BaritoneWalkHack();
	public final BaseFinderHack baseFinderHack = new BaseFinderHack();
	public final BlinkHack blinkHack = new BlinkHack();
	public final BoatFlyHack boatFlyHack = new BoatFlyHack();
	public final BonemealAuraHack bonemealAuraHack = new BonemealAuraHack();
	public final BossStackHack bossStackHack = new BossStackHack();
	public final BowAimbotHack bowAimbotHack = new BowAimbotHack();
	public final BowSpamHack bowSpamHack = new BowSpamHack();
	public final BuildRandomHack buildRandomHack = new BuildRandomHack();
	public final BunnyHopHack bunnyHopHack = new BunnyHopHack();
	public final BurrowHack burrowHack = new BurrowHack();
	public final CameraDistanceHack cameraDistanceHack =
		new CameraDistanceHack();
	public final CameraNoClipHack cameraNoClipHack = new CameraNoClipHack();
	public final CaveFinderHack caveFinderHack = new CaveFinderHack();
	public final ChatTranslatorHack chatTranslatorHack =
		new ChatTranslatorHack();
	public final ChestEspHack chestEspHack = new ChestEspHack();
	public final CityEspHack cityEspHack = new CityEspHack();
	public final ClickAuraHack clickAuraHack = new ClickAuraHack();
	public final ClickGuiHack clickGuiHack = new ClickGuiHack();
	public final CrashChestHack crashChestHack = new CrashChestHack();
	public final CreativeFlightHack creativeFlightHack =
		new CreativeFlightHack();
	public final CriticalsHack criticalsHack = new CriticalsHack();
	public final CrystalAuraHack crystalAuraHack = new CrystalAuraHack();
	public final DelayRemoverHack delayRemoverHack =
		new DelayRemoverHack();
	public final DerpHack derpHack = new DerpHack();
	public final DolphinHack dolphinHack = new DolphinHack();
	public final ExcavatorHack excavatorHack = new ExcavatorHack();
	public final ElytraFlyHack elytraFlyHack = new ElytraFlyHack();
	public final EntityCullingHack entityCullingHack = new EntityCullingHack();
	public final ExtraElytraHack extraElytraHack = new ExtraElytraHack();
	public final FancyChatHack fancyChatHack = new FancyChatHack();
	public final FakeLagHack fakeLagHack = new FakeLagHack();
	public final FastBreakHack fastBreakHack = new FastBreakHack();
	public final FastLadderHack fastLadderHack = new FastLadderHack();
	public final FastPlaceHack fastPlaceHack = new FastPlaceHack();
	public final FastUseHack fastUseHack = new FastUseHack();
	public final FeedAuraHack feedAuraHack = new FeedAuraHack();
	public final FightBotHack fightBotHack = new FightBotHack();
	public final FishHack fishHack = new FishHack();
	public final FlightHack flightHack = new FlightHack();
	public final FollowHack followHack = new FollowHack();
	public final ForceOpHack forceOpHack = new ForceOpHack();
	public final FreecamHack freecamHack = new FreecamHack();
	public final FullbrightHack fullbrightHack = new FullbrightHack();
	public final GlideHack glideHack = new GlideHack();
	public final HandNoClipHack handNoClipHack = new HandNoClipHack();
	public final HeadRollHack headRollHack = new HeadRollHack();
	public final HealthTagsHack healthTagsHack = new HealthTagsHack();
	public final HighJumpHack highJumpHack = new HighJumpHack();
	public final HitboxesHack hitboxesHack = new HitboxesHack();
	public final HoleEspHack holeEspHack = new HoleEspHack();
	public final HoleFillerHack holeFillerHack = new HoleFillerHack();
	public final InfiniChatHack infiniChatHack = new InfiniChatHack();
	public final InstaBuildHack instaBuildHack = new InstaBuildHack();
	public final InstantBunkerHack instantBunkerHack = new InstantBunkerHack();
	public final InvWalkHack invWalkHack = new InvWalkHack();
	public final ItemEspHack itemEspHack = new ItemEspHack();
	public final ItemGeneratorHack itemGeneratorHack = new ItemGeneratorHack();
	public final JesusHack jesusHack = new JesusHack();
	public final JetpackHack jetpackHack = new JetpackHack();
	public final KaboomHack kaboomHack = new KaboomHack();
	public final KeepSprintHack keepSprintHack = new KeepSprintHack();
	public final KillauraLegitHack killauraLegitHack = new KillauraLegitHack();
	public final MaceDmgHack maceDmgHack = new MaceDmgHack();
	public final KillauraHack killauraHack = new KillauraHack();
	public final KillPotionHack killPotionHack = new KillPotionHack();
	public final LiquidsHack liquidsHack = new LiquidsHack();
	public final LsdHack lsdHack = new LsdHack();
	public final MassTpaHack massTpaHack = new MassTpaHack();
	public final MileyCyrusHack mileyCyrusHack = new MileyCyrusHack();
	public final MobEspHack mobEspHack = new MobEspHack();
	public final MobSpawnEspHack mobSpawnEspHack = new MobSpawnEspHack();
	public final MultiAuraHack multiAuraHack = new MultiAuraHack();
	public final NameProtectHack nameProtectHack = new NameProtectHack();
	public final NameTagsHack nameTagsHack = new NameTagsHack();
	public final NavigatorHack navigatorHack = new NavigatorHack();
	public final NewChunksHack newChunksHack = new NewChunksHack();
	public final NoBackgroundHack noBackgroundHack = new NoBackgroundHack();
	public final NoClipHack noClipHack = new NoClipHack();
	public final NoFallHack noFallHack = new NoFallHack();
	public final NoFireOverlayHack noFireOverlayHack = new NoFireOverlayHack();
	public final NoFogHack noFogHack = new NoFogHack();
	public final NoHurtcamHack noHurtcamHack = new NoHurtcamHack();
	public final NoJumpDelayHack noJumpDelayHack = new NoJumpDelayHack();
	public final NoLevitationHack noLevitationHack = new NoLevitationHack();
	public final NoOverlayHack noOverlayHack = new NoOverlayHack();
	public final NoPumpkinHack noPumpkinHack = new NoPumpkinHack();
	public final NoShieldOverlayHack noShieldOverlayHack =
		new NoShieldOverlayHack();
	public final NoSlowdownHack noSlowdownHack = new NoSlowdownHack();
	public final NoVignetteHack noVignetteHack = new NoVignetteHack();
	public final NoVelocityHack noVelocityHack = new NoVelocityHack();
	public final NoWeatherHack noWeatherHack = new NoWeatherHack();
	public final NoWebHack noWebHack = new NoWebHack();
	public final NukerHack nukerHack = new NukerHack();
	public final NukerLegitHack nukerLegitHack = new NukerLegitHack();
	public final OpenWaterEspHack openWaterEspHack = new OpenWaterEspHack();
	public final OverlayHack overlayHack = new OverlayHack();
	public final PacketFlyHack packetFlyHack = new PacketFlyHack();
	public final PanicHack panicHack = new PanicHack();
	public final ParkourHack parkourHack = new ParkourHack();
	public final PlayerEspHack playerEspHack = new PlayerEspHack();
	public final PlayerHaloHack playerHaloHack = new PlayerHaloHack();
	public final PortalEspHack portalEspHack = new PortalEspHack();
	public final PortalGuiHack portalGuiHack = new PortalGuiHack();
	public final PotionSaverHack potionSaverHack = new PotionSaverHack();
	public final ProphuntEspHack prophuntEspHack = new ProphuntEspHack();
	public final ProtectHack protectHack = new ProtectHack();
	public final RadarHack radarHack = new RadarHack();
	public final RainbowUiHack rainbowUiHack = new RainbowUiHack();
	public final ReachHack reachHack = new ReachHack();
	public final RemoteViewHack remoteViewHack = new RemoteViewHack();
	public final RestockHack restockHack = new RestockHack();
	public final RotationSnapHack rotationSnapHack = new RotationSnapHack();
	public final SafeWalkHack safeWalkHack = new SafeWalkHack();
	public final ScaffoldWalkHack scaffoldWalkHack = new ScaffoldWalkHack();
	public final SearchHack searchHack = new SearchHack();
	public final SelfTrapHack selfTrapHack = new SelfTrapHack();
	public final SkinDerpHack skinDerpHack = new SkinDerpHack();
	public final SneakHack sneakHack = new SneakHack();
	public final SnowShoeHack snowShoeHack = new SnowShoeHack();
	public final SpeedHackHack speedHackHack = new SpeedHackHack();
	public final SpeedMineHack speedMineHack = new SpeedMineHack();
	public final SpeedNukerHack speedNukerHack = new SpeedNukerHack();
	public final SpiderHack spiderHack = new SpiderHack();
	public final StepHack stepHack = new StepHack();
	public final SurroundHack surroundHack = new SurroundHack();
	public final TemplateToolHack templateToolHack = new TemplateToolHack();
	public final ThrowHack throwHack = new ThrowHack();
	public final TillauraHack tillauraHack = new TillauraHack();
	public final TimerHack timerHack = new TimerHack();
	public final TiredHack tiredHack = new TiredHack();
	public final TooManyHaxHack tooManyHaxHack = new TooManyHaxHack();
	public final TargetStrafeHack targetStrafeHack = new TargetStrafeHack();
	public final TargetShaderHack targetShaderHack = new TargetShaderHack();
	public final TpAuraHack tpAuraHack = new TpAuraHack();
	public final TrajectoriesHack trajectoriesHack = new TrajectoriesHack();
	public final TreeBotHack treeBotHack = new TreeBotHack();
	public final TriggerBotHack triggerBotHack = new TriggerBotHack();
	public final TrollPotionHack trollPotionHack = new TrollPotionHack();
	public final TrueSightHack trueSightHack = new TrueSightHack();
	public final TunnellerHack tunnellerHack = new TunnellerHack();
	public final VeinMinerHack veinMinerHack = new VeinMinerHack();
	public final XRayHack xRayHack = new XRayHack();

	public final AntiBotHack antiBotHack = new AntiBotHack();
	public final BreadcrumbsHack breadcrumbsHack = new BreadcrumbsHack();
	public final DankBobbingHack dankBobbingHack = new DankBobbingHack();
	public final LightOverlayHack lightOverlayHack = new LightOverlayHack();
	public final LogoutSpotsHack logoutSpotsHack = new LogoutSpotsHack();
	public final NotebotHack notebotHack = new NotebotHack();
	public final PopChamsHack popChamsHack = new PopChamsHack();
	public final PacketCancellerHack packetCancellerHack = new PacketCancellerHack();
	public final PacketLoggerHack packetLoggerHack = new PacketLoggerHack();
	public final TwerkHack twerkHack = new TwerkHack();
	public final VomitHack vomitHack = new VomitHack();

	public final AntiAnchorHack antiAnchorHack = new AntiAnchorHack();
	public final AntiAnvilHack antiAnvilHack = new AntiAnvilHack();
	public final AntiBedHack antiBedHack = new AntiBedHack();
	public final SelfWebHack selfWebHack = new SelfWebHack();
	public final SelfAnvilHack selfAnvilHack = new SelfAnvilHack();
	public final AutoWeaponHack autoWeaponHack = new AutoWeaponHack();
	public final QuiverHack quiverHack = new QuiverHack();
	public final FastClimbHack fastClimbHack = new FastClimbHack();
	public final ClickTPHack clickTpHack = new ClickTPHack();
	public final LongJumpHack longJumpHack = new LongJumpHack();
	public final MiddleClickExtraHack middleClickExtraHack =
		new MiddleClickExtraHack();
	public final AutoMendHack autoMendHack = new AutoMendHack();
	public final AutoReplenishHack autoReplenishHack = new AutoReplenishHack();
	public final ChestSwapHack chestSwapHack = new ChestSwapHack();
	public final AutoGapHack autoGapHack = new AutoGapHack();
	public final TracersHack tracersHack = new TracersHack();
	public final BreakIndicatorsHack breakIndicatorsHack =
		new BreakIndicatorsHack();
	public final VoidESPHack voidEspHack = new VoidESPHack();
	public final TunnelESPHack tunnelEspHack = new TunnelESPHack();
	public final TimeChangerHack timeChangerHack = new TimeChangerHack();
	public final WeatherChangerHack weatherChangerHack =
		new WeatherChangerHack();

	public final ArrowDodgeHack arrowDodgeHack = new ArrowDodgeHack();
	public final AutoAnvilHack autoAnvilHack = new AutoAnvilHack();
	public final BedAuraHack bedAuraHack = new BedAuraHack();
	public final OffhandHack offhandHack = new OffhandHack();
	public final AirJumpHack airJumpHack = new AirJumpHack();
	public final AutoJumpHack autoJumpHack = new AutoJumpHack();
	public final ElytraBoostHack elytraBoostHack = new ElytraBoostHack();
	public final ReverseStepHack reverseStepHack = new ReverseStepHack();
	public final AutoClickerHack autoClickerHack = new AutoClickerHack();
	public final AutoMountHack autoMountHack = new AutoMountHack();
	public final BetterChatHack betterChatHack = new BetterChatHack();
	public final RotationHack rotationHack = new RotationHack();
	public final SpamHack spamHack = new SpamHack();
	public final BlockEspHack blockEspHack = new BlockEspHack();
	public final BlockSelectionHack blockSelectionHack =
		new BlockSelectionHack();
	public final MarkerHack markerHack = new MarkerHack();
	public final TrailHack trailHack = new TrailHack();
	public final AutoBreedHack autoBreedHack = new AutoBreedHack();
	public final AutoSmelterHack autoSmelterHack = new AutoSmelterHack();
	public final NotifierHack notifierHack = new NotifierHack();
	public final WeatherDisplayHack weatherDisplayHack = new WeatherDisplayHack();
	public final ServerIpDisplayHack serverIpDisplayHack = new ServerIpDisplayHack();
	public final ScoreboardHack scoreboardHack = new ScoreboardHack();
	public final PotionCounterHack potionCounterHack = new PotionCounterHack();
	public final PlayTimeDisplayHack playTimeDisplayHack = new PlayTimeDisplayHack();
	public final NameDisplayHack nameDisplayHack = new NameDisplayHack();
	public final MusicInfoHack musicInfoHack = new MusicInfoHack();
	public final MemoryUsageHack memoryUsageHack = new MemoryUsageHack();
	public final ItemInfoHack itemInfoHack = new ItemInfoHack();
	public final InventoryDisplayHack inventoryDisplayHack = new InventoryDisplayHack();
	public final ImageDisplayHack imageDisplayHack = new ImageDisplayHack();
	public final GameModeDisplayHack gameModeDisplayHack = new GameModeDisplayHack();
	public final DayCounterHack dayCounterHack = new DayCounterHack();
	public final CompassHack compassHack = new CompassHack();
	public final CalendarHack calendarHack = new CalendarHack();
	public final CrosshairHack crosshairHack = new CrosshairHack();
	public final ReachCirclesHack reachCirclesHack = new ReachCirclesHack();
	public final ProjectileTrailHack projectileTrailHack = new ProjectileTrailHack();
	public final JumpCircleHack jumpCircleHack = new JumpCircleHack();
	public final ClickEffectHack clickEffectHack = new ClickEffectHack();
	public final ShinyPotsHack shinyPotsHack = new ShinyPotsHack();
	public final KillSoundsHack killSoundsHack = new KillSoundsHack();
	public final KillEffectsHack killEffectsHack = new KillEffectsHack();
	public final MechvibesHack mechvibesHack = new MechvibesHack();
	public final PackDisplayHack packDisplayHack = new PackDisplayHack();
	public final MinemenHack minemenHack = new MinemenHack();
	public final HypixelHack hypixelHack = new HypixelHack();
	public final ClientSpooferHack clientSpooferHack = new ClientSpooferHack();
	public final MinimalViewBobbingHack minimalViewBobbingHack = new MinimalViewBobbingHack();
	public final FpsBoostHack fpsBoostHack = new FpsBoostHack();
	public final UHCOverlayHack uhcOverlayHack = new UHCOverlayHack();
	public final BlockInfoHack blockInfoHack = new BlockInfoHack();
	public final SpeedometerHack speedometerHack = new SpeedometerHack();
	public final PlayerCounterHack playerCounterHack = new PlayerCounterHack();
	public final ReachDisplayHack reachDisplayHack = new ReachDisplayHack();
	public final MouseStrokesHack mouseStrokesHack = new MouseStrokesHack();
	public final CpsDisplayHack cpsDisplayHack = new CpsDisplayHack();
	public final SessionInfoHack sessionInfoHack = new SessionInfoHack();
	public final HorseStatsHack horseStatsHack = new HorseStatsHack();
	public final TntTimerHack tntTimerHack = new TntTimerHack();
	public final TargetInfoHack targetInfoHack = new TargetInfoHack();
	public final BossHealthHack bossHealthHack = new BossHealthHack();
	public final PotionStatusHack potionStatusHack = new PotionStatusHack();
	public final ArmorStatusHack armorStatusHack = new ArmorStatusHack();
	public final AppleSkinHack appleSkinHack = new AppleSkinHack();
	public final StopwatchHack stopwatchHack = new StopwatchHack();
	public final KeystrokesHack keystrokesHack = new KeystrokesHack();
	public final ComboCounterHack comboCounterHack = new ComboCounterHack();
	public final HypixelQuickPlayHack hypixelQuickPlayHack = new HypixelQuickPlayHack();
	public final AutoTextHack autoTextHack = new AutoTextHack();
	public final FpsLimiterHack fpsLimiterHack = new FpsLimiterHack();
	public final BorderlessFullscreenHack borderlessFullscreenHack = new BorderlessFullscreenHack();
	public final AsyncScreenshotHack asyncScreenshotHack = new AsyncScreenshotHack();
	public final FovModifierHack fovModifierHack = new FovModifierHack();
	public final BowZoomHack bowZoomHack = new BowZoomHack();
	public final TaplookHack taplookHack = new TaplookHack();
	public final ToggleSprintHack toggleSprintHack = new ToggleSprintHack();
	public final ToggleSneakHack toggleSneakHack = new ToggleSneakHack();
	public final QuickSwitchHack quickSwitchHack = new QuickSwitchHack();
	public final GodbridgeAssistHack godbridgeAssistHack = new GodbridgeAssistHack();
	public final AncientCitySearchHack ancientCitySearchHack = new AncientCitySearchHack();
	public final SpearExploitHack spearExploitHack = new SpearExploitHack();
	public final OdmGearHack odmGearHack = new OdmGearHack();
	public final PitcherHack pitcherHack = new PitcherHack();
	public final FlightAntiKickHack flightAntiKickHack = new FlightAntiKickHack();
	public final ScreenActionsHack screenActionsHack = new ScreenActionsHack();
	public final SonarBypassHack sonarBypassHack = new SonarBypassHack();
	public final SprintStatusHack sprintStatusHack = new SprintStatusHack();
	public final KnockbackDirectionHack knockbackDirectionHack = new KnockbackDirectionHack();
	public final KillFxHack killFxHack = new KillFxHack();
	public final ImgWorldStatsHack imgWorldStatsHack = new ImgWorldStatsHack();
	public final ImgTotemParticleHack imgTotemParticleHack = new ImgTotemParticleHack();
	public final ImgTipsHack imgTipsHack = new ImgTipsHack();
	public final MobHudHack mobHudHack = new MobHudHack();
	public final MineEspHack mineEspHack = new MineEspHack();
	public final ImgChamsHack imgChamsHack = new ImgChamsHack();
	public final FillEspHack fillEspHack = new FillEspHack();
	public final ElytraFollowerHack elytraFollowerHack = new ElytraFollowerHack();
	public final CustomItemEspHack customItemEspHack = new CustomItemEspHack();
	public final CustomFovHack customFovHack = new CustomFovHack();
	public final BreakEspHack breakEspHack = new BreakEspHack();
	public final AntiLagHack antiLagHack = new AntiLagHack();
	public final AntiAntiXrayHack antiAntiXrayHack = new AntiAntiXrayHack();
	public final AmbienceHack ambienceHack = new AmbienceHack();
	public final XCarryHack xCarryHack = new XCarryHack();
	public final AutoKouZiHack autoKouZiHack = new AutoKouZiHack();
	public final AutoNodHack autoNodHack = new AutoNodHack();
	public final AutoTPAcceptHack autoTPAcceptHack = new AutoTPAcceptHack();
	public final AutoServerHack autoServerHack = new AutoServerHack();
	public final HexChatHack hexChatHack = new HexChatHack();
	public final GAntiExplosionHack gAntiExplosionHack = new GAntiExplosionHack();
	public final FeedbackBlockerHack feedbackBlockerHack = new FeedbackBlockerHack();
	public final ChatPrefixCustomHack chatPrefixCustomHack = new ChatPrefixCustomHack();
	public final ChatHiderHack chatHiderHack = new ChatHiderHack();
	public final ChatFilterHack chatFilterHack = new ChatFilterHack();
	public final FoKillAuraHack foKillAuraHack = new FoKillAuraHack();
	public final AutoMineSandHack autoMineSandHack = new AutoMineSandHack();
	public final RTsearchHack rTsearchHack = new RTsearchHack();
	public final SchematicProHack schematicProHack = new SchematicProHack();
	public final FurnaceUncloggerHack furnaceUncloggerHack = new FurnaceUncloggerHack();
	public final AdaAutoHotbarHack adaAutoHotbarHack = new AdaAutoHotbarHack();
	public final AdaManualCrystalHack adaManualCrystalHack = new AdaManualCrystalHack();
	public final VillagerTraderHack villagerTraderHack = new VillagerTraderHack();
	public final AutoInvTotemHack autoInvTotemHack = new AutoInvTotemHack();
	public final AutoFireworkHack autoFireworkHack = new AutoFireworkHack();
	public final AutoDoubleHandHack autoDoubleHandHack = new AutoDoubleHandHack();
	public final AutoChorusHack autoChorusHack = new AutoChorusHack();
	public final FreezeHack freezeHack = new FreezeHack();
	public final LegitNoFallHack legitNoFallHack = new LegitNoFallHack();
	public final PortalGodModeHack portalGodModeHack = new PortalGodModeHack();
	public final StuckHack stuckHack = new StuckHack();
	public final BacktrackHack backtrackHack = new BacktrackHack();
	public final PearlPhaseHack pearlPhaseHack = new PearlPhaseHack();
	public final TpMachineGunHack tpMachineGunHack = new TpMachineGunHack();
	public final TpBowAuraHack tpBowAuraHack = new TpBowAuraHack();
	public final TpAnchorHack tpAnchorHack = new TpAnchorHack();
	public final TntBomberHack tntBomberHack = new TntBomberHack();
	public final SwordGapHack swordGapHack = new SwordGapHack();
	public final SpearKillHack spearKillHack = new SpearKillHack();
	public final PistonCrystalHack pistonCrystalHack = new PistonCrystalHack();
	public final MacroAnchorHack macroAnchorHack = new MacroAnchorHack();
	public final MaceBreakerProHack maceBreakerProHack = new MaceBreakerProHack();
	public final MaceAuraHack maceAuraHack = new MaceAuraHack();
	public final CrystalMacroHack crystalMacroHack = new CrystalMacroHack();
	public final CrossbowAuraHack crossbowAuraHack = new CrossbowAuraHack();
	public final AutoChestAuraHack autoChestAuraHack = new AutoChestAuraHack();
	public final AttractAuraHack attractAuraHack = new AttractAuraHack();
	public final FastCrossbowHack fastCrossbowHack = new FastCrossbowHack();
	public final AutoTorchHack autoTorchHack = new AutoTorchHack();
	public final ReplenishHack replenishHack = new ReplenishHack();
	public final PushCrystalHack pushCrystalHack = new PushCrystalHack();
	public final PrinterHack printerHack = new PrinterHack();
	public final PlaceRenderHack placeRenderHack = new PlaceRenderHack();
	public final CrystalAuraPlusHack crystalAuraPlusHack = new CrystalAuraPlusHack();
	public final ShieldEspHack shieldEspHack = new ShieldEspHack();
	public final ChatHighlightHack chatHighlightHack = new ChatHighlightHack();
	public final AutoVaultHack autoVaultHack = new AutoVaultHack();
	public final AutoSmithingHack autoSmithingHack = new AutoSmithingHack();
	public final AutoDeoxidizerHack autoDeoxidizerHack = new AutoDeoxidizerHack();
	public final GlassFillerHack glassFillerHack = new GlassFillerHack();
	public final FireworkElytraFlyHack fireworkElytraFlyHack = new FireworkElytraFlyHack();
	public final ElytraGrimAccelerateHack elytraGrimAccelerateHack = new ElytraGrimAccelerateHack();
	public final DeathRenderHack deathRenderHack = new DeathRenderHack();
	public final AutoRefreshTradeHack autoRefreshTradeHack = new AutoRefreshTradeHack();
	public final OreVeinESPHack oreVeinEspHack = new OreVeinESPHack();
	public final LavaESPHack lavaEspHack = new LavaESPHack();
	public final ItemDespawnTimerHack itemDespawnTimerHack = new ItemDespawnTimerHack();
	public final HitboxESPHack hitboxEspHack = new HitboxESPHack();
	public final EntityTagsHack entityTagsHack = new EntityTagsHack();
	public final AttackRangeIndicatorHack attackRangeIndicatorHack = new AttackRangeIndicatorHack();
	public final AutoPlaceBlockHack autoPlaceBlockHack = new AutoPlaceBlockHack();
	public final AutoOffHandHack autoOffHandHack = new AutoOffHandHack();
	public final AutoLoginHack autoLoginHack = new AutoLoginHack();
	public final AutoArmorPlusHack autoArmorPlusHack = new AutoArmorPlusHack();
	public final AutoAnchorHack autoAnchorHack = new AutoAnchorHack();
	public final ElytraCollectorHack elytraCollectorHack = new ElytraCollectorHack();
	public final AutoTrashHack autoTrashHack = new AutoTrashHack();
	public final MessageAuraHack messageAuraHack = new MessageAuraHack();
	public final InventoryTweaksHack inventoryTweaksHack = new InventoryTweaksHack();
	public final BookBotHack bookBotHack = new BookBotHack();
	public final BetterBeaconsHack betterBeaconsHack = new BetterBeaconsHack();
	public final NoRotateHack noRotateHack = new NoRotateHack();
	public final FakePlayerHack fakePlayerHack = new FakePlayerHack();
	public final EntityOwnerHack entityOwnerHack = new EntityOwnerHack();
	public final StashFinderHack stashFinderHack = new StashFinderHack();
	public final SpawnProoferHack spawnProoferHack = new SpawnProoferHack();
	public final PacketMineHack packetMineHack = new PacketMineHack();
	public final NoGhostBlocksHack noGhostBlocksHack = new NoGhostBlocksHack();
	public final InfinityMinerHack infinityMinerHack = new InfinityMinerHack();
	public final HighwayBuilderHack highwayBuilderHack = new HighwayBuilderHack();
	public final LiquidFillerHack liquidFillerHack = new LiquidFillerHack();
	public final FlamethrowerHack flamethrowerHack = new FlamethrowerHack();
	public final EChestFarmerHack eChestFarmerHack = new EChestFarmerHack();
	public final AutoBrewerHack autoBrewerHack = new AutoBrewerHack();
	public final AutoShearerHack autoShearerHack = new AutoShearerHack();
	public final AutoNametagHack autoNametagHack = new AutoNametagHack();

	private final TreeMap<String, Hack> hax =
		new TreeMap<>(String::compareToIgnoreCase);
	
	private final EnabledHacksFile enabledHacksFile;
	private final Path profilesFolder =
		WurstClient.INSTANCE.getWurstFolder().resolve("enabled hacks");
	
	private final EventManager eventManager =
		WurstClient.INSTANCE.getEventManager();
	
	public HackList(Path enabledHacksFile)
	{
		this.enabledHacksFile = new EnabledHacksFile(enabledHacksFile);
		
		try
		{
			for(Field field : HackList.class.getDeclaredFields())
			{
				if(!field.getName().endsWith("Hack"))
					continue;
				
				Hack hack = (Hack)field.get(this);
				hax.put(hack.getName(), hack);
			}
			
		}catch(Exception e)
		{
			String message = "Initializing Wurst hacks";
			CrashReport report = CrashReport.forThrowable(e, message);
			throw new ReportedException(report);
		}
		
		eventManager.add(UpdateListener.class, this);
	}
	
	@Override
	public void onUpdate()
	{
		enabledHacksFile.load(this);
		eventManager.remove(UpdateListener.class, this);
	}
	
	public void saveEnabledHax()
	{
		enabledHacksFile.save(this);
	}
	
	public Hack getHackByName(String name)
	{
		return hax.get(name);
	}

	public void registerAddonHack(Hack hack)
	{
		if(hax.containsKey(hack.getName()))
			throw new IllegalArgumentException(
				"Hack already registered: " + hack.getName());
		hax.put(hack.getName(), hack);
	}
	
	public Collection<Hack> getAllHax()
	{
		return Collections.unmodifiableCollection(hax.values());
	}
	
	public int countHax()
	{
		return hax.size();
	}
	
	public ArrayList<Path> listProfiles()
	{
		if(!Files.isDirectory(profilesFolder))
			return new ArrayList<>();
		
		try(Stream<Path> files = Files.list(profilesFolder))
		{
			return files.filter(Files::isRegularFile)
				.collect(Collectors.toCollection(ArrayList::new));
			
		}catch(IOException e)
		{
			throw new RuntimeException(e);
		}
	}
	
	public void loadProfile(String fileName) throws IOException, JsonException
	{
		enabledHacksFile.loadProfile(this, profilesFolder.resolve(fileName));
	}
	
	public void saveProfile(String fileName) throws IOException, JsonException
	{
		enabledHacksFile.saveProfile(this, profilesFolder.resolve(fileName));
	}
}
