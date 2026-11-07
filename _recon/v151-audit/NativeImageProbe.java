import java.io.FileInputStream;
import java.io.InputStream;

import com.mojang.blaze3d.platform.NativeImage;

public class NativeImageProbe
{
	public static void main(String[] args) throws Exception
	{
		try(InputStream in = new FileInputStream(args[0]);
			NativeImage image = NativeImage.read(in))
		{
			int width = image.getWidth();
			int height = image.getHeight();
			int[] abgr = image.getPixelsABGR();
			int[] plain = image.getPixels();
			int middle = (height / 2) * width + width / 2;
			System.out.println("size=" + width + "x" + height);
			System.out.printf("ABGR[0]=%08X ABGR[mid]=%08X%n", abgr[0],
				abgr[middle]);
			System.out.printf("PIX[0]=%08X PIX[mid]=%08X%n", plain[0],
				plain[middle]);
			for(int y = 0; y < height && y < 40; y++)
				for(int x = 0; x < width && x < 40; x++)
				{
					int p = abgr[y * width + x];
					if((p >>> 24) != 0)
					{
						System.out.printf("first opaque at %d,%d = %08X%n", x,
							y, p);
						return;
					}
				}
		}
	}
}
