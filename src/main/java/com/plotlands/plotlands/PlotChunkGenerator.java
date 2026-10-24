package com.plotlands.plotlands;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.server.level.ServerLevelAccessor;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LevelHeightAccess;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.WorldGenRegion;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeManager;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.chunk.ProtoChunk;
import net.minecraft.world.level.levelgen.Blender;
import net.minecraft.world.level.levelgen.GenerationStep;
import net.minecraft.world.level.levelgen.GenerationSettings;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.NoiseColumn;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.carving.CarvingMask;
import net.minecraft.world.level.levelgen.climate.Climate;
import net.minecraft.world.level.levelgen.structure.StructureAccessor;
import java.util.List;

public class PlotChunkGenerator extends net.minecraft.world.level.levelgen.ChunkGenerator {

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
    protected MapCodec<? extends net.minecraft.world.level.levelgen.ChunkGenerator> codec() { return CODEC; }

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

    private static void fill(ProtoChunk chunk, Holder<Biome> biome) {
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
        for (int sy = chunk.getMinSection(); sy < chunk.getMaxSection(); sy++) {
            LevelChunkSection sec = chunk.getOrCreateSection(chunk.getSectionIndex(sy * 16));
            for (int x = 0; x < 4; x++) for (int y = 0; y < 4; y++) for (int z = 0; z < 4; z++)
                sec.biomes.set(x, y, z, biome);
        }
    }

    @Override
    public void applyNoise(ServerLevelAccessor level, StructureAccessor structures, Blender blender,
                           RandomState randomState, Climate.Sampler sampler, ChunkAccess chunk) {
        if (chunk instanceof ProtoChunk proto) {
            Holder<Biome> biome = this.biomeSource.getNoiseBiome(
                    chunk.getPos().getMinBlockX() >> 2, 0, chunk.getPos().getMinBlockZ() >> 2, randomState);
            fill(proto, biome);
        }
    }

    @Override public void buildSurface(WorldGenRegion region, StructureAccessor structures,
                                       RandomState randomState, ChunkAccess chunk) { }

    @Override public void applyCarvers(BiomeManager biomeManager, StructureAccessor structures,
                                       RandomState randomState, Climate.Sampler sampler, ChunkAccess chunk,
                                       GenerationStep.Carving step, CarvingMask mask) { }

    @Override public void applyBiomeDecoration(WorldGenLevel level, ChunkAccess chunk,
                                               StructureAccessor structures) { }

    @Override public GenerationSettings getBiomeGenerationSettings(Holder<Biome> biome) {
        return biome.value().getGenerationSettings();
    }

    @Override public int getBaseHeight(int x, int z, Heightmap.Types type,
                                       LevelHeightAccess level, RandomState randomState) { return SURFACE_Y + 1; }

    @Override
    public NoiseColumn getBaseColumn(int x, int z, LevelHeightAccess level, RandomState randomState) {
        BlockState[] column = new BlockState[this.getGenDepth()];
        for (int y = this.getMinY(); y < this.getMinY() + this.getGenDepth(); y++) {
            column[y - this.getMinY()] =
                    y == -64 ? BEDROCK : (y <= -39 ? DIRT : (y == SURFACE_Y ? surface(x, z) : AIR));
        }
        return new NoiseColumn(this.getMinY(), column);
    }

    @Override public void addDebugScreenInfo(List<String> list, RandomState randomState, BlockPos pos) { }
    @Override public int getSpawnHeight(LevelHeightAccess level) { return SURFACE_Y + 1; }
    @Override public int getSeaLevel() { return -63; }
    @Override public int getMinY() { return -64; }
    @Override public int getGenDepth() { return 384; }
}
