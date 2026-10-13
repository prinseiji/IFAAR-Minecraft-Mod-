package com.kodari.breathless;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/** Small helpers shared by the player hazard, the mob rules and the item code. */
public final class Masks {
   private Masks() {
   }

   public static boolean isMask(ItemStack stack) {
      return stack.is(ModItems.GAS_MASK) || stack.is(ModItems.GAS_MASK_EMPTY);
   }

   public static MaskFilters getFilters(ItemStack stack) {
      MaskFilters filters = isMask(stack) ? stack.get(ModItems.FILTERS) : null;
      return filters == null ? MaskFilters.EMPTY : filters;
   }

   /** True if the entity wears a Gas Mask with enough filters that still have life. */
   public static boolean hasWorkingMask(LivingEntity entity) {
      ItemStack head = entity.getItemBySlot(EquipmentSlot.HEAD);
      return isMask(head) && getFilters(head).working();
   }

   /** Overworld only: the nether and end have no spore zone. */
   public static boolean isHazardLevel(Level level) {
      return level.dimension() == Level.OVERWORLD;
   }

   public static boolean isUnderground(Entity entity) {
      return isHazardLevel(entity.level()) && entity.getY() < Tuning.HAZARD_Y;
   }

   /** Registry id of the entity's type, e.g. "minecraft:zombie". Empty string if unknown. */
   public static String typeId(Entity entity) {
      Identifier key = BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType());
      return key == null ? "" : key.toString();
   }

   public static void playAt(Entity at, SoundEvent sound, float volume, float pitch) {
      at.level().playSound(null, at.blockPosition(), sound, SoundSource.PLAYERS, volume, pitch);
   }

   /** Percent (0..100) for display. */
   public static int percent(int life) {
      return Math.round(100.0F * Math.max(0, Math.min(Tuning.FILTER_MAX, life)) / Tuning.FILTER_MAX);
   }

   /**
    * Wears the player's filters down. The two filters take turns, so a pair lasts twice as long as one.
    * Plays the crack sounds when the weakest filter drops below 50% / 25%, and the run-out sound when one is spent.
    */
   public static void drainFilter(ServerPlayer player, int amount) {
      ItemStack head = player.getItemBySlot(EquipmentSlot.HEAD);
      if (!isMask(head)) {
         return;
      }
      MaskFilters before = getFilters(head);
      if (before.count() <= 0) {
         return;
      }
      int turn = (player.tickCount / Tuning.DRAIN_INTERVAL_TICKS) % 2;
      boolean useFirst = turn == 0 ? before.first() > 0 : before.second() <= 0;
      int first = before.first();
      int second = before.second();
      if (useFirst) {
         first = Math.max(0, first - amount);
      } else {
         second = Math.max(0, second - amount);
      }
      MaskFilters after = new MaskFilters(first, second);

      float weakBefore = before.weakest();
      float weakAfter = after.weakest();
      if (weakBefore >= Tuning.CRACK_STAGE_2 && weakAfter < Tuning.CRACK_STAGE_2) {
         playAt(player, ModSounds.CRACK_25, 1.0F, 1.0F);
      } else if (weakBefore >= Tuning.CRACK_STAGE_1 && weakAfter < Tuning.CRACK_STAGE_1) {
         playAt(player, ModSounds.CRACK_50, 1.0F, 1.0F);
      }

      boolean spent = after.count() < before.count();
      if (spent) {
         playAt(player, ModSounds.FILTER_RUNOUT, 1.0F, 1.0F);
         player.sendOverlayMessage(Component.literal(after.count() == 0 ? "Your filters are spent!" : "A filter is spent!"));
         player.setItemSlot(EquipmentSlot.HEAD, ModItems.maskWith(after));
      } else {
         head.set(ModItems.FILTERS, after);
      }
   }
}
