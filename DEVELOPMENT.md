# IFAAR: Breathless — technical notes

Expansion for IFAAR (mod id `combatinjuries`). Mod id `breathless`, package `com.kodari.breathless`.
Standalone jar, requires IFAAR at runtime. Built with the same versions as IFAAR (MC 26.2, Loom 1.18.2, Fabric API 0.161.0+26.2, Java 25).

## How it hooks into IFAAR
`CombatInjuriesApi.BEFORE_SHOT` (fired by IFAAR at the very top of the Adrenaline Shot code): while the exposure meter is above 0, Breathless takes the shot over (consumes it, cooldown, inject sound, meter to 0, 4 hearts of damage, F13ND pop-up sequence) and returns true, so NO rush starts. In every other case it returns false and IFAAR does its normal thing. A shot mid-rush, a stunned player or a shot on cooldown is never taken over.
`src/main/java/com/kodari/combatinjuries/` holds a compile-time copy of the API (the dev's real file) plus a stub `CombatInjuries` class (only `hasInjury`, `rushShotsOf`, `isStunned`, `ADRENALINE_INJECT`).
`build.gradle` excludes it from the jar, so the real IFAAR classes are used at runtime.
**If IFAAR's `CombatInjuriesApi.java` ever changes, copy the new version over the one in this project.**

## Files
- `Tuning.java` — every number (fill time, slowness tiers, damage ramp, blur, filters, drop chances, F13ND pop-up timings).
- `Hazard.java` — exposure meter, critical state, slowness, cure rules, ambience/warning sounds, the BEFORE_SHOT purge, F13ND sound schedule. Meter is a persistent player attachment (`Breathless.EXPOSURE`), reset on death.
- `MobRules.java` — spawn restriction below Y=0, zombie masks (2 weak filters) + drops, zombie spore damage, skeleton detection range, arrow trails.
- `MaskFilters.java` — the data component stored on a mask: two filter slots (0 = empty, FILTER_MAX = new).
- `Masks.java`, `ModItems.java`, `FilterItem.java`, `FilterClicks.java` — items, helpers, bundle-style inventory clicks (filter on cursor -> click mask = insert; empty cursor + right-click mask = take the last filter out).
- `ModSounds.java` — all sound events (files in assets/breathless/sounds, listed in sounds.json).
- `ExposurePayload.java`, `FiendPayload.java` — server to client sync / pop-up start.
- `mixin/HealMixin`, `mixin/SkeletonAimMixin`, `client/mixin/FogRendererMixin` — all OPTIONAL (require 0).
- `client/SporeHud.java`, `Tendrils.java`, `ClientState.java`, `BreathlessClient.java` — haze, vision pulses, floating spores, visor + cracks, gauge, F13ND pop-ups, critical visuals, tooltips, particles.

## Rules as built
- Spore zone: overworld, player Y < 0. Meter rises only without a working mask (mask on AND both filters have life). 90 s from 0 to 100 standing, 2x when sprinting or airborne. Never falls on its own.
- Slowness from exposure: 25% I, 50% II, 75% III, 100% IV.
- Mask holds 2 filters and needs both (Tuning.FILTERS_REQUIRED). The filters take turns draining, so a pair lasts 20 min below Y=0 (2x drain sprinting). Crack sound + visor crack when the weakest filter drops below 50% / 25%. When one is spent the mask stops working until a new filter goes in.
- Critical (100): wheeze loop, suffocation damage every 25 ticks that climbs from 1.0 to 3.0 over 30 s of staying critical, screen fades to black over 20 s, veins + vignette, tendrils sound at the start.
- Cure needs a working mask AND Instant Health. Mask first then heal: the meter drops by 30 (Instant Health I), 60 (II) or 100 (III+), see Tuning.PURGE_LEVEL_*. Heal first: remembered 2 min, applied when a working mask goes on.
- Adrenaline Shot while the meter is above 0 (F13ND; the IFAAR rush minigame only works at a completely empty meter; mid-rush shots are never taken over): meter to 0, 4 hearts genericKill damage (not in creative), no rush. Pop-up sequence on the client: cover pop-ups pile onto the gauge with a constant red vignette, they all vanish revealing an empty gauge, then the final "event run unsuccessful" pop-up appears with a red flash, and everything fades. `/breathless fiend` plays it without the cure. `Tuning.POPUPS_ENABLED=false` turns it off.
- Creative mode: the meter does not rise and there is no damage, but the critical sounds, the ambience and the visuals still run, so they can be tested. Test damage and the adrenaline cost in survival.
- Fog: unmasked is thick (22 blocks of sight, thinning with depth and exposure); a working mask roughly doubles it.
- Particles: tiny blue motes like cave dust, plus big mist puffs along Y=0. Without a mask, motes spawn 3-9 blocks away and are pulled to your face.
- Vision pulses ("blur") are a FAKE: a milky haze that closes in from the edges, from 40% exposure. Not a real blur shader.
- Zombies (exact `minecraft:zombie`, fresh spawns below Y=0) get a mask with 2 weak filters. Hits wear the filters; when it stops working it breaks. Unmasked zombies take 1 spore damage per second. Drops: 12% mask, 12% one loose filter, rest nothing.
- Skeletons: immune, detection range x0.6 while below Y=0, +7 arrow spread (mixin), white/cyan particle trail on their arrows.
- Natural/chunk-gen spawns of hostile mobs below Y=0 other than zombie/skeleton are cancelled.
- Sounds are played by the server at the player's position, so people standing nearby hear them too. Ambience plays in 8 s chunks (6 variants each for muffled/open).

## API names: where they came from
VERIFIED against Fabric API source (branch 26.3, close to 26.2): HudElementRegistry/VanillaHudElements, GuiGraphicsExtractor (fill, blit, text), PayloadTypeRegistry.clientboundPlay, ClientPlayNetworking.registerGlobalReceiver, ServerPlayNetworking.send, ServerEntityEvents (ALLOW_LOAD with spawn reason), ServerLivingEntityEvents, ServerPlayerEvents, AttachmentRegistry.createPersistent, CreativeModeTabEvents, CommandRegistrationCallback, Item.Properties.setId, Equippable.builder, hurtServer, InteractionResult.
VERIFIED from the working Sculkbreath jar: FogRenderer.setupFog / FogData fields, HealOrHarmMobEffect.applyInstantaneousEffect, AbstractSkeleton.performRangedAttack + spawnProjectileUsingShoot arg 7, MobEffects.INSTANT_HEALTH, Item.Properties.durability.
VERIFIED by a successful in-game build (round 1): everything that was on the old "not verified" list (drown/genericKill, sendOverlayMessage, isLoadedFromDisk, getAllEffects, sendParticles, the particle types, getTickCount, isFirstPerson, GLASS_BREAK, setDamageOnHurt).
VERIFIED against Fabric API source in batch 1: ItemClickBehaviorCallback.EVENT + EventResult, ItemTooltipCallback.EVENT (stack, context, type, lines), DataComponentType.builder().persistent().networkSynchronized() registered in BuiltInRegistries.DATA_COMPONENT_TYPE, ServerEntityEvents.EQUIPMENT_CHANGE, Level.isClientSide(), StreamCodec.composite/VAR_INT.
NOT verified in batch 1 (first suspects if the build fails): `DustParticleOptions(int, float)` constructor, `Slot.set(ItemStack)` and `SlotAccess.set/get`, `ClickAction.SECONDARY`, `MobEffectInstance(Holder,int,int,boolean,boolean)`, `MobEffects.SLOWNESS`, `ItemStack.consume(int, LivingEntity)` and `ServerPlayer.getCooldowns().addCooldown(ItemStack,int)/isOnCooldown(ItemStack)` (both copied from IFAAR's own code, so likely fine), `Entity.getRandom().nextInt()`, `Player.getEyePosition()`, `Component.translatable(String, Object...)`, `ChatFormatting`, `GuiGraphicsExtractor.blit` with a sub-region (spore sheet).
Nothing here has been played. Only a syntax-level javac check was run.

## Sound files: how they must be made
Always convert with `ffmpeg -i in.mp3 -vn -map_metadata -1 -ac 1 -c:a libvorbis out.ogg`. WITHOUT `-vn`, mp3s with cover art become OGGs with an extra Theora video stream and Minecraft silently refuses to play them (this broke the ambience, the sigh, the damage sound and the 50% crack in batch 1). Mono is needed for positional sound. Files are normalized to about -20 dB mean (ambience -26 dB) because the originals were very quiet.
