package kaptainwutax.seedcrackerX.command;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import java.net.URI;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.Util;

public class DatabaseCommand extends ClientCommand {
   public static URI DATABASE_URL = URI.create("https://docs.google.com/spreadsheets/d/1tuQiE-0leW88em9OHbZnH-RFNhVqgoHhIt9WQbeqqWw/edit?usp=sharing");

   @Override
   public String getName() {
      return "database";
   }

   @Override
   public void build(LiteralArgumentBuilder<CommandSourceStack> builder) {
      builder.executes(this::openURL);
   }

   public int openURL(CommandContext<CommandSourceStack> context) {
      Util.getPlatform().openUri(DATABASE_URL);
      return 0;
   }
}
