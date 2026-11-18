package net.wurstclient.gui.title;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import com.mojang.realmsclient.RealmsMainScreen;
import net.minecraft.SharedConstants;
import net.minecraft.client.Minecraft;
import net.wurstclient.util.render.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.resources.Identifier;
import net.wurstclient.WurstClient;
import net.wurstclient.altmanager.screens.AltManagerScreen;
import net.wurstclient.gui.MainMenuBackground;
import net.wurstclient.hud2.nanovg.NanoVGUI;
import net.wurstclient.util.ScreenRegistry;
import net.wurstclient.gui.opai.OpaiMenuMotion;
import net.wurstclient.gui.opai.OpaiMenuFade;
import net.minecraft.util.Util;

/** Native menu with the user's original sizes, material and icons. */
public final class WurstTitleMenu
{
    private static final Identifier LOGO = Identifier.fromNamespaceAndPath("wurstpenguin", "title-nextgen/wurst-logo.png");
    private static final Identifier LOGO_GLOW = Identifier.fromNamespaceAndPath("wurstpenguin", "title-nextgen/wurst-logo-glow.png");
    private final Screen parent;
    private final List<WurstTitleButton> main = new ArrayList<>();
    private final List<WurstTitleButton> tools = new ArrayList<>();
    private Minecraft minecraft;
    private TitleMenuLayout layout;
    private boolean toolsShown, switching;
    private long started, switched, pageStarted;
    private WurstTitleButton account;
    private OpaiMenuFade fade;
    private Runnable destination;

    public WurstTitleMenu(Screen parent) { this.parent = parent; }
    public void init(Minecraft minecraft, int width, int height, Consumer<AbstractWidget> add)
    {
        this.minecraft = minecraft;
        long now = Util.getMillis();
        boolean returning = OpaiMenuMotion.enter(false, now) != null;
        fade = returning ? new OpaiMenuFade(true, now) : null;
        destination = null;
        layout = TitleMenuLayout.of(width, height);
        main.clear(); tools.clear();
        started = System.nanoTime();
        pageStarted = started + 700_000_000L;
        if(returning) started = pageStarted = System.nanoTime() - 3_000_000_000L;
        add(main, add, "单人游戏", "singleplayer", () -> openList(() -> ScreenRegistry.WORLD_SELECTION.open(parent)), false);
        add(main, add, "多人游戏", "multiplayer", () -> openList(() -> ScreenRegistry.MULTIPLAYER.open(parent)), false);
        add(main, add, "其他", "options", this::switchPage, false);
        add(tools, add, "游戏设置", "options", () -> ScreenRegistry.OPTIONS.open(parent), false);
        add(tools, add, "账号管理", "user", this::openAccounts, false);
        add(tools, add, "Realms", "realms", () -> minecraft.setScreenAndShow(new RealmsMainScreen(parent)), false);
        add(tools, add, "ClickGUI", "info", () -> ScreenRegistry.CLICK_GUI.open(parent), false);
        add(tools, add, "Navigator", "navigator", () -> ScreenRegistry.NAVIGATOR.open(parent), false);
        add(tools, add, "返回", null, this::switchPage, false);
        add(tools, add, "退出", "exit", minecraft::stop, true);
        account = new WurstTitleButton(Math.min(40, layout.size()), "账号", icon("user"), this::openAccounts, false);
        account.presentation(width - layout.margin() - account.getWidth(), layout.margin(), 0, false);
        add.accept(account);
        net.wurstclient.util.render.V152Gpu.linear(minecraft.getTextureManager().getTexture(LOGO));
        net.wurstclient.util.render.V152Gpu.linear(minecraft.getTextureManager().getTexture(LOGO_GLOW));
    }
    private void openAccounts()
    {
        openList(() -> minecraft.setScreenAndShow(new AltManagerScreen(parent, WurstClient.INSTANCE.getAltManager())));
    }
    private void openList(Runnable action)
    {
        if(destination != null) return;
        destination = action;
        fade = new OpaiMenuFade(false, Util.getMillis());
        parent.setFocused(null);
        main.forEach(b -> b.active = false);
        tools.forEach(b -> b.active = false);
        account.active = false;
    }
    private void add(List<WurstTitleButton> page, Consumer<AbstractWidget> add,
        String text, String name, Runnable action, boolean dangerous)
    {
        Identifier texture = name == null ? null : icon(name);
        if(texture != null) net.wurstclient.util.render.V152Gpu.linear(minecraft.getTextureManager().getTexture(texture));
        WurstTitleButton button = new WurstTitleButton(layout.size(), text, texture, action, dangerous);
        button.presentation(0, 0, 0, false);
        page.add(button); add.accept(button);
    }
    private void switchPage()
    {
        if(switching) return;
        switching = true;
        switched = System.nanoTime();
        main.forEach(b -> b.active = false);
        tools.forEach(b -> b.active = false);
        parent.setFocused(null);
    }
    private void animate(List<WurstTitleButton> page, float elapsed, boolean outgoing, boolean interactive)
    {
        for(int i = 0; i < page.size(); i++)
        {
            float t = clamp((elapsed - i * 100) / 400);
            float progress = outgoing ? 1 - TitleMenuLayout.backIn(t) : TitleMenuLayout.backOut(t);
            page.get(i).presentation(layout.x(i, page.size()) - (1 - progress) * Math.min(200, layout.width() * .4F),
                layout.y(i, page.size()), progress, interactive && t >= 1);
        }
    }
    public void render(GuiGraphicsExtractor graphics, int mouseX, int mouseY,
        float partialTicks, int width, int height)
    {
        long now = System.nanoTime();
        long fadeNow = Util.getMillis();
        if(fade != null && destination == null && fade.finished(fadeNow))
        {
            fade = null;
            started = pageStarted = now - 3_000_000_000L;
        }
        if(destination != null && fade.finished(fadeNow))
        {
            NanoVGUI.pass(graphics, () -> MainMenuBackground.render(width, height, 255));
            Runnable next = destination;
            destination = null;
            next.run();
            return;
        }
        boolean flipping = fade != null;
        final float menuOpacity = fade == null ? 1 : fade.opacity(fadeNow);
        if(switching)
        {
            float elapsed = (now - switched) / 1_000_000F;
            int delay = toolsShown ? 700 : 500;
            var outgoing = toolsShown ? tools : main;
            var incoming = toolsShown ? main : tools;
            animate(outgoing, elapsed, true, false);
            animate(incoming, elapsed - delay, false, false);
            if(elapsed >= delay + 400 + (incoming.size() - 1) * 100)
            {
                outgoing.forEach(b -> b.presentation(0, 0, 0, false));
                toolsShown = !toolsShown;
                switching = false;
                pageStarted = switched + delay * 1_000_000L;
            }
        }
        else animate(toolsShown ? tools : main, (now - pageStarted) / 1_000_000F, false, !flipping);
        float entry = (now - started) / 1_000_000F - 700;
        float chrome = 1 - (float)Math.pow(1 - clamp(entry / 700), 3);
        if(fade != null && destination == null) {
            chrome = 1;
            animate(toolsShown ? tools : main, 3000, false, !flipping);
        }
        final float chromeAlpha = chrome * menuOpacity;
        account.presentation(width - layout.margin() - account.getWidth(), layout.margin(), chrome, chrome >= .999F && !flipping);
        if(flipping) {
            main.forEach(b -> b.active = false);
            tools.forEach(b -> b.active = false);
        }
        graphics.fill(0, 0, width, height, 0xFF090A14);
        NanoVGUI.pass(graphics, () -> {
            MainMenuBackground.render(width, height, 255);
            int margin = layout.margin();
            NanoVGUI.push();
            NanoVGUI.context().setAlpha(chromeAlpha);
            float logoHeight = Math.min(40, layout.size());
            ClientLogo.draw(NanoVGUI.context(), margin, margin, logoHeight);
            String user = NanoVGUI.plainSubstrByWidth(minecraft.getUser().getName(),
                Math.max(0, width / 2F - account.getWidth() - margin * 2), 9);
            NanoVGUI.text(user, account.getX() - 8 - NanoVGUI.width(user, 9),
                margin + 14, 0xFFB5BEF0, 9);
            NanoVGUI.pop();
            NanoVGUI.push();
            for(WurstTitleButton b : main) b.renderNanoVG(mouseX, mouseY, menuOpacity);
            for(WurstTitleButton b : tools) b.renderNanoVG(mouseX, mouseY, menuOpacity);
            account.renderNanoVG(mouseX, mouseY, menuOpacity);
            NanoVGUI.pop();
            NanoVGUI.push();
            NanoVGUI.context().setAlpha(menuOpacity);
            NanoVGUI.text("Minecraft " + SharedConstants.getCurrentVersion().name()
                + " / Fabric", margin, height - 15, 0xFFB0B2C5, 8);
            String version = "WurstB+ Plus " + WurstClient.VERSION;
            NanoVGUI.text(version, width - margin - NanoVGUI.width(version, 8),
                height - 15, 0xFFB0B2C5, 8);
            NanoVGUI.pop();
        });
    }
    private static float clamp(float t) { return Math.max(0, Math.min(1, t)); }
    private static Identifier icon(String name)
    {
        if(name.equals("navigator"))
            return Identifier.fromNamespaceAndPath("wurst", "textures/gui/icons/search.png");
        return Identifier.fromNamespaceAndPath("wurst", "textures/gui/fdp/" + name + ".png");
    }
}
