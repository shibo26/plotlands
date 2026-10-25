package com.plotlands.plotlands;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.ChunkRegion;
import net.minecraft.world.HeightLimitView;
import net.minecraft.world.Heightmap;
import net.minecraft.world.StructureWorldAccess;
import net.minecraft.world.biome.source.BiomeAccess;
import net.minecraft.world.biome.source.BiomeSource;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.ProtoChunk;
import net.minecraft.world.gen.GenerationStep;
import net.minecraft.world.gen.StructureAccessor;
import net.minecraft.world.gen.chunk.Blender;
import net.minecraft.world.gen.chunk.ChunkGenerator;
import net.minecraft.world.gen.chunk.VerticalBlockSample;
import net.minecraft.world.gen.noise.NoiseConfig;

import java.util.List;
import java.util.concurrent.CompletableFuture;

public class PlotChunkGenerator extends ChunkGenerator {
    public static final MapCodec<PlotChunkGenerator> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
        BiomeSource.CODEC.fieldOf("biome_source").forGetter(g -> g.biomeSource)
    ).apply(i, PlotChunkGenerator::new));

    public static final int PLOT = 128;
    public static final int ROAD = 9;
    public static final int PERIOD = PLOT + ROAD;
    private static final int SURFACE_Y = 63;

    private static final BlockState AIR = Blocks.AIR.getDefaultState();
    private static final BlockState GRASS = Blocks.GRASS_BLOCK.getDefaultState();
    private static final BlockState DIRT = Blocks.DIRT.getDefaultState();
    private static final BlockState STONE = Blocks.STONE.getDefaultState();
    private static final BlockState BEDROCK = Blocks.BEDROCK.getDefaultState();
    private static final BlockState SLAB = Blocks.SANDSTONE_SLAB.getDefaultState();
    private static final BlockState SPRUCE = Blocks.SPRUCE_PLANKS.getDefaultState();
    private static final BlockState BIRCH = Blocks.BIRCH_PLANKS.getDefaultState();

    // Corrected motif pattern to match the reference image
    private static final int[][] MOTIF = {
        // Center
        {0, 0},
        // Cardinal directions (forming +)
        {-1, 0}, {1, 0}, {0, -1}, {0, 1},
        // Diagonal corners
        {-2, -2}, {2, -2}, {-2, 2}, {2, 2},
        // Extended diagonals
        {-3, -1}, {3, -1}, {-1, -3}, {1, -3},
        {-3, 1}, {3, 1}, {-1, 3}, {1, 3},
        // Outer pattern
        {-4, -2}, {4, -2}, {-2, -4}, {2, -4},
        {-4, 2}, {4, 2}, {-2, 4}, {2, 4}
    };

    public PlotChunkGenerator(BiomeSource biomeSource) {
        super(biomeSource);
    }

    @Override
    protected MapCodec<? extends ChunkGenerator> getCodec() { 
        return CODEC; 
    }

    private static int mod(int a, int n) { 
        return ((a % n) + n) % n; 
    }

    private static BlockState roadProfile(int i) {
        // i is 0-8, roads are 2 spruce, 5 birch, 2 spruce
        return (i < 2 || i > 6) ? SPRUCE : BIRCH;
    }

    private static BlockState motif(int i, int j) {
        int di = i - 4, dj = j - 4;
        for (int[] o : MOTIF) {
            if (o[0] == di && o[1] == dj) return SPRUCE;
        }
        return BIRCH;
    }

    private static BlockState surface(int wx, int wz) {
        int u = mod(wx, PERIOD), v = mod(wz, PERIOD);
        boolean uRoad = u >= PLOT, vRoad = v >= PLOT;
        
        if (uRoad && vRoad) {
            // Intersection - 9x9 area
            return motif(u - PLOT, v - PLOT);
        }
        if (uRoad) {
            // Vertical road
            return roadProfile(u - PLOT);
        }
        if (vRoad) {
            // Horizontal road  
            return roadProfile(v - PLOT);
        }
        // Plot border - slabs on the edge
        if (u == 0 || u == PLOT - 1 || v == 0 || v == PLOT - 1) {
            return SLAB;
        }
        // Inside plot - grass
        return GRASS;
    }

    private static void fill(ProtoChunk chunk) {
        ChunkPos cp = chunk.getPos();
        int x0 = cp.getStartX(), z0 = cp.getStartZ();
        
        BlockPos.Mutable pos = new BlockPos.Mutable();
        
        for (int lx = 0; lx < 16; lx++) {
            for (int lz = 0; lz < 16; lz++) {
                int worldX = x0 + lx;
                int worldZ = z0 + lz;
                BlockState topBlock = surface(worldX, worldZ);
                
                // Generate proper depth from bedrock up
                for (int y = chunk.getBottomY(); y <= SURFACE_Y; y++) {
                    BlockState state;
                    
                    if (y == chunk.getBottomY()) {
                        // Bedrock layer
                        state = BEDROCK;
                    } else if (y < SURFACE_Y - 3) {
                        // Deep stone layers
                        state = STONE;
                    } else if (y < SURFACE_Y) {
                        // Dirt layers near surface
                        state = DIRT;
                    } else if (y == SURFACE_Y) {
                        // Surface block
                        state = topBlock;
                    } else {
                        state = AIR;
                    }
                    
                    pos.set(lx, y, lz);
                    chunk.setBlockState(pos, state, false);
                }
            }
        }
    }

    @Override
    public CompletableFuture<Chunk> populateNoise(Blender blender, NoiseConfig noiseConfig,
                                                  StructureAccessor structures, Chunk chunk) {
        if (chunk instanceof ProtoChunk proto) {
            fill(proto);
        }
        return CompletableFuture.completedFuture(chunk);
    }

    @Override
    public void buildSurface(ChunkRegion region, StructureAccessor structures,
                             NoiseConfig noiseConfig, Chunk chunk) { }

    @Override
    public void carve(ChunkRegion region, long seed, NoiseConfig noiseConfig, BiomeAccess biomeAccess,
                      StructureAccessor structures, Chunk chunk, GenerationStep.Carver step) { }

    @Override
    public void populateEntities(ChunkRegion region) { }

    @Override
    public void generateFeatures(StructureWorldAccess region, Chunk chunk, StructureAccessor structures) { }

    @Override
    public int getHeight(int x, int z, Heightmap.Type type, HeightLimitView world, NoiseConfig noiseConfig) {
        return SURFACE_Y + 1;
    }

    @Override
    public VerticalBlockSample getColumnSample(int x, int z, HeightLimitView world, NoiseConfig noiseConfig) {
        BlockState[] column = new BlockState[this.getWorldHeight()];
        for (int y = this.getMinimumY(); y < this.getMinimumY() + this.getWorldHeight(); y++) {
            if (y == this.getMinimumY()) {
                column[y - this.getMinimumY()] = BEDROCK;
            } else if (y < SURFACE_Y - 3) {
                column[y - this.getMinimumY()] = STONE;
            } else if (y < SURFACE_Y) {
                column[y - this.getMinimumY()] = DIRT;
            } else if (y == SURFACE_Y) {
                column[y - this.getMinimumY()] = surface(x, z);
            } else {
                column[y - this.getMinimumY()] = AIR;
            }
        }
        return new VerticalBlockSample(this.getMinimumY(), column);
    }

    @Override
    public void getDebugHudText(List<String> list, NoiseConfig noiseConfig, BlockPos pos) { }

    @Override 
    public int getSpawnHeight(HeightLimitView world) { 
        return SURFACE_Y + 1; 
    }
    
    @Override 
    public int getWorldHeight() { 
        return 384; 
    }
    
    @Override 
    public int getMinimumY() { 
        return -64; 
    }
    
    @Override 
    public int getSeaLevel() { 
        return -63; 
    }
}
