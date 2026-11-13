/*
 * Copyright (c) 2025 Penguin
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 */
package net.wurstclient.discord;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

public final class DiscordRpc implements AutoCloseable
{
	private static final String PIPE_PREFIX = "\\\\.\\pipe\\discord-ipc-";
	private static final int MAX_PIPES = 10;

	private final String clientId;
	private final AtomicReference<RandomAccessFile> pipe =
		new AtomicReference<>();
	private final AtomicLong nonceCounter = new AtomicLong();
	private final AtomicBoolean closed = new AtomicBoolean();

	public DiscordRpc(String clientId)
	{
		this.clientId = clientId;
	}

	public boolean connect()
	{
		if(closed.get())
			return false;
		closePipe();
		for(int i = 0; i < MAX_PIPES; i++)
		{
			RandomAccessFile candidate = null;
			try
			{
				candidate = new RandomAccessFile(PIPE_PREFIX + i, "rw");
				if(closed.get())
				{
					close(candidate);
					return false;
				}
				pipe.set(candidate);

				String handshake = buildHandshake();
				writeFrame(candidate, 0, handshake);

				JsonObject response = readResponse(candidate);
				if(response == null)
				{
					closePipe(candidate);
					continue;
				}

				String evt = getString(response, "evt");
				if("READY".equals(evt) && pipe.get() == candidate)
					return true;

				closePipe(candidate);
			}catch(Exception e)
			{
				closePipe(candidate);
			}
		}
		return false;
	}

	public boolean setActivity(String state, String details,
		String largeImage, String largeText)
	{
		RandomAccessFile current = pipe.get();
		if(current == null)
			return false;

		try
		{
			JsonObject activity = new JsonObject();
			if(state != null)
				activity.addProperty("state", state);
			if(details != null)
				activity.addProperty("details", details);

			JsonObject timestamps = new JsonObject();
			timestamps.addProperty("start",
				System.currentTimeMillis() / 1000);
			activity.add("timestamps", timestamps);

			if(largeImage != null)
			{
				JsonObject assets = new JsonObject();
				assets.addProperty("large_image", largeImage);
				if(largeText != null)
					assets.addProperty("large_text", largeText);
				activity.add("assets", assets);
			}

			JsonObject args = new JsonObject();
			args.addProperty("pid", ProcessHandle.current().pid());
			args.add("activity", activity);

			JsonObject frame = new JsonObject();
			frame.addProperty("cmd", "SET_ACTIVITY");
			frame.addProperty("nonce", nextNonce());
			frame.add("args", args);

			writeFrame(current, 1, frame.toString());
			JsonObject response = readResponse(current);
			if(response == null)
				throw new IOException("Discord RPC closed without a response");
			return pipe.get() == current;
		}catch(Exception e)
		{
			closePipe(current);
			return false;
		}
	}

	public boolean isConnected()
	{
		return pipe.get() != null;
	}

	@Override
	public void close()
	{
		closed.set(true);
		closePipe();
	}

	private void closePipe()
	{
		close(pipe.getAndSet(null));
	}

	private void closePipe(RandomAccessFile expected)
	{
		if(expected != null && pipe.compareAndSet(expected, null))
			close(expected);
		else if(expected != null && pipe.get() != expected)
			close(expected);
	}

	private static void close(RandomAccessFile current)
	{
		if(current == null)
			return;
		try
		{
			current.close();
		}catch(IOException e)
		{}
	}

	private String buildHandshake()
	{
		JsonObject obj = new JsonObject();
		obj.addProperty("v", 1);
		obj.addProperty("client_id", clientId);
		return obj.toString();
	}

	private void writeFrame(RandomAccessFile current, int opcode, String json)
		throws IOException
	{
		byte[] payload = json.getBytes(StandardCharsets.UTF_8);
		byte[] header = new byte[8];
		header[0] = (byte)(opcode & 0xFF);
		header[1] = (byte)((opcode >> 8) & 0xFF);
		header[2] = (byte)((opcode >> 16) & 0xFF);
		header[3] = (byte)((opcode >> 24) & 0xFF);
		int len = payload.length;
		header[4] = (byte)(len & 0xFF);
		header[5] = (byte)((len >> 8) & 0xFF);
		header[6] = (byte)((len >> 16) & 0xFF);
		header[7] = (byte)((len >> 24) & 0xFF);

		current.write(header);
		current.write(payload);
	}

	private JsonObject readResponse(RandomAccessFile current)
	{
		try
		{
			byte[] header = new byte[8];
			current.readFully(header);

			int opcode = (header[0] & 0xFF)
				| ((header[1] & 0xFF) << 8)
				| ((header[2] & 0xFF) << 16)
				| ((header[3] & 0xFF) << 24);
			int length = (header[4] & 0xFF)
				| ((header[5] & 0xFF) << 8)
				| ((header[6] & 0xFF) << 16)
				| ((header[7] & 0xFF) << 24);

			if(length <= 0 || length > 65536)
				return null;

			byte[] payload = new byte[length];
			current.readFully(payload);

			String json = new String(payload, StandardCharsets.UTF_8);
			return JsonParser.parseString(json).getAsJsonObject();
		}catch(Exception e)
		{
			return null;
		}
	}

	private String nextNonce()
	{
		return String.valueOf(nonceCounter.incrementAndGet());
	}

	private static String getString(JsonObject obj, String key)
	{
		if(obj == null || !obj.has(key))
			return null;
		return obj.get(key).getAsString();
	}
}
