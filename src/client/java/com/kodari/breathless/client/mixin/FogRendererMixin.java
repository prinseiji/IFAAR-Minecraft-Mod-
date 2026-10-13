package com.kodari.breathless.client.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.fog.FogData;
import net.minecraft.client.renderer.fog.FogRenderer;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;

import com.kodari.breathless.Masks;
import com.kodari.breathless.Tuning;
import com.kodari.breathless.client.ClientState;

/**
 * The real fog: a thick blue gas that pools below Y=0. It eases in over about 12 blocks (smoothstep, no hard line),
 * gets denser and darker the deeper you go, and closes in as your exposure climbs.
 */
@Mixin(FogRenderer.class)
public abstract class FogRendererMixin {
   /** The gas starts to show this many blocks above Y=0, and is at full strength 12 blocks lower. */
   private static final double FADE_BLOCKS = 12.0D;
   private static final double BLEED_ABOVE = 1.5D;

   @Inject(method = "setupFog", at = @At("RETURN"))
   private void breathless$sporeFog(Camera camera, int renderDistance, DeltaTracker tracker, float partialTick, ClientLevel level, CallbackInfoReturnable<FogData> cir) {
      Minecraft mc = Minecraft.getInstance();
      if (mc.player == null || level.dimension() != Level.OVERWORLD) {
         return;
      }
      double y = mc.player.getY();
      double t = Mth.clamp((Tuning.HAZARD_Y + BLEED_ABOVE - y) / FADE_BLOCKS, 0.0D, 1.0D);
      if (t <= 0.0D) {
         return;
      }
      FogData fog = cir.getReturnValue();
      float blend = (float)(t * t * (3.0D - 2.0D * t)); // smoothstep
      float depth = (float)Mth.clamp(-y / 64.0D, 0.0D, 1.0D);

      // Unmasked: 22 blocks of sight right under the line, thinning with depth, and closing in with exposure.
      // A working mask clears the air a lot: you can see about twice as far.
      float end = Mth.lerp(Math.min(1.0F, ClientState.exposure / 100.0F), 22.0F, 9.0F);
      end *= 1.0F - 0.35F * depth;
      if (Masks.hasWorkingMask(mc.player)) {
         end = end * 1.9F + 8.0F;
      }
      fog.environmentalStart = Mth.lerp(blend, fog.environmentalStart, 0.0F);
      fog.environmentalEnd = Mth.lerp(blend, fog.environmentalEnd, end);
      fog.renderDistanceStart = Mth.lerp(blend, fog.renderDistanceStart, 0.0F);
      fog.renderDistanceEnd = Mth.lerp(blend, fog.renderDistanceEnd, end);
      // Blue, and a little darker the deeper you go.
      float dark = 1.0F - 0.4F * depth;
      fog.color.x = Mth.lerp(blend, fog.color.x, 0.10F * dark);
      fog.color.y = Mth.lerp(blend, fog.color.y, 0.46F * dark);
      fog.color.z = Mth.lerp(blend, fog.color.z, 0.70F * dark);
   }
}
