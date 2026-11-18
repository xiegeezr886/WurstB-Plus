/* AutoLogin account storage adapted from LeavesHack 13daf96.
 * SPDX-License-Identifier: AGPL-3.0-or-later */
package net.wurstclient.leaves;

import com.google.gson.*;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

/** Accounts are scoped to both the exact server address and username. */
public final class LoginAccounts {
  public record Account(String server, String username, String password) {}

  private final Path file;
  private final LinkedHashMap<String, Account> accounts = new LinkedHashMap<>();

  public LoginAccounts(Path file) {
    this.file = file;
  }

  public static String normalizeServer(String value) {
    String server = value.trim().toLowerCase(Locale.ROOT);
    if (server.endsWith(":25565")) server = server.substring(0, server.length() - 6);
    return server;
  }

  private static String key(String server, String username) {
    return normalizeServer(server) + "\n" + username;
  }

  public synchronized Account find(String server, String username) {
    return accounts.get(key(server, username));
  }

  public synchronized List<Account> list() {
    return List.copyOf(accounts.values());
  }

  public synchronized void load() throws IOException {
    if (!Files.exists(file)) return;
    LinkedHashMap<String, Account> loaded = new LinkedHashMap<>();
    try {
      JsonArray array =
          JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8)).getAsJsonArray();
      for (JsonElement element : array) {
        JsonObject object = element.getAsJsonObject();
        Account account =
            new Account(
                normalizeServer(object.get("server").getAsString()),
                object.get("username").getAsString(),
                object.get("password").getAsString());
        if (valid(account)) loaded.put(key(account.server(), account.username()), account);
      }
    } catch (RuntimeException e) {
      throw new IOException("Invalid AutoLogin account file");
    }
    accounts.clear();
    accounts.putAll(loaded);
  }

  public synchronized void put(Account account) throws IOException {
    if (!valid(account)) throw new IllegalArgumentException("Invalid account fields");
    String id = key(account.server(), account.username());
    Account old = accounts.put(id, account);
    try {
      save();
    } catch (IOException e) {
      if (old == null) accounts.remove(id);
      else accounts.put(id, old);
      throw e;
    }
  }

  public synchronized void remove(Account account) throws IOException {
    String id = key(account.server(), account.username());
    Account old = accounts.remove(id);
    try {
      save();
    } catch (IOException e) {
      if (old != null) accounts.put(id, old);
      throw e;
    }
  }

  public static boolean valid(Account account) {
    return account != null
        && !account.server().isBlank()
        && !account.username().isBlank()
        && !account.password().isBlank()
        && !account.server().matches("(?s).*\\s.*")
        && !account.username().matches("(?s).*\\s.*")
        && !account.password().matches("(?s).*\\s.*");
  }

  private void save() throws IOException {
    Files.createDirectories(file.toAbsolutePath().getParent());
    JsonArray array = new JsonArray();
    for (Account account : accounts.values()) {
      JsonObject object = new JsonObject();
      object.addProperty("server", account.server());
      object.addProperty("username", account.username());
      object.addProperty("password", account.password());
      array.add(object);
    }
    Path temporary = Files.createTempFile(file.toAbsolutePath().getParent(), "autologin-", ".tmp");
    try {
      Files.writeString(
          temporary,
          new GsonBuilder().setPrettyPrinting().create().toJson(array),
          StandardCharsets.UTF_8);
      try {
        Files.move(
            temporary, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
      } catch (AtomicMoveNotSupportedException e) {
        Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING);
      }
    } finally {
      Files.deleteIfExists(temporary);
    }
  }
}
