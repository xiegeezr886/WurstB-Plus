package net.wurstclient.altmanager;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Path;
import java.util.List;
import net.wurstclient.altmanager.credentials.CredentialStore;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class AltManagerClearTest
{
	@TempDir Path directory;

	@Test
	void clearPersistsAnEmptyListAndKeepsCountersConsistent() throws Exception
	{
		Path file = directory.resolve("accounts.json");
		Path keys = directory.resolve("keys");
		Encryption encryption = new Encryption(keys, new CredentialStore() {
			private String value;
			@Override public String read(String service, String account) { return value; }
			@Override public void write(String service, String account, String value) { this.value = value; }
		});
		AltManager manager = new AltManager(new AltsFile(file, keys, encryption));
		manager.addAll(List.of(new CrackedAlt("MoonTest", true),
			new MojangAlt("test@example.invalid", "test-only", "PremiumTest", false)));
		assertEquals(2, new AltManager(new AltsFile(file, keys, encryption)).getList().size());
		manager.clear();
		assertTrue(manager.getList().isEmpty());
		assertEquals(0, manager.getNumPremium());
		assertEquals(0, manager.getNumCracked());
		assertTrue(new AltManager(new AltsFile(file, keys, encryption)).getList().isEmpty());
		manager.clear();
		manager.add(new CrackedAlt("AfterClear"));
		AltManager reopened = new AltManager(new AltsFile(file, keys, encryption));
		assertEquals("AfterClear", reopened.getList().get(0).getName());
		assertEquals(1, reopened.getNumCracked());
	}
}
