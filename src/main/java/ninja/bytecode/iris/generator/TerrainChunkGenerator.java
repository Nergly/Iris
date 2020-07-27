package ninja.bytecode.iris.generator;

import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.data.Bisected;
import org.bukkit.block.data.Bisected.Half;
import org.bukkit.block.data.BlockData;

import lombok.Data;
import lombok.EqualsAndHashCode;
import ninja.bytecode.iris.layer.GenLayerCave;
import ninja.bytecode.iris.object.DecorationPart;
import ninja.bytecode.iris.object.InferredType;
import ninja.bytecode.iris.object.IrisBiome;
import ninja.bytecode.iris.object.IrisBiomeDecorator;
import ninja.bytecode.iris.object.IrisRegion;
import ninja.bytecode.iris.object.atomics.AtomicSliver;
import ninja.bytecode.iris.util.BiomeMap;
import ninja.bytecode.iris.util.BiomeResult;
import ninja.bytecode.iris.util.BlockDataTools;
import ninja.bytecode.iris.util.HeightMap;
import ninja.bytecode.iris.util.RNG;
import ninja.bytecode.shuriken.collections.KList;
import ninja.bytecode.shuriken.math.M;

@Data
@EqualsAndHashCode(callSuper = false)
public abstract class TerrainChunkGenerator extends ParallelChunkGenerator
{
	private long lastUpdateRequest = M.ms();
	private long lastChunkLoad = M.ms();
	private GenLayerCave glCave;
	private RNG rockRandom;

	public TerrainChunkGenerator(String dimensionName, int threads)
	{
		super(dimensionName, threads);
	}

	public void onInit(World world, RNG rng)
	{
		super.onInit(world, rng);
		rockRandom = getMasterRandom().nextParallelRNG(2858678);
		glCave = new GenLayerCave(this, rng.nextParallelRNG(238948));
	}

	@Override
	protected void onGenerateColumn(int cx, int cz, int rx, int rz, int x, int z, AtomicSliver sliver, BiomeMap biomeMap)
	{
		try
		{
			BlockData block;
			int fluidHeight = getDimension().getFluidHeight();
			double ox = getModifiedX(rx, rz);
			double oz = getModifiedZ(rx, rz);
			double wx = getZoomed(ox);
			double wz = getZoomed(oz);
			int depth = 0;
			double noise = getNoiseHeight(rx, rz);
			int height = (int) Math.round(noise) + fluidHeight;
			IrisBiome biome = sampleTrueBiome(rx, rz).getBiome();
			KList<BlockData> layers = biome.generateLayers(wx, wz, masterRandom, height, height - getFluidHeight());
			KList<BlockData> seaLayers = biome.isSea() ? biome.generateSeaLayers(wx, wz, masterRandom, fluidHeight - height) : new KList<>();
			cacheBiome(x, z, biome);

			for(int k = Math.max(height, fluidHeight); k < Math.max(height, fluidHeight) + 3; k++)
			{
				if(k < Math.max(height, fluidHeight) + 3)
				{
					if(biomeMap != null)
					{
						sliver.set(k, biome.getGroundBiome(masterRandom, rz, k, rx));
					}
				}
			}

			for(int k = Math.max(height, fluidHeight); k >= 0; k--)
			{
				if(k == 0)
				{
					sliver.set(0, BEDROCK);
					continue;
				}

				boolean underwater = k > height && k <= fluidHeight;

				if(biomeMap != null)
				{
					sliver.set(k, biome.getGroundBiome(masterRandom, rz, k, rx));
					biomeMap.setBiome(x, z, biome);
				}

				if(underwater)
				{
					block = seaLayers.hasIndex(fluidHeight - k) ? layers.get(depth) : getDimension().getFluid(rockRandom, wx, k, wz);
				}

				else
				{
					block = layers.hasIndex(depth) ? layers.get(depth) : getDimension().getRock(rockRandom, wx, k, wz);
					depth++;
				}

				sliver.set(k, block);

				if(k == height && block.getMaterial().isSolid() && k < fluidHeight)
				{
					decorateUnderwater(biome, sliver, wx, k, wz, rx, rz, block);
				}

				if(k == Math.max(height, fluidHeight) && block.getMaterial().isSolid() && k < 255 && k > fluidHeight)
				{
					decorateLand(biome, sliver, wx, k, wz, rx, rz, block);
				}
			}

			glCave.genCaves(rx, rz, x, z, sliver);
		}

		catch(Throwable e)
		{
			fail(e);
		}
	}

	protected boolean canPlace(Material mat, Material onto)
	{
		if(onto.equals(Material.GRASS_PATH))
		{
			if(!mat.isSolid())
			{
				return false;
			}
		}

		if(onto.equals(Material.ACACIA_LEAVES) || onto.equals(Material.BIRCH_LEAVES) || onto.equals(Material.DARK_OAK_LEAVES) || onto.equals(Material.JUNGLE_LEAVES) || onto.equals(Material.OAK_LEAVES) || onto.equals(Material.SPRUCE_LEAVES))
		{
			if(!mat.isSolid())
			{
				return false;
			}
		}

		return true;
	}

	private void decorateLand(IrisBiome biome, AtomicSliver sliver, double wx, int k, double wz, int rx, int rz, BlockData block)
	{
		if(!getDimension().isDecorate())
		{
			return;
		}

		int j = 0;

		for(IrisBiomeDecorator i : biome.getDecorators())
		{
			if(i.getPartOf().equals(DecorationPart.SHORE_LINE) && !touchesSea(rx, rz))
			{
				continue;
			}

			BlockData d = i.getBlockData(getMasterRandom().nextParallelRNG(biome.hashCode() + j++), wx, wz);

			if(d != null)
			{
				if(!canPlace(d.getMaterial(), block.getMaterial()))
				{
					continue;
				}

				if(d.getMaterial().equals(Material.CACTUS))
				{
					if(!block.getMaterial().equals(Material.SAND) && !block.getMaterial().equals(Material.RED_SAND))
					{
						sliver.set(k, BlockDataTools.getBlockData("RED_SAND"));
					}
				}

				if(d instanceof Bisected && k < 254)
				{
					Bisected t = ((Bisected) d.clone());
					t.setHalf(Half.TOP);
					Bisected b = ((Bisected) d.clone());
					b.setHalf(Half.BOTTOM);
					sliver.set(k + 1, b);
					sliver.set(k + 2, t);
				}

				else
				{
					int stack = i.getHeight(getMasterRandom().nextParallelRNG(39456 + i.hashCode()), wx, wz);

					if(stack == 1)
					{
						sliver.set(k + 1, d);
					}

					else if(k < 255 - stack)
					{
						for(int l = 0; l < stack; l++)
						{
							sliver.set(k + l + 1, d);
						}
					}
				}

				break;
			}
		}
	}

	private void decorateUnderwater(IrisBiome biome, AtomicSliver sliver, double wx, int y, double wz, int rx, int rz, BlockData block)
	{
		if(!getDimension().isDecorate())
		{
			return;
		}

		int j = 0;

		for(IrisBiomeDecorator i : biome.getDecorators())
		{
			if(biome.getInferredType().equals(InferredType.SHORE))
			{
				continue;
			}

			BlockData d = i.getBlockData(getMasterRandom().nextParallelRNG(biome.hashCode() + j++), wx, wz);

			if(d != null)
			{
				int stack = i.getHeight(getMasterRandom().nextParallelRNG(39456 + i.hashCode()), wx, wz);

				if(stack == 1)
				{
					sliver.set(i.getPartOf().equals(DecorationPart.SEA_SURFACE) ? (getFluidHeight() + 1) : (y + 1), d);
				}

				else if(y < getFluidHeight() - stack)
				{
					for(int l = 0; l < stack; l++)
					{
						sliver.set(i.getPartOf().equals(DecorationPart.SEA_SURFACE) ? (getFluidHeight() + 1 + l) : (y + l + 1), d);
					}
				}

				break;
			}
		}
	}

	@Override
	protected void onPostGenerate(RNG random, int x, int z, ChunkData data, BiomeGrid grid, HeightMap height, BiomeMap biomeMap)
	{
		onPreParallaxPostGenerate(random, x, z, data, grid, height, biomeMap);
	}

	protected void onPreParallaxPostGenerate(RNG random, int x, int z, ChunkData data, BiomeGrid grid, HeightMap height, BiomeMap biomeMap)
	{

	}

	protected void onPostParallaxPostGenerate(RNG random, int x, int z, ChunkData data, BiomeGrid grid, HeightMap height, BiomeMap biomeMap)
	{

	}

	protected double getNoiseHeight(int rx, int rz)
	{
		double wx = getZoomed(rx);
		double wz = getZoomed(rz);

		return getBiomeHeight(wx, wz);
	}

	public BiomeResult sampleTrueBiomeBase(int x, int z)
	{
		if(!getDimension().getFocus().equals(""))
		{
			return focus();
		}

		double wx = getModifiedX(x, z);
		double wz = getModifiedZ(x, z);
		IrisRegion region = sampleRegion(x, z);
		int height = (int) Math.round(getTerrainHeight(x, z));
		double sh = region.getShoreHeight(wx, wz);
		IrisBiome current = sampleBiome(x, z).getBiome();

		// Stop shores from spawning on land
		if(current.isShore() && height > sh)
		{
			return glBiome.generateLandData(wx, wz, x, z, region);
		}

		// Stop land & shore from spawning underwater
		if(current.isShore() || current.isLand() && height <= getDimension().getFluidHeight())
		{
			return glBiome.generateSeaData(wx, wz, x, z, region);
		}

		// Stop oceans from spawning on land
		if(current.isSea() && height > getDimension().getFluidHeight())
		{
			return glBiome.generateLandData(wx, wz, x, z, region);
		}

		// Stop land from spawning underwater
		if(height <= getDimension().getFluidHeight())
		{
			return glBiome.generateSeaData(wx, wz, x, z, region);
		}

		// Stop land from spawning where shores go
		if(height <= getDimension().getFluidHeight() + sh)
		{
			return glBiome.generateShoreData(wx, wz, x, z, region);
		}

		return glBiome.generateRegionData(wx, wz, x, z, region);
	}

	public BiomeResult sampleTrueBiome(int x, int z)
	{
		if(!getDimension().getFocus().equals(""))
		{
			return focus();
		}

		double wx = getModifiedX(x, z);
		double wz = getModifiedZ(x, z);
		IrisRegion region = sampleRegion(x, z);
		int height = sampleHeight(x, z);
		double sh = region.getShoreHeight(wx, wz);
		BiomeResult res = sampleTrueBiomeBase(x, z);
		IrisBiome current = res.getBiome();

		// Stop oceans from spawning on the first level of beach
		if(current.isSea() && height > getDimension().getFluidHeight() - sh)
		{
			return glBiome.generateShoreData(wx, wz, x, z, region);
		}

		return res;
	}

	@Override
	protected int onSampleColumnHeight(int cx, int cz, int rx, int rz, int x, int z)
	{
		int fluidHeight = getDimension().getFluidHeight();
		double noise = getNoiseHeight(rx, rz);

		return (int) Math.round(noise) + fluidHeight;
	}

	private boolean touchesSea(int rx, int rz)
	{
		return isFluidAtHeight(rx + 1, rz) || isFluidAtHeight(rx - 1, rz) || isFluidAtHeight(rx, rz - 1) || isFluidAtHeight(rx, rz + 1);
	}

	public boolean isUnderwater(int x, int z)
	{
		return isFluidAtHeight(x, z);
	}

	public boolean isFluidAtHeight(int x, int z)
	{
		return Math.round(getTerrainHeight(x, z)) < getFluidHeight();
	}

	public int getFluidHeight()
	{
		return getDimension().getFluidHeight();
	}

	public double getTerrainHeight(int x, int z)
	{
		return getNoiseHeight(x, z) + getFluidHeight();
	}

	public double getTerrainWaterHeight(int x, int z)
	{
		return Math.max(getTerrainHeight(x, z), getFluidHeight());
	}
}
