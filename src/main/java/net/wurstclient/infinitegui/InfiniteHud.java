package net.wurstclient.infinitegui;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import org.lwjgl.glfw.GLFW;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.wurstclient.WurstClient;
import net.wurstclient.events.GUIRenderListener;
import net.wurstclient.events.MouseButtonListener;

/**
 * 无限GUI 的 HUD 项管理器（上游 {@code ItemManager} 的子集：注册、更新、每帧分发、配置存取）。
 *
 * <p>
 * 本移植只带无限GUI 真正有的、且**值得换**的那几项：{@link TimeWindow}（时间显示）、
 * {@link FpsWindow}（FPS显示）、{@link CpsWindow}（CPS显示）。这三项对应客户端原有的
 * clock / fps / cps 三个元素，那三个元素已随之删除（"替换源码"，不是叠一层）。
 *
 * <p>
 * <b>键盘显示与通知不换</b>：WurstB+ 自己那套（五排键帽 W/A S D/SPACE/SNK·SPR/LMB·RMB、键帽
 * 边长可调、玻璃键帽）比无限GUI 的对应项好，用户明确要求保留；通知同理（WurstB+ 是 Compose
 * 卡片流，无限GUI 只是一条文字 toast）。上游其余信息项（计数器 / 文件数量 / 文本 / B站粉丝 /
 * B站弹幕 / 音乐信息）本批未移植。</p>
 *
 * <p>
 * 配置沿用上游的 JSON 形状（{@code {"Items":[{"type":…,"isEnabled":…}]}}），只是放在本工程自己的
 * 目录里：{@code <wurst>/infinitegui/profile.json}。上游那一项默认是关的，这里**默认开**——
 * 否则"替换了源码"会表现成"那几个 HUD 项凭空消失"。</p>
 */
public final class InfiniteHud implements GUIRenderListener, MouseButtonListener
{
	private static final Gson GSON =
		new GsonBuilder().setPrettyPrinting().create();

	private final List<InfiniteWindow> windows = new ArrayList<>();
	private Path file;

	/** 拖动状态：有光标的界面里按住左键拖窗口（游戏内光标被抓取，不适合拖）。 */
	private InfiniteWindow dragging;
	private int dragOffsetX;
	private int dragOffsetY;
	private boolean dragButtonDown;

	public void start()
	{
		file = WurstClient.INSTANCE.getWurstFolder()
			.resolve("infinitegui").resolve("profile.json");

		windows.add(new TimeWindow());
		windows.add(new FpsWindow());
		windows.add(new CpsWindow());

		applyDefaults();
		load();

		WurstClient.INSTANCE.getEventManager().add(GUIRenderListener.class,
			this);
		WurstClient.INSTANCE.getEventManager().add(MouseButtonListener.class,
			this);
	}

	public void stop()
	{
		save();
		WurstClient.INSTANCE.getEventManager().remove(GUIRenderListener.class,
			this);
		WurstClient.INSTANCE.getEventManager().remove(MouseButtonListener.class,
			this);
	}

	/** 上游默认全是关的；这里默认开，并把四块摆在左上角一列（可拖）。 */
	private void applyDefaults()
	{
		int y = 12;
		for(InfiniteWindow window : windows)
		{
			window.setEnabled(true);
			window.moveTo(12, y);
			y += window.getHeight() + 4;
		}
	}

	public List<InfiniteWindow> getWindows()
	{
		return java.util.Collections.unmodifiableList(windows);
	}

	@Override
	public void onRenderGUI(GuiGraphics graphics, float partialTicks)
	{
		Minecraft minecraft = Minecraft.getInstance();
		double guiScale = Math.max(1, minecraft.getWindow().getGuiScale());
		int mouseX = (int)(minecraft.mouseHandler.xpos() / guiScale);
		int mouseY = (int)(minecraft.mouseHandler.ypos() / guiScale);

		updateDragging(minecraft, mouseX, mouseY);

		for(InfiniteWindow window : windows)
			window.render(graphics, mouseX, mouseY, partialTicks);
	}

	/**
	 * 拖动只在有光标的界面里生效（HUD 编辑器 / 暂停菜单 / 标题界面…）。游戏内鼠标被游戏锁住，
	 * 那时不响应，免得打着游戏把窗口拖走。
	 */
	private void updateDragging(Minecraft minecraft, int mouseX, int mouseY)
	{
		boolean canDrag = minecraft.screen != null;
		boolean down = canDrag
			&& GLFW.glfwGetMouseButton(minecraft.getWindow().getWindow(),
				GLFW.GLFW_MOUSE_BUTTON_LEFT) == GLFW.GLFW_PRESS;

		if(down && !dragButtonDown)
		{
			dragging = null;
			for(InfiniteWindow window : windows)
				if(window.isEnabled() && window.contains(mouseX, mouseY))
				{
					dragging = window;
					dragOffsetX = mouseX - window.getX();
					dragOffsetY = mouseY - window.getY();
					break;
				}
		}

		if(down && dragging != null)
			dragging.moveTo(mouseX - dragOffsetX, mouseY - dragOffsetY);

		if(!down && dragButtonDown && dragging != null)
		{
			dragging = null;
			save();
		}

		dragButtonDown = down;
	}

	@Override
	public void onMouseButton(int button, int action)
	{
		if(action != GLFW.GLFW_PRESS)
			return;

		for(InfiniteWindow window : windows)
			if(window instanceof CpsWindow cps && cps.isEnabled())
				cps.onClick(button);
	}

	public void save()
	{
		try
		{
			Files.createDirectories(file.getParent());

			JsonObject root = new JsonObject();
			JsonArray items = new JsonArray();
			for(InfiniteWindow window : windows)
			{
				JsonObject node = new JsonObject();
				window.save(node);
				items.add(node);
			}
			root.add("Items", items);

			Files.writeString(file, GSON.toJson(root), StandardCharsets.UTF_8);

		}catch(IOException | RuntimeException e)
		{
			System.out.println("[无限GUI] 配置写入失败：" + e);
		}
	}

	private void load()
	{
		if(!Files.isRegularFile(file))
			return;

		try
		{
			String text = Files.readString(file, StandardCharsets.UTF_8);
			JsonObject root = JsonParser.parseString(text).getAsJsonObject();
			if(!root.has("Items"))
				return;

			for(JsonElement element : root.getAsJsonArray("Items"))
			{
				JsonObject node = element.getAsJsonObject();
				if(!node.has("type"))
					continue;

				String type = node.get("type").getAsString();
				for(InfiniteWindow window : windows)
					if(window.getType().equals(type))
					{
						window.load(node);
						break;
					}
			}

		}catch(IOException | RuntimeException e)
		{
			System.out.println("[无限GUI] 配置读取失败，用默认值：" + e);
		}
	}
}
