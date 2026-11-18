package kaptainwutax.seedcrackerX.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import kaptainwutax.seedcrackerX.util.Log;
import net.minecraft.commands.Commands;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

public abstract class ClientCommand {
   public static void sendFeedback(String message, ChatFormatting color, boolean overlay) {
      try {
         Minecraft.getInstance().player.displayClientMessage(Component.literal(message).withStyle(color), overlay);
      } catch (Exception var4) {
      }
   }

   public abstract String getName();

   public abstract void build(LiteralArgumentBuilder<CommandSourceStack> var1);

   public final void register(CommandDispatcher<CommandSourceStack> dispatcher) {
      LiteralArgumentBuilder<CommandSourceStack> builder = Commands.literal(this.getName());
      this.build(builder);
      LiteralArgumentBuilder<CommandSourceStack> seedCrackerRootCommand = (LiteralArgumentBuilder<CommandSourceStack>)Commands.literal(
            "seedcracker"
         )
         .executes(context -> {
            Log.error("Error: please enter a valid seedcracker command");
            return 1;
         });
      dispatcher.register((LiteralArgumentBuilder)seedCrackerRootCommand.then(builder));
   }
}
