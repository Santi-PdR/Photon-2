package com.lowdragmc.photon.client.gameobject.emitter.data.material;

import net.minecraft.nbt.FloatTag;
import net.minecraft.nbt.ListTag;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import org.joml.Vector2f;
import org.joml.Vector3f;
import org.joml.Vector4f;

/** Runtime-free regression for rejecting non-finite shader graph material overrides. */
@OnlyIn(Dist.CLIENT)
public final class ShaderGraphMaterialRegression {
    private ShaderGraphMaterialRegression() {
    }

    public static void verify() {
        var invalidVector = new ListTag();
        invalidVector.add(FloatTag.valueOf(0f));
        invalidVector.add(FloatTag.valueOf(Float.NaN));
        var validVector = new ListTag();
        validVector.add(FloatTag.valueOf(0f));
        validVector.add(FloatTag.valueOf(1f));
        if (ShaderGraphMaterial.decodeValue(invalidVector) != null
                || !(ShaderGraphMaterial.decodeValue(validVector) instanceof Vector2f)
                || ShaderGraphMaterial.isFiniteOverride(Float.NaN)
                || ShaderGraphMaterial.isFiniteOverride(Float.POSITIVE_INFINITY)
                || ShaderGraphMaterial.isFiniteOverride(new Vector2f(0f, Float.NaN))
                || ShaderGraphMaterial.isFiniteOverride(new Vector3f(0f, Float.NEGATIVE_INFINITY, 1f))
                || ShaderGraphMaterial.isFiniteOverride(new Vector4f(0f, 1f, Float.NaN, 1f))
                || !ShaderGraphMaterial.isFiniteOverride(1f)
                || !ShaderGraphMaterial.isFiniteOverride(new Vector4f(0f, 1f, 2f, 3f))) {
            throw new IllegalStateException("Shader graph material accepted a non-finite uniform override");
        }
    }
}
