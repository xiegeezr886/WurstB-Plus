package net.wurstclient.util;

import org.joml.Matrix4f;
import org.joml.Vector4f;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.wurstclient.WurstClient;

public enum WorldToScreen
{
	;

	public static ScreenBounds project(AABB box, Matrix4f view,
		Matrix4f projection)
	{
		Vec3 camera = WurstClient.MC.gameRenderer.mainCamera().position();
		float minX = Float.POSITIVE_INFINITY;
		float minY = Float.POSITIVE_INFINITY;
		float maxX = Float.NEGATIVE_INFINITY;
		float maxY = Float.NEGATIVE_INFINITY;

		for(int x = 0; x < 2; x++)
			for(int y = 0; y < 2; y++)
				for(int z = 0; z < 2; z++)
				{
					Vector4f point = new Vector4f(
						(float)((x == 0 ? box.minX : box.maxX) - camera.x),
						(float)((y == 0 ? box.minY : box.maxY) - camera.y),
						(float)((z == 0 ? box.minZ : box.maxZ) - camera.z), 1);
					view.transform(point);
					projection.transform(point);
					if(point.w <= 0.05F)
						return null;

					float ndcX = point.x / point.w;
					float ndcY = point.y / point.w;
					float screenX = (ndcX * 0.5F + 0.5F)
						* WurstClient.MC.getWindow().getGuiScaledWidth();
					float screenY = (0.5F - ndcY * 0.5F)
						* WurstClient.MC.getWindow().getGuiScaledHeight();
					minX = Math.min(minX, screenX);
					minY = Math.min(minY, screenY);
					maxX = Math.max(maxX, screenX);
					maxY = Math.max(maxY, screenY);
				}

		int width = WurstClient.MC.getWindow().getGuiScaledWidth();
		int height = WurstClient.MC.getWindow().getGuiScaledHeight();
		if(maxX < 0 || maxY < 0 || minX > width || minY > height)
			return null;
		return new ScreenBounds(minX, minY, maxX, maxY);
	}

	public record ScreenBounds(float minX, float minY, float maxX, float maxY)
	{
	}
}
