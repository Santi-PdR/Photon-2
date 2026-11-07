package com.lowdragmc.photon.integration;

import com.lowdragmc.lowdraglib.syncdata.AccessorOp;
import com.lowdragmc.lowdraglib.syncdata.TypedPayloadRegistries;
import com.lowdragmc.lowdraglib.syncdata.accessor.CustomObjectAccessor;
import com.lowdragmc.lowdraglib.syncdata.payload.ITypedPayload;
import com.lowdragmc.lowdraglib.syncdata.payload.NbtTagPayload;
import com.lowdragmc.lowdraglib2.LDLib2;
import com.lowdragmc.lowdraglib2.Platform;
import com.lowdragmc.lowdraglib2.editor.resource.FilePath;
import com.lowdragmc.lowdraglib2.editor.resource.IResourcePath;
import com.lowdragmc.lowdraglib2.math.GradientColor;
import com.lowdragmc.lowdraglib2.math.Range;
import com.lowdragmc.photon.Photon;
import com.lowdragmc.photon.client.gameobject.emitter.data.model.IModelSource;
import com.lowdragmc.photon.client.gameobject.emitter.data.number.NumberFunction;
import com.lowdragmc.photon.client.gameobject.emitter.data.number.NumberFunction3;
import com.lowdragmc.photon.client.gameobject.emitter.data.number.curve.ECBCurves;
import com.lowdragmc.photon.client.gameobject.emitter.data.shape.IShape;
import com.lowdragmc.photon.client.gameobject.emitter.data.shape.MeshData;
import com.lowdragmc.photon.client.gameobject.emitter.data.material.IMaterial;
import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.Decoder;
import com.mojang.serialization.DynamicOps;
import com.mojang.serialization.Encoder;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import org.joml.Vector2f;
import org.joml.Vector2i;

import java.io.File;
import java.nio.file.Path;
import java.util.List;

/** Bridges LDLib 1.x typed payloads written by old Photon projects to LDLib2 codecs. */
@OnlyIn(Dist.CLIENT)
public final class PhotonLegacyPayloadAccessors {
    private static final Codec<Number> NUMBER_CODEC = Codec.of(new Encoder<>() {
        @Override
        public <T> DataResult<T> encode(Number value, DynamicOps<T> ops, T prefix) {
            T encoded;
            if (value instanceof Byte number) encoded = ops.createByte(number);
            else if (value instanceof Short number) encoded = ops.createShort(number);
            else if (value instanceof Integer number) encoded = ops.createInt(number);
            else if (value instanceof Long number) encoded = ops.createLong(number);
            else if (value instanceof Float number) encoded = ops.createFloat(number);
            else encoded = ops.createDouble(value.doubleValue());
            return ops.mergeToPrimitive(prefix, encoded);
        }
    }, new Decoder<>() {
        @Override
        public <T> DataResult<Pair<Number, T>> decode(DynamicOps<T> ops, T input) {
            return ops.getNumberValue(input).map(value -> Pair.of(value, input));
        }
    });
    private static final Codec<Vector2f> VECTOR2F_CODEC = Codec.FLOAT.listOf().comapFlatMap(values ->
            values.size() == 2 ? DataResult.success(new Vector2f(values.get(0), values.get(1)))
                    : DataResult.error(() -> "Expected a two-element Vector2f, got " + values.size()),
            value -> List.of(value.x, value.y));
    private static final Codec<Vector2i> VECTOR2I_CODEC = Codec.INT.listOf().comapFlatMap(values ->
            values.size() == 2 ? DataResult.success(new Vector2i(values.get(0), values.get(1)))
                    : DataResult.error(() -> "Expected a two-element Vector2i, got " + values.size()),
            value -> List.of(value.x, value.y));
    private static boolean registered;

    private PhotonLegacyPayloadAccessors() {}

    public static synchronized void register() {
        if (registered) return;
        registerCodec(Number.class, NUMBER_CODEC);
        TypedPayloadRegistries.register(NbtTagPayload.class, NbtTagPayload::new, curvesAccessor(), 1000);
        registerCodec(Vector2f.class, VECTOR2F_CODEC);
        registerCodec(Vector2i.class, VECTOR2I_CODEC);
        registerCodec(Range.class, Range.CODEC);
        TypedPayloadRegistries.register(NbtTagPayload.class, NbtTagPayload::new, gradientAccessor(), 1000);
        TypedPayloadRegistries.register(NbtTagPayload.class, NbtTagPayload::new, meshAccessor(), 1000);
        registerCodec(IResourcePath.class, resourcePathCodec(IResourcePath.CODEC, LDLib2.getAssetsDir().toPath()));
        registerCodec(NumberFunction.class, NumberFunction.CODEC);
        registerCodec(NumberFunction3.class, NumberFunction3.CODEC);
        registerCodec(IShape.class, IShape.CODEC);
        registerCodec(IModelSource.class, IModelSource.CODEC);
        registerCodec(IMaterial.class, IMaterial.CODEC);
        registered = true;
    }

    private static <T> void registerCodec(Class<T> type, Codec<T> codec) {
        TypedPayloadRegistries.register(NbtTagPayload.class, NbtTagPayload::new, codecAccessor(type, codec), 1000);
    }

    private static Codec<IResourcePath> resourcePathCodec(Codec<IResourcePath> codec, Path assetsDir) {
        return codec.xmap(path -> {
            if (!(path instanceof FilePath filePath)) return path;
            File resolved = resolveEditorResourceFile(filePath.getPath(), filePath.getFile(), assetsDir);
            return resolved.equals(filePath.getFile()) ? path : new FilePath(resolved);
        }, path -> path);
    }

    private static File resolveEditorResourceFile(String storedPath, File resolvedFile, Path assetsDir) {
        String prefix = "./ldlib2/assets/";
        if (resolvedFile.isFile() || storedPath == null || !storedPath.startsWith(prefix)) return resolvedFile;
        try {
            Path root = assetsDir.toAbsolutePath().normalize();
            Path relative = Path.of(storedPath.substring(prefix.length())).normalize();
            if (relative.isAbsolute() || relative.startsWith("..")) return resolvedFile;
            Path candidate = root.resolve(relative).normalize();
            return candidate.startsWith(root) && candidate.toFile().isFile() ? candidate.toFile() : resolvedFile;
        } catch (RuntimeException ignored) {
            return resolvedFile;
        }
    }

    private static <T> CustomObjectAccessor<T> codecAccessor(Class<T> type, Codec<T> codec) {
        return new CustomObjectAccessor<>(type, false) {
            @Override
            public ITypedPayload<?> serialize(AccessorOp op, T value) {
                Tag encoded = codec.encodeStart(NbtOps.INSTANCE, value).getOrThrow(false,
                        error -> Photon.LOGGER.error("Failed to persist {}: {}", type.getSimpleName(), error));
                return NbtTagPayload.of(encoded);
            }

            @Override
            public T deserialize(AccessorOp op, ITypedPayload<?> payload) {
                Tag encoded = (Tag) payload.getPayload();
                return codec.parse(NbtOps.INSTANCE, encoded).getOrThrow(false,
                        error -> Photon.LOGGER.error("Failed to restore {}: {}", type.getSimpleName(), error));
            }
        };
    }

    private static CustomObjectAccessor<GradientColor> gradientAccessor() {
        return new CustomObjectAccessor<>(GradientColor.class, false) {
            @Override
            public ITypedPayload<?> serialize(AccessorOp op, GradientColor value) {
                return NbtTagPayload.of(value.serializeNBT(Platform.getFrozenRegistry()));
            }

            @Override
            public GradientColor deserialize(AccessorOp op, ITypedPayload<?> payload) {
                GradientColor value = new GradientColor();
                value.deserializeNBT(Platform.getFrozenRegistry(), (CompoundTag) payload.getPayload());
                return value;
            }
        };
    }

    private static CustomObjectAccessor<ECBCurves> curvesAccessor() {
        return new CustomObjectAccessor<>(ECBCurves.class, false) {
            @Override
            public ITypedPayload<?> serialize(AccessorOp op, ECBCurves value) {
                return NbtTagPayload.of(value.serializeNBT(Platform.getFrozenRegistry()));
            }

            @Override
            public ECBCurves deserialize(AccessorOp op, ITypedPayload<?> payload) {
                ECBCurves value = new ECBCurves();
                value.deserializeNBT(Platform.getFrozenRegistry(), (net.minecraft.nbt.ListTag) payload.getPayload());
                return value;
            }
        };
    }

    private static CustomObjectAccessor<MeshData> meshAccessor() {
        return new CustomObjectAccessor<>(MeshData.class, false) {
            @Override
            public ITypedPayload<?> serialize(AccessorOp op, MeshData value) {
                return NbtTagPayload.of(value.serializeNBT(Platform.getFrozenRegistry()));
            }

            @Override
            public MeshData deserialize(AccessorOp op, ITypedPayload<?> payload) {
                MeshData value = new MeshData();
                value.deserializeNBT(Platform.getFrozenRegistry(), (CompoundTag) payload.getPayload());
                return value;
            }
        };
    }
}
