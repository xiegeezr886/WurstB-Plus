package net.wurstclient.altmanager;

import java.io.ObjectOutputStream;
import java.math.BigInteger;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.SecureRandom;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.util.Base64;

import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;

import net.wurstclient.altmanager.credentials.CredentialStore;

/** S10: a shared legacy key must survive when the native key differs. */
public class EncryptionMigrationTest
{
	private static final class FakeStore implements CredentialStore
	{
		private String value;

		private FakeStore(String value)
		{
			this.value = value;
		}

		@Override
		public String read(String service, String account)
		{
			return value;
		}

		@Override
		public void write(String service, String account, String newValue)
		{
			value = newValue;
		}
	}

	public static void main(String[] args) throws Exception
	{
		byte[] legacyKey = new byte[16];
		byte[] distinctKey = new byte[16];
		SecureRandom random = new SecureRandom();
		random.nextBytes(legacyKey);
		random.nextBytes(distinctKey);

		Path different = Files.createTempDirectory("enc-different");
		writeLegacyKey(different, legacyKey);
		FakeStore differentStore = new FakeStore(encode(distinctKey));
		new Encryption(different, differentStore)
			.saveEncryptedFile(different.resolve("alts.json"), "{}");
		check(Files.exists(different.resolve("wurst_aes.txt")),
			"legacy key kept when native key differs");

		Encryption again = new Encryption(different, differentStore);
		check("{}".equals(again.loadEncryptedFile(different.resolve("alts.json"))),
			"migrated account file still decrypts after restart");

		Path same = Files.createTempDirectory("enc-same");
		writeLegacyKey(same, legacyKey);
		new Encryption(same, new FakeStore(encode(legacyKey)))
			.saveEncryptedFile(same.resolve("alts.json"), "{}");
		check(!Files.exists(same.resolve("wurst_aes.txt")),
			"legacy key removed when native key is the same key");
	}

	private static String encode(byte[] key)
	{
		return Base64.getEncoder().encodeToString(key);
	}

	private static void writeLegacyKey(Path folder, byte[] aesKey)
		throws Exception
	{
		Files.createDirectories(folder);
		KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
		generator.initialize(1024);
		KeyPair pair = generator.generateKeyPair();
		RSAPublicKey publicKey = (RSAPublicKey)pair.getPublic();
		RSAPrivateKey privateKey = (RSAPrivateKey)pair.getPrivate();

		try(ObjectOutputStream out = new ObjectOutputStream(
			Files.newOutputStream(folder.resolve("wurst_rsa_public.txt"))))
		{
			out.writeObject(publicKey.getModulus());
			out.writeObject(publicKey.getPublicExponent());
		}
		try(ObjectOutputStream out = new ObjectOutputStream(
			Files.newOutputStream(folder.resolve("wurst_rsa_private.txt"))))
		{
			out.writeObject(privateKey.getModulus());
			out.writeObject(privateKey.getPrivateExponent());
		}

		Cipher cipher = Cipher.getInstance("RSA");
		cipher.init(Cipher.ENCRYPT_MODE, pair.getPublic());
		Files.write(folder.resolve("wurst_aes.txt"),
			cipher.doFinal(aesKey));
	}

	private static void check(boolean ok, String message)
	{
		if(!ok)
			throw new AssertionError(message);
		System.out.println("OK: " + message);
	}
}
