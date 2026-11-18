package kaptainwutax.seedcrackerX.config;

import com.seedfinding.mccore.version.MCVersion;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import kaptainwutax.seedcrackerX.SeedCracker;
import kaptainwutax.seedcrackerX.command.DatabaseCommand;
import kaptainwutax.seedcrackerX.cracker.HashedSeedData;
import kaptainwutax.seedcrackerX.finder.Finder;
import me.shedaniel.clothconfig2.api.ConfigBuilder;
import me.shedaniel.clothconfig2.api.ConfigCategory;
import me.shedaniel.clothconfig2.api.ConfigEntryBuilder;
import me.shedaniel.clothconfig2.impl.builders.SubCategoryBuilder;
import me.shedaniel.clothconfig2.impl.builders.DropdownMenuBuilder.TopCellElementBuilder;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ClickEvent.OpenUrl;
import net.minecraft.network.chat.HoverEvent.ShowText;
import net.minecraft.resources.Identifier;

public class ConfigScreen {
   private static final Config config = Config.get();

   private ArrayList<MCVersion> getSupportedVersions() {
      ArrayList<MCVersion> newerVersions = new ArrayList<>();

      for (MCVersion version : MCVersion.values()) {
         if (!version.isOlderThan(MCVersion.v1_8)) {
            newerVersions.add(version);
         }
      }

      return newerVersions;
   }

   private MCVersion mcVersionFromString(String version) {
      MCVersion mcVersion = MCVersion.fromString(version);
      return mcVersion == null ? MCVersion.latest() : mcVersion;
   }

   public Screen getConfigScreenByCloth(Screen parent) {
      ConfigBuilder builder = ConfigBuilder.create()
         .setParentScreen(parent)
         .setTitle(Component.translatable("title"))
         .setDefaultBackgroundTexture(Identifier.parse("minecraft:textures/block/blackstone.png"))
         .setTransparentBackground(true);
      ConfigEntryBuilder eb = builder.entryBuilder();
      ConfigCategory settings = builder.getOrCreateCategory(Component.translatable("settings"));
      settings.addEntry(eb.startBooleanToggle(Component.translatable("settings.active"), config.active).setSaveConsumer(val -> config.active = val).build());
      settings.addEntry(
         eb.startBooleanToggle(Component.translatable("settings.database"), config.databaseSubmits)
            .setSaveConsumer(val -> config.databaseSubmits = val)
            .build()
      );
      settings.addEntry(
         eb.startBooleanToggle(Component.translatable("settings.hideNameDatabase"), config.anonymusSubmits)
            .setSaveConsumer(val -> config.anonymusSubmits = val)
            .build()
      );
      settings.addEntry(
         eb.startTextDescription(
               Component.translatable("settings.openDatabase")
                  .withStyle(
                     s -> s.withClickEvent(new OpenUrl(DatabaseCommand.DATABASE_URL))
                        .withHoverEvent(new ShowText(Component.literal("google sheet")))
                        .withColor(ChatFormatting.BLUE)
                        .withUnderlined(true)
                        .withItalic(true)
                  )
            )
            .build()
      );
      settings.addEntry(
         eb.startDropdownMenu(Component.translatable("settings.version"), TopCellElementBuilder.of(config.getVersion(), this::mcVersionFromString))
            .setSelections(this.getSupportedVersions())
            .setSuggestionMode(false)
            .setDefaultValue(config.getVersion())
            .setSaveConsumer(config::setVersion)
            .build()
      );
      settings.addEntry(eb.startTextDescription(Component.literal("==============")).build());
      settings.addEntry(eb.startTextDescription(Component.translatable("settings.visuals")).build());
      settings.addEntry(
         eb.startEnumSelector(Component.translatable("settings.outline"), Config.RenderType.class, config.render)
            .setSaveConsumer(val -> config.render = val)
            .build()
      );
      settings.addEntry(eb.startTextDescription(Component.literal("==============")).build());
      settings.addEntry(eb.startTextDescription(Component.translatable("settings.finderToggles")).build());

      for (Finder.Type finder : Finder.Type.values()) {
         settings.addEntry(
            eb.startBooleanToggle(Component.translatable(finder.nameKey), finder.enabled.get()).setSaveConsumer(val -> finder.enabled.set(val)).build()
         );
      }

      settings.addEntry(eb.startTextDescription(Component.literal("==============")).build());
      settings.addEntry(
         eb.startBooleanToggle(Component.translatable("settings.antiXrayMode"), config.antiXrayBypass)
            .setSaveConsumer(val -> config.antiXrayBypass = val)
            .build()
      );
      settings.addEntry(eb.startTextDescription(Component.translatable("settings.antiAntiXrayExplained")).build());
      ConfigCategory info = builder.getOrCreateCategory(Component.translatable("info"));
      info.addEntry(eb.startBooleanToggle(Component.translatable("info.clearData"), false).setSaveConsumer(val -> {
         if (val) {
            SeedCracker.get().reset();
         }
      }).build());
      Set<Long> worldSeeds = SeedCracker.get().getDataStorage().getTimeMachine().worldSeeds;
      if (!worldSeeds.isEmpty()) {
         SubCategoryBuilder world = eb.startSubCategory(Component.translatable("info.worldSeeds"));

         for (long worldSeed : worldSeeds) {
            world.add(eb.startTextField(Component.literal(""), String.valueOf(worldSeed)).build());
         }

         info.addEntry(world.setExpanded(true).build());
      } else {
         info.addEntry(eb.startTextDescription(Component.translatable("info.noWorldSeeds")).build());
      }

      Set<Long> structureSeeds = SeedCracker.get().getDataStorage().getTimeMachine().structureSeeds;
      if (!structureSeeds.isEmpty()) {
         SubCategoryBuilder struc = eb.startSubCategory(Component.translatable("info.structureSeeds"));

         for (long structureSeed : structureSeeds) {
            struc.add(eb.startTextField(Component.literal(""), String.valueOf(structureSeed)).build());
         }

         info.addEntry(struc.setExpanded(true).build());
      } else {
         info.addEntry(eb.startTextDescription(Component.translatable("info.noStructureSeeds")).build());
      }

      if (config.debug) {
         List<Integer> pillarSeeds = SeedCracker.get().getDataStorage().getTimeMachine().pillarSeeds;
         if (pillarSeeds == null) {
            info.addEntry(eb.startTextDescription(Component.translatable("info.noPillarSeeds")).build());
         } else {
            SubCategoryBuilder pillar = eb.startSubCategory(Component.translatable("info.pillarSeeds"));
            Iterator var23 = pillarSeeds.iterator();

            while (var23.hasNext()) {
               long structureSeed = ((Integer)var23.next()).intValue();
               pillar.add(eb.startTextField(Component.literal(""), String.valueOf(structureSeed)).build());
            }

            info.addEntry(pillar.setExpanded(true).build());
         }

         HashedSeedData hashedSeed = SeedCracker.get().getDataStorage().hashedSeedData;
         if (hashedSeed != null) {
            info.addEntry(eb.startTextField(Component.translatable("info.hashedSeed"), String.valueOf(hashedSeed.getHashedSeed())).build());
         } else {
            info.addEntry(eb.startTextDescription(Component.translatable("info.noHashedSeed")).build());
         }
      }

      builder.setSavingRunnable(Config::save);
      return builder.build();
   }
}
