/*
 * Opt-in local test control server.
 *
 * Disabled unless `-Dwurst.test.port=<port>` is set or
 * `<gameDir>/wurst/test-control.properties` contains `port=<port>`.
 * Binds to loopback only and requires a token for every request.
 *
 * Endpoints (send X-Wurst-Token or ?token=):
 *   GET  /ping
 *   GET  /hacks
 *   POST /hacks/enable?name=<name>
 *   POST /hacks/disable?name=<name>
 *   POST /hacks/enableAll
 *   POST /hacks/disableAll
 *   GET  /logs?lines=200&errorsOnly=0
 */
package net.wurstclient.util;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import net.minecraft.client.Screenshot;
import net.wurstclient.WurstClient;
import net.wurstclient.hack.Hack;
import net.wurstclient.hud2.GuiEditHUD;
import net.wurstclient.hud2.HUDMod;
import net.wurstclient.hud2.HudManager;

public final class TestControlServer
{
	private static HttpServer server;
	private static String token;
	private static boolean started;

	private TestControlServer()
	{
	}

	public static synchronized void startIfConfigured()
	{
		if(started)
			return;
		started = true;

		try
		{
			Properties config = loadConfig();
			int port = readPort(config);
			if(port <= 0)
				return;

			token = System.getProperty("wurst.test.token",
				config.getProperty("token", "")).trim();
			Path wurstFolder = WurstClient.MC.gameDirectory.toPath()
				.resolve("wurst");
			Files.createDirectories(wurstFolder);
			if(token.isEmpty())
			{
				token = UUID.randomUUID().toString();
				Path tokenFile = wurstFolder.resolve("test-token.txt");
				Files.writeString(tokenFile, token, StandardCharsets.UTF_8);
				System.out.println("[WurstTest] token written to " + tokenFile);
			}

			server = HttpServer.create(new InetSocketAddress(
				InetAddress.getByName("127.0.0.1"), port), 0);
			server.createContext("/", TestControlServer::handle);
			server.setExecutor(Executors.newSingleThreadExecutor(r -> {
				Thread thread = new Thread(r, "Wurst-TestControl");
				thread.setDaemon(true);
				return thread;
			}));
			server.start();
			System.out.println("[WurstTest] listening on http://127.0.0.1:"
				+ server.getAddress().getPort() + "/");
		}catch(Throwable t)
		{
			System.out.println("[WurstTest] failed to start: " + t);
		}
	}

	private static Properties loadConfig() throws IOException
	{
		Properties config = new Properties();
		Path path = WurstClient.MC.gameDirectory.toPath().resolve("wurst")
			.resolve("test-control.properties");
		if(Files.isRegularFile(path))
			try(InputStream in = Files.newInputStream(path))
			{
				config.load(in);
			}
		return config;
	}

	private static int readPort(Properties config)
	{
		String value = System.getProperty("wurst.test.port",
			config.getProperty("port", "")).trim();
		if(value.isEmpty() || "0".equals(value))
			return 0;
		try
		{
			return Integer.parseInt(value);
		}catch(NumberFormatException e)
		{
			System.out.println("[WurstTest] invalid port: " + value);
			return 0;
		}
	}

	private static void handle(HttpExchange exchange) throws IOException
	{
		try
		{
			if(!"/ping".equals(exchange.getRequestURI().getPath())
				&& !authorized(exchange))
			{
				send(exchange, 401, "text/plain", "unauthorized");
				return;
			}

			String path = exchange.getRequestURI().getPath();
			Map<String, String> query =
				parseQuery(exchange.getRequestURI().getRawQuery());
			String method = exchange.getRequestMethod();

			switch(path)
			{
				case "/ping":
				send(exchange, 200, "text/plain", "ok");
				break;

				case "/hacks":
				send(exchange, 200, "application/json", listHacks());
				break;

				case "/hacks/enable":
				handleSingle(exchange, method, query, true);
				break;

				case "/hacks/disable":
				handleSingle(exchange, method, query, false);
				break;

				case "/hacks/enableAll":
				handleAll(exchange, method, true);
				break;

				case "/hacks/disableAll":
				handleAll(exchange, method, false);
				break;

					case "/hud":
					send(exchange, 200, "application/json", listHud());
					break;

					case "/hud/enableAll":
					handleHudAll(exchange, method, true);
					break;

					case "/hud/disableAll":
					handleHudAll(exchange, method, false);
					break;

					case "/hud/toggle":
					handleHudToggle(exchange, method, query);
					break;

					case "/hud/move":
					handleHudMove(exchange, method, query);
					break;

					case "/hud/scale":
					handleHudScale(exchange, method, query);
					break;

					case "/hud/open":
					handleHudEditor(exchange, method, true);
					break;

					case "/hud/close":
					handleHudEditor(exchange, method, false);
					break;

					case "/world/enter":
					handleWorldEnter(exchange, method, query);
					break;

					case "/command":
					handleCommand(exchange, method, query);
					break;

					case "/screenshot":
					handleScreenshot(exchange, method, query);
					break;

					case "/logs":
					handleLogs(exchange, query);
					break;

				default:
				send(exchange, 404, "text/plain", "not found");
			}
		}catch(Throwable t)
		{
			t.printStackTrace();
			try
			{
				send(exchange, 500, "text/plain", "error: " + t);
			}catch(IOException ignored)
			{
			}
		}finally
		{
			exchange.close();
		}
	}

	private static boolean authorized(HttpExchange exchange)
	{
		String provided = exchange.getRequestHeaders().getFirst("X-Wurst-Token");
		if(provided == null || provided.isBlank())
			provided = parseQuery(exchange.getRequestURI().getRawQuery())
				.get("token");
		return token != null && !token.isEmpty() && token.equals(provided);
	}

	private static void handleSingle(HttpExchange exchange, String method,
		Map<String, String> query, boolean enable) throws Exception
	{
		if(!"POST".equalsIgnoreCase(method))
		{
			send(exchange, 405, "text/plain", "POST required");
			return;
		}
		String name = query.get("name");
		if(name == null || name.isBlank())
		{
			send(exchange, 400, "application/json",
				"{\"ok\":false,\"error\":\"name required\"}");
			return;
		}

		String result = onClientThread(() -> {
			Hack hack = WurstClient.INSTANCE.getHax().getHackByName(name);
			if(hack == null)
				return "{\"ok\":false,\"error\":\"unknown hack\"}";
			hack.setEnabled(enable);
			return "{\"ok\":true,\"name\":\"" + escape(hack.getName())
				+ "\",\"enabled\":" + hack.isEnabled() + "}";
		});
		send(exchange, 200, "application/json", result);
	}

	private static void handleAll(HttpExchange exchange, String method,
		boolean enable) throws Exception
	{
		if(!"POST".equalsIgnoreCase(method))
		{
			send(exchange, 405, "text/plain", "POST required");
			return;
		}

		String result = onClientThread(() -> {
			int changed = 0;
			int failed = 0;
			List<String> failedNames = new ArrayList<>();
			for(Hack hack : WurstClient.INSTANCE.getHax().getAllHax())
			{
				if("ClickGUI".equals(hack.getName())
					|| "Navigator".equals(hack.getName())
					|| "Panic".equals(hack.getName()))
					continue;
				try
				{
					System.out.println("[WurstTest] "
						+ (enable ? "enable " : "disable ")
						+ hack.getName());
					hack.setEnabled(enable);
					if(hack.isEnabled() == enable)
						changed++;
					else
					{
						failed++;
						failedNames.add(hack.getName());
					}
				}catch(Throwable t)
				{
					failed++;
					failedNames.add(hack.getName());
				}
			}
			StringBuilder json = new StringBuilder();
			json.append("{\"ok\":true,\"changed\":").append(changed)
				.append(",\"failed\":").append(failed)
				.append(",\"failedNames\":[");
			for(int i = 0; i < failedNames.size(); i++)
			{
				if(i > 0)
					json.append(',');
				json.append('"').append(escape(failedNames.get(i)))
					.append('"');
			}
			return json.append("]}").toString();
		});
		send(exchange, 200, "application/json", result);
	}

	private static String listHud()
	{
		HudManager manager = WurstClient.INSTANCE.getHudManager();
		StringBuilder json = new StringBuilder("{\"modules\":[");
		boolean first = true;
		for(HUDMod mod : manager.getMods().values())
		{
			if(!first)
				json.append(',');
			first = false;
			json.append("{\"id\":\"").append(escape(mod.getId()))
				.append("\",\"name\":\"").append(escape(mod.getDisplayName()))
				.append("\",\"enabled\":").append(mod.isEnabled())
				.append(",\"x\":").append(mod.getX())
				.append(",\"y\":").append(mod.getY())
				.append(",\"scale\":").append(mod.getScale())
				.append(",\"width\":").append(mod.getWidth())
				.append(",\"height\":").append(mod.getHeight())
				.append('}');
		}
		return json.append("]}").toString();
	}

	private static void handleHudAll(HttpExchange exchange, String method,
		boolean enable) throws Exception
	{
		if(!"POST".equalsIgnoreCase(method))
		{
			send(exchange, 405, "text/plain", "POST required");
			return;
		}

		String result = onClientThread(() -> {
			int changed = 0;
			int failed = 0;
			for(HUDMod mod : WurstClient.INSTANCE.getHudManager().getMods()
				.values())
			{
				try
				{
					mod.setEnabled(enable);
					if(mod.isEnabled() == enable)
						changed++;
					else
						failed++;
				}catch(Throwable t)
				{
					failed++;
				}
			}
			return "{\"ok\":true,\"changed\":" + changed + ",\"failed\":"
				+ failed + "}";
		});
		send(exchange, 200, "application/json", result);
	}

	private static void handleHudToggle(HttpExchange exchange, String method,
		Map<String, String> query) throws Exception
	{
		if(!"POST".equalsIgnoreCase(method))
		{
			send(exchange, 405, "text/plain", "POST required");
			return;
		}
		String id = query.get("id");
		boolean enabled = Boolean.parseBoolean(query.getOrDefault("enabled",
			"true"));
		String result = onClientThread(() -> {
			HUDMod mod = WurstClient.INSTANCE.getHudManager().getModule(id);
			if(mod == null)
				return "{\"ok\":false,\"error\":\"unknown hud module\"}";
			mod.setEnabled(enabled);
			return "{\"ok\":true,\"id\":\"" + escape(mod.getId())
				+ "\",\"enabled\":" + mod.isEnabled() + "}";
		});
		send(exchange, 200, "application/json", result);
	}

	private static void handleHudMove(HttpExchange exchange, String method,
		Map<String, String> query) throws Exception
	{
		if(!"POST".equalsIgnoreCase(method))
		{
			send(exchange, 405, "text/plain", "POST required");
			return;
		}
		String result = onClientThread(() -> {
			HUDMod mod = WurstClient.INSTANCE.getHudManager()
				.getModule(query.get("id"));
			if(mod == null)
				return "{\"ok\":false,\"error\":\"unknown hud module\"}";
			try
			{
				mod.setX(Integer.parseInt(query.getOrDefault("x",
					Integer.toString(mod.getX()))));
				mod.setY(Integer.parseInt(query.getOrDefault("y",
					Integer.toString(mod.getY()))));
				WurstClient.INSTANCE.getHudManager().saveState();
				return "{\"ok\":true}";
			}catch(NumberFormatException e)
			{
				return "{\"ok\":false,\"error\":\"invalid coordinates\"}";
			}
		});
		send(exchange, 200, "application/json", result);
	}

	private static void handleHudScale(HttpExchange exchange, String method,
		Map<String, String> query) throws Exception
	{
		if(!"POST".equalsIgnoreCase(method))
		{
			send(exchange, 405, "text/plain", "POST required");
			return;
		}
		String result = onClientThread(() -> {
			HUDMod mod = WurstClient.INSTANCE.getHudManager()
				.getModule(query.get("id"));
			if(mod == null)
				return "{\"ok\":false,\"error\":\"unknown hud module\"}";
			try
			{
				mod.setScale(Float.parseFloat(query.getOrDefault("scale",
					Float.toString(mod.getScale()))));
				WurstClient.INSTANCE.getHudManager().saveState();
				return "{\"ok\":true}";
			}catch(NumberFormatException e)
			{
				return "{\"ok\":false,\"error\":\"invalid scale\"}";
			}
		});
		send(exchange, 200, "application/json", result);
	}

	private static void handleHudEditor(HttpExchange exchange, String method,
		boolean open) throws Exception
	{
		if(!"POST".equalsIgnoreCase(method))
		{
			send(exchange, 405, "text/plain", "POST required");
			return;
		}
		String result = onClientThread(() -> {
			WurstClient.MC.setScreen(open ? new GuiEditHUD() : null);
			return "{\"ok\":true}";
		});
		send(exchange, 200, "application/json", result);
	}

	private static void handleWorldEnter(HttpExchange exchange, String method,
		Map<String, String> query) throws Exception
	{
		if(!"POST".equalsIgnoreCase(method))
		{
			send(exchange, 405, "text/plain", "POST required");
			return;
		}
		String name = query.getOrDefault("name", "xray-test");
		String result = onClientThread(() -> {
			try
			{
				WurstClient.MC.createWorldOpenFlows()
					.loadLevel(WurstClient.MC.screen, name);
				return "{\"ok\":true,\"world\":\"" + escape(name) + "\"}";
			}catch(Throwable t)
			{
				return "{\"ok\":false,\"error\":\"" + escape(t.toString())
					+ "\"}";
			}
		});
		send(exchange, 200, "application/json", result);
	}

	private static void handleCommand(HttpExchange exchange, String method,
		Map<String, String> query) throws Exception
	{
		if(!"POST".equalsIgnoreCase(method))
		{
			send(exchange, 405, "text/plain", "POST required");
			return;
		}
		String command = query.get("cmd");
		if(command == null || command.isBlank())
		{
			send(exchange, 400, "text/plain", "cmd required");
			return;
		}
		String normalized = command.startsWith("/")
			? command.substring(1) : command;

		String result = onClientThread(() -> {
			if(WurstClient.MC.player == null
				|| WurstClient.MC.player.connection == null)
				return "{\"ok\":false,\"error\":\"no player\"}";
			try
			{
				WurstClient.MC.player.connection.sendCommand(normalized);
				return "{\"ok\":true}";
			}catch(Throwable t)
			{
				return "{\"ok\":false,\"error\":\"" + escape(t.toString())
					+ "\"}";
			}
		});
		send(exchange, 200, "application/json", result);
	}

	private static void handleScreenshot(HttpExchange exchange, String method,
		Map<String, String> query) throws Exception
	{
		if(!"POST".equalsIgnoreCase(method))
		{
			send(exchange, 405, "text/plain", "POST required");
			return;
		}
		String name = query.getOrDefault("name", "hud-test")
			.replaceAll("[^A-Za-z0-9_-]", "_");
		String result = onClientThread(() -> {
			try
			{
				File directory = new File(WurstClient.MC.gameDirectory,
					"screenshots");
				directory.mkdirs();
				Screenshot.grab(directory, name,
					WurstClient.MC.getMainRenderTarget(), component -> {});
				File[] files = directory
					.listFiles((dir, fileName) -> fileName.startsWith(name)
						&& fileName.endsWith(".png"));
				if(files == null || files.length == 0)
					return "{\"ok\":true,\"file\":null}";
				File newest = files[0];
				for(File file : files)
					if(file.lastModified() > newest.lastModified())
						newest = file;
				return "{\"ok\":true,\"file\":\""
					+ escape(newest.getAbsolutePath().replace('\\', '/'))
					+ "\"}";
			}catch(Throwable t)
			{
				return "{\"ok\":false,\"error\":\"" + escape(t.toString())
					+ "\"}";
			}
		});
		send(exchange, 200, "application/json", result);
	}

	private static void handleLogs(HttpExchange exchange,
		Map<String, String> query) throws IOException
	{
		int lines = parseInt(query.get("lines"), 200, 1, 5000);
		boolean errorsOnly = "1".equals(query.get("errorsOnly"))
			|| "true".equalsIgnoreCase(query.get("errorsOnly"));

		Path logFile = WurstClient.MC.gameDirectory.toPath().resolve("logs")
			.resolve("latest.log");
		if(!Files.isRegularFile(logFile))
		{
			send(exchange, 404, "text/plain", "latest.log not found");
			return;
		}

		List<String> all = Files.readAllLines(logFile, StandardCharsets.UTF_8);
		int from = Math.max(0, all.size() - lines);
		StringBuilder result = new StringBuilder();
		for(int i = from; i < all.size(); i++)
		{
			String line = all.get(i);
			if(errorsOnly && !isErrorLine(line))
				continue;
			result.append(line).append('\n');
		}
		send(exchange, 200, "text/plain; charset=utf-8", result.toString());
	}

	private static boolean isErrorLine(String line)
	{
		return line.contains("ERROR") || line.contains("FATAL")
			|| line.contains("Exception") || line.contains("Caused by");
	}

	private static String listHacks()
	{
		StringBuilder json = new StringBuilder("[");
		boolean first = true;
		for(Hack hack : WurstClient.INSTANCE.getHax().getAllHax())
		{
			if(!first)
				json.append(',');
			first = false;
			json.append("{\"name\":\"").append(escape(hack.getName()))
				.append("\",\"enabled\":").append(hack.isEnabled()).append('}');
		}
		return json.append(']').toString();
	}

	private static <T> T onClientThread(java.util.concurrent.Callable<T> action)
		throws Exception
	{
		CompletableFuture<T> future = new CompletableFuture<>();
		WurstClient.MC.execute(() -> {
			try
			{
				future.complete(action.call());
			}catch(Throwable t)
			{
				future.completeExceptionally(t);
			}
		});
		return future.get(120, TimeUnit.SECONDS);
	}

	private static Map<String, String> parseQuery(String rawQuery)
	{
		Map<String, String> result = new LinkedHashMap<>();
		if(rawQuery == null || rawQuery.isEmpty())
			return result;
		for(String part : rawQuery.split("&"))
		{
			int eq = part.indexOf('=');
			String key = eq < 0 ? part : part.substring(0, eq);
			String value = eq < 0 ? "" : part.substring(eq + 1);
			result.put(URLDecoder.decode(key, StandardCharsets.UTF_8),
				URLDecoder.decode(value, StandardCharsets.UTF_8));
		}
		return result;
	}

	private static int parseInt(String value, int fallback, int min, int max)
	{
		if(value == null)
			return fallback;
		try
		{
			return Math.max(min, Math.min(max, Integer.parseInt(value)));
		}catch(NumberFormatException e)
		{
			return fallback;
		}
	}

	private static String escape(String value)
	{
		StringBuilder out = new StringBuilder();
		for(char c : value.toCharArray())
			switch(c)
			{
				case '"':
				out.append("\\\"");
				break;
				case '\\':
				out.append("\\\\");
				break;
				case '\n':
				out.append("\\n");
				break;
				case '\r':
				out.append("\\r");
				break;
				case '\t':
				out.append("\\t");
				break;
				default:
				out.append(c);
			}
		return out.toString();
	}

	private static void send(HttpExchange exchange, int status, String type,
		String body) throws IOException
	{
		byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
		exchange.getResponseHeaders().set("Content-Type", type);
		exchange.sendResponseHeaders(status, bytes.length);
		try(OutputStream out = exchange.getResponseBody())
		{
			out.write(bytes);
		}
	}
}
