package kaptainwutax.seedcrackerX.util;

import com.mojang.authlib.HttpAuthenticationService;
import com.mojang.authlib.exceptions.AuthenticationException;
import com.mojang.authlib.exceptions.AuthenticationUnavailableException;
import com.mojang.authlib.exceptions.InsufficientPrivilegesException;
import com.mojang.authlib.exceptions.InvalidCredentialsException;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpClient.Version;
import java.net.http.HttpRequest.BodyPublishers;
import java.net.http.HttpResponse.BodyHandlers;
import java.util.HashMap;
import java.util.Map;
import kaptainwutax.seedcrackerX.config.Config;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

public class Database {
   public static Component joinFakeServerForAuth() {
      try {
         Minecraft client = Minecraft.getInstance();
         client.getMinecraftSessionService().joinServer(client.getUser().getGameProfile(), client.getUser().getAccessToken(), "seedcrackerx");
         return null;
      } catch (AuthenticationUnavailableException var1) {
         return Component.translatable("disconnect.loginFailedInfo", new Object[]{Component.translatable("disconnect.loginFailedInfo.serversUnavailable")});
      } catch (InvalidCredentialsException var2) {
         return Component.translatable("disconnect.loginFailedInfo", new Object[]{Component.translatable("disconnect.loginFailedInfo.invalidSession")});
      } catch (InsufficientPrivilegesException var3) {
         return Component.translatable("disconnect.loginFailedInfo", new Object[]{Component.translatable("disconnect.loginFailedInfo.insufficientPrivileges")});
      } catch (AuthenticationException var4) {
         return Component.translatable("disconnect.loginFailedInfo", new Object[]{var4.getMessage()});
      }
   }

   public static void handleDatabaseCall(Long seed) {
      HttpClient httpClient = HttpClient.newBuilder().version(Version.HTTP_2).build();
      Minecraft client = Minecraft.getInstance();
      Map<String, Object> data = new HashMap<>();
      data.put("serverIp", client.getConnection().getConnection().getRemoteAddress().toString());
      data.put("dimension", client.level.dimensionType().effectsLocation().getPath());
      data.put("seed", seed + "L");
      data.put("version", Config.get().getVersion().name);
      data.put("username", client.player.getName().getString());
      data.put("hash", Config.get().anonymusSubmits ? 1 : 0);
      HttpRequest request = HttpRequest.newBuilder()
         .POST(BodyPublishers.ofString(HttpAuthenticationService.buildQuery(data)))
         .uri(URI.create("https://script.google.com/macros/s/AKfycbzU-o8IUaKMQ-MOJEqD8hFGTAC7E15l4uiVqkQsOWxGXgh_HVny6x_TSDVKR8V2wmm9Aw/exec"))
         .setHeader("User-Agent", "SeedcrackerX mod")
         .header("Content-Type", "application/x-www-form-urlencoded")
         .build();

      try {
         HttpResponse<String> response = httpClient.send(request, BodyHandlers.ofString());
         if (response.statusCode() == 302) {
            Log.warn("database.success");
         } else {
            Log.warn("database.fail");
         }
      } catch (InterruptedException | IOException var6) {
         Log.warn("database.fail");
      }
   }
}
