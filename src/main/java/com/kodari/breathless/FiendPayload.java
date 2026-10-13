package com.kodari.breathless;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Server -> client: F13ND just cleared the spores. Start the pop-up sequence. The seed makes the pop-up layout differ each time. */
public record FiendPayload(int seed) implements CustomPacketPayload {
   public static final CustomPacketPayload.Type<FiendPayload> TYPE = new CustomPacketPayload.Type<>(Breathless.id("fiend"));
   public static final StreamCodec<RegistryFriendlyByteBuf, FiendPayload> CODEC =
      StreamCodec.composite(ByteBufCodecs.VAR_INT, FiendPayload::seed, FiendPayload::new);

   @Override
   public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
      return TYPE;
   }
}
