package net.wurstclient.util.render;

import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.Map;

import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL15;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexFormat;

import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;
import net.wurstclient.util.EasyVertexBuffer;

public final class EntityOcclusionCuller implements AutoCloseable
{
	private final Map<Entity, QueryState> queries = new IdentityHashMap<>();
	private EasyVertexBuffer unitCube;
	private int cleanupCounter;

	public boolean isOccluded(Entity entity, double cameraX, double cameraY,
		double cameraZ, float partialTicks, PoseStack poseStack,
		long visibleDelayMillis, long hiddenDelayMillis)
	{
		RenderSystem.assertOnRenderThread();
		QueryState state = queries.computeIfAbsent(entity, ignored -> new QueryState());
		readAvailableResult(state);

		long now = System.currentTimeMillis();
		if(!state.pending && now >= state.nextQueryMillis)
		{
			issueQuery(entity, state, cameraX, cameraY, cameraZ, partialTicks,
				poseStack);
			state.nextQueryMillis = now
				+ (state.visible ? visibleDelayMillis : hiddenDelayMillis);
		}

		if(++cleanupCounter >= 256)
		{
			cleanupCounter = 0;
			removeDeadQueries();
		}
		return !state.visible;
	}

	private void readAvailableResult(QueryState state)
	{
		if(!state.pending || GL15.glGetQueryObjecti(state.id,
			GL15.GL_QUERY_RESULT_AVAILABLE) == GL11.GL_FALSE)
			return;
		state.visible = GL15.glGetQueryObjecti(state.id,
			GL15.GL_QUERY_RESULT) != 0;
		state.pending = false;
	}

	private void issueQuery(Entity entity, QueryState state, double cameraX,
		double cameraY, double cameraZ, float partialTicks, PoseStack poseStack)
	{
		if(state.id == 0)
			state.id = GL15.glGenQueries();
		ensureUnitCube();

		double renderX = Mth.lerp(partialTicks, entity.xOld, entity.getX());
		double renderY = Mth.lerp(partialTicks, entity.yOld, entity.getY());
		double renderZ = Mth.lerp(partialTicks, entity.zOld, entity.getZ());
		AABB box = entity.getBoundingBox().inflate(0.08);

		poseStack.pushPose();
		poseStack.translate(renderX - entity.getX() + box.minX - cameraX,
			renderY - entity.getY() + box.minY - cameraY,
			renderZ - entity.getZ() + box.minZ - cameraZ);
		poseStack.scale((float)box.getXsize(), (float)box.getYsize(),
			(float)box.getZsize());

		GL15.glBeginQuery(GL15.GL_SAMPLES_PASSED, state.id);
		try
		{
			unitCube.draw(poseStack, RenderType.debugFilledBox(), () -> {
				RenderSystem.colorMask(false, false, false, false);
				RenderSystem.depthMask(false);
			});
		}finally
		{
			GL15.glEndQuery(GL15.GL_SAMPLES_PASSED);
			RenderSystem.colorMask(true, true, true, true);
			RenderSystem.depthMask(true);
			poseStack.popPose();
		}
		state.pending = true;
	}

	private void ensureUnitCube()
	{
		if(unitCube != null)
			return;
		unitCube = EasyVertexBuffer.createAndUpload(
			VertexFormat.Mode.TRIANGLE_STRIP, DefaultVertexFormat.POSITION_COLOR,
			vertices -> LevelRenderer.addChainedFilledBoxVertices(new PoseStack(),
				vertices, 0, 0, 0, 1, 1, 1, 1, 1, 1, 1));
	}

	private void removeDeadQueries()
	{
		for(Iterator<Map.Entry<Entity, QueryState>> iterator = queries.entrySet()
			.iterator(); iterator.hasNext();)
		{
			Map.Entry<Entity, QueryState> entry = iterator.next();
			if(!entry.getKey().isRemoved())
				continue;
			delete(entry.getValue());
			iterator.remove();
		}
	}

	@Override
	public void close()
	{
		if(!RenderSystem.isOnRenderThread())
		{
			RenderSystem.recordRenderCall(this::close);
			return;
		}
		queries.values().forEach(this::delete);
		queries.clear();
		if(unitCube != null)
		{
			unitCube.close();
			unitCube = null;
		}
	}

	private void delete(QueryState state)
	{
		if(state.id != 0)
			GL15.glDeleteQueries(state.id);
	}

	private static final class QueryState
	{
		private int id;
		private boolean pending;
		private boolean visible = true;
		private long nextQueryMillis;
	}
}
