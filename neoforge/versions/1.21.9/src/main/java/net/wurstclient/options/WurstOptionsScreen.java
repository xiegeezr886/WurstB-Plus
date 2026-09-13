/*
 * Copyright (c) 2014-2025 Wurst-Imperium and contributors.
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 */
package net.wurstclient.options;

import net.minecraft.client.gui.GuiGraphics;

import java.util.Arrays;
import java.util.List;
import java.util.function.Supplier;

import net.wurstclient.util.ScreenUtils;
import net.minecraft.client.gui.Font;
import net.wurstclient.util.render.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.InputWithModifiers;
import net.minecraft.network.chat.Component;
import net.wurstclient.WurstClient;
import net.wurstclient.commands.FriendsCmd;
import net.wurstclient.hacks.XRayHack;
import net.wurstclient.hud2.HudEditorScreen;
import net.wurstclient.other_features.VanillaSpoofOtf;
import net.wurstclient.settings.CheckboxSetting;
import net.wurstclient.util.ChatUtils;

public class WurstOptionsScreen extends Screen
{
	private Screen prevScreen;
	
	public WurstOptionsScreen(Screen prevScreen)
	{
		super(Component.literal(""));
		this.prevScreen = prevScreen;
	}
	
	@Override
	public void init()
	{
		addRenderableWidget(Button
			.builder(Component.literal("返回"), b -> minecraft.setScreen(prevScreen))
			.bounds(width / 2 - 100, height / 4 + 144 - 16, 200, 20)
			.build());
		
		addSettingButtons();
		addManagerButtons();
	}
	
	private void addSettingButtons()
	{
		WurstClient wurst = WurstClient.INSTANCE;
		FriendsCmd friendsCmd = wurst.getCmds().friendsCmd;
		CheckboxSetting middleClickFriends = friendsCmd.getMiddleClickFriends();
		VanillaSpoofOtf vanillaSpoofOtf = wurst.getOtfs().vanillaSpoofOtf;
		CheckboxSetting forceEnglish =
			wurst.getOtfs().translationsOtf.getForceEnglish();
		
		new WurstOptionsButton(-154, 24,
			() -> "Click Friends: "
				+ (middleClickFriends.isChecked() ? "ON" : "OFF"),
			middleClickFriends.getWrappedDescription(200),
			b -> middleClickFriends
				.setChecked(!middleClickFriends.isChecked()));
		
		new WurstOptionsButton(-154, 48,
			() -> "Spoof Vanilla: "
				+ (vanillaSpoofOtf.isEnabled() ? "ON" : "OFF"),
			vanillaSpoofOtf.getDescription(),
			b -> vanillaSpoofOtf.doPrimaryAction());
		
		new WurstOptionsButton(-154, 72,
			() -> "Translations: " + (!forceEnglish.isChecked() ? "ON" : "OFF"),
			"Allows text in Wurst to be displayed in other languages than"
				+ " English. It will use the same language that Minecraft is"
				+ " set to.\n\n" + "This is an experimental feature!",
			b -> forceEnglish.setChecked(!forceEnglish.isChecked()));
	}
	
	private void addManagerButtons()
	{
		XRayHack xRayHack = WurstClient.INSTANCE.getHax().xRayHack;
		
		new WurstOptionsButton(-50, 24, () -> "Keybinds",
			"Keybinds allow you to toggle any hack or command by simply"
				+ " pressing a button.",
			b -> minecraft.setScreen(new KeybindManagerScreen(this)));
		
		new WurstOptionsButton(-50, 48, () -> "X-Ray Blocks",
			"Manager for the blocks that X-Ray will show.",
			b -> xRayHack.openBlockListEditor(this));
		
		new WurstOptionsButton(-50, 72, () -> "Zoom",
			"The Zoom Manager allows you to change the zoom key and how far it"
				+ " will zoom in.",
			b -> minecraft.setScreen(new ZoomManagerScreen(this)));

		new WurstOptionsButton(-50, 96, () -> "HUD Editor",
			"Drag and reposition HUD elements on screen. Right-click to"
				+ " cycle alignment. Click to enable/disable elements.",
			b -> minecraft.setScreen(new HudEditorScreen()));
	}
	
	@Override
	public void onClose()
	{
		minecraft.setScreen(prevScreen);
	}

	@Override
	public boolean isPauseScreen()
	{
		return false;
	}
@Override
	public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTicks)
	{
		renderContents(new GuiGraphicsExtractor(graphics), mouseX, mouseY, partialTicks);
	}

	private void renderContents(GuiGraphicsExtractor context, int mouseX, int mouseY,
		float partialTicks)
	{
		renderTitles(context);
		super.render(context.getInner(), mouseX, mouseY, partialTicks);
		renderButtonTooltip(context, mouseX, mouseY);
	}
	
	private void renderTitles(GuiGraphicsExtractor context)
	{
		Font tr = minecraft.font;
		int middleX = width / 2;
		int y1 = 40;
		int y2 = height / 4 + 24 - 28;
		
		context.centeredText(tr, WurstClient.CLIENT_NAME + " 设置", middleX, y1,
			0xFFffffff);
		
		context.centeredText(tr, "Settings", middleX - 104, y2,
			0xFFcccccc);
		context.centeredText(tr, "Managers", middleX, y2,
			0xFFcccccc);
		context.centeredText(tr, "Links", middleX + 104, y2,
			0xFFcccccc);
	}
	
	private void renderButtonTooltip(GuiGraphicsExtractor context, int mouseX,
		int mouseY)
	{
		for(AbstractWidget button : ScreenUtils.getButtons(this))
		{
			if(!button.isHoveredOrFocused() || !(button instanceof WurstOptionsButton))
				continue;
			
			WurstOptionsButton woButton = (WurstOptionsButton)button;
			
			if(woButton.tooltip.isEmpty())
				continue;
			
			context.setComponentTooltipForNextFrame(font, woButton.tooltip, mouseX,
				mouseY);
			break;
		}
	}
	
	private final class WurstOptionsButton extends Button
	{
		private final Supplier<String> messageSupplier;
		private final List<Component> tooltip;
		
		public WurstOptionsButton(int xOffset, int yOffset,
			Supplier<String> messageSupplier, String tooltip,
			OnPress pressAction)
		{
			super(WurstOptionsScreen.this.width / 2 + xOffset,
				WurstOptionsScreen.this.height / 4 - 16 + yOffset, 100, 20,
				Component.literal(messageSupplier.get()), pressAction,
				Button.DEFAULT_NARRATION);
			
			this.messageSupplier = messageSupplier;
			
			if(tooltip.isEmpty())
				this.tooltip = Arrays.asList();
			else
			{
				String[] lines = ChatUtils.wrapText(tooltip, 200).split("\n");
				
				Component[] lines2 = new Component[lines.length];
				for(int i = 0; i < lines.length; i++)
					lines2[i] = Component.literal(lines[i]);
				
				this.tooltip = Arrays.asList(lines2);
			}
			
			addRenderableWidget(this);
		}
		
		@Override
		public void onPress(InputWithModifiers context)
		{
			super.onPress(context);
			setMessage(Component.literal(messageSupplier.get()));
		}

		@Override
		protected void renderContents(GuiGraphics graphics, int mouseX,
			int mouseY, float partialTicks)
		{
			renderDefaultSprite(graphics);
			graphics.drawCenteredString(font, getMessage(),
				getX() + getWidth() / 2,
				getY() + (getHeight() - font.lineHeight) / 2, getFGColor());
		}
	}
}
