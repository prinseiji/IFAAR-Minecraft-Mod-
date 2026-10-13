package com.kodari.breathless;

import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.equipment.Equippable;

public final class ModItems {
   private ModItems() {
   }

   /** What is inside a mask (two filter slots). Absent = no filters. */
   public static DataComponentType<MaskFilters> FILTERS;

   /** Worn mask with at least one filter inside. Same name as the empty one, different inventory icon. */
   public static Item GAS_MASK;
   /** Worn mask with no filters. Does not protect. */
   public static Item GAS_MASK_EMPTY;
   /** Loose filter. Click it onto a mask (or right-click it while wearing a mask) to insert it. */
   public static Item GAS_MASK_FILTER;

   private static ResourceKey<Item> key(String path) {
      return ResourceKey.create(Registries.ITEM, Breathless.id(path));
   }

   /**
    * No equipment asset on purpose: with none set, the game draws the item's own 3D model on the head
    * (the "head" display transform in models/item/gas_mask_worn.json), like a carved pumpkin.
    */
   private static Equippable headEquippable() {
      return Equippable.builder(EquipmentSlot.HEAD).setDamageOnHurt(false).build();
   }

   public static void register() {
      FILTERS = Registry.register(
         BuiltInRegistries.DATA_COMPONENT_TYPE,
         Breathless.id("filters"),
         DataComponentType.<MaskFilters>builder().persistent(MaskFilters.CODEC).networkSynchronized(MaskFilters.STREAM_CODEC).build()
      );

      ResourceKey<Item> maskKey = key("gas_mask");
      GAS_MASK = Registry.register(
         BuiltInRegistries.ITEM,
         maskKey,
         new Item(new Item.Properties().setId(maskKey).stacksTo(1).component(DataComponents.EQUIPPABLE, headEquippable()))
      );

      ResourceKey<Item> emptyKey = key("gas_mask_empty");
      GAS_MASK_EMPTY = Registry.register(
         BuiltInRegistries.ITEM,
         emptyKey,
         new Item(new Item.Properties().setId(emptyKey).stacksTo(1).component(DataComponents.EQUIPPABLE, headEquippable()))
      );

      ResourceKey<Item> filterKey = key("gas_mask_filter");
      GAS_MASK_FILTER = Registry.register(
         BuiltInRegistries.ITEM,
         filterKey,
         new FilterItem(new Item.Properties().setId(filterKey).durability(Tuning.FILTER_MAX))
      );
   }

   /** A mask holding these filters (the right item and the component set). */
   public static ItemStack maskWith(MaskFilters filters) {
      if (filters.count() <= 0) {
         return new ItemStack(GAS_MASK_EMPTY);
      }
      ItemStack stack = new ItemStack(GAS_MASK);
      stack.set(FILTERS, filters);
      return stack;
   }

   /** A loose filter with this much life left. */
   public static ItemStack filterWith(int life) {
      ItemStack stack = new ItemStack(GAS_MASK_FILTER);
      stack.setDamageValue(Math.max(0, Tuning.FILTER_MAX - Math.max(1, life)));
      return stack;
   }
}
