/*
 * Copyright (c) 2014-2025 Wurst-Imperium and contributors.
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 */
package net.wurstclient.background;

/**
 * 骨骼姿势与蒙皮：把 {@link WePuppetRig} 解出来的动画采样，变成每个顶点的位置。
 *
 * <p>
 * 约定全部按 WE 的**行向量**写法（{@code v' = v · M}，平移在矩阵第 4 行，即下标
 * {@code 12..14}）—— 文档见 {@code docs/puppet-mdl-format.md}：</p>
 *
 * <pre>
 * 每帧 9 个 float：位置 xyz、欧拉角 xyz（弧度）、缩放 xyz
 * 四元数 q = qz·qy·qx（等价 R = Rz·Ry·Rx，X 先转），位置/缩放线性插值、旋转短弧 nlerp
 * local = S·R·T；world[i] = local[i] · world[parent]
 * 蒙皮：顶点在**模型空间**（绑定姿势），所以 skin[i] = inverse(worldBind[i]) · world[i]
 *       v' = Σ w_k · (v · skin[k])
 * </pre>
 *
 * <p>
 * <b>自校验</b>：绑定姿势下（动画第 0 帧就是这个姿势，实测每根轨道的第 0 帧位置等于它的
 * 绑定平移）{@code world == worldBind}，于是每根 {@code skin} 都是单位矩阵、蒙皮后的顶点
 * 必须回到原位。这条非常容易证伪，所以它是这一层的判据。</p>
 */
public final class WePuppetPose
{
	private WePuppetPose()
	{}

	/** 单位矩阵。 */
	private static float[] identity()
	{
		float[] m = new float[16];
		m[0] = m[5] = m[10] = m[15] = 1;
		return m;
	}

	/**
	 * 某一时刻每根骨骼的蒙皮矩阵（每根 16 个 float）。
	 *
	 * @param seconds
	 *            动画时间；按 {@code mode} 循环（{@code mirror} 往返、{@code single} 只播一次）
	 */
	public static float[] skinMatrices(WePuppetRig rig,
		WePuppetRig.Animation animation, float seconds)
	{
		int bones = rig.boneCount();

		if(animation == null || animation.tracks().size() != bones)
			return identityAll(bones);

		int frames = animation.frames();
		float time = seconds * animation.fps();

		if(frames > 0)
		{
			float span = frames;

			if("mirror".equalsIgnoreCase(animation.mode()))
			{
				time = time % (2 * span);

				if(time > span)
					time = 2 * span - time;

			}else if("single".equalsIgnoreCase(animation.mode()))
				time = Math.min(time, span);
			else
				time = time % span;
		}

		int first = Math.max(0, (int)Math.floor(time));
		int second = frames > 0 ? Math.min(first + 1, frames) : first;
		float blend = time - first;

		float[][] worldBind = worldBind(rig);
		float[][] world = new float[bones][];
		float[] out = new float[bones * 16];

		for(int i = 0; i < bones; i++)
		{
			WePuppetRig.Bone bone = rig.bones().get(i);
			float[] local =
				localMatrix(animation.tracks().get(i), first, second, blend);
			int parent = bone.parent();

			world[i] = parent >= 0 && parent < i
				? multiply(local, world[parent]) : local;

			// skin = inverse(bind) · world（行向量：先回到骨骼局部、再摆到新姿势）
			float[] skin = multiply(invert(worldBind[i]), world[i]);
			System.arraycopy(skin, 0, out, i * 16, 16);
		}

		return out;
	}

	/** 绑定姿势下每根骨骼的世界矩阵：{@code local · world(parent)}。 */
	private static float[][] worldBind(WePuppetRig rig)
	{
		int bones = rig.boneCount();
		float[][] world = new float[bones][];

		for(int i = 0; i < bones; i++)
		{
			WePuppetRig.Bone bone = rig.bones().get(i);
			int parent = bone.parent();

			world[i] = parent >= 0 && parent < i
				? multiply(bone.bindMatrix(), world[parent]) : bone.bindMatrix();
		}

		return world;
	}

	/** 一根骨骼在 {@code first → second}（混合系数 {@code blend}）的 local 矩阵。 */
	static float[] localMatrix(WePuppetRig.Track track, int first, int second,
		float blend)
	{
		float px = lerp(track.sample(first, 0), track.sample(second, 0), blend);
		float py = lerp(track.sample(first, 1), track.sample(second, 1), blend);
		float pz = lerp(track.sample(first, 2), track.sample(second, 2), blend);

		float[] a = quaternion(track.sample(first, 3), track.sample(first, 4),
			track.sample(first, 5));
		float[] b = quaternion(track.sample(second, 3), track.sample(second, 4),
			track.sample(second, 5));
		float[] q = nlerp(a, b, blend);

		float sx = lerp(track.sample(first, 6), track.sample(second, 6), blend);
		float sy = lerp(track.sample(first, 7), track.sample(second, 7), blend);
		float sz = lerp(track.sample(first, 8), track.sample(second, 8), blend);

		float[] rotation = rowRotation(q);
		float[] m = new float[16];

		// S·R：行 i 乘以 s_i
		for(int row = 0; row < 3; row++)
		{
			float scale = row == 0 ? sx : row == 1 ? sy : sz;

			for(int column = 0; column < 3; column++)
				m[row * 4 + column] = rotation[row * 4 + column] * scale;
		}

		m[12] = px;
		m[13] = py;
		m[14] = pz;
		m[15] = 1;
		return m;
	}

	/** 欧拉角（弧度）→ 四元数，顺序 {@code q = qz·qy·qx}（即 X 先转）。 */
	static float[] quaternion(float rx, float ry, float rz)
	{
		float[] qx = axisQuaternion(rx, 1, 0, 0);
		float[] qy = axisQuaternion(ry, 0, 1, 0);
		float[] qz = axisQuaternion(rz, 0, 0, 1);
		return multiplyQuaternion(multiplyQuaternion(qz, qy), qx);
	}

	private static float[] axisQuaternion(float angle, float x, float y, float z)
	{
		float half = angle * 0.5F;
		float sine = (float)Math.sin(half);
		return new float[]{x * sine, y * sine, z * sine, (float)Math.cos(half)};
	}

	private static float[] multiplyQuaternion(float[] a, float[] b)
	{
		return new float[]{a[3] * b[0] + a[0] * b[3] + a[1] * b[2] - a[2] * b[1],
			a[3] * b[1] - a[0] * b[2] + a[1] * b[3] + a[2] * b[0],
			a[3] * b[2] + a[0] * b[1] - a[1] * b[0] + a[2] * b[3],
			a[3] * b[3] - a[0] * b[0] - a[1] * b[1] - a[2] * b[2]};
	}

	/** 短弧 nlerp（文档：不是 slerp）。 */
	static float[] nlerp(float[] a, float[] b, float t)
	{
		float dot = a[0] * b[0] + a[1] * b[1] + a[2] * b[2] + a[3] * b[3];
		float sign = dot < 0 ? -1 : 1;
		float x = lerp(a[0], sign * b[0], t);
		float y = lerp(a[1], sign * b[1], t);
		float z = lerp(a[2], sign * b[2], t);
		float w = lerp(a[3], sign * b[3], t);
		float length = (float)Math.sqrt(x * x + y * y + z * z + w * w);

		return length > 1e-6F ? new float[]{x / length, y / length, z / length,
			w / length} : new float[]{0, 0, 0, 1};
	}

	/** 四元数 → **行向量**旋转矩阵（列向量那套的转置）。 */
	static float[] rowRotation(float[] q)
	{
		float x = q[0], y = q[1], z = q[2], w = q[3];
		return new float[]{1 - 2 * (y * y + z * z), 2 * (x * y + z * w),
			2 * (x * z - y * w), 0, 2 * (x * y - z * w),
			1 - 2 * (x * x + z * z), 2 * (y * z + x * w), 0,
			2 * (x * z + y * w), 2 * (y * z - x * w),
			1 - 2 * (x * x + y * y), 0, 0, 0, 0, 0};
	}

	private static float lerp(float a, float b, float t)
	{
		return a + (b - a) * t;
	}

	/** 行向量 4x4 相乘：{@code (a·b)[i][j] = Σ a[i][k]·b[k][j]}。 */
	static float[] multiply(float[] a, float[] b)
	{
		float[] out = new float[16];

		for(int row = 0; row < 4; row++)
			for(int column = 0; column < 4; column++)
			{
				float sum = 0;

				for(int k = 0; k < 4; k++)
					sum += a[row * 4 + k] * b[k * 4 + column];

				out[row * 4 + column] = sum;
			}

		return out;
	}

	/** 4x4 求逆（高斯-约当）。绑定矩阵可能带缩放，所以不做"转置即逆"的假设。 */
	static float[] invert(float[] m)
	{
		float[][] a = new float[4][8];

		for(int row = 0; row < 4; row++)
		{
			for(int column = 0; column < 4; column++)
				a[row][column] = m[row * 4 + column];

			a[row][4 + row] = 1;
		}

		for(int column = 0; column < 4; column++)
		{
			int pivot = column;

			for(int row = column + 1; row < 4; row++)
				if(Math.abs(a[row][column]) > Math.abs(a[pivot][column]))
					pivot = row;

			if(Math.abs(a[pivot][column]) < 1e-9F)
				return identity();

			float[] swap = a[column];
			a[column] = a[pivot];
			a[pivot] = swap;

			float scale = a[column][column];

			for(int k = 0; k < 8; k++)
				a[column][k] /= scale;

			for(int row = 0; row < 4; row++)
			{
				if(row == column)
					continue;

				float factor = a[row][column];

				if(factor == 0)
					continue;

				for(int k = 0; k < 8; k++)
					a[row][k] -= factor * a[column][k];
			}
		}

		float[] out = new float[16];

		for(int row = 0; row < 4; row++)
			for(int column = 0; column < 4; column++)
				out[row * 4 + column] = a[row][4 + column];

		return out;
	}

	private static float[] identityAll(int bones)
	{
		float[] out = new float[bones * 16];

		for(int i = 0; i < bones; i++)
			System.arraycopy(identity(), 0, out, i * 16, 16);

		return out;
	}

	/**
	 * 蒙皮后的顶点（只取 x、y；这一层的网格本来就在 z=0 平面上）。
	 *
	 * @param out
	 *            长度 {@code 2 × 顶点数} 的输出缓冲
	 */
	public static void skin(WePuppetMesh mesh, float[] skinMatrices, float[] out)
	{
		float[] positions = mesh.positions();
		int[] indices = mesh.boneIndices();
		float[] weights = mesh.boneWeights();

		for(int v = 0; v < mesh.vertexCount(); v++)
		{
			float x = positions[v * 2];
			float y = positions[v * 2 + 1];
			float sumX = 0;
			float sumY = 0;
			float total = 0;

			for(int k = 0; k < 4; k++)
			{
				float weight = weights[v * 4 + k];

				if(weight == 0)
					continue;

				int bone = indices[v * 4 + k];

				if(bone < 0 || (bone + 1) * 16 > skinMatrices.length)
					continue;

				int at = bone * 16;
				sumX += weight * (x * skinMatrices[at]
					+ y * skinMatrices[at + 4] + skinMatrices[at + 12]);
				sumY += weight * (x * skinMatrices[at + 1]
					+ y * skinMatrices[at + 5] + skinMatrices[at + 13]);
				total += weight;
			}

			if(total > 1e-6F)
			{
				out[v * 2] = sumX / total;
				out[v * 2 + 1] = sumY / total;

			}else
			{
				out[v * 2] = x;
				out[v * 2 + 1] = y;
			}
		}
	}
}
