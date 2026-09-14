/*
 * Copyright (c) 2025 Penguin
 */
package net.wurstclient.util;

/**
 * HoleFiller 的纯决策逻辑。判定规则取自参考项目 OpenEpsilon(1.12.2) 的
 * AutoHoleFill（见 {@code _oe_ref/core-spec.md} §13 的过滤链，以及
 * {@code util/combat/HoleUtils.kt} 与 {@code module/combat/AutoHoleFill.kt}
 * 的 getHoleInfos()）：
 *
 * <p>
 * 1. 洞必须是「脚下一格、头上一格都可替换，且下方不是空气」的两格空间。
 *
 * <p>
 * 2. 洞被碰撞箱占住时不能填。参考在 getHoleInfos() 里丢掉两种情况：洞内
 * 已有实体的（{@code entities.none { it.entityBoundingBox.intersects(
 * holeInfo.boundingBox) }}），以及玩家自己身上的洞（「玩家自身 AABB 与
 * detectBox 相交则丢弃该洞（防自伤/防自填）」，{@code AutoHoleFill.kt:317}）。
 *
 * <p>
 * 这里只做几何与方块性质查询，不引用任何 Minecraft 类型，便于单测。
 */
public enum HoleFillPolicy
{
	;

	/**
	 * 方块性质的三分类，用来把洞的判定与 Minecraft 解耦。
	 */
	public enum BlockKind
	{
		/** 空气。 */
		AIR,

		/** 可替换但非空气，例如草、雪层。 */
		REPLACEABLE,

		/** 不可替换，即原版 {@code BlockState#canBeReplaced()} 为 false。 */
		SOLID
	}

	/**
	 * 按绝对坐标查询方块性质。
	 */
	@FunctionalInterface
	public interface BlockLookup
	{
		BlockKind kindAt(int x, int y, int z);
	}

	/**
	 * (x,y,z) 是否是一个可填的洞。
	 */
	public static boolean isHole(BlockLookup blocks, int x, int y, int z)
	{
		return blocks.kindAt(x, y, z) != BlockKind.SOLID
			&& blocks.kindAt(x, y + 1, z) != BlockKind.SOLID
			&& blocks.kindAt(x, y - 1, z) != BlockKind.AIR;
	}

	/**
	 * 洞所在的 1×2 空间是否与给定碰撞箱相交，只有边界相接不算相交。
	 */
	public static boolean isOccupiedBy(double minX, double minY, double minZ,
		double maxX, double maxY, double maxZ, int x, int y, int z)
	{
		return maxX > x && minX < x + 1
			&& maxY > y && minY < y + 2
			&& maxZ > z && minZ < z + 1;
	}
}
