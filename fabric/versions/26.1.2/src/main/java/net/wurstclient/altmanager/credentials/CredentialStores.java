package net.wurstclient.altmanager.credentials;

import java.io.IOException;
import java.util.Locale;

public final class CredentialStores
{
	private CredentialStores() {}

	public static CredentialStore create() throws IOException
	{
		String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
		if(os.contains("win"))
			return new WindowsCredentialStore();
		if(os.contains("mac"))
			return new CommandCredentialStore(
				new String[]{"security", "find-generic-password", "-w", "-s",
					"{service}", "-a", "{account}"},
				new String[]{"security", "add-generic-password", "-U", "-s",
					"{service}", "-a", "{account}", "-w", "{value}"}, false);
		if(os.contains("nix") || os.contains("nux") || os.contains("aix"))
			return new CommandCredentialStore(
				new String[]{"secret-tool", "lookup", "service", "{service}",
					"account", "{account}"},
				new String[]{"secret-tool", "store", "--label=WurstB+ Plus",
					"service", "{service}", "account", "{account}"}, true);

		throw new IOException("Unsupported operating system: " + os);
	}
}
