"""Restore reference external rotation/inventory guards and relative scroll slots."""
import json
import subprocess
from concurrent.futures import ThreadPoolExecutor
from refactor_openopal_combat import ROOT, PLAN, PACKAGE, projects, replace

ENTITY = '''/* OpenOpal EntityMixin rotation invalidation; GPL-3.0. */
package net.wurstclient.mixin;
import net.minecraft.world.entity.Entity;
import net.wurstclient.WurstClient;
import net.wurstclient.opal.rotation.RotationHelper;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(Entity.class)
public class OpenOpalEntityRotationMixin {
    @Inject(method = {"setYRot(F)V", "setXRot(F)V"}, at = @At("HEAD"))
    private void opalRotationSet(float value, CallbackInfo ci) {
        if((Object)this == WurstClient.MC.player) RotationHelper.getClientHandler().onRotationSet();
    }
}
'''
INVENTORY = '''/* OpenOpal InventoryScreenMixin visual rotation guard; GPL-3.0. */
package net.wurstclient.mixin;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.wurstclient.opal.rotation.RotationHelper;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(InventoryScreen.class)
public class OpenOpalInventoryViewMixin {
    @Inject(method = "METHOD", at = @At("HEAD"))
    private static void opalViewStart(CallbackInfo ci) { RotationHelper.getClientHandler().setTicking(true); }
    @Inject(method = "METHOD", at = @At("RETURN"))
    private static void opalViewEnd(CallbackInfo ci) { RotationHelper.getClientHandler().setTicking(false); }
}
'''


def main():
    api=json.loads((PLAN/'mc-api.json').read_text(encoding='utf-8'))
    def inspect(item):
        version,info=item
        s=subprocess.check_output(['C:/Users/ui863/.jdks/jdk25.0.4_10/bin/javap.exe','-p','-classpath',info['jar'],'net.minecraft.client.gui.screens.inventory.InventoryScreen'],text=True)
        method='extractEntityInInventoryFollowsMouse' if 'extractEntityInInventoryFollowsMouse' in s else 'renderEntityInInventoryFollowsMouse'
        assert method in s,version
        return version,method
    with ThreadPoolExecutor(max_workers=4) as pool:methods=dict(pool.map(inspect,api.items()))
    pending={}
    for p in projects():
        props=dict(line.split('=',1) for line in (p/'gradle.properties').read_text(encoding='utf-8-sig').splitlines() if '=' in line)
        pending[p/PACKAGE/'mixin/OpenOpalEntityRotationMixin.java']=ENTITY
        pending[p/PACKAGE/'mixin/OpenOpalInventoryViewMixin.java']=INVENTORY.replace('METHOD',methods[props['minecraft_version']])
        f=p/PACKAGE/'opal/SlotHelper.java'
        s=f.read_text(encoding='utf-8')
        s=replace(s,'    public boolean onScroll(double amount)', '''    public boolean onScrollTo(int nextSlot, int actualSlot) {
        ensureContext();
        if(!active) return false;
        if(silence != Silence.NONE) setVisualSlot(Math.floorMod(currentItem + nextSlot - actualSlot, 9));
        return true;
    }
    public boolean onScroll(double amount)''')
        pending[f]=s
        f=p/PACKAGE/'mixin/OpenOpalScrollMixin.java'
        s=f.read_text(encoding='utf-8')
        if 'onSelect(slot)' in s:
            field='inventory.getSelectedSlot()' if 'getSelectedSlot()' in pending[p/PACKAGE/'opal/SlotHelper.java'] else 'inventory.selected'
            s=s.replace('onSelect(slot)', 'onScrollTo(slot, '+field+')')
            pending[f]=s
        config=next((p/'src/main/resources').glob('wurst*.mixins.json'))
        pending[config]=replace(config.read_text(encoding='utf-8-sig'), '"client": [','"client": [\n    "OpenOpalEntityRotationMixin",\n    "OpenOpalInventoryViewMixin",')
    for f in pending:
        if f.exists():assert not (PLAN/'before-view-port'/f.relative_to(ROOT)).exists(),f
    for f,s in pending.items():
        if f.exists():
            b=PLAN/'before-view-port'/f.relative_to(ROOT)
            b.parent.mkdir(parents=True,exist_ok=True)
            b.write_bytes(f.read_bytes())
        f.parent.mkdir(parents=True,exist_ok=True)
        f.write_text(s,encoding='utf-8',newline='\n')
    print('View and scroll corrections:',len(pending),'files')


if __name__=='__main__':main()
