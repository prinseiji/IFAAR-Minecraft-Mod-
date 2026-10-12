package com.kodari.combatinjuries;

import net.fabricmc.fabric.api.event.Event;
import net.fabricmc.fabric.api.event.EventFactory;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/**
 * Small public surface for add-on mods. Everything here is safe to call from other mods;
 * the rest of IFAAR is internal and may change.
 *
 * Injury ids: hemorrhage, tetanus, concussion, fracture, winded, asphyxia, hysteria.
 */
public final class CombatInjuriesApi {
   private CombatInjuriesApi() {
   }

   @FunctionalInterface
   public interface PlayerListener {
      void on(ServerPlayer player);
   }

   @FunctionalInterface
   public interface RushEndListener {
      void on(ServerPlayer player, boolean cashedOut);
   }

   /** Return true to take over the shot: IFAAR then does nothing else and the use succeeds. */
   @FunctionalInterface
   public interface BeforeShotListener {
      boolean handle(ServerPlayer player, ItemStack stack);
   }

   /**
    * Fired at the very top of the Adrenaline Shot branch in onUseItem: before the stun/lockout/cooldown checks, before the shot is
    * consumed, before any rush state changes and before the inject sound. If any listener returns true, IFAAR does nothing
    * else and returns InteractionResult.SUCCESS; the listener must consume the shot and play its own sound.
    * If no listener returns true, behaviour is exactly as before.
    */
   public static final Event<BeforeShotListener> BEFORE_SHOT = EventFactory.createArrayBacked(BeforeShotListener.class, listeners -> (player, stack) -> {
      for (BeforeShotListener l : listeners) {
         if (l.handle(player, stack)) {
            return true;
         }
      }

      return false;
   });

   /** Fired when a player takes the first shot of an adrenaline rush. */
   public static final Event<PlayerListener> RUSH_STARTED = EventFactory.createArrayBacked(PlayerListener.class, listeners -> player -> {
      for (PlayerListener l : listeners) {
         l.on(player);
      }
   });

   /** Fired when a rush ends without an overdose. cashedOut is true after a perfect 4-shot chain. */
   public static final Event<RushEndListener> RUSH_ENDED = EventFactory.createArrayBacked(RushEndListener.class, listeners -> (player, cashedOut) -> {
      for (RushEndListener l : listeners) {
         l.on(player, cashedOut);
      }
   });

   /** Fired when an overdose is triggered (before the lethal damage is dealt). */
   public static final Event<PlayerListener> OVERDOSED = EventFactory.createArrayBacked(PlayerListener.class, listeners -> player -> {
      for (PlayerListener l : listeners) {
         l.on(player);
      }
   });

   /** True if the player currently has the given injury (see the id list above). */
   public static boolean hasInjury(Player player, String id) {
      return CombatInjuries.hasInjury(player, id);
   }

   /** Shots taken in the current rush (0 when not rushing). */
   public static int rushShots(Player player) {
      return CombatInjuries.rushShotsOf(player);
   }

   public static boolean isStunned(Player player) {
      return CombatInjuries.isStunned(player);
   }
}
