package com.github.lunatrius.schematica.client.render;

import com.github.lunatrius.schematica.Config;
import com.github.lunatrius.schematica.client.SchematicState;
import com.github.lunatrius.schematica.reference.Reference;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.QuadInstance;
import net.minecraft.client.Minecraft;
import net.minecraft.client.color.block.BlockTintSource;
import net.minecraft.client.gui.components.debug.DebugScreenDisplayer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.block.dispatch.BlockStateModel;
import net.minecraft.client.renderer.block.dispatch.BlockStateModelPart;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gizmos.GizmoStyle;
import net.minecraft.gizmos.Gizmos;
import net.minecraft.resources.Identifier;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterDebugEntriesEvent;
import net.neoforged.neoforge.client.event.SubmitCustomGeometryEvent;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Ghost blocks, mismatch highlights and selection guides (legacy renderer behavior). */
@EventBusSubscriber(modid = Reference.MODID, value = Dist.CLIENT)
public final class SchematicRenderer {
    private static final int POINT_A_COLOR = 0x3FBF0000;
    private static final int POINT_B_COLOR = 0x3F0000BF;
    private static final int SELECTION_COLOR = 0x7F00BF00;
    private static final int BOUNDS_COLOR = 0x7FBF00BF;

    // These are the original 1.12.2 RGB colors. Gizmos use ARGB, so the
    // alpha channel has to be added explicitly (0x00BFFF used to be invisible).
    private static final int GHOST_COLOR = 0xFFFFFF;
    private static final int EXTRA_BLOCK_COLOR = 0xBF00BF;
    private static final int WRONG_BLOCK_COLOR = 0xFF0000;
    private static final int WRONG_META_COLOR = 0xBF5F00;
    private static final int MISSING_BLOCK_COLOR = 0x00BFFF;

    private static volatile int drawnBlocks;
    private static volatile int drawnQuads;

    private SchematicRenderer() {}

    @SubscribeEvent
    static void emitGizmos(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        SchematicState state = SchematicState.instance();
        if (!state.isLoaded() || mc.level == null || mc.player == null) {
            return;
        }

        drawnBlocks = 0;
        try (Gizmos.TemporaryCollection ignored = mc.collectPerTickGizmos()) {
            emitGuides(state);
            if (!state.isHidden()) {
                emitSchematic(mc, state);
            }
        }
    }

    private static void emitGuides(SchematicState state) {
        BlockPos origin = state.position();
        if (state.pointA() != null) {
            Gizmos.cuboid(box(state.pointA().offset(origin)),
                    GizmoStyle.stroke(POINT_A_COLOR, 2.0F)).setAlwaysOnTop();
        }
        if (state.pointB() != null) {
            Gizmos.cuboid(box(state.pointB().offset(origin)),
                    GizmoStyle.stroke(POINT_B_COLOR, 2.0F)).setAlwaysOnTop();
        }
        if (state.showGuide() && state.pointA() != null && state.pointB() != null) {
            BlockPos a = state.pointA().offset(origin);
            BlockPos b = state.pointB().offset(origin);
            Gizmos.cuboid(new AABB(a.getX(), a.getY(), a.getZ(),
                            b.getX() + 1.0, b.getY() + 1.0, b.getZ() + 1.0).inflate(1.0),
                    GizmoStyle.stroke(SELECTION_COLOR, 2.0F)).setAlwaysOnTop();
        }
        if (state.schematic() != null) {
            BlockPos end = origin.offset(state.schematic().getWidth() - 1,
                    state.schematic().getHeight() - 1, state.schematic().getLength() - 1);
            Gizmos.cuboid(new AABB(origin.getX(), origin.getY(), origin.getZ(),
                            end.getX() + 1.0, end.getY() + 1.0, end.getZ() + 1.0).inflate(1.0),
                    GizmoStyle.stroke(BOUNDS_COLOR, 2.0F)).setAlwaysOnTop();
        }
    }

    private static void emitSchematic(Minecraft mc, SchematicState state) {
        int distance = Math.min(Config.RENDER_DISTANCE.get(), 16);
        double delta = Config.BLOCK_DELTA.get();
        BlockPos center = mc.player.blockPosition();
        int alpha = Config.ALPHA_ENABLED.get()
                ? Math.max(16, Math.min(255, (int) Math.round(Config.ALPHA.get() * 255.0)))
                : 0xFF;
        int fillAlpha = Config.ALPHA_ENABLED.get() ? Math.max(0x20, alpha / 2) : 0x99;
        int placed = 0;
        int max = 1500;

        for (int dx = -distance; dx <= distance && placed < max; dx++) {
            for (int dy = -distance; dy <= distance && placed < max; dy++) {
                for (int dz = -distance; dz <= distance && placed < max; dz++) {
                    BlockPos worldPos = center.offset(dx, dy, dz);
                    BlockState expected = state.renderStateAt(worldPos);
                    if (state.isExtraAir(expected)) {
                        continue;
                    }
                    BlockState actual = mc.level.getBlockState(worldPos);
                    int rgb = ghostColor(state, expected, actual);
                    if (rgb == 0 || (Config.TEXTURED_GHOSTS.get() && rgb == GHOST_COLOR)) {
                        continue;
                    }
                    AABB box = box(worldPos).deflate(delta);
                    if (Config.TEXTURED_GHOSTS.get()) {
                        // Classic Schematica draws a translucent filled cuboid over the
                        // textured ghost when the world differs (alpha 0x3F).
                        Gizmos.cuboid(box, GizmoStyle.fill(argb(rgb, 0x3F)));
                    } else {
                        Gizmos.cuboid(box, GizmoStyle.strokeAndFill(
                                argb(rgb, alpha), 1.5F, argb(rgb, fillAlpha)));
                    }
                    placed++;
                }
            }
        }
        drawnBlocks = placed;
    }

    /** Turns a legacy RGB color into an ARGB color the 26.3 gizmo renderer understands. */
    private static int argb(int rgb, int alpha) {
        return (alpha << 24) | (rgb & 0x00FFFFFF);
    }

    private static int ghostColor(SchematicState state, BlockState expected, BlockState actual) {
        boolean expectedAir = state.isExtraAir(expected);
        boolean actualAir = actual == null || actual.isAir();
        if (expectedAir && actualAir) {
            return 0;
        }
        if (!Config.HIGHLIGHT.get()) {
            return GHOST_COLOR;
        }
        if (expectedAir) {
            return Config.HIGHLIGHT_AIR.get() ? EXTRA_BLOCK_COLOR : 0;
        }
        if (actualAir) {
            return MISSING_BLOCK_COLOR;
        }
        if (expected.getBlock() != actual.getBlock()) {
            return WRONG_BLOCK_COLOR;
        }
        if (expected != actual) {
            return WRONG_META_COLOR;
        }
        return GHOST_COLOR;
    }

    private static AABB box(BlockPos pos) {
        return new AABB(pos.getX(), pos.getY(), pos.getZ(),
                pos.getX() + 1.0, pos.getY() + 1.0, pos.getZ() + 1.0);
    }

    /**
     * Classic Schematica ghost: every schematic block is drawn with its real model and
     * texture, translucent and full-bright. The mismatch colors are filled cuboids on
     * top (see emitSchematic), exactly like the 1.12.2 RenderOverlay.
     *
     * Important: the level pose is camera-relative. Vanilla's submitTransientBlocks()
     * translates by (blockPos - camera.pos) before submitting, so we must do the same.
     */
    @SubscribeEvent
    public static void submitGhostBlocks(SubmitCustomGeometryEvent event) {
        Minecraft mc = Minecraft.getInstance();
        SchematicState state = SchematicState.instance();
        if (!Config.TEXTURED_GHOSTS.get() || mc.level == null || mc.player == null
                || !state.isLoaded() || state.isHidden()) {
            return;
        }

        SubmitNodeCollector collector = event.getSubmitNodeCollector();
        Vec3 camera = event.getLevelRenderState().cameraRenderState.pos;
        if (camera == null) {
            return;
        }
        int alpha = Config.ALPHA_ENABLED.get()
                ? Math.max(16, Math.min(255, (int) Math.round(Config.ALPHA.get() * 255.0)))
                : 0x80;

        BlockPos center = mc.player.blockPosition();
        int distance = Math.min(Config.RENDER_DISTANCE.get(), 12);
        Map<BlockState, List<BakedQuad>> quads = new HashMap<>();
        List<BlockPos> targets = new ArrayList<>();
        for (int dx = -distance; dx <= distance && targets.size() < 1200; dx++) {
            for (int dy = -distance; dy <= distance && targets.size() < 1200; dy++) {
                for (int dz = -distance; dz <= distance && targets.size() < 1200; dz++) {
                    BlockPos worldPos = center.offset(dx, dy, dz);
                    if (!state.isExtraAir(state.renderStateAt(worldPos))) {
                        targets.add(worldPos);
                    }
                }
            }
        }

        int[] submitted = { 0 };
        collector.submitCustomGeometry(event.getPoseStack(), RenderTypes.translucentMovingBlock(),
                (pose, consumer) -> {
                    PoseStack stack = new PoseStack();
                    stack.mulPose(pose.pose());
                    QuadInstance instance = new QuadInstance();
                    instance.setLightCoords(0x00F000F0);
                    instance.setOverlayCoords(0);
                    for (BlockPos worldPos : targets) {
                        BlockState expected = state.renderStateAt(worldPos);
                        List<BakedQuad> blockQuads = quads.computeIfAbsent(expected,
                                key -> quadsFor(mc, key));
                        if (blockQuads.isEmpty()) {
                            continue;
                        }
                        stack.pushPose();
                        stack.translate(worldPos.getX() - camera.x, worldPos.getY() - camera.y,
                                worldPos.getZ() - camera.z);
                        for (BakedQuad quad : blockQuads) {
                            Direction side = quad.direction();
                            if (side != null
                                    && !state.isExtraAir(state.renderStateAt(worldPos.relative(side)))) {
                                continue; // hidden face between two schematic blocks
                            }
                            int tint = quad.materialInfo().tintIndex();
                            int color = 0xFFFFFF;
                            if (tint >= 0) {
                                BlockTintSource source = mc.getBlockColors()
                                        .getTintSource(expected, tint);
                                if (source != null) {
                                    color = source.colorInWorld(expected, mc.level, worldPos);
                                }
                            }
                            instance.setColor(argb(color, alpha));
                            consumer.putBakedQuad(stack.last(), quad, instance);
                            submitted[0]++;
                        }
                        stack.popPose();
                    }
                });
        drawnQuads = submitted[0];
    }

    private static List<BakedQuad> quadsFor(Minecraft mc, BlockState blockState) {
        List<BakedQuad> result = new ArrayList<>();
        BlockStateModel model = mc.getModelManager().getBlockStateModelSet().get(blockState);
        if (model == null) {
            return result;
        }
        List<BlockStateModelPart> parts = new ArrayList<>();
        model.collectParts(RandomSource.create(42L), parts);
        for (BlockStateModelPart part : parts) {
            for (Direction direction : Direction.values()) {
                result.addAll(part.getQuads(direction));
            }
            result.addAll(part.getQuads(null)); // unculled faces (crosses, plants, ...)
        }
        return result;
    }

    public static void registerDebug(RegisterDebugEntriesEvent event) {
        if (!Config.SHOW_DEBUG_INFO.get()) {
            return;
        }
        event.register(Identifier.parse("schematica:schematic"),
                (DebugScreenDisplayer displayer, net.minecraft.world.level.Level level,
                 net.minecraft.world.level.chunk.LevelChunk chunk,
                 net.minecraft.world.level.chunk.LevelChunk renderedChunk) -> {
                    SchematicState state = SchematicState.instance();
                    if (!state.isLoaded()) {
                        return;
                    }
                    displayer.addPriorityLine("Schematica: " + state.name());
                    displayer.addPriorityLine("Position: " + state.position().toShortString());
                    displayer.addPriorityLine("Layer: " + state.layerMode().label()
                            + " (" + state.renderingLayer() + ")");
                    displayer.addPriorityLine("Blocks: "
                            + (state.schematic().getWidth() * state.schematic().getHeight()
                            * state.schematic().getLength()));
                    displayer.addPriorityLine("Ghosts drawn: " + drawnBlocks
                            + (state.isHidden() ? " (schematic hidden)"
                            : drawnBlocks == 0 ? " (none in range/layer)" : ""));
                    displayer.addPriorityLine("Ghost quads: " + drawnQuads);
                    displayer.addPriorityLine("Player: "
                            + Minecraft.getInstance().player.blockPosition().toShortString());
                });
    }
}
