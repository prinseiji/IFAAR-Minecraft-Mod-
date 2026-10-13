package com.kodari.breathless.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.monster.skeleton.AbstractSkeleton;

import com.kodari.breathless.Masks;
import com.kodari.breathless.Tuning;

/** Skeletons shoot worse while standing in the spores: adds to the arrow spread. */
@Mixin(AbstractSkeleton.class)
public abstract class SkeletonAimMixin {
   @ModifyArg(
      method = "performRangedAttack(Lnet/minecraft/world/entity/LivingEntity;F)V",
      at = @At(
         value = "INVOKE",
         target = "Lnet/minecraft/world/entity/projectile/Projectile;spawnProjectileUsingShoot(Lnet/minecraft/world/entity/projectile/Projectile;Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/item/ItemStack;DDDFF)Lnet/minecraft/world/entity/projectile/Projectile;"
      ),
      index = 7
   )
   private float breathless$worseAim(float inaccuracy) {
      if (Masks.isUnderground((Entity)(Object)this)) {
         return inaccuracy + (float)Tuning.SKELETON_EXTRA_INACCURACY;
      }
      return inaccuracy;
   }
}
