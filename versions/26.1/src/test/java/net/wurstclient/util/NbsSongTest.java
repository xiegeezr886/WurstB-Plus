package net.wurstclient.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;

final class NbsSongTest
{
	@Test
	void readsModernSongsWithChordsAndTickJumps() throws Exception
	{
		ByteArrayOutputStream bytes = new ByteArrayOutputStream();
		DataOutputStream out = new DataOutputStream(bytes);
		writeShort(out, 0);
		out.writeByte(5);
		out.writeByte(16);
		writeShort(out, 3);
		writeShort(out, 2);
		writeMetadata(out, 1250, true);

		writeShort(out, 1);
		writeShort(out, 1);
		writeModernNote(out, 0, 33, 100, 100, 0);
		writeShort(out, 1);
		writeModernNote(out, 2, 45, 80, 90, -25);
		writeShort(out, 0);
		writeShort(out, 2);
		writeShort(out, 1);
		writeModernNote(out, 4, 57, 100, 100, 0);
		writeShort(out, 0);
		writeShort(out, 0);

		NbsSong song = NbsSong.read(
			new ByteArrayInputStream(bytes.toByteArray()));
		assertEquals(5, song.getVersion());
		assertEquals(12.5, song.getTempo());
		assertEquals(2, song.getLastTick());
		assertEquals(3, song.getNotes().size());
		assertEquals(0, song.getNotes().get(0).tick());
		assertEquals(0, song.getNotes().get(0).layer());
		assertEquals(1, song.getNotes().get(1).layer());
		assertEquals(-25, song.getNotes().get(1).pitch());
		assertEquals(2, song.getNotes().get(2).tick());
	}

	@Test
	void readsLegacySongs() throws Exception
	{
		ByteArrayOutputStream bytes = new ByteArrayOutputStream();
		DataOutputStream out = new DataOutputStream(bytes);
		writeShort(out, 1);
		writeShort(out, 1);
		writeMetadata(out, 1000, false);
		writeShort(out, 1);
		writeShort(out, 1);
		out.writeByte(1);
		out.writeByte(40);
		writeShort(out, 0);
		writeShort(out, 0);

		NbsSong song = NbsSong.read(
			new ByteArrayInputStream(bytes.toByteArray()));
		assertEquals(0, song.getVersion());
		assertEquals(10, song.getTempo());
		assertEquals(1, song.getNotes().size());
		assertEquals(1, song.getNotes().get(0).instrument());
		assertEquals(40, song.getNotes().get(0).key());
	}

	@Test
	void rejectsOversizedStrings() throws Exception
	{
		ByteArrayOutputStream bytes = new ByteArrayOutputStream();
		DataOutputStream out = new DataOutputStream(bytes);
		writeShort(out, 1);
		writeShort(out, 1);
		writeInt(out, 1024 * 1024 + 1);

		assertThrows(IOException.class, () -> NbsSong.read(
			new ByteArrayInputStream(bytes.toByteArray())));
	}

	private static void writeMetadata(DataOutputStream out, int tempo,
		boolean modern) throws IOException
	{
		writeString(out, "Song");
		writeString(out, "Author");
		writeString(out, "Original");
		writeString(out, "Description");
		writeShort(out, tempo);
		out.writeByte(0);
		out.writeByte(10);
		out.writeByte(4);
		for(int i = 0; i < 5; i++)
			writeInt(out, 0);
		writeString(out, "");
		if(modern)
		{
			out.writeByte(0);
			out.writeByte(0);
			writeShort(out, 0);
		}
	}

	private static void writeModernNote(DataOutputStream out, int instrument,
		int key, int velocity, int panning, int pitch) throws IOException
	{
		out.writeByte(instrument);
		out.writeByte(key);
		out.writeByte(velocity);
		out.writeByte(panning);
		writeShort(out, pitch);
	}

	private static void writeString(DataOutputStream out, String value)
		throws IOException
	{
		byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
		writeInt(out, bytes.length);
		out.write(bytes);
	}

	private static void writeShort(DataOutputStream out, int value)
		throws IOException
	{
		out.writeByte(value);
		out.writeByte(value >>> 8);
	}

	private static void writeInt(DataOutputStream out, int value)
		throws IOException
	{
		out.writeByte(value);
		out.writeByte(value >>> 8);
		out.writeByte(value >>> 16);
		out.writeByte(value >>> 24);
	}
}
