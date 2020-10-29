package com.volmit.iris.v2.generator;

import com.volmit.iris.v2.DataProvider;
import com.volmit.iris.v2.scaffold.engine.Engine;
import com.volmit.iris.util.B;
import org.bukkit.Material;
import org.bukkit.block.Biome;
import org.bukkit.block.data.BlockData;

import com.volmit.iris.Iris;
import com.volmit.iris.v2.scaffold.stream.ProceduralStream;
import com.volmit.iris.v2.scaffold.stream.interpolation.Interpolated;
import com.volmit.iris.manager.IrisDataManager;
import com.volmit.iris.noise.CNG;
import com.volmit.iris.object.DecorationPart;
import com.volmit.iris.object.InferredType;
import com.volmit.iris.object.IrisBiome;
import com.volmit.iris.object.IrisDecorator;
import com.volmit.iris.object.IrisBiomePaletteLayer;
import com.volmit.iris.object.IrisGenerator;
import com.volmit.iris.object.IrisRegion;
import com.volmit.iris.util.KList;
import com.volmit.iris.util.M;
import com.volmit.iris.util.RNG;

import lombok.Data;

@Data
public class IrisComplex implements DataProvider
{
	private RNG rng;
	private double fluidHeight;
	private IrisDataManager data;
	private KList<IrisGenerator> generators;
	private static final BlockData AIR = Material.AIR.createBlockData();
	private ProceduralStream<IrisRegion> regionStream;
	private ProceduralStream<InferredType> bridgeStream;
	private ProceduralStream<IrisBiome> landBiomeStream;
	private ProceduralStream<IrisBiome> caveBiomeStream;
	private ProceduralStream<IrisBiome> seaBiomeStream;
	private ProceduralStream<IrisBiome> shoreBiomeStream;
	private ProceduralStream<IrisBiome> baseBiomeStream;
	private ProceduralStream<IrisBiome> trueBiomeStream;
	private ProceduralStream<Biome> trueBiomeDerivativeStream;
	private ProceduralStream<Double> heightStream;
	private ProceduralStream<Double> maxHeightStream;
	private ProceduralStream<Double> overlayStream;
	private ProceduralStream<Double> heightFluidStream;
	private ProceduralStream<Double> slopeStream;
	private ProceduralStream<RNG> rngStream;
	private ProceduralStream<RNG> chunkRngStream;
	private ProceduralStream<IrisDecorator> terrainSurfaceDecoration;
	private ProceduralStream<IrisDecorator> terrainCeilingDecoration;
	private ProceduralStream<IrisDecorator> seaSurfaceDecoration;
	private ProceduralStream<IrisDecorator> shoreSurfaceDecoration;
	private ProceduralStream<BlockData> terrainStream;
	private ProceduralStream<BlockData> rockStream;
	private ProceduralStream<BlockData> fluidStream;
	private ProceduralStream<BlockData> glassStream;

	public ProceduralStream<IrisBiome> getBiomeStream(InferredType type)
	{
		switch(type)
		{
			case CAVE:
				return caveBiomeStream;
			case DEFER:
				break;
			case LAKE:
				break;
			case LAND:
				return landBiomeStream;
			case RIVER:
				break;
			case SEA:
				return seaBiomeStream;
			case SHORE:
				return shoreBiomeStream;
			default:
				break;
		}

		return null;
	}

	public IrisComplex(Engine engine)
	{
		int cacheSize = 8192;
		BlockData glass = B.getBlockData("GLASS");
		this.rng = new RNG(engine.getWorld().getSeed());
		this.data = data;
		fluidHeight = engine.getDimension().getFluidHeight();
		generators = new KList<>();
		RNG rng = new RNG(engine.getWorld().getSeed());
		glassStream = ProceduralStream.of((x,y,z) -> glass, Interpolated.BLOCK_DATA);
		//@builder
		engine.getDimension().getRegions().forEach((i) -> data.getRegionLoader().load(i)
			.getAllBiomes(this).forEach((b) -> b
				.getGenerators()
				.forEach((c) -> registerGenerator(c.getCachedGenerator(this)))));
		overlayStream = ProceduralStream.ofDouble((x, z) -> 0D);
		engine.getDimension().getOverlayNoise().forEach((i) -> overlayStream.add((x, z) -> i.get(rng, x, z)));
		rngStream = ProceduralStream.of((x, z) -> new RNG(((x.longValue()) << 32) | (z.longValue() & 0xffffffffL))
				.nextParallelRNG(engine.getWorld().getSeed()), Interpolated.RNG)
			.cache2D(64);
		chunkRngStream = rngStream.blockToChunkCoords();
		rockStream = engine.getDimension().getRockPalette().getLayerGenerator(rng.nextRNG(), data).stream()
			.select(engine.getDimension().getRockPalette().getBlockData(data));
		fluidStream = engine.getDimension().getFluidPalette().getLayerGenerator(rng.nextRNG(), data).stream()
			.select(engine.getDimension().getFluidPalette().getBlockData(data));
		regionStream = engine.getDimension().getRegionStyle().create(rng.nextRNG()).stream()
			.zoom(engine.getDimension().getRegionZoom())
			.selectRarity(engine.getDimension().getRegions())
			.convertCached((s) -> data.getRegionLoader().load(s))
			.cache2D(cacheSize);
		caveBiomeStream = regionStream.convertCached((r)
			-> engine.getDimension().getCaveBiomeStyle().create(rng.nextRNG()).stream()
				.zoom(r.getCaveBiomeZoom())
				.selectRarity(r.getCaveBiomes())
				.convertCached((s) -> data.getBiomeLoader().load(s)
						.setInferredType(InferredType.CAVE))
			).convertAware2D(ProceduralStream::get)
				.cache2D(cacheSize);
		landBiomeStream = regionStream.convertCached((r)
			-> engine.getDimension().getLandBiomeStyle().create(rng.nextRNG()).stream()
				.zoom(r.getLandBiomeZoom())
				.selectRarity(r.getLandBiomes())
				.convertCached((s) -> data.getBiomeLoader().load(s)
						.setInferredType(InferredType.LAND))
			).convertAware2D(ProceduralStream::get)
				.cache2D(cacheSize);
		seaBiomeStream = regionStream.convertCached((r)
			-> engine.getDimension().getSeaBiomeStyle().create(rng.nextRNG()).stream()
				.zoom(r.getSeaBiomeZoom())
				.selectRarity(r.getSeaBiomes())
				.convertCached((s) -> data.getBiomeLoader().load(s)
						.setInferredType(InferredType.SEA))
			).convertAware2D(ProceduralStream::get)
				.cache2D(cacheSize);
		shoreBiomeStream = regionStream.convertCached((r)
			-> engine.getDimension().getShoreBiomeStyle().create(rng.nextRNG()).stream()
				.zoom(r.getShoreBiomeZoom())
				.selectRarity(r.getShoreBiomes())
				.convertCached((s) -> data.getBiomeLoader().load(s)
						.setInferredType(InferredType.SHORE))
			).convertAware2D(ProceduralStream::get)
				.cache2D(cacheSize);
		bridgeStream = engine.getDimension().getContinentalStyle().create(rng.nextRNG()).stream()
			.convert((v) -> v >= engine.getDimension().getLandChance() ? InferredType.SEA : InferredType.LAND);
		baseBiomeStream = bridgeStream.convertAware2D((t, x, z) -> t.equals(InferredType.SEA)
			? seaBiomeStream.get(x, z) : landBiomeStream.get(x, z))
			.convertAware2D(this::implode).cache2D(cacheSize);
		heightStream = baseBiomeStream.convertAware2D((b, x, z) -> getHeight(b, x, z, engine.getWorld().getSeed()))
				.roundDouble()
			.cache2D(cacheSize);
		slopeStream = heightStream.slope();
		trueBiomeStream = heightStream
				.convertAware2D((h, x, z) ->
					fixBiomeType(h, baseBiomeStream.get(x, z),
							regionStream.get(x, z), x, z, fluidHeight))
				.cache2D(cacheSize);
		trueBiomeDerivativeStream = trueBiomeStream.convert(IrisBiome::getDerivative);
		heightFluidStream = heightStream.max(fluidHeight);
		maxHeightStream = ProceduralStream.ofDouble((x, z) -> 255D);
		terrainSurfaceDecoration = trueBiomeStream
			.convertAware2D((b, xx,zz) -> decorateFor(b, xx, zz, DecorationPart.NONE));
		terrainCeilingDecoration = trueBiomeStream
			.convertAware2D((b, xx,zz) -> decorateFor(b, xx, zz, DecorationPart.CEILING));
		shoreSurfaceDecoration = trueBiomeStream
			.convertAware2D((b, xx,zz) -> decorateFor(b, xx, zz, DecorationPart.SHORE_LINE));
		seaSurfaceDecoration = trueBiomeStream
			.convertAware2D((b, xx,zz) -> decorateFor(b, xx, zz, DecorationPart.SEA_SURFACE));
		terrainStream = ProceduralStream.of(this::fillTerrain, Interpolated.BLOCK_DATA);
		//@done
	}

	private BlockData fillTerrain(Double x, Double y, Double z)
	{
		double height = heightStream.get(x, z);
		IrisBiome biome = trueBiomeStream.get(x, z);
		int depth = (int) (Math.round(height) - y);
		int atDepth = 0;

		if(y > height && y <= fluidHeight)
		{
			return fluidStream.get(x, y, z);
		}

		if(depth < -1)
		{
			return AIR;
		}

		for(IrisBiomePaletteLayer i : biome.getLayers())
		{
			int th = i.getHeightGenerator(rng, data).fit(i.getMinHeight(), i.getMaxHeight(), x, z);

			if(atDepth + th >= depth)
			{
				return i.get(rng, x, y, z, data);
			}

			atDepth += th;
		}

		return rockStream.get(x, y, z);
	}

	private IrisDecorator decorateFor(IrisBiome b, double x, double z, DecorationPart part)
	{
		RNG rngc = chunkRngStream.get(x, z);

		for(IrisDecorator i : b.getDecorators())
		{
			if(!i.getPartOf().equals(part))
			{
				continue;
			}

			BlockData block = i.getBlockData(b, rngc, x, z, data);

			if(block != null)
			{
				return i;
			}
		}

		return null;
	}

	private IrisBiome implode(IrisBiome b, Double x, Double z)
	{
		if(b.getChildren().isEmpty())
		{
			return b;
		}

		return implode(b, x, z, 3);
	}

	private IrisBiome implode(IrisBiome b, Double x, Double z, int max)
	{
		if(max < 0)
		{
			return b;
		}

		if(b.getChildren().isEmpty())
		{
			return b;
		}

		CNG childCell = b.getChildrenGenerator(rng, 123, b.getChildShrinkFactor());
		KList<IrisBiome> chx = b.getRealChildren(this).copy();
		chx.add(b);
		IrisBiome biome = childCell.fitRarity(chx, x, z);
		biome.setInferredType(b.getInferredType());
		return implode(biome, x, z, max - 1);
	}

	private IrisBiome fixBiomeType(Double height, IrisBiome biome, IrisRegion region, Double x, Double z, double fluidHeight)
	{
		double sh = region.getShoreHeight(x, z);

		if(height >= fluidHeight-1 && height <= fluidHeight + sh && !biome.isShore())
		{
			return shoreBiomeStream.get(x, z);
		}

		if(height > fluidHeight + sh && !biome.isLand())
		{
			return landBiomeStream.get(x, z);
		}

		if(height < fluidHeight && !biome.isAquatic())
		{
			return seaBiomeStream.get(x, z);
		}

		if(height == fluidHeight && !biome.isShore())
		{
			return shoreBiomeStream.get(x, z);
		}

		return biome;
	}

	private double getHeight(IrisBiome b, double x, double z, long seed)
	{
		double h = 0;

		for(IrisGenerator gen : generators)
		{
			double hi = gen.getInterpolator().interpolate(x, z, (xx, zz) ->
			{
				try
				{
					IrisBiome bx = baseBiomeStream.get(xx, zz);

					return bx.getGenLinkMax(gen.getLoadKey());
				}

				catch(Throwable e)
				{
					Iris.warn("Failed to sample hi biome at " + xx + " " + zz + " using the generator " + gen.getLoadKey());
				}

				return 0;
			});

			double lo = gen.getInterpolator().interpolate(x, z, (xx, zz) ->
			{
				try
				{
					IrisBiome bx = baseBiomeStream.get(xx, zz);

					return bx.getGenLinkMin(gen.getLoadKey());
				}

				catch(Throwable e)
				{
					Iris.warn("Failed to sample lo biome at " + xx + " " + zz + " using the generator " + gen.getLoadKey());
				}

				return 0;
			});

			h += M.lerp(lo, hi, gen.getHeight(x, z, seed + 239945));
		}

		return h + fluidHeight + overlayStream.get(x,z);
	}

	private void registerGenerator(IrisGenerator cachedGenerator)
	{
		for(IrisGenerator i : generators)
		{
			if(i.getLoadKey().equals(cachedGenerator.getLoadKey()))
			{
				return;
			}
		}

		generators.add(cachedGenerator);
	}
}