/*
 * Rise standard ClickGUI port for Minecraft 1.20.1 / WurstB+ Plus.
 * Reference: AnShanArchive/Rise-6.9.5, commit f58ef9c, ui/click/standard.
 */
package net.wurstclient.clickgui2;

import java.text.Collator;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.wurstclient.Feature;
import net.wurstclient.WurstClient;
import net.wurstclient.hack.Hack;
import net.wurstclient.gui.MainMenuBackground;
import net.wurstclient.hud2.nanovg.NanoVGUI;
import org.lwjgl.glfw.GLFW;

public final class NavigatorScreen extends Screen
{
	private static final Minecraft MC = WurstClient.MC;
	private static final WurstClient WURST = WurstClient.INSTANCE;
	private static final int HEADER = 38;
	private static final int GAP = 7;
	private static final int ICON_SIZE = 8;
	// The supplied Rise 6.9.2 capture has tighter card corners than 6.9.5.
	private static final int CARD_RADIUS = 3;
	private static final int BACKGROUND = 0xFE171A21;
	private static final int SECONDARY = 0xFF121419;
	// Cool blue ambient tint from the supplied capture, independent of the
	// animated selector. A full Blend primary produces a visible cyan cast.
	private static final int AMBIENT = 0x014764FD;
	private static final int TEXT = 0xFFFFFFFF;
	private static final NavigatorCategory[] CATEGORIES = NavigatorCategory.values();
	private static final Session SESSION = new Session();

	private final Map<Feature, Card> cards = new IdentityHashMap<>();
	private final List<Card> paintedCards = new ArrayList<>();
	private List<Feature> visibleFeatures = List.of();
	private List<Feature> previousFeatures = List.of();
	private int previousCategory;
	private long categoryChanged;
	private NavigatorTextBox searchBox;
	private boolean typedWhileOpen;
	private int panelX;
	private int panelY;
	private int panelWidth;
	private int panelHeight;
	private int sidebarWidth;
	private int listTop;
	private int listBottom;
	private final NavigatorAnimation[] selectors = java.util.Arrays.stream(CATEGORIES)
		.map(category -> new NavigatorAnimation(0)).toArray(NavigatorAnimation[]::new);
	private float searchOpacity = 255;
	private long lastFrame;
	private boolean dragging;
	private int dragButton;
	private double dragOffsetX;
	private double dragOffsetY;
	private boolean closing;
	private long closedAt;
	private NavigatorAnimation scale;
	private NavigatorAnimation opacity;

	public NavigatorScreen()
	{
		super(Component.literal(WurstClient.CLIENT_NAME));
	}

	@Override
	protected void init()
	{
		blurAll();
		NavigatorPreferences.load();
		// Rise always lays out a 400x300 surface. It is centered only when a
		// previously saved position no longer fits the current window.
		panelWidth = 400;
		panelHeight = 300;
		sidebarWidth = 100;
		panelX = SESSION.x;
		panelY = SESSION.y;
		if(panelX < 0 || panelY < 0 || panelX + panelWidth > width || panelY + panelHeight > height)
		{
			panelX = (width - panelWidth) / 2;
			panelY = (height - panelHeight) / 2;
		}
		WURST.getGui().init();
		closing = dragging = typedWhileOpen = false;
		long now = System.currentTimeMillis();
		scale = new NavigatorAnimation(0);
		opacity = new NavigatorAnimation(0);
		scale.animate(1, 300, now);
		opacity.animate(1, 300, now);
		searchBox = new NavigatorTextBox(null, query -> {
			SESSION.query = query;
			scroll().reset();
			refreshFeatures();
		});
		searchBox.set(SESSION.query);
		searchBox.end();
		refreshFeatures();
		previousFeatures = visibleFeatures;
		previousCategory = SESSION.category;
		categoryChanged = now - 400;
	}

	private List<Feature> interfaceFeatures()
	{
		return List.of(WURST.getHax().clickGuiHack, WURST.getHax().navigatorHack,
			WURST.getOtfs().wurstLogoOtf, WURST.getOtfs().hackListOtf,
			WURST.getOtfs().keybindManagerOtf, WURST.getOtfs().translationsOtf);
	}

	private void refreshFeatures()
	{
		List<Feature> features = new ArrayList<>(FeatureMenuSupport.getAllFeatures());
		Collator collator = Collator.getInstance();
		features.sort((first, second) -> collator.compare(featureName(first), featureName(second)));
		if(SESSION.category == 0)
			features.removeIf(feature -> {
				List<String> aliases = new ArrayList<>(List.of(feature.getName(), featureName(feature)));
				aliases.addAll(List.of(feature.getSearchTags().split("\u00a7")));
				return !NavigatorSearch.matches(aliases, SESSION.query);
			});
		else
		{
			NavigatorCategory selected = CATEGORIES[SESSION.category];
			if(selected == NavigatorCategory.INTERFACE)
				features.removeIf(feature -> !interfaceFeatures().contains(feature));
			else
				features.removeIf(feature -> NavigatorCategory.of(feature) != selected);
		}
		visibleFeatures = List.copyOf(features);
	}

	private static String featureName(Feature feature)
	{
		// Menu labels must not call HUD render names, which can require a player.
		if(feature instanceof Hack)
		{
			String key = "hack.name." + feature.getName().toLowerCase(Locale.ROOT);
			String translated = WURST.translate(key);
			return translated.equals(key) ? feature.getName() : translated;
		}
		return feature.getDisplayName();
	}

	private NavigatorScroll scroll()
	{
		return scrollFor(SESSION.category);
	}

	private NavigatorScroll scrollFor(int category)
	{
		return SESSION.scrolls.computeIfAbsent(category, ignored -> new NavigatorScroll());
	}

	private void selectCategory(int category)
	{
		if(category == SESSION.category)
			return;
		blurAll();
		previousFeatures = visibleFeatures;
		previousCategory = SESSION.category;
		SESSION.category = category;
		categoryChanged = System.currentTimeMillis();
		typedWhileOpen = false;
		if(searchBox != null) searchBox.focus(false);
		refreshFeatures();
	}

	@Override
	public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTicks)
	{
		long now = System.currentTimeMillis();
		long elapsed = lastFrame == 0 ? 16 : Math.min(500, Math.max(0, now - lastFrame));
		lastFrame = now;
		if(closing && now - closedAt >= 300)
		{
			MC.setScreen(null);
			return;
		}
		WURST.getGui().updateColors();
		float zoom = Math.max(0.001F, scale.value(now));
		float alpha = opacity.value(now);
		long transition = now - categoryChanged;
		boolean showingPrevious = transition < 200;
		int category = showingPrevious ? previousCategory : SESSION.category;
		List<Feature> features = showingPrevious ? previousFeatures : visibleFeatures;
		layoutCards(features, category, now);
		if(category == 0)
			searchOpacity = Math.max(0, Math.min(255, searchOpacity
				+ (scrollFor(0).target() < 0 ? -4 : 4) * elapsed));
		boolean canInteract = !closing && zoom > 0.99F && transition >= 200;
		int hoverX = canInteract ? mouseX : Integer.MIN_VALUE / 2;
		int hoverY = canInteract ? mouseY : Integer.MIN_VALUE / 2;

		NanoVGUI.pass(graphics, () -> {
			// Without a world, Minecraft keeps the previous screen's color buffer.
			// Repaint it so translucent shadows and animation frames cannot build up.
			if(MC.level == null)
				MainMenuBackground.render(width, height, MainMenuBackground.MENU_ALPHA);
			NanoVGUI.push();
			NanoVGUI.context().scale(panelX, panelY, panelWidth, panelHeight, zoom);
			NanoVGUI.context().setAlpha(alpha);
			if(zoom > 0.993F)
				NavigatorStyle.dropShadow(18, panelX, panelY, panelWidth, panelHeight, 30, 15.6F);
			NanoVGUI.fillRounded(panelX, panelY, panelX + panelWidth,
				panelY + panelHeight, 12, BACKGROUND);
			NanoVGUI.pushScissor(panelX + sidebarWidth, panelY + 1,
				panelWidth - sidebarWidth - 1, panelHeight - 2);
			if(category == 0)
				searchBox.render(panelX + 250, panelY + 17 + scrollFor(0).current(),
					150, 10, NavigatorTextBox.withAlpha(TEXT, Math.round(searchOpacity)),
					true, NavigatorPreferences.text("ui.search.text"), now);
			for(Card card : paintedCards)
				if(card.y + card.height > listTop && card.y < listBottom)
					renderCard(graphics, card, hoverX, hoverY, partialTicks, category, now);
			renderScrollbar(category);
			NanoVGUI.popScissor();
			float fade = transition < 200 ? transition / 200F : Math.max(0, 1 - (transition - 200) / 200F);
			if(fade > 0)
				NanoVGUI.fill(panelX + sidebarWidth, panelY + 1, panelX + panelWidth - 1,
					panelY + panelHeight - 1, (Math.round(fade * 254) << 24) | 0x171A21);
			renderSidebar(graphics, now);
			NanoVGUI.pop();
		});

		// The supplied visual reference has no broad glow around its selector.
		// Keep the solid selector without the optional Rise bloom pass.
		NanoVGUI.pass(graphics, () -> {
			if(canInteract)
			{
				WURST.getGui().renderPopups(graphics, mouseX, mouseY);
				WURST.getGui().renderTooltip(graphics, mouseX, mouseY);
			}
		});
	}

	private void layoutCards(List<Feature> features, int category, long now)
	{
		listTop = panelY + 1;
		listBottom = panelY + panelHeight - 1;
		paintedCards.clear();
		float total = 0;
		for(Feature feature : features)
		{
			Card card = cards.computeIfAbsent(feature, Card::new);
			card.updateHeight(now);
			paintedCards.add(card);
			total += card.height + GAP;
		}
		NavigatorScroll scrolling = scrollFor(category);
		scrolling.bounds(total, panelHeight - (category == 0 ? 37 : 7));
		float y = panelY + (category == 0 ? 35 : 7) + scrolling.update(now);
		for(Card card : paintedCards)
		{
			card.y = y;
			if(card.settings != null)
				card.settings.layout(panelX + sidebarWidth + 14, Math.round(y + HEADER + 1),
					271, listTop, listBottom, Math.round(y + card.height));
			y += card.height + GAP;
		}
	}

	private void renderCard(GuiGraphics graphics, Card card, int mouseX, int mouseY,
		float partialTicks, int category, long now)
	{
		int x = panelX + sidebarWidth + 8;
		int right = panelX + panelWidth - 9;
		float y = card.y;
		NanoVGUI.fillRounded(x, y, right, y + card.height, CARD_RADIUS, 0x32000000);
		boolean hovered = inside(mouseX, mouseY, x, Math.max(listTop, y),
			right, Math.min(listBottom, y + card.height));
		card.hover.animate(hovered ? card.pressed ? 35 : 20 : 0, 50, now, NavigatorAnimation.Easing.LINEAR);
		NanoVGUI.fillRounded(x, y, right, y + card.height, CARD_RADIUS,
			Math.round(card.hover.value(now)) << 24);
		int text = card.feature.isEnabled() ? NavigatorPreferences.theme.accent(0, y / 5, now) : 0xC8FFFFFF;
		String name = NanoVGUI.plainSubstrByWidth(featureName(card.feature), right - x - 12, 10);
		NanoVGUI.text(name, x + 6, y + 8, text, 10);
		if(category == 0)
		{
			String label = card.feature.getCategory() == null
				? FeatureMenuSupport.getTypeName(card.feature) : card.feature.getCategory().getName();
			float labelX = x + NanoVGUI.width(name, 10) + 10;
			if(labelX + NanoVGUI.width(label, 7.5F) + 5 < right)
				NanoVGUI.text("(" + label + ")", labelX, y + 10, 0x40FFFFFF, 7.5F);
		}
		NanoVGUI.text(NanoVGUI.plainSubstrByWidth(NanoVGUI.stripCodes(
			FeatureMenuSupport.getOneLineDescription(card.feature)),
			right - x - 12, 7.5F), x + 6, y + 25, 0x46FFFFFF, 7.5F);
		if(card.settings != null && card.height > HEADER + 1)
		{
			NanoVGUI.push();
			NanoVGUI.context().setAlpha(opacity.value(now)
				* card.settingsOpacity.value(now));
			card.settings.renderContent(graphics, mouseX, mouseY, partialTicks);
			NanoVGUI.pop();
		}
	}

	private void renderSidebar(GuiGraphics graphics, long now)
	{
		NanoVGUI.pushScissor(panelX + 1, panelY + 1, panelWidth - 2, panelHeight - 2);
		NanoVGUI.fillRounded(panelX, panelY, panelX + sidebarWidth,
			panelY + panelHeight, 12, SECONDARY);
		NanoVGUI.fill(panelX + sidebarWidth - 12, panelY + 1, panelX + sidebarWidth,
			panelY + panelHeight - 1, SECONDARY);
		// RenderUtil.c accepts a center and radius, not a top-left and diameter.
		for(int i = 1; i <= 8; i++)
			NanoVGUI.circle(panelX + sidebarWidth - i * 25, panelY + panelHeight / 2F - i * 25, i * 50,
				AMBIENT);
		String brand = "WurstB+";
		NanoVGUI.text(brand, panelX + 14, panelY + 13, TEXT, 14);
		NanoVGUI.text(WurstClient.VERSION, panelX + 14 + NanoVGUI.width(brand, 14),
			panelY + 10, NavigatorPreferences.theme.primary(), 7);
		for(int i = 0; i < CATEGORIES.length; i++)
		{
			NavigatorCategory category = CATEGORIES[i];
			float y = panelY + 45.5F + i * 19.5F;
			boolean selected = SESSION.category == i;
			selectors[i].animate(selected ? 255 : 0, 200, now, NavigatorAnimation.Easing.LINEAR);
			renderSelector(i, now);
			int color = selected ? TEXT : 0xC8FFFFFF;
			float shift = selectors[i].value(now) / 80;
			NanoVGUI.push();
			NanoVGUI.translate(panelX + 13 + shift, y);
			category.icon.draw(graphics, 0, 0, ICON_SIZE, color);
			NanoVGUI.pop();
			NanoVGUI.text(category.label(), panelX + 17 + ICON_SIZE + shift, y, color, 8);
		}
		NanoVGUI.popScissor();
	}

	private void renderSelector(int i, long now)
	{
		NavigatorCategory category = CATEGORIES[i];
		float y = panelY + 45.5F + i * 19.5F;
		float w = NanoVGUI.width(category.label(), 8)
			+ ICON_SIZE + 16;
		int accent = new java.awt.Color(NavigatorPreferences.theme.accent(0, y / 5, now)).darker().getRGB();
		int color = NavigatorTextBox.withAlpha(accent, Math.round(selectors[i].value(now)));
		NanoVGUI.fillRounded(panelX + 10, y - 5.5F, panelX + 10 + w,
			y + 9.5F, 5, color);
	}

	private void renderScrollbar(int category)
	{
		NavigatorScroll scrolling = SESSION.scrolls.get(category);
		float viewport = panelHeight - 14 - (category == 0 ? 28 : 0);
		float content = viewport - scrolling.minimum();
		if(content <= viewport)
			return;
		float thumb = viewport * viewport / content;
		float ratio = scrolling.current() / scrolling.minimum();
		float y = panelY + (category == 0 ? 35 : 7) + (viewport - thumb) * ratio;
		NanoVGUI.fillRounded(panelX + panelWidth - 4, y,
			panelX + panelWidth - 3, y + thumb, 0.5F, 0x3CFFFFFF);
	}

	@Override
	public boolean mouseClicked(double mouseX, double mouseY, int button)
	{
		long now = System.currentTimeMillis();
		if(closing || scale.value(now) < 0.99F)
			return true;
		ClickGui gui = WURST.getGui();
		if(gui.handleOverlayMouseClick((int)mouseX, (int)mouseY, button))
			return true;
		if(!inside(mouseX, mouseY, panelX, panelY, panelX + panelWidth, panelY + panelHeight))
		{
			blurAll();
			searchBox.focus(false);
			return false;
		}
		if(mouseY < panelY + 15)
		{
			blurAll();
			searchBox.focus(false);
			dragging = true;
			dragButton = button;
			dragOffsetX = panelX - mouseX;
			dragOffsetY = panelY - mouseY;
			return true;
		}
		if(mouseX < panelX + sidebarWidth && button == GLFW.GLFW_MOUSE_BUTTON_LEFT)
		{
			int index = (int)((mouseY - panelY - 40.5) / 19.5);
			if(mouseY >= panelY + 40 && index >= 0 && index < CATEGORIES.length)
				selectCategory(index);
			return true;
		}
		if(now - categoryChanged < 200)
			return true;
		if(SESSION.category == 0 && mouseY >= panelY + 15
			&& mouseY < panelY + panelHeight - 7
			&& searchBox.click(mouseX, mouseY, button))
		{
			blurAll();
			return true;
		}
		searchBox.focus(false);
		for(Card card : paintedCards)
		{
			if(!inside(mouseX, mouseY, panelX + sidebarWidth + 8, Math.max(listTop, card.y),
				panelX + panelWidth - 9, Math.min(listBottom, card.y + card.height)))
				continue;
			for(Card other : cards.values())
				if(other != card && other.settings != null)
					other.settings.blur();
			if(mouseY < card.y + HEADER - 3)
			{
				if(card.settings != null)
					card.settings.blur();
				card.pressed = true;
				if(button == GLFW.GLFW_MOUSE_BUTTON_LEFT)
					FeatureMenuSupport.runPrimaryAction(card.feature);
				else if(button == GLFW.GLFW_MOUSE_BUTTON_RIGHT && !card.feature.getSettings().isEmpty())
				{
					if(card.expanded())
						SESSION.expanded.remove(card.feature.getName());
					else
						SESSION.expanded.add(card.feature.getName());
				}
			}else if(card.expanded() && card.settings != null)
				card.settings.mouseClicked(mouseX, mouseY, button);
			return true;
		}
		blurAll();
		return true;
	}

	@Override
	public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY)
	{
		if(dragging && button == dragButton)
		{
			panelX = (int)Math.round(mouseX + dragOffsetX);
			panelY = (int)Math.round(mouseY + dragOffsetY);
			SESSION.x = panelX;
			SESSION.y = panelY;
			return true;
		}
		return false;
	}

	@Override
	public boolean mouseReleased(double mouseX, double mouseY, int button)
	{
		dragging = false;
		WURST.getGui().handleMouseRelease(mouseX, mouseY, button);
		for(Card card : cards.values())
		{
			card.pressed = false;
			if(card.settings != null)
				card.settings.release();
		}
		return true;
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double delta)
	{
		if(closing || delta == 0 || !inside(mouseX, mouseY, panelX, panelY,
			panelX + panelWidth, panelY + panelHeight))
			return false;
		if(mouseX < panelX + sidebarWidth)
		{
			return true;
		}
		WURST.getGui().handleMouseRelease(mouseX, mouseY, GLFW.GLFW_MOUSE_BUTTON_LEFT);
		for(Card card : cards.values())
			if(card.settings != null)
				card.settings.release();
		scroll().wheel(delta, System.currentTimeMillis());
		return true;
	}

	@Override
	public boolean charTyped(char character, int modifiers)
	{
		if(closing)
			return true;
		for(Card card : paintedCards)
			if(card.expanded() && card.settings != null && card.settings.isEditing())
			{
				card.settings.charTyped(character, modifiers);
				return true;
			}
		if(Character.isISOControl(character))
			return false;
		if(SESSION.category != 0)
		{
			selectCategory(0);
			// Type-to-search must accept its first character during the fade.
			categoryChanged = System.currentTimeMillis() - 200;
		}
		if(!typedWhileOpen)
		{
			searchBox.set("");
			typedWhileOpen = true;
		}
		searchBox.focus(true);
		return searchBox.character(character);
	}

	@Override
	public boolean keyPressed(int key, int scan, int modifiers)
	{
		if(key == GLFW.GLFW_KEY_ESCAPE)
		{
			onClose();
			return true;
		}
		if(closing)
			return true;
		for(Card card : paintedCards)
			if(card.expanded() && card.settings != null && card.settings.isEditing())
			{
				card.settings.keyPressed(key, scan, modifiers);
				return true;
			}
		if(SESSION.category == 0)
		{
			if(key == GLFW.GLFW_KEY_UP || key == GLFW.GLFW_KEY_DOWN) return true;
			searchBox.focus(true);
			searchBox.key(key, modifiers);
		}
		return true;
	}

	@Override
	public void onClose()
	{
		if(closing)
			return;
		closing = true;
		closedAt = System.currentTimeMillis();
		blurAll();
		searchBox.focus(false);
		mouseReleased(0, 0, GLFW.GLFW_MOUSE_BUTTON_LEFT);
		scale.animate(0, 300, closedAt, NavigatorAnimation.Easing.LINEAR);
		opacity.animate(0, 100, closedAt, NavigatorAnimation.Easing.LINEAR);
	}

	@Override
	public void removed()
	{
		SESSION.x = panelX;
		SESSION.y = panelY;
		blurAll();
		mouseReleased(0, 0, GLFW.GLFW_MOUSE_BUTTON_LEFT);
		super.removed();
	}

	@Override
	public boolean isPauseScreen()
	{
		return false;
	}

	private void blurAll()
	{
		for(Card card : cards.values())
			if(card.settings != null)
				card.settings.blur();
	}

	private static boolean inside(double x, double y, double left, double top, double right, double bottom)
	{
		return x >= left && x < right && y >= top && y < bottom;
	}

	private static final class Session
	{
		private int x = -1;
		private int y = -1;
		private int category;
		private String query = "";
		private final Set<String> expanded = new HashSet<>();
		private final Map<Integer, NavigatorScroll> scrolls = new HashMap<>();
	}

	private static final class Card
	{
		private final Feature feature;
		private final NavigatorAnimation expansion = new NavigatorAnimation(HEADER);
		private final NavigatorAnimation hover = new NavigatorAnimation(0);
		private final NavigatorAnimation settingsOpacity = new NavigatorAnimation(0);
		private NavigatorSettingsPanel settings;
		private float height = HEADER;
		private float y;
		private boolean pressed;

		Card(Feature feature)
		{
			this.feature = feature;
		}

		boolean expanded()
		{
			return SESSION.expanded.contains(feature.getName());
		}

		void updateHeight(long now)
		{
			if(expanded() && settings == null)
				settings = new NavigatorSettingsPanel(feature);
			float natural = HEADER + (settings == null ? 0 : Math.max(0, settings.naturalHeight() - 1));
			float target = expanded() ? natural : HEADER;
			long duration = Math.min(450, Math.round(natural * 3));
			expansion.animate(target, duration, now);
			settingsOpacity.animate(expanded() ? 1 : 0, duration / (expanded() ? 2 : 3),
				now, NavigatorAnimation.Easing.LINEAR);
			height = expansion.value(now);
		}
	}
}
