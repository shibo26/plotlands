package com.plotlands.plotlands;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.server.world.ServerWorldAccess;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.HeightLimitView;
import net.minecraft.world.Heightmap;
import net.minecraft.world.StructureAccessor;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.biome.source.BiomeAccess;
import net.minecraft.world.biome.source.BiomeSource;
import net.minecraft.world.chunk.ChunkAccess;
import net.minecraft.world.chunk.ChunkSection;
import net.minecraft.world.chunk.ProtoChunk;
import net.minecraft.world.gen.GenerationSettings;
import net.minecraft.world.gen.GenerationStep;
import net.minecraft.world.gen.NoiseConfig;
import net.minecraft.world.gen.WorldGenRegion;
import net.minecraft.world.gen.carver.CarvingMask;
import net.minecraft.world.gen.chunk.Blender;
import net.minecraft.world.gen.chunk.ChunkGenerator;
import net.minecraft.world.gen.chunk.climate.Climate;
import net.minecraft.world.levelgen.NoiseColumn;
import java.util.List;

public class PlotChunkGenerator extends ChunkGenerator {

    public static final MapCodec<PlotChunkGenerator> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            BiomeSource.CODEC.fieldOf("biome_source").forGetter(g -> g.biomeSource)
    ).apply(i, PlotChunkGenerator::new));

    public static final int PLOT = 128;
    public static final int ROAD = 9;
    public static final int PERIOD = PLOT + ROAD;
    private static final int SURFACE_Y = 63;

    private static final BlockState AIR     = Blocks.AIR.defaultBlockState();
    private static final BlockState GRASS   = Blocks.GRASS_BLOCK.defaultBlockState();
    private static final BlockState DIRT    = Blocks.DIRT.defaultBlockState();
    private static final BlockState BEDROCK = Blocks.BEDROCK.defaultBlockState();
    private static final BlockState SLAB    = Blocks.SANDSTONE_SLAB.defaultBlockState();
    private static final BlockState SPRUCE  = Blocks.SPRUCE_PLANKS.defaultBlockState();
    private static final BlockState BIRCH   = Blocks.BIRCH_PLANKS.defaultBlockState();
    private static final GenerationSettings NO_FEATURES = new GenerationSettings.Builder().build();

    private static final int[][] MOTIF = {
        {-2,-4},{-1,-4},{1,-4},{2,-4},
        {-1,-3},{1,-3},
        {-2,-2},{0,-2},{2,-2},
        {-3,-1},{3,-1},
        {-4,0},{-2,0},{0,0},{2,0},{4,0},
        {-3,1},{3,1},
        {-2,2},{0,2},{2,2},
        {-1,3},{1,3},
        {-2,4},{-1,4},{1,4},{2,4}
    };

    public PlotChunkGenerator(BiomeSource biomeSource) {
        super(biomeSource);
    }

    @Override
    protected MapCodec<? extends ChunkGenerator> codec() { return CODEC; }

    private static int mod(int a, int n) { return ((a % n) + n) % n; }

    private static BlockState roadProfile(int i) {
        return (i < 2 || i > 6) ? SPRUCE : BIRCH;
    }

    private static BlockState motif(int i, int j) {
        int di = i - 4, dj = j - 4;
        for (int[] o : MOTIF) if (o[0] == di && o[1] == dj) return SPRUCE;
        return BIRCH;
    }

    private static BlockState surface(int wx, int wz) {
        int u = mod(wx, PERIOD), v = mod(wz, PERIOD);
        boolean uRoad = u >= PLOT, vRoad = v >= PLOT;
        if (uRoad && vRoad) return motif(u - PLOT, v - PLOT);
        if (uRoad)          return roadProfile(u - PLOT);
        if (vRoad)          return roadProfile(v - PLOT);
        if (u == 0 || u == PLOT - 1 || v == 0 || v == PLOT - 1) return SLAB;
        return GRASS;
    }

    private static void fill(ProtoChunk chunk, RegistryEntry<Biome> biome) {
        ChunkPos cp = chunk.getPos();
        int x0 = cp.getMinBlockX(), z0 = cp.getMinBlockZ();
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int lx = 0; lx < 16; lx++) {
            for (int lz = 0; lz < 16; lz++) {
                BlockState top = surface(x0 + lx, z0 + lz);
                chunk.setBlockState(pos.set(lx, -64, lz), BEDROCK, false);
                for (int y = -63; y <= -39; y++) chunk.setBlockState(pos.set(lx, y, lz), DIRT, false);
                chunk.setBlockState(pos.set(lx, SURFACE_Y, lz), top, false);
            }
        }
        for (int sy = -4; sy <= 19; sy++) {
            ChunkSection sec = chunk.getOrCreateSection(sy);
            for (int x = 0; x < 4; x++) for (int y = 0; y < 4; y++) for (int z = 0; z < 4; z++)
                sec.getBiomeContainer().set(x, y, z, biome);
        }
    }

    @Override
    public void populateNoise(ServerWorldAccess region, StructureAccessor structures, Blender blender,
                              NoiseConfig noiseConfig, Climate.Sampler sampler, ChunkAccess chunk) {
        if (chunk instanceof ProtoChunk proto) {
            RegistryEntry<Biome> biome = this.biomeSource.getBiome(
                    chunk.getPos().getMinBlockX() >> 2, 0, chunk.getPos().getMinBlockZ() >> 2, noiseConfig);
            fill(proto, biome);
        }
    }

    @Override public void buildSurface(WorldGenRegion region, StructureAccessor structures,
                                       NoiseConfig noiseConfig, ChunkAccess chunk) { }

    @Override public void carve(BiomeAccess biomeAccess, StructureAccessor structures, NoiseConfig noiseConfig,
                                Climate.Sampler sampler, ChunkAccess chunk,
                                GenerationStep.Carving step, CarvingMask mask) { }

    @Override public GenerationSettings getGenerationSettings(RegistryEntry<Biome> biome) { return NO_FEATURES; }

    @Override public int getHeight(int x, int z, Heightmap.Type type,
                                   HeightLimitView world, NoiseConfig noiseConfig) { return SURFACE_Y + 1; }

    @Override
    public NoiseColumn getColumn(int x, int z, HeightLimitView world, NoiseConfig noiseConfig) {
        BlockState[] column = new BlockState[this.getGenDepth()];
        for (int y = this.getMinY(); y < this.getMinY() + this.getGenDepth(); y++) {
            column[y - this.getMinY()] =
                    y == -64 ? BEDROCK : (y <= -39 ? DIRT : (y == SURFACE_Y ? surface(x, z) : AIR));
        }
        return new NoiseColumn(this.getMinY(), column);
    }

    @Override public void addDebugInfo(List<String> list, NoiseConfig noiseConfig, BlockPos pos) { }
    @Override public int getSpawnHeight(HeightLimitView world) { return SURFACE_Y + 1; }
    @Override public int getSeaLevel() { return -63; }
    @Override public int getMinY() { return -64; }
    @Override public int getGenDepth() { return 384; }
}
