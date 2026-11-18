/* SPDX-License-Identifier: AGPL-3.0-or-later */
package net.wurstclient.leaves;

import com.google.gson.*;
import java.awt.Color;
import net.wurstclient.settings.ColorSetting;
import net.wurstclient.settings.SliderSetting;
import net.wurstclient.settings.SliderSetting.ValueDisplay;

/** RGBA setting for Leaves renderers; existing Wurst RGB settings keep their behavior. */
public final class LeavesColorSetting extends ColorSetting {
  private final SliderSetting opacity;

  public LeavesColorSetting(String name, Color value) {
    this(name, "", value);
  }

  public LeavesColorSetting(String name, String description, Color value) {
    super(name, description, value);
    opacity =
        new SliderSetting(name + " opacity", value.getAlpha(), 0, 255, 1, ValueDisplay.INTEGER);
    withChildren(opacity);
  }

  @Override
  public int getColorI() {
    return super.getColorI(opacity.getValueI());
  }

  @Override
  public JsonElement toJson() {
    JsonObject value = new JsonObject();
    value.add("rgb", super.toJson());
    value.add("alpha", opacity.toJson());
    return value;
  }

  @Override
  public void fromJson(JsonElement value) {
    if (value.isJsonObject()) {
      JsonObject object = value.getAsJsonObject();
      if (object.has("rgb")) super.fromJson(object.get("rgb"));
      if (object.has("alpha")) opacity.fromJson(object.get("alpha"));
    } else super.fromJson(value);
  }
}
