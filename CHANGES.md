# Changes
## Round 1 (first build)
Everything. See DEVELOPMENT.md.

## Batch 1 (untested)
- Masks: both are named "Gas Mask". A mask holds 2 filters (needs both). Percent lines under the name, click filters onto a mask in the inventory, right-click a mask to take one out.
- 11 new sounds hooked up (mask on, filter in/out, filter spent, 50%/25% cracks, tendrils, spore damage, sigh of relief, exposed, muffled/open ambience) + pop-up sounds.
- Fog: smooth 12-block fade, denser and darker with depth, blue. Mist layer of particles on the Y=0 line.
- Particles: blue dust; they stream toward you when the mask is off. Floating spore sprites on screen from your sheet.
- Exposure: 90 s to fill, Slowness I-IV by exposure, milky vision pulses, damage ramps up while critical.
- Visor: cracks only at the edges, no splotches, instant with a flash and shake.
- Zombies: much rarer drops, masks hold 2 weak filters.
- Gauge: your biohazard gauge replaces the bar (background removed).
- Adrenaline Shot while critical: one-time cure through IFAAR's BEFORE_SHOT hook, no rush. F13ND pop-up sequence with sounds, red vignette, final "event run unsuccessful" pop-up.
- `/breathless fiend` to test the pop-ups; `/breathless give` now gives masks with filters.

## Batch 2 (untested)
- Fog: thick unmasked, about twice the sight range when masked.
- Gauge smaller; on-screen spore sprites removed; real particles are now tiny blue motes and bigger, brighter mist along Y=0 (denser, down to 5 blocks below the line).
- Unmasked spores are pulled to your face (the pull was being cut to a tenth by the particle type).
- Tendrils: thin, smooth strands instead of blocks.
- F13ND: red vignette fades in; pop-ups half the size; final pop-up in the middle of the screen; trimmed 0.6 s of silence off the start of the final sound (that was the delay) and 0.2 s off the sigh.
- Sounds: ambience, damage sound and critical sounds now also play in creative (damage itself does not); "exposed" cough removed.
- Instant Health purges by level: I = 30, II = 60, III+ = 100.
- Adrenaline Shot: IFAAR's rush only works at 0 exposure; with any exposure the shot is the F13ND cure.
- FOUND the cause of the silent sounds: the mp3s had cover art, so the converted OGGs had a hidden video stream and Minecraft would not play them. All sounds re-converted without it, and raised in volume (several were very quiet).
- Adrenaline cure: if the 4-heart hit is swallowed for any reason, health is taken directly; overlay message added.
