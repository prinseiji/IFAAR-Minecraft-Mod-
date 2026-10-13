package com.kodari.breathless;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Random;
import java.util.Set;

import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.item.ItemStack;

/**
 * Everything about what lives below Y=0:
 * - only zombies and skeletons may spawn naturally
 * - zombies wear gas masks (low filter life) and drop them
 * - skeletons see less far and shoot worse, and their arrows glow so you can dodge them
 */
public final class MobRules {
   private MobRules() {
   }

   /** Vanilla skeleton detection range. We set absolute values (not multiply), so reloads can never stack the penalty. */
   private static final double SKELETON_NORMAL_RANGE = 16.0D;
   private static final Random RANDOM = new Random();

   private static final Set<Mob> ZOMBIES = Collections.newSetFromMap(new IdentityHashMap<>());
   private static final Set<Mob> SKELETONS = Collections.newSetFromMap(new IdentityHashMap<>());
   private static final List<Entity> ARROWS = new ArrayList<>();

   public static void init() {
      // 1) Natural spawning below Y=0: zombies and skeletons only.
      ServerEntityEvents.ALLOW_LOAD.register((entity, level, reason, loadedFromDisk) -> {
         if (loadedFromDisk || !(entity instanceof Monster)) {
            return true;
         }
         if (reason != EntitySpawnReason.NATURAL && reason != EntitySpawnReason.CHUNK_GENERATION) {
            return true;
         }
         if (!Masks.isHazardLevel(level) || entity.getY() >= Tuning.HAZARD_Y) {
            return true;
         }
         String type = Masks.typeId(entity);
         return type.equals("minecraft:zombie") || type.equals("minecraft:skeleton");
      });

      ServerEntityEvents.ENTITY_LOAD.register(MobRules::onLoad);
      ServerEntityEvents.ENTITY_UNLOAD.register((entity, level) -> {
         if (entity instanceof Mob mob) {
            ZOMBIES.remove(mob);
            SKELETONS.remove(mob);
         }
      });

      ServerTickEvents.END_SERVER_TICK.register(MobRules::tick);

      // 2) Zombie loot: mask / filter drops, and the mask wearing out when they are hit.
      ServerLivingEntityEvents.ALLOW_DEATH.register((entity, source, amount) -> {
         if (entity instanceof Mob mob && Masks.typeId(mob).equals("minecraft:zombie")) {
            dropMask(mob);
         }
         return true;
      });
      ServerLivingEntityEvents.AFTER_DAMAGE.register((entity, source, baseDamage, damageTaken, blocked) -> {
         if (source.getEntity() == null || !(entity instanceof Mob mob) || !Masks.typeId(mob).equals("minecraft:zombie")) {
            return;
         }
         ItemStack head = mob.getItemBySlot(EquipmentSlot.HEAD);
         if (!Masks.isMask(head)) {
            return;
         }
         MaskFilters filters = Masks.getFilters(head);
         int wear = Math.max(1, Math.round(damageTaken * Tuning.ZOMBIE_MASK_WEAR_PER_DAMAGE));
         MaskFilters worn = new MaskFilters(Math.max(0, filters.first() - wear), Math.max(0, filters.second() - wear));
         if (!worn.working()) {
            // The mask gives out: from now on this zombie breathes spores like everyone else.
            mob.setItemSlot(EquipmentSlot.HEAD, ItemStack.EMPTY);
            mob.level().playSound(null, mob.blockPosition(), SoundEvents.GLASS_BREAK, SoundSource.HOSTILE, 0.8F, 1.1F);
         } else {
            head.set(ModItems.FILTERS, worn);
         }
      });
   }

   // ---- loading ----

   private static void onLoad(Entity entity, ServerLevel level) {
      if (!Masks.isHazardLevel(level)) {
         return;
      }
      String type = Masks.typeId(entity);
      if (type.equals("minecraft:zombie") && entity instanceof Mob zombie) {
         ZOMBIES.add(zombie);
         if (!entity.isLoadedFromDisk() && entity.getY() < Tuning.HAZARD_Y && zombie.getItemBySlot(EquipmentSlot.HEAD).isEmpty()) {
            zombie.setItemSlot(EquipmentSlot.HEAD, rollZombieMask());
         }
      } else if (type.equals("minecraft:skeleton") && entity instanceof Mob skeleton) {
         SKELETONS.add(skeleton);
      } else if (type.equals("minecraft:arrow") && !entity.isLoadedFromDisk()) {
         onArrow(entity);
      }
   }

   /** Remaining life of one zombie filter: 90% of the time 3-25% left, 10% of the time 25-50%. */
   private static int rollFilterLife() {
      float remaining = RANDOM.nextFloat() < Tuning.ZOMBIE_MASK_LOW_CHANCE
         ? 0.03F + RANDOM.nextFloat() * 0.22F
         : 0.25F + RANDOM.nextFloat() * 0.25F;
      return Math.max(3, Math.round(remaining * Tuning.FILTER_MAX));
   }

   /** A gas mask with two nearly dead filters. */
   private static ItemStack rollZombieMask() {
      return ModItems.maskWith(new MaskFilters(rollFilterLife(), rollFilterLife()));
   }

   private static void dropMask(Mob zombie) {
      ItemStack head = zombie.getItemBySlot(EquipmentSlot.HEAD);
      if (!Masks.isMask(head)) {
         return;
      }
      ItemStack drop = ItemStack.EMPTY;
      float roll = RANDOM.nextFloat();
      MaskFilters filters = Masks.getFilters(head);
      if (roll < Tuning.ZOMBIE_DROP_MASK) {
         drop = head.copy();
      } else if (roll < Tuning.ZOMBIE_DROP_MASK + Tuning.ZOMBIE_DROP_FILTER && filters.count() > 0) {
         drop = ModItems.filterWith(filters.lastLife());
      }
      // Clear the slot so vanilla's own equipment drop cannot give a second copy.
      zombie.setItemSlot(EquipmentSlot.HEAD, ItemStack.EMPTY);
      if (!drop.isEmpty() && zombie.level() instanceof ServerLevel level) {
         level.addFreshEntity(new ItemEntity(level, zombie.getX(), zombie.getY() + 0.3D, zombie.getZ(), drop));
      }
   }

   // ---- skeleton arrows ----
   // (The worse aim itself is done by SkeletonAimMixin; here we only mark arrows for the trail.)

   private static void onArrow(Entity arrow) {
      for (Mob skeleton : SKELETONS) {
         if (skeleton.getY() < Tuning.HAZARD_Y && skeleton.distanceToSqr(arrow) < 2.25D) {
            ARROWS.add(arrow);
            return;
         }
      }
   }

   // ---- ticking ----

   private static void tick(MinecraftServer server) {
      // Faint white/cyan streak behind skeleton arrows (counter-play).
      Iterator<Entity> arrows = ARROWS.iterator();
      while (arrows.hasNext()) {
         Entity arrow = arrows.next();
         if (arrow.isRemoved() || arrow.getDeltaMovement().lengthSqr() < 0.0025D || !(arrow.level() instanceof ServerLevel level)) {
            arrows.remove();
            continue;
         }
         level.sendParticles(ParticleTypes.END_ROD, arrow.getX(), arrow.getY(), arrow.getZ(), 1, 0.02D, 0.02D, 0.02D, 0.0D);
         level.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, arrow.getX(), arrow.getY(), arrow.getZ(), 1, 0.03D, 0.03D, 0.03D, 0.0D);
      }

      if (server.getTickCount() % 20 != 0) {
         return;
      }

      // Zombies without a working mask are hurt by the spores.
      Iterator<Mob> zombies = ZOMBIES.iterator();
      while (zombies.hasNext()) {
         Mob zombie = zombies.next();
         if (zombie.isRemoved() || !(zombie.level() instanceof ServerLevel level)) {
            zombies.remove();
            continue;
         }
         if (zombie.isAlive() && Masks.isUnderground(zombie) && !Masks.hasWorkingMask(zombie)) {
            zombie.hurtServer(level, level.damageSources().drown(), Tuning.ZOMBIE_SPORE_DAMAGE);
         }
      }

      // Skeletons are immune to the spores but see less far while inside them.
      Iterator<Mob> skeletons = SKELETONS.iterator();
      while (skeletons.hasNext()) {
         Mob skeleton = skeletons.next();
         if (skeleton.isRemoved()) {
            skeletons.remove();
            continue;
         }
         AttributeInstance range = skeleton.getAttribute(Attributes.FOLLOW_RANGE);
         if (range == null) {
            continue;
         }
         double target = Masks.isUnderground(skeleton) ? SKELETON_NORMAL_RANGE * Tuning.SKELETON_RANGE_FACTOR : SKELETON_NORMAL_RANGE;
         if (Math.abs(range.getBaseValue() - target) > 0.01D) {
            range.setBaseValue(target);
         }
      }
   }
}
