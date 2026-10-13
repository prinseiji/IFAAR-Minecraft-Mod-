package com.kodari.breathless.client;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

import com.kodari.breathless.Breathless;
import com.kodari.breathless.Masks;
import com.kodari.breathless.Tuning;

/** All the screen drawing: spore haze, vision pulses, mask visor, exposure gauge, F13ND pop-ups, and the critical-state effects. */
public final class SporeHud {
   private SporeHud() {
   }

   private static final Identifier MASK_OVERLAY = Breathless.id("textures/misc/gas_mask_overlay.png");
   private static final Identifier MASK_CRACKS_1 = Breathless.id("textures/misc/gas_mask_cracks_1.png");
   private static final Identifier MASK_CRACKS_2 = Breathless.id("textures/misc/gas_mask_cracks_2.png");
   private static final Identifier GAUGE = Breathless.id("textures/gui/gauge.png");
   private static final Identifier[] POPUPS = new Identifier[6];
   private static final Identifier POPUP_FINAL = Breathless.id("textures/gui/popup_final.png");

   static {
      for (int i = 0; i < POPUPS.length; i++) {
         POPUPS[i] = Breathless.id("textures/gui/popup_" + i + ".png");
      }
   }

   /** Pixel widths of the pop-up pictures (all are 83 tall). Same order as POPUPS. */
   private static final int[] POPUP_W = {331, 195, 185, 394, 150, 202};
   private static final int POPUP_FINAL_W = 184;
   private static final int POPUP_H = 83;
   private static final float POPUP_SCALE = 0.5F;

   // Gauge picture is 94x404; the dark tube inside it starts here.
   private static final int GAUGE_W = 94;
   private static final int GAUGE_H = 404;
   private static final int TUBE_X = 30;
   private static final int TUBE_Y = 78;
   private static final int TUBE_W = 33;
   private static final int TUBE_H = 294;
   private static final float GAUGE_SCALE = 0.36F;

   private static int argb(int alpha, int rgb) {
      return (Math.max(0, Math.min(255, alpha)) << 24) | (rgb & 0xFFFFFF);
   }

   private static float smooth(float t) {
      return t * t * (3.0F - 2.0F * t);
   }

   private static float bump(float x, float center, float width) {
      return Math.max(0.0F, 1.0F - Math.abs(x - center) / width);
   }

   // ---- spore haze, vision pulses, floating spores, mask visor ----

   static void renderFogAndMask(GuiGraphicsExtractor g, DeltaTracker tracker) {
      Minecraft mc = Minecraft.getInstance();
      LocalPlayer player = mc.player;
      if (player == null || mc.level == null) {
         return;
      }
      int w = g.guiWidth();
      int h = g.guiHeight();
      float exposure = ClientState.shownExposure();

      // Light blue haze on top of the real fog (FogRendererMixin). It is also the fallback if that mixin cannot apply.
      double y = player.getY();
      if (y < Tuning.HAZARD_Y && mc.level.dimension() == Level.OVERWORLD) {
         float depth = (float)Math.min(1.0D, -y / 8.0D);
         float deep = (float)Math.min(1.0D, -y / 64.0D);
         int alpha = (int)(depth * (18.0F + 16.0F * deep));
         g.fill(0, 0, w, h, argb(alpha, 0x2A8CC0));
      }

      // Punishment: now and then the vision goes milky, closing in from the edges. Faster and stronger as exposure climbs.
      if (exposure >= Tuning.BLUR_START) {
         float strength = Math.min(1.0F, (exposure - Tuning.BLUR_START) / (100.0F - Tuning.BLUR_START));
         int period = (int)(Tuning.BLUR_PERIOD_TICKS * (1.8F - 0.8F * strength));
         float phase = (ClientState.clock % period) / (float)period;
         float pulse = phase < 0.4F ? (float)Math.sin(Math.PI * phase / 0.4F) : 0.0F;
         if (pulse > 0.0F) {
            float amount = pulse * (0.35F + 0.65F * strength);
            g.fill(0, 0, w, h, argb((int)(amount * 70.0F), 0xB9E6F2));
            drawVignette(g, w, h, 0xCFEFF6, amount * 0.85F, 0.5F);
         }
      }

      // Mask visor, first person only (like the pumpkin). Cracks sit at the edges.
      ItemStack head = player.getItemBySlot(EquipmentSlot.HEAD);
      if (Masks.isMask(head) && mc.options.getCameraType().isFirstPerson()) {
         g.blit(RenderPipelines.GUI_TEXTURED, MASK_OVERLAY, 0, 0, 0.0F, 0.0F, w, h, w, h);
         int stage = ClientState.stageOf(head);
         if (stage >= 1) {
            drawCrack(g, MASK_CRACKS_1, w, h, ClientState.crackAnim > 0 && ClientState.crackAnimStage == 1);
         }
         if (stage >= 2) {
            drawCrack(g, MASK_CRACKS_2, w, h, ClientState.crackAnim > 0 && ClientState.crackAnimStage == 2);
         }
         if (ClientState.crackAnim > 0) {
            // A quick white flash as the glass gives.
            g.fill(0, 0, w, h, argb(ClientState.crackAnim * 9, 0xE8F8FF));
         }
      }
   }

   /** A crack layer. While it is new it slams in: starts oversized and a little shaky, then settles. */
   private static void drawCrack(GuiGraphicsExtractor g, Identifier texture, int w, int h, boolean fresh) {
      if (!fresh) {
         g.blit(RenderPipelines.GUI_TEXTURED, texture, 0, 0, 0.0F, 0.0F, w, h, w, h);
         return;
      }
      float k = ClientState.crackAnim / (float)ClientState.CRACK_ANIM_TICKS;
      float scale = 1.0F + 0.14F * k;
      int dw = (int)(w * scale);
      int dh = (int)(h * scale);
      int shake = (int)(4.0F * k) * ((ClientState.clock % 2 == 0) ? 1 : -1);
      g.blit(RenderPipelines.GUI_TEXTURED, texture, (w - dw) / 2 + shake, (h - dh) / 2 - shake, 0.0F, 0.0F, dw, dh, dw, dh);
   }

   // ---- exposure gauge + F13ND pop-ups ----

   static void renderMeter(GuiGraphicsExtractor g, DeltaTracker tracker) {
      Minecraft mc = Minecraft.getInstance();
      LocalPlayer player = mc.player;
      if (player == null || mc.level == null) {
         return;
      }
      float exposure = ClientState.shownExposure();
      boolean fiend = ClientState.fiendTick >= 0;
      if (!fiend && exposure < 0.5F && !Masks.isUnderground(player)) {
         return;
      }
      int gw = Math.round(GAUGE_W * GAUGE_SCALE);
      int gh = Math.round(GAUGE_H * GAUGE_SCALE);
      int x = 6;
      int y = g.guiHeight() / 2 - gh / 2;

      g.blit(RenderPipelines.GUI_TEXTURED, GAUGE, x, y, 0.0F, 0.0F, gw, gh, gw, gh);

      int tx = x + Math.round(TUBE_X * GAUGE_SCALE);
      int ty = y + Math.round(TUBE_Y * GAUGE_SCALE);
      int tw = Math.round(TUBE_W * GAUGE_SCALE);
      int th = Math.round(TUBE_H * GAUGE_SCALE);
      int filled = Math.round(th * Math.min(100.0F, exposure) / 100.0F);
      if (filled > 0) {
         // cyan when light, deep blue when heavy; flashes at 100
         float f = Math.min(1.0F, exposure / 100.0F);
         int r = (int)(0x3F + (0x14 - 0x3F) * f);
         int gr = (int)(0xD0 + (0x48 - 0xD0) * f);
         int b = (int)(0xD8 + (0x7A - 0xD8) * f);
         int color = 0xE6000000 | (r << 16) | (gr << 8) | b;
         if (exposure >= 99.99F && (mc.player.tickCount / 5) % 2 == 0) {
            color = 0xE6E8FFFF;
         }
         g.fill(tx, ty + th - filled, tx + tw, ty + th, color);
      }
      String label = Math.round(exposure) + "%";
      g.text(mc.font, label, x + gw / 2 - mc.font.width(label) / 2, y + gh + 3, 0xFF9FD9DD);

      if (fiend) {
         drawPopups(g, x + gw / 2, y + gh / 2, gh);
      }
   }

   /** The F13ND pop-ups: they pile onto the gauge, vanish together, and one last error pop-up appears. */
   private static void drawPopups(GuiGraphicsExtractor g, int cx, int cy, int gaugeHeight) {
      int t = ClientState.fiendTick;
      int screenW = g.guiWidth();
      int screenH = g.guiHeight();
      int dh = Math.round(POPUP_H * POPUP_SCALE);

      if (t < Tuning.POPUP_CLEAR_TICK) {
         Random rnd = new Random(ClientState.fiendSeed);
         List<Integer> kinds = new ArrayList<>();
         for (int i = 0; i < POPUPS.length; i++) {
            kinds.add(i);
         }
         Collections.shuffle(kinds, rnd);
         for (int i = 0; i < Tuning.POPUP_COUNT; i++) {
            int born = Tuning.POPUP_FIRST_TICK + i * Tuning.POPUP_INTERVAL;
            int kind = i < kinds.size() ? kinds.get(i) : rnd.nextInt(POPUPS.length);
            float ox = (rnd.nextFloat() - 0.5F) * 60.0F;
            // Stratified over the gauge height so the whole bar ends up covered, with some mess on top.
            float oy = (((i * 5) % 6) / 5.0F - 0.5F) * (gaugeHeight - dh) + (rnd.nextFloat() - 0.5F) * 14.0F;
            float phase = rnd.nextFloat() * 6.28F;
            if (t < born) {
               continue;
            }
            int dw = Math.round(POPUP_W[kind] * POPUP_SCALE);
            // They shift around a little after landing.
            float sx = (float)Math.sin((t + i * 5) * 0.35F + phase) * 3.0F;
            float sy = (float)Math.cos((t + i * 3) * 0.30F + phase) * 2.0F;
            int px = Math.max(2, Math.min(screenW - dw - 2, Math.round(cx + ox + sx - dw / 2.0F)));
            int py = Math.max(2, Math.min(screenH - dh - 2, Math.round(cy + oy + sy - dh / 2.0F)));
            g.blit(RenderPipelines.GUI_TEXTURED, POPUPS[kind], px, py, 0.0F, 0.0F, dw, dh, dw, dh);
         }
      } else if (t >= Tuning.POPUP_FINAL_TICK && t < Tuning.POPUP_END_TICK) {
         int dw = Math.round(POPUP_FINAL_W * POPUP_SCALE);
         int shake = t - Tuning.POPUP_FINAL_TICK < 5 ? ((t % 2 == 0) ? 3 : -3) : 0;
         // The final pop-up sits in the middle of the screen, not on the gauge.
         int px = Math.round(screenW / 2.0F - dw / 2.0F) + shake;
         int py = Math.round(screenH / 2.0F - dh / 2.0F);
         g.blit(RenderPipelines.GUI_TEXTURED, POPUP_FINAL, px, py, 0.0F, 0.0F, dw, dh, dw, dh);
      }
   }

   // ---- critical state + F13ND vignette ----

   static void renderCritical(GuiGraphicsExtractor g, DeltaTracker tracker) {
      int w = g.guiWidth();
      int h = g.guiHeight();

      // F13ND: constant red vignette while the pop-ups cover the bar, then a sudden flash with the final pop-up.
      int ft = ClientState.fiendTick;
      if (ft >= 0) {
         float red = 0.0F;
         if (ft < Tuning.POPUP_CLEAR_TICK) {
            // Fades in, so it does not slam onto the screen.
            red = 0.8F * smooth(Math.min(1.0F, (ft + 1) / 14.0F));
         } else if (ft >= Tuning.POPUP_FINAL_TICK && ft < Tuning.POPUP_END_TICK) {
            float rise = smooth(Math.min(1.0F, (ft - Tuning.POPUP_FINAL_TICK + 1) / 7.0F));
            red = 0.95F * rise * (1.0F - (ft - Tuning.POPUP_FINAL_TICK) / (float)(Tuning.POPUP_END_TICK - Tuning.POPUP_FINAL_TICK));
         }
         if (red > 0.0F) {
            drawVignette(g, w, h, 0xB0101A, red, 0.5F);
         }
      }

      int ticks = ClientState.critTicks;
      if (ticks <= 0) {
         return;
      }

      // The fade to black.
      float fade = Math.min(1.0F, ticks / (float)(Tuning.FADE_SECONDS * 20));
      int blackAlpha = (int)(fade * 255.0F);
      if (blackAlpha > 0) {
         g.fill(0, 0, w, h, blackAlpha << 24);
      }

      // Heartbeat (lub-dub).
      float phase = (ticks % 26) / 26.0F;
      float pulse = Math.max(bump(phase, 0.08F, 0.10F), 0.7F * bump(phase, 0.30F, 0.10F));
      // The effects stay faintly visible even when the screen is black.
      float visible = 1.0F - 0.65F * fade;

      float intensity = Math.min(1.0F, ticks / 40.0F) * (0.40F + 0.45F * pulse) * (0.6F + 0.4F * visible);
      drawVignette(g, w, h, 0x0A3A52, intensity, 0.42F);
      Tendrils.draw(g, w, h, Math.min(1.0F, ticks / 160.0F), pulse, visible);
   }

   /** Soft frame around the screen. reach is how far in it goes, as a fraction of the shorter side. */
   private static void drawVignette(GuiGraphicsExtractor g, int w, int h, int rgb, float intensity, float reach) {
      int steps = 22;
      int depth = (int)(Math.min(w, h) * reach);
      int th = Math.max(1, depth / steps);
      for (int k = 0; k < steps; k++) {
         float f = 1.0F - k / (float)steps;
         int alpha = (int)(Math.min(1.0F, intensity) * f * f * 255.0F);
         if (alpha <= 0) {
            continue;
         }
         int c = argb(alpha, rgb);
         int o = k * th;
         g.fill(o, o, w - o, o + th, c);
         g.fill(o, h - o - th, w - o, h - o, c);
         g.fill(o, o + th, o + th, h - o - th, c);
         g.fill(w - o - th, o + th, w - o, h - o - th, c);
      }
   }
}
