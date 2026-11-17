/*
 * Copyright (c) 2025 Penguin
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 */
package net.wurstclient.util;

import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class NbsSong
{
	private static final long MAX_FILE_SIZE = 16L * 1024 * 1024;
	private static final int MAX_STRING_BYTES = 1024 * 1024;
	private static final int MAX_NOTES = 1_000_000;

	private final int version;
	private final double tempo;
	private final List<Note> notes;
	private final int lastTick;

	private NbsSong(int version, double tempo, List<Note> notes, int lastTick)
	{
		this.version = version;
		this.tempo = tempo;
		this.notes = Collections.unmodifiableList(notes);
		this.lastTick = lastTick;
	}

	public static NbsSong read(Path path) throws IOException
	{
		if(Files.size(path) > MAX_FILE_SIZE)
			throw new IOException("NBS file is too large");
		try(InputStream in = Files.newInputStream(path))
		{
			return read(in);
		}
	}

	public static NbsSong read(InputStream input) throws IOException
	{
		DataInputStream in = new DataInputStream(input);
		int firstLength = readUnsignedShort(in);
		int version = 0;
		if(firstLength == 0)
		{
			version = in.readUnsignedByte();
			if(version < 1 || version > 5)
				throw new IOException("Unsupported NBS version: " + version);
			in.readUnsignedByte();
			readUnsignedShort(in);
		}
		readUnsignedShort(in);

		readString(in);
		readString(in);
		readString(in);
		readString(in);

		double tempo = readUnsignedShort(in) / 100.0;
		if(tempo <= 0)
			tempo = 10;
		in.readUnsignedByte();
		in.readUnsignedByte();
		in.readUnsignedByte();
		readInt(in);
		readInt(in);
		readInt(in);
		readInt(in);
		readInt(in);
		readString(in);
		if(version >= 4)
		{
			in.readUnsignedByte();
			in.readUnsignedByte();
			readUnsignedShort(in);
		}

		List<Note> notes = new ArrayList<>();
		int tick = -1;
		while(true)
		{
			int tickJump = readUnsignedShort(in);
			if(tickJump == 0)
				break;
			tick += tickJump;
			if(tick < 0 || tick > 10_000_000)
				throw new IOException("Invalid NBS tick index");

			int layer = -1;
			while(true)
			{
				int layerJump = readUnsignedShort(in);
				if(layerJump == 0)
					break;
				layer += layerJump;
				int instrument = in.readUnsignedByte();
				int key = in.readUnsignedByte();
				int velocity = 100;
				int panning = 100;
				int pitch = 0;
				if(version >= 4)
				{
					velocity = in.readUnsignedByte();
					panning = in.readUnsignedByte();
					pitch = readSignedShort(in);
				}
				notes.add(new Note(tick, layer, instrument, key, velocity,
					panning, pitch));
				if(notes.size() > MAX_NOTES)
					throw new IOException("NBS file contains too many notes");
			}
		}

		return new NbsSong(version, tempo, notes, Math.max(0, tick));
	}

	private static String readString(DataInputStream in) throws IOException
	{
		int length = readInt(in);
		if(length < 0 || length > MAX_STRING_BYTES)
			throw new IOException("Invalid NBS string length: " + length);
		byte[] bytes = new byte[length];
		in.readFully(bytes);
		return new String(bytes, StandardCharsets.UTF_8);
	}

	private static int readUnsignedShort(DataInputStream in) throws IOException
	{
		int low = in.readUnsignedByte();
		return low | in.readUnsignedByte() << 8;
	}

	private static short readSignedShort(DataInputStream in) throws IOException
	{
		return (short)readUnsignedShort(in);
	}

	private static int readInt(DataInputStream in) throws IOException
	{
		int byte0 = in.readUnsignedByte();
		int byte1 = in.readUnsignedByte();
		int byte2 = in.readUnsignedByte();
		int byte3 = in.readUnsignedByte();
		return byte0 | byte1 << 8 | byte2 << 16 | byte3 << 24;
	}

	public int getVersion()
	{
		return version;
	}

	public double getTempo()
	{
		return tempo;
	}

	public List<Note> getNotes()
	{
		return notes;
	}

	public int getLastTick()
	{
		return lastTick;
	}

	public record Note(int tick, int layer, int instrument, int key,
		int velocity, int panning, int pitch)
	{
	}
}
