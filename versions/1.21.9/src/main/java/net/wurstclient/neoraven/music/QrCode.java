/* Music player adapted from NeoRaven (GPL-3.0). See META-INF/licenses/neoraven/MUSIC-NOTICE.txt. */
package net.wurstclient.neoraven.music;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;
import java.util.HashMap;
import java.util.Map;

/**
 * QR encoding for the NetEase login flow.
 *
 * <p>NetEase has no endpoint that renders the login QR; the web client builds the {@code
 * https://music.163.com/login?codekey=<unikey>} URL locally and draws the code itself, so the port
 * has to encode it too. ZXing core is embedded in the jar for exactly this call.
 */
public final class QrCode {

  private QrCode() {}

  /** Encodes {@code content} into a module matrix ({@code [y][x]}), or {@code null} on failure. */
  public static boolean[][] encode(String content, int quietZone) {
    try {
      Map<EncodeHintType, Object> hints = new HashMap<EncodeHintType, Object>();
      hints.put(EncodeHintType.MARGIN, quietZone);
      hints.put(EncodeHintType.ERROR_CORRECTION, ErrorCorrectionLevel.M);
      hints.put(EncodeHintType.CHARACTER_SET, "UTF-8");
      BitMatrix matrix = new QRCodeWriter().encode(content, BarcodeFormat.QR_CODE, 0, 0, hints);
      int width = matrix.getWidth();
      int height = matrix.getHeight();
      boolean[][] modules = new boolean[height][width];
      for (int y = 0; y < height; y++) {
        for (int x = 0; x < width; x++) {
          modules[y][x] = matrix.get(x, y);
        }
      }
      return modules;
    } catch (Throwable t) {
      return null;
    }
  }
}
