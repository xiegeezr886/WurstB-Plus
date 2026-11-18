/*
 * Copyright (c) 2026 WurstB+ Plus contributors
 *
 * This source code is subject to the terms of the GNU Affero General Public
 * License, version 3. If a copy of the AGPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/agpl-3.0.txt
 */
// Ported from Soar Client v7.1.2 (LGPL-3.0).
package net.wurstclient.hud2.mods;

import java.awt.Color;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.wurstclient.hud2.HUDMod;
import net.wurstclient.hud2.compat.HudCompat;
import net.wurstclient.hud2.compat.HudItemIcons;
import net.wurstclient.hud2.nanovg.HudFonts;
import net.wurstclient.settings.CheckboxSetting;

public final class ArmorStatusMod extends HUDMod {
  private final CheckboxSetting durability =
      new CheckboxSetting("Durability", "Shows each armor piece's remaining durability.", true);
  private final Map<Integer, Row> rows = new LinkedHashMap<>();
  private boolean preview;
  private long lastFrame;

  public ArmorStatusMod() {
    super("ArmorStatus", "Armor Status", "Shows the equipped armor and its durability.");
    setTranslationKey("armor_status");
    setDefaultPosition(10, 366);
    addSetting(durability);
  }

  @Override
  protected void onDisable() {
    rows.clear();
    lastFrame = 0;
  }

  @Override
  protected void draw() {
    long now = System.nanoTime() / 1_000_000;
    List<ItemStack> items = HudCompat.armorItems();
    boolean nextPreview = isEditing() && items.stream().allMatch(ItemStack::isEmpty);
    if (nextPreview != preview || now - lastFrame > 1000 || MC.player == null && !isEditing())
      rows.clear();
    preview = nextPreview;
    lastFrame = now;
    if (preview)
      items =
          List.of(
              new ItemStack(Items.DIAMOND_HELMET),
              new ItemStack(Items.DIAMOND_CHESTPLATE),
              new ItemStack(Items.DIAMOND_LEGGINGS),
              new ItemStack(Items.DIAMOND_BOOTS));
    float cardHeight = durability.isChecked() ? 32 : 22;
    for (Row row : rows.values()) row.present = false;
    int index = 0;
    for (int slot = 0; slot < items.size(); slot++) {
      ItemStack stack = items.get(slot);
      if (stack.isEmpty()) continue;
      float y = 4 + index++ * (cardHeight + 4);
      Row row = rows.get(slot);
      if (row == null) {
        row = new Row(stack.copy(), y, now);
        rows.put(slot, row);
      }
      if (!ItemStack.matches(row.stack, stack)) row.stack = stack.copy();
      row.present = true;
      row.move(y, now);
      row.show(true, now);
    }
    float width = 0, height = 0;
    var iterator = rows.values().iterator();
    while (iterator.hasNext()) {
      Row row = iterator.next();
      row.show(row.present, now);
      float alpha = row.alpha(now);
      if (!row.present && alpha <= .001F) {
        iterator.remove();
        continue;
      }
      ItemStack stack = row.stack;
      String name = stack.getHoverName().getString();
      int maximum = stack.getMaxDamage();
      int remaining = Math.max(0, maximum - stack.getDamageValue());
      int accent = durabilityColor(maximum <= 0 ? 1 : remaining / (float) maximum);
      String detail =
          maximum > 0
              ? remaining + " / " + maximum + "  ·  " + Math.round(remaining * 100F / maximum) + "%"
              : "—";
      float textWidth = getTextWidth(name, 9, HudFonts.DEMIBOLD);
      if (durability.isChecked())
        textWidth = Math.max(textWidth, getTextWidth(detail, 8, HudFonts.DEMIBOLD));
      float cardWidth = 42 + textWidth, x = 6 - 6 * (1 - alpha), y = row.y(now), scale = getScale();
      width = Math.max(width, cardWidth + 12);
      height = Math.max(height, y + cardHeight + 4);
      nvg.save();
      try {
        nvg.setAlpha(alpha);
        OpaiStatusCard.draw(
            nvg,
            getX() + x * scale,
            getY() + y * scale,
            cardWidth * scale,
            cardHeight * scale,
            scale,
            accent);
        if (stack.isEnchanted())
          OpaiStatusCard.enchanted(
              nvg,
              getX() + x * scale,
              getY() + y * scale,
              cardWidth * scale,
              cardHeight * scale,
              scale,
              now);
        drawArmorIcon(stack, x + 8, y + (cardHeight - 16) / 2);
        drawText(
            name,
            x + 33,
            durability.isChecked() ? y + 5.5F : y + (cardHeight - 9) / 2,
            9,
            HudFonts.DEMIBOLD,
            new Color(accent, true));
        if (durability.isChecked())
          drawText(detail, x + 33, y + 18, 8, HudFonts.DEMIBOLD, new Color(accent, true));
      } finally {
        nvg.restore();
      }
    }
    setSize((int) Math.ceil(width), (int) Math.ceil(height));
    if (!rows.isEmpty()) markDrawn();
  }

  private void drawArmorIcon(ItemStack stack, float x, float y) {
    int image = HudItemIcons.get(stack);
    if (image > 0) {
      drawImage(image, x, y, 16, 16);
      return;
    }
    // Use the actual item model's sprite while the full GUI render is queued/unavailable.
    var sprite = MC.getItemRenderer().getModel(stack, MC.level, MC.player, 0).getParticleIcon();
    int texture = MC.getTextureManager().getTexture(net.minecraft.client.renderer.texture.TextureAtlas.LOCATION_BLOCKS).getId();
    int color = 0xFFFFFFFF;
    float scale = getScale();
    nvg.drawImageRegion(
        texture,
        getX() + x * scale,
        getY() + y * scale,
        16 * scale,
        16 * scale,
        sprite.getU0(),
        sprite.getV0(),
        sprite.getU1(),
        sprite.getV1(),
        color);
  }

  private static int durabilityColor(float fraction) {
    float value = Math.max(0, Math.min(1, fraction));
    int low = 0xFFFF756F, middle = 0xFFF3CF78, full = 0xFF97DDB0;
    int from = value <= .5F ? low : middle, to = value <= .5F ? middle : full;
    float t = value <= .5F ? value * 2 : (value - .5F) * 2;
    int r = Math.round((from >> 16 & 255) + ((to >> 16 & 255) - (from >> 16 & 255)) * t);
    int g = Math.round((from >> 8 & 255) + ((to >> 8 & 255) - (from >> 8 & 255)) * t);
    int b = Math.round((from & 255) + ((to & 255) - (from & 255)) * t);
    return 0xFF000000 | r << 16 | g << 8 | b;
  }

  private static float ease(long now, long since) {
    float t = Math.max(0, Math.min(1, (now - since) / 180F));
    return 1 - (1 - t) * (1 - t) * (1 - t);
  }

  private static final class Row {
    ItemStack stack;
    boolean present, visible = true;
    float fromAlpha, fromY, targetY;
    long faded, moved;

    Row(ItemStack stack, float y, long now) {
      this.stack = stack;
      fromY = targetY = y;
      faded = moved = now;
    }

    float alpha(long now) {
      return fromAlpha + ((visible ? 1 : 0) - fromAlpha) * ease(now, faded);
    }

    float y(long now) {
      return fromY + (targetY - fromY) * ease(now, moved);
    }

    void show(boolean value, long now) {
      if (visible == value) return;
      fromAlpha = alpha(now);
      visible = value;
      faded = now;
    }

    void move(float value, long now) {
      if (targetY == value) return;
      fromY = y(now);
      targetY = value;
      moved = now;
    }
  }
}
