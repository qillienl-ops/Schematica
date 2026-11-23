package com.github.lunatrius.schematica.client;

import com.github.lunatrius.schematica.SchematicPrinter;
import com.github.lunatrius.schematica.client.gui.SchematicControlScreen;
import com.github.lunatrius.schematica.client.gui.SchematicLoadScreen;
import com.github.lunatrius.schematica.client.gui.SchematicSaveScreen;
import com.github.lunatrius.schematica.client.printer.ClientPrinter;
import com.github.lunatrius.schematica.reference.Reference;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.BlockHitResult;
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
import net.neoforged.neoforge.client.settings.KeyConflictContext;
import net.neoforged.neoforge.client.settings.KeyModifier;

/** Client entry point: the ten legacy keybinds, GUIs, HUD and config screen. */
@Mod(value = Reference.MODID, dist = Dist.CLIENT)
@EventBusSubscriber(modid = Reference.MODID, value = Dist.CLIENT)
public final class SchematicaClient {
    public static final KeyMapping.Category CATEGORY =
            new KeyMapping.Category(Identifier.parse("schematica:general"));

    // Legacy 1.12.2 defaults: numpad / (284), * (285) and - (269).
    // GLFW 84/85/86 are T/U/V, which is why numpad did nothing in earlier builds.
    public static final KeyMapping KEY_LOAD = new KeyMapping(
            "key.schematica.load", KeyConflictContext.UNIVERSAL,
            InputConstants.Type.KEYBOARD.getOrCreate(284), CATEGORY);
    public static final KeyMapping KEY_SAVE = new KeyMapping(
            "key.schematica.save", KeyConflictContext.UNIVERSAL,
            InputConstants.Type.KEYBOARD.getOrCreate(285), CATEGORY);
    public static final KeyMapping KEY_CONTROL = new KeyMapping(
            "key.schematica.control", KeyConflictContext.UNIVERSAL,
            InputConstants.Type.KEYBOARD.getOrCreate(269), CATEGORY);
    public static final KeyMapping KEY_LAYER_INC = unbound("key.schematica.layerInc");
    public static final KeyMapping KEY_LAYER_DEC = unbound("key.schematica.layerDec");
    public static final KeyMapping KEY_LAYER_TOGGLE = unbound("key.schematica.layerToggle");
    public static final KeyMapping KEY_RENDER_TOGGLE = unbound("key.schematica.renderToggle");
    public static final KeyMapping KEY_PRINTER_TOGGLE = unbound("key.schematica.printerToggle");
    public static final KeyMapping KEY_MOVE_HERE = unbound("key.schematica.moveHere");
    public static final KeyMapping KEY_PICK_BLOCK = new KeyMapping(
            "key.schematica.pickBlock", KeyConflictContext.IN_GAME, KeyModifier.SHIFT,
            InputConstants.Type.MOUSE.getOrCreate(InputConstants.MOUSE_BUTTON_MIDDLE), CATEGORY);

    // Modern convenience shortcuts kept from the first port.
    public static final KeyMapping KEY_LOAD_ALT = new KeyMapping(
            "key.schematica.loadAlt", InputConstants.KEY_L, CATEGORY);
    public static final KeyMapping KEY_PRINTER_STOP = new KeyMapping(
            "key.schematica.printerStop", InputConstants.KEY_P, CATEGORY);
    public static final KeyMapping KEY_LIST = new KeyMapping(
            "key.schematica.list", InputConstants.KEY_K, CATEGORY);

    public SchematicaClient(IEventBus modEventBus, ModContainer container) {
        container.registerExtensionPoint(IConfigScreenFactory.class, ConfigurationScreen::new);
        modEventBus.addListener(com.github.lunatrius.schematica.client.render.SchematicRenderer::registerDebug);
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(
                com.github.lunatrius.schematica.client.render.SchematicRenderer::submitGhostBlocks);
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(
                SchematicaClientCommands::register);
    }

    private static KeyMapping unbound(String name) {
        return new KeyMapping(name, 0, CATEGORY);
    }

    @SubscribeEvent
    static void registerKeys(RegisterKeyMappingsEvent event) {
        event.registerCategory(CATEGORY);
        event.register(KEY_LOAD);
        event.register(KEY_SAVE);
        event.register(KEY_CONTROL);
        event.register(KEY_LAYER_INC);
        event.register(KEY_LAYER_DEC);
        event.register(KEY_LAYER_TOGGLE);
        event.register(KEY_RENDER_TOGGLE);
        event.register(KEY_PRINTER_TOGGLE);
        event.register(KEY_MOVE_HERE);
        event.register(KEY_PICK_BLOCK);
        event.register(KEY_LOAD_ALT);
        event.register(KEY_PRINTER_STOP);
        event.register(KEY_LIST);
    }

    @SubscribeEvent
    static void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) {
            return;
        }
        ClientPrinter.tick(mc);
        if (mc.gui.screen() != null) {
            return;
        }
        SchematicState state = SchematicState.instance();

        if (KEY_LOAD.consumeClick() || KEY_LOAD_ALT.consumeClick()) {
            mc.gui.setScreen(new SchematicLoadScreen());
            return;
        }
        if (KEY_SAVE.consumeClick()) {
            mc.gui.setScreen(new SchematicSaveScreen());
            return;
        }
        if (KEY_CONTROL.consumeClick()) {
            mc.gui.setScreen(new SchematicControlScreen());
            return;
        }
        if (KEY_LAYER_INC.consumeClick()) {
            state.setRenderingLayer(state.renderingLayer() + 1);
        } else if (KEY_LAYER_DEC.consumeClick()) {
            state.setRenderingLayer(state.renderingLayer() - 1);
        } else if (KEY_LAYER_TOGGLE.consumeClick()) {
            state.setLayerMode(state.layerMode().next());
        } else if (KEY_RENDER_TOGGLE.consumeClick()) {
            state.setHidden(!state.isHidden());
        } else if (KEY_PRINTER_TOGGLE.consumeClick()) {
            ClientPrinter.toggle();
        } else if (KEY_MOVE_HERE.consumeClick()) {
            state.moveHere(mc.player.blockPosition());
        } else if (KEY_PICK_BLOCK.consumeClick()) {
            pickBlock(mc);
        } else if (KEY_PRINTER_STOP.consumeClick()) {
            ClientPrinter.stop();
            mc.player.connection.sendCommand("schematica printer stop");
        } else if (KEY_LIST.consumeClick()) {
            mc.player.connection.sendCommand("schematica list");
        }
    }

    private static void pickBlock(Minecraft mc) {
        SchematicState state = SchematicState.instance();
        if (!state.isLoaded()) {
            return;
        }
        // Legacy raytraced the SchematicWorld, not the real world, so Shift+middle
        // click on a ghost (including one floating in mid-air) picks its block.
        BlockPos pos = rayTraceSchematic(mc, state);
        if (pos == null) {
            return;
        }
        ItemStack stack = com.github.lunatrius.schematica.client.printer.ClientPrinter
                .requiredItem(state.renderStateAt(pos), pos, mc.player);
        if (stack.isEmpty()) {
            return;
        }
        mc.player.getInventory().setItem(mc.player.getInventory().getSelectedSlot(), stack);
    }

    private static BlockPos rayTraceSchematic(Minecraft mc, SchematicState state) {
        net.minecraft.world.phys.Vec3 eye = mc.player.getEyePosition(1.0F);
        net.minecraft.world.phys.Vec3 look = mc.player.getViewVector(1.0F);
        double reach = mc.player.blockInteractionRange();
        BlockPos last = null;
        for (double d = 0; d <= reach; d += 0.1) {
            BlockPos pos = new BlockPos((int) Math.floor(eye.x + look.x * d),
                    (int) Math.floor(eye.y + look.y * d),
                    (int) Math.floor(eye.z + look.z * d));
            if (!pos.equals(last)) {
                last = pos;
                if (!state.isExtraAir(state.renderStateAt(pos))) {
                    return pos;
                }
            }
        }
        return null;
    }

    @SubscribeEvent
    static void onRenderGui(RenderGuiEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) {
            return;
        }
        var graphics = event.getGuiGraphics();
        String clientStatus = ClientPrinter.isActive() ? "Printer active" : "Printer idle";
        int y = 6;
        graphics.text(mc.font, "Schematica: " + clientStatus + " | " + SchematicPrinter.status(),
                6, y, 0xFFFFFF, true);
        y += 10;

        SchematicState state = SchematicState.instance();
        if (!state.isLoaded()) {
            return;
        }
        var schematic = state.schematic();
        graphics.text(mc.font, "Schematic: " + state.name() + " ("
                + schematic.getWidth() + "x" + schematic.getHeight() + "x" + schematic.getLength()
                + ") @ " + state.position().toShortString() + " [" + state.layerMode().label() + "]",
                6, y, 0xFFFFFF, true);
        y += 10;

        // Legacy OverlayHandler showed the block state under the crosshair,
        // including all properties, so you could verify what the printer sees.
        BlockPos look = rayTraceSchematic(mc, state);
        if (look != null) {
            net.minecraft.world.level.block.state.BlockState blockState = state.renderStateAt(look);
            StringBuilder text = new StringBuilder("Looking at: ")
                    .append(net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(blockState.getBlock()))
                    .append(" @ ").append(look);
            for (var value : blockState.getValues().toList()) {
                text.append(' ').append(value.property().getName()).append('=')
                        .append(value.value());
            }
            graphics.text(mc.font, text.toString(), 6, y, 0xFFFFFF, true);
        }
    }
}
