/* SPDX-License-Identifier: AGPL-3.0-or-later */
package net.wurstclient.leaves;

import com.google.gson.*;
import java.util.*;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.enchantment.Enchantment;
import net.wurstclient.WurstClient;
import net.wurstclient.clickgui2.components.AbstractListEditButton;
import net.wurstclient.keybinds.PossibleKeybind;
import net.wurstclient.settings.Setting;
import net.wurstclient.util.text.WText;

public final class EnchantmentListSetting extends Setting {
  private final Set<String> selected = new LinkedHashSet<>();

  public EnchantmentListSetting(String name) {
    super(name, WText.translated("Select the enchantments to look for."));
  }

  public boolean contains(Enchantment value) {
    return selected.contains(BuiltInRegistries.ENCHANTMENT.getKey(value).toString());
  }

  @Override
  public net.wurstclient.clickgui2.Component getComponent() {
    return new AbstractListEditButton() {
      {
        setWidth(getDefaultWidth());
        setHeight(getDefaultHeight());
      }

      @Override
      protected void openScreen() {
        MC.setScreen(new Editor(MC.screen));
      }

      @Override
      protected String getText() {
        return getTranslatedName() + ": " + selected.size();
      }

      @Override
      protected Setting getSetting() {
        return EnchantmentListSetting.this;
      }
    };
  }

  @Override
  public void fromJson(JsonElement value) {
    selected.clear();
    if (!value.isJsonArray()) return;
    for (JsonElement item : value.getAsJsonArray())
      if (item.isJsonPrimitive()) selected.add(item.getAsString());
  }

  @Override
  public JsonElement toJson() {
    JsonArray array = new JsonArray();
    selected.forEach(array::add);
    return array;
  }

  @Override
  public JsonObject exportWikiData() {
    JsonObject result = new JsonObject();
    result.addProperty("name", getName());
    result.addProperty("description", getDescription());
    result.addProperty("type", "enchantment_list");
    return result;
  }

  @Override
  public Set<PossibleKeybind> getPossibleKeybinds(String feature) {
    return Set.of();
  }

  private final class Editor extends Screen {
    private final Screen parent;
    private String query = "";
    private int page;

    Editor(Screen parent) {
      super(Component.literal(getTranslatedName()));
      this.parent = parent;
    }

    @Override
    protected void init() {
      EditBox search =
          new EditBox(
              font,
              width / 2 - 150,
              30,
              300,
              20,
              Component.literal(
                  net.wurstclient.WurstClient.INSTANCE.translate("gui.wurst.leaves.search")));
      search.setValue(query);
      addRenderableWidget(search);
      List<Enchantment> values =
          BuiltInRegistries.ENCHANTMENT.stream()
              .filter(
                  e ->
                      BuiltInRegistries.ENCHANTMENT
                              .getKey(e)
                              .toString()
                              .contains(query.toLowerCase(Locale.ROOT))
                          || e.getFullname(1).getString().contains(query))
              .toList();
      int rows = Math.max(1, (height - 110) / 24),
          pages = Math.max(1, (values.size() + rows - 1) / rows);
      page = Math.min(page, pages - 1);
      for (int i = page * rows; i < Math.min(values.size(), (page + 1) * rows); i++) {
        Enchantment value = values.get(i);
        String id = BuiltInRegistries.ENCHANTMENT.getKey(value).toString();
        addRenderableWidget(
            Button.builder(
                    Component.literal(
                        (selected.contains(id) ? "[x] " : "[ ] ")
                            + value.getFullname(1).getString()),
                    button -> {
                      if (!selected.remove(id)) selected.add(id);
                      notifyChanged();
                      WurstClient.INSTANCE.saveSettings();
                      rebuildWidgets();
                    })
                .bounds(width / 2 - 150, 58 + (i - page * rows) * 24, 300, 20)
                .build());
      }
      search.setResponder(
          text -> {
            query = text;
            page = 0;
            rebuildWidgets();
          });
      setInitialFocus(search);
      addRenderableWidget(
          Button.builder(
                  Component.literal("<"),
                  b -> {
                    page = Math.max(0, page - 1);
                    rebuildWidgets();
                  })
              .bounds(width / 2 - 150, height - 35, 40, 20)
              .build());
      addRenderableWidget(
          Button.builder(Component.translatable("gui.done"), b -> onClose())
              .bounds(width / 2 - 100, height - 35, 200, 20)
              .build());
      addRenderableWidget(
          Button.builder(
                  Component.literal(">"),
                  b -> {
                    page = Math.min(pages - 1, page + 1);
                    rebuildWidgets();
                  })
              .bounds(width / 2 + 110, height - 35, 40, 20)
              .build());
    }

    @Override
    public void render(GuiGraphics g, int x, int y, float delta) {
      renderBackground(g);
      g.drawCenteredString(font, title, width / 2, 12, 0xFFFFFFFF);
      super.render(g, x, y, delta);
    }

    @Override
    public void onClose() {
      minecraft.setScreen(parent);
    }
  }
}
