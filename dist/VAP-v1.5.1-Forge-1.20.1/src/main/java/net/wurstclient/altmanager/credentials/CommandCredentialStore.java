package net.wurstclient.altmanager.credentials;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

final class CommandCredentialStore implements CredentialStore
{
	private final String[] readCommand;
	private final String[] writeCommand;
	private final boolean valueOnStdin;
	private final int notFoundExitCode;
	private final boolean requireEmptyErrorForNotFound;

	CommandCredentialStore(String[] readCommand, String[] writeCommand,
		boolean valueOnStdin, int notFoundExitCode,
		boolean requireEmptyErrorForNotFound)
	{
		this.readCommand = readCommand;
		this.writeCommand = writeCommand;
		this.valueOnStdin = valueOnStdin;
		this.notFoundExitCode = notFoundExitCode;
		this.requireEmptyErrorForNotFound = requireEmptyErrorForNotFound;
	}

	@Override
	public String read(String service, String account) throws IOException
	{
		Process process = start(readCommand, service, account, null);
		byte[] output = process.getInputStream().readAllBytes();
		byte[] error = process.getErrorStream().readAllBytes();
		int exitCode = waitFor(process);
		if(exitCode == 0)
			return new String(output, StandardCharsets.UTF_8).stripTrailing();

		// Only a confirmed "not found" may look like an absent key. Any other
		// failure must stay an IOException, otherwise Encryption would create
		// and store a new master key and replace the still valid old one.
		if(exitCode == notFoundExitCode
			&& (!requireEmptyErrorForNotFound || error.length == 0))
			return null;

		throw new IOException("Credential read failed (" + exitCode + "): "
			+ new String(error, StandardCharsets.UTF_8).strip());
	}

	@Override
	public void write(String service, String account, String value)
		throws IOException
	{
		Process process = start(writeCommand, service, account, value);
		if(valueOnStdin)
			try(OutputStream out = process.getOutputStream())
			{
				out.write(value.getBytes(StandardCharsets.UTF_8));
			}

		byte[] error = process.getErrorStream().readAllBytes();
		int exitCode = waitFor(process);
		if(exitCode != 0)
			throw new IOException("Credential command failed (" + exitCode + "): "
				+ new String(error, StandardCharsets.UTF_8).strip());
	}

	private Process start(String[] template, String service, String account,
		String value) throws IOException
	{
		List<String> command = new ArrayList<>(template.length);
		Arrays.stream(template).map(part -> part.replace("{service}", service)
			.replace("{account}", account)
			.replace("{value}", value == null ? "" : value)).forEach(command::add);
		return new ProcessBuilder(command).start();
	}

	private int waitFor(Process process) throws IOException
	{
		try
		{
			return process.waitFor();
		}catch(InterruptedException e)
		{
			Thread.currentThread().interrupt();
			throw new IOException("Interrupted while accessing credential store", e);
		}
	}
}
