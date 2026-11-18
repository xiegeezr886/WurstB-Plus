/* RedFix/LiquidBounce ArrayList behavior adapted for the native HUD.
 * SPDX-License-Identifier: GPL-3.0-or-later */
package net.wurstclient.hud2.redfix;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/** Frame-rate independent keyed fly/FLIP transitions in Minecraft GUI units. */
public final class RedFixListLayout
{
    // The reference browser uses GUI scale 2 as its coordinate space.
    public static final float FONT_SIZE = 7, HEIGHT = 12.5F, GAP = 1;
    public static final float PADDING = 10.5F, FLY_DISTANCE = 25;
    private static final Pattern WORD = Pattern.compile("[A-Z]?[a-z]+|[0-9]+|[A-Z]+(?![a-z])");
    private final Map<String, Row> rows = new LinkedHashMap<>();

    public record Entry(String id, String name, String tag, int color,
        float nameWidth, float textWidth)
    {
        public float width() { return textWidth + PADDING; }
    }

    public List<Row> update(List<Entry> entries, boolean ascending,
        boolean right, long now)
    {
        List<Entry> sorted = new ArrayList<>(entries);
        Comparator<Entry> order = Comparator.comparingDouble(Entry::textWidth);
        if(!ascending) order = order.reversed();
        sorted.sort(order);
        rows.values().forEach(row -> row.present = false);
        for(int i = 0; i < sorted.size(); i++)
        {
            Entry entry = sorted.get(i);
            float x = right ? -entry.width() : 0, y = i * (HEIGHT + GAP);
            Row row = rows.computeIfAbsent(entry.id(), key -> new Row(entry, x, y, now));
            row.entry = entry;
            row.present = true;
            row.x.to(x, now);
            row.y.to(y, now);
            row.alpha.to(1, now);
        }
        rows.values().removeIf(row -> {
            if(!row.present) row.alpha.to(0, now);
            return !row.present && row.alpha.value(now) <= 0;
        });
        return List.copyOf(rows.values());
    }

    public void clear() { rows.clear(); }

    public static String spacedName(String name)
    {
        var matcher = WORD.matcher(name);
        List<String> words = new ArrayList<>();
        while(matcher.find()) words.add(matcher.group());
        return words.isEmpty() ? name : String.join(" ", words);
    }

    /** Wurst includes tags in render names; RedFix receives a separate tag. */
    public static String tag(String id, String localizedName, String renderName)
    {
        String clean = renderName.replaceAll("\u00a7.", "");
        String suffix = clean.startsWith(id) ? clean.substring(id.length())
            : clean.startsWith(localizedName) ? clean.substring(localizedName.length()) : "";
        suffix = suffix.trim();
        if(suffix.startsWith("[") && suffix.endsWith("]"))
            suffix = suffix.substring(1, suffix.length() - 1);
        return suffix;
    }

    public static final class Row
    {
        private Entry entry;
        private boolean present;
        private final Motion x, y, alpha;
        Row(Entry entry, float x, float y, long now)
        {
            this.entry = entry;
            this.x = new Motion(x, now);
            this.y = new Motion(y, now);
            alpha = new Motion(0, now);
        }
        public Entry entry() { return entry; }
        public float alpha(long now) { return alpha.value(now); }
        public float x(long now) { return x.value(now) + FLY_DISTANCE * (1 - alpha(now)); }
        public float y(long now) { return y.value(now); }
    }

    private static final class Motion
    {
        private float start, target;
        private long since;
        Motion(float value, long now) { start = target = value; since = now; }
        float value(long now)
        {
            float t = Math.max(0, Math.min(1, (now - since) / 200F));
            float ease = 1 - (1 - t) * (1 - t) * (1 - t);
            return start + (target - start) * ease;
        }
        void to(float value, long now)
        {
            if(value == target) return;
            start = value(now); target = value; since = now;
        }
    }
}
