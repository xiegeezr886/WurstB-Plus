/*
 * OpaiBounce Effects.svelte adapted for the Fabric HUD framework.
 * See META-INF/licenses/opai-effects for upstream attribution and licenses.
 */
package net.wurstclient.hud2.mods;

import java.awt.Color;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.network.chat.Component;
import net.wurstclient.hud2.HUDMod;
import net.wurstclient.hud2.compat.HudCompat;
import net.wurstclient.hud2.compat.HudCompat.EffectInfo;
import net.wurstclient.hud2.compat.HudTextures;
import net.wurstclient.hud2.nanovg.HudFonts;
import net.wurstclient.settings.CheckboxSetting;

public final class PotionStatusMod extends HUDMod
{
    private static final Set<String> ICONS = Set.of("speed", "slowness", "haste",
        "mining_fatigue", "strength", "jump_boost", "nausea", "regeneration",
        "resistance", "fire_resistance", "water_breathing", "invisibility",
        "night_vision", "weakness", "poison", "absorption", "saturation");
    private static final String[] LEVELS = {"I", "II", "III", "IV", "V", "VI", "VII", "VIII", "IX", "X"};
    private final CheckboxSetting showDuration = new CheckboxSetting(
        "Duration", "Shows the remaining effect duration.", true);
    private final Map<String, Row> rows = new LinkedHashMap<>();
    private long lastFrame;
    private boolean preview;

    public PotionStatusMod()
    {
        super("PotionStatus", "Potion Status", "Lists the player's active potion effects.");
        setTranslationKey("potion_status");
        setDefaultPosition(10, 410);
        addSetting(showDuration);
    }

    @Override protected void onDisable() { rows.clear(); lastFrame = 0; }

    @Override protected void draw()
    {
        long now = System.nanoTime() / 1_000_000;
        List<EffectInfo> effects = HudCompat.effects();
        boolean nextPreview = effects.isEmpty() && isEditing();
        if(nextPreview != preview || now - lastFrame > 1000
            || MC.player == null && !isEditing()) rows.clear();
        preview = nextPreview;
        lastFrame = now;
        if(preview) effects = List.of(sample("night_vision", 9580, 0, 0x1f1fa1),
            sample("speed", 1780, 1, 0x7cafc6));
        float cardHeight = showDuration.isChecked() ? 32 : 22;
        for(Row row : rows.values()) row.present = false;
        for(int i = 0; i < effects.size(); i++)
        {
            EffectInfo effect = effects.get(i);
            float targetY = 4 + i * (cardHeight + 4);
            Row row = rows.get(effect.id());
            if(row == null)
            {
                row = new Row(effect, targetY, now);
                rows.put(effect.id(), row);
            }
            row.effect = effect;
            row.present = true;
            row.move(targetY, now);
            row.show(true, now);
        }
        float width = 0, height = 0;
        var iterator = rows.values().iterator();
        while(iterator.hasNext())
        {
            Row row = iterator.next();
            row.show(row.present, now);
            float alpha = row.alpha(now);
            if(!row.present && alpha <= .001F) { iterator.remove(); continue; }
            EffectInfo effect = row.effect;
            String name = effect.name();
            if(effect.amplifier() > 0)
                name += " " + (effect.amplifier() < LEVELS.length
                    ? LEVELS[effect.amplifier()] : Long.toString((long)effect.amplifier() + 1));
            String duration = duration(effect.durationTicks());
            float textWidth = getTextWidth(name, 9, HudFonts.DEMIBOLD);
            if(showDuration.isChecked())
                textWidth = Math.max(textWidth, getTextWidth(duration, 8, HudFonts.DEMIBOLD));
            float cardWidth = 42 + textWidth;
            float y = row.y(now), x = 6 - 6 * (1 - alpha);
            width = Math.max(width, cardWidth + 12);
            height = Math.max(height, y + cardHeight + 4);
            nvg.save();
            try
            {
                nvg.setAlpha(alpha);
                float s = getScale();
                nvg.drawHorizontalGradientRoundedRect(getX() + x * s, getY() + y * s,
                    cardWidth * s, cardHeight * s, 6 * s, tintedBackground(effect.color()), 0x8F121216);
                drawIcon(effect, x + 9, y + (cardHeight - 13.8F) / 2, 13.8F);
                float nameY = showDuration.isChecked() ? y + 5.5F : y + (cardHeight - 9) / 2;
                drawText(name, x + 33, nameY, 9, HudFonts.DEMIBOLD, new Color(effect.color() | 0xFF000000, true));
                if(showDuration.isChecked())
                    drawText(duration, x + 33, y + 18, 8, HudFonts.DEMIBOLD,
                        new Color(effect.durationTicks() >= 0 && effect.durationTicks() < 300
                            ? 0xFFFF6B62 : 0xFFF2F2F2, true));
            }finally { nvg.restore(); }
        }
        setSize((int)Math.ceil(width), (int)Math.ceil(height));
        if(!rows.isEmpty()) markDrawn();
    }

    private void drawIcon(EffectInfo effect, float x, float y, float size)
    {
        String key = effect.id().startsWith("minecraft:") ? effect.id().substring(10) : "";
        if(key.equals("health_boost")) key = "regeneration";
        float s = getScale();
        int color = effect.color() | 0xFF000000;
        if(ICONS.contains(key))
            nvg.drawImage(HudTextures.id("wurstpenguin:opai/potion/" + key + ".png"),
                getX() + x * s, getY() + y * s, size * s, size * s, 0, color);
        else if(effect.iconTexture() > 0)
            nvg.drawImageRegion(effect.iconTexture(), getX() + x * s, getY() + y * s,
                size * s, size * s, effect.iconU0(), effect.iconV0(), effect.iconU1(), effect.iconV1(), color);
    }

    private static EffectInfo sample(String id, int ticks, int amplifier, int color)
    {
        return new EffectInfo(Component.translatable("effect.minecraft." + id).getString(),
            amplifier, ticks, color, -1, 0, 0, 1, 1, "minecraft:" + id);
    }
    private static String duration(int ticks)
    {
        if(ticks < 0) return "*:*";
        int seconds = ticks / 20;
        return seconds / 60 + ":" + (seconds % 60 < 10 ? "0" : "") + seconds % 60;
    }
    private static int tintedBackground(int rgb)
    {
        // CSS color-mix(in srgb, effect 20%, #121216b8), premultiplied alpha.
        float a = .2F + .8F * 184 / 255F;
        int r = Math.round(((rgb >> 16 & 255) * .2F + 18 * .8F * 184 / 255F) / a);
        int g = Math.round(((rgb >> 8 & 255) * .2F + 18 * .8F * 184 / 255F) / a);
        int b = Math.round(((rgb & 255) * .2F + 22 * .8F * 184 / 255F) / a);
        return Math.round(a * 255) << 24 | r << 16 | g << 8 | b;
    }
    private static float ease(long now, long since)
    {
        float t = Math.max(0, Math.min(1, (now - since) / 180F));
        return 1 - (1 - t) * (1 - t) * (1 - t);
    }
    private static final class Row
    {
        EffectInfo effect;
        boolean present, visible = true;
        float fromAlpha, fromY, targetY;
        long faded, moved;
        Row(EffectInfo effect, float y, long now)
        {
            this.effect = effect;
            fromY = targetY = y;
            faded = moved = now;
        }
        float alpha(long now) { return fromAlpha + ((visible ? 1 : 0) - fromAlpha) * ease(now, faded); }
        float y(long now) { return fromY + (targetY - fromY) * ease(now, moved); }
        void show(boolean value, long now)
        {
            if(visible == value) return;
            fromAlpha = alpha(now); visible = value; faded = now;
        }
        void move(float value, long now)
        {
            if(targetY == value) return;
            fromY = y(now); targetY = value; moved = now;
        }
    }
}
