package com.caxture.randomanhunt.fabric.ui;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.renderpearl.api.pipeline.BlendFunction;
import com.mojang.renderpearl.api.pipeline.ColorTargetState;
import com.mojang.renderpearl.api.pipeline.PrimitiveTopology;
import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import net.minecraft.client.renderer.BindGroupLayouts;
import net.minecraft.resources.Identifier;

/** Dedicated image pipeline so third-party dark-GUI rewrites do not match a vanilla GUI pipeline. */
public final class CompassSelectorRenderPipeline {
    public static final RenderPipeline IMAGE = RenderPipeline.builder()
            .withLocation(id(CompassSelectorRenderSpec.PIPELINE_PATH))
            .withBindGroupLayout(BindGroupLayouts.GLOBALS)
            .withBindGroupLayout(BindGroupLayouts.PROJECTION)
            .withBindGroupLayout(BindGroupLayouts.DYNAMIC_TRANSFORMS)
            .withVertexShader(id(CompassSelectorRenderSpec.SHADER_PATH))
            .withFragmentShader(id(CompassSelectorRenderSpec.SHADER_PATH))
            .withBindGroupLayout(BindGroupLayouts.SAMPLER0)
            .withColorTargetState(new ColorTargetState(
                    BlendFunction.TRANSLUCENT_PREMULTIPLIED_ALPHA))
            .withVertexBinding(0, DefaultVertexFormat.POSITION_TEX_COLOR)
            .withPrimitiveTopology(PrimitiveTopology.QUADS)
            .build();

    private CompassSelectorRenderPipeline() {
    }

    private static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(CompassSelectorRenderSpec.NAMESPACE, path);
    }
}
