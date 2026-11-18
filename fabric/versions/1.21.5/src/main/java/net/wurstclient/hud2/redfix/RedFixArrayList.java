/* RedFix/LiquidBounce ArrayList theme adapted for the native HUD.
 * See META-INF/licenses/redfix-hud. SPDX-License-Identifier: GPL-3.0-or-later */
package net.wurstclient.hud2.redfix;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import net.wurstclient.Category;
import net.wurstclient.WurstClient;
import net.wurstclient.hack.Hack;
import net.wurstclient.hud2.nanovg.HudFonts;
import net.wurstclient.hud2.nanovg.NanoVGContext;
import net.wurstclient.hud2.redfix.RedFixListLayout.Entry;
import net.wurstclient.hud2.redfix.RedFixListLayout.Row;

/** Shared by the HUD editor's ArrayList and the legacy ModuleList toggle. */
public final class RedFixArrayList
{
    private final RedFixListLayout layout = new RedFixListLayout();
    private final Map<String, Entry> measurements = new HashMap<>();
    private List<Row> rows = List.of();
    private long lastFrame;
    private boolean preview;
    private float width, height;

    public void clear()
    {
        layout.clear(); measurements.clear(); rows = List.of(); lastFrame = 0;
        width = height = 0;
    }

    public void update(NanoVGContext nvg, boolean tags, boolean spaced,
        boolean ascending, boolean right, boolean onlyBound, boolean editing)
    {
        long now = System.nanoTime() / 1_000_000;
        var wurst = WurstClient.INSTANCE;
        List<Entry> active = new ArrayList<>();
        if(wurst.getHax() != null)
            for(Hack hack : wurst.getHax().getAllHax())
            {
                if(!hack.isEnabled() || hack.getCategory() == null
                    || hack == wurst.getHax().moduleListHack
                    || onlyBound && wurst.getKeybinds().getKeyForCommand(".t " + hack.getName()) == null)
                    continue;
                String id = hack.getName();
                String key = "hack.name." + id.toLowerCase(Locale.ROOT);
                String localized = wurst.translate(key);
                String tag = tags && WurstClient.MC.player != null ? RedFixListLayout.tag(id,
                    localized.equals(key) ? id : localized, hack.getRenderName()) : "";
                active.add(measure(nvg, id, spaced ? RedFixListLayout.spacedName(id) : id,
                    tag, hack.getCategory().getColor()));
            }
        boolean nextPreview = active.isEmpty() && editing;
        if(nextPreview != preview || now - lastFrame > 1000) layout.clear();
        preview = nextPreview;
        if(preview)
        {
            active.add(measure(nvg, "sample-speed", "Speed", tags ? "Custom" : "", Category.MOVEMENT.getColor()));
            active.add(measure(nvg, "sample-aura", "KillAura", tags ? "Single" : "", Category.COMBAT.getColor()));
            active.add(measure(nvg, "sample-light", "Fullbright", "", Category.RENDER.getColor()));
        }
        rows = layout.update(active, ascending, right, now);
        width = height = 0;
        for(Row row : rows)
        {
            width = Math.max(width, row.entry().width());
            height = Math.max(height, row.y(now) + RedFixListLayout.HEIGHT);
        }
        // Cache at most one measurement per registered module, including changing tags.
        lastFrame = now;
    }

    private Entry measure(NanoVGContext nvg, String id, String name, String tag, int color)
    {
        Entry old = measurements.get(id);
        if(old != null && old.name().equals(name) && old.tag().equals(tag) && old.color() == color)
            return old;
        float nameWidth = nvg.getTextWidth(name, RedFixListLayout.FONT_SIZE, HudFonts.MEDIUM);
        float textWidth = nvg.getTextWidth(name + (tag.isEmpty() ? "" : " " + tag),
            RedFixListLayout.FONT_SIZE, HudFonts.MEDIUM);
        Entry result = new Entry(id, name, tag, color, nameWidth, textWidth);
        measurements.put(id, result);
        return result;
    }

    /** edge is the common right edge (right alignment), otherwise the left edge. */
    public void draw(NanoVGContext nvg, float edge, float top, float scale)
    {
        for(Row row : rows)
        {
            Entry e = row.entry();
            float x = edge + row.x(lastFrame) * scale;
            float y = top + row.y(lastFrame) * scale;
            float w = e.width() * scale, h = RedFixListLayout.HEIGHT * scale;
            nvg.save();
            try
            {
                nvg.setAlpha(row.alpha(lastFrame));
                nvg.drawHorizontalGradientRoundedRect(x, y, w, h, 2 * scale,
                    0x9E000000 | e.color(), 0x1A000000 | e.color());
                nvg.save();
                nvg.scissor(x, y, 1.5F * scale, h);
                nvg.drawRoundedRect(x, y, w, h, 2 * scale, 0xFF000000 | e.color());
                nvg.restore();
                // CSS padding-left 8px + border 3px; normal 17px line box.
                float tx = x + 5.5F * scale, ty = y + 2.75F * scale;
                nvg.drawText(e.name(), tx, ty, 0xFFFFFFFF,
                    RedFixListLayout.FONT_SIZE * scale, HudFonts.MEDIUM);
                if(!e.tag().isEmpty())
                    nvg.drawText(" " + e.tag(), tx + e.nameWidth() * scale, ty,
                        0xFFAAAAAA, RedFixListLayout.FONT_SIZE * scale, HudFonts.MEDIUM);
            }finally { nvg.restore(); }
        }
    }

    public float width() { return width; }
    public float height() { return height; }
    public boolean hasRows() { return !rows.isEmpty(); }
}
