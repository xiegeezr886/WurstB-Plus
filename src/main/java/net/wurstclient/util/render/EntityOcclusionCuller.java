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

/**
 * 遮挡查询的持有者：每个实体一个 GL query 对象，另加一个共用的单位立方体 VBO。
 *
 * <p>本类是<b>渲染线程亲和</b>的：{@link #isOccluded} 里 {@code assertOnRenderThread}
 * 就写在第一行，所有 GL 调用也都要求渲染线程。{@link #close()} 因此额外做了一层
 * 保护——不在渲染线程上调用时改用 {@link RenderSystem#recordRenderCall} 排到渲染
 * 线程执行，而不是直接发 GL 调用。这个「谁都可以调 close()，GL 一定落在渲染线程」
 * 的性质要保留。</p>
 *
 * <p>生命周期由 {@link net.wurstclient.hacks.EntityCullingHack} 负责：开启时 new、
 * 关闭（含 panic）时 close、进出世界/切换维度时 close + new。
 * {@code WurstClient.shutdown()} 里<b>故意不关</b>它——那是 JVM 关服钩子的线程，
 * 在那里发 GL 调用不安全；功能还开着时残留的 query / VBO 会随 GL 上下文一起销毁，
 * 这本来就是最后兜底。</p>
 */
public final class EntityOcclusionCuller implements AutoCloseable
{
	/** 只在渲染线程上读写；entities 用的是强引用，靠世界切换时 {@link #reset()} 清掉。 */
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

		// 幂等：queries 清空、unitCube 置回 null 之后，再调一次不会重复删除
		// 已经删掉的 GL 对象（QueryState.id 在 delete() 里被归零，所以也不怕
		// 同一帧里先 removeDeadQueries() 再 close()）。故意不加 closed 标记：
		// 同一个实例允许重新使用。
		queries.values().forEach(this::delete);
		queries.clear();
		if(unitCube != null)
		{
			unitCube.close();
			unitCube = null;
		}
	}

	/**
	 * 清掉当前世界留下的查询状态和实体强引用（{@code queries} 的 key 是
	 * {@code Entity}）。换维度/重进世界时实体对象会被整个替换，旧实体除了这里没有
	 * 别的地方会放手：{@link #removeDeadQueries()} 只按 {@code isRemoved()} 每 256 次
	 * 清理一遍，跨世界时漏掉的那些会一直被引用住。必须在渲染线程上调用。
	 */
	public void reset()
	{
		close();
	}

	private void delete(QueryState state)
	{
		if(state.id != 0)
		{
			GL15.glDeleteQueries(state.id);
			// 必须归零：否则同一个 state 再被 delete 一次就是重复删除 GL 对象
			state.id = 0;
		}
	}

	private static final class QueryState
	{
		private int id;
		private boolean pending;
		private boolean visible = true;
		private long nextQueryMillis;
	}
}
