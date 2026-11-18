/* Epsilon AntiBot.kt online-player identity policy, GPL-3.0. */
package net.wurstclient.hacks;

import java.util.UUID;
import net.minecraft.world.entity.player.Player;
import net.wurstclient.Category;
import net.wurstclient.SearchTags;
import net.wurstclient.hack.Hack;

@SearchTags({"anti bot", "fake player", "npc detect"})
public final class AntiBotHack extends Hack {
  public AntiBotHack() {
    super("AntiBot");
    setCategory(Category.MISC);
  }

  public boolean isBot(Player player) {
    return player != MC.player && isBot(player.getUUID());
  }

  public boolean isBot(UUID uuid) {
    return isEnabled()
        && MC.getConnection() != null
        && MC.getConnection().getPlayerInfo(uuid) == null;
  }
}
