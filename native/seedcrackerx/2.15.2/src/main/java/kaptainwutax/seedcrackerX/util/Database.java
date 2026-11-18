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
import java.net.http.HttpClient.Redirect;
import java.net.http.HttpRequest.BodyPublishers;
import java.net.http.HttpResponse.BodyHandlers;
import java.time.Duration;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import kaptainwutax.seedcrackerX.config.Config;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import org.apache.http.entity.ContentType;
import org.jetbrains.annotations.Nullable;

public class Database {
   private static final String DATABASE_POST_URL = "https://script.google.com/macros/s/AKfycbye87L-fEYq2EkgczvhKb_kGecp5wL1oX95vg45TRSwNvpv7K-53zoInGTeI1FZ0kv7DA/exec";
   private static final String DATABASE_URL = "https://docs.google.com/spreadsheets/d/1tuQiE-0leW88em9OHbZnH-RFNhVqgoHhIt9WQbeqqWw/export?format=csv&gid=0";
   private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder().followRedirects(Redirect.NORMAL).build();
   private static final Duration TIMEOUT = Duration.ofSeconds(10L);
   private static final String SEEDCRACKERX_USER_AGENT = "SeedcrackerX mod";
   private static final Map<String, Long> connectionToSeed = new HashMap<>();
   private static final Map<Long, Long> hashedSeedToSeed = new HashMap<>();

   public static Component joinFakeServerForAuth() {
      try {
         Minecraft client = Minecraft.getInstance();
         client.getMinecraftSessionService().joinServer(client.getUser().getProfileId(), client.getUser().getAccessToken(), "seedcrackerx");
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

   @Nullable
   public static Long getSeed(String connection, long hashedSeed) {
      Long seed = connectionToSeed.get(connection);
      return seed != null ? seed : hashedSeedToSeed.get(hashedSeed);
   }

   public static void handleDatabaseCall(Long seed) {
      Minecraft client = Minecraft.getInstance();
      Map<String, Object> data = new HashMap<>();
      data.put("serverIp", client.getConnection().getConnection().getRemoteAddress().toString());
      data.put("dimension", client.level.dimensionType().effectsLocation().getPath());
      data.put("seed", seed + "L");
      data.put("version", Config.get().getVersion().name);
      data.put("username", client.player.getName().getString());
      data.put("hash", Config.get().anonymusSubmits ? 1 : 0);
      HttpRequest request = HttpRequest.newBuilder(
            URI.create("https://script.google.com/macros/s/AKfycbye87L-fEYq2EkgczvhKb_kGecp5wL1oX95vg45TRSwNvpv7K-53zoInGTeI1FZ0kv7DA/exec")
         )
         .timeout(TIMEOUT)
         .POST(BodyPublishers.ofString(HttpAuthenticationService.buildQuery(data)))
         .setHeader("User-Agent", "SeedcrackerX mod")
         .header("Content-Type", ContentType.APPLICATION_FORM_URLENCODED.getMimeType())
         .build();

      try {
         HttpResponse<String> response = HTTP_CLIENT.send(request, BodyHandlers.ofString());
         if (response.statusCode() == 302) {
            Log.warn("database.success");
         } else {
            Log.warn("database.fail");
         }
      } catch (InterruptedException | IOException var5) {
         Log.warn("database.fail");
      }
   }

   public static void fetchSeeds() {
      HttpRequest request = HttpRequest.newBuilder(
            URI.create("https://docs.google.com/spreadsheets/d/1tuQiE-0leW88em9OHbZnH-RFNhVqgoHhIt9WQbeqqWw/export?format=csv&gid=0")
         )
         .timeout(TIMEOUT)
         .GET()
         .setHeader("User-Agent", "SeedcrackerX mod")
         .build();
      HTTP_CLIENT.sendAsync(request, BodyHandlers.ofString()).thenApply(HttpResponse::body).thenAccept(Database::parseCsv);
   }

   private static void parseCsv(String csv) {
      Arrays.stream(csv.split("\n")).skip(1L).forEach(row -> {
         try {
            String[] seedEntry = row.split(",");
            String connection = seedEntry[0];
            String seedString = seedEntry[2];
            long seed = Long.parseLong(seedString.substring(0, seedString.length() - 1));
            connectionToSeed.put(connection, seed);
            long hashedSeed = Long.parseLong(seedEntry[6]);
            hashedSeedToSeed.put(hashedSeed, seed);
         } catch (ArrayIndexOutOfBoundsException | NumberFormatException var8) {
         }
      });
   }
}
