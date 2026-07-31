package net.wurstclient.altmanager.credentials;

import java.io.IOException;

public interface CredentialStore
{
	String read(String service, String account) throws IOException;

	void write(String service, String account, String value) throws IOException;
}
