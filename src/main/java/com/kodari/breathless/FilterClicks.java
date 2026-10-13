package com.kodari.breathless;

import net.fabricmc.fabric.api.item.v1.ItemClickBehaviorCallback;
import net.fabricmc.fabric.api.util.EventResult;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.ClickAction;
import net.minecraft.world.item.ItemStack;

/**
 * Bundle-style inventory handling for the Gas Mask:
 * - hold a filter on the cursor and click a mask: the filter goes inside
 * - empty cursor, right-click a mask that has filters: the last filter comes out onto the cursor
 * Both sides run this, so it only touches things both sides can compute the same way. Sounds are server only.
 */
public final class FilterClicks {
   private FilterClicks() {
   }

   public static void init() {
      ItemClickBehaviorCallback.EVENT.register((hovered, slot, cursor, cursorAccess, action, player) -> {
         if (!Masks.isMask(hovered)) {
            return EventResult.PASS;
         }
         MaskFilters filters = Masks.getFilters(hovered);

         // Insert
         if (cursor.is(ModItems.GAS_MASK_FILTER)) {
            if (filters.isFull()) {
               return EventResult.DENY;
            }
            int life = Tuning.FILTER_MAX - cursor.getDamageValue();
            slot.set(ModItems.maskWith(filters.with(life)));
            ItemStack rest = cursor.copy();
            rest.shrink(1);
            cursorAccess.set(rest);
            if (player instanceof ServerPlayer serverPlayer) {
               Masks.playAt(serverPlayer, ModSounds.FILTER_ON, 1.0F, 1.0F);
            }
            return EventResult.DENY;
         }

         // Take out
         if (cursor.isEmpty() && action == ClickAction.SECONDARY && filters.count() > 0) {
            ItemStack out = ModItems.filterWith(filters.lastLife());
            slot.set(ModItems.maskWith(filters.withoutLast()));
            cursorAccess.set(out);
            if (player instanceof ServerPlayer serverPlayer) {
               Masks.playAt(serverPlayer, ModSounds.FILTER_OFF, 1.0F, 1.0F);
            }
            return EventResult.DENY;
         }
         return EventResult.PASS;
      });
   }
}
