package net.wurstclient.clickgui2.screens;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletionException;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.qrcode.QRCodeWriter;
import com.google.zxing.common.BitMatrix;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.wurstclient.clickgui2.FlatRenderer;
import net.wurstclient.clickgui2.supersoft.SuperSoftTheme;
import net.wurstclient.clickgui2.supersoft.UiTween;
import net.wurstclient.gui.visual.VisualTheme;
import net.wurstclient.music.NeteaseCloudApi;
import net.wurstclient.music.NeteaseMusicPlayer;
import org.lwjgl.glfw.GLFW;

public final class NeteaseLoginScreen extends Screen
{
	private static final NeteaseMusicPlayer PLAYER = NeteaseMusicPlayer.INSTANCE;
	private static final int PANEL_WIDTH = 320;
	private static final int PANEL_HEIGHT = 250;
	private static final int PRIMARY = 0xFFEC4141;
	private static final int BACKGROUND = VisualTheme.BACKGROUND;
	private static final int CARD = VisualTheme.CONTROL;
	private static final int TEXT = VisualTheme.TEXT;
	private static final int MUTED = VisualTheme.TEXT_MUTED;

	private final Screen parent;
	private final UiTween screenMotion = new UiTween(0, 300);
	private final UiTween modeMotion = new UiTween(1, 200);
	private final Map<String, UiTween> hoverMotions = new HashMap<>();
	private LoginMode mode = LoginMode.PHONE;
	private InputField focused = InputField.NONE;
	private String phone = "";
	private String captcha = "";
	private String message = "";
	private boolean loading;
	private long captchaAvailableAt;
	private long nextQrPoll;
	private long qrGeneration;
	private NeteaseCloudApi.QrLogin qrLogin;
	private NeteaseCloudApi.QrStatus qrStatus =
		NeteaseCloudApi.QrStatus.WAITING;
	private BitMatrix qrMatrix;
	private boolean closing;

	public NeteaseLoginScreen(Screen parent)
	{
		super(Component.literal("登录网易云音乐"));
		this.parent = parent;
	}

	@Override
	protected void init()
	{
		if(PLAYER.isLoggedIn())
		{
			minecraft.setScreen(parent);
			return;
		}
		if(mode == LoginMode.QR)
			startQrLogin();
	}

	@Override
	public void tick()
	{
		if(mode != LoginMode.QR || loading || qrLogin == null)
			return;
		if(qrStatus != NeteaseCloudApi.QrStatus.WAITING
			&& qrStatus != NeteaseCloudApi.QrStatus.SCANNED)
			return;
		if(System.currentTimeMillis() >= nextQrPoll)
			pollQrLogin();
	}

	@Override
	public void render(GuiGraphics graphics, int mouseX, int mouseY,
		float partialTicks)
	{
		float open = screenMotion.update(closing ? 0 : 1);
		if(closing && open <= 0.001F)
		{
			minecraft.setScreen(parent);
			return;
		}
		graphics.fill(0, 0, width, height, Math.round(open * 176) << 24);
		Bounds bounds = bounds();
		float centerX = (bounds.left() + bounds.right()) / 2F;
		float centerY = (bounds.top() + bounds.bottom()) / 2F;
		float scale = 0.9F + 0.1F * open;
		graphics.pose().pushPose();
		graphics.pose().translate(centerX, centerY, 0);
		graphics.pose().scale(scale, scale, 1);
		graphics.pose().translate(-centerX, -centerY, 0);
		FlatRenderer.fillRoundedRect(graphics, bounds.left() - 5,
			bounds.top() + 5, bounds.right() + 5, bounds.bottom() + 8, 10,
			0x66000000);
		FlatRenderer.fillRoundedRect(graphics, bounds.left(), bounds.top(),
			bounds.right(), bounds.bottom(), 10, BACKGROUND);
		boolean backHovered = contains(mouseX, mouseY, bounds.left() + 8,
			bounds.top() + 8, bounds.left() + 34, bounds.top() + 32);
		graphics.drawString(font, "<", bounds.left() + 14, bounds.top() + 14,
			SuperSoftTheme.mix(MUTED, TEXT, hover("back", backHovered)), false);
		graphics.drawCenteredString(font, "登录网易云音乐",
			(bounds.left() + bounds.right()) / 2, bounds.top() + 15, TEXT);

		renderTab(graphics, bounds.left() + 77, bounds.top() + 36,
			"手机号", LoginMode.PHONE, mouseX, mouseY);
		renderTab(graphics, bounds.left() + 164, bounds.top() + 36,
			"二维码", LoginMode.QR, mouseX, mouseY);

		int slide = Math.round((1 - modeMotion.update(1)) * 16);
		graphics.pose().pushPose();
		graphics.pose().translate(slide, 0, 0);
		if(mode == LoginMode.QR)
			renderQrLogin(graphics, bounds, mouseX, mouseY);
		else
			renderPhoneLogin(graphics, bounds, mouseX, mouseY);
		graphics.pose().popPose();
		graphics.pose().popPose();
	}

	private void renderTab(GuiGraphics graphics, int left, int top, String label,
		LoginMode target, int mouseX, int mouseY)
	{
		int right = left + 78;
		boolean selected = mode == target;
		boolean hovered = contains(mouseX, mouseY, left, top, right, top + 22);
		float progress = hover("tab-" + target, selected || hovered);
		FlatRenderer.fillRoundedRect(graphics, left, top, right, top + 22, 4,
			SuperSoftTheme.mix(CARD, PRIMARY, selected ? 1 : progress * 0.5F));
		graphics.drawCenteredString(font, label, (left + right) / 2, top + 7,
			selected ? TEXT : MUTED);
	}

	private void renderQrLogin(GuiGraphics graphics, Bounds bounds, int mouseX,
		int mouseY)
	{
		int size = 126;
		int left = (bounds.left() + bounds.right() - size) / 2;
		int top = bounds.top() + 66;
		graphics.fill(left, top, left + size, top + size, 0xFFFFFFFF);
		if(qrMatrix == null)
			graphics.drawCenteredString(font, loading ? "正在生成..." : "生成失败",
				left + size / 2, top + size / 2 - 4, 0xFF4A3A44);
		else
		{
			int scale = Math.max(1,
				Math.min(size / qrMatrix.getWidth(), size / qrMatrix.getHeight()));
			int drawnWidth = qrMatrix.getWidth() * scale;
			int drawnHeight = qrMatrix.getHeight() * scale;
			int offsetX = left + (size - drawnWidth) / 2;
			int offsetY = top + (size - drawnHeight) / 2;
			for(int y = 0; y < qrMatrix.getHeight(); y++)
				for(int x = 0; x < qrMatrix.getWidth(); x++)
					if(qrMatrix.get(x, y))
						graphics.fill(offsetX + x * scale, offsetY + y * scale,
							offsetX + (x + 1) * scale,
							offsetY + (y + 1) * scale, 0xFF000000);
		}

		String status = switch(qrStatus)
		{
			case WAITING -> "请使用网易云音乐 App 扫码";
			case SCANNED -> "已扫码，请在手机上确认";
			case SUCCESS -> "登录成功";
			case EXPIRED -> "二维码已过期，点击刷新";
			case ERROR -> message.isBlank() ? "二维码登录失败" : message;
		};
		graphics.drawCenteredString(font,
			font.plainSubstrByWidth(status, bounds.right() - bounds.left() - 28),
			(bounds.left() + bounds.right()) / 2, top + size + 10,
			qrStatus == NeteaseCloudApi.QrStatus.ERROR ? PRIMARY : MUTED);
		if(qrStatus == NeteaseCloudApi.QrStatus.EXPIRED
			|| qrStatus == NeteaseCloudApi.QrStatus.ERROR)
		{
			int refreshLeft = (bounds.left() + bounds.right() - 68) / 2;
			FlatRenderer.fillRoundedRect(graphics, refreshLeft, top + size + 24,
				refreshLeft + 68, top + size + 44, 4, PRIMARY);
			graphics.drawCenteredString(font, "刷新", refreshLeft + 34,
				top + size + 30, TEXT);
		}
	}

	private void renderPhoneLogin(GuiGraphics graphics, Bounds bounds,
		int mouseX, int mouseY)
	{
		int left = bounds.left() + 38;
		int right = bounds.right() - 38;
		int phoneTop = bounds.top() + 78;
		renderInput(graphics, left, phoneTop, right, phoneTop + 27, phone,
			"手机号", focused == InputField.PHONE, mouseX, mouseY, "phone");

		int captchaTop = phoneTop + 39;
		int sendLeft = right - 82;
		renderInput(graphics, left, captchaTop, sendLeft - 7, captchaTop + 27,
			captcha, "验证码", focused == InputField.CAPTCHA, mouseX, mouseY,
			"captcha");
		boolean canSend = !loading && phone.length() >= 5
			&& System.currentTimeMillis() >= captchaAvailableAt;
		boolean sendHovered = contains(mouseX, mouseY, sendLeft, captchaTop,
			right, captchaTop + 27);
		float sendHover = hover("send", canSend && sendHovered);
		FlatRenderer.fillRoundedRect(graphics, sendLeft - Math.round(sendHover),
			captchaTop - Math.round(sendHover), right + Math.round(sendHover),
			captchaTop + 27 + Math.round(sendHover), 4,
			canSend ? PRIMARY : CARD);
		long seconds = Math.max(0,
			(captchaAvailableAt - System.currentTimeMillis() + 999) / 1000);
		graphics.drawCenteredString(font,
			seconds > 0 ? seconds + "s" : "获取验证码", (sendLeft + right) / 2,
			captchaTop + 9, canSend ? TEXT : MUTED);

		int loginTop = captchaTop + 43;
		boolean loginHovered = contains(mouseX, mouseY, left, loginTop, right,
			loginTop + 29);
		float loginHover = hover("login", !loading && loginHovered);
		FlatRenderer.fillRoundedRect(graphics, left - Math.round(loginHover),
			loginTop - Math.round(loginHover), right + Math.round(loginHover),
			loginTop + 29 + Math.round(loginHover), 4,
			loading ? CARD : PRIMARY);
		graphics.drawCenteredString(font, loading ? "登录中..." : "登录",
			(left + right) / 2, loginTop + 10, loading ? MUTED : TEXT);
		String status = message.isBlank() ? "验证码由网易云官方 HTTPS 接口发送"
			: message;
		graphics.drawCenteredString(font,
			font.plainSubstrByWidth(status, bounds.right() - bounds.left() - 28),
			(bounds.left() + bounds.right()) / 2, loginTop + 41,
			message.contains("成功") ? 0xFF75D58A
				: message.isBlank() ? 0x778F7A88 : PRIMARY);
	}

	private void renderInput(GuiGraphics graphics, int left, int top, int right,
		int bottom, String value, String placeholder, boolean active, int mouseX,
		int mouseY, String id)
	{
		FlatRenderer.fillRoundedRect(graphics, left, top, right, bottom, 4, CARD);
		boolean hovered = contains(mouseX, mouseY, left, top, right, bottom);
		FlatRenderer.drawRoundedOutline(graphics, left, top, right, bottom, 4,
			SuperSoftTheme.mix(0x664F3948, PRIMARY,
				hover("input-" + id, active || hovered)));
		String shown = value.isEmpty() ? placeholder : value;
		graphics.drawString(font,
			font.plainSubstrByWidth(shown, right - left - 14), left + 7,
			top + 10, value.isEmpty() ? 0x779F8997 : TEXT, false);
		if(active && System.currentTimeMillis() / 500 % 2 == 0)
		{
			int cursor = left + 7
				+ font.width(font.plainSubstrByWidth(value, right - left - 14));
			graphics.fill(cursor, top + 7, cursor + 1, bottom - 6, TEXT);
		}
	}

	@Override
	public boolean mouseClicked(double mouseX, double mouseY, int button)
	{
		if(button != 0 || screenMotion.get() < 0.9F)
			return super.mouseClicked(mouseX, mouseY, button);
		Bounds bounds = bounds();
		if(contains(mouseX, mouseY, bounds.left() + 8, bounds.top() + 8,
			bounds.left() + 34, bounds.top() + 32))
		{
			onClose();
			return true;
		}
		if(contains(mouseX, mouseY, bounds.left() + 77, bounds.top() + 36,
			bounds.left() + 155, bounds.top() + 58))
		{
			switchMode(LoginMode.PHONE);
			return true;
		}
		if(contains(mouseX, mouseY, bounds.left() + 164, bounds.top() + 36,
			bounds.left() + 242, bounds.top() + 58))
		{
			switchMode(LoginMode.QR);
			return true;
		}

		if(mode == LoginMode.QR)
		{
			if(qrStatus == NeteaseCloudApi.QrStatus.EXPIRED
				|| qrStatus == NeteaseCloudApi.QrStatus.ERROR)
			{
				startQrLogin();
				return true;
			}
			return false;
		}

		int left = bounds.left() + 38;
		int right = bounds.right() - 38;
		int phoneTop = bounds.top() + 78;
		int captchaTop = phoneTop + 39;
		int sendLeft = right - 82;
		if(contains(mouseX, mouseY, left, phoneTop, right, phoneTop + 27))
			focused = InputField.PHONE;
		else if(contains(mouseX, mouseY, left, captchaTop, sendLeft - 7,
			captchaTop + 27))
			focused = InputField.CAPTCHA;
		else if(contains(mouseX, mouseY, sendLeft, captchaTop, right,
			captchaTop + 27))
			sendCaptcha();
		else if(contains(mouseX, mouseY, left, captchaTop + 43, right,
			captchaTop + 72))
			loginWithCaptcha();
		else
			focused = InputField.NONE;
		return true;
	}

	@Override
	public boolean keyPressed(int keyCode, int scanCode, int modifiers)
	{
		if(keyCode == GLFW.GLFW_KEY_ESCAPE)
		{
			onClose();
			return true;
		}
		if(mode != LoginMode.PHONE)
			return super.keyPressed(keyCode, scanCode, modifiers);
		if(keyCode == GLFW.GLFW_KEY_TAB)
		{
			focused = focused == InputField.PHONE ? InputField.CAPTCHA
				: InputField.PHONE;
			return true;
		}
		if(keyCode == GLFW.GLFW_KEY_ENTER)
		{
			loginWithCaptcha();
			return true;
		}
		if(keyCode == GLFW.GLFW_KEY_BACKSPACE)
		{
			if(focused == InputField.PHONE && !phone.isEmpty())
				phone = phone.substring(0, phone.length() - 1);
			else if(focused == InputField.CAPTCHA && !captcha.isEmpty())
				captcha = captcha.substring(0, captcha.length() - 1);
			return true;
		}
		if(keyCode == GLFW.GLFW_KEY_V && hasControlDown())
		{
			appendDigits(minecraft.keyboardHandler.getClipboard());
			return true;
		}
		return super.keyPressed(keyCode, scanCode, modifiers);
	}

	@Override
	public boolean charTyped(char codePoint, int modifiers)
	{
		if(mode == LoginMode.PHONE && Character.isDigit(codePoint))
		{
			appendDigits(String.valueOf(codePoint));
			return true;
		}
		return super.charTyped(codePoint, modifiers);
	}

	private void appendDigits(String value)
	{
		String digits = value.replaceAll("\\D", "");
		if(focused == InputField.PHONE)
			phone = (phone + digits).substring(0,
				Math.min(15, phone.length() + digits.length()));
		else if(focused == InputField.CAPTCHA)
			captcha = (captcha + digits).substring(0,
				Math.min(8, captcha.length() + digits.length()));
	}

	private void switchMode(LoginMode target)
	{
		if(mode == target)
			return;
		mode = target;
		modeMotion.snap(0);
		focused = InputField.NONE;
		message = "";
		qrGeneration++;
		loading = false;
		if(target == LoginMode.QR)
			startQrLogin();
	}

	private void startQrLogin()
	{
		long generation = ++qrGeneration;
		mode = LoginMode.QR;
		loading = true;
		message = "";
		qrLogin = null;
		qrMatrix = null;
		qrStatus = NeteaseCloudApi.QrStatus.WAITING;
		PLAYER.beginQrLogin().whenComplete((login, error) -> runOnClient(() -> {
			if(generation != qrGeneration)
				return;
			loading = false;
			if(error != null)
			{
				qrStatus = NeteaseCloudApi.QrStatus.ERROR;
				message = readableMessage(error);
				return;
			}
			try
			{
				qrLogin = login;
				qrMatrix = new QRCodeWriter().encode(login.loginUrl(),
					BarcodeFormat.QR_CODE, 37, 37,
					Map.of(EncodeHintType.MARGIN, 1));
				nextQrPoll = System.currentTimeMillis() + 1000;
			}catch(Exception e)
			{
				qrStatus = NeteaseCloudApi.QrStatus.ERROR;
				message = "无法生成二维码";
			}
		}));
	}

	private void pollQrLogin()
	{
		long generation = qrGeneration;
		loading = true;
		nextQrPoll = System.currentTimeMillis() + 2000;
		PLAYER.checkQrLogin(qrLogin.key()).whenComplete((check, error) ->
			runOnClient(() -> {
				if(generation != qrGeneration)
					return;
				loading = false;
				if(error != null)
				{
					qrStatus = NeteaseCloudApi.QrStatus.ERROR;
					message = readableMessage(error);
					return;
				}
				qrStatus = check.status();
				message = check.message();
				if(check.status() == NeteaseCloudApi.QrStatus.SUCCESS)
					onClose();
			}));
	}

	private void sendCaptcha()
	{
		if(loading || phone.length() < 5
			|| System.currentTimeMillis() < captchaAvailableAt)
			return;
		loading = true;
		message = "正在发送验证码...";
		PLAYER.sendCaptcha(phone, "86").whenComplete((result, error) ->
			runOnClient(() -> {
				loading = false;
				if(error != null)
					message = readableMessage(error);
				else
				{
					message = result.message();
					if(result.success())
						captchaAvailableAt = System.currentTimeMillis() + 60000;
				}
			}));
	}

	private void loginWithCaptcha()
	{
		if(loading || phone.length() < 5 || captcha.length() < 4)
			return;
		loading = true;
		message = "正在登录...";
		PLAYER.loginWithCaptcha(phone, captcha, "86")
			.whenComplete((result, error) -> runOnClient(() -> {
				loading = false;
				if(error != null)
					message = readableMessage(error);
				else
				{
					message = result.message();
					if(result.success())
						onClose();
				}
			}));
	}

	private void runOnClient(Runnable action)
	{
		if(minecraft != null)
			minecraft.execute(() -> {
				if(minecraft.screen == this)
					action.run();
			});
	}

	private String readableMessage(Throwable error)
	{
		Throwable current = error;
		while(current instanceof CompletionException && current.getCause() != null)
			current = current.getCause();
		while(current.getCause() != null)
			current = current.getCause();
		return current.getMessage() == null ? "网易云请求失败"
			: current.getMessage();
	}

	private float hover(String id, boolean hovered)
	{
		return hoverMotions.computeIfAbsent(id, ignored -> new UiTween(0, 150))
			.update(hovered ? 1 : 0);
	}

	@Override
	public void onClose()
	{
		if(closing)
			return;
		qrGeneration++;
		closing = true;
	}

	@Override
	public boolean isPauseScreen()
	{
		return false;
	}

	private Bounds bounds()
	{
		int panelWidth = Math.min(PANEL_WIDTH, Math.max(280, width - 24));
		int panelHeight = Math.min(PANEL_HEIGHT, Math.max(220, height - 24));
		int left = (width - panelWidth) / 2;
		int top = (height - panelHeight) / 2;
		return new Bounds(left, top, left + panelWidth, top + panelHeight);
	}

	private static boolean contains(double x, double y, int left, int top,
		int right, int bottom)
	{
		return x >= left && x < right && y >= top && y < bottom;
	}

	private enum LoginMode
	{
		QR,
		PHONE
	}

	private enum InputField
	{
		NONE,
		PHONE,
		CAPTCHA
	}

	private record Bounds(int left, int top, int right, int bottom)
	{}
}
