package com.plotlands.plotlands;

import net.fabricmc.api.ModInitializer;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.util.Identifier;

public class PlotlandsMod implements ModInitializer {
    public static final String ID = "plotlands";

    @Override
    public void onInitialize() {
        // FIX: Use Identifier.of() for Minecraft 1.21+
        Registry.register(Registries.CHUNK_GENERATOR, Identifier.of(ID, "plots"), PlotChunkGenerator.CODEC);
    }
}
