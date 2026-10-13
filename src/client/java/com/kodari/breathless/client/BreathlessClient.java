package com.kodari.breathless.client;

import java.util.List;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

import com.kodari.breathless.Breathless;
import com.kodari.breathless.ExposurePayload;
import com.kodari.breathless.FiendPayload;
import com.kodari.breathless.MaskFilters;
import com.kodari.breathless.Masks;
import com.kodari.breathless.ModItems;
import com.kodari.breathless.Tuning;

public class BreathlessClient implements ClientModInitializer {
   @Override
   public void onInitializeClient() {
      ClientPlayNetworking.registerGlobalReceiver(ExposurePayload.TYPE, (payload, context) -> ClientState.exposure = payload.exposure());
      ClientPlayNetworking.registerGlobalReceiver(FiendPayload.TYPE, (payload, context) -> ClientState.startFiend(payload.seed()));
      ClientTickEvents.END_CLIENT_TICK.register(ClientState::tick);

      // Under the vanilla overlays: spore haze, vision pulses, floating spores, then the mask visor.
      HudElementRegistry.attachElementBefore(VanillaHudElements.MISC_OVERLAYS, Breathless.id("fog_and_mask"), SporeHud::renderFogAndMask);
      // The visible exposure gauge, with the F13ND pop-ups on top of it.
      HudElementRegistry.attachElementAfter(VanillaHudElements.INFO_BAR, Breathless.id("spore_meter"), SporeHud::renderMeter);
      // Critical state and the F13ND vignette: black fade, pulsing vignette, creeping veins. Drawn over the HUD, under chat.
      HudElementRegistry.attachElementBefore(VanillaHudElements.CHAT, Breathless.id("spore_critical"), SporeHud::renderCritical);

      // Percentage lines under the item name.
      ItemTooltipCallback.EVENT.register((stack, context, type, lines) -> addTooltip(stack, lines));
   }

   private static ChatFormatting colorFor(int percent) {
      if (percent > 50) {
         return ChatFormatting.GREEN;
      }
      return percent > 25 ? ChatFormatting.YELLOW : ChatFormatting.RED;
   }

   private static void addTooltip(ItemStack stack, List<Component> lines) {
      int at = Math.min(1, lines.size());
      if (stack.is(ModItems.GAS_MASK_FILTER)) {
         int percent = Masks.percent(Tuning.FILTER_MAX - stack.getDamageValue());
         lines.add(at, Component.translatable("breathless.tooltip.filter_life", percent).withStyle(colorFor(percent)));
      } else if (Masks.isMask(stack)) {
         MaskFilters filters = Masks.getFilters(stack);
         Component header = Component.translatable("breathless.tooltip.filters", filters.count(), 2)
            .withStyle(filters.working() ? ChatFormatting.GRAY : ChatFormatting.RED);
         lines.add(at++, header);
         for (int slot = 0; slot < 2; slot++) {
            int life = slot == 0 ? filters.first() : filters.second();
            if (life > 0) {
               int percent = Masks.percent(life);
               lines.add(at++, Component.translatable("breathless.tooltip.filter_slot", slot + 1, percent).withStyle(colorFor(percent)));
            } else {
               lines.add(at++, Component.translatable("breathless.tooltip.filter_empty", slot + 1).withStyle(ChatFormatting.DARK_GRAY));
            }
         }
         lines.add(at++, Component.translatable("breathless.tooltip.insert_hint").withStyle(ChatFormatting.DARK_GRAY));
         if (filters.count() > 0) {
            lines.add(at, Component.translatable("breathless.tooltip.remove_hint").withStyle(ChatFormatting.DARK_GRAY));
         }
      }
   }
}
