/*
 * Copyright (c) 2014-2025 Wurst-Imperium and contributors.
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 */
package net.wurstclient.altmanager;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.SecureRandom;
import java.security.spec.RSAPrivateKeySpec;
import java.security.spec.RSAPublicKeySpec;
import java.util.Arrays;
import java.util.Base64;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;

import net.wurstclient.altmanager.credentials.CredentialStore;
import net.wurstclient.altmanager.credentials.CredentialStores;
import net.wurstclient.util.json.JsonException;
import net.wurstclient.util.json.JsonUtils;
import net.wurstclient.util.json.WsonArray;
import net.wurstclient.util.json.WsonObject;

public final class Encryption
{
	private static final byte[] MAGIC = {'W', 'B', 'P', '2'};
	private static final int NONCE_LENGTH = 12;
	private static final int GCM_TAG_BITS = 128;
	private static final String CREDENTIAL_SERVICE = "WurstB+ Plus";
	private static final String CREDENTIAL_ACCOUNT = "account-master-key";

	private final SecureRandom random = new SecureRandom();
	private final Path legacyFolder;
	private final SecretKey masterKey;
	private final SecretKey legacyKey;
	private final boolean masterKeyStoredNatively;
	private boolean legacyFilesPresent;

	public Encryption(Path encFolder) throws IOException
	{
		this(encFolder, CredentialStores.create());
	}

	Encryption(Path encFolder, CredentialStore credentialStore)
		throws IOException
	{
		legacyFolder = encFolder;
		legacyKey = loadLegacyKey(encFolder);
		legacyFilesPresent = legacyKey != null;

		String storedKey;
		try
		{
			storedKey = credentialStore.read(CREDENTIAL_SERVICE,
				CREDENTIAL_ACCOUNT);
		}catch(IOException e)
		{
			if(legacyKey == null)
				throw e;
			System.err.println("OS credential store unavailable; retaining legacy "
				+ "account key files: " + e.getMessage());
			masterKey = legacyKey;
			masterKeyStoredNatively = false;
			return;
		}
		if(storedKey != null && !storedKey.isBlank())
		{
			try
			{
				masterKey = new SecretKeySpec(Base64.getDecoder().decode(storedKey),
					"AES");
			}catch(IllegalArgumentException e)
			{
				throw new IOException("Invalid key in the operating system credential store",
					e);
			}
			masterKeyStoredNatively = true;
			return;
		}

		masterKey = legacyKey != null ? legacyKey : generateKey();
		boolean storedNatively;
		try
		{
			credentialStore.write(CREDENTIAL_SERVICE, CREDENTIAL_ACCOUNT,
				Base64.getEncoder().encodeToString(masterKey.getEncoded()));
			storedNatively = true;
		}catch(IOException e)
		{
			if(legacyKey == null)
				throw e;
			System.err.println("Could not migrate account key to OS credential "
				+ "store; retaining legacy files: " + e.getMessage());
			storedNatively = false;
		}
		masterKeyStoredNatively = storedNatively;
	}

	public byte[] decrypt(byte[] bytes)
	{
		try
		{
			byte[] payload = Base64.getDecoder().decode(bytes);
			if(hasMagic(payload))
				return decryptGcm(payload);
			return decryptLegacy(payload);
		}catch(IllegalArgumentException | GeneralSecurityException e)
		{
			throw new IllegalStateException("Could not decrypt account data", e);
		}
	}

	public String loadEncryptedFile(Path path) throws IOException
	{
		try
		{
			return new String(decrypt(Files.readAllBytes(path)),
				StandardCharsets.UTF_8);
		}catch(IllegalStateException e)
		{
			throw new IOException(e);
		}
	}

	public JsonElement parseFile(Path path) throws IOException, JsonException
	{
		try
		{
			return JsonParser.parseString(loadEncryptedFile(path));
		}catch(JsonParseException e)
		{
			throw new JsonException(e);
		}
	}

	public WsonArray parseFileToArray(Path path)
		throws IOException, JsonException
	{
		JsonElement json = parseFile(path);
		if(!json.isJsonArray())
			throw new JsonException();
		return new WsonArray(json.getAsJsonArray());
	}

	public WsonObject parseFileToObject(Path path)
		throws IOException, JsonException
	{
		JsonElement json = parseFile(path);
		if(!json.isJsonObject())
			throw new JsonException();
		return new WsonObject(json.getAsJsonObject());
	}

	public byte[] encrypt(byte[] bytes)
	{
		try
		{
			byte[] nonce = new byte[NONCE_LENGTH];
			random.nextBytes(nonce);
			Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
			cipher.init(Cipher.ENCRYPT_MODE, masterKey,
				new GCMParameterSpec(GCM_TAG_BITS, nonce));
			byte[] ciphertext = cipher.doFinal(bytes);
			ByteBuffer payload = ByteBuffer.allocate(MAGIC.length + NONCE_LENGTH
				+ ciphertext.length);
			payload.put(MAGIC).put(nonce).put(ciphertext);
			return Base64.getEncoder().encode(payload.array());
		}catch(GeneralSecurityException e)
		{
			throw new IllegalStateException("Could not encrypt account data", e);
		}
	}

	public void saveEncryptedFile(Path path, String content) throws IOException
	{
		byte[] encrypted;
		try
		{
			encrypted = encrypt(content.getBytes(StandardCharsets.UTF_8));
		}catch(IllegalStateException e)
		{
			throw new IOException(e);
		}

		Path parent = path.toAbsolutePath().getParent();
		if(parent != null)
			Files.createDirectories(parent);
		Path temporary = path.resolveSibling(path.getFileName() + ".tmp");
		Files.write(temporary, encrypted);
		try
		{
			Files.move(temporary, path, StandardCopyOption.ATOMIC_MOVE,
				StandardCopyOption.REPLACE_EXISTING);
		}catch(IOException e)
		{
			Files.move(temporary, path, StandardCopyOption.REPLACE_EXISTING);
		}
		cleanupLegacyFiles();
	}

	public void toEncryptedJson(JsonObject json, Path path)
		throws IOException, JsonException
	{
		try
		{
			saveEncryptedFile(path, JsonUtils.PRETTY_GSON.toJson(json));
		}catch(JsonParseException e)
		{
			throw new JsonException(e);
		}
	}

	private byte[] decryptGcm(byte[] payload)
		throws GeneralSecurityException
	{
		ByteBuffer buffer = ByteBuffer.wrap(payload);
		buffer.position(MAGIC.length);
		byte[] nonce = new byte[NONCE_LENGTH];
		buffer.get(nonce);
		byte[] ciphertext = new byte[buffer.remaining()];
		buffer.get(ciphertext);
		Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
		cipher.init(Cipher.DECRYPT_MODE, masterKey,
			new GCMParameterSpec(GCM_TAG_BITS, nonce));
		return cipher.doFinal(ciphertext);
	}

	private byte[] decryptLegacy(byte[] payload)
		throws GeneralSecurityException
	{
		// Try the real legacy key first, then the master key (which can be the
		// migrated form of that same key). CFB8 cannot detect a wrong key, so
		// only accept a candidate whose plaintext actually looks like the JSON
		// that AltsFile wrote. Otherwise a wrong key silently produces garbage
		// that the caller would treat as a corrupted alt list.
		SecretKey[] candidates = legacyKey != null
			? new SecretKey[] {legacyKey, masterKey}
			: new SecretKey[] {masterKey};

		for(SecretKey key : candidates)
		{
			if(key == null || key.getEncoded().length != 16)
				continue;

			Cipher cipher = Cipher.getInstance("AES/CFB8/NoPadding");
			cipher.init(Cipher.DECRYPT_MODE, key,
				new IvParameterSpec(key.getEncoded()));
			byte[] plain = cipher.doFinal(payload);
			if(looksLikeJson(plain))
				return plain;
		}

		throw new GeneralSecurityException(
			"Could not decrypt legacy account data: legacy key unavailable");
	}

	private static boolean looksLikeJson(byte[] bytes)
	{
		int start = 0;
		int end = bytes.length;
		while(start < end && isJsonWhitespace(bytes[start]))
			start++;
		while(end > start && isJsonWhitespace(bytes[end - 1]))
			end--;

		if(end - start < 2)
			return false;
		if(bytes[start] != '{' && bytes[start] != '[')
			return false;
		if(bytes[end - 1] != '}' && bytes[end - 1] != ']')
			return false;

		try
		{
			StandardCharsets.UTF_8.newDecoder()
				.decode(ByteBuffer.wrap(bytes, start, end - start));
			return true;
		}catch(CharacterCodingException e)
		{
			return false;
		}
	}

	private static boolean isJsonWhitespace(byte b)
	{
		return b == ' ' || b == '\t' || b == '\r' || b == '\n';
	}

	private boolean hasMagic(byte[] payload)
	{
		if(payload.length < MAGIC.length + NONCE_LENGTH + 16)
			return false;
		for(int i = 0; i < MAGIC.length; i++)
			if(payload[i] != MAGIC[i])
				return false;
		return true;
	}

	private SecretKey generateKey() throws IOException
	{
		try
		{
			KeyGenerator generator = KeyGenerator.getInstance("AES");
			generator.init(256, random);
			return generator.generateKey();
		}catch(GeneralSecurityException e)
		{
			throw new IOException("Could not generate account encryption key", e);
		}
	}

	private static byte[] readLegacyBytes(Path file, int limit) throws IOException
	{
		try(java.io.InputStream input = Files.newInputStream(file))
		{
			byte[] bytes = input.readNBytes(limit + 1);
			if(bytes.length > limit)
				throw new IOException("Legacy key file is too large: " + file);
			return bytes;
		}
	}

	private static ObjectInputStream openLegacyKey(Path file) throws IOException
	{
		ObjectInputStream input = new ObjectInputStream(new java.io.ByteArrayInputStream(
			readLegacyBytes(file, 16 * 1024)));
		input.setObjectInputFilter(info -> {
			if(info.depth() > 4 || info.references() > 16
				|| info.streamBytes() > 16 * 1024 || info.arrayLength() > 1024)
				return java.io.ObjectInputFilter.Status.REJECTED;
			Class<?> type = info.serialClass();
			if(type == null) return java.io.ObjectInputFilter.Status.UNDECIDED;
			return type == BigInteger.class || type == Number.class || type == byte[].class
				? java.io.ObjectInputFilter.Status.ALLOWED
				: java.io.ObjectInputFilter.Status.REJECTED;
		});
		return input;
	}

	private static BigInteger readLegacyInteger(ObjectInputStream input)
		throws IOException, ClassNotFoundException
	{
		Object value = input.readObject();
		if(!(value instanceof BigInteger number) || number.signum() <= 0
			|| number.bitLength() > 8192)
			throw new java.io.InvalidObjectException("Invalid legacy RSA integer");
		return number;
	}

	private SecretKey loadLegacyKey(Path folder) throws IOException
	{
		Path publicFile = folder.resolve("wurst_rsa_public.txt");
		Path privateFile = folder.resolve("wurst_rsa_private.txt");
		Path aesFile = folder.resolve("wurst_aes.txt");
		if(!Files.isRegularFile(publicFile) || !Files.isRegularFile(privateFile)
			|| !Files.isRegularFile(aesFile))
			return null;

		try
		{
			KeyFactory factory = KeyFactory.getInstance("RSA");
			PublicKey publicKey;
			try(ObjectInputStream in =
				openLegacyKey(publicFile))
			{
				publicKey = factory.generatePublic(new RSAPublicKeySpec(
					readLegacyInteger(in), readLegacyInteger(in)));
			}
			PrivateKey privateKey;
			try(ObjectInputStream in =
				openLegacyKey(privateFile))
			{
				privateKey = factory.generatePrivate(new RSAPrivateKeySpec(
					readLegacyInteger(in), readLegacyInteger(in)));
			}
			Cipher cipher = Cipher.getInstance("RSA");
			cipher.init(Cipher.DECRYPT_MODE, privateKey);
			return new SecretKeySpec(cipher.doFinal(readLegacyBytes(aesFile, 1024)),
				"AES");
		}catch(GeneralSecurityException | ReflectiveOperationException e)
		{
			throw new IOException("Could not load legacy account encryption key", e);
		}
	}

	private void cleanupLegacyFiles()
	{
		if(!legacyFilesPresent || !masterKeyStoredNatively)
			return;

		// The legacy key folder is shared by every Minecraft installation of
		// this user. Deleting it after migrating one file would strand the
		// account files that other installations have not migrated yet.
		if(legacyKeyDiffers())
			return;

		try
		{
			Files.deleteIfExists(legacyFolder.resolve("wurst_rsa_public.txt"));
			Files.deleteIfExists(legacyFolder.resolve("wurst_rsa_private.txt"));
			Files.deleteIfExists(legacyFolder.resolve("wurst_aes.txt"));
			Files.deleteIfExists(
				legacyFolder.resolve("READ ME I AM VERY IMPORTANT.txt"));
			Files.deleteIfExists(legacyFolder);
			legacyFilesPresent = false;
		}catch(IOException e)
		{
			System.err.println("Could not remove migrated legacy key files: "
				+ e.getMessage());
		}
	}


	private boolean legacyKeyDiffers()
	{
		return legacyKey != null
			&& !Arrays.equals(legacyKey.getEncoded(), masterKey.getEncoded());
	}

	public static Path chooseEncryptionFolder()
	{
		String userHome = System.getProperty("user.home");
		String xdgDataHome = System.getenv("XDG_DATA_HOME");
		String folderName = ".Wurst encryption";
		Path homeFolder = Paths.get(userHome, folderName).normalize();
		if(xdgDataHome == null || xdgDataHome.isEmpty())
			return homeFolder;
		Path xdgFolder = Paths.get(xdgDataHome, folderName).normalize();
		if(Files.isDirectory(xdgFolder) || !Files.isDirectory(homeFolder))
			return xdgFolder;
		return homeFolder;
	}
}
