package kaptainwutax.seedcrackerX.init;

import com.mojang.brigadier.CommandDispatcher;
import java.util.ArrayList;
import java.util.List;
import kaptainwutax.seedcrackerX.command.ClientCommand;
import kaptainwutax.seedcrackerX.command.CrackerCommand;
import kaptainwutax.seedcrackerX.command.DataCommand;
import kaptainwutax.seedcrackerX.command.DatabaseCommand;
import kaptainwutax.seedcrackerX.command.FinderCommand;
import kaptainwutax.seedcrackerX.command.GuiCommand;
import kaptainwutax.seedcrackerX.command.RenderCommand;
import kaptainwutax.seedcrackerX.command.VersionCommand;
import net.minecraft.commands.CommandSourceStack;

public class ClientCommands {
   public static final String PREFIX = "seedcracker";
   public static final List<ClientCommand> COMMANDS = new ArrayList<>();
   public static RenderCommand RENDER;
   public static FinderCommand FINDER;
   public static DataCommand DATA;
   public static CrackerCommand CRACKER;
   public static VersionCommand VERSION;
   public static GuiCommand GUI;
   public static DatabaseCommand DATABASE;

   public static void registerCommands(CommandDispatcher<CommandSourceStack> dispatcher) {
      COMMANDS.forEach(clientCommand -> clientCommand.register(dispatcher));
   }

   static {
      COMMANDS.add(RENDER = new RenderCommand());
      COMMANDS.add(FINDER = new FinderCommand());
      COMMANDS.add(DATA = new DataCommand());
      COMMANDS.add(CRACKER = new CrackerCommand());
      COMMANDS.add(VERSION = new VersionCommand());
      COMMANDS.add(GUI = new GuiCommand());
      COMMANDS.add(DATABASE = new DatabaseCommand());
   }
}
