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
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

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
	private static final int CURRENT_VAPE_LAYOUT_VERSION = 2;
	public static final String BUILTIN_FONT = "CozyUI";
	private static final String FONT_PACK_NAME = "WurstBPlus-Font";
	private static final String FONT_PACK_ID = "file/" + FONT_PACK_NAME;
	private static final ResourceLocation CUSTOM_FONT =
		new ResourceLocation(WurstClient.MOD_ID, "selected");

	private final Path path;
	private final Path fontsFolder;
	private boolean commandsEnabled = true;
	private boolean fontEnabled = true;
	private boolean vapeMode;
	private int vapeLayoutVersion;
	private String selectedFont = BUILTIN_FONT;
	private boolean targetPlayers = true;
	private boolean targetMonsters = true;
	private boolean targetAnimals = true;
	private boolean targetTeams = true;
	private boolean targetVillagers = true;
	private final Map<String, VapeFrameState> vapeFrames = new HashMap<>();
	private final Set<String> vapeFavorites = new LinkedHashSet<>();
	private final Set<String> vapeHiddenModules = new LinkedHashSet<>();

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
			if(json.has("vapeMode"))
				vapeMode = json.get("vapeMode").getAsBoolean();
			if(json.has("vapeLayoutVersion"))
				vapeLayoutVersion = json.get("vapeLayoutVersion").getAsInt();
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
			if(json.has("vapeFrames") && json.get("vapeFrames").isJsonObject())
			{
				JsonObject frames = json.getAsJsonObject("vapeFrames");
				for(var entry : frames.entrySet())
				{
					JsonObject frame = entry.getValue().getAsJsonObject();
					vapeFrames.put(entry.getKey(), new VapeFrameState(
						frame.get("x").getAsDouble(),
						frame.get("y").getAsDouble(),
						frame.has("collapsed")
							&& frame.get("collapsed").getAsBoolean(),
						!frame.has("visible")
							|| frame.get("visible").getAsBoolean()));
				}
			}
			loadStringSet(json, "vapeFavorites", vapeFavorites);
			loadStringSet(json, "vapeHiddenModules", vapeHiddenModules);
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
		json.addProperty("vapeMode", vapeMode);
		json.addProperty("vapeLayoutVersion", vapeLayoutVersion);
		json.addProperty("selectedFont", selectedFont);
		json.addProperty("targetPlayers", targetPlayers);
		json.addProperty("targetMonsters", targetMonsters);
		json.addProperty("targetAnimals", targetAnimals);
		json.addProperty("targetTeams", targetTeams);
		json.addProperty("targetVillagers", targetVillagers);
		JsonObject frames = new JsonObject();
		for(var entry : vapeFrames.entrySet())
		{
			JsonObject frame = new JsonObject();
			frame.addProperty("x", entry.getValue().x());
			frame.addProperty("y", entry.getValue().y());
			frame.addProperty("collapsed", entry.getValue().collapsed());
			frame.addProperty("visible", entry.getValue().visible());
			frames.add(entry.getKey(), frame);
		}
		json.add("vapeFrames", frames);
		json.add("vapeFavorites", toJsonArray(vapeFavorites));
		json.add("vapeHiddenModules", toJsonArray(vapeHiddenModules));
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

	public boolean isVapeMode()
	{
		return vapeMode;
	}

	public void setVapeMode(boolean vapeMode)
	{
		this.vapeMode = vapeMode;
		save();
	}

	public void migrateVapeLayout()
	{
		if(vapeLayoutVersion >= CURRENT_VAPE_LAYOUT_VERSION)
			return;
		vapeFrames.clear();
		vapeLayoutVersion = CURRENT_VAPE_LAYOUT_VERSION;
		save();
	}

	public VapeFrameState getVapeFrameState(String name)
	{
		return vapeFrames.get(name);
	}

	public void setVapeFrameState(String name, double x, double y,
		boolean collapsed)
	{
		VapeFrameState old = vapeFrames.get(name);
		setVapeFrameState(name, x, y, collapsed,
			old == null || old.visible());
	}

	public void setVapeFrameState(String name, double x, double y,
		boolean collapsed, boolean visible)
	{
		vapeFrames.put(name, new VapeFrameState(x, y, collapsed, visible));
		save();
	}

	public void clearVapeFrameStates()
	{
		vapeFrames.clear();
		save();
	}

	public boolean isVapeFavorite(String featureName)
	{
		return vapeFavorites.contains(normalizeFeatureName(featureName));
	}

	public void toggleVapeFavorite(String featureName)
	{
		toggleName(vapeFavorites, featureName);
		save();
	}

	public boolean isVapeModuleHidden(String featureName)
	{
		return vapeHiddenModules.contains(normalizeFeatureName(featureName));
	}

	public void toggleVapeModuleHidden(String featureName)
	{
		toggleName(vapeHiddenModules, featureName);
		save();
	}

	private static void toggleName(Set<String> names, String featureName)
	{
		String normalized = normalizeFeatureName(featureName);
		if(!names.remove(normalized))
			names.add(normalized);
	}

	private static String normalizeFeatureName(String featureName)
	{
		return featureName.toLowerCase(Locale.ROOT);
	}

	private static void loadStringSet(JsonObject json, String key,
		Set<String> target)
	{
		if(!json.has(key) || !json.get(key).isJsonArray())
			return;
		for(var value : json.getAsJsonArray(key))
			if(value.isJsonPrimitive())
				target.add(normalizeFeatureName(value.getAsString()));
	}

	private static JsonArray toJsonArray(Set<String> values)
	{
		JsonArray array = new JsonArray();
		for(String value : values)
			array.add(value);
		return array;
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

	public record VapeFrameState(double x, double y, boolean collapsed,
		boolean visible)
	{
		public VapeFrameState(double x, double y, boolean collapsed)
		{
			this(x, y, collapsed, true);
		}
	}
}
