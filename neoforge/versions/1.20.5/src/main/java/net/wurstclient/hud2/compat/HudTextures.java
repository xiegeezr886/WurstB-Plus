/* Copyright (c) 2026 WurstB+ Plus contributors. SPDX-License-Identifier: AGPL-3.0-only */
package net.wurstclient.hud2.compat;

import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.wurstclient.WurstClient;

/** Use TextureManager-owned textures, including downloaded skins and music covers. */
public final class HudTextures {
  private HudTextures() {}

  public static int id(String path) {
    if (path == null || path.isBlank()) return -1;
    ResourceLocation location = ResourceLocation.tryParse(path);
    if (location == null) return -1;
    try {
      var texture = Minecraft.getInstance().getTextureManager().getTexture(location);
      return texture == null ? -1 : texture.getId();
    } catch (RuntimeException e) {
      return -1;
    }
  }

  public static int localPlayerSkin() {
    return WurstClient.MC.player == null ? -1
        : id(WurstClient.MC.player.getSkin().texture().toString());
  }
}
