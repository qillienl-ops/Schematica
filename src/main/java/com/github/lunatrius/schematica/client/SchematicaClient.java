package com.github.lunatrius.schematica.client;

import com.github.lunatrius.schematica.SchematicPrinter;
import com.github.lunatrius.schematica.reference.Reference;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.event.RenderGuiEvent;
import net.neoforged.neoforge.client.gui.ConfigurationScreen;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;

/** Client entry point: keybindings, HUD and the config screen extension. */
@Mod(value = Reference.MODID, dist = Dist.CLIENT)
@EventBusSubscriber(modid = Reference.MODID, value = Dist.CLIENT)
public final class SchematicaClient {
    public static final KeyMapping.Category CATEGORY =
            new KeyMapping.Category(Identifier.parse("schematica:general"));

    public static final KeyMapping KEY_LOAD = new KeyMapping(
            "key.schematica.load", InputConstants.KEY_L, CATEGORY);
    public static final KeyMapping KEY_PRINTER = new KeyMapping(
            "key.schematica.printer", InputConstants.KEY_P, CATEGORY);
    public static final KeyMapping KEY_LIST = new KeyMapping(
            "key.schematica.list", InputConstants.KEY_K, CATEGORY);

    public SchematicaClient(ModContainer container) {
        container.registerExtensionPoint(IConfigScreenFactory.class, ConfigurationScreen::new);
    }

    @SubscribeEvent
    static void registerKeys(RegisterKeyMappingsEvent event) {
        event.registerCategory(CATEGORY);
        event.register(KEY_LOAD);
        event.register(KEY_PRINTER);
        event.register(KEY_LIST);
    }

    @SubscribeEvent
    static void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.gui.screen() != null) {
            return;
        }

        if (KEY_LOAD.consumeClick()) {
            mc.gui.setScreen(new SchematicLoadScreen());
        } else if (KEY_PRINTER.consumeClick()) {
            mc.player.connection.sendCommand("schematica printer stop");
        } else if (KEY_LIST.consumeClick()) {
            mc.player.connection.sendCommand("schematica list");
        }
    }

    @SubscribeEvent
    static void onRenderGui(RenderGuiEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            return;
        }
        var graphics = event.getGuiGraphics();
        graphics.text(mc.font, "Schematica: " + SchematicPrinter.status(), 6, 6, 0xFFFFFF, true);
    }
}
