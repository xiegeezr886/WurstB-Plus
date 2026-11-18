/* OpaiBounce binding controls adaptation. See assets/wurstpenguin/opai/NOTICE.txt. */
package net.wurstclient.clickgui2;

import net.wurstclient.Feature;
import net.wurstclient.hack.Hack;
import net.wurstclient.keybinds.KeyAction;
import net.wurstclient.keybinds.KeybindList;

final class OpaiBinding
{
    private final KeybindList bindings;
    private final String command;
    private KeyAction action = KeyAction.TOGGLE;

    OpaiBinding(KeybindList bindings, String command)
    {
        this.bindings = bindings;
        this.command = command;
        for(KeyAction mode : KeyAction.values())
            if(bindings.getKeyForCommand(command(mode)) != null) { action = mode; break; }
    }

    // Only Hacks register an explicit primary toggle. HUD/settings bindings are not module toggles.
    static String commandFor(Feature feature)
    {
        return feature instanceof Hack ? feature.getName() : null;
    }

    KeyAction action() { return action; }
    String key()
    {
        String key = bindings.getKeyForCommand(command(action));
        if(key != null) return key;
        for(KeyAction mode : KeyAction.values())
            if((key = bindings.getKeyForCommand(command(mode))) != null) return key;
        return null;
    }
    void action(KeyAction next)
    {
        String key = key();
        action = next;
        if(key != null) bind(key);
    }
    void bind(String key)
    {
        for(KeyAction mode : KeyAction.values()) bindings.unbindCommand(command(mode));
        if(key != null) bindings.bindCommand(key, command(action));
    }
    private String command(KeyAction mode)
    {
        return (mode == KeyAction.HOLD ? "+" : mode == KeyAction.SMART ? "~" : "") + command;
    }
}
