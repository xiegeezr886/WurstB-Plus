/* SPDX-License-Identifier: GPL-3.0-or-later */
package net.wurstclient.util;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/** Native bytes come from the owning module, never Minecraft resource packs. */
public final class BundledNativeFiles
{
	private BundledNativeFiles() {}

	public static void extract(Class<?> owner, String resource, String sha256,
		Path target) throws IOException
	{
		if(sha256 == null)
			throw new IOException("No trusted digest for native " + resource);
		if(Files.isRegularFile(target, LinkOption.NOFOLLOW_LINKS)
			&& sha256.equals(hash(target)))
			return;

		Files.createDirectories(target.toAbsolutePath().getParent());
		Path temporary = Files.createTempFile(target.toAbsolutePath().getParent(),
			target.getFileName().toString(), ".tmp");
		try
		{
			try(InputStream input = owner.getModule().getResourceAsStream(resource))
			{
				if(input == null)
					throw new IOException("Missing bundled native " + resource);
				Files.copy(input, temporary, StandardCopyOption.REPLACE_EXISTING);
			}
			if(!sha256.equals(hash(temporary)))
				throw new IOException("Bundled native digest mismatch: " + resource);
			try
			{
				Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE,
					StandardCopyOption.REPLACE_EXISTING);
			}catch(AtomicMoveNotSupportedException e)
			{
				Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING);
			}
			if(!sha256.equals(hash(target)))
				throw new IOException("Extracted native digest mismatch: " + target);
		}finally
		{
			Files.deleteIfExists(temporary);
		}
	}

	private static String hash(Path file) throws IOException
	{
		try(InputStream input = Files.newInputStream(file))
		{
			MessageDigest digest = MessageDigest.getInstance("SHA-256");
			byte[] buffer = new byte[8192];
			int read;
			while((read = input.read(buffer)) != -1)
				digest.update(buffer, 0, read);
			return HexFormat.of().formatHex(digest.digest());
		}catch(NoSuchAlgorithmException e)
		{
			throw new IOException("SHA-256 unavailable", e);
		}
	}
}
