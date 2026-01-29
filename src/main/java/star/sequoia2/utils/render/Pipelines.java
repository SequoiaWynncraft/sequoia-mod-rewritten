package star.sequoia2.utils.render;

import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.DepthTestFunction;
import com.mojang.blaze3d.systems.GpuDevice;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.gl.UniformType;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.resource.ResourceManager;
import net.minecraft.resource.SynchronousResourceReloader;
import net.minecraft.util.Identifier;
import org.apache.commons.io.IOUtils;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import static net.minecraft.client.gl.RenderPipelines.POSITION_COLOR_SNIPPET;
import static net.minecraft.client.gl.RenderPipelines.RENDERTYPE_LINES_SNIPPET;

public abstract class Pipelines {
    private static final List<RenderPipeline> PIPELINES = new ArrayList<>();

    static final RenderPipeline GLOBAL_QUADS_PIPELINE = add(RenderPipeline.builder(POSITION_COLOR_SNIPPET)
            .withLocation("pipeline/global_fill_pipeline")
            .withVertexFormat(VertexFormats.POSITION_COLOR, VertexFormat.DrawMode.QUADS)
            .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
            .withBlend(BlendFunction.TRANSLUCENT)
            .withDepthWrite(false)
            .withCull(false)
            .build());

    static final RenderPipeline GLOBAL_TRIANGLE_FAN_PIPELINE = add(RenderPipeline.builder(POSITION_COLOR_SNIPPET)
            .withLocation("pipeline/global_triangle_fan_pipeline")
            .withVertexFormat(VertexFormats.POSITION_COLOR, VertexFormat.DrawMode.TRIANGLE_FAN)
            .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
            .withBlend(BlendFunction.TRANSLUCENT)
            .withDepthWrite(false)
            .withCull(false)
            .build());

    static final RenderPipeline GLOBAL_LINES_PIPELINE = add(RenderPipeline.builder(RENDERTYPE_LINES_SNIPPET)
            .withLocation("pipeline/global_lines_pipeline")
            .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
            .withBlend(BlendFunction.TRANSLUCENT)
            .withDepthWrite(false)
            .withCull(false)
            .build());

    private static RenderPipeline add(RenderPipeline pipeline) {
        PIPELINES.add(pipeline);
        return pipeline;
    }

    private Pipelines() {}

    public static class Reloader implements SynchronousResourceReloader {
        @Override
        public void reload(ResourceManager manager) {
            GpuDevice device = RenderSystem.getDevice();

            for (RenderPipeline pipeline : PIPELINES) {
                device.precompilePipeline(pipeline, (identifier, shaderType) -> {
                    var resource = manager.getResource(identifier).get();

                    try (var in = resource.getInputStream()) {
                        return IOUtils.toString(in, StandardCharsets.UTF_8);
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                });
            }
        }
    }
}