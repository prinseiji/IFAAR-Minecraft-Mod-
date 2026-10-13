package com.kodari.breathless;

import com.mojang.brigadier.arguments.FloatArgumentType;
import com.mojang.serialization.Codec;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.ItemStack;

/**
 * IFAAR: Breathless. An expansion for IFAAR (mod id "combatinjuries").
 * Below Y=0 the air is full of sculk spores. See DEVELOPMENT.md.
 */
public class Breathless implements ModInitializer {
   public static final String MOD_ID = "breathless";

   /** The player's spore exposure, 0..100. Saved with the player. */
   public static final AttachmentType<Float> EXPOSURE = AttachmentRegistry.createPersistent(id("spore_exposure"), Codec.FLOAT);

   public static Identifier id(String path) {
      return Identifier.fromNamespaceAndPath(MOD_ID, path);
   }

   @Override
   public void onInitialize() {
      ModSounds.register();
      ModItems.register();
      PayloadTypeRegistry.clientboundPlay().register(ExposurePayload.TYPE, ExposurePayload.CODEC);
      PayloadTypeRegistry.clientboundPlay().register(FiendPayload.TYPE, FiendPayload.CODEC);
      FilterClicks.init();

      Hazard.init();
      MobRules.init();

      CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.COMBAT).register(entries -> {
         entries.accept(ModItems.GAS_MASK_EMPTY);
         entries.accept(ModItems.maskWith(new MaskFilters(Tuning.FILTER_MAX, Tuning.FILTER_MAX)));
         entries.accept(ModItems.GAS_MASK_FILTER);
      });

      registerCommands();
   }

   /**
    * Test commands:
    *   /breathless exposure <0-100>   set your meter
    *   /breathless give               an empty mask, a loaded mask, a nearly dead mask, and loose filters
    *   /breathless fiend              play the F13ND pop-up sequence (no damage, no reset)
    */
   private static void registerCommands() {
      CommandRegistrationCallback.EVENT.register((dispatcher, buildContext, selection) -> dispatcher.register(
         Commands.literal("breathless")
            .then(Commands.literal("exposure").then(Commands.argument("value", FloatArgumentType.floatArg(0.0F, 100.0F)).executes(context -> {
               ServerPlayer player = context.getSource().getPlayerOrException();
               float value = FloatArgumentType.getFloat(context, "value");
               Hazard.setExposure(player, value);
               Hazard.forceSync(player);
               context.getSource().sendSuccess(() -> Component.literal("Spore exposure set to " + value), false);
               return 1;
            })))
            .then(Commands.literal("give").executes(context -> {
               ServerPlayer player = context.getSource().getPlayerOrException();
               player.getInventory().add(new ItemStack(ModItems.GAS_MASK_EMPTY));
               player.getInventory().add(ModItems.maskWith(new MaskFilters(Tuning.FILTER_MAX, Tuning.FILTER_MAX)));
               player.getInventory().add(ModItems.maskWith(new MaskFilters(150, 150)));
               player.getInventory().add(ModItems.filterWith(Tuning.FILTER_MAX));
               player.getInventory().add(ModItems.filterWith(Tuning.FILTER_MAX));
               player.getInventory().add(ModItems.filterWith(150));
               context.getSource().sendSuccess(() -> Component.literal("Gave gas masks and filters."), false);
               return 1;
            }))
            .then(Commands.literal("fiend").executes(context -> {
               Hazard.testFiend(context.getSource().getPlayerOrException());
               context.getSource().sendSuccess(() -> Component.literal("F13ND sequence started (test)."), false);
               return 1;
            }))
      ));
   }
}
