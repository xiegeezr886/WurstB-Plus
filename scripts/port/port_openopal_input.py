"""Transplant OpenOpal mouse buttons, shared swing timer and input lifecycle."""
import re
from refactor_openopal_combat import ROOT, PLAN, PACKAGE, projects, replace, section
from port_openopal_sources import REFERENCE

HEADER = '/* Adapted directly from OpenOpal (GPL-3.0); see META-INF/licenses/openopal/LICENSE. */\n'


def original(path):
    return (REFERENCE / path).read_text(encoding='utf-8')


def mouse_button():
    s = original('client/feature/helper/impl/player/mouse/MouseButton.java')
    for old, new in {
        'wtf.opal.client.feature.helper.impl.player.mouse': 'net.wurstclient.opal',
        'net.minecraft.client.option.KeyBinding': 'net.minecraft.client.KeyMapping',
        'wtf.opal.mixin.KeyBindingAccessor': 'net.wurstclient.mixin.OpenOpalKeyAccessor',
        'KeyBindingAccessor': 'OpenOpalKeyAccessor', 'KeyBinding': 'KeyMapping',
        '.wasPressed()': '.consumeClick()', '.isPressed()': '.isDown()',
        '.getTimesPressed()': '.opal$getClickCount()',
    }.items():
        s = s.replace(old, new)
    return HEADER + s


def mouse_helper():
    s = original('client/feature/helper/impl/player/mouse/MouseHelper.java')
    s = s.replace('wtf.opal.client.feature.helper.impl.player.mouse', 'net.wurstclient.opal')
    s = s.replace('net.minecraft.client.option.KeyBinding', 'net.minecraft.client.KeyMapping')
    s = s.replace('KeyBinding', 'KeyMapping').replace('wtf.opal.client.Constants.mc', 'net.wurstclient.WurstClient.MC')
    s = s.replace('mc.options.attackKey', 'MC.options.keyAttack').replace('mc.options.useKey', 'MC.options.keyUse')
    s = s.replace('private static MouseHelper instance;', 'private static MouseHelper instance;')
    s = s.replace('return instance.', 'return getInstance().')
    s = s.replace('        return instance;', '        if (instance == null) setInstance();\n        return instance;')
    return HEADER + s


CPS = HEADER + '''package net.wurstclient.opal;
import java.util.function.BooleanSupplier;
import java.util.function.IntSupplier;

/** CPSProperty's original timer state; settings are supplied by the host UI. */
public final class CPSProperty {
    private final IntSupplier cps;
    private final BooleanSupplier modernDelay;
    private long nextClick;
    public CPSProperty(IntSupplier cps, BooleanSupplier modernDelay) {
        this.cps = cps;
        this.modernDelay = modernDelay;
    }
    public boolean isModernDelay() { return modernDelay.getAsBoolean(); }
    public int getCPS() { return cps.getAsInt(); }
    public int getClickDelay() { return 1000 / getCPS(); }
    public void resetClick() { nextClick = getClickDelay(); }
    public long getNextClick() { return nextClick; }
}
'''


def swing_delay():
    s = original('client/feature/helper/impl/player/swing/SwingDelay.java')
    s = s.replace('wtf.opal.client.feature.helper.impl.player.swing', 'net.wurstclient.opal')
    s = re.sub(r'import wtf.opal[^;]+;\n', '', s)
    s = s.replace('wtf.opal.client.Constants.mc', 'net.wurstclient.WurstClient.MC')
    s = s.replace(' implements IHelper', '').replace('mc.player', 'MC.player')
    s = s.replace('getAttackCooldownProgress', 'getAttackStrengthScale')
    s = s.replace('private static SwingDelay instance;', 'private static SwingDelay instance = new SwingDelay();')
    s = s.replace('        EventDispatcher.subscribe(instance);\n', '')
    return HEADER + s


KEY_ACCESSOR = HEADER + '''package net.wurstclient.mixin;
import net.minecraft.client.KeyMapping;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
@Mixin(KeyMapping.class)
public interface OpenOpalKeyAccessor {
    @Accessor("clickCount") int opal$getClickCount();
}
'''

INPUT_MIXIN = HEADER + '''package net.wurstclient.mixin;
import net.minecraft.client.Minecraft;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.network.protocol.game.ServerboundSwingPacket;
import net.wurstclient.WurstClient;
import net.wurstclient.opal.MouseHelper;
import net.wurstclient.opal.MouseButton;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.Slice;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** OpenOpal MinecraftClientMixin event ordering, mapped to Mojmap. */
@Mixin(Minecraft.class)
public class OpenOpalInputMixin {
    @Inject(method = "tick()V", at = @At("HEAD"))
    private void opalPreTick(CallbackInfo ci) {
        if (!WurstClient.INSTANCE.isEnabled() || WurstClient.INSTANCE.getHax() == null) return;
        var hax = WurstClient.INSTANCE.getHax();
        if (hax.scaffoldWalkHack.isEnabled()) hax.scaffoldWalkHack.onUpdate();
        if (hax.killauraHack.isEnabled()) hax.killauraHack.onUpdate();
    }
    @Inject(method = "handleKeybinds()V", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/client/player/LocalPlayer;isUsingItem()Z", ordinal = 0))
    private void opalMouseInput(CallbackInfo ci) {
        if (!WurstClient.INSTANCE.isEnabled() || WurstClient.INSTANCE.getHax() == null) return;
        var hax = WurstClient.INSTANCE.getHax();
        if (hax.scaffoldWalkHack.isEnabled()) hax.scaffoldWalkHack.onOpalMouseInput();
        if (hax.killauraHack.isEnabled()) hax.killauraHack.onOpalMouseInput();
    }
    @Inject(method = "handleKeybinds()V", at = @At("TAIL"))
    private void opalInputTail(CallbackInfo ci) { MouseHelper.getInstance().tick(); }

    @Redirect(method = "handleKeybinds()V", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/client/KeyMapping;isDown()Z"))
    private boolean opalIsPressed(KeyMapping key) {
        MouseButton button = MouseHelper.getButtonFromBinding(key);
        return button == null ? key.isDown() : button.isPressed();
    }
    @Redirect(method = "handleKeybinds()V", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/client/KeyMapping;consumeClick()Z"),
        slice = @Slice(from = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/player/LocalPlayer;isUsingItem()Z", ordinal = 0)))
    private boolean opalWasPressed(KeyMapping key) {
        MouseButton button = MouseHelper.getButtonFromBinding(key);
        return button == null ? key.consumeClick() : button.wasPressed();
    }
    @Redirect(method = "startAttack()Z", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/client/player/LocalPlayer;swing(Lnet/minecraft/world/InteractionHand;)V"))
    private void opalAttackSwing(LocalPlayer player, InteractionHand hand) {
        if (MouseHelper.getLeftButton().isShowSwings()) player.swing(hand);
        else player.connection.send(new ServerboundSwingPacket(hand));
    }
    @Redirect(method = "startUseItem()V", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/client/player/LocalPlayer;swing(Lnet/minecraft/world/InteractionHand;)V"))
    private void opalUseSwing(LocalPlayer player, InteractionHand hand) {
        if (MouseHelper.getRightButton().isShowSwings()) player.swing(hand);
        else player.connection.send(new ServerboundSwingPacket(hand));
    }
}
'''


def aura(s):
    s = replace(s, 'import net.wurstclient.Category;', '''import net.wurstclient.Category;
import net.wurstclient.opal.CPSProperty;
import net.wurstclient.opal.SwingDelay;
import net.wurstclient.opal.MouseHelper;
import net.wurstclient.events.PacketInputListener;
import net.minecraft.network.protocol.game.ClientboundDamageEventPacket;
import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket;''')
    s = replace(s, 'implements UpdateListener, PreMotionListener, PostMotionListener, RenderListener',
        'implements UpdateListener, PreMotionListener, PostMotionListener, RenderListener, PacketInputListener')
    s = s.replace('360, 30, 360, 10,', '180, 1, 180, 1,')
    s = s.replace('fov.getValue(),', 'fov.getValue() * 2,')
    s = s.replace('private int grimKeepTicks;', '''private int grimKeepTicks, grimDamageKeepTicks, grimDamageWindowTicks;
    private Object statePlayer, stateWorld;
    private EntityHitResult lookaheadHit;
    private final CPSProperty attackTiming = new CPSProperty(attackCps::getValueI,
        () -> cpsMode.getSelected() == CpsMode.AUTO);
    private final CPSProperty swingTiming = new CPSProperty(swingCps::getValueI, () -> false);''')
    s = s.replace('EVENTS.add(UpdateListener.class, this);', 'EVENTS.add(PacketInputListener.class, this);')
    s = s.replace('EVENTS.remove(UpdateListener.class, this);', 'EVENTS.remove(PacketInputListener.class, this);')
    s = replace(s, '\t\tactions.clear();\n\t\tif(!requirementsMet())', '''        if(statePlayer != MC.player || stateWorld != MC.level) {
            clearTarget();
            grimDamageKeepTicks = grimDamageWindowTicks = 0;
            statePlayer = MC.player;
            stateWorld = MC.level;
        }
        if(grimKeepTicks > 0) grimKeepTicks--;
        if(grimDamageKeepTicks > 0) grimDamageKeepTicks--;
        if(grimDamageWindowTicks > 0) grimDamageWindowTicks--;
		actions.clear();
		if(!requirementsMet())''')
    s = replace(s, '''		if(grimKeepTicks > 0)
		{
			grimKeepTicks--;
			MC.player.setSprinting(true);
		}
''', '')
    s = replace(s, '\t\tactions.begin(MC.player, MC.level, MC.player.tickCount);\n\t\tplanTargets();', '\t\tactions.begin(MC.player, MC.level, MC.player.tickCount);\n\t\tplanTargets();\n\t\tupdateAutoblock();')
    s = section(s, '\t@Override\n\tpublic void onPostMotion()', '\n\t@Override\n\tpublic void onRender', '''    @Override
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
        if(MC.player.isUsingItem()) return;
        target = targeting.revalidate(target, attackConfig());
        if(target == null) return;
        if(overrideRaycast.isChecked() && !heypixelBypass.isChecked()) {
            if(tickLookahead.isChecked()
                && (lookaheadHit == null || lookaheadHit.getEntity() != target.entity)) return;
            MC.hitResult = new EntityHitResult(target.entity, target.hitVec);
        }
        if(!(MC.hitResult instanceof EntityHitResult hit)) return;
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
            && p.getId() == player.getId();
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
''')
    s = replace(s, 'if((attackCooldown19.isChecked() || cpsMode.getSelected() == CpsMode.AUTO)\n\t\t\t&& !smartAttack)', 'if(attackCooldown19.isChecked() && !smartAttack)')
    s = replace(s, '|| System.currentTimeMillis() >= nextAttackTime;', '|| SwingDelay.isSwingAvailable(attackTiming, false);')
    s = section(s, '\tprivate long clickDelay()', '\n\tprivate void switchToAxe', '''    private void tryFakeSwing() {
        if(MC.hitResult instanceof net.minecraft.world.phys.BlockHitResult
            && MC.hitResult.getType() == net.minecraft.world.phys.HitResult.Type.BLOCK) return;
        OpenAura.Config config = new OpenAura.Config(swingRange.getValue(),
            fov.getValue() * 2, targetFlags(), throughWalls.isChecked());
        if(OpenAura.collect(config).isEmpty() || !SwingDelay.isSwingAvailable(swingTiming)) return;
        var left = MouseHelper.getLeftButton();
        left.setPressed(true, net.wurstclient.opal.RandomUtility.getRandomInt(2));
        if(hideFakeSwings.isChecked() && !(MC.hitResult instanceof EntityHitResult))
            left.setShowSwings(false);
        swingTiming.resetClick();
    }
''')
    s = section(s, '\tprivate void keepSprint(', '\n\tprivate void updateAutoblock()', '')
    start = '\t\tInteractionHand hand = findBlockableHand();'
    s = section(s, start, '\n\tprivate void stopBlocking()', '''        MouseHelper.getRightButton().setPressed(true, 2);
        MouseHelper.getRightButton().setShowSwings(false);
    }
''')
    s = replace(s, '\t\tif(MC.player != null && MC.gameMode != null && MC.player.isUsingItem()', '\t\tMouseHelper.getRightButton().setPressed(false, 0);\n\t\tif(MC.player != null && MC.gameMode != null && MC.player.isUsingItem()')
    s = s.replace('\t\tgrimKeepTicks = 0;', '\t\tgrimKeepTicks = grimDamageKeepTicks = grimDamageWindowTicks = 0;\n\t\tlookaheadHit = null;')
    s = s.replace('\tprivate long nextAttackTime;\n\tprivate long nextSwingTime;\n', '')
    s = s.replace('\t\tnextAttackTime = 0;\n\t\tnextSwingTime = 0;\n', '')
    return s


def scaffold(s):
    s = s.replace('\t\tEVENTS.add(UpdateListener.class, this);\n', '')
    s = s.replace('\t\tEVENTS.remove(UpdateListener.class, this);\n', '')
    s = replace(s, '\t\tif(mode.getSelected() != lastActiveMode)\n\t\t{', '\t\tif(mode.getSelected() != lastActiveMode)\n\t\t{\n\t\t\trecovery.reset();')
    s = replace(s, '\t\tif(!canRunMotion())\n\t\t{\n\t\t\tclearMotionPlan();\n\t\t\treleaseSimulatedInput();', '\t\tif(!canRunMotion())\n\t\t{\n\t\t\trecovery.reset();\n\t\t\tclearMotionPlan();\n\t\t\treleaseSimulatedInput();')
    s = replace(s, '\t@Override\n\tpublic void onMoveInput(', '''    public void onOpalMouseInput() {
        if(!canRunMotion()) { clearMotionPlan(); return; }
        activeMode().onMouseInput();
    }

	@Override
	public void onMoveInput(''')
    return s


def silence(s):
    s = section(s, '\t\t// The movement packet is already on the wire', '\n\t@Override\n\tpublic ScaffoldWalkHack.RotationRequest', '''    }

    @Override
    public void onMouseInput() {
        if(!ready()) { cancelPendingPlacement(); return; }
        if(tryPlaceArmed()) invalidateSearchCache();
    }
''')
    s = s.replace('Vec3 requestedEyePos = placementRequestEyePos;', 'Vec3 requestedEyePos = WurstClient.MC.player.getEyePosition();')
    return s


def main():
    common = {'opal/MouseButton.java': mouse_button(), 'opal/MouseHelper.java': mouse_helper(),
        'opal/CPSProperty.java': CPS, 'opal/SwingDelay.java': swing_delay(),
        'mixin/OpenOpalKeyAccessor.java': KEY_ACCESSOR, 'mixin/OpenOpalInputMixin.java': INPUT_MIXIN}
    pending = {}
    for project in projects():
        for name, content in common.items(): pending[project / PACKAGE / name] = content
        for name, transform in [('hacks/KillauraHack.java', aura), ('hacks/ScaffoldWalkHack.java', scaffold),
            ('hacks/scaffold/SilenceTellyScaffoldMode.java', silence)]:
            path = project / PACKAGE / name
            pending[path] = transform(path.read_text(encoding='utf-8'))
        path = project / PACKAGE / 'hacks/scaffold/ScaffoldMode.java'
        pending[path] = replace(path.read_text(encoding='utf-8'), '\tdefault void onPostMotion()', '\tdefault void onMouseInput() {}\n\n\tdefault void onPostMotion()')
        path = project / PACKAGE / 'hacks/KeepSprintHack.java'
        pending[path] = replace(path.read_text(encoding='utf-8'), 'KeepSprintPolicy.shouldPreserveSprint(isEnabled(),', 'KeepSprintPolicy.shouldPreserveSprint(isEnabled() || WURST.getHax().killauraHack.shouldKeepSprint(),')
        config = next((project / 'src/main/resources').glob('wurst*.mixins.json'))
        pending[config] = replace(config.read_text(encoding='utf-8-sig'), '"client": [', '"client": [\n    "OpenOpalKeyAccessor",\n    "OpenOpalInputMixin",')
    for path, content in pending.items():
        if path.exists():
            backup = PLAN / 'before-input-port' / path.relative_to(ROOT)
            backup.parent.mkdir(parents=True, exist_ok=True)
            assert not backup.exists(), backup
            backup.write_bytes(path.read_bytes())
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text(content, encoding='utf-8', newline='\n')
    print('Input source port:', len(pending), 'files')


if __name__ == '__main__': main()
