package net.wurstclient.clickgui2;

import static org.junit.jupiter.api.Assertions.*;
import static com.mojang.blaze3d.platform.InputConstants.*;
import java.nio.file.Path;
import java.util.Set;
import net.wurstclient.keybinds.KeyAction;
import net.wurstclient.keybinds.Keybind;
import net.wurstclient.keybinds.KeybindList;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class OpaiInteractionTest
{
    @Test
    void selectedTextIsReplacedAndShiftNavigationRetainsTheAnchor()
    {
        OpaiTextBox editor = new OpaiTextBox(null);
        editor.set("hello world"); editor.focus(true);
        editor.key(KEY_HOME, 0);
        for(int i = 0; i < 5; i++) editor.key(KEY_RIGHT, MOD_SHIFT);
        assertEquals("hello", editor.selection());
        editor.insert("你好");
        assertEquals("你好 world", editor.value());
        assertEquals(2, editor.cursor());
        editor.key(KEY_A, MOD_CONTROL);
        editor.character('!');
        assertEquals("!", editor.value());
    }

    @Test
    void pastedNumbersFilterControlsAndInvalidCharactersWithoutErasingASelection()
    {
        OpaiTextBox editor = new OpaiTextBox("0123456789.-");
        editor.set("123"); editor.focus(true); editor.selectAll();
        editor.insert("abc\n\u00a7");
        assertEquals("123", editor.value());
        assertEquals("123", editor.selection());
        editor.insert("-4a.5\n");
        assertEquals("-4.5", editor.value());
        editor.key(KEY_LEFT, 0);
        editor.key(KEY_BACKSPACE, 0);
        editor.character('.');
        assertEquals("-4.5", editor.value());
        editor.focus(false);
        assertFalse(editor.character('9'));
    }

    @Test
    void wordDeletionAndArrowSelectionWorkAtBothBoundaries()
    {
        OpaiTextBox editor = new OpaiTextBox(null);
        editor.set("one two three"); editor.focus(true);
        editor.key(KEY_LEFT, MOD_CONTROL | MOD_SHIFT);
        assertEquals("three", editor.selection());
        editor.key(KEY_BACKSPACE, 0);
        assertEquals("one two ", editor.value());
        editor.key(KEY_BACKSPACE, MOD_CONTROL);
        assertEquals("one ", editor.value());
        editor.key(KEY_HOME, 0);
        editor.key(KEY_BACKSPACE, 0);
        assertEquals("one ", editor.value());
        editor.key(KEY_END, 0);
        editor.key(KEY_DELETE, 0);
        assertEquals("one ", editor.value());
    }

    @Test
    void emojiCursorAndDeletionNeverSplitASurrogatePair()
    {
        OpaiTextBox editor = new OpaiTextBox(null);
        editor.set("A😀B"); editor.focus(true);
        editor.key(KEY_LEFT, 0);
        editor.key(KEY_BACKSPACE, 0);
        assertEquals("AB", editor.value());
        assertEquals(1, editor.cursor());
    }

    @Test
    void dropdownCapsItsHeightScrollsIndependentlyAndClipsHitTargets()
    {
        OpaiDropdown menu = new OpaiDropdown(20);
        menu.toggle(0); menu.advance(1);
        assertEquals(120, menu.height());
        assertEquals(0, menu.hit(0));
        assertEquals(-1, menu.hit(120));
        menu.wheel(-0.5);
        assertEquals(10, menu.scroll());
        assertEquals(1, menu.hit(10));
        menu.wheel(-100);
        assertEquals(280, menu.scroll());
        assertEquals(19, menu.hit(119));
        menu.wheel(100);
        assertEquals(0, menu.scroll());
        menu.close();
        assertEquals(-1, menu.hit(0));
        menu.advance(1);
        assertEquals(0, menu.height());
    }

    @Test
    void dropdownRevealCanReverseWithoutScalingOrMovingItsOptions()
    {
        OpaiDropdown menu = new OpaiDropdown(3);
        menu.toggle(0); menu.advance(0.01F);
        float partial = menu.height();
        assertTrue(partial > 0 && partial < 60);
        assertEquals(0, menu.hit(10));
        assertEquals(-1, menu.hit(partial));
        menu.close(); menu.advance(0.01F);
        float closing = menu.height();
        assertTrue(closing < partial && closing > 0);
        menu.toggle(0); menu.advance(0);
        assertEquals(closing, menu.height());
        menu.advance(1);
        assertEquals(60, menu.height());
    }

    @Test
    void grayscaleAndBlackKeepTheSelectedHueAndSaturationUntilAnExternalChange()
    {
        OpaiColor picker = new OpaiColor();
        picker.sync(0xFF00FFFF);
        picker.hue(0.75);
        picker.plane(0.8, 0);
        picker.sync(picker.rgb());
        assertEquals(0.75F, picker.hue());
        assertEquals(0.8F, picker.saturation());
        picker.plane(0, 1);
        picker.sync(picker.rgb());
        assertEquals(0.75F, picker.hue());
        picker.plane(1, 1);
        assertEquals(0xFF8000FF, picker.rgb());
        picker.sync(0xFF00FF00);
        assertEquals(1F / 3, picker.hue(), 0.001F);
        picker.hue(1);
        picker.sync(picker.rgb());
        assertEquals(1, picker.hue()); // bottom red stays at the bottom
    }

    @Test
    void colorDraggingOutsideThePlaneClampsToItsEdges()
    {
        OpaiColor picker = new OpaiColor();
        picker.hue(4); picker.plane(-5, 9);
        assertEquals(0xFFFFFFFF, picker.rgb());
        assertEquals(1, picker.hue());
        picker.hue(-1); picker.plane(5, -3);
        assertEquals(0xFF000000, picker.rgb());
        assertEquals(1, picker.saturation());
        assertEquals(0, picker.hue());
    }

    @Test
    void changingBindModesPreservesOtherCommandsAndEscapedSemicolons(@TempDir Path temp)
    {
        KeybindList list = new KeybindList(temp.resolve("keys.json"));
        list.setKeybinds(Set.of(new Keybind("key.keyboard.r", "KillAura;say hello;;world"),
            new Keybind("key.keyboard.g", "+killaura;flight"), new Keybind("key.keyboard.h", "~KillAura")));
        OpaiBinding binding = new OpaiBinding(list, "KillAura");
        binding.action(KeyAction.HOLD);
        assertEquals("key.keyboard.r", list.getKeyForCommand("+KillAura"));
        assertNull(list.getKeyForCommand("KillAura"));
        assertNull(list.getKeyForCommand("~KillAura"));
        assertEquals("flight", list.getCommands("key.keyboard.g"));
        assertEquals("say hello;;world;+KillAura", list.getCommands("key.keyboard.r"));
        binding.action(KeyAction.SMART);
        binding.bind("key.keyboard.g");
        assertEquals("say hello;;world", list.getCommands("key.keyboard.r"));
        assertEquals("flight;~KillAura", list.getCommands("key.keyboard.g"));
        binding.bind(null);
        assertEquals("flight", list.getCommands("key.keyboard.g"));
        assertNull(binding.key());
    }

    @Test
    void existingHoldModeIsDetectedAndNewModeCanBeChosenBeforeBinding(@TempDir Path temp)
    {
        KeybindList list = new KeybindList(temp.resolve("keys.json"));
        list.setKeybinds(Set.of(new Keybind("key.keyboard.g", "+flight")));
        OpaiBinding binding = new OpaiBinding(list, "Flight");
        assertEquals(KeyAction.HOLD, binding.action());
        binding.bind(null);
        binding.action(KeyAction.SMART);
        binding.bind("key.keyboard.f");
        assertEquals("key.keyboard.f", list.getKeyForCommand("~Flight"));
        assertNull(list.getKeyForCommand("+Flight"));
    }
}
