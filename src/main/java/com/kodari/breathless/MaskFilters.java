package com.kodari.breathless;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/**
 * What is inside a gas mask: two filter slots. Each number is that filter's remaining life
 * (0 = empty slot, Tuning.FILTER_MAX = brand new). Stored on the mask as a data component.
 */
public record MaskFilters(int first, int second) {
   public static final MaskFilters EMPTY = new MaskFilters(0, 0);

   public static final Codec<MaskFilters> CODEC = RecordCodecBuilder.create(instance -> instance.group(
      Codec.INT.fieldOf("first").forGetter(MaskFilters::first),
      Codec.INT.fieldOf("second").forGetter(MaskFilters::second)
   ).apply(instance, MaskFilters::new));

   public static final StreamCodec<ByteBuf, MaskFilters> STREAM_CODEC =
      StreamCodec.composite(ByteBufCodecs.VAR_INT, MaskFilters::first, ByteBufCodecs.VAR_INT, MaskFilters::second, MaskFilters::new);

   /** How many slots hold a filter. */
   public int count() {
      return (first > 0 ? 1 : 0) + (second > 0 ? 1 : 0);
   }

   public boolean isFull() {
      return count() >= 2;
   }

   /** True when there are enough filters for the mask to protect. */
   public boolean working() {
      return count() >= Math.min(2, Tuning.FILTERS_REQUIRED);
   }

   /** Life of the given slot (0 or 1) as 0..1. */
   public float fraction(int slot) {
      int value = slot == 0 ? first : second;
      return Math.max(0.0F, Math.min(1.0F, value / (float)Tuning.FILTER_MAX));
   }

   /** The weakest loaded filter, 0..1 (1 if none, so nothing looks cracked). */
   public float weakest() {
      float result = 1.0F;
      if (first > 0) {
         result = Math.min(result, fraction(0));
      }
      if (second > 0) {
         result = Math.min(result, fraction(1));
      }
      return result;
   }

   /** Put a filter with this much life into the first empty slot. */
   public MaskFilters with(int life) {
      int clamped = Math.max(1, Math.min(Tuning.FILTER_MAX, life));
      return first <= 0 ? new MaskFilters(clamped, second) : new MaskFilters(first, clamped);
   }

   /** The life of the filter that would come out next (the last one in), or 0 if none. */
   public int lastLife() {
      return second > 0 ? second : first;
   }

   /** Remove the last filter that went in. */
   public MaskFilters withoutLast() {
      return second > 0 ? new MaskFilters(first, 0) : new MaskFilters(0, 0);
   }
}
