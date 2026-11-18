package net.wurstclient.leaves;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class LoginAccountsTest {
  @TempDir Path directory;

  @Test
  void scopesPasswordsToServerAndExactUsernameAndPersistsChanges() throws Exception {
    Path file = directory.resolve("accounts.json");
    LoginAccounts store = new LoginAccounts(file);
    var a = new LoginAccounts.Account("Example.COM:25565", "Alice", "one");
    var b = new LoginAccounts.Account("other.example", "Alice", "two");
    store.put(a);
    store.put(b);
    store.put(new LoginAccounts.Account("example.com", "Bob", "three"));
    LoginAccounts reloaded = new LoginAccounts(file);
    reloaded.load();
    assertEquals("one", reloaded.find("example.com", "Alice").password());
    assertEquals("two", reloaded.find("other.example:25565", "Alice").password());
    assertEquals("three", reloaded.find("example.com", "Bob").password());
    assertNull(reloaded.find("example.com", "alice"));
    reloaded.remove(a);
    reloaded.load();
    assertNull(reloaded.find("example.com", "Alice"));
    assertEquals(2, reloaded.list().size());
  }

  @Test
  void invalidFileDoesNotOverwriteExistingData() throws Exception {
    Path file = directory.resolve("accounts.json");
    Files.writeString(file, "broken");
    assertThrows(java.io.IOException.class, () -> new LoginAccounts(file).load());
    assertEquals("broken", Files.readString(file));
  }

  @Test
  void rejectsCommandInjectionAndEmptyFields() {
    assertFalse(
        LoginAccounts.valid(new LoginAccounts.Account("server", "user", "password\ncommand")));
    assertFalse(LoginAccounts.valid(new LoginAccounts.Account("server", "", "secret")));
    assertTrue(LoginAccounts.valid(new LoginAccounts.Account("[::1]:25566", "user", "secret")));
  }
}
