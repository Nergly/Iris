/*
 * Iris is a World Generator for Minecraft Bukkit Servers
 * Copyright (c) 2022 Arcane Arts (Volmit Software)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.volmit.iris.engine.mantle.components;

import com.volmit.iris.engine.data.cache.Cache;
import com.volmit.iris.engine.jigsaw.PlannedStructure;
import com.volmit.iris.engine.mantle.EngineMantle;
import com.volmit.iris.engine.mantle.IrisMantleComponent;
import com.volmit.iris.engine.mantle.MantleWriter;
import com.volmit.iris.engine.object.*;
import com.volmit.iris.util.collection.KList;
import com.volmit.iris.util.collection.KMap;
import com.volmit.iris.util.collection.KSet;
import com.volmit.iris.util.context.ChunkContext;
import com.volmit.iris.util.documentation.BlockCoordinates;
import com.volmit.iris.util.documentation.ChunkCoordinates;
import com.volmit.iris.util.mantle.MantleFlag;
import com.volmit.iris.util.math.Position2;
import com.volmit.iris.util.math.RNG;
import com.volmit.iris.util.matter.slices.container.JigsawStructuresContainer;
import com.volmit.iris.util.noise.CNG;
import com.volmit.iris.util.noise.NoiseType;

import java.util.List;

public class MantleJigsawComponent extends IrisMantleComponent {

    public MantleJigsawComponent(EngineMantle engineMantle) {
        super(engineMantle, MantleFlag.JIGSAW);
    }

    private RNG applyNoise(int x, int z) {
        long seed = Cache.key(x, z) + getEngineMantle().getEngine().getSeedManager().getJigsaw();
        CNG cng = CNG.signatureFast(new RNG(seed), NoiseType.WHITE, NoiseType.GLOB);
        return new RNG((long) (seed * cng.noise(x, z)));
    }

    @Override
    public void generateLayer(MantleWriter writer, int x, int z, ChunkContext context) {
        RNG rng = applyNoise(x, z);
        int xxx = 8 + (x << 4);
        int zzz = 8 + (z << 4);
        IrisRegion region = getComplex().getRegionStream().get(xxx, zzz);
        IrisBiome biome = getComplex().getTrueBiomeStream().get(xxx, zzz);
        generateJigsaw(writer, rng, x, z, biome, region);
    }

    @ChunkCoordinates
    private void generateJigsaw(MantleWriter writer, RNG rng, int x, int z, IrisBiome biome, IrisRegion region) {
        if (getDimension().getStronghold() != null) {
            List<Position2> poss = getDimension().getStrongholds(seed());

            if (poss != null) {
                for (Position2 pos : poss) {
                    if (x == pos.getX() >> 4 && z == pos.getZ() >> 4) {
                        IrisJigsawStructure structure = getData().getJigsawStructureLoader().load(getDimension().getStronghold());
                        place(writer, pos.toIris(), structure, rng);
                        return;
                    }
                }
            }
        }

        KSet<Position2> cachedRegions = new KSet<>();
        KMap<String, KSet<Position2>> cache = new KMap<>();
        KMap<Position2, Double> distanceCache = new KMap<>();
        boolean placed = placeStructures(writer, rng, x, z, biome.getJigsawStructures(), cachedRegions, cache, distanceCache);
        if (!placed)
            placed = placeStructures(writer, rng, x, z, region.getJigsawStructures(), cachedRegions, cache, distanceCache);
        if (!placed)
            placeStructures(writer, rng, x, z, getDimension().getJigsawStructures(), cachedRegions, cache, distanceCache);
    }

    @ChunkCoordinates
    private boolean placeStructures(MantleWriter writer, RNG rng, int x, int z, KList<IrisJigsawStructurePlacement> structures,
            KSet<Position2> cachedRegions, KMap<String, KSet<Position2>> cache, KMap<Position2, Double> distanceCache) {
        for (IrisJigsawStructurePlacement i : structures) {
            if (rng.nextInt(i.getRarity()) == 0) {
                if (checkDistances(i.collectDistances(), x, z, cachedRegions, cache, distanceCache))
                    continue;
                IrisPosition position = new IrisPosition((x << 4) + rng.nextInt(15), 0, (z << 4) + rng.nextInt(15));
                IrisJigsawStructure structure = getData().getJigsawStructureLoader().load(i.getStructure());
                if (place(writer, position, structure, rng))
                    return true;
            }
        }
        return false;
    }

    @ChunkCoordinates
    private boolean checkDistances(KMap<String, Integer> distances, int x, int z, KSet<Position2> cachedRegions, KMap<String, KSet<Position2>> cache, KMap<Position2, Double> distanceCache) {
        int range = 0;
        for (int d : distances.values())
            range = Math.max(range, d);

        for (int xx = -range; xx <= range; xx++) {
            for (int zz = -range; zz <= range; zz++) {
                Position2 pos = new Position2((xx + x) >> 5, (zz + z) >> 5);
                if (cachedRegions.contains(pos)) continue;
                cachedRegions.add(pos);
                JigsawStructuresContainer container = getMantle().get(pos.getX(), 0, pos.getZ(), JigsawStructuresContainer.class);
                if (container == null) continue;
                for (String key : container.getStructures()) {
                    cache.computeIfAbsent(key, k -> new KSet<>()).addAll(container.getPositions(key));
                }
            }
        }
        Position2 pos = new Position2(x, z);
        for (String structure : distances.keySet()) {
            if (!cache.containsKey(structure)) continue;
            double minDist = distances.get(structure);
            minDist = minDist * minDist;
            for (Position2 sPos : cache.get(structure)) {
                double dist = distanceCache.computeIfAbsent(sPos,  position2 -> position2.distance(pos));
                if (minDist > dist) return true;
            }
        }
        return false;
    }

    @ChunkCoordinates
    public IrisJigsawStructure guess(int x, int z) {
        // todo The guess doesnt bring into account that the placer may return -1
        // todo doesnt bring skipped placements into account
        RNG rng = applyNoise(x, z);
        IrisBiome biome = getEngineMantle().getEngine().getSurfaceBiome((x << 4) + 8, (z << 4) + 8);
        IrisRegion region = getEngineMantle().getEngine().getRegion((x << 4) + 8, (z << 4) + 8);

        if (getDimension().getStronghold() != null) {
            List<Position2> poss = getDimension().getStrongholds(seed());

            if (poss != null) {
                for (Position2 pos : poss) {
                    if (x == pos.getX() >> 4 && z == pos.getZ() >> 4) {
                        return getData().getJigsawStructureLoader().load(getDimension().getStronghold());
                    }
                }
            }
        }

        for (IrisJigsawStructurePlacement i : biome.getJigsawStructures()) {
            if (rng.nextInt(i.getRarity()) == 0) {
                return getData().getJigsawStructureLoader().load(i.getStructure());
            }
        }

        for (IrisJigsawStructurePlacement i : region.getJigsawStructures()) {
            if (rng.nextInt(i.getRarity()) == 0) {
                return getData().getJigsawStructureLoader().load(i.getStructure());
            }
        }

        for (IrisJigsawStructurePlacement i : getDimension().getJigsawStructures()) {
            if (rng.nextInt(i.getRarity()) == 0) {
                return getData().getJigsawStructureLoader().load(i.getStructure());
            }
        }

        return null;
    }

    @BlockCoordinates
    private boolean place(MantleWriter writer, IrisPosition position, IrisJigsawStructure structure, RNG rng) {
        return new PlannedStructure(structure, position, rng).place(writer, getMantle(), writer.getEngine());
    }
}
