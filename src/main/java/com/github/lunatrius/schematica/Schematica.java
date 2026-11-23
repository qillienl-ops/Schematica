package com.github.lunatrius.schematica;

import com.github.lunatrius.schematica.reference.Reference;
import com.mojang.logging.LogUtils;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.common.NeoForge;
import org.slf4j.Logger;

@Mod(Reference.MODID)
public class Schematica {
    public static final Logger LOGGER = LogUtils.getLogger();

    public Schematica(IEventBus modEventBus, ModContainer container) {
        container.registerConfig(ModConfig.Type.LOCAL, Config.SPEC);
        modEventBus.addListener(this::commonSetup);
        NeoForge.EVENT_BUS.addListener(SchematicaCommands::register);
        NeoForge.EVENT_BUS.addListener(SchematicPrinter::onServerTick);
    }

    private void commonSetup(FMLCommonSetupEvent event) {
        LOGGER.info("Schematica {} loaded for Minecraft 26.3 (NeoForge)", Reference.VERSION);
    }
}
