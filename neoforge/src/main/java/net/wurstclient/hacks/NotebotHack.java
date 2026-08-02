/*
 * Copyright (c) 2025 Penguin
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 */
package net.wurstclient.hacks;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.NoteBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.wurstclient.Category;
import net.wurstclient.SearchTags;
import net.wurstclient.events.UpdateListener;
import net.wurstclient.hack.Hack;
import net.wurstclient.settings.SliderSetting;
import net.wurstclient.util.NbsSong;
import net.wurstclient.util.NbsSong.Note;

@SearchTags({"note bot", "music", "song", "nbs"})
public final class NotebotHack extends Hack implements UpdateListener
{
	private final SliderSetting range = new SliderSetting("Range", 10, 3, 30,
		1, SliderSetting.ValueDisplay.INTEGER);
	private final SliderSetting speed = new SliderSetting("Speed (TPS)", 10, 1,
		20, 1, SliderSetting.ValueDisplay.INTEGER);

	private List<Note> notes = List.of();
	private final Map<NoteKey, BlockPos> noteBlocks = new HashMap<>();
	private int currentNote;
	private int songTick;
	private int lastSongTick;
	private int rescanTicks;
	private double tickAccumulator;

	public NotebotHack()
	{
		super("Notebot");
		setCategory(Category.FUN);
		addSetting(range);
		addSetting(speed);
	}

	@Override
	protected void onEnable()
	{
		EVENTS.add(UpdateListener.class, this);
		loadSong();
		scanNoteBlocks();
		resetPlayback();
	}

	@Override
	protected void onDisable()
	{
		EVENTS.remove(UpdateListener.class, this);
		notes = List.of();
		noteBlocks.clear();
		resetPlayback();
	}

	@Override
	public void onUpdate()
	{
		if(MC.player == null || MC.level == null || MC.gameMode == null
			|| notes.isEmpty())
			return;

		if(++rescanTicks >= 40)
		{
			rescanTicks = 0;
			scanNoteBlocks();
		}

		tickAccumulator += speed.getValue() / 20.0;
		while(tickAccumulator >= 1)
		{
			playSongTick();
			tickAccumulator--;
		}
	}

	private void playSongTick()
	{
		while(currentNote < notes.size()
			&& notes.get(currentNote).tick() == songTick)
		{
			play(notes.get(currentNote));
			currentNote++;
		}

		if(songTick++ >= lastSongTick)
		{
			currentNote = 0;
			songTick = 0;
		}
	}

	private void play(Note note)
	{
		NoteKey noteKey = NoteKey.from(note);
		if(noteKey == null)
			return;

		BlockPos target = noteBlocks.get(noteKey);
		if(target == null)
			return;

		BlockState state = MC.level.getBlockState(target);
		if(!matches(state, noteKey))
		{
			noteBlocks.remove(noteKey);
			return;
		}

		MC.gameMode.startDestroyBlock(target, Direction.UP);
		MC.player.swing(InteractionHand.MAIN_HAND);
	}

	private void scanNoteBlocks()
	{
		noteBlocks.clear();
		if(MC.player == null || MC.level == null)
			return;

		int searchRange = (int)range.getValueI();
		BlockPos playerPos = MC.player.blockPosition();
		Map<NoteKey, Double> distances = new HashMap<>();
		for(int x = -searchRange; x <= searchRange; x++)
			for(int y = -searchRange; y <= searchRange; y++)
				for(int z = -searchRange; z <= searchRange; z++)
				{
					BlockPos pos = playerPos.offset(x, y, z);
					BlockState state = MC.level.getBlockState(pos);
					if(!state.is(Blocks.NOTE_BLOCK))
						continue;

					NoteKey key = new NoteKey(state.getValue(NoteBlock.NOTE),
						state.getValue(NoteBlock.INSTRUMENT).ordinal());
					double distance = pos.distSqr(playerPos);
					if(distance < distances.getOrDefault(key,
						Double.MAX_VALUE))
					{
						distances.put(key, distance);
						noteBlocks.put(key, pos.immutable());
					}
				}
	}

	private static boolean matches(BlockState state, NoteKey key)
	{
		return state.is(Blocks.NOTE_BLOCK)
			&& state.getValue(NoteBlock.NOTE) == key.pitch
			&& state.getValue(NoteBlock.INSTRUMENT).ordinal() == key.instrument;
	}

	private void loadSong()
	{
		notes = List.of();
		Path folder = WURST.getWurstFolder().resolve("notebot_songs");
		if(!Files.isDirectory(folder))
			return;

		try(var stream = Files.list(folder))
		{
			Path songPath = stream.filter(Files::isRegularFile)
				.filter(path -> path.getFileName().toString().toLowerCase(
					Locale.ROOT).endsWith(".nbs"))
				.sorted(Comparator.comparing(path -> path.getFileName()
					.toString(), String.CASE_INSENSITIVE_ORDER))
				.findFirst().orElse(null);
			if(songPath == null)
				return;

			NbsSong song = NbsSong.read(songPath);
			notes = song.getNotes();
			lastSongTick = song.getLastTick();
		}catch(IOException e)
		{
			System.err.println("Notebot: Failed to load song: "
				+ e.getMessage());
		}
	}

	private void resetPlayback()
	{
		currentNote = 0;
		songTick = 0;
		rescanTicks = 0;
		tickAccumulator = 0;
	}

	private record NoteKey(int pitch, int instrument)
	{
		private static NoteKey from(Note note)
		{
			int pitch = note.key() - 33;
			if(pitch < 0 || pitch > 24 || note.instrument() < 0
				|| note.instrument() > 15)
				return null;
			return new NoteKey(pitch, note.instrument());
		}
	}
}
