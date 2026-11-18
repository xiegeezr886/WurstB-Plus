/* FriendsManager adapted from LeavesHack 13daf96.
 * SPDX-License-Identifier: AGPL-3.0-or-later */
package net.wurstclient.hacks;

import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import net.wurstclient.Category;
import net.wurstclient.events.UpdateListener;
import net.wurstclient.hack.Hack;
import net.wurstclient.leaves.ActionSetting;
import net.wurstclient.settings.*;
import net.wurstclient.util.ChatUtils;

public final class FriendsManagerHack extends Hack implements UpdateListener {
  private final CheckboxSetting debug = new CheckboxSetting("DeBug", false),
      alien = new CheckboxSetting("Alien", true),
      hachimi = new CheckboxSetting("Hachimi", false),
      mio = new CheckboxSetting("Mio", false);
  private final TextFieldSetting prefix = new TextFieldSetting("Prefix", ";"),
      hachimiPrefix = new TextFieldSetting("HachimiPrefix", ";"),
      mioPrefix = new TextFieldSetting("MioPrefix", "."),
      add = new TextFieldSetting("AddFCommand", "friend add"),
      remove = new TextFieldSetting("RemoveFCommand", "friend remove"),
      file =
          new TextFieldSetting(
              "TXT path", "UTF-8 text file containing one friend name per line.", "");
  private Set<String> previous = Set.of();
  private final ArrayDeque<String> commands = new ArrayDeque<>();
  private Object connection;

  public FriendsManagerHack() {
    super("FriendsManager");
    setCategory(Category.MISC);
    for (Setting s :
        new Setting[] {
          debug,
          prefix,
          hachimiPrefix,
          mioPrefix,
          alien,
          hachimi,
          mio,
          add,
          remove,
          file,
          new ActionSetting(
              "Sync Friends", "Send the friend list to the selected clients.", this::sync),
          new ActionSetting(
              "Import TXT", "Import one friend name per line from TXT path.", this::importFile)
        }) addSetting(s);
  }

  @Override
  protected void onEnable() {
    previous = new LinkedHashSet<>(WURST.getFriends().toList());
    connection = MC.getConnection();
    EVENTS.add(UpdateListener.class, this);
  }

  @Override
  protected void onDisable() {
    commands.clear();
    EVENTS.remove(UpdateListener.class, this);
  }

  @Override
  public void onUpdate() {
    if (connection != MC.getConnection()) {
      commands.clear();
      connection = MC.getConnection();
    }
    Set<String> now = new LinkedHashSet<>(WURST.getFriends().toList());
    for (String name : difference(previous, now)) enqueue(remove.getValue(), name);
    for (String name : difference(now, previous)) enqueue(add.getValue(), name);
    previous = now;
    if (MC.player != null
        && MC.getConnection() != null
        && !commands.isEmpty()
        && MC.player.tickCount % 2 == 0) {
      String command = commands.removeFirst();
      if (debug.isChecked()) ChatUtils.message("FriendsManager: %s", command);
      MC.getConnection().sendChat(command);
    }
  }

  public static Set<String> difference(Set<String> a, Set<String> b) {
    Set<String> result = new LinkedHashSet<>(a);
    result.removeAll(b);
    return result;
  }

  private void enqueue(String command, String name) {
    if (MC.getConnection() == null) return;
    Set<String> messages = new LinkedHashSet<>();
    if (alien.isChecked()) messages.add(prefix.getValue() + command + " " + name);
    if (hachimi.isChecked()) messages.add(hachimiPrefix.getValue() + command + " " + name);
    if (mio.isChecked())
      messages.add(
          mioPrefix.getValue()
              + command
              + " "
              + (name.codePoints().anyMatch(c -> c >= 0x4E00 && c <= 0x9FFF)
                  ? "\"" + name + "\""
                  : name));
    for (String message : messages)
      if (!message.contains("\n") && !message.contains("\r") && commands.size() < 4096)
        commands.addLast(message);
  }

  private void sync() {
    if (!isEnabled()) setEnabled(true);
    for (String name : WURST.getFriends().toList()) enqueue(add.getValue(), name);
    ChatUtils.message("FriendsManager: queued friend synchronization.");
  }

  private void importFile() {
    try {
      Path path = Path.of(file.getValue());
      if (Files.size(path) > 1_048_576) {
        ChatUtils.error("FriendsManager: TXT exceeds 1 MiB.");
        return;
      }
      Set<String> names = new LinkedHashSet<>(Files.readAllLines(path, StandardCharsets.UTF_8));
      int imported = 0;
      for (String line : names) {
        String name = line.replace("\uFEFF", "").trim();
        if (name.isEmpty() || name.length() > 64 || name.chars().anyMatch(Character::isISOControl))
          continue;
        if (!WURST.getFriends().contains(name)) {
          WURST.getFriends().addAndSave(name);
          imported++;
        }
      }
      ChatUtils.message("FriendsManager: imported %s friends.", imported);
    } catch (Exception e) {
      ChatUtils.error("FriendsManager: couldn't read the TXT file.");
    }
  }
}
