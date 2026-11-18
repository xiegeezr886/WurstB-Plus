/* SPDX-License-Identifier: AGPL-3.0-or-later */
package net.wurstclient.leaves;

import java.io.IOException;
import net.wurstclient.util.render.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public final class LoginAccountsScreen extends Screen {
  private final Screen parent;
  private final LoginAccounts accounts;
  private int page;
  private String status = "";

  public LoginAccountsScreen(Screen parent, LoginAccounts accounts) {
    super(
        Component.literal(
            net.wurstclient.WurstClient.INSTANCE.translate("gui.wurst.leaves.accounts")));
    this.parent = parent;
    this.accounts = accounts;
  }

  @Override
  protected void init() {
    if (accounts == null) {
      onClose();
      return;
    }
    var entries = accounts.list();
    int rows = Math.max(1, (height - 130) / 24),
        pages = Math.max(1, (entries.size() + rows - 1) / rows);
    page = Math.min(page, pages - 1);
    for (int i = page * rows; i < Math.min(entries.size(), (page + 1) * rows); i++) {
      var account = entries.get(i);
      int y = 40 + (i - page * rows) * 24;
      addRenderableWidget(
          Button.builder(
                  Component.literal(account.username() + " @ " + account.server()),
                  b -> minecraft.setScreenAndShow(new Editor(account)))
              .bounds(width / 2 - 160, y, 270, 20)
              .build());
      addRenderableWidget(
          Button.builder(
                  Component.literal("×"),
                  b -> {
                    try {
                      accounts.remove(account);
                      rebuildWidgets();
                    } catch (IOException e) {
                      status =
                          net.wurstclient.WurstClient.INSTANCE.translate(
                              "gui.wurst.leaves.save_failed");
                    }
                  })
              .bounds(width / 2 + 115, y, 45, 20)
              .build());
    }
    addRenderableWidget(
        Button.builder(
                Component.literal("<"),
                b -> {
                  page = Math.max(0, page - 1);
                  rebuildWidgets();
                })
            .bounds(width / 2 - 160, height - 65, 45, 20)
            .build());
    addRenderableWidget(
        Button.builder(
                Component.literal(
                    net.wurstclient.WurstClient.INSTANCE.translate("gui.wurst.leaves.add_account")),
                b -> minecraft.setScreenAndShow(new Editor(null)))
            .bounds(width / 2 - 110, height - 65, 220, 20)
            .build());
    addRenderableWidget(
        Button.builder(
                Component.literal(">"),
                b -> {
                  page = Math.min(pages - 1, page + 1);
                  rebuildWidgets();
                })
            .bounds(width / 2 + 115, height - 65, 45, 20)
            .build());
    addRenderableWidget(
        Button.builder(Component.translatable("gui.done"), b -> onClose())
            .bounds(width / 2 - 100, height - 35, 200, 20)
            .build());
  }

  @Override
  public void onClose() {
    minecraft.setScreenAndShow(parent);
  }

  @Override public void extractRenderState(net.minecraft.client.gui.GuiGraphicsExtractor g, int x, int y, float delta) { render(new GuiGraphicsExtractor(g), x, y, delta); }
public void render(GuiGraphicsExtractor g, int x, int y, float delta) {
    extractBackground(g.getInner(), x, y, delta);
    g.centeredText(font, title, width / 2, 12, -1);
    g.centeredText(font, status, width / 2, height - 85, 0xFFFF6666);
    super.extractRenderState(g.getInner(), x, y, delta);
  }

  private final class Editor extends Screen {
    private final LoginAccounts.Account original;
    private EditBox server, username, password;
    private String error = "";

    Editor(LoginAccounts.Account original) {
      super(
          Component.literal(
              net.wurstclient.WurstClient.INSTANCE.translate("gui.wurst.leaves.edit_account")));
      this.original = original;
    }

    @Override
    protected void init() {
      server = field(60, "Server");
      username = field(100, "Username");
      password = field(140, "Password");
      password.addFormatter(
          (text, offset) -> Component.literal("*".repeat(text.length())).getVisualOrderText());
      server.setValue(
          original != null
              ? original.server()
              : minecraft.getCurrentServer() != null ? minecraft.getCurrentServer().ip : "");
      username.setValue(original != null ? original.username() : minecraft.getUser().getName());
      password.setValue(original != null ? original.password() : "");
      addRenderableWidget(
          Button.builder(
                  Component.translatable("gui.done"),
                  b -> {
                    var entry =
                        new LoginAccounts.Account(
                            LoginAccounts.normalizeServer(server.getValue()),
                            username.getValue().trim(),
                            password.getValue());
                    if (!LoginAccounts.valid(entry)) {
                      error =
                          net.wurstclient.WurstClient.INSTANCE.translate(
                              "gui.wurst.leaves.invalid");
                      return;
                    }
                    try {
                      accounts.put(entry);
                      if (original != null
                          && (!original.server().equals(entry.server())
                              || !original.username().equals(entry.username())))
                        accounts.remove(original);
                      onClose();
                    } catch (IOException e) {
                      error =
                          net.wurstclient.WurstClient.INSTANCE.translate(
                              "gui.wurst.leaves.save_failed");
                    }
                  })
              .bounds(width / 2 - 155, 185, 150, 20)
              .build());
      addRenderableWidget(
          Button.builder(Component.translatable("gui.cancel"), b -> onClose())
              .bounds(width / 2 + 5, 185, 150, 20)
              .build());
    }

    private EditBox field(int y, String name) {
      EditBox box = new EditBox(font, width / 2 - 150, y, 300, 20, Component.literal(name));
      box.setMaxLength(256);
      return addRenderableWidget(box);
    }

    @Override
    public void onClose() {
      minecraft.setScreenAndShow(LoginAccountsScreen.this);
    }

    @Override public void extractRenderState(net.minecraft.client.gui.GuiGraphicsExtractor g, int x, int y, float delta) { render(new GuiGraphicsExtractor(g), x, y, delta); }
public void render(GuiGraphicsExtractor g, int x, int y, float delta) {
      extractBackground(g.getInner(), x, y, delta);
      g.centeredText(font, title, width / 2, 15, -1);
      g.text(
          font,
          Component.literal(
              net.wurstclient.WurstClient.INSTANCE.translate("gui.wurst.leaves.server")),
          width / 2 - 150,
          48,
          -1);
      g.text(
          font,
          Component.literal(
              net.wurstclient.WurstClient.INSTANCE.translate("gui.wurst.leaves.username")),
          width / 2 - 150,
          88,
          -1);
      g.text(
          font,
          Component.literal(
              net.wurstclient.WurstClient.INSTANCE.translate("gui.wurst.leaves.password")),
          width / 2 - 150,
          128,
          -1);
      g.centeredText(
          font,
          Component.literal(
              net.wurstclient.WurstClient.INSTANCE.translate("gui.wurst.leaves.plaintext")),
          width / 2,
          166,
          0xFFAAAAAA);
      g.centeredText(font, error, width / 2, 213, 0xFFFF6666);
      super.extractRenderState(g.getInner(), x, y, delta);
    }
  }
}
