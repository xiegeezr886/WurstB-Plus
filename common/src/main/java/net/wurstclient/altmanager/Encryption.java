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
		SecretKey key = legacyKey != null ? legacyKey : masterKey;
		if(key.getEncoded().length != 16)
			throw new GeneralSecurityException("Legacy AES key is unavailable");
		Cipher cipher = Cipher.getInstance("AES/CFB8/NoPadding");
		cipher.init(Cipher.DECRYPT_MODE, key,
			new IvParameterSpec(key.getEncoded()));
		return cipher.doFinal(payload);
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
				new ObjectInputStream(Files.newInputStream(publicFile)))
			{
				publicKey = factory.generatePublic(new RSAPublicKeySpec(
					(BigInteger)in.readObject(), (BigInteger)in.readObject()));
			}
			PrivateKey privateKey;
			try(ObjectInputStream in =
				new ObjectInputStream(Files.newInputStream(privateFile)))
			{
				privateKey = factory.generatePrivate(new RSAPrivateKeySpec(
					(BigInteger)in.readObject(), (BigInteger)in.readObject()));
			}
			Cipher cipher = Cipher.getInstance("RSA");
			cipher.init(Cipher.DECRYPT_MODE, privateKey);
			return new SecretKeySpec(cipher.doFinal(Files.readAllBytes(aesFile)),
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
