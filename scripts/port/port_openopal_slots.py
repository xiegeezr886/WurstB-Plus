"""Port SlotHelper's lease and visual slot semantics, with loader API adapters."""
import re
from refactor_openopal_combat import ROOT, PLAN, PACKAGE, projects, replace, section
from port_openopal_input import original, HEADER


def slot_helper(modern, new_gui):
    s = original('client/feature/helper/impl/player/slot/SlotHelper.java')
    pairs = {'wtf.opal.client.feature.helper.impl.player.slot':'net.wurstclient.opal',
        'net.minecraft.client.network.ClientPlayerEntity':'net.minecraft.client.player.LocalPlayer',
        'ClientPlayerEntity':'LocalPlayer','net.minecraft.entity.player.PlayerInventory':'net.minecraft.world.entity.player.Inventory',
        'PlayerInventory':'Inventory','net.minecraft.item.ItemStack':'net.minecraft.world.item.ItemStack',
        'net.minecraft.util.math.MathHelper':'net.minecraft.util.Mth','MathHelper':'Mth',
        'wtf.opal.client.Constants.mc':'net.wurstclient.WurstClient.MC','mc.':'MC.',
        '.getMainHandStack()':'.getMainHandItem()', '.getMainStacks().get(this.currentItem)':'.getItem(this.currentItem)',
        'MC.currentScreen':'MC.gui.screen()' if new_gui else 'MC.screen',
        'MC.getOverlay()':'MC.gui.overlay()' if new_gui else 'MC.getOverlay()',
        'private static SlotHelper instance;':'private static SlotHelper instance = new SlotHelper();'}
    for old,new in pairs.items(): s=s.replace(old,new)
    s=s.replace('    public void sync(boolean reset, boolean check) {', '''    public void sync(boolean reset, boolean check) {
        ensureContext();
        if(MC.player == null) return;''')
    s=s.replace('    public SlotHelper setTargetItem(int currentItem) {', '''    public SlotHelper setTargetItem(int currentItem) {
        ensureContext();
        if(MC.player == null) return this;''')
    s=s.replace('    private int currentItem, targetItem;', '''    private Object player, world;
    private void ensureContext() {
        if(player != MC.player || world != MC.level) {
            active = false;
            silence = Silence.DEFAULT;
            ticks = 0;
            player = MC.player;
            world = MC.level;
        }
    }
    public boolean onSelect(int slot) {
        ensureContext();
        if(!active) return false;
        if(silence != Silence.NONE) setVisualSlot(slot);
        return true;
    }
    public boolean onScroll(double amount) {
        if(!active) return false;
        if(silence != Silence.NONE)
            setVisualSlot(Math.floorMod(currentItem - (int)Math.signum(amount), 9));
        return true;
    }
    private int currentItem, targetItem;''')
    if not modern:
        s = s.replace('.getSelectedSlot()', '.selected')
        s = s.replace('.setSelectedSlot(this.currentItem)', '.selected = this.currentItem')
        s = s.replace('.setSelectedSlot(this.targetItem)', '.selected = this.targetItem')
    return HEADER+s


SLOT_MIXIN = HEADER+'''package net.wurstclient.mixin;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Inventory;
import net.wurstclient.opal.SlotHelper;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(Minecraft.class)
public class OpenOpalSlotInputMixin {
    @Redirect(method = "handleKeybinds()V", at = @At(value = "KIND", target = "TARGET"OPCODE))
    private void opalSlot(Inventory inventory, int slot) {
        if(!SlotHelper.getInstance().onSelect(slot)) SET_SLOT;
    }
    @Inject(method = "handleKeybinds()V", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/client/player/LocalPlayer;isUsingItem()Z", ordinal = 0))
    private void opalSyncSlot(CallbackInfo ci) { SlotHelper.getInstance().sync(false, false); }
}
'''

SLOT_TICK = HEADER+'''package net.wurstclient.mixin;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.wurstclient.opal.SlotHelper;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(MultiPlayerGameMode.class)
public class OpenOpalSlotTickMixin {
    @Inject(method = "tick()V", at = @At("HEAD"))
    private void opalTick(CallbackInfo ci) { SlotHelper.getInstance().tick(); }
}
'''

HUD = HEADER+'''package net.wurstclient.mixin;
import net.minecraft.client.gui.HUD_CLASS;
import net.minecraft.world.item.ItemStack;
import net.wurstclient.WurstClient;
import net.wurstclient.opal.SlotHelper;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
@Mixin(HUD_CLASS.class)
public class OpenOpalSlotHudMixin {
    @ModifyExpressionValue(method = "*", at = @At(value = "KIND", target = "TARGET"OPCODE))
    private int opalHotbar(int actual) {
        var helper = SlotHelper.getInstance();
        return helper.isActive() && helper.getSilence() == SlotHelper.Silence.FULL
            ? helper.getVisualSlot() : actual;
    }
    @ModifyExpressionValue(method = "*", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/world/entity/player/Inventory;SELECTED_ITEM()Lnet/minecraft/world/item/ItemStack;"))
    private ItemStack opalName(ItemStack actual) {
        var helper = SlotHelper.getInstance();
        return helper.isActive() && helper.getSilence() == SlotHelper.Silence.FULL
            ? helper.getMainHandStack(WurstClient.MC.player) : actual;
    }
}
'''


def scaffold(s):
    s = section(s, '\tpublic boolean updateHeldBlockSlot()', '\n\t// ------------------------------------------------------- placement engine', '''    public boolean updateHeldBlockSlot() {
        if(MC.player == null) return false;
        int slot = getPlaceableBlock();
        if(slot == -1) return !isTellyMode() && ScaffoldUtils.isPlaceableStack(MC.player.getOffhandItem());
        selectBlockSlot(slot, isTellyMode());
        return true;
    }
    public void selectBlockSlot(int slot, boolean visible) {
        net.wurstclient.opal.SlotHelper.Silence silence = visible
            || switchMode.getSelected() == SwitchMode.NORMAL
            ? net.wurstclient.opal.SlotHelper.Silence.NONE
            : switchMode.getSelected() == SwitchMode.FULL
                ? net.wurstclient.opal.SlotHelper.Silence.FULL
                : net.wurstclient.opal.SlotHelper.Silence.DEFAULT;
        net.wurstclient.opal.SlotHelper.setCurrentItem(slot).silence(silence);
    }
    public void resetSlot() { net.wurstclient.opal.SlotHelper.getInstance().stop(); }
''')
    s = section(s, '\tprivate void restoreSlotAfterPlacement()', '\n\tpublic boolean canPlaceAtHit', '''    private void restoreSlotAfterPlacement() {
        // Original SlotHelper holds the slot for one tick, including FULL silence.
    }
''')
    return s


def heypixel(s):
    # The original mode delegates selection/restoration to SlotHelper.
    a=s.index('\t\thack.resetSlot();')
    b=s.index('\t\toldSlot = -1;', a)
    s=s[:a]+'\t\thack.resetSlot();\n'+s[b:]
    pattern=r'\t\tif\(player.getInventory\(\)\.(?:selected|getSelectedSlot\(\)) != slot\)\n\t\t\{.*?\n\t\t\}'
    s,n=re.subn(pattern,'\t\thack.selectBlockSlot(slot, false);',s,flags=re.S)
    assert n==1,n
    return s


def silence(s):
    a=s.index('\t\tvar player = WurstClient.MC.player;',s.index('public void onDisable'))
    b=s.index('\t\toriginalSlot = -1;',a)
    s=s[:a]+'\t\thack.resetSlot();\n'+s[b:]
    s=section(s,'\tprivate void applySlot(SlotData slot)','\n\tprivate boolean isSlotStillValid', '''    private void applySlot(SlotData slot) {
        if(slot.hand() != InteractionHand.MAIN_HAND) {
            net.wurstclient.opal.SlotHelper.getInstance().stop();
            return;
        }
        net.wurstclient.opal.SlotHelper.setCurrentItem(slot.slot()).silence(
            hack.silenceSpoofItem.isChecked() ? net.wurstclient.opal.SlotHelper.Silence.FULL
                : net.wurstclient.opal.SlotHelper.Silence.NONE);
    }
''')
    # Existing temporary restore now becomes redundant: selected slot already equals armed slot.
    return s


def main():
    pending={}
    for project in projects():
        scaffold_path=project/PACKAGE/'hacks/ScaffoldWalkHack.java'
        s=scaffold_path.read_text(encoding='utf-8')
        modern='.getSelectedSlot()' in s
        new_gui='MC.gui.screen()' in s
        helper=slot_helper(modern,new_gui)
        input_mixin=SLOT_MIXIN.replace('KIND','INVOKE' if modern else 'FIELD').replace('TARGET',
            'Lnet/minecraft/world/entity/player/Inventory;setSelectedSlot(I)V' if modern else 'Lnet/minecraft/world/entity/player/Inventory;selected:I')
        input_mixin=input_mixin.replace('OPCODE','' if modern else ', opcode = org.objectweb.asm.Opcodes.PUTFIELD')
        input_mixin=input_mixin.replace('SET_SLOT','inventory.setSelectedSlot(slot)' if modern else 'inventory.selected = slot')
        hud=HUD.replace('HUD_CLASS','Hud' if new_gui else 'Gui').replace('KIND','INVOKE' if modern else 'FIELD')
        hud=hud.replace('TARGET','Lnet/minecraft/world/entity/player/Inventory;getSelectedSlot()I' if modern else 'Lnet/minecraft/world/entity/player/Inventory;selected:I')
        hud=hud.replace('OPCODE','' if modern else ', opcode = org.objectweb.asm.Opcodes.GETFIELD')
        hud=hud.replace('SELECTED_ITEM','getSelectedItem' if modern else 'getSelected')
        for name,content in {'opal/SlotHelper.java':helper,'mixin/OpenOpalSlotInputMixin.java':input_mixin,
            'mixin/OpenOpalSlotTickMixin.java':SLOT_TICK,'mixin/OpenOpalSlotHudMixin.java':hud}.items():
            pending[project/PACKAGE/name]=content
        pending[scaffold_path]=scaffold(s)
        for name,transform in [('HeypixelScaffoldMode',heypixel),('SilenceTellyScaffoldMode',silence)]:
            path=project/PACKAGE/f'hacks/scaffold/{name}.java'
            pending[path]=transform(path.read_text(encoding='utf-8'))
        path=project/PACKAGE/'hacks/KillauraHack.java'
        text=path.read_text(encoding='utf-8')
        a=text.index('\tprivate void switchToAxe(')
        b=text.index('\n\tprivate void updateAutoblock()',a)
        text=text[:a]+'''    private void switchToAxe(LivingEntity target) {
        if(!target.isBlocking()) return;
        for(int slot=0; slot<9; slot++) {
            if(MC.player.getInventory().getItem(slot).is(ItemTags.AXES)) {
                net.wurstclient.opal.SlotHelper.setCurrentItem(slot)
                    .silence(net.wurstclient.opal.SlotHelper.Silence.NONE);
                return;
            }
        }
    }
'''+text[b:]
        text=text.replace('ItemStack held = MC.player.getMainHandItem();','ItemStack held = net.wurstclient.opal.SlotHelper.getInstance().getMainHandStack(MC.player);')
        pending[path]=text
        config=next((project/'src/main/resources').glob('wurst*.mixins.json'))
        pending[config]=replace(config.read_text(encoding='utf-8-sig'),'"client": [','"client": [\n    "OpenOpalSlotInputMixin",\n    "OpenOpalSlotTickMixin",\n    "OpenOpalSlotHudMixin",')
    for path,content in pending.items():
        if path.exists():
            backup=PLAN/'before-slot-port'/path.relative_to(ROOT)
            backup.parent.mkdir(parents=True,exist_ok=True)
            assert not backup.exists(),backup
            backup.write_bytes(path.read_bytes())
        path.parent.mkdir(parents=True,exist_ok=True)
        path.write_text(content,encoding='utf-8',newline='\n')
    print('Slot source port:',len(pending),'files')


if __name__=='__main__': main()
