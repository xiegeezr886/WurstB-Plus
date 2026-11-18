/* AutoLogin adapted from LeavesHack 13daf96.
 * SPDX-License-Identifier: AGPL-3.0-or-later */
package net.wurstclient.hacks;

import java.io.IOException;
import net.wurstclient.Category;
import net.wurstclient.events.*;
import net.wurstclient.hack.Hack;
import net.wurstclient.leaves.*;
import net.wurstclient.settings.*;
import net.wurstclient.settings.SliderSetting.ValueDisplay;
import net.wurstclient.util.ChatUtils;

public final class AutoLoginHack extends Hack implements UpdateListener, ChatOutputListener {
  private final CheckboxSetting autoSave = new CheckboxSetting("AutoSave", true);
  private final TextFieldSetting login = new TextFieldSetting("LoginCommand", "l"),
      register = new TextFieldSetting("RegCommand", "reg"),
      change = new TextFieldSetting("CpCommand", "cp");
  private final SliderSetting delay =
      new SliderSetting("Delay", 40, 0, 200, 1, ValueDisplay.INTEGER.withSuffix(" ticks"));
  private LoginAccounts accounts;
  private Object connection;
  private int remaining;
  private boolean pending;

  public AutoLoginHack() {
    super("AutoLogin");
    setCategory(Category.MISC);
    for (Setting s :
        new Setting[] {
          autoSave,
          login,
          register,
          change,
          delay,
          new ActionSetting(
              "Accounts",
              "Manage passwords saved separately for each server and username.",
              () -> MC.setScreen(new LoginAccountsScreen(MC.screen, getAccounts())))
        }) addSetting(s);
  }

  public LoginAccounts getAccounts() {
    if (accounts == null) {
      LoginAccounts loaded =
          new LoginAccounts(WURST.getWurstFolder().resolve("leaves-autologin.json"));
      try {
        loaded.load();
        accounts = loaded;
      } catch (IOException e) {
        ChatUtils.error(
            "AutoLogin: couldn't read saved accounts. The existing file was preserved.");
        return null;
      }
    }
    return accounts;
  }

  @Override
  protected void onEnable() {
    connection = null;
    pending = false;
    EVENTS.add(UpdateListener.class, this);
    EVENTS.add(ChatOutputListener.class, this);
  }

  @Override
  protected void onDisable() {
    EVENTS.remove(UpdateListener.class, this);
    EVENTS.remove(ChatOutputListener.class, this);
    connection = null;
    pending = false;
  }

  public static String stripSlash(String command) {
    return command.trim().replaceFirst("^/", "");
  }

  private String server() {
    return MC.getCurrentServer() == null
        ? null
        : LoginAccounts.normalizeServer(MC.getCurrentServer().ip);
  }

  @Override
  public void onUpdate() {
    if (MC.getConnection() != connection) {
      connection = MC.getConnection();
      pending = connection != null;
      remaining = delay.getValueI();
    }
    if (!pending || MC.player == null || MC.level == null || server() == null) return;
    if (remaining-- > 0) return;
    pending = false;
    LoginAccounts store = getAccounts();
    if (store == null) return;
    var account = store.find(server(), MC.getUser().getName());
    String command = stripSlash(login.getValue());
    if (account != null && !command.isBlank())
      MC.getConnection().sendCommand(command + " " + account.password());
  }

  @Override
  public void onSentMessage(ChatOutputEvent event) {
    if (!autoSave.isChecked() || server() == null || !event.getOriginalMessage().startsWith("/"))
      return;
    String[] args = stripSlash(event.getOriginalMessage()).split("\\s+");
    String secret =
        capturedPassword(
            args,
            stripSlash(login.getValue()),
            stripSlash(register.getValue()),
            stripSlash(change.getValue()));
    if (secret == null) return;
    LoginAccounts store = getAccounts();
    if (store == null) return;
    try {
      store.put(new LoginAccounts.Account(server(), MC.getUser().getName(), secret));
    } catch (IOException | IllegalArgumentException e) {
      ChatUtils.error("AutoLogin: couldn't save the account.");
    }
  }

  public static String capturedPassword(
      String[] args, String login, String register, String change) {
    if (args.length >= 3 && args[0].equals(change)) return args[2];
    if (args.length >= 2 && (args[0].equals(login) || args[0].equals(register))) return args[1];
    return null;
  }
}
