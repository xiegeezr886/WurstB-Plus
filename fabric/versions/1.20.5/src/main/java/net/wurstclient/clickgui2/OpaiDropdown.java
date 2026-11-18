/* OpaiBounce interaction adaptation. See assets/wurstpenguin/opai/NOTICE.txt. */
package net.wurstclient.clickgui2;

/** Shared measurement and hit testing for the clipped, independently scrolling options. */
final class OpaiDropdown
{
    static final float ROW = 20, MAX_HEIGHT = 120;
    private final int count;
    private boolean open;
    private float reveal, scroll;

    OpaiDropdown(int count) { this.count = count; }
    boolean open() { return open; }
    float scroll() { return scroll; }
    float fullHeight() { return Math.min(MAX_HEIGHT, count * ROW); }
    float height() { return fullHeight() * reveal; }
    void close() { open = false; }
    void toggle(int selected)
    {
        open = !open;
        if(open) scroll = clamp(selected * ROW - fullHeight() / 2 + ROW / 2);
    }
    void advance(float seconds)
    {
        if(seconds <= 0) return;
        float target = open ? 1 : 0;
        reveal = target + (reveal - target) * (float)Math.exp(-40 * Math.max(0, seconds));
        if(Math.abs(reveal - target) < 0.0005F) reveal = target;
    }
    void wheel(double delta) { scroll = clamp(scroll - (float)delta * ROW); }
    int hit(double localY)
    {
        if(!open || localY < 0 || localY >= height()) return -1;
        int index = (int)((localY + scroll) / ROW);
        return index < count ? index : -1;
    }
    private float clamp(float value) { return Math.max(0, Math.min(Math.max(0, count * ROW - fullHeight()), value)); }
}
