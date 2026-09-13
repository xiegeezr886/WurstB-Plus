package net.wurstclient.keybinds;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class KeybindListTest
{
	@Test
	void movesOneCommandWithoutOverwritingCombinedBindings(@TempDir Path temp)
	{
		KeybindList list = new KeybindList(temp.resolve("keybinds.json"));
		list.add("key.keyboard.p", "say ready;flight");
		list.add("key.keyboard.q", "fullbright");

		list.bindCommand("key.keyboard.q", "flight");

		assertEquals("key.keyboard.q", list.getKeyForCommand("flight"));
		assertNull(list.getCommands("key.keyboard.g"));
		assertEquals("say ready", list.getCommands("key.keyboard.p"));
		assertEquals("fullbright;flight",
			list.getCommands("key.keyboard.q"));

		list.unbindCommand("flight");
		assertNull(list.getKeyForCommand("flight"));
		assertEquals("fullbright", list.getCommands("key.keyboard.q"));
	}
}
