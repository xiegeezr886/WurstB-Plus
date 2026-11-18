package net.wurstclient.util.render;

import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.protocol.game.ServerboundPlayerInputPacket;
import net.minecraft.world.entity.player.Input;

/** 1.21.6 sends sneak state as part of the full player input. */
public final class V152Input {
  private V152Input() {}
  public static ServerboundPlayerInputPacket sneak(LocalPlayer player, boolean shift) {
    Input input = player.input.keyPresses;
    return new ServerboundPlayerInputPacket(new Input(input.forward(), input.backward(),
        input.left(), input.right(), input.jump(), shift, input.sprint()));
  }
}
