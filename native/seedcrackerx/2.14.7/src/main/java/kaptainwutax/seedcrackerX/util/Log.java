package kaptainwutax.seedcrackerX.util;

import java.util.regex.Pattern;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentUtils;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.ClickEvent.Action;
import net.minecraft.world.entity.player.Player;

public class Log {
   public static void debug(String message) {
      Player player = getPlayer();
      if (player != null) {
         schedule(() -> player.displayClientMessage(Component.literal(message), false));
      }
   }

   public static void warn(String translateKey, Object... args) {
      String message = translate(translateKey).formatted(args);
      Player player = getPlayer();
      if (player != null) {
         schedule(() -> player.displayClientMessage(Component.literal(message).withStyle(ChatFormatting.GREEN), false));
      }
   }

   public static void warn(String translateKey) {
      warn(translateKey, new Object[0]);
   }

   public static void error(String translateKey) {
      String message = translate(translateKey);
      Player player = getPlayer();
      if (player != null) {
         schedule(() -> player.displayClientMessage(Component.literal(message).withStyle(ChatFormatting.RED), false));
      }
   }

   public static void printSeed(String translateKey, long seedValue) {
      String message = translate(translateKey);
      String[] data = message.split(Pattern.quote("${SEED}"));
      String seed = String.valueOf(seedValue);
      Component text = ComponentUtils.wrapInSquareBrackets(
         Component.literal(seed)
            .withStyle(
               style -> style.withColor(ChatFormatting.GREEN)
                  .withClickEvent(new ClickEvent(Action.COPY_TO_CLIPBOARD, seed))
                  .withHoverEvent(new HoverEvent(net.minecraft.network.chat.HoverEvent.Action.SHOW_TEXT, Component.translatable("chat.copy.click")))
                  .withInsertion(seed)
            )
      );
      Player player = getPlayer();
      if (player != null) {
         MutableComponent text1 = Component.literal(data[0]).append(text);
         if (data.length > 1) {
            text1.append(Component.literal(data[1]));
         }

         schedule(() -> player.displayClientMessage(text1, false));
      }
   }

   public static void printDungeonInfo(String message) {
      Component text = ComponentUtils.wrapInSquareBrackets(
         Component.literal(message)
            .withStyle(
               style -> style.withColor(ChatFormatting.GREEN)
                  .withClickEvent(new ClickEvent(Action.COPY_TO_CLIPBOARD, message))
                  .withHoverEvent(new HoverEvent(net.minecraft.network.chat.HoverEvent.Action.SHOW_TEXT, Component.translatable("chat.copy.click")))
                  .withInsertion(message)
            )
      );
      Player player = getPlayer();
      if (player != null) {
         schedule(() -> player.displayClientMessage(text, false));
      }
   }

   public static String translate(String translateKey) {
      return Language.getInstance().getOrDefault(translateKey);
   }

   private static void schedule(Runnable runnable) {
      Minecraft.getInstance().execute(runnable);
   }

   private static Player getPlayer() {
      return Minecraft.getInstance().player;
   }
}
