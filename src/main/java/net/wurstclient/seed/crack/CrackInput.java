/*
 * Copyright (c) 2014-2025 Wurst-Imperium and contributors.
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 */
package net.wurstclient.seed.crack;

import java.util.ArrayList;
import java.util.List;

/**
 * Turns "I saw this structure in this chunk" into the constraint list that
 * {@link LatticeCracker#crack} expects.
 *
 * <p>
 * Vanilla ({@code RandomSpreadStructurePlacement#getPotentialStructureChunk})
 * picks a structure chunk inside a region of {@code spacing} chunks with
 *
 * <pre>
 * long j = regionX * 341873128712L + regionZ * 132897987541L + levelSeed
 * 	+ salt;
 * RandomSource r = new LegacyRandomSource(0L);
 * r.setSeed(j);
 * int offsetX = r.nextInt(spacing - separation);
 * int offsetZ = r.nextInt(spacing - separation);
 * </pre>
 *
 * so a structure that was really found in chunk {@code (chunkX, chunkZ)} gives
 * {@code offsetX = chunkX - regionX * spacing} and the same for Z. Both
 * offsets are the two draws of one stream, which is exactly the pair
 * {@link LatticeCracker} can pin the low bits of the seed with.
 *
 * <p>
 * Note that {@code offsetX} is the value {@code nextInt} returned, so for a
 * power-of-two range it is the fast path result
 * {@code (int)((long)range * (long)next(31) >> 31)} rather than
 * {@code next(31) % range}. {@link LatticeCracker} models both cases.
 */
public final class CrackInput
{
	/** Vanilla offset of a region in X, from {@code StructurePlacement}. */
	public static final long REGION_X_FACTOR = 341873128712L;
	
	/** Vanilla offset of a region in Z, from {@code StructurePlacement}. */
	public static final long REGION_Z_FACTOR = 132897987541L;
	
	/** Salt of {@code StructureSets.VILLAGES} (spacing 34, separation 8). */
	public static final int VILLAGE_SALT = 10387312;
	
	/** Salt of {@code StructureSets.SHIPWRECKS} (spacing 24, separation 4). */
	public static final int SHIPWRECK_SALT = 165745295;
	
	/** Salt of {@code StructureSets.ANCIENT_CITIES} (spacing 24, separation 8). */
	public static final int ANCIENT_CITY_SALT = 20083232;
	
	private CrackInput()
	{
		
	}
	
	/**
	 * @param spacing
	 *            the region size in chunks of the structure set
	 * @param separation
	 *            the minimum distance to the region border in chunks
	 * @param salt
	 *            the salt of the structure set
	 * @param chunkX
	 *            chunk X of the structure the player found
	 * @param chunkZ
	 *            chunk Z of the structure the player found
	 * @return the two constraints (first and second draw of the same stream),
	 *         or an empty list if this placement cannot have produced that
	 *         chunk, or if the range is outside the supported 2..64
	 */
	public static List<LatticeCracker.Constraint> constraintsFor(int spacing,
		int separation, int salt, int chunkX, int chunkZ)
	{
		return constraintsFor(spacing, separation, salt, chunkX, chunkZ,
			false);
	}
	
	/**
	 * Same as
	 * {@link #constraintsFor(int, int, int, int, int)}, but with the
	 * {@code triangular} flag of the placement.
	 *
	 * @param triangular
	 *            not supported yet: a triangular placement averages two draws
	 *            per offset, so it needs three or four draws instead of two and
	 *            is rejected with an empty list instead of a wrong answer
	 */
	public static List<LatticeCracker.Constraint> constraintsFor(int spacing,
		int separation, int salt, int chunkX, int chunkZ, boolean triangular)
	{
		ArrayList<LatticeCracker.Constraint> constraints = new ArrayList<>();
		
		if(triangular)
			return constraints;
		
		int range = spacing - separation;
		
		if(spacing <= 0 || range < 2 || range > LatticeCracker.MAX_RANGE)
			return constraints;
		
		int regionX = Math.floorDiv(chunkX, spacing);
		int regionZ = Math.floorDiv(chunkZ, spacing);
		int wantX = chunkX - regionX * spacing;
		int wantZ = chunkZ - regionZ * spacing;
		
		if(wantX < 0 || wantX >= range || wantZ < 0 || wantZ >= range)
			return constraints;
		
		long base = baseFor(spacing, salt, chunkX, chunkZ);
		constraints.add(new LatticeCracker.Constraint(base, 0, range, wantX));
		constraints.add(new LatticeCracker.Constraint(base, 1, range, wantZ));
		return constraints;
	}
	
	/**
	 * @return the {@code base} value of the stream that places a structure of
	 *         this set in this chunk, i.e. everything the level seed is added
	 *         to before {@code setSeed}
	 */
	public static long baseFor(int spacing, int salt, int chunkX, int chunkZ)
	{
		int regionX = Math.floorDiv(chunkX, spacing);
		int regionZ = Math.floorDiv(chunkZ, spacing);
		return (long)regionX * REGION_X_FACTOR
			+ (long)regionZ * REGION_Z_FACTOR + salt;
	}
	
	/**
	 * @return the chunk a structure of this set would be placed in, given the
	 *         level seed, i.e. the forward direction of the same computation
	 */
	public static int[] structureChunk(int spacing, int separation, int salt,
		long levelSeed, int regionX, int regionZ)
	{
		int range = spacing - separation;
		long base = (long)regionX * REGION_X_FACTOR
			+ (long)regionZ * REGION_Z_FACTOR + salt;
		long state = (levelSeed + base & LatticeCracker.MASK48)
			^ LatticeCracker.A;
		
		state = (LatticeCracker.A * state + LatticeCracker.C)
			& LatticeCracker.MASK48;
		int offsetX = LatticeCracker.nextIntValue(range,
			(int)(state >>> 17));
		
		state = (LatticeCracker.A * state + LatticeCracker.C)
			& LatticeCracker.MASK48;
		int offsetZ = LatticeCracker.nextIntValue(range,
			(int)(state >>> 17));
		
		return new int[]{regionX * spacing + offsetX,
			regionZ * spacing + offsetZ};
	}
}
