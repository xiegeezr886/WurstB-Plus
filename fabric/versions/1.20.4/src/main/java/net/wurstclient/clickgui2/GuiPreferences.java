package net.wurstclient.clickgui2;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.repository.PackRepository;
import net.wurstclient.WurstClient;
import net.wurstclient.util.json.JsonException;
import net.wurstclient.util.json.JsonUtils;

public final class GuiPreferences
{
	public static final String BUILTIN_FONT = "CozyUI";
	private static final String FONT_PACK_NAME = "WurstBPlus-Font";
	private static final String FONT_PACK_ID = "file/" + FONT_PACK_NAME;
	private static final ResourceLocation CUSTOM_FONT =
		new ResourceLocation(WurstClient.MOD_ID, "selected");

	private final Path path;
	private final Path fontsFolder;
	private boolean commandsEnabled = true;
	private boolean fontEnabled = true;
	private String selectedFont = BUILTIN_FONT;
	private boolean targetPlayers = true;
	private boolean targetMonsters = true;
	private boolean targetAnimals = true;
	private boolean targetTeams = true;
	private boolean targetVillagers = true;

	public GuiPreferences(Path wurstFolder)
	{
		path = wurstFolder.resolve("gui-preferences.json");
		fontsFolder = wurstFolder.resolve("fonts");
		load();
	}

	private void load()
	{
		try
		{
			JsonObject json = JsonUtils.parseFile(path).getAsJsonObject();
			if(json.has("commandsEnabled"))
				commandsEnabled = json.get("commandsEnabled").getAsBoolean();
			if(json.has("fontEnabled"))
				fontEnabled = json.get("fontEnabled").getAsBoolean();
			if(json.has("selectedFont"))
				selectedFont = json.get("selectedFont").getAsString();
			if(json.has("targetPlayers"))
				targetPlayers = json.get("targetPlayers").getAsBoolean();
			if(json.has("targetMonsters"))
				targetMonsters = json.get("targetMonsters").getAsBoolean();
			if(json.has("targetAnimals"))
				targetAnimals = json.get("targetAnimals").getAsBoolean();
			if(json.has("targetTeams"))
				targetTeams = json.get("targetTeams").getAsBoolean();
			if(json.has("targetVillagers"))
				targetVillagers = json.get("targetVillagers").getAsBoolean();
		}catch(NoSuchFileException e)
		{
			return;
		}catch(IOException | JsonException | RuntimeException e)
		{
			System.err.println("Couldn't load GUI preferences.");
			e.printStackTrace();
		}
	}

	private void save()
	{
		JsonObject json = new JsonObject();
		json.addProperty("commandsEnabled", commandsEnabled);
		json.addProperty("fontEnabled", fontEnabled);
		json.addProperty("selectedFont", selectedFont);
		json.addProperty("targetPlayers", targetPlayers);
		json.addProperty("targetMonsters", targetMonsters);
		json.addProperty("targetAnimals", targetAnimals);
		json.addProperty("targetTeams", targetTeams);
		json.addProperty("targetVillagers", targetVillagers);
		try
		{
			JsonUtils.toJson(json, path);
		}catch(IOException | JsonException e)
		{
			throw new RuntimeException("Couldn't save GUI preferences.", e);
		}
	}

	public boolean isCommandsEnabled()
	{
		return commandsEnabled;
	}

	public void setCommandsEnabled(boolean commandsEnabled)
	{
		this.commandsEnabled = commandsEnabled;
		save();
	}

	public boolean isFontEnabled()
	{
		return fontEnabled;
	}

	public void setFontEnabled(boolean fontEnabled)
	{
		this.fontEnabled = fontEnabled;
		save();
	}

	public String getSelectedFont()
	{
		return selectedFont;
	}

	public Path getFontsFolder()
	{
		return fontsFolder;
	}

	public boolean isTargetEnabled(TargetType type)
	{
		return switch(type)
		{
			case PLAYERS -> targetPlayers;
			case MONSTERS -> targetMonsters;
			case ANIMALS -> targetAnimals;
			case TEAMS -> targetTeams;
			case VILLAGERS -> targetVillagers;
		};
	}

	public void toggleTarget(TargetType type)
	{
		switch(type)
		{
			case PLAYERS -> targetPlayers = !targetPlayers;
			case MONSTERS -> targetMonsters = !targetMonsters;
			case ANIMALS -> targetAnimals = !targetAnimals;
			case TEAMS -> targetTeams = !targetTeams;
			case VILLAGERS -> targetVillagers = !targetVillagers;
		}
		save();
	}

	public List<String> listFonts()
	{
		ArrayList<String> fonts = new ArrayList<>();
		fonts.add(BUILTIN_FONT);
		try
		{
			Files.createDirectories(fontsFolder);
			try(var files = Files.list(fontsFolder))
			{
				files.filter(Files::isRegularFile).map(Path::getFileName)
					.map(Path::toString).filter(GuiPreferences::isFontFile)
					.sorted(String.CASE_INSENSITIVE_ORDER).forEach(fonts::add);
			}
		}catch(IOException e)
		{
			throw new RuntimeException("Couldn't list custom fonts.", e);
		}
		return fonts;
	}

	private static boolean isFontFile(String name)
	{
		String lower = name.toLowerCase(Locale.ROOT);
		return lower.endsWith(".ttf") || lower.endsWith(".otf");
	}

	public void selectFont(String fontName)
	{
		if(BUILTIN_FONT.equals(fontName))
		{
			selectedFont = BUILTIN_FONT;
			save();
			return;
		}

		Path source = fontsFolder.resolve(fontName).normalize();
		if(!source.getParent().equals(fontsFolder.normalize())
			|| !Files.isRegularFile(source) || !isFontFile(fontName))
			throw new IllegalArgumentException("Unknown font: " + fontName);

		createFontPack(source);
		selectedFont = fontName;
		save();
		enableFontPack();
	}

	private void createFontPack(Path source)
	{
		Minecraft minecraft = Minecraft.getInstance();
		Path packFolder = minecraft.getResourcePackDirectory()
			.resolve(FONT_PACK_NAME);
		Path fontAsset = packFolder.resolve("assets").resolve(WurstClient.MOD_ID)
			.resolve("font").resolve("selected.ttf");
		try
		{
			Files.createDirectories(fontAsset.getParent());
			Files.copy(source, fontAsset, StandardCopyOption.REPLACE_EXISTING);

			JsonObject pack = new JsonObject();
			JsonObject packInfo = new JsonObject();
			packInfo.addProperty("pack_format", 15);
			packInfo.addProperty("description",
				WurstClient.CLIENT_NAME + " selected GUI font");
			pack.add("pack", packInfo);
			JsonUtils.toJson(pack, packFolder.resolve("pack.mcmeta"));

			JsonObject font = new JsonObject();
			JsonArray providers = new JsonArray();
			JsonObject custom = new JsonObject();
			custom.addProperty("type", "ttf");
			custom.addProperty("file", WurstClient.MOD_ID + ":font/selected.ttf");
			custom.addProperty("size", 11);
			custom.addProperty("oversample", 2.0F);
			JsonArray shift = new JsonArray();
			shift.add(0);
			shift.add(0);
			custom.add("shift", shift);
			providers.add(custom);
			JsonObject fallback = new JsonObject();
			fallback.addProperty("type", "reference");
			fallback.addProperty("id", "minecraft:default");
			providers.add(fallback);
			font.add("providers", providers);
			JsonUtils.toJson(font, fontAsset.getParent().resolve("selected.json"));
		}catch(IOException | JsonException e)
		{
			throw new RuntimeException("Couldn't create custom font pack.", e);
		}
	}

	private void enableFontPack()
	{
		Minecraft minecraft = Minecraft.getInstance();
		PackRepository repository = minecraft.getResourcePackRepository();
		repository.reload();
		Collection<String> selected = new ArrayList<>(repository.getSelectedIds());
		if(!selected.contains(FONT_PACK_ID))
			selected.add(FONT_PACK_ID);
		repository.setSelected(selected);
		minecraft.options.updateResourcePacks(repository);
	}

	public Component styleText(String text)
	{
		Component component = Component.literal(text);
		if(!fontEnabled || BUILTIN_FONT.equals(selectedFont))
			return component;
		return component.copy().withStyle(style -> style.withFont(CUSTOM_FONT));
	}

	public enum TargetType
	{
		PLAYERS,
		MONSTERS,
		ANIMALS,
		TEAMS,
		VILLAGERS
	}
}
