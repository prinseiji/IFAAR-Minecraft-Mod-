package com.kodari.breathless;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.sounds.SoundEvent;

/** Every sound event of the mod. The files live in assets/breathless/sounds and are listed in sounds.json. */
public final class ModSounds {
   private ModSounds() {
   }

   public static SoundEvent WHEEZE;
   public static SoundEvent MASK_ON;
   public static SoundEvent FILTER_ON;
   public static SoundEvent FILTER_OFF;
   public static SoundEvent FILTER_RUNOUT;
   public static SoundEvent CRACK_50;
   public static SoundEvent CRACK_25;
   public static SoundEvent TENDRILS;
   public static SoundEvent SPORE_DAMAGE;
   public static SoundEvent SIGH_RELIEF;
   public static SoundEvent AMBIENCE_MUFFLED;
   public static SoundEvent AMBIENCE_OPEN;
   public static SoundEvent POPUP_SPAM;
   public static SoundEvent POPUP_FINAL;

   private static SoundEvent reg(String name) {
      return Registry.register(BuiltInRegistries.SOUND_EVENT, Breathless.id(name), SoundEvent.createVariableRangeEvent(Breathless.id(name)));
   }

   public static void register() {
      WHEEZE = reg("wheeze");
      MASK_ON = reg("mask_on");
      FILTER_ON = reg("filter_on");
      FILTER_OFF = reg("filter_off");
      FILTER_RUNOUT = reg("filter_runout");
      CRACK_50 = reg("crack_50");
      CRACK_25 = reg("crack_25");
      TENDRILS = reg("tendrils");
      SPORE_DAMAGE = reg("spore_damage");
      SIGH_RELIEF = reg("sigh_relief");
      AMBIENCE_MUFFLED = reg("ambience_muffled");
      AMBIENCE_OPEN = reg("ambience_open");
      POPUP_SPAM = reg("popup_spam");
      POPUP_FINAL = reg("popup_final");
   }
}
