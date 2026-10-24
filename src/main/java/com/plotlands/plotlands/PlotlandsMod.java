package com.plotlands.plotlands;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.fabricmc.api.ModInitializer;

public class PlotlandsMod implements ModInitializer {
    public static final String ID = "plotlands";

    @Override
    public void onInitialize() {
        Registry.register(Registries.CHUNK_GENERATOR,
                new ResourceLocation(ID, "plots"), PlotChunkGenerator.CODEC);
    }
}
