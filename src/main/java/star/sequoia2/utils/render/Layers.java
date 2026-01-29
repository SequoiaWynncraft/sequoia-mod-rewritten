package star.sequoia2.utils.render;

import net.minecraft.client.render.LayeringTransform;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.RenderSetup;

import java.util.concurrent.ConcurrentHashMap;

import static star.sequoia2.utils.render.Pipelines.GLOBAL_LINES_PIPELINE;
import static star.sequoia2.utils.render.Pipelines.GLOBAL_QUADS_PIPELINE;
import static star.sequoia2.utils.render.Pipelines.GLOBAL_TRIANGLE_FAN_PIPELINE;

public class Layers {
    private static final RenderLayer GLOBAL_QUADS;
    private static final RenderLayer GLOBAL_TRIANGLE_FAN;
    private static final ConcurrentHashMap<Integer, RenderLayer> GLOBAL_LINES = new ConcurrentHashMap<>();
    private static final RenderLayer OUTLINE_SHADER_LAYER;

    public static RenderLayer getGlobalLines(double width) {
        int lineWidth = (int) Math.round(width);
        return GLOBAL_LINES.computeIfAbsent(lineWidth, lw -> RenderLayer.of(
                "sequoia2_global_lines_" + lw,
                RenderSetup.builder(GLOBAL_LINES_PIPELINE)
                        .expectedBufferSize(256)
                        .layeringTransform(LayeringTransform.VIEW_OFFSET_Z_LAYERING)
                        .build()
        ));
    }

    public static RenderLayer getGlobalQuads() {
        return GLOBAL_QUADS;
    }

    public static RenderLayer getGlobalTriangleFan() {
        return GLOBAL_TRIANGLE_FAN;
    }

    static {
        GLOBAL_QUADS = RenderLayer.of(
                "sequoia2_global_fill",
                RenderSetup.builder(GLOBAL_QUADS_PIPELINE)
                        .expectedBufferSize(256)
                        .layeringTransform(LayeringTransform.VIEW_OFFSET_Z_LAYERING)
                        .build()
        );

        GLOBAL_TRIANGLE_FAN = RenderLayer.of(
                "sequoia2_global_triangle_fan",
                RenderSetup.builder(GLOBAL_TRIANGLE_FAN_PIPELINE)
                        .expectedBufferSize(256)
                        .layeringTransform(LayeringTransform.VIEW_OFFSET_Z_LAYERING)
                        .build()
        );

        OUTLINE_SHADER_LAYER = RenderLayer.of(
                "sequoia2_outline",
                RenderSetup.builder(GLOBAL_LINES_PIPELINE)
                        .expectedBufferSize(256)
                        .layeringTransform(LayeringTransform.VIEW_OFFSET_Z_LAYERING)
                        .build()
        );
    }
}
