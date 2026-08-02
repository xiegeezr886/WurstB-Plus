package net.wurstclient.util;

import java.util.Objects;
import java.util.function.BiFunction;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.gui.screens.DeathScreen;
import net.minecraft.client.gui.screens.DisconnectedScreen;
import net.minecraft.client.gui.screens.OptionsScreen;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.EffectRenderingInventoryScreen;
import net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen;
import net.minecraft.client.gui.screens.worldselection.SelectWorldScreen;
import net.wurstclient.clickgui2.ClickGuiScreen;
import net.wurstclient.clickgui2.NavigatorScreen;
import net.wurstclient.hud2.HudEditorScreen;

public enum ScreenRegistry
{
	TITLE(TitleScreen.class, (client, parent) -> new TitleScreen()),
	WORLD_SELECTION(SelectWorldScreen.class,
		(client, parent) -> new SelectWorldScreen(parent)),
	MULTIPLAYER(JoinMultiplayerScreen.class,
		(client, parent) -> new JoinMultiplayerScreen(parent)),
	OPTIONS(OptionsScreen.class,
		(client, parent) -> new OptionsScreen(parent, client.options)),
	PAUSE(PauseScreen.class),
	DISCONNECTED(DisconnectedScreen.class),
	CONTAINER(AbstractContainerScreen.class),
	INVENTORY(EffectRenderingInventoryScreen.class),
	CHAT(ChatScreen.class),
	DEATH(DeathScreen.class),
	CLICK_GUI(ClickGuiScreen.class,
		(client, parent) -> new ClickGuiScreen()),
	NAVIGATOR(NavigatorScreen.class,
		(client, parent) -> new NavigatorScreen()),
	HUD_EDITOR(HudEditorScreen.class,
		(client, parent) -> new HudEditorScreen());

	private final Class<? extends Screen> screenType;
	private final BiFunction<Minecraft, Screen, Screen> factory;

	ScreenRegistry(Class<? extends Screen> screenType)
	{
		this(screenType, null);
	}

	ScreenRegistry(Class<? extends Screen> screenType,
		BiFunction<Minecraft, Screen, Screen> factory)
	{
		this.screenType = screenType;
		this.factory = factory;
	}

	public boolean matches(Screen screen)
	{
		return screen != null && screenType.isInstance(screen);
	}

	public boolean matchesType(Class<? extends Screen> type)
	{
		return screenType.isAssignableFrom(Objects.requireNonNull(type));
	}

	public boolean isOpen()
	{
		return matches(Minecraft.getInstance().screen);
	}

	public Screen create(Screen parent)
	{
		if(factory == null)
			throw new UnsupportedOperationException(
				name() + " requires constructor-specific data");
		return factory.apply(Minecraft.getInstance(), parent);
	}

	public void open(Screen parent)
	{
		Minecraft.getInstance().setScreen(create(parent));
	}

	public static boolean isAny(Screen screen, ScreenRegistry... types)
	{
		for(ScreenRegistry type : types)
			if(type.matches(screen))
				return true;
		return false;
	}

	public static boolean isWurstIngameScreen(Screen screen)
	{
		return screen != null && isWurstIngameScreenType(screen.getClass());
	}

	public static boolean isWurstIngameScreenType(
		Class<? extends Screen> type)
	{
		String packageName = Objects.requireNonNull(type).getPackageName();
		return isPackage(packageName, "net.wurstclient.clickgui2")
			|| isPackage(packageName, "net.wurstclient.hud2")
			|| isPackage(packageName, "net.wurstclient.options");
	}

	private static boolean isPackage(String packageName, String root)
	{
		return packageName.equals(root) || packageName.startsWith(root + ".");
	}
}
