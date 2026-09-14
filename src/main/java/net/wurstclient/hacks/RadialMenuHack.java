/*
 * Copyright (c) 2014-2025 Wurst-Imperium and contributors.
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 */
package net.wurstclient.hacks;

import java.util.ArrayList;
import java.util.List;

import org.lwjgl.glfw.GLFW;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.wurstclient.Category;
import net.wurstclient.DontBlock;
import net.wurstclient.SearchTags;
import net.wurstclient.WurstClient;
import net.wurstclient.events.GUIRenderListener;
import net.wurstclient.events.KeyPressListener;
import net.wurstclient.hack.Hack;
import net.wurstclient.hud.RadialMenuRenderer;
import net.wurstclient.hud.RadialMenuState;
import net.wurstclient.settings.TextFieldSetting;
import net.wurstclient.util.ChatUtils;
import net.wurstclient.util.text.WText;

/**
 * 长按 Tab 唤出的功能圆盘。
 *
 * <p>
 * 按住 Tab 超过 {@link RadialMenuState#HOLD_DELAY_MS} 才展开；在那之前松手，
 * Tab 保持原样，本功能不会触发任何动作。展开后指针方向决定选中哪一片，指针回到
 * 圆心（死区）即取消，松手执行选中的项。
 *
 * <p>
 * 选项通过设置里的「Entries」文本框配置，格式为
 * {@code 名称=命令;名称=命令}（也接受换行分隔）。命令的写法与按键绑定一致：
 * 只写一个词就是切换同名功能，带空格或点号则交给 {@code CmdProcessor} 执行，
 * 例如 {@code .t}。设置沿用工程原有的 Setting / settings.json 持久化。
 *
 * <p>
 * 需要先在 ClickGUI 的 Other 分类里开启本功能（开关状态会记进
 * enabled-hacks.json）；未开启时长按 Tab 不做任何事。
 */
@SearchTags({"radial menu", "pie menu", "wheel", "long press tab",
	"circle menu", "\u5706\u76D8", "\u6247\u5F62\u83DC\u5355"})
@DontBlock
public final class RadialMenuHack extends Hack
	implements KeyPressListener, GUIRenderListener
{
	/** 默认选项：名称=命令，分号分隔。 */
	private static final String DEFAULT_ENTRIES =
		"\u98DE\u884C=fly;\u51B2\u523A=sprint;"
			+ "\u591C\u89C6=fullbright;\u81EA\u52A8\u5D29\u5854=scaffoldwalk;"
			+ "\u5E73\u51E1\u89C6\u89D2=nametags;\u9762\u677F=.t";
	
	private final TextFieldSetting entries =
		new TextFieldSetting("Entries",
			WText.literal("\u5706\u76D8\u4E0A\u7684\u9879\u76EE\u3002"
				+ "\u683C\u5F0F\uFF1A\u540D\u79F0=\u547D\u4EE4\uFF0C"
				+ "\u7528\u5206\u53F7\u6216\u6362\u884C\u5206\u9694\u3002"
				+ "\u547D\u4EE4\u53EA\u5199\u4E00\u4E2A\u8BCD\u5C31\u662F"
				+ "\u5207\u6362\u540C\u540D\u529F\u80FD\uFF0C\u5E26"
				+ "\u7A7A\u683C\u6216\u70B9\u53F7\u5219\u6267\u884C"
				+ "\u547D\u4EE4\uFF0C\u4F8B\u5982 .t \u6216 /gamemode 1\u3002"),
			DEFAULT_ENTRIES);
	
	private RadialMenuState state =
		new RadialMenuState(parseEntries(DEFAULT_ENTRIES));
	
	private RadialMenuHack()
	{
		super("RadialMenu");
		setCategory(Category.OTHER);
		// 名字里没有 Hack 后缀，反射式的默认按键提示不会带上它，补一条
		addPossibleKeybind("RadialMenu", "Toggle RadialMenu");
		addSetting(entries);
		entries.addChangeListener(() -> state =
			new RadialMenuState(parseEntries(entries.getValue())));
	}
	
	/**
	 * 注册本功能（只做一次）。
	 *
	 * <p>
	 * 工程的 {@code HackList}/{@code OtfList} 用的是写死的字段，不编辑
	 * {@code WurstClient.java} 就没法把新功能加进去；这里改用现成的
	 * {@code HackList.registerAddonHack()} 注册，于是设置界面、enabled-hacks 和
	 * settings.json 都会自动包含它。调用点是
	 * {@code IngameHUD.onRenderGUI}，那时 {@code WurstClient.initialize()} 早已
	 * 完成，重复调用也不会重复注册。
	 */
	public static synchronized RadialMenuHack get()
	{
		WurstClient wurst = WurstClient.INSTANCE;
		
		if(wurst == null || wurst.getHax() == null)
			return null;
		
		RadialMenuHack existing = (RadialMenuHack)wurst.getHax()
			.getHackByName("RadialMenu");
		
		if(existing != null)
			return existing;
		
		RadialMenuHack hack = new RadialMenuHack();
		wurst.getHax().registerAddonHack(hack);
		return hack;
	}
	
	@Override
	protected void onEnable()
	{
		EVENTS.add(KeyPressListener.class, this);
		EVENTS.add(GUIRenderListener.class, this);
	}
	
	@Override
	protected void onDisable()
	{
		EVENTS.remove(KeyPressListener.class, this);
		EVENTS.remove(GUIRenderListener.class, this);
		
		// 先让状态收起来，再收回鼠标，否则 releaseCursor 会因为圆盘还开着而跳过
		if(state.isOpen())
			state.commit();
		
		releaseCursor();
	}
	
	@Override
	public void onKeyPress(KeyPressEvent event)
	{
		if(event.getKeyCode() != GLFW.GLFW_KEY_TAB)
			return;
		
		if(event.getAction() == GLFW.GLFW_PRESS)
		{
			// 界面打开时不抢 Tab
			if(MC.screen != null)
				return;
			
			state.press(now());
			return;
		}
		
		if(event.getAction() != GLFW.GLFW_RELEASE)
			return;
		
		if(!state.isOpen())
		{
			// 圆盘从未真正展开：把计时清掉，Tab 的原行为完全不受影响
			state.update(now(), false, 0D, 0D, 1F);
			return;
		}
		
		int selected = state.commit();
		releaseCursor();
		
		// 只有真的展开过、且指针不在死区/缝隙里，才会执行动作
		if(selected >= 0)
			runEntry(selected);
	}
	
	@Override
	public void onRenderGUI(GuiGraphics context, float partialTicks)
	{
		long now = now();
		boolean held = isTabHeld();
		
		// 鼠标位置每帧重新取，指针移动即切换选中
		double mouseX = MC.mouseHandler.xpos() / MC.getWindow().getGuiScale();
		double mouseY = MC.mouseHandler.ypos() / MC.getWindow().getGuiScale();
		
		int centreX = MC.getWindow().getGuiScaledWidth() / 2;
		int centreY = Math.round(MC.getWindow().getGuiScaledHeight() * 0.46F);
		float outerRadius = outerRadius(centreX, centreY);
		
		double[] pointer =
			RadialMenuRenderer.pointerOffset(mouseX, mouseY, centreX, centreY);
		
		state.update(now, held, pointer[0], pointer[1], outerRadius);
		
		if(state.isOpen())
			keepCursorReleased();
		else
			releaseCursor();
		
		float progress = state.progress(now);
		
		if(progress <= 0.001F)
			return;
		
		Font font = MC.font;
		RadialMenuRenderer.draw(context, font, state, centreX, centreY,
			outerRadius, progress);
	}
	
	/**
	 * 松手时执行选中的项。命令写法与按键绑定保持一致。
	 */
	private void runEntry(int index)
	{
		List<String> items = state.items();
		
		if(index < 0 || index >= items.size())
			return;
		
		String command = commandOf(entries.getValue(), index);
		
		if(command.isEmpty())
			return;
		
		try
		{
			WurstClient.INSTANCE.getCmdProcessor().process(command);
			
		}catch(RuntimeException e)
		{
			ChatUtils.error("\u5706\u76D8\u6267\u884C\u5931\u8D25: " + command);
			e.printStackTrace();
		}
	}
	
	private float outerRadius(int centreX, int centreY)
	{
		int screenWidth = MC.getWindow().getGuiScaledWidth();
		int screenHeight = MC.getWindow().getGuiScaledHeight();
		float byHeight = Math.min(centreY, screenHeight - centreY) * 0.92F;
		float byWidth = Math.min(centreX, screenWidth - centreX) * 0.88F;
		return Math.max(28F, Math.min(byHeight, byWidth));
	}
	
	/**
	 * 圆盘展开时把指针放出来，鼠标能动才能选；关闭时收回。
	 */
	private void keepCursorReleased()
	{
		if(MC.mouseHandler.isMouseGrabbed())
			MC.mouseHandler.releaseMouse();
	}
	
	private void releaseCursor()
	{
		// 圆盘还开着的时候绝不能抓回鼠标，否则指针就动不了、选不中任何一片
		if(state.isOpen() || MC.screen != null)
			return;
		
		if(!MC.mouseHandler.isMouseGrabbed())
			MC.mouseHandler.grabMouse();
	}
	
	private boolean isTabHeld()
	{
		return GLFW.glfwGetKey(MC.getWindow().getWindow(),
			GLFW.GLFW_KEY_TAB) == GLFW.GLFW_PRESS;
	}
	
	private long now()
	{
		return System.currentTimeMillis();
	}
	
	/**
	 * 解析「名称=命令」列表。没有等号时用命令本身当名称，这样随便写一个
	 * {@code fly} 也能用。
	 */
	private static List<String> parseEntries(String raw)
	{
		ArrayList<String> labels = new ArrayList<>();
		
		if(raw == null)
			return labels;
		
		for(String part : raw.split("[;\\r\\n]+"))
		{
			String entry = part.trim();
			
			if(entry.isEmpty())
				continue;
			
			String label = labelOf(entry);
			
			if(label.isEmpty() || labels.contains(label))
				continue;
			
			labels.add(label);
		}
		
		return labels;
	}
	
	/** 第 index 项的命令部分。 */
	private static String commandOf(String raw, int index)
	{
		if(raw == null)
			return "";
		
		int seen = 0;
		
		for(String part : raw.split("[;\\r\\n]+"))
		{
			String entry = part.trim();
			
			if(entry.isEmpty())
				continue;
			
			String label = labelOf(entry);
			
			if(label.isEmpty())
				continue;
			
			if(seen++ == index)
				return commandOf(entry);
		}
		
		return "";
	}
	
	private static String labelOf(String entry)
	{
		int equals = entry.indexOf('=');
		return equals < 0 ? entry.trim() : entry.substring(0, equals).trim();
	}
	
	private static String commandOf(String entry)
	{
		int equals = entry.indexOf('=');
		String command = equals < 0 ? entry.trim()
			: entry.substring(equals + 1).trim();
		
		return command.startsWith(".") ? command.substring(1) : command;
	}
}
