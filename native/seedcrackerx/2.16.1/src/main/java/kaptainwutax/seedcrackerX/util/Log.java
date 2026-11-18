package kaptainwutax.seedcrackerX.util;

import java.util.regex.Pattern;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentUtils;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.ClickEvent.CopyToClipboard;
import net.minecraft.network.chat.HoverEvent.ShowText;

public class Log {
   public static void debug(String message) {
      sendMessage(Component.literal(message));
   }

   public static void warn(String translateKey, Object... args) {
      String message = translate(translateKey).formatted(args);
      sendMessage(Component.literal(message).withStyle(ChatFormatting.GREEN));
   }

   public static void warn(String translateKey) {
      warn(translateKey, new Object[0]);
   }

   public static void error(String translateKey) {
      String message = translate(translateKey);
      sendMessage(Component.literal(message).withStyle(ChatFormatting.RED));
   }

   public static void printSeed(String translateKey, long seedValue) {
      String message = translate(translateKey);
      String[] data = message.split(Pattern.quote("${SEED}"));
      String seed = String.valueOf(seedValue);
      Component text = ComponentUtils.wrapInSquareBrackets(
         Component.literal(seed)
            .withStyle(
               style -> style.withColor(ChatFormatting.GREEN)
                  .withClickEvent(new CopyToClipboard(seed))
                  .withHoverEvent(new ShowText(Component.translatable("chat.copy.click")))
                  .withInsertion(seed)
            )
      );
      MutableComponent text1 = Component.literal(data[0]).append(text);
      if (data.length > 1) {
         text1.append(Component.literal(data[1]));
      }

      sendMessage(text1);
   }

   public static void printDungeonInfo(String message) {
      Component text = ComponentUtils.wrapInSquareBrackets(
         Component.literal(message)
            .withStyle(
               style -> style.withColor(ChatFormatting.GREEN)
                  .withClickEvent(new CopyToClipboard(message))
                  .withHoverEvent(new ShowText(Component.translatable("chat.copy.click")))
                  .withInsertion(message)
            )
      );
      sendMessage(text);
   }

   public static String translate(String translateKey) {
      return Language.getInstance().getOrDefault(translateKey);
   }

   private static void schedule(Runnable runnable) {
      Minecraft.getInstance().execute(runnable);
   }

   private static void sendMessage(Component component) {
      schedule(() -> Minecraft.getInstance().gui.chatListener().handleSystemMessage(component, false));
   }
}
