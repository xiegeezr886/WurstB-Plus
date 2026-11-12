package net.wurstclient.util;

import static org.junit.jupiter.api.Assertions.*;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class BundledNativeFilesTest
{
	private static final String RESOURCE = "net/wurstclient/util/BundledNativeFiles.class";

	private byte[] bundled() throws IOException
	{
		try(var input = BundledNativeFiles.class.getModule().getResourceAsStream(RESOURCE))
		{
			return input.readAllBytes();
		}
	}

	@Test
	void corruptCacheIsReplacedWithPinnedModuleBytes(@TempDir Path temp) throws Exception
	{
		byte[] bytes = bundled();
		String digest = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
		Path target = temp.resolve("native.dll");
		Files.writeString(target, "corrupted cache");
		BundledNativeFiles.extract(BundledNativeFiles.class, RESOURCE, digest, target);
		assertArrayEquals(bytes, Files.readAllBytes(target));
		try(var files = Files.list(temp)) { assertEquals(1, files.count()); }
	}

	@Test
	void incorrectTrustedDigestNeverReplacesExistingFile(@TempDir Path temp) throws Exception
	{
		Path target = temp.resolve("native.dll");
		Files.writeString(target, "existing");
		assertThrows(IOException.class, () -> BundledNativeFiles.extract(
			BundledNativeFiles.class, RESOURCE, "0".repeat(64), target));
		assertEquals("existing", Files.readString(target));
		try(var files = Files.list(temp)) { assertEquals(1, files.count()); }
	}

	@Test
	void missingModuleResourceCannotBeSubstitutedByCache(@TempDir Path temp) throws Exception
	{
		Path target = temp.resolve("native.dll");
		assertThrows(IOException.class, () -> BundledNativeFiles.extract(
			BundledNativeFiles.class, "missing/native.dll", "0".repeat(64), target));
		assertFalse(Files.exists(target));
	}
}
