package com.kodari.breathless;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/**
 * Right-click a filter while wearing a Gas Mask that has a free slot to slot it in.
 * (In an inventory you can also click the filter onto a mask, see FilterClicks.)
 */
public class FilterItem extends Item {
   public FilterItem(Item.Properties properties) {
      super(properties);
   }

   @Override
   public InteractionResult use(Level level, Player player, InteractionHand hand) {
      ItemStack filter = player.getItemInHand(hand);
      ItemStack head = player.getItemBySlot(EquipmentSlot.HEAD);
      if (!Masks.isMask(head) || Masks.getFilters(head).isFull()) {
         return InteractionResult.PASS;
      }
      if (!level.isClientSide()) {
         int life = Tuning.FILTER_MAX - filter.getDamageValue();
         player.setItemSlot(EquipmentSlot.HEAD, ModItems.maskWith(Masks.getFilters(head).with(life)));
         if (!player.isCreative()) {
            filter.shrink(1);
         }
         if (player instanceof ServerPlayer serverPlayer) {
            Masks.playAt(serverPlayer, ModSounds.FILTER_ON, 1.0F, 1.0F);
         }
      }
      return InteractionResult.SUCCESS;
   }
}
