/* Fabric adaptation of NeoRaven CoverCache; GPL-3.0. */
package net.wurstclient.neoraven.music;

import com.mojang.blaze3d.platform.NativeImage;
import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;
import javax.imageio.ImageIO;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;
import net.wurstclient.hud2.nanovg.NanoVGContext;

/** Bounded thumbnails; HTTP and image decoding never run in a render pass. */
public final class CoverCache {
  private static final LinkedHashMap<String, ResourceLocation> textures =
      new LinkedHashMap<>(32, .75F, true);
  private static final Set<String> pending = ConcurrentHashMap.newKeySet();
  private static final Map<String, Long> failures = new ConcurrentHashMap<>();
  private static final ExecutorService io =
      Executors.newFixedThreadPool(
          2,
          r -> {
            Thread t = new Thread(r, "wurst-music-covers");
            t.setDaemon(true);
            return t;
          });
  private static volatile boolean closed;
  private static int nextId;

  private CoverCache() {}

  public static ResourceLocation get(String url) {
    if (closed || url == null || url.isEmpty()) return null;
    ResourceLocation found = textures.get(url);
    if (found != null) return found;
    if (pending.size() >= 16
        || failures.getOrDefault(url, 0L) > System.currentTimeMillis()
        || !pending.add(url)) return null;
    io.execute(
        () -> {
          try {
            HttpURLConnection c =
                (HttpURLConnection) new URL(url.replace("http://", "https://")).openConnection();
            c.setConnectTimeout(6000);
            c.setReadTimeout(8000);
            byte[] bytes;
            try (InputStream in = c.getInputStream()) {
              bytes = in.readNBytes(2 * 1024 * 1024 + 1);
              if (bytes.length > 2 * 1024 * 1024) throw new IOException("Cover too large");
            } finally {
              c.disconnect();
            }
            var image = ImageIO.read(new ByteArrayInputStream(bytes));
            if (image == null || image.getWidth() > 1024 || image.getHeight() > 1024)
              throw new IOException("Invalid cover");
            // A fixed texture size bounds VRAM even for unexpectedly large API thumbnails.
            var small =
                new java.awt.image.BufferedImage(
                    96, 96, java.awt.image.BufferedImage.TYPE_INT_ARGB);
            var graphics = small.createGraphics();
            graphics.drawImage(image, 0, 0, 96, 96, null);
            graphics.dispose();
            int[] pixels = small.getRGB(0, 0, 96, 96, null, 0, 96);
            Minecraft.getInstance()
                .execute(
                    () -> {
                      try {
                        if (closed) return;
                        NativeImage nativeImage = new NativeImage(96, 96, false);
                        for (int y = 0; y < 96; y++)
                          for (int x = 0; x < 96; x++) {
                            int p = pixels[y * 96 + x];
                            nativeImage.setPixel(x, y, p);
                          }
                        ResourceLocation id =
                            ResourceLocation.fromNamespaceAndPath("wurst", "music/cover_" + nextId++);
                        Minecraft.getInstance()
                            .getTextureManager()
                            .register(id, new DynamicTexture(nativeImage));
                        textures.put(url, id);
                        if (textures.size() > 48) {
                          var first = textures.entrySet().iterator();
                          release(first.next().getValue());
                          first.remove();
                        }
                      } finally {
                        pending.remove(url);
                      }
                    });
          } catch (Exception e) {
            if (failures.size() > 128) failures.clear();
            failures.put(url, System.currentTimeMillis() + 60000);
            pending.remove(url);
          }
        });
    return null;
  }

  private static void release(ResourceLocation id) {
    var manager = Minecraft.getInstance().getTextureManager();
    NanoVGContext.get().forgetTexture(manager.getTexture(id).getId());
    manager.release(id);
  }

  public static void close() {
    closed = true;
    io.shutdownNow();
    textures.values().forEach(CoverCache::release);
    textures.clear();
    failures.clear();
  }
}
