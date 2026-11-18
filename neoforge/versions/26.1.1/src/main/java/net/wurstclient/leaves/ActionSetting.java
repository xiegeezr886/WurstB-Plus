/* SPDX-License-Identifier: AGPL-3.0-or-later */
package net.wurstclient.leaves;

import com.google.gson.*;
import java.util.Set;
import net.wurstclient.clickgui2.Component;
import net.wurstclient.clickgui2.components.AbstractListEditButton;
import net.wurstclient.keybinds.PossibleKeybind;
import net.wurstclient.settings.Setting;
import net.wurstclient.util.text.WText;

/** A settings-panel action without persisted state. */
public class ActionSetting extends Setting {
  private final Runnable action;

  public ActionSetting(String name, String description, Runnable action) {
    super(name, WText.translated(description));
    this.action = action;
  }

  @Override
  public Component getComponent() {
    return new AbstractListEditButton() {
      {
        setWidth(getDefaultWidth());
        setHeight(getDefaultHeight());
      }

      @Override
      protected void openScreen() {
        action.run();
      }

      @Override
      protected String getText() {
        return getTranslatedName();
      }

      @Override
      protected Setting getSetting() {
        return ActionSetting.this;
      }
    };
  }

  @Override
  public void fromJson(JsonElement json) {}

  @Override
  public JsonElement toJson() {
    return JsonNull.INSTANCE;
  }

  @Override
  public JsonObject exportWikiData() {
    JsonObject result = new JsonObject();
    result.addProperty("name", getName());
    result.addProperty("description", getDescription());
    result.addProperty("type", "action");
    return result;
  }

  @Override
  public Set<PossibleKeybind> getPossibleKeybinds(String featureName) {
    return Set.of();
  }
}
