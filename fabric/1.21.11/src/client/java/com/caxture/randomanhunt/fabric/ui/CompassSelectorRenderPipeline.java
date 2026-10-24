package com.caxture.randomanhunt.fabric.ui;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.DepthTestFunction;
import com.mojang.blaze3d.shaders.UniformType;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.resources.Identifier;

/** Dedicated image pipeline so third-party dark-GUI rewrites do not match a vanilla GUI pipeline. */
public final class CompassSelectorRenderPipeline {
    public static final RenderPipeline IMAGE = RenderPipeline.builder()
            .withLocation(id(CompassSelectorRenderSpec.PIPELINE_PATH))
            .withUniform("Projection", UniformType.UNIFORM_BUFFER)
            .withUniform("DynamicTransforms", UniformType.UNIFORM_BUFFER)
            .withVertexShader(id(CompassSelectorRenderSpec.SHADER_PATH))
            .withFragmentShader(id(CompassSelectorRenderSpec.SHADER_PATH))
            .withSampler("Sampler0")
            .withBlend(BlendFunction.TRANSLUCENT_PREMULTIPLIED_ALPHA)
            .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
            .withVertexFormat(DefaultVertexFormat.POSITION_TEX_COLOR, VertexFormat.Mode.QUADS)
            .build();

    private CompassSelectorRenderPipeline() {
    }

    private static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(CompassSelectorRenderSpec.NAMESPACE, path);
    }
}
