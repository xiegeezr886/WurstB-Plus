/*
 * Copyright (c) 2014-2025 Wurst-Imperium and contributors.
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 */
package net.wurstclient;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import net.minecraft.client.Minecraft;
import net.wurstclient.altmanager.AltManager;
import net.wurstclient.altmanager.Encryption;
import net.wurstclient.clickgui2.ClickGui;
import net.wurstclient.clickgui2.GuiPreferences;
import net.wurstclient.command.CmdList;
import net.wurstclient.command.CmdProcessor;
import net.wurstclient.command.Command;
import net.wurstclient.event.EventManager;
import net.wurstclient.events.ChatOutputListener;
import net.wurstclient.events.GUIRenderListener;
import net.wurstclient.events.KeyPressListener;
import net.wurstclient.events.PacketOutputListener;
import net.wurstclient.events.PostMotionListener;
import net.wurstclient.events.RenderListener;
import net.wurstclient.events.UpdateListener;
import net.wurstclient.hack.Hack;
import net.wurstclient.hack.HackConflictManager;
import net.wurstclient.hack.HackLifecycleManager;
import net.wurstclient.hack.HackList;
import net.wurstclient.hud.IngameHUD;
import net.wurstclient.hud2.HudManager;
import net.wurstclient.hud2.ClientMetricsManager;
import net.wurstclient.keybinds.KeybindList;
import net.wurstclient.keybinds.KeybindProcessor;
import net.wurstclient.addon.AddonManager;
import net.wurstclient.discord.DiscordRpcManager;
import net.wurstclient.macros.MacroManager;
import net.wurstclient.proxy.ProxyManager;
import net.wurstclient.waypoints.WaypointsManager;
import net.wurstclient.mixinterface.IMinecraftClient;
import net.wurstclient.other_feature.OtfList;
import net.wurstclient.other_feature.OtherFeature;
import net.wurstclient.settings.SettingsFile;
import net.wurstclient.update.ProblematicResourcePackDetector;
import net.wurstclient.update.WurstUpdater;
import net.wurstclient.util.json.JsonException;
import net.wurstclient.util.render.AsyncTextureLoader;
import net.wurstclient.music.NeteaseMusicPlayer;
import net.wurstclient.util.render.PostEffectQueue;
import net.wurstclient.util.EntitySnapshotManager;
import net.wurstclient.util.inventory.InventoryActionQueue;

public enum WurstClient
{
	INSTANCE;
	
	public static Minecraft MC;
	public static IMinecraftClient IMC;
	
	public static final String VERSION = "1.6.0";
	public static final String MC_VERSION = "1.20.1";
	public static final String CLIENT_NAME = "WurstB+ Plus";
	public static final String MOD_ID = "wurstpenguin";
	
	private EventManager eventManager;
	private HackConflictManager hackConflictManager;
	private HackLifecycleManager hackLifecycleManager;
	private AltManager altManager;
	private HackList hax;
	private CmdList cmds;
	private OtfList otfs;
	private SettingsFile settingsFile;
	private Path settingsProfileFolder;
	private KeybindList keybinds;
	private ClickGui gui;
	private GuiPreferences guiPreferences;
	private CmdProcessor cmdProcessor;
	private IngameHUD hud;
	private HudManager hudManager;
	private ClientMetricsManager clientMetricsManager;
	private EntitySnapshotManager entitySnapshotManager;
	private PostEffectQueue postEffectQueue;
	private InventoryActionQueue inventoryActionQueue;
	private RotationFaker rotationFaker;
	private FriendsList friends;
	private WurstTranslator translator;
	
	private boolean enabled = true;
	private static boolean guiInitialized;
	private WurstUpdater updater;
	private ProblematicResourcePackDetector problematicPackDetector;
	private Path wurstFolder;
	private MacroManager macroManager;
	private WaypointsManager waypointsManager;
	private DiscordRpcManager discordRpcManager;
	private AddonManager addonManager;
	private ProxyManager proxyManager;
	private boolean shutdownHookRegistered;
	
	public void initialize()
	{
		System.out.println("Starting " + CLIENT_NAME + "...");
		
		MC = Minecraft.getInstance();
		IMC = (IMinecraftClient)MC;
		wurstFolder = createWurstFolder();
		guiPreferences = new GuiPreferences(wurstFolder);
		
		eventManager = new EventManager(this);
		hackConflictManager = new HackConflictManager();
		rotationFaker = new RotationFaker();
		eventManager.add(PostMotionListener.class, rotationFaker);
		eventManager.add(PacketOutputListener.class, rotationFaker);

		clientMetricsManager = new ClientMetricsManager();
		clientMetricsManager.start();
		entitySnapshotManager = new EntitySnapshotManager();
		entitySnapshotManager.start();
		postEffectQueue = new PostEffectQueue();
		inventoryActionQueue = new InventoryActionQueue();
		inventoryActionQueue.start();
		hudManager = new HudManager();
		
		Path enabledHacksFile = wurstFolder.resolve("enabled-hacks.json");
		hax = new HackList(enabledHacksFile);
		hackLifecycleManager = new HackLifecycleManager(hax, eventManager);
		
		cmds = new CmdList();
		
		otfs = new OtfList();

		addonManager = new AddonManager();
		addonManager.discoverAddons();

		Path settingsFile = wurstFolder.resolve("settings.json");
		settingsProfileFolder = wurstFolder.resolve("settings");
		this.settingsFile = new SettingsFile(settingsFile, hax, cmds, otfs);
		this.settingsFile.load();
		hax.tooManyHaxHack.loadBlockedHacksFile();

		Path keybindsFile = wurstFolder.resolve("keybinds.json");
		keybinds = new KeybindList(keybindsFile);

		gui = new ClickGui();

		Path friendsFile = wurstFolder.resolve("friends.json");
		friends = new FriendsList(friendsFile);
		friends.load();

		translator = new WurstTranslator();

		cmdProcessor = new CmdProcessor(cmds);
		eventManager.add(ChatOutputListener.class, cmdProcessor);

		KeybindProcessor keybindProcessor =
			new KeybindProcessor(hax, keybinds, cmdProcessor);
		eventManager.add(KeyPressListener.class, keybindProcessor);

		macroManager = new MacroManager(wurstFolder, cmdProcessor);
		eventManager.add(KeyPressListener.class, macroManager);

		waypointsManager = new WaypointsManager(wurstFolder);
		eventManager.add(RenderListener.class, waypointsManager);

		discordRpcManager = new DiscordRpcManager();
		discordRpcManager.start();

		proxyManager = new ProxyManager(wurstFolder);
		
		hud = new IngameHUD();
		eventManager.add(GUIRenderListener.class, hud);

		hudManager.start();
		
		updater = new WurstUpdater();
		eventManager.add(UpdateListener.class, updater);
		
		problematicPackDetector = new ProblematicResourcePackDetector();
		problematicPackDetector.start();
		
		Path altsFile = wurstFolder.resolve("alts.encrypted_json");
		Path encFolder = Encryption.chooseEncryptionFolder();
		altManager = new AltManager(altsFile, encFolder);

		if(!shutdownHookRegistered)
		{
			shutdownHookRegistered = true;
			Runtime.getRuntime().addShutdownHook(new Thread(this::shutdown,
				"WurstB-Shutdown"));
		}
		
	}

	public void shutdown()
	{
		if(discordRpcManager != null)
			discordRpcManager.shutdown();
		if(clientMetricsManager != null && eventManager != null)
			clientMetricsManager.stop();
		if(entitySnapshotManager != null && eventManager != null)
			entitySnapshotManager.stop();
		if(inventoryActionQueue != null && eventManager != null)
			inventoryActionQueue.stop();
		NeteaseMusicPlayer.INSTANCE.shutdown();
		AsyncTextureLoader.shutdown();
	}
	
	private Path createWurstFolder()
	{
		Path dotMinecraftFolder = MC.gameDirectory.toPath().normalize();
		Path wurstFolder = dotMinecraftFolder.resolve("wurst");
		
		try
		{
			Files.createDirectories(wurstFolder);
			
		}catch(IOException e)
		{
			throw new RuntimeException(
				"Couldn't create .minecraft/wurst folder.", e);
		}
		
		return wurstFolder;
	}
	
	public String translate(String key, Object... args)
	{
		return translator.translate(key, args);
	}
	
	public EventManager getEventManager()
	{
		return eventManager;
	}
	
	public HackConflictManager getHackConflictManager()
	{
		return hackConflictManager;
	}
	
	public void saveSettings()
	{
		settingsFile.save();
	}
	
	public ArrayList<Path> listSettingsProfiles()
	{
		if(!Files.isDirectory(settingsProfileFolder))
			return new ArrayList<>();
		
		try(Stream<Path> files = Files.list(settingsProfileFolder))
		{
			return files.filter(Files::isRegularFile)
				.collect(Collectors.toCollection(ArrayList::new));
			
		}catch(IOException e)
		{
			throw new RuntimeException(e);
		}
	}
	
	public void loadSettingsProfile(String fileName)
		throws IOException, JsonException
	{
		settingsFile.loadProfile(settingsProfileFolder.resolve(fileName));
	}
	
	public void saveSettingsProfile(String fileName)
		throws IOException, JsonException
	{
		settingsFile.saveProfile(settingsProfileFolder.resolve(fileName));
	}
	
	public HackList getHax()
	{
		return hax;
	}
	
	public CmdList getCmds()
	{
		return cmds;
	}
	
	public OtfList getOtfs()
	{
		return otfs;
	}
	
	public Feature getFeatureByName(String name)
	{
		if(name == null || name.isEmpty())
			return null;

		Hack hack = getHax().getHackByName(name);
		if(hack != null)
			return hack;

		String cmdName = name.startsWith(".") ? name.substring(1) : name;
		Command cmd = getCmds().getCmdByName(cmdName);
		if(cmd != null)
			return cmd;

		OtherFeature otf = getOtfs().getOtfByName(name);
		return otf;
	}
	
	public KeybindList getKeybinds()
	{
		return keybinds;
	}
	
	public ClickGui getGui()
	{
		if(!guiInitialized)
		{
			guiInitialized = true;
			gui.init();
		}
		
		return gui;
	}
	
	public CmdProcessor getCmdProcessor()
	{
		return cmdProcessor;
	}
	
	public IngameHUD getHud()
	{
		return hud;
	}
	
	public RotationFaker getRotationFaker()
	{
		return rotationFaker;
	}
	
	public FriendsList getFriends()
	{
		return friends;
	}
	
	public WurstTranslator getTranslator()
	{
		return translator;
	}
	
	public boolean isEnabled()
	{
		return enabled;
	}
	
	public void setEnabled(boolean enabled)
	{
		this.enabled = enabled;
		
		if(!enabled)
		{
			hax.panicHack.setEnabled(true);
			hax.panicHack.onUpdate();
		}
	}
	
	public WurstUpdater getUpdater()
	{
		return updater;
	}
	
	public ProblematicResourcePackDetector getProblematicPackDetector()
	{
		return problematicPackDetector;
	}
	
	public Path getWurstFolder()
	{
		return wurstFolder;
	}

	public Path getSettingsProfileFolder()
	{
		return settingsProfileFolder;
	}

	public GuiPreferences getGuiPreferences()
	{
		return guiPreferences;
	}

	public HudManager getHudManager()
	{
		return hudManager;
	}

	public MacroManager getMacroManager()
	{
		return macroManager;
	}

	public WaypointsManager getWaypointsManager()
	{
		return waypointsManager;
	}

	public AddonManager getAddonManager()
	{
		return addonManager;
	}

	public ProxyManager getProxyManager()
	{
		return proxyManager;
	}

	public ClientMetricsManager getClientMetricsManager()
	{
		return clientMetricsManager;
	}

	public EntitySnapshotManager getEntitySnapshotManager()
	{
		return entitySnapshotManager;
	}

	public PostEffectQueue getPostEffectQueue()
	{
		return postEffectQueue;
	}

	public InventoryActionQueue getInventoryActionQueue()
	{
		return inventoryActionQueue;
	}
	
	public AltManager getAltManager()
	{
		return altManager;
	}
}
