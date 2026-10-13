package com.kodari.breathless;

/**
 * Every number you might want to tweak lives here. Shared by client and server.
 * Times are in seconds unless the name says TICKS (20 ticks = 1 second).
 */
public final class Tuning {
   private Tuning() {
   }

   // ---- Exposure meter (0..100) ----
   /** Seconds to go from 0 to 100 while standing still below Y=0 with no working mask. */
   public static final float FILL_SECONDS = 90.0F;
   /** Fill speed multiplier while sprinting or in the air (jumping). */
   public static final float ACTIVE_MULTIPLIER = 2.0F;
   /** Below this Y (overworld only) the spores are in the air. */
   public static final double HAZARD_Y = 0.0D;

   // ---- Slowness that scales with exposure (meter value -> Slowness level) ----
   public static final float SLOW_TIER_1 = 25.0F;   // Slowness I
   public static final float SLOW_TIER_2 = 50.0F;   // Slowness II
   public static final float SLOW_TIER_3 = 75.0F;   // Slowness III
   public static final float SLOW_TIER_CRIT = 100.0F; // Slowness IV

   // ---- Critical state (meter at 100) ----
   /** Seconds for the screen to fade fully to black once critical. */
   public static final int FADE_SECONDS = 20;
   /** Suffocation damage (half-hearts) when the critical state starts, and how often (ticks). */
   public static final float DAMAGE_START = 1.0F;
   public static final int DAMAGE_INTERVAL_TICKS = 25;
   /** The damage climbs to this value the longer you stay critical, reaching it after DAMAGE_RAMP_SECONDS. */
   public static final float DAMAGE_MAX = 3.0F;
   public static final int DAMAGE_RAMP_SECONDS = 30;
   /** How long the wheeze sound sample is, in ticks. The sound is replayed this often while critical. */
   public static final int WHEEZE_TICKS = 70;

   // ---- Blur / haze pulses (client) ----
   /** The vision-haze pulses start at this exposure and get stronger toward 100. */
   public static final float BLUR_START = 40.0F;
   /** Ticks between pulses at 100% (longer at lower exposure). */
   public static final int BLUR_PERIOD_TICKS = 140;

   // ---- Cure ----
   /** How much an Instant Health potion lowers the meter (needs a working mask), by potion level: I, II, III or more. */
   public static final float PURGE_LEVEL_1 = 30.0F;
   public static final float PURGE_LEVEL_2 = 60.0F;
   public static final float PURGE_LEVEL_3 = 100.0F;
   /** If you heal first and put the mask on later, the cure is remembered this long. */
   public static final int ANTIDOTE_TICKS = 2400;

   // ---- Adrenaline trade-off (F13ND clears the spores) ----
   /** 8.0 = four hearts. */
   public static final float ADRENALINE_DAMAGE = 8.0F;
   /** Cooldown put on the Adrenaline Shot item after it is used as a cure. */
   public static final int ADRENALINE_COOLDOWN_TICKS = 100;

   // ---- F13ND pop-up sequence (all in ticks, counted from the injection) ----
   /** Master switch. false = no pop-ups, the cure still works. */
   public static final boolean POPUPS_ENABLED = true;
   /** How many cover pop-ups are thrown onto the bar, one every POPUP_INTERVAL ticks. */
   public static final int POPUP_COUNT = 12;
   public static final int POPUP_FIRST_TICK = 4;
   public static final int POPUP_INTERVAL = 3;
   /** Tick when all cover pop-ups vanish and the empty bar is revealed (and the red vignette stops). */
   public static final int POPUP_CLEAR_TICK = POPUP_FIRST_TICK + POPUP_COUNT * POPUP_INTERVAL + 14;
   /** Tick when the final "event run unsuccessful" pop-up appears (with a red flash). */
   public static final int POPUP_FINAL_TICK = POPUP_CLEAR_TICK + 22;
   /** Tick when the final pop-up (and the flash) is gone. The whole sequence ends here. */
   public static final int POPUP_END_TICK = POPUP_FINAL_TICK + 55;

   // ---- Gas mask / filter ----
   /** Filter life units. One unit is used every DRAIN_INTERVAL_TICKS * 2 ticks (the two filters take turns). */
   public static final int FILTER_MAX = 1200;
   public static final int DRAIN_INTERVAL_TICKS = 10;
   /** How many filters the mask needs to work (the mask holds 2). */
   public static final int FILTERS_REQUIRED = 2;
   /** Filter drains this many times faster while sprinting. */
   public static final int SPRINT_DRAIN = 2;
   /** Visor overlay cracks appear when remaining filter life drops below these fractions. */
   public static final float CRACK_STAGE_1 = 0.50F;
   public static final float CRACK_STAGE_2 = 0.25F;

   // ---- Zombies ----
   /** 90%: remaining filter life is rolled in 3%..25%. 10%: rolled in 25%..50%. */
   public static final float ZOMBIE_MASK_LOW_CHANCE = 0.90F;
   /** Chance a masked zombie drops its mask (with its filters inside). */
   public static final float ZOMBIE_DROP_MASK = 0.12F;
   /** Extra chance (after the mask roll) that only one loose filter drops. */
   public static final float ZOMBIE_DROP_FILTER = 0.12F;
   /** Filter life lost per point of damage when a zombie is hit by an attacker. */
   public static final float ZOMBIE_MASK_WEAR_PER_DAMAGE = 6.0F;
   /** Spore damage to an unmasked zombie, every 20 ticks. */
   public static final float ZOMBIE_SPORE_DAMAGE = 1.0F;

   // ---- Skeletons ----
   /** Detection range multiplier inside the spores (0.6 = 40% shorter). */
   public static final double SKELETON_RANGE_FACTOR = 0.6D;
   /** Extra arrow spread. Vanilla skeleton spread is about 6 to 14, so 7 is roughly 40% worse on Normal. */
   public static final double SKELETON_EXTRA_INACCURACY = 7.0D;

   // ---- Ambience (server plays an 8 second chunk this often while below Y=0) ----
   public static final int AMBIENCE_TICKS = 154;
}
