package com.volmit.iris.gen.post;

import org.bukkit.block.data.BlockData;

import com.volmit.iris.gen.PostBlockChunkGenerator;
import com.volmit.iris.util.B;
import com.volmit.iris.util.IrisPostBlockFilter;

@Post("floating-block-remover")
public class PostFloatingNibDeleter extends IrisPostBlockFilter
{
	private static final BlockData AIR = B.getBlockData("AIR");

	public PostFloatingNibDeleter(PostBlockChunkGenerator gen, int phase)
	{
		super(gen, phase);
	}

	public PostFloatingNibDeleter(PostBlockChunkGenerator gen)
	{
		this(gen, 0);
	}

	@Override
	public void onPost(int x, int z)
	{
		int g = 0;
		int h = highestTerrainBlock(x, z);

		if(h < 1)
		{
			return;
		}

		int ha = highestTerrainBlock(x + 1, z);
		int hb = highestTerrainBlock(x, z + 1);
		int hc = highestTerrainBlock(x - 1, z);
		int hd = highestTerrainBlock(x, z - 1);
		g += ha < h - 1 ? 1 : 0;
		g += hb < h - 1 ? 1 : 0;
		g += hc < h - 1 ? 1 : 0;
		g += hd < h - 1 ? 1 : 0;

		if(g == 4 && isAir(x, h - 1, z))
		{
			setPostBlock(x, h, z, AIR);

			for(int i = h - 1; i > 0; i--)
			{
				if(!isAir(x, i, z))
				{
					updateHeight(x, z, i);
					break;
				}
			}
		}
	}
}