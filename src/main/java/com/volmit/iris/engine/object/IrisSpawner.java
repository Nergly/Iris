/*
 * Iris is a World Generator for Minecraft Bukkit Servers
 * Copyright (c) 2021 Arcane Arts (Volmit Software)
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

package com.volmit.iris.engine.object;

import com.volmit.iris.Iris;
import com.volmit.iris.engine.cache.AtomicCache;
import com.volmit.iris.engine.framework.Engine;
import com.volmit.iris.engine.object.annotations.*;
import com.volmit.iris.engine.object.common.IRare;
import com.volmit.iris.engine.stream.ProceduralStream;
import com.volmit.iris.engine.stream.convert.SelectionStream;
import com.volmit.iris.util.collection.KList;
import com.volmit.iris.util.math.RNG;
import com.volmit.iris.util.reflect.V;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;
import org.bukkit.Chunk;

@EqualsAndHashCode(callSuper = true)
@Accessors(chain = true)
@NoArgsConstructor
@AllArgsConstructor
@Desc("Represents an entity spawn during initial chunk generation")
@Data
public class IrisSpawner extends IrisRegistrant {
    @ArrayType(min = 1, type = IrisEntitySpawn.class)
    @Desc("The entity spawns to add")
    private KList<IrisEntitySpawn> spawns = new KList<>();

    private AtomicCache<KList<IrisEntitySpawn>> selection = new AtomicCache<>();

    public boolean spawnInChunk(Engine engine, Chunk c) {
        if(spawns.isEmpty())
        {
            Iris.warn("    Spawner " + getLoadKey() + " has an empty spawn list! (" + getLoadFile().getPath() + ")");
            return false;
        }

        return selection.aquire(() -> {
            KList<IrisEntitySpawn> rarityTypes = new KList<>();

            for (IrisEntitySpawn i : spawns) {
                rarityTypes.addMultiple(i, IRare.get(i));
            }

            return rarityTypes;
        }).getRandom(RNG.r).spawn(engine, c, RNG.r);
    }
}