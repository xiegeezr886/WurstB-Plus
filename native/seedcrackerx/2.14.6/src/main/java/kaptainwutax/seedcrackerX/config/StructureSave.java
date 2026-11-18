package kaptainwutax.seedcrackerX.config;

import com.seedfinding.mcfeature.Feature.Data;
import com.seedfinding.mcfeature.structure.RegionStructure;
import com.seedfinding.mcfeature.structure.Structure;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;
import kaptainwutax.seedcrackerX.Features;
import kaptainwutax.seedcrackerX.cracker.storage.DataStorage;
import kaptainwutax.seedcrackerX.cracker.storage.ScheduledSet;
import net.wurstclient.seed.SeedPlatform;
import net.minecraft.client.Minecraft;
import net.minecraft.network.Connection;
import net.minecraft.world.level.storage.LevelResource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class StructureSave {
   // Forge may clear the integrated server before firing player logout.
   private static String sessionFile;

   public static void beginSession() {
      Minecraft client = Minecraft.getInstance();
      if (client.getSingleplayerServer() != null) {
         sessionFile = client.getSingleplayerServer().getWorldPath(LevelResource.ROOT)
            .toAbsolutePath().normalize().getFileName().toString();
      } else if (client.getConnection() != null) {
         sessionFile = client.getConnection().getConnection().getRemoteAddress().toString();
      } else {
         sessionFile = null;
      }
      if (sessionFile != null) sessionFile = sessionFile.replaceAll("[\\\\/:*?\"<>|]", "_") + ".txt";
   }

   public static void endSession() {
      sessionFile = null;
   }
   private static final Logger logger = LoggerFactory.getLogger("structureSave");
   public static final Path saveDir = Paths.get(SeedPlatform.configDir().toFile().toString(), "SeedCrackerX saved structures");
   private static final List<RegionStructure<?, ?>> structureTypes = List.of(
      Features.IGLOO,
      Features.BURIED_TREASURE,
      Features.PILLAGER_OUTPOST,
      Features.DESERT_PYRAMID,
      Features.JUNGLE_PYRAMID,
      Features.END_CITY,
      Features.MONUMENT,
      Features.SHIPWRECK,
      Features.SWAMP_HUT
   );

   public static void saveStructures(ScheduledSet<DataStorage.Entry<Data<?>>> baseData) {
      if (sessionFile == null) return;
      try {
         Files.createDirectories(saveDir);
         Path saveFile = saveDir.resolve(getWorldName());
         Files.deleteIfExists(saveFile);
         Files.createFile(saveFile);

         try (FileWriter writer = new FileWriter(saveFile.toFile())) {
            for (DataStorage.Entry<Data<?>> dataEntry : baseData) {
               if (dataEntry.data.feature instanceof Structure structure) {
                  String data = Structure.getName(structure.getClass()) + ";" + dataEntry.data.chunkX + ";" + dataEntry.data.chunkZ + "\n";
                  writer.write(data);
               }
            }
         }
      } catch (IOException var9) {
         logger.error("seedcracker couldn't save structures", var9);
      }
   }

   public static List<com.seedfinding.mcfeature.structure.RegionStructure.Data<?>> loadStructures() {
      List<com.seedfinding.mcfeature.structure.RegionStructure.Data<?>> result = new ArrayList<>();
      if (sessionFile == null) return result;

      try {
         Files.createDirectories(saveDir);
         Path saveFile = saveDir.resolve(getWorldName());

         try (
            FileInputStream fis = new FileInputStream(saveFile.toFile());
            Scanner sc = new Scanner(fis);
         ) {
            while (sc.hasNextLine()) {
               String line = sc.nextLine();
               String[] info = line.split(";");
               if (info.length == 3) {
                  String structureName = info[0];

                  for (RegionStructure<?, ?> idk : structureTypes) {
                     if (structureName.equals(idk.getName())) {
                        result.add(idk.at(Integer.parseInt(info[1]), Integer.parseInt(info[2])));
                        break;
                     }
                  }
               }
            }
         }
      } catch (FileNotFoundException var13) {
         logger.warn("seedcracker couldn't find the structures file", var13);
         return result;
      } catch (IOException var14) {
         logger.error("seedcracker couldn't load previous structures", var14);
      }

      return result;
   }

   private static String getWorldName() {
      return sessionFile;
   }
}
