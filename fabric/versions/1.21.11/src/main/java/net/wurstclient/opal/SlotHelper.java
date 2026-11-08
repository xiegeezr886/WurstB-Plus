/* Adapted directly from OpenOpal (GPL-3.0); see META-INF/licenses/openopal/LICENSE. */
package net.wurstclient.opal;

import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.util.Mth;

import static net.wurstclient.WurstClient.MC;

public final class SlotHelper {
    private SlotHelper() {
    }

    private Object player, world;
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
    public boolean onScrollTo(int nextSlot, int actualSlot) {
        ensureContext();
        if(!active) return false;
        if(silence != Silence.NONE) setVisualSlot(Math.floorMod(currentItem + nextSlot - actualSlot, 9));
        return true;
    }
    public boolean onScroll(double amount) {
        ensureContext();
        if(!active) return false;
        if(silence != Silence.NONE)
            setVisualSlot(Math.floorMod(currentItem - (int)Math.signum(amount), 9));
        return true;
    }
    private int currentItem, targetItem;
    private boolean active;
    private int activeTick, ticks;
    private Silence silence = Silence.DEFAULT;

    public SlotHelper setTargetItem(int currentItem) {
        ensureContext();
        if(MC.player == null) return this;
        currentItem = Mth.clamp(currentItem, 0, 8);

        if (!this.active) {
            this.currentItem = MC.player.getInventory().getSelectedSlot();
        }
        this.targetItem = currentItem;
        this.activeTick = this.ticks;
        this.active = true;
        this.sync(true, true);
        return this;
    }

    public SlotHelper silence(Silence silence) {
        this.silence = silence;
        return this;
    }

    public void setVisualSlot(int currentItem) {
        this.currentItem = currentItem;
    }

    public void stop() { // if you need something to swap back instantly use this, otherwise there will be a 1 tick delay purposefully to prevent collisions with mc
        this.activeTick = -1;
        this.sync(true, true);
    }

    public void sync(boolean reset, boolean check) {
        ensureContext();
        if(MC.player == null) return;
        if (this.active) {
            if (!check || (MC.getOverlay() == null && MC.screen == null)) {
                if (reset && this.activeTick != this.ticks) {
                    MC.player.getInventory().setSelectedSlot(this.currentItem);
                    this.silence = Silence.DEFAULT;
                    this.active = false;
                } else {
                    MC.player.getInventory().setSelectedSlot(this.targetItem);
                }
            }
        }
    }

    public void tick() {
        this.sync(true, true);
        this.ticks++;
    }

    public ItemStack getMainHandStack(LocalPlayer player) {
        ensureContext();
        return this.active && this.silence != Silence.NONE ? player.getInventory().getItem(this.currentItem) : player.getMainHandItem();
    }

    public int getSelectedSlot(Inventory inventory) {
        ensureContext();
        return this.active && this.silence == Silence.FULL ? this.currentItem : inventory.getSelectedSlot();
    }

    public boolean isActive() {
        ensureContext();
        return active;
    }

    public int getVisualSlot() {
        return currentItem;
    }

    public Silence getSilence() {
        return silence;
    }

    private static SlotHelper instance = new SlotHelper();

    public static SlotHelper getInstance() {
        return instance;
    }

    public static void setInstance() {
        instance = new SlotHelper();
    }

    public static SlotHelper setCurrentItem(int currentItem) {
        return instance.setTargetItem(currentItem);
    }

    public enum Silence {
        NONE,
        DEFAULT,
        FULL
    }
}
