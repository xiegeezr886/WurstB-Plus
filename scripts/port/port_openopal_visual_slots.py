"""Complete SlotHelper scroll and first person renderer integration."""
from refactor_openopal_combat import ROOT,PLAN,PACKAGE,projects,replace
from port_openopal_input import HEADER

VISUAL=HEADER+'''package net.wurstclient.mixin;
import RENDERER_IMPORT;
import net.minecraft.world.item.ItemStack;
import net.wurstclient.WurstClient;
import net.wurstclient.opal.SlotHelper;
import net.wurstclient.opal.rotation.RotationHelper;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
@Mixin(RENDERER.class)
public class OpenOpalHandsMixin {
    @ModifyExpressionValue(method = "tick", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/client/player/LocalPlayer;getMainHandItem()Lnet/minecraft/world/item/ItemStack;"))
    private ItemStack opalHeldItem(ItemStack actual) {
        return SlotHelper.getInstance().getMainHandStack(WurstClient.MC.player);
    }
METHODS
}
'''

SCROLL=HEADER+'''package net.wurstclient.mixin;
import net.minecraft.client.MouseHandler;
import net.minecraft.world.entity.player.Inventory;
import net.wurstclient.opal.SlotHelper;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
@Mixin(MouseHandler.class)
public class OpenOpalScrollMixin {
    @WrapOperation(method = "onScroll(JDD)V", at = @At(value = "INVOKE", target = "TARGET"))
    private void opalScroll(Inventory inventory, PARAM, Operation<Void> original) {
        if(!SlotHelper.getInstance().CHECK) original.call(inventory, VALUE);
    }
}
'''

def main():
    pending={}
    for p in projects():
        props=dict(line.split('=',1) for line in (p/'gradle.properties').read_text(encoding='utf-8-sig').splitlines() if '=' in line)
        v=tuple(map(int,props['minecraft_version'].split('.')))
        modern='.getSelectedSlot()' in (p/PACKAGE/'opal/SlotHelper.java').read_text(encoding='utf-8')
        renderer='FirstPersonHandsAndItems' if v>=(26,3) else 'ItemInHandRenderer'
        pkg='net.minecraft.client.player.' if v>=(26,3) else 'net.minecraft.client.renderer.'
        method='extractRenderState' if v>=(26,3) else 'renderHandsWithItems'
        methods=[]
        for field,getter in [('xBobO','getLastRenderPitchOr'),('xBob','getRenderPitchOr'),('yBobO','getLastRenderYawOr'),('yBob','getRenderYawOr')]:
            methods.append(f'''    @ModifyExpressionValue(method = "{method}", at = @At(value = "FIELD",
        target = "Lnet/minecraft/client/player/LocalPlayer;{field}:F"))
    private float opal{field}(float original) {{ return RotationHelper.getClientHandler().{getter}(original); }}''')
        for axis,getter in [('X','getPitchOr'),('Y','getYawOr')]:
            methods.append(f'''    @ModifyExpressionValue(method = "{method}", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/client/player/LocalPlayer;getView{axis}Rot(F)F"))
    private float opal{axis}(float original) {{ return RotationHelper.getClientHandler().{getter}(original); }}''')
        visual=VISUAL.replace('RENDERER_IMPORT',pkg+renderer).replace('RENDERER',renderer).replace('METHODS','\n'.join(methods))
        scroll=SCROLL.replace('TARGET','Lnet/minecraft/world/entity/player/Inventory;setSelectedSlot(I)V' if modern else 'Lnet/minecraft/world/entity/player/Inventory;swapPaint(D)V')
        scroll=scroll.replace('PARAM','int slot' if modern else 'double amount').replace('CHECK','onSelect(slot)' if modern else 'onScroll(amount)').replace('VALUE','slot' if modern else 'amount')
        pending[p/PACKAGE/'mixin/OpenOpalHandsMixin.java']=visual
        pending[p/PACKAGE/'mixin/OpenOpalScrollMixin.java']=scroll
        if v>=(26,3):
            path=p/PACKAGE/'mixin/OpenOpalInputMixin.java'
            s=path.read_text(encoding='utf-8')
            methods=''
            for name,button in [('startAttack()Z','Left'),('startUseItem()V','Right')]:
                methods+=f'''    @Redirect(method = "{name}", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/client/player/LocalPlayer;swing(Lnet/minecraft/world/InteractionHand;Lnet/minecraft/world/item/component/SwingAnimation;Z)Z"))
    private boolean opal{button}Swing(LocalPlayer player, InteractionHand hand,
        net.minecraft.world.item.component.SwingAnimation animation, boolean send) {{
        return MouseHelper.get{button}Button().isShowSwings() && player.swing(hand, animation, send);
    }}
'''
            pending[path]=s[:s.rindex('}')]+methods+'}\n'
        config=next((p/'src/main/resources').glob('wurst*.mixins.json'))
        pending[config]=replace(config.read_text(encoding='utf-8-sig'),'"client": [','"client": [\n    "OpenOpalHandsMixin",\n    "OpenOpalScrollMixin",')
    for path,s in pending.items():
        if path.exists():
            backup=PLAN/'before-visual-slot-port'/path.relative_to(ROOT)
            backup.parent.mkdir(parents=True,exist_ok=True)
            assert not backup.exists(),backup
            backup.write_bytes(path.read_bytes())
        path.parent.mkdir(parents=True,exist_ok=True)
        path.write_text(s,encoding='utf-8',newline='\n')
    print('Visual and scroll integration:',len(pending),'files')


if __name__=='__main__':main()
