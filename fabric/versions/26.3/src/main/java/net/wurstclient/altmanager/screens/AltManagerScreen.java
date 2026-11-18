/*
 * Copyright (c) 2014-2025 Wurst-Imperium and contributors.
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 */
package net.wurstclient.altmanager.screens;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;


import com.google.gson.JsonObject;

import net.wurstclient.util.render.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.AlertScreen;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.wurstclient.gui.opai.OpaiSelectionMenu;
import net.wurstclient.WurstClient;
import net.wurstclient.altmanager.*;
import net.wurstclient.mixinterface.IMinecraftClient;
import net.wurstclient.util.MultiProcessingUtils;
import net.wurstclient.util.json.JsonException;
import net.wurstclient.util.json.JsonUtils;
import net.wurstclient.util.json.WsonObject;

/** Shared Opai list presentation over the existing Wurst account backend. */
public final class AltManagerScreen extends Screen
{
	private static final HashSet<Alt> failedLogins = new HashSet<>();
	
	private final Screen prevScreen;
	private final AltManager altManager;
	
	private Alt selectedAlt;
	private String statusMessage = "";
	private boolean shouldAsk = true;
	private OpaiSelectionMenu menu;
	private static final Identifier ACCOUNT_ICON =
		Identifier.fromNamespaceAndPath("wurst", "textures/gui/fdp/user.png");
	
	private Button useButton;
	private Button starButton;
	private Button editButton;
	private Button deleteButton;
	
	private Button importButton;
	private Button exportButton;
	private Button logoutButton;
	private Button backButton;
	private Button clearButton;
	private Button randomButton;
	
	public AltManagerScreen(Screen prevScreen, AltManager altManager)
	{
		super(Component.literal("账号管理"));
		this.prevScreen = prevScreen;
		this.altManager = altManager;
	}
	
	@Override
	public void init()
	{
		WurstClient wurst = WurstClient.INSTANCE;
		
		Exception folderException = altManager.getFolderException();
		if(folderException != null && shouldAsk)
		{
			Component title = Component.literal(
				wurst.translate("gui.wurst.altmanager.folder_error.title"));
			Component message = Component.literal(wurst.translate(
				"gui.wurst.altmanager.folder_error.message", folderException));
			Component buttonText = Component.translatable("gui.done");
			
			// This just sets shouldAsk to false and closes the message.
			Runnable action = this::dismissFolderError;
			
			AlertScreen screen =
				new AlertScreen(action, title, message, buttonText, false);
			minecraft.setScreenAndShow(screen);
			
		}
		
		useButton = button("登录", b -> pressLogin());
		Button add = button("添加账号", b -> minecraft.setScreenAndShow(new AddAltScreen(this, altManager)));
		Button direct = button("直接登录", b -> minecraft.setScreenAndShow(new DirectLoginScreen(this)));
		deleteButton = button("删除", b -> pressDelete());
		logoutButton = button("恢复会话", b -> pressLogout());
		backButton = button("返回", b -> onClose());
		editButton = button("编辑", b -> pressEdit());
		starButton = button("收藏", b -> pressFavorite());
		randomButton = button("随机账号", b -> pressRandom());
		clearButton = button("清空列表", b -> pressClear());
		importButton = button("导入", b -> pressImportAlts());
		exportButton = button("导出", b -> pressExportAlts());
		Alt previousSelection = selectedAlt;
		menu = new OpaiSelectionMenu(this, "账号管理", this::accountRows,
			item -> { selectedAlt = (Alt)item; updateAltButtons(); },
			this::pressLogin, () -> minecraft.setScreenAndShow(prevScreen),
			List.of(useButton, add, direct, deleteButton, logoutButton, backButton,
				editButton, starButton, randomButton, clearButton, importButton, exportButton), 3, 3);
		if(altManager.getList().contains(previousSelection)) menu.selectItem(previousSelection);
		menu.subtitle(() -> statusMessage.isBlank()
			? "当前账号：" + minecraft.getUser().getName() : statusMessage);
		setFocused(null);
		updateAltButtons();
	}

	private Button button(String label, Button.OnPress action)
	{
		return Button.builder(Component.literal(label), action).build();
	}

	private List<OpaiSelectionMenu.Row> accountRows()
	{
		List<OpaiSelectionMenu.Row> rows = new ArrayList<>();
		String current = minecraft.getUser().getName();
		for(Alt alt : altManager.getList())
		{
			String detail = alt.isCracked() ? "离线账号" : "正版账号";
			if(alt.isFavorite()) detail += " · 已收藏";
			String extra = failedLogins.contains(alt) ? "登录失败"
				: current.equalsIgnoreCase(alt.getName()) ? "当前使用" : "";
			rows.add(new OpaiSelectionMenu.Row(alt, alt.getDisplayName(), detail,
				extra, ACCOUNT_ICON, true));
		}
		return rows;
	}

	private void updateAltButtons()
	{
		boolean altSelected = selectedAlt != null
			&& altManager.getList().contains(selectedAlt);
		useButton.active = altSelected;
		starButton.active = altSelected;
		editButton.active = altSelected;
		deleteButton.active = altSelected;
		clearButton.active = !altManager.getList().isEmpty();
		randomButton.active = true;
		logoutButton.active =
			((IMinecraftClient)minecraft).getWurstSession() != null;
		backButton.active = true;
		boolean windowMode = !minecraft.options.fullscreen().get();
		importButton.active = windowMode;
		exportButton.active = windowMode;
		starButton.setMessage(Component.literal(altSelected && selectedAlt.isFavorite()
			? "取消收藏" : "收藏"));
	}
	
	@Override public boolean keyPressed(net.minecraft.client.input.KeyEvent event) { return keyPressed(event.key(), event.keycode(), event.modifiers()); }
public boolean keyPressed(int keyCode, int scanCode, int modifiers)
	{
		return menu != null && menu.key(keyCode);
	}

	@Override public boolean mouseClicked(net.minecraft.client.input.MouseButtonEvent event, boolean doubleClick) { return mouseClicked(event.x(), event.y(), event.button()); }
public boolean mouseClicked(double x, double y, int button)
	{
		return menu != null && menu.click(x, y, button);
	}

	@Override
	public boolean mouseScrolled(double x, double y, double horizontal, double delta)
	{
		return menu != null && menu.scroll(x, y, delta);
	}

	private void pressLogin()
	{
		Alt alt = selectedAlt;
		if(alt == null || !altManager.getList().contains(alt))
			return;
		login(alt);
	}

	private void login(Alt alt)
	{
		try
		{
			altManager.login(alt);
			failedLogins.remove(alt);
			statusMessage = "已登录：" + alt.getDisplayName();
			
		}catch(LoginException e)
		{
			failedLogins.add(alt);
			statusMessage = e.getMessage() == null ? "登录失败。" : e.getMessage();
		}
		updateAltButtons();
	}
	
	private void pressLogout()
	{
		((IMinecraftClient)minecraft).setWurstSession(null);
		statusMessage = "已恢复原会话。";
		updateAltButtons();
	}
	
	private void pressFavorite()
	{
		Alt alt = selectedAlt;
		if(alt == null)
			return;
		
		altManager.toggleFavorite(alt);
		statusMessage = alt.isFavorite() ? "已添加收藏。" : "已取消收藏。";
		updateAltButtons();
	}
	
	private void pressEdit()
	{
		Alt alt = selectedAlt;
		if(alt == null)
			return;
		
		minecraft.setScreenAndShow(new EditAltScreen(this, altManager, alt));
	}
	
	private void pressDelete()
	{
		Alt alt = selectedAlt;
		if(alt == null)
			return;
		
		Component text = Component.literal("确定要删除此账号？");
		
		String altName = alt.getDisplayName();
		Component message = Component.literal(WurstClient.tr(
			"\"%s\" will be lost forever! (A long time!)", altName));
		
		ConfirmScreen screen = new ConfirmScreen(this::confirmRemove, text,
			message, Component.literal("删除"), Component.literal("取消"));
		minecraft.setScreenAndShow(screen);
	}

	private void pressClear()
	{
		if(altManager.getList().isEmpty())
			return;
		ConfirmScreen screen = new ConfirmScreen(confirmed -> {
			if(confirmed)
			{
				altManager.clear();
				selectedAlt = null;
				statusMessage = "账号列表已清空。";
			}
			minecraft.setScreenAndShow(this);
		}, Component.literal("确定清空已保存的账号？"),
			Component.literal("当前登录会话将会保留。"),
			Component.literal("清空"), Component.literal("取消"));
		minecraft.setScreenAndShow(screen);
	}

	private void pressRandom()
	{
		Alt alt = new CrackedAlt(NameGenerator.generateName());
		login(alt);
	}
	
	private void pressImportAlts()
	{
		try
		{
			Process process = MultiProcessingUtils.startProcessWithIO(
				ImportAltsFileChooser.class,
				WurstClient.INSTANCE.getWurstFolder().toString());
			
			Path path = getFileChooserPath(process);
			process.waitFor();
			
			if(path.getFileName().toString().endsWith(".json"))
				importAsJSON(path);
			else
				importAsTXT(path);
			statusMessage = "账号已导入。";
				updateAltButtons();
			
		}catch(IOException | InterruptedException | JsonException e)
		{
			statusMessage = "文件操作已取消或失败。";
			if(e instanceof InterruptedException) Thread.currentThread().interrupt();
		}
	}
	
	private void importAsJSON(Path path) throws IOException, JsonException
	{
		WsonObject wson = JsonUtils.parseFileToObject(path);
		ArrayList<Alt> alts = AltsFile.parseJson(wson);
		altManager.addAll(alts);
	}
	
	private void importAsTXT(Path path) throws IOException
	{
		List<String> lines = Files.readAllLines(path);
		ArrayList<Alt> alts = new ArrayList<>();
		
		for(String line : lines)
		{
			String[] data = line.split(":");
			
			switch(data.length)
			{
				case 1:
				alts.add(new CrackedAlt(data[0]));
				break;
				
				case 2:
				alts.add(new MojangAlt(data[0], data[1]));
				break;
			}
		}
		
		altManager.addAll(alts);
	}
	
	private void pressExportAlts()
	{
		try
		{
			Process process = MultiProcessingUtils.startProcessWithIO(
				ExportAltsFileChooser.class,
				WurstClient.INSTANCE.getWurstFolder().toString());
			
			Path path = getFileChooserPath(process);
			
			process.waitFor();
			
			if(path.getFileName().toString().endsWith(".json"))
				exportAsJSON(path);
			else
				exportAsTXT(path);
			statusMessage = "账号已导出。";
			
		}catch(IOException | InterruptedException | JsonException e)
		{
			statusMessage = "文件操作已取消或失败。";
			if(e instanceof InterruptedException) Thread.currentThread().interrupt();
		}
	}
	
	private Path getFileChooserPath(Process process) throws IOException
	{
		try(BufferedReader bf =
			new BufferedReader(new InputStreamReader(process.getInputStream(),
				StandardCharsets.UTF_8)))
		{
			String response = bf.readLine();
			
			if(response == null)
				throw new IOException("No response from FileChooser");
			
			try
			{
				return Paths.get(response);
				
			}catch(InvalidPathException e)
			{
				throw new IOException(
					"Response from FileChooser is not a valid path");
			}
		}
	}
	
	private void exportAsJSON(Path path) throws IOException, JsonException
	{
		JsonObject json = AltsFile.createJson(altManager);
		JsonUtils.toJson(json, path);
	}
	
	private void exportAsTXT(Path path) throws IOException
	{
		List<String> lines = new ArrayList<>();
		
		for(Alt alt : altManager.getList())
			lines.add(alt.exportAsTXT());
		
		Files.write(path, lines);
	}
	
	private void dismissFolderError()
	{
		shouldAsk = false;
		minecraft.setScreenAndShow(this);
	}
	
	private void confirmRemove(boolean confirmed)
	{
		Alt alt = selectedAlt;
		if(alt == null)
			return;
		
		if(confirmed)
		{
			altManager.remove(alt);
			selectedAlt = null;
			statusMessage = "账号已删除。";
		}
		updateAltButtons();
		
		minecraft.setScreenAndShow(this);
	}
	
	@Override public void extractRenderState(net.minecraft.client.gui.GuiGraphicsExtractor context, int mouseX, int mouseY, float partialTicks) { render(new GuiGraphicsExtractor(context), mouseX, mouseY, partialTicks); }
public void render(GuiGraphicsExtractor context, int mouseX, int mouseY, float partialTicks)
	{
		updateAltButtons();
		if(menu != null) menu.render(context, mouseX, mouseY);
	}

	@Override
	public void onClose()
	{
		if(menu != null) menu.close();
		else minecraft.setScreenAndShow(prevScreen);
	}
}
