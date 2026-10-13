package com.kodari.breathless;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionContents;

import com.kodari.combatinjuries.CombatInjuries;
import com.kodari.combatinjuries.CombatInjuriesApi;

/** Server side of the spore hazard: the exposure meter, the critical state, the cure and the F13ND purge. */
public final class Hazard {
   private Hazard() {
   }

   /** Per-player scratch values that do not need to survive a restart. */
   private static final class Rt {
      int antidoteTicks;
      int healCooldown;
      int damageTimer;
      int wheezeTimer;
      int critTicks;
      boolean wasCritical;
      float lastHealth = -1.0F;
      float lastSent = -1.0F;
      int drinkLevel = -1;
      int pendingLevel = -1;
      int drinkRemaining;
      int antidoteLevel;
      // ambience / warnings
      int ambienceTimer;
      boolean ambienceMuffled;
      int slowTimer;
      // F13ND pop-up sequence (-1 = not running); the server only schedules the sounds, the client draws it
      int fiendTick = -1;
   }

   private static final Map<UUID, Rt> RUNTIME = new HashMap<>();

   public static void init() {
      ServerTickEvents.END_SERVER_TICK.register(Hazard::tick);
      ServerPlayerEvents.JOIN.register(player -> RUNTIME.remove(player.getUUID()));
      ServerPlayerEvents.LEAVE.register(player -> RUNTIME.remove(player.getUUID()));
      ServerPlayerEvents.AFTER_RESPAWN.register((oldPlayer, newPlayer, alive) -> {
         RUNTIME.remove(newPlayer.getUUID());
         newPlayer.removeAttached(Breathless.EXPOSURE);
      });
      // "Putting the mask on" sound.
      ServerEntityEvents.EQUIPMENT_CHANGE.register((entity, slot, previous, current) -> {
         if (slot == EquipmentSlot.HEAD && entity instanceof ServerPlayer player && Masks.isMask(current) && !Masks.isMask(previous)) {
            Masks.playAt(player, ModSounds.MASK_ON, 1.0F, 1.0F);
         }
      });
      // IFAAR hook: an Adrenaline Shot while critical is a one-time cure (no rush, no minigame).
      CombatInjuriesApi.BEFORE_SHOT.register(Hazard::onBeforeShot);
   }

   // ---- meter access ----

   public static float getExposure(Player player) {
      Float value = player.getAttached(Breathless.EXPOSURE);
      return value == null ? 0.0F : value;
   }

   public static void setExposure(ServerPlayer player, float value) {
      player.setAttached(Breathless.EXPOSURE, Math.max(0.0F, Math.min(100.0F, value)));
   }

   public static boolean isCritical(Player player) {
      return getExposure(player) >= 100.0F - 0.001F;
   }

   private static void sync(ServerPlayer player, Rt rt, float value) {
      ServerPlayNetworking.send(player, new ExposurePayload(value));
      rt.lastSent = value;
   }

   /** Called by the /breathless command so the HUD updates right away. */
   public static void forceSync(ServerPlayer player) {
      Rt rt = RUNTIME.computeIfAbsent(player.getUUID(), k -> new Rt());
      sync(player, rt, getExposure(player));
   }

   /** Called by HealMixin the moment an Instant Health effect lands on a player (drunk or splashed). */
   public static void notifyInstantHealth(ServerPlayer player, int amplifier) {
      Rt rt = RUNTIME.computeIfAbsent(player.getUUID(), k -> new Rt());
      rt.pendingLevel = Math.max(rt.pendingLevel, amplifier);
   }

   /** Called by the /breathless command to test the F13ND sequence without needing a shot. */
   public static void testFiend(ServerPlayer player) {
      startFiend(player, RUNTIME.computeIfAbsent(player.getUUID(), k -> new Rt()), false);
   }

   // ---- main tick ----

   private static void tick(MinecraftServer server) {
      for (ServerPlayer player : server.getPlayerList().getPlayers()) {
         tickPlayer(player);
      }
   }

   private static void tickPlayer(ServerPlayer player) {
      if (!player.isAlive()) {
         return;
      }
      Rt rt = RUNTIME.computeIfAbsent(player.getUUID(), k -> new Rt());
      ServerLevel level = (ServerLevel)player.level();
      boolean exempt = player.isCreative() || player.isSpectator();
      boolean underground = Masks.isUnderground(player);
      boolean maskOk = Masks.hasWorkingMask(player);
      float start = getExposure(player);
      float exposure = start;

      tickFiendSounds(player, rt);

      // 1) Did the player just get healed by an Instant Health potion?
      if (rt.healCooldown > 0) {
         rt.healCooldown--;
      }
      int healLevel = -1;
      float health = player.getHealth();
      if (rt.lastHealth >= 0.0F && health - rt.lastHealth >= 1.99F) {
         healLevel = 0;
      }
      rt.lastHealth = health;
      if (player.isUsingItem()) {
         rt.drinkLevel = healingLevel(player.getUseItem());
         rt.drinkRemaining = player.getUseItemRemainingTicks();
      } else {
         if (rt.drinkLevel >= 0 && rt.drinkRemaining <= 2) {
            healLevel = Math.max(healLevel, rt.drinkLevel);
         }
         rt.drinkLevel = -1;
      }
      if (rt.pendingLevel >= 0) {
         healLevel = Math.max(healLevel, rt.pendingLevel);
         rt.pendingLevel = -1;
      }
      if (healLevel >= 0 && rt.healCooldown <= 0) {
         rt.healCooldown = 10;
         exposure = onHealed(player, rt, exposure, maskOk, healLevel);
      }
      if (rt.antidoteTicks > 0) {
         rt.antidoteTicks--;
      }

      // 2) Rising (only below Y=0 with no working mask)
      if (!exempt && underground && !maskOk && exposure < 100.0F) {
         float rate = 100.0F / (Tuning.FILL_SECONDS * 20.0F);
         if (player.isSprinting() || !player.onGround()) {
            rate *= Tuning.ACTIVE_MULTIPLIER;
         }
         exposure = Math.min(100.0F, exposure + rate);
      }

      // 3) The filters wear down while the mask is doing its job
      if (!exempt && underground && maskOk && player.tickCount % Tuning.DRAIN_INTERVAL_TICKS == 0) {
         Masks.drainFilter(player, player.isSprinting() ? Tuning.SPRINT_DRAIN : 1);
         maskOk = Masks.hasWorkingMask(player);
      }

      // 4) Critical state (the sounds also play in creative so you can test them; the damage does not)
      boolean critical = exposure >= 100.0F - 0.001F && !player.isSpectator();
      if (critical) {
         if (maskOk && rt.antidoteTicks > 0) {
            // Mask is on and the cure is already in the blood: purge now.
            exposure = Math.max(0.0F, exposure - purgeFor(rt.antidoteLevel));
            rt.antidoteTicks = 0;
            Masks.playAt(player, ModSounds.SIGH_RELIEF, 1.0F, 1.0F);
            player.sendOverlayMessage(Component.literal("The spores loosen their grip."));
         } else {
            if (!rt.wasCritical) {
               Masks.playAt(player, ModSounds.TENDRILS, 1.0F, 1.0F);
            }
            rt.critTicks++;
            if (++rt.damageTimer >= Tuning.DAMAGE_INTERVAL_TICKS) {
               rt.damageTimer = 0;
               float ramp = Math.min(1.0F, rt.critTicks / (float)(Tuning.DAMAGE_RAMP_SECONDS * 20));
               float damage = Tuning.DAMAGE_START + (Tuning.DAMAGE_MAX - Tuning.DAMAGE_START) * ramp;
               Masks.playAt(player, ModSounds.SPORE_DAMAGE, 1.0F, 1.0F);
               if (!exempt) {
                  player.hurtServer(level, level.damageSources().drown(), damage);
               }
            }
            if (--rt.wheezeTimer <= 0) {
               rt.wheezeTimer = Tuning.WHEEZE_TICKS;
               Masks.playAt(player, ModSounds.WHEEZE, 1.0F, 1.0F);
            }
         }
      } else {
         rt.damageTimer = 0;
         rt.wheezeTimer = 0;
         rt.critTicks = 0;
      }
      rt.wasCritical = exposure >= 100.0F - 0.001F && !player.isSpectator();

      // 5) Slowness that gets worse with exposure
      if (!exempt && ++rt.slowTimer >= 15) {
         rt.slowTimer = 0;
         int amplifier = slowLevel(exposure);
         if (amplifier >= 0) {
            player.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 40, amplifier, false, false));
         }
      }

      // 6) Ambience while below Y=0 (muffled behind a working mask, open air without one)
      if (!player.isSpectator() && underground) {
         if (--rt.ambienceTimer <= 0 || rt.ambienceMuffled != maskOk) {
            rt.ambienceTimer = Tuning.AMBIENCE_TICKS;
            rt.ambienceMuffled = maskOk;
            level.playSound(null, player.blockPosition(), maskOk ? ModSounds.AMBIENCE_MUFFLED : ModSounds.AMBIENCE_OPEN, SoundSource.AMBIENT, 0.8F, 1.0F);
         }
      } else {
         rt.ambienceTimer = 0;
      }

      // 7) Save + tell the client
      if (exposure != start) {
         setExposure(player, exposure);
      }
      float shown = getExposure(player);
      if (rt.lastSent < 0.0F || Math.abs(shown - rt.lastSent) >= 0.25F || (player.tickCount % 20 == 0 && shown != rt.lastSent)) {
         sync(player, rt, shown);
      }
   }

   private static int slowLevel(float exposure) {
      if (exposure >= Tuning.SLOW_TIER_CRIT - 0.001F) {
         return 3;
      }
      if (exposure >= Tuning.SLOW_TIER_3) {
         return 2;
      }
      if (exposure >= Tuning.SLOW_TIER_2) {
         return 1;
      }
      if (exposure >= Tuning.SLOW_TIER_1) {
         return 0;
      }
      return -1;
   }

   // ---- healing / cure ----

   private static String effectPath(MobEffectInstance instance) {
      return instance.getEffect().unwrapKey().map(key -> key.identifier().getPath()).orElse("");
   }

   /** The Instant Health level of a potion in hand (0 = I, 1 = II ...), or -1 if it is not an Instant Health potion. */
   private static int healingLevel(ItemStack stack) {
      if (!stack.is(Items.POTION)) {
         return -1;
      }
      PotionContents contents = stack.get(DataComponents.POTION_CONTENTS);
      if (contents == null) {
         return -1;
      }
      int level = -1;
      for (MobEffectInstance instance : contents.getAllEffects()) {
         if ("instant_health".equals(effectPath(instance))) {
            level = Math.max(level, instance.getAmplifier());
         }
      }
      return level;
   }

   private static float purgeFor(int level) {
      if (level <= 0) {
         return Tuning.PURGE_LEVEL_1;
      }
      return level == 1 ? Tuning.PURGE_LEVEL_2 : Tuning.PURGE_LEVEL_3;
   }

   /**
    * Both halves are needed to cure the critical state: a working mask AND a healing event.
    * Mask first, then heal: purge right now. Heal first, then mask: the cure is remembered for a while.
    * A stronger potion purges more (Tuning.PURGE_LEVEL_*).
    */
   private static float onHealed(ServerPlayer player, Rt rt, float exposure, boolean maskOk, int level) {
      if (maskOk) {
         if (exposure > 0.0F) {
            Masks.playAt(player, ModSounds.SIGH_RELIEF, 1.0F, 1.0F);
            player.sendOverlayMessage(Component.literal("The spores loosen their grip."));
         }
         rt.antidoteTicks = 0;
         return Math.max(0.0F, exposure - purgeFor(level));
      }
      if (exposure >= 100.0F - 0.001F) {
         rt.antidoteTicks = Tuning.ANTIDOTE_TICKS;
         rt.antidoteLevel = level;
         player.sendOverlayMessage(Component.literal("The cure is in your blood. Get a gas mask on!"));
      }
      return exposure;
   }

   // ---- F13ND: the adrenaline purge ----

   /**
    * IFAAR asks us first, before it does anything with the shot. While the meter is above 0 we take the shot over:
    * it is used up, F13ND burns the spores out (meter to 0, four hearts of damage) and NO adrenaline rush starts.
    * In every other situation we say "not handled" and IFAAR behaves exactly as before.
    */
   private static boolean onBeforeShot(ServerPlayer player, ItemStack stack) {
      // The IFAAR rush minigame is only for a completely clean meter. Any exposure at all and the shot clears it instead.
      if (getExposure(player) <= 0.001F) {
         return false;
      }
      if (CombatInjuriesApi.rushShots(player) > 0 || CombatInjuriesApi.isStunned(player) || player.getCooldowns().isOnCooldown(stack)) {
         return false;
      }
      ItemStack cooldownStack = stack.copy();
      if (!player.isCreative()) {
         stack.consume(1, player);
      }
      player.getCooldowns().addCooldown(cooldownStack, Tuning.ADRENALINE_COOLDOWN_TICKS);
      ServerLevel level = (ServerLevel)player.level();
      level.playSound(null, player.getX(), player.getY(), player.getZ(), CombatInjuries.ADRENALINE_INJECT, SoundSource.PLAYERS, 1.0F, 1.0F);
      startFiend(player, RUNTIME.computeIfAbsent(player.getUUID(), k -> new Rt()), true);
      return true;
   }

   private static void startFiend(ServerPlayer player, Rt rt, boolean realCure) {
      // The pop-up packet goes first so the client can freeze the bar at its old value before the reset arrives.
      if (Tuning.POPUPS_ENABLED) {
         rt.fiendTick = 0;
         ServerPlayNetworking.send(player, new FiendPayload(player.getRandom().nextInt()));
      }
      if (realCure) {
         setExposure(player, 0.0F);
         rt.antidoteTicks = 0;
         rt.damageTimer = 0;
         rt.wheezeTimer = 0;
         rt.critTicks = 0;
         rt.wasCritical = false;
         sync(player, rt, 0.0F);
         if (!player.isCreative() && !player.isSpectator()) {
            ServerLevel level = (ServerLevel)player.level();
            float before = player.getHealth();
            player.hurtServer(level, level.damageSources().genericKill(), Tuning.ADRENALINE_DAMAGE);
            // Safety net: if something swallowed the hit, take the health directly (never to the point of killing).
            if (player.isAlive() && player.getHealth() >= before - 0.01F && before - Tuning.ADRENALINE_DAMAGE > 0.0F) {
               player.setHealth(before - Tuning.ADRENALINE_DAMAGE);
            }
            player.sendOverlayMessage(Component.literal("The spores are gone, and it cost you."));
         }
      }
   }

   /** The server only plays the pop-up sounds on the same schedule the client draws them on. */
   private static void tickFiendSounds(ServerPlayer player, Rt rt) {
      if (rt.fiendTick < 0) {
         return;
      }
      int t = rt.fiendTick++;
      int sinceFirst = t - Tuning.POPUP_FIRST_TICK;
      if (sinceFirst >= 0 && sinceFirst % Tuning.POPUP_INTERVAL == 0 && sinceFirst / Tuning.POPUP_INTERVAL < Tuning.POPUP_COUNT) {
         Masks.playAt(player, ModSounds.POPUP_SPAM, 1.0F, 0.9F + player.getRandom().nextFloat() * 0.3F);
      }
      if (t == Tuning.POPUP_FINAL_TICK) {
         Masks.playAt(player, ModSounds.POPUP_FINAL, 1.0F, 1.0F);
      }
      if (t >= Tuning.POPUP_END_TICK) {
         rt.fiendTick = -1;
      }
   }
}
