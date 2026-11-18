package net.wurstclient.altmanager;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.ObjectOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.spec.RSAPrivateKeySpec;
import java.security.spec.RSAPublicKeySpec;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.IvParameterSpec;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import net.wurstclient.altmanager.credentials.CredentialStore;

final class EncryptionTest
{
	@Test
	void gcmUsesRandomNoncesAndRejectsTampering(@TempDir Path temp)
		throws Exception
	{
		Encryption encryption = new Encryption(temp.resolve("legacy"),
			new MemoryCredentialStore());
		byte[] cleartext = "account-data".getBytes(StandardCharsets.UTF_8);
		byte[] first = encryption.encrypt(cleartext);
		byte[] second = encryption.encrypt(cleartext);

		assertNotEquals(new String(first, StandardCharsets.US_ASCII),
			new String(second, StandardCharsets.US_ASCII));
		assertArrayEquals(cleartext, encryption.decrypt(first));

		byte[] payload = Base64.getDecoder().decode(first);
		payload[payload.length - 1] ^= 1;
		byte[] tampered = Base64.getEncoder().encode(payload);
		assertThrows(IllegalStateException.class,
			() -> encryption.decrypt(tampered));
	}

	@Test
	void legacyCfbDataMigratesAfterSuccessfulSave(@TempDir Path temp)
		throws Exception
	{
		Path legacyFolder = temp.resolve("legacy");
		Files.createDirectories(legacyFolder);
		SecretKey legacyKey = createLegacyKeys(legacyFolder);
		byte[] cleartext = "{\"test\":true}".getBytes(StandardCharsets.UTF_8);
		Cipher legacyCipher = Cipher.getInstance("AES/CFB8/NoPadding");
		legacyCipher.init(Cipher.ENCRYPT_MODE, legacyKey,
			new IvParameterSpec(legacyKey.getEncoded()));
		byte[] legacyPayload = Base64.getEncoder()
			.encode(legacyCipher.doFinal(cleartext));

		Encryption encryption = new Encryption(legacyFolder,
			new MemoryCredentialStore());
		assertArrayEquals(cleartext, encryption.decrypt(legacyPayload));

		Path accounts = temp.resolve("alts.json");
		encryption.saveEncryptedFile(accounts,
			new String(cleartext, StandardCharsets.UTF_8));
		assertArrayEquals(cleartext, encryption.decrypt(Files.readAllBytes(accounts)));
		assertFalse(Files.exists(legacyFolder.resolve("wurst_aes.txt")));
		assertFalse(Files.exists(legacyFolder.resolve("wurst_rsa_private.txt")));
	}

	private SecretKey createLegacyKeys(Path folder) throws Exception
	{
		KeyPairGenerator pairGenerator = KeyPairGenerator.getInstance("RSA");
		pairGenerator.initialize(1024);
		KeyPair pair = pairGenerator.generateKeyPair();
		KeyFactory factory = KeyFactory.getInstance("RSA");

		try(ObjectOutputStream out = new ObjectOutputStream(Files.newOutputStream(
			folder.resolve("wurst_rsa_public.txt"))))
		{
			RSAPublicKeySpec spec = factory.getKeySpec(pair.getPublic(),
				RSAPublicKeySpec.class);
			out.writeObject(spec.getModulus());
			out.writeObject(spec.getPublicExponent());
		}
		try(ObjectOutputStream out = new ObjectOutputStream(Files.newOutputStream(
			folder.resolve("wurst_rsa_private.txt"))))
		{
			RSAPrivateKeySpec spec = factory.getKeySpec(pair.getPrivate(),
				RSAPrivateKeySpec.class);
			out.writeObject(spec.getModulus());
			out.writeObject(spec.getPrivateExponent());
		}

		KeyGenerator keyGenerator = KeyGenerator.getInstance("AES");
		keyGenerator.init(128);
		SecretKey key = keyGenerator.generateKey();
		Cipher rsa = Cipher.getInstance("RSA");
		rsa.init(Cipher.ENCRYPT_MODE, pair.getPublic());
		Files.write(folder.resolve("wurst_aes.txt"),
			rsa.doFinal(key.getEncoded()));
		return key;
	}

	private static final class MemoryCredentialStore implements CredentialStore
	{
		private final Map<String, String> values = new HashMap<>();

		@Override
		public String read(String service, String account)
		{
			return values.get(service + account);
		}

		@Override
		public void write(String service, String account, String value)
		{
			values.put(service + account, value);
		}
	}
}
