package com.lowdragmc.photon.integration;

import com.lowdragmc.lowdraglib2.compat.network.codec.ByteBufCodecs;
import com.lowdragmc.lowdraglib2.syncdata.AccessorRegistries;
import com.lowdragmc.lowdraglib2.syncdata.accessor.direct.CustomDirectAccessor;
import com.lowdragmc.lowdraglib.syncdata.AccessorOp;
import com.lowdragmc.lowdraglib.syncdata.TypedPayloadRegistries;
import com.lowdragmc.lowdraglib.syncdata.accessor.CustomObjectAccessor;
import com.lowdragmc.lowdraglib.syncdata.payload.ITypedPayload;
import com.lowdragmc.lowdraglib.syncdata.payload.NbtTagPayload;
import com.lowdragmc.photon.client.gameobject.emitter.data.material.IMaterial;
import com.lowdragmc.photon.client.gameobject.emitter.data.model.IModelSource;
import com.lowdragmc.photon.client.gameobject.emitter.data.number.NumberFunction;
import com.lowdragmc.photon.client.gameobject.emitter.data.number.NumberFunction3;
import com.lowdragmc.photon.client.gameobject.emitter.data.shape.IShape;
import com.lowdragmc.photon.Photon;
import com.mojang.serialization.Codec;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

/** Client-only LDLib2 accessors, loaded by the common plugin only through {@link net.minecraftforge.fml.DistExecutor}. */
@OnlyIn(Dist.CLIENT)
public final class PhotonLDLibClientPlugin {
    private PhotonLDLibClientPlugin() {
    }

    public static void registerAccessors() {
        // Photon 1.20.1 still uses LDLib 1's PersistedParser for the editor's saved FX
        // configs, alongside LDLib2 for the editor UI. Register the codec-backed values
        // with both libraries; LDLib 1 otherwise rejects NumberFunction3 during load.
        TypedPayloadRegistries.init();
        registerLegacyCodecAccessor(NumberFunction.class, NumberFunction.CODEC);
        registerLegacyCodecAccessor(NumberFunction3.class, NumberFunction3.CODEC);
        registerLegacyCodecAccessor(IShape.class, IShape.CODEC);
        registerLegacyCodecAccessor(IModelSource.class, IModelSource.CODEC);
        registerLegacyCodecAccessor(IMaterial.class, IMaterial.CODEC);

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

    private static <T> void registerLegacyCodecAccessor(Class<T> type, Codec<T> codec) {
        TypedPayloadRegistries.register(NbtTagPayload.class, NbtTagPayload::new,
                new CustomObjectAccessor<T>(type, false) {
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
                }, 1000);
    }
}
