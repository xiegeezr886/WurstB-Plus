package kaptainwutax.seedcrackerX.command;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import kaptainwutax.seedcrackerX.config.Config;
import kaptainwutax.seedcrackerX.util.Log;
import net.minecraft.commands.Commands;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.ChatFormatting;

public class CrackerCommand extends ClientCommand {
   @Override
   public String getName() {
      return "cracker";
   }

   @Override
   public void build(LiteralArgumentBuilder<CommandSourceStack> builder) {
      ((LiteralArgumentBuilder)((LiteralArgumentBuilder)builder.then(Commands.literal("ON").executes(context -> this.setActive(true))))
            .then(Commands.literal("OFF").executes(context -> this.setActive(false))))
         .executes(context -> this.toggleActive());
      builder.then(
         ((LiteralArgumentBuilder)((LiteralArgumentBuilder)Commands.literal("debug")
                  .then(Commands.literal("ON").executes(context -> this.setDebug(true))))
               .then(Commands.literal("OFF").executes(context -> this.setDebug(false))))
            .executes(context -> this.toggleDebug())
      );
   }

   private void feedback(boolean success, boolean flag) {
      String action = Log.translate(flag ? "cracker.enabled" : "cracker.disabled");
      if (success) {
         sendFeedback(Log.translate("cracker.successfully") + action, ChatFormatting.GREEN, false);
      } else {
         sendFeedback(Log.translate("cracker.already") + action, ChatFormatting.RED, false);
      }

      Config.save();
   }

   private int setActive(boolean flag) {
      this.feedback(Config.get().active != flag, flag);
      Config.get().active = flag;
      return 0;
   }

   private int toggleActive() {
      Config.get().active = !Config.get().active;
      this.feedback(true, Config.get().active);
      return 0;
   }

   private int setDebug(boolean flag) {
      this.feedback(Config.get().debug != flag, flag);
      Config.get().debug = flag;
      return 0;
   }

   private int toggleDebug() {
      Config.get().debug = !Config.get().debug;
      this.feedback(true, Config.get().debug);
      return 0;
   }
}
