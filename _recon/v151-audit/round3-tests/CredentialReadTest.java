package net.wurstclient.altmanager.credentials;

/** S11: a failed credential read must not look like an absent key. */
public class CredentialReadTest
{
	public static void main(String[] args) throws Exception
	{
		CommandCredentialStore missing = new CommandCredentialStore(
			new String[]{"cmd", "/c", "exit", "44"},
			new String[]{"cmd", "/c", "exit", "0"}, false, 44, false);
		if(missing.read("service", "account") != null)
			throw new AssertionError("not-found must return null");

		CommandCredentialStore failing = new CommandCredentialStore(
			new String[]{"cmd", "/c", "exit", "73"},
			new String[]{"cmd", "/c", "exit", "0"}, false, 44, false);
		try
		{
			failing.read("service", "account");
			throw new AssertionError("read failure must throw IOException");
		}catch(java.io.IOException expected)
		{
			System.out.println(
				"OK: exit 44 -> null, exit 73 -> IOException");
		}
	}
}
