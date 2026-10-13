package com.kodari.breathless.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;

import com.kodari.breathless.Hazard;

/**
 * Tells the hazard system the moment Instant Health lands on a player (drunk or splashed).
 * Optional: if this ever fails to apply, Hazard still notices healing by watching health jumps.
 */
@Mixin(targets = "net.minecraft.world.effect.HealOrHarmMobEffect")
public abstract class HealMixin {
   @Inject(method = "applyInstantaneousEffect", at = @At("HEAD"))
   private void breathless$onInstantEffect(ServerLevel level, Entity source, Entity indirectSource, LivingEntity target, int amplifier, double scale, CallbackInfo ci) {
      if ((Object)this == MobEffects.INSTANT_HEALTH.value() && target instanceof ServerPlayer player) {
         Hazard.notifyInstantHealth(player, amplifier);
      }
   }
}
