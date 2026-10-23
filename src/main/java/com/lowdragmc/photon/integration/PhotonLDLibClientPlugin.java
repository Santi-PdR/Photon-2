package com.lowdragmc.photon.integration;

import com.lowdragmc.lowdraglib2.LDLib2;
import com.lowdragmc.lowdraglib2.Platform;
import com.lowdragmc.lowdraglib2.compat.network.codec.ByteBufCodecs;
import com.lowdragmc.lowdraglib2.editor.resource.FilePath;
import com.lowdragmc.lowdraglib2.editor.resource.IResourcePath;
import com.lowdragmc.lowdraglib2.math.GradientColor;
import com.lowdragmc.lowdraglib2.math.Range;
import com.lowdragmc.lowdraglib2.syncdata.AccessorRegistries;
import com.lowdragmc.lowdraglib2.syncdata.accessor.direct.CustomDirectAccessor;
import com.lowdragmc.lowdraglib.syncdata.AccessorOp;
import com.lowdragmc.lowdraglib.syncdata.TypedPayloadRegistries;
import com.lowdragmc.lowdraglib.syncdata.accessor.CustomObjectAccessor;
import com.lowdragmc.lowdraglib.syncdata.payload.ITypedPayload;
import com.lowdragmc.lowdraglib.syncdata.payload.NbtTagPayload;
import com.lowdragmc.photon.Photon;
import com.lowdragmc.photon.client.gameobject.emitter.data.number.curve.ECBCurves;
import com.lowdragmc.photon.client.gameobject.emitter.data.material.IMaterial;
import com.lowdragmc.photon.client.gameobject.emitter.data.model.IModelSource;
import com.lowdragmc.photon.client.gameobject.emitter.data.number.NumberFunction;
import com.lowdragmc.photon.client.gameobject.emitter.data.number.NumberFunction3;
import com.lowdragmc.photon.client.gameobject.emitter.data.shape.IShape;
import com.lowdragmc.photon.client.gameobject.emitter.data.shape.MeshData;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.Decoder;
import com.mojang.serialization.DynamicOps;
import com.mojang.serialization.Encoder;
import com.mojang.datafixers.util.Pair;
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

/** Client-only LDLib2 accessors, loaded by the common plugin only through {@link net.minecraftforge.fml.DistExecutor}. */
@OnlyIn(Dist.CLIENT)
public final class PhotonLDLibClientPlugin {
    /** Preserve the concrete numeric representation used by NbtOps (byte/int/float/double, etc.). */
    static final Codec<Number> NUMBER_CODEC = Codec.of(new Encoder<>() {
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
    static final Codec<Vector2f> VECTOR2F_CODEC = Codec.FLOAT.listOf().comapFlatMap(values ->
            values.size() == 2
                    ? DataResult.success(new Vector2f(values.get(0), values.get(1)))
                    : DataResult.error(() -> "Expected a two-element Vector2f, got " + values.size()),
            value -> List.of(value.x, value.y));
    static final Codec<Vector2i> VECTOR2I_CODEC = Codec.INT.listOf().comapFlatMap(values ->
            values.size() == 2
                    ? DataResult.success(new Vector2i(values.get(0), values.get(1)))
                    : DataResult.error(() -> "Expected a two-element Vector2i, got " + values.size()),
            value -> List.of(value.x, value.y));
    private static boolean legacyAccessorsRegistered;

    private PhotonLDLibClientPlugin() {
    }

    public static void registerAccessors() {
        AccessorRegistries.registerAccessor(CustomDirectAccessor.builder(NumberFunction.class)
                .codec(NumberFunction.CODEC)
                .streamCodec(ByteBufCodecs.fromCodec(NumberFunction.CODEC))
                .codecMark()
                .build());
        AccessorRegistries.registerAccessor(CustomDirectAccessor.builder(NumberFunction3.class)
                .codec(NumberFunction3.CODEC)
                .streamCodec(ByteBufCodecs.fromCodec(NumberFunction3.CODEC))
                .codecMark()
                .build());
        AccessorRegistries.registerAccessor(CustomDirectAccessor.builder(IShape.class)
                .codec(IShape.CODEC)
                .streamCodec(ByteBufCodecs.fromCodec(IShape.CODEC))
                .codecMark()
                .build());
        AccessorRegistries.registerAccessor(CustomDirectAccessor.builder(IModelSource.class)
                .codec(IModelSource.CODEC)
                .streamCodec(ByteBufCodecs.fromCodec(IModelSource.CODEC))
                .codecMark()
                .build());
        AccessorRegistries.registerAccessor(CustomDirectAccessor.builder(IMaterial.class)
                .codec(IMaterial.CODEC)
                .streamCodec(ByteBufCodecs.fromCodec(IMaterial.CODEC))
                .copyMark(IMaterial::copy)
                .build());
    }

    /** Register old LDLib typed payload accessors before its registry becomes immutable. */
    public static synchronized void registerLegacyAccessors() {
        if (legacyAccessorsRegistered) return;
        registerLegacyCodecAccessor(Number.class, NUMBER_CODEC);
        registerLegacyCodecAccessor(ECBCurves.class, ECBCurves.CODEC);
        registerLegacyCodecAccessor(Vector2f.class, VECTOR2F_CODEC);
        registerLegacyCodecAccessor(Vector2i.class, VECTOR2I_CODEC);
        // Photon 26.2's speed-based settings persist LDLib2 Range values. The LDLib1
        // PersistedParser used by the 1.20.1 port cannot infer an accessor for this type,
        // so route it through LDLib2's canonical NBT codec as well.
        registerLegacyCodecAccessor(Range.class, Range.CODEC);
        // Gradient colors are provider-aware NBT values rather than Codec-backed values.
        // They are persisted inside Photon color functions and gradient textures.
        TypedPayloadRegistries.register(NbtTagPayload.class, NbtTagPayload::new,
                legacyGradientColorAccessor(), 1000);
        // Mesh emitters persist MeshData directly (rather than as a sub-persisted object).
        // Bridge its provider-aware serializer so LDLib1 history snapshots can retain the model source.
        TypedPayloadRegistries.register(NbtTagPayload.class, NbtTagPayload::new,
                legacyMeshDataAccessor(), 1000);
        // LDLib2-backed material fields are still persisted by LDLib1's parser through Photon codecs.
        // UIResourceMaterial stores its file/builtin reference as IResourcePath, so bridge LDLib2's
        // own versioned path codec just like the Photon model/material interfaces below.
        registerLegacyCodecAccessor(IResourcePath.class,
                editorResourcePathCodec(IResourcePath.CODEC, LDLib2.getAssetsDir().toPath()));
        registerLegacyCodecAccessor(NumberFunction.class, NumberFunction.CODEC);
        registerLegacyCodecAccessor(NumberFunction3.class, NumberFunction3.CODEC);
        registerLegacyCodecAccessor(IShape.class, IShape.CODEC);
        registerLegacyCodecAccessor(IModelSource.class, IModelSource.CODEC);
        registerLegacyCodecAccessor(IMaterial.class, IMaterial.CODEC);
        legacyAccessorsRegistered = true;
    }

    private static <T> void registerLegacyCodecAccessor(Class<T> type, Codec<T> codec) {
        TypedPayloadRegistries.register(NbtTagPayload.class, NbtTagPayload::new,
                legacyCodecAccessor(type, codec), 1000);
    }

    static Codec<IResourcePath> editorResourcePathCodec(Codec<IResourcePath> codec, Path assetsDir) {
        return codec.xmap(path -> {
            if (!(path instanceof FilePath filePath)) return path;
            var resolved = resolveEditorResourceFile(filePath.getPath(), filePath.getFile(), assetsDir);
            return resolved.equals(filePath.getFile()) ? path : new FilePath(resolved);
        }, path -> path);
    }

    static File resolveEditorResourceFile(String storedPath, File resolvedFile, Path assetsDir) {
        final String assetsPrefix = "./ldlib2/assets/";
        if (resolvedFile.isFile() || storedPath == null || !storedPath.startsWith(assetsPrefix)) {
            return resolvedFile;
        }
        try {
            var root = assetsDir.toAbsolutePath().normalize();
            // The stored path is game-directory-relative, while assetsDir already points at
            // <game>/ldlib2/assets. Resolve only the part below that root; keeping the prefix
            // here would incorrectly look under <game>/ldlib2/assets/ldlib2/assets/.
            var relative = Path.of(storedPath.substring(assetsPrefix.length())).normalize();
            if (relative.isAbsolute() || relative.startsWith("..")) return resolvedFile;
            var candidate = root.resolve(relative).normalize();
            return candidate.startsWith(root) && candidate.toFile().isFile() ? candidate.toFile() : resolvedFile;
        } catch (RuntimeException ignored) {
            return resolvedFile;
        }
    }

    static <T> CustomObjectAccessor<T> legacyCodecAccessor(Class<T> type, Codec<T> codec) {
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

    static CustomObjectAccessor<GradientColor> legacyGradientColorAccessor() {
        return new CustomObjectAccessor<>(GradientColor.class, false) {
            @Override
            public ITypedPayload<?> serialize(AccessorOp op, GradientColor value) {
                return NbtTagPayload.of(value.serializeNBT(Platform.getFrozenRegistry()));
            }

            @Override
            public GradientColor deserialize(AccessorOp op, ITypedPayload<?> payload) {
                var value = new GradientColor();
                value.deserializeNBT(Platform.getFrozenRegistry(), (CompoundTag) payload.getPayload());
                return value;
            }
        };
    }

    static CustomObjectAccessor<MeshData> legacyMeshDataAccessor() {
        return new CustomObjectAccessor<>(MeshData.class, false) {
            @Override
            public ITypedPayload<?> serialize(AccessorOp op, MeshData value) {
                return NbtTagPayload.of(value.serializeNBT(Platform.getFrozenRegistry()));
            }

            @Override
            public MeshData deserialize(AccessorOp op, ITypedPayload<?> payload) {
                var value = new MeshData();
                value.deserializeNBT(Platform.getFrozenRegistry(), (CompoundTag) payload.getPayload());
                return value;
            }
        };
    }
}
