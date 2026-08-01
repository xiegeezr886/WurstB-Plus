package net.wurstclient.altmanager.credentials;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

import com.sun.jna.Memory;
import com.sun.jna.Native;
import com.sun.jna.Pointer;
import com.sun.jna.Structure;
import com.sun.jna.WString;
import com.sun.jna.platform.win32.WinBase.FILETIME;
import com.sun.jna.ptr.PointerByReference;
import com.sun.jna.win32.StdCallLibrary;
import com.sun.jna.win32.W32APIOptions;

final class WindowsCredentialStore implements CredentialStore
{
	private static final int CRED_TYPE_GENERIC = 1;
	private static final int CRED_PERSIST_LOCAL_MACHINE = 2;
	private static final int ERROR_NOT_FOUND = 1168;

	private interface Advapi32Credentials extends StdCallLibrary
	{
		Advapi32Credentials INSTANCE = Native.load("Advapi32",
			Advapi32Credentials.class, W32APIOptions.UNICODE_OPTIONS);

		boolean CredReadW(WString targetName, int type, int flags,
			PointerByReference credential);

		boolean CredWriteW(Credential credential, int flags);

		void CredFree(Pointer credential);
	}

	@Structure.FieldOrder({"Flags", "Type", "TargetName", "Comment",
		"LastWritten", "CredentialBlobSize", "CredentialBlob", "Persist",
		"AttributeCount", "Attributes", "TargetAlias", "UserName"})
	public static final class Credential extends Structure
	{
		public int Flags;
		public int Type;
		public WString TargetName;
		public WString Comment;
		public FILETIME LastWritten = new FILETIME();
		public int CredentialBlobSize;
		public Pointer CredentialBlob;
		public int Persist;
		public int AttributeCount;
		public Pointer Attributes;
		public WString TargetAlias;
		public WString UserName;

		public Credential() {}

		public Credential(Pointer pointer)
		{
			super(pointer);
			read();
		}
	}

	@Override
	public String read(String service, String account) throws IOException
	{
		PointerByReference reference = new PointerByReference();
		if(!Advapi32Credentials.INSTANCE.CredReadW(new WString(target(service,
			account)), CRED_TYPE_GENERIC, 0, reference))
		{
			int error = Native.getLastError();
			if(error == ERROR_NOT_FOUND)
				return null;
			throw new IOException("CredReadW failed with error " + error);
		}

		Pointer pointer = reference.getValue();
		try
		{
			Credential credential = new Credential(pointer);
			if(credential.CredentialBlobSize == 0)
				return "";
			byte[] value = credential.CredentialBlob.getByteArray(0,
				credential.CredentialBlobSize);
			return new String(value, StandardCharsets.UTF_8);
		}finally
		{
			Advapi32Credentials.INSTANCE.CredFree(pointer);
		}
	}

	@Override
	public void write(String service, String account, String value)
		throws IOException
	{
		byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
		Memory blob = new Memory(Math.max(1, bytes.length));
		if(bytes.length > 0)
			blob.write(0, bytes, 0, bytes.length);

		Credential credential = new Credential();
		credential.Type = CRED_TYPE_GENERIC;
		credential.TargetName = new WString(target(service, account));
		credential.CredentialBlobSize = bytes.length;
		credential.CredentialBlob = blob;
		credential.Persist = CRED_PERSIST_LOCAL_MACHINE;
		credential.UserName = new WString(account);
		credential.write();

		if(!Advapi32Credentials.INSTANCE.CredWriteW(credential, 0))
			throw new IOException(
				"CredWriteW failed with error " + Native.getLastError());
	}

	private String target(String service, String account)
	{
		return service + "/" + account;
	}
}
