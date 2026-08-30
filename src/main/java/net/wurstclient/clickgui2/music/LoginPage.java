package net.wurstclient.clickgui2.music;

import java.util.Map;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import net.minecraft.client.gui.GuiGraphics;
import net.wurstclient.clickgui2.FlatRenderer;
import net.wurstclient.clickgui2.supersoft.SuperSoftTheme;
import net.wurstclient.clickgui2.supersoft.UiTween;
import net.wurstclient.music.MusicAccountManager;
import net.wurstclient.music.MusicProvider;
import net.wurstclient.music.NeteaseCloudApi;
import org.lwjgl.glfw.GLFW;

/**
 * 登录页：三平台 Provider 页签 + 网易云手机号/二维码/Cookie 三模式。
 */
public final class LoginPage extends MusicRegion
{
	private final UiTween loginModeMotion = new UiTween(1, 200);
	private LoginMode loginMode = LoginMode.PHONE;
	private MusicProvider loginProvider = MusicProvider.NETEASE;
	private LoginField loginField = LoginField.NONE;
	private String phone = "";
	private String captcha = "";
	private String loginMessage = "";
	private boolean loginLoading;
	private boolean cookieFocused;
	private String cookieInput = "";
	private long captchaAvailableAt;
	private long nextQrPoll;
	private long qrGeneration;
	private NeteaseCloudApi.QrLogin qrLogin;
	private NeteaseCloudApi.QrStatus qrStatus =
		NeteaseCloudApi.QrStatus.WAITING;
	private BitMatrix qrMatrix;

	public LoginPage(MusicContext ctx)
	{
		super(ctx);
	}

	public void render(GuiGraphics graphics, int left, int top, int right, int bottom,
		int mouseX, int mouseY)
	{
		drawCenteredText(graphics, "MUSIC ACCOUNTS", 7, (left + right) / 2,
			top + 9, withAlpha(TEXT, 0.72F), right - left - 20);
		int center = (left + right) / 2;
		int providerLeft = center - 106;
		MusicProvider[] providers = MusicProvider.values();
		for(int index = 0; index < providers.length; index++)
			renderProviderTab(graphics, providers[index],
				providerLeft + index * 72, top + 23, mouseX, mouseY);
		if(loginProvider != MusicProvider.NETEASE)
		{
			renderCookieLogin(graphics, left, top, right, mouseX, mouseY);
			return;
		}
		renderLoginTab(graphics, center - 91, top + 52, "手机号",
			LoginMode.PHONE, mouseX, mouseY);
		renderLoginTab(graphics, center - 28, top + 52, "二维码", LoginMode.QR,
			mouseX, mouseY);
		renderLoginTab(graphics, center + 35, top + 52, "Cookie",
			LoginMode.COOKIE, mouseX, mouseY);
		int slide = Math.round((1 - loginModeMotion.update(1)) * 8);
		graphics.pose().pushPose();
		graphics.pose().translate(slide, 0, 0);
		if(loginMode == LoginMode.PHONE)
			renderInlinePhoneLogin(graphics, left, top, right, mouseX, mouseY);
		else if(loginMode == LoginMode.QR)
			renderInlineQrLogin(graphics, left, top, right, mouseX, mouseY);
		else
			renderCookieLogin(graphics, left, top, right, mouseX, mouseY);
		graphics.pose().popPose();
	}

	private void renderProviderTab(GuiGraphics graphics, MusicProvider provider,
		int left, int top, int mouseX, int mouseY)
	{
		boolean selected = loginProvider == provider;
		boolean hovered = contains(mouseX, mouseY, left, top, left + 68,
			top + 22);
		float progress = motion("provider-" + provider).update(
			selected ? 1 : hovered ? 0.55F : 0);
		FlatRenderer.fillRoundedRect(graphics, left, top, left + 68, top + 22,
			5, SuperSoftTheme.mix(0x16FFFFFF, withAlpha(accent(), 0.19F),
				progress));
		drawText(graphics, provider.getShortName(), 6, left + 7, top + 7,
			selected ? accent() : MUTED, 18);
		drawText(graphics, provider.getDisplayName(), 5, left + 25, top + 8,
			selected ? TEXT : withAlpha(TEXT, 0.65F), 34);
		if(isProviderConnected(provider))
			FlatRenderer.fillRoundedRect(graphics, left + 60, top + 8,
				left + 64, top + 12, 2, 0xFF62D98B);
	}

	private void renderLoginTab(GuiGraphics graphics, int left, int top,
		String label, LoginMode target, int mouseX, int mouseY)
	{
		boolean selected = loginMode == target;
		boolean hovered = contains(mouseX, mouseY, left, top, left + 56,
			top + 22);
		float progress = motion("login-tab-" + target).update(
			selected ? 1 : hovered ? 0.5F : 0);
		FlatRenderer.fillRoundedRect(graphics, left, top, left + 56, top + 22,
			4, withAlpha(accent(), progress));
		drawCenteredText(graphics, label, 7, left + 28, top + 7,
			selected ? TEXT : MUTED, 48);
	}

	private void renderInlinePhoneLogin(GuiGraphics graphics, int left, int top,
		int right, int mouseX, int mouseY)
	{
		int formLeft = left + 8;
		int formRight = right - 8;
		int phoneTop = top + 86;
		renderLoginInput(graphics, formLeft, phoneTop, formRight, phoneTop + 26,
			phone, "手机号", loginField == LoginField.PHONE, mouseX, mouseY,
			"phone");
		int captchaTop = phoneTop + 34;
		int sendLeft = formRight - 82;
		renderLoginInput(graphics, formLeft, captchaTop, sendLeft - 6,
			captchaTop + 26, captcha, "验证码",
			loginField == LoginField.CAPTCHA, mouseX, mouseY, "captcha");
		boolean canSend = !loginLoading && phone.length() == 11
			&& System.currentTimeMillis() >= captchaAvailableAt;
		boolean sendHovered = contains(mouseX, mouseY, sendLeft, captchaTop,
			formRight, captchaTop + 27);
		float sendHover = motion("inline-send").update(
			canSend && sendHovered ? 1 : 0);
		FlatRenderer.fillRoundedRect(graphics, sendLeft - Math.round(sendHover),
			captchaTop - Math.round(sendHover), formRight + Math.round(sendHover),
			captchaTop + 26 + Math.round(sendHover), 4,
			canSend ? accent() : CARD);
		long seconds = Math.max(0,
			(captchaAvailableAt - System.currentTimeMillis() + 999) / 1000);
		drawCenteredText(graphics,
			seconds > 0 ? seconds + "s" : "获取验证码", 6,
			(sendLeft + formRight) / 2, captchaTop + 9,
			canSend ? TEXT : MUTED, formRight - sendLeft - 8);
		int loginTop = captchaTop + 34;
		boolean loginHovered = contains(mouseX, mouseY, formLeft, loginTop,
			formRight, loginTop + 29);
		float loginHover = motion("inline-login").update(
			!loginLoading && loginHovered ? 1 : 0);
		FlatRenderer.fillRoundedRect(graphics, formLeft - Math.round(loginHover),
			loginTop - Math.round(loginHover), formRight + Math.round(loginHover),
			loginTop + 29 + Math.round(loginHover), 4,
			loginLoading ? CARD : accent());
		drawCenteredText(graphics, loginLoading ? "登录中..." : "登录", 8,
			(formLeft + formRight) / 2, loginTop + 10,
			loginLoading ? MUTED : TEXT, formRight - formLeft - 12);
		String status = loginMessage.isBlank()
			? "验证码由网易云官方 HTTPS 接口发送" : loginMessage;
		drawCenteredText(graphics, status, 6, (left + right) / 2,
			loginTop + 39,
			loginMessage.contains("成功") ? 0xFF75D58A
				: loginMessage.isBlank() ? 0x778F7A88 : accent(),
			right - left - 30);
	}

	private void renderLoginInput(GuiGraphics graphics, int left, int top,
		int right, int bottom, String value, String placeholder, boolean active,
		int mouseX, int mouseY, String id)
	{
		FlatRenderer.fillRoundedRect(graphics, left, top, right, bottom, 4, CARD);
		boolean hovered = contains(mouseX, mouseY, left, top, right, bottom);
		float border = motion("login-input-" + id).update(
			active || hovered ? 1 : 0);
		FlatRenderer.drawRoundedOutline(graphics, left, top, right, bottom, 4,
			SuperSoftTheme.mix(0x664F3948, accent(), border));
		String shown = value.isEmpty() ? placeholder : value;
		drawText(graphics, shown, value.isEmpty() ? 6 : 7, left + 7,
			top + 10, value.isEmpty() ? 0x779F8997 : TEXT, right - left - 14);
		if(active && System.currentTimeMillis() / 500 % 2 == 0)
		{
			int cursor = left + 7
				+ font().width(font().plainSubstrByWidth(value,
					right - left - 14));
			graphics.fill(cursor, top + 7, cursor + 1, bottom - 6, accent());
		}
	}

	private void renderInlineQrLogin(GuiGraphics graphics, int left, int top,
		int right, int mouseX, int mouseY)
	{
		int size = 90;
		int qrLeft = (left + right - size) / 2;
		int qrTop = top + 84;
		graphics.fill(qrLeft, qrTop, qrLeft + size, qrTop + size, 0xFFFFFFFF);
		if(qrMatrix == null)
			drawCenteredText(graphics,
				loginLoading ? "正在生成..." : "生成失败", 7,
				qrLeft + size / 2, qrTop + size / 2 - 4, 0xFF4A3A44,
				size - 12);
		else
		{
			int scale = Math.max(1, Math.min(size / qrMatrix.getWidth(),
				size / qrMatrix.getHeight()));
			int offsetX = qrLeft + (size - qrMatrix.getWidth() * scale) / 2;
			int offsetY = qrTop + (size - qrMatrix.getHeight() * scale) / 2;
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
			case ERROR -> loginMessage.isBlank() ? "二维码登录失败" : loginMessage;
		};
		drawCenteredText(graphics, status, 6, (left + right) / 2,
			qrTop + size + 10,
			qrStatus == NeteaseCloudApi.QrStatus.ERROR ? accent() : MUTED,
			right - left - 30);
		drawCenteredText(graphics, "请使用网易云音乐APP扫码登录", 5,
			(left + right) / 2, qrTop + size + 24, withAlpha(TEXT, 0.4F),
			right - left - 30);
	}

	private void renderCookieLogin(GuiGraphics graphics, int left, int top,
		int right, int mouseX, int mouseY)
	{
		MusicAccountManager.AccountStatus status = loginProvider
			== MusicProvider.NETEASE ? null
				: player().getAccountManager().getStatus(loginProvider);
		boolean connected = isProviderConnected(loginProvider);
		int formLeft = left + 18;
		int formRight = right - 18;
		int fieldTop = top + 86;
		drawCenteredText(graphics,
			loginProvider.getDisplayName() + (connected ? " 已连接" : " 会话导入"),
			8, (left + right) / 2, top + 62,
			connected ? 0xFF62D98B : TEXT, right - left - 24);
		FlatRenderer.fillRoundedRect(graphics, formLeft, fieldTop, formRight,
			fieldTop + 32, 5, CARD);
		boolean hovered = contains(mouseX, mouseY, formLeft, fieldTop,
			formRight, fieldTop + 32);
		float focus = motion("cookie-input").update(
			cookieFocused || hovered ? 1 : 0);
		FlatRenderer.drawRoundedOutline(graphics, formLeft, fieldTop, formRight,
			fieldTop + 32, 5,
			SuperSoftTheme.mix(0x334F5A62, accent(), focus));
		String inputText = cookieInput.isEmpty() ? cookiePlaceholder()
			: "Cookie 已输入 " + cookieInput.length() + " 字符";
		drawText(graphics, inputText, 6, formLeft + 8, fieldTop + 11,
			cookieInput.isEmpty() ? MUTED : TEXT, formRight - formLeft - 16);
		if(cookieFocused && System.currentTimeMillis() / 500 % 2 == 0)
			graphics.fill(formRight - 9, fieldTop + 8, formRight - 8,
				fieldTop + 24, accent());

		int actionTop = fieldTop + 40;
		FlatRenderer.fillRoundedRect(graphics, formLeft, actionTop, formRight,
			actionTop + 27, 5, loginLoading ? CARD : accent());
		drawCenteredText(graphics, loginLoading ? "正在验证..." : "保存并连接",
			7, (left + right) / 2, actionTop + 9,
			loginLoading ? MUTED : 0xFF03110F, formRight - formLeft - 12);
		if(connected)
		{
			String account = status == null && player().getUserProfile() != null
				? player().getUserProfile().nickname()
				: status == null ? loginProvider.getDisplayName()
					: status.nickname();
			drawText(graphics, account, 5, formLeft, actionTop + 36,
				withAlpha(TEXT, 0.62F), formRight - formLeft - 54);
			FlatRenderer.fillRoundedRect(graphics, formRight - 48, actionTop + 32,
				formRight, actionTop + 51, 5, 0x22FFFFFF);
			drawCenteredText(graphics, "退出", 5, formRight - 24,
				actionTop + 39, MUTED, 40);
		}
		String hint = loginMessage.isBlank()
			? "Cookie 仅保存在 WurstB 本地配置目录" : loginMessage;
		drawCenteredText(graphics, hint, 5, (left + right) / 2,
			actionTop + 58, loginMessage.isBlank() ? withAlpha(TEXT, 0.42F)
				: loginMessage.contains("已保存") ? 0xFF62D98B : accent(),
			right - left - 28);
	}

	private String cookiePlaceholder()
	{
		return switch(loginProvider)
		{
			case NETEASE -> "MUSIC_U=...; __csrf=...";
			case QQ -> "uin=...; qqmusic_key=...";
			case KUGOU -> "userid=...; token=...";
		};
	}

	public boolean click(double mouseX, double mouseY, MusicContext.Bounds b)
	{
		int contentLeft = b.left + SIDEBAR_WIDTH;
		int center = (contentLeft + b.right) / 2;
		int providerLeft = center - 106;
		MusicProvider[] providers = MusicProvider.values();
		for(int index = 0; index < providers.length; index++)
			if(contains(mouseX, mouseY, providerLeft + index * 72, b.top + 23,
				providerLeft + index * 72 + 68, b.top + 45))
			{
				selectLoginProvider(providers[index]);
				return true;
			}
		if(loginProvider != MusicProvider.NETEASE)
			return clickCookieLogin(mouseX, mouseY, b);
		if(contains(mouseX, mouseY, center - 91, b.top + 52, center - 35,
			b.top + 74))
		{
			switchLoginMode(LoginMode.PHONE);
			return true;
		}
		if(contains(mouseX, mouseY, center - 28, b.top + 52, center + 28,
			b.top + 74))
		{
			switchLoginMode(LoginMode.QR);
			return true;
		}
		if(contains(mouseX, mouseY, center + 35, b.top + 52, center + 91,
			b.top + 74))
		{
			switchLoginMode(LoginMode.COOKIE);
			return true;
		}
		if(loginMode == LoginMode.COOKIE)
			return clickCookieLogin(mouseX, mouseY, b);
		if(loginMode == LoginMode.QR)
		{
			if(qrStatus == NeteaseCloudApi.QrStatus.EXPIRED
				|| qrStatus == NeteaseCloudApi.QrStatus.ERROR)
				startQrLogin();
			return true;
		}
		int formLeft = contentLeft + 8;
		int formRight = b.right - 8;
		int phoneTop = b.top + 86;
		int captchaTop = phoneTop + 34;
		int sendLeft = formRight - 82;
		if(contains(mouseX, mouseY, formLeft, phoneTop, formRight,
			phoneTop + 26))
			loginField = LoginField.PHONE;
		else if(contains(mouseX, mouseY, formLeft, captchaTop, sendLeft - 6,
			captchaTop + 26))
			loginField = LoginField.CAPTCHA;
		else if(contains(mouseX, mouseY, sendLeft, captchaTop, formRight,
			captchaTop + 26))
			sendCaptcha();
		else if(contains(mouseX, mouseY, formLeft, captchaTop + 34, formRight,
			captchaTop + 63))
			loginWithCaptcha();
		else
			loginField = LoginField.NONE;
		return true;
	}

	private boolean clickCookieLogin(double mouseX, double mouseY,
		MusicContext.Bounds b)
	{
		int contentLeft = b.left + SIDEBAR_WIDTH;
		int formLeft = contentLeft + 18;
		int formRight = b.right - 18;
		int fieldTop = b.top + 86;
		int actionTop = fieldTop + 40;
		if(contains(mouseX, mouseY, formLeft, fieldTop, formRight,
			fieldTop + 32))
		{
			cookieFocused = true;
			loginField = LoginField.NONE;
			return true;
		}
		cookieFocused = false;
		if(contains(mouseX, mouseY, formLeft, actionTop, formRight,
			actionTop + 27))
		{
			submitCookieLogin();
			return true;
		}
		if(isProviderConnected(loginProvider)
			&& contains(mouseX, mouseY, formRight - 48, actionTop + 32,
				formRight, actionTop + 51))
		{
			logoutProvider(loginProvider);
			return true;
		}
		return true;
	}

	public void tick()
	{
		if(ctx.page == MusicContext.Page.LOGIN && loginMode == LoginMode.QR
			&& !loginLoading && qrLogin != null
			&& (qrStatus == NeteaseCloudApi.QrStatus.WAITING
				|| qrStatus == NeteaseCloudApi.QrStatus.SCANNED)
			&& System.currentTimeMillis() >= nextQrPoll)
			pollQrLogin();
	}

	public boolean keyPressed(int keyCode, int modifiers)
	{
		if(cookieFocused)
		{
			if(keyCode == GLFW.GLFW_KEY_ENTER)
			{
				submitCookieLogin();
				return true;
			}
			if(keyCode == GLFW.GLFW_KEY_BACKSPACE && !cookieInput.isEmpty())
			{
				cookieInput = cookieInput.substring(0, cookieInput.length() - 1);
				return true;
			}
			if(keyCode == GLFW.GLFW_KEY_V
				&& (modifiers & GLFW.GLFW_MOD_CONTROL) != 0)
			{
				appendCookie(minecraftKeyboardClipboard());
				return true;
			}
		}
		if(loginMode == LoginMode.PHONE)
		{
			if(keyCode == GLFW.GLFW_KEY_TAB)
			{
				loginField = loginField == LoginField.PHONE ? LoginField.CAPTCHA
					: LoginField.PHONE;
				return true;
			}
			if(keyCode == GLFW.GLFW_KEY_ENTER)
			{
				loginWithCaptcha();
				return true;
			}
			if(keyCode == GLFW.GLFW_KEY_BACKSPACE)
			{
				if(loginField == LoginField.PHONE && !phone.isEmpty())
					phone = phone.substring(0, phone.length() - 1);
				else if(loginField == LoginField.CAPTCHA
					&& !captcha.isEmpty())
					captcha = captcha.substring(0, captcha.length() - 1);
				return true;
			}
			if(keyCode == GLFW.GLFW_KEY_V
				&& (modifiers & GLFW.GLFW_MOD_CONTROL) != 0)
			{
				appendLoginDigits(minecraftKeyboardClipboard());
				return true;
			}
		}
		return false;
	}

	public boolean charTyped(char codePoint)
	{
		if(cookieFocused && ctx.page == MusicContext.Page.LOGIN
			&& !Character.isISOControl(codePoint))
		{
			appendCookie(String.valueOf(codePoint));
			return true;
		}
		if(ctx.page == MusicContext.Page.LOGIN
			&& loginMode == LoginMode.PHONE && Character.isDigit(codePoint)
			&& loginField != LoginField.NONE)
		{
			appendLoginDigits(String.valueOf(codePoint));
			return true;
		}
		return false;
	}

	private String minecraftKeyboardClipboard()
	{
		return net.minecraft.client.Minecraft.getInstance().keyboardHandler
			.getClipboard();
	}

	public boolean isCookieFocused()
	{
		return cookieFocused;
	}

	public boolean isPhoneMode()
	{
		return ctx.page == MusicContext.Page.LOGIN
			&& loginMode == LoginMode.PHONE;
	}

	public void resetFocus()
	{
		cookieFocused = false;
	}

	public void invalidateQr()
	{
		qrGeneration++;
	}

	private void appendLoginDigits(String value)
	{
		String digits = value.replaceAll("\\D", "");
		if(loginField == LoginField.PHONE)
			phone = (phone + digits).substring(0,
				Math.min(11, phone.length() + digits.length()));
		else if(loginField == LoginField.CAPTCHA)
			captcha = (captcha + digits).substring(0,
				Math.min(6, captcha.length() + digits.length()));
	}

	private void appendCookie(String value)
	{
		if(value == null || value.isEmpty() || cookieInput.length() >= 16_384)
			return;
		String cleaned = value.replace('\r', ' ').replace('\n', ' ');
		int remaining = 16_384 - cookieInput.length();
		cookieInput += cleaned.substring(0,
			Math.min(remaining, cleaned.length()));
	}

	private void selectLoginProvider(MusicProvider provider)
	{
		if(loginProvider == provider)
			return;
		loginProvider = provider;
		loginMode = LoginMode.PHONE;
		loginField = LoginField.NONE;
		cookieFocused = false;
		cookieInput = "";
		loginMessage = "";
		loginLoading = false;
		qrGeneration++;
		loginModeMotion.snap(0);
	}

	private boolean isProviderConnected(MusicProvider provider)
	{
		if(provider == MusicProvider.NETEASE)
			return player().isLoggedIn();
		return player().getAccountManager().isConnected(provider);
	}

	private void submitCookieLogin()
	{
		if(loginLoading)
			return;
		String submitted = cookieInput.trim();
		if(submitted.isEmpty())
		{
			loginMessage = "请先粘贴 Cookie";
			return;
		}
		loginLoading = true;
		loginMessage = "正在验证会话...";
		if(loginProvider == MusicProvider.NETEASE)
		{
			player().loginWithCookie(submitted).whenComplete((result, error) ->
				ctx.runOnClient(() -> {
					loginLoading = false;
					if(error != null)
						loginMessage = ctx.readableMessage(error);
					else
					{
						loginMessage = result.message();
						if(result.success())
							cookieInput = "";
					}
				}));
			return;
		}
		MusicAccountManager.ImportResult result = player().getAccountManager()
			.importCookie(loginProvider, submitted);
		loginLoading = false;
		loginMessage = result.message();
		if(result.success())
			cookieInput = "";
	}

	private void logoutProvider(MusicProvider provider)
	{
		if(provider == MusicProvider.NETEASE)
		{
			player().logout();
			if(ctx.onLogout != null)
				ctx.onLogout.run();
		}else
			player().getAccountManager().logout(provider);
		loginMessage = provider.getDisplayName() + " 已退出";
		cookieInput = "";
	}

	private void switchLoginMode(LoginMode target)
	{
		if(loginMode == target)
			return;
		loginMode = target;
		loginField = LoginField.NONE;
		cookieFocused = false;
		loginMessage = "";
		loginModeMotion.snap(0);
		qrGeneration++;
		loginLoading = false;
		if(target == LoginMode.QR)
			startQrLogin();
	}

	private void startQrLogin()
	{
		long generation = ++qrGeneration;
		loginMode = LoginMode.QR;
		loginLoading = true;
		loginMessage = "";
		qrLogin = null;
		qrMatrix = null;
		qrStatus = NeteaseCloudApi.QrStatus.WAITING;
		player().beginQrLogin().whenComplete((login, error) ->
			ctx.runOnClient(() -> {
				if(generation != qrGeneration)
					return;
				loginLoading = false;
				if(error != null)
				{
					qrStatus = NeteaseCloudApi.QrStatus.ERROR;
					loginMessage = ctx.readableMessage(error);
					return;
				}
				try
				{
					qrLogin = login;
					qrMatrix = new QRCodeWriter().encode(login.loginUrl(),
						BarcodeFormat.QR_CODE, 37, 37,
						Map.of(EncodeHintType.MARGIN, 1));
					nextQrPoll = System.currentTimeMillis() + 1000;
				}catch(Exception exception)
				{
					qrStatus = NeteaseCloudApi.QrStatus.ERROR;
					loginMessage = "无法生成二维码";
				}
			}));
	}

	private void pollQrLogin()
	{
		long generation = qrGeneration;
		loginLoading = true;
		nextQrPoll = System.currentTimeMillis() + 2000;
		player().checkQrLogin(qrLogin.key()).whenComplete((check, error) ->
			ctx.runOnClient(() -> {
				if(generation != qrGeneration)
					return;
				loginLoading = false;
				if(error != null)
				{
					qrStatus = NeteaseCloudApi.QrStatus.ERROR;
					loginMessage = ctx.readableMessage(error);
					return;
				}
				qrStatus = check.status();
				loginMessage = check.message();
				if(check.status() == NeteaseCloudApi.QrStatus.SUCCESS)
					ctx.switchPage(MusicContext.Page.HOME);
			}));
	}

	private void sendCaptcha()
	{
		if(loginLoading || phone.length() != 11
			|| System.currentTimeMillis() < captchaAvailableAt)
			return;
		loginLoading = true;
		loginMessage = "正在发送验证码...";
		player().sendCaptcha(phone, "86").whenComplete((result, error) ->
			ctx.runOnClient(() -> {
				loginLoading = false;
				if(error != null)
					loginMessage = ctx.readableMessage(error);
				else
				{
					loginMessage = result.message();
					if(result.success())
						captchaAvailableAt = System.currentTimeMillis() + 60000;
				}
			}));
	}

	private void loginWithCaptcha()
	{
		if(loginLoading || phone.length() != 11 || captcha.length() < 4)
			return;
		loginLoading = true;
		loginMessage = "正在登录...";
		player().loginWithCaptcha(phone, captcha, "86")
			.whenComplete((result, error) -> ctx.runOnClient(() -> {
				loginLoading = false;
				if(error != null)
					loginMessage = ctx.readableMessage(error);
				else
				{
					loginMessage = result.message();
					if(result.success())
						ctx.switchPage(MusicContext.Page.HOME);
				}
			}));
	}

	enum LoginMode
	{
		PHONE,
		QR,
		COOKIE
	}

	enum LoginField
	{
		NONE,
		PHONE,
		CAPTCHA
	}
}
