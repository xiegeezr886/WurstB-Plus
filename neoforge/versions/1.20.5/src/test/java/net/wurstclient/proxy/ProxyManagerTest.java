package net.wurstclient.proxy;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import io.netty.handler.proxy.Socks4ProxyHandler;
import io.netty.handler.proxy.Socks5ProxyHandler;
import net.wurstclient.proxy.ProxyConfig.ProxyType;

final class ProxyManagerTest
{
	@Test
	void createsMatchingNettyHandlers(@TempDir Path temp)
	{
		ProxyManager manager = new ProxyManager(temp);
		ProxyConfig socks4 = new ProxyConfig("four", ProxyType.SOCKS4,
			"127.0.0.1", 1080);
		manager.add(socks4);
		manager.setActiveProxy("four");
		assertInstanceOf(Socks4ProxyHandler.class,
			manager.createProxyHandler());

		ProxyConfig socks5 = new ProxyConfig("five", ProxyType.SOCKS5,
			"proxy.example", 1081);
		socks5.setUsername("user");
		socks5.setPassword("secret");
		manager.add(socks5);
		manager.setActiveProxy("five");
		assertInstanceOf(Socks5ProxyHandler.class,
			manager.createProxyHandler());
	}

	@Test
	void replacingAnActiveProxyKeepsReplacementActive(@TempDir Path temp)
	{
		ProxyManager manager = new ProxyManager(temp);
		manager.add(new ProxyConfig("main", ProxyType.SOCKS5, "old", 1080));
		manager.setActiveProxy("main");
		ProxyConfig replacement = new ProxyConfig("main", ProxyType.SOCKS5,
			"new", 1081);
		manager.add(replacement);
		assertSame(replacement, manager.getActiveProxy());
	}

	@Test
	void unknownNamesDoNotClearTheActiveProxy(@TempDir Path temp)
	{
		ProxyManager manager = new ProxyManager(temp);
		ProxyConfig active = new ProxyConfig("main", ProxyType.SOCKS5,
			"host", 1080);
		manager.add(active);
		manager.setActiveProxy("main");
		assertFalse(manager.setActiveProxy("missing"));
		assertSame(active, manager.getActiveProxy());
		assertFalse(manager.remove("missing"));
		assertSame(active, manager.getActiveProxy());
	}

	@Test
	void rejectsInvalidEndpoints()
	{
		assertThrows(IllegalArgumentException.class,
			() -> new ProxyConfig("proxy", ProxyType.SOCKS5, " ", 1080));
		assertThrows(IllegalArgumentException.class,
			() -> new ProxyConfig("proxy", ProxyType.SOCKS5, "host", 0));
		assertThrows(IllegalArgumentException.class,
			() -> new ProxyConfig("proxy", ProxyType.SOCKS5, "host", 65536));
	}
}
