package com.kodari.breathless;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Server -> client: your current spore exposure (0..100). The server owns the real value. */
public record ExposurePayload(float exposure) implements CustomPacketPayload {
   public static final CustomPacketPayload.Type<ExposurePayload> TYPE = new CustomPacketPayload.Type<>(Breathless.id("exposure"));
   public static final StreamCodec<RegistryFriendlyByteBuf, ExposurePayload> CODEC =
      StreamCodec.composite(ByteBufCodecs.FLOAT, ExposurePayload::exposure, ExposurePayload::new);

   @Override
   public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
      return TYPE;
   }
}
