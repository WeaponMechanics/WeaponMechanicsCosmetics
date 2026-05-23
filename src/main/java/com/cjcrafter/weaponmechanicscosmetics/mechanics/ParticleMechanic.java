/*
 * Copyright (c) 2022-2026. All rights reserved. Distribution of this file, similar
 * files, related files, or related projects is strictly controlled.
 */

package com.cjcrafter.weaponmechanicscosmetics.mechanics;

import com.cryptomorin.xseries.particles.XParticle;
import me.deecaad.core.MechanicsCore;
import me.deecaad.core.file.SerializeData;
import me.deecaad.core.file.SerializerException;
import me.deecaad.core.file.serializers.AnyVectorProvider;
import me.deecaad.core.file.serializers.ColorSerializer;
import me.deecaad.core.file.serializers.ItemSerializer;
import me.deecaad.core.file.serializers.VectorProvider;
import me.deecaad.core.file.serializers.VectorSerializer;
import me.deecaad.core.mechanics.CastData;
import me.deecaad.core.mechanics.Conditions;
import me.deecaad.core.mechanics.Mechanics;
import me.deecaad.core.mechanics.Targeters;
import me.deecaad.core.mechanics.conditions.Condition;
import me.deecaad.core.mechanics.defaultmechanics.Mechanic;
import me.deecaad.core.mechanics.targeters.Targeter;
import me.deecaad.core.utils.EntityTransform;
import me.deecaad.core.utils.ImmutableVector;
import me.deecaad.core.utils.Quaternion;
import org.bukkit.*;
import org.bukkit.block.BlockType;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Iterator;
import java.util.List;
import java.util.OptionalInt;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static com.cryptomorin.xseries.particles.XParticle.*;

public class ParticleMechanic extends Mechanic {

    private Particle particle;
    private int count;
    private double extra;
    private VectorProvider offset; // sometimes velocity
    private Object options;
    private boolean force;

    private Targeter shape;
    private Targeter viewers;
    private List<Condition> viewerConditions;

    /**
     * Default constructor for serializer
     */
    public ParticleMechanic() {
    }

    public ParticleMechanic(Particle particle, int count, double extra, VectorProvider offset, Object options,
                            boolean force, Targeter shape, Targeter viewers, List<Condition> viewerConditions) {
        this.particle = particle;
        this.count = count;
        this.extra = extra;
        this.offset = offset;
        this.options = options;
        this.force = force;

        this.shape = shape;
        this.viewers = viewers;
        this.viewerConditions = viewerConditions;
    }

    public void display(@NotNull CastData castData, @Nullable Quaternion localRotation) {
        Location location = castData.getTargetLocation();

        if (location == null)
            // I've noticed others using @Target{} in modules such as Shoot
            // and that would result in a NPE, so in case that happens
            // we simply get the source location (same as using @Source{...}
            location = castData.getSourceLocation();

        World world = location.getWorld();
        if (world == null) return;

        if (localRotation == null) {
            EntityTransform localTransform = castData.getTarget() == null ? null : new EntityTransform(castData.getSource());
            localRotation = localTransform == null ? null : localTransform.getLocalRotation();
        }

        Vector offset = this.offset.provide(localRotation);
        double extra = this.extra == -11 ? offset.length() / 20 : this.extra;
        Object data = this.options;

        // If the server says this particle needs 'Color' then we give
        // it a default in case there isn't one defined in the config
        Class<?> required = particle.getDataType();
        if (data == null && required == Color.class) {
            data = Color.fromARGB(255, 255, 255, 255); // default WHITE
        }

        if (viewers == null) {
            world.spawnParticle(particle, location.getX(), location.getY(), location.getZ(), count, offset.getX(), offset.getY(), offset.getZ(), extra, data, force);
            return;
        }

        Iterator<CastData> targets = viewers.getTargets(castData);
        outer : while (targets.hasNext()) {
            CastData viewer = targets.next();

            // Only players can see particles
            if (!(viewer.getTarget() instanceof Player player))
                continue;

            for (Condition condition : viewerConditions) {
                if (!condition.isAllowed(viewer))
                    continue outer;
            }

            player.spawnParticle(particle, location.getX(), location.getY(), location.getZ(), count, offset.getX(), offset.getY(), offset.getZ(), extra, data, force);
        }
    }

    @Override
    protected void use0(CastData cast) {
        if (shape == null) {
            display(cast, null);
            return;
        }

        CastData shaped = cast.clone();

        if (shaped.getTargetLocation() == null) {
            shaped.setTargetLocation(shaped.getSourceLocation());
        }

        Iterator<CastData> targets = shape.getTargets(shaped);
        while (targets.hasNext()) {
            targets.next();
            display(shaped, null);
        }
    }

    @Override
    public @NotNull NamespacedKey getKey() {
        return new NamespacedKey(MechanicsCore.getInstance(), "particle");
    }

    @Override
    public @Nullable String getWikiLink() {
        return "https://cjcrafter.gitbook.io/mechanics/mechanics/particle";
    }

    @NotNull
    @Override
    public Mechanic serialize(SerializeData data) throws SerializerException {

        // This Serializer was developed using information from this thread:
        // https://www.spigotmc.org/threads/comprehensive-particle-spawning-guide-1-13-1-18.343001/
        // Big thanks to Esophose who compiled all of that information for
        // anybody to use.

        Particle particle = data.of("Particle").assertExists().getParticle().get();
        OptionalInt count = data.of("Count").assertRange(0, null).getInt();
        double extra = 0.0;
        VectorProvider offset;
        Object options = null;
        boolean force = data.of("Always_Show").getBool().orElse(true);

        // TODO Actually, using multiple packets we can fix this
        if (data.has("Velocity") && data.has("Noise"))
            throw data.exception(null, "Cannot use both 'Velocity' and 'Noise' at the same time!");

        XParticle xParticle = XParticle.of(particle);
        switch (xParticle) {
            // Takes 2 colors, and a size scalar
            case DUST_COLOR_TRANSITION -> {
                Color color = data.of("Color").assertExists().serialize(new ColorSerializer()).get();
                Color fade = data.of("Fade_Color").assertExists().serialize(new ColorSerializer()).get();
                float size = (float) data.of("Size").assertRange(0.0, null).getDouble().orElse(1.0);
                noVelocity(particle, data);
                noBlock(particle, data);
                offset = parseVector(data, "Noise");

                options = new Particle.DustTransition(color, fade, size);
            }

            // Takes 1 color, and a size scalar
            case DUST -> {
                Color color = data.of("Color").assertExists().serialize(new ColorSerializer()).get();
                float size = (float) data.of("Size").assertRange(0.0, null).getDouble().orElse(1.0);
                noVelocity(particle, data);
                noFade(particle, data);
                noBlock(particle, data);
                offset = parseVector(data, "Noise");

                options = new Particle.DustOptions(color, size);
            }

            // Takes a color, but stores it in the offset vector.
            case ENTITY_EFFECT -> {
                Color color = data.of("Color").assertExists().serialize(new ColorSerializer()).get();
                if (data.has("Count"))
                    throw data.exception("Count", "'" + ENTITY_EFFECT.get() + "' cannot use the 'Count' argument!", "Consider using '" + DUST.get() + "' particles instead.");
                if (data.has("Noise"))
                    throw data.exception("Noise", "'" + ENTITY_EFFECT.get() + "' cannot use the 'Noise' argument!", "Consider using '" + DUST.get() + "' particles instead.");
                noVelocity(particle, data);
                noFade(particle, data);
                noBlock(particle, data);

                count = OptionalInt.of(0);
                extra = 1.0;
                offset = new AnyVectorProvider(false, new ImmutableVector(color.getRed() / 255.0, color.getGreen() / 255.0, color.getBlue() / 255.0));
            }

            // Takes an ItemStack
            case ITEM -> {
                noVelocity(particle, data);
                noColor(particle, data);
                noFade(particle, data);
                offset = parseVector(data, "Noise");
                options = data.of("Material_Data").assertExists().serialize(new ItemSerializer()).get();
            }

            case BLOCK, FALLING_DUST, BLOCK_MARKER, DUST_PILLAR -> {
                noVelocity(particle, data);
                noColor(particle, data);
                noFade(particle, data);
                offset = parseVector(data, "Noise");

                BlockType blockType = data.of("Material_Data").assertExists().getBukkitRegistry(BlockType.class).get();
                options = blockType.createBlockData();
            }

            // All of these particles can be directional by setting count=0.
            // This means that instead of using offset, we should use velocity.
            case BUBBLE_COLUMN_UP, BUBBLE_POP, CAMPFIRE_COSY_SMOKE, CAMPFIRE_SIGNAL_SMOKE, CLOUD, CRIT,
                 ENCHANTED_HIT, DAMAGE_INDICATOR, DRAGON_BREATH, ELECTRIC_SPARK, ENCHANT, END_ROD, EXPLOSION,
                 FIREWORK, FLAME, NAUTILUS, PORTAL, REVERSE_PORTAL, SCRAPE, SCULK_CHARGE, SCULK_CHARGE_POP,
                 SCULK_SOUL, SMALL_FLAME, LARGE_SMOKE, SMOKE, SOUL, SOUL_FIRE_FLAME, SPIT, SQUID_INK,
                 TOTEM_OF_UNDYING, BUBBLE, WAX_OFF, WAX_ON -> {

                noBlock(particle, data);
                noColor(particle, data);
                noFade(particle, data);

                // When the user doesn't define a count, we will define it for
                // them. 0 for velocity, 1 for offset.
                if (count.isEmpty()) {
                    if (data.has("Velocity"))
                        count = OptionalInt.of(0);
                    else
                        count = OptionalInt.of(1);
                }

                // When the user defines
                if (count.getAsInt() == 0 && data.has("Noise"))
                    throw data.exception("Noise", "Cannot use 'Noise' when 'Count=0'. Count must be 1 or higher!");
                if (count.getAsInt() != 0 && data.has("Velocity"))
                    throw data.exception("Velocity", "Cannot use 'Velocity' when 'Count≠0'. Count must be 0!");
                if (count.getAsInt() == 0) {
                    offset = parseVector(data, "Velocity");
                    extra = -11;
                } else {
                    offset = parseVector(data, "Noise");
                }
            }

            default -> {
                noVelocity(particle, data);
                noBlock(particle, data);
                noColor(particle, data);
                noFade(particle, data);
                if (count.isPresent() && count.getAsInt() == 0)
                    throw data.exception("Count", "Cannot use Count=0 for '" + particle + "'");
                offset = parseVector(data, "Noise");
            }
        }

        // Paper 1.21.9+ changed some particles (ex. FLASH) to require Color data
        // If the particle wants Color we get it from the config (or default to WHITE)
        if (options == null && particle.getDataType() == Color.class) {
            options = data.of("Color").serialize(new ColorSerializer()).orElse(Color.WHITE);
        }

        // When the user didn't specify count and the plugin couldn't
        // automatically determine a count, we should set it to 1.
        if (count.isEmpty())
            count = OptionalInt.of(1);

        Targeter shape = data.of("Shape").serializeRegistry(Targeters.REGISTRY).orElse(null);
        if (shape != null && !(shape instanceof me.deecaad.core.mechanics.targeters.RelativeTargeter)) {
            throw data.exception("Shape", "Expected a relative/shape targeter like Sphere{...}, Cube{...}, etc.");
        }
        forceUseTarget(shape);

        Targeter viewers = data.of("Viewers").serializeRegistry(Targeters.REGISTRY).orElse(null);
        List<Condition> viewerConditions = data.of("Viewer_Conditions").getRegistryList(Conditions.REGISTRY);

        return applyParentArgs(data, new ParticleMechanic(particle, count.getAsInt(), extra, offset, options, force, shape, viewers, viewerConditions));
    }

    private void noVelocity(Particle particle, SerializeData data) throws SerializerException {
        if (data.has("Velocity")) {
            String velocityParticles = Stream.of(BUBBLE_COLUMN_UP, BUBBLE_POP, CAMPFIRE_COSY_SMOKE, CAMPFIRE_SIGNAL_SMOKE, CLOUD, CRIT,
                ENCHANTED_HIT, DAMAGE_INDICATOR, DRAGON_BREATH, ELECTRIC_SPARK, ENCHANT, END_ROD, EXPLOSION,
                FIREWORK, FLAME, NAUTILUS, PORTAL, REVERSE_PORTAL, SCRAPE, SCULK_CHARGE, SCULK_CHARGE_POP,
                SCULK_SOUL, SMALL_FLAME, LARGE_SMOKE, SMOKE, SOUL, SOUL_FIRE_FLAME, SPIT, SQUID_INK,
                TOTEM_OF_UNDYING, BUBBLE, WAX_OFF, WAX_ON)
                .map(XParticle::get)
                .map(Particle::name)
                .map(String::toLowerCase)
                .map(s -> "'" + s + "'")
                .collect(Collectors.joining(", "));

            throw data.exception("Velocity", "'" + particle + "' cannot use the 'Velocity' argument.",
                    "Only " + velocityParticles + " can use 'Velocity'.",
                    "Note that not all of the above particles may be available in your MC version.");
        }
    }

    private void noBlock(Particle particle, SerializeData data) throws SerializerException {
        if (data.has("Material_Data")) {
            String itemParticles = Stream.of(ITEM, BLOCK, FALLING_DUST, BLOCK_MARKER, DUST_PILLAR)
                .map(XParticle::get)
                .map(Particle::name)
                .map(String::toLowerCase)
                .map(s -> "'" + s + "'")
                .collect(Collectors.joining(", "));
            throw data.exception("Material_Data", "'" + particle + "' cannot use the 'Material_Data' argument",
                    "Only " + itemParticles + " can use 'Material_Data'");
        }
    }

    private void noColor(Particle particle, SerializeData data) throws SerializerException {
        if (!data.has("Color"))
            return;

        XParticle xp = XParticle.of(particle);

        boolean allowed =
                xp == DUST || xp == DUST_COLOR_TRANSITION || xp == ENTITY_EFFECT
                        // Allow Color for any particle that actually uses Color as its data type on this server
                        || particle.getDataType() == Color.class
                        // Let's allow color for FLASH even for older servers, we’ll just ignore it there (pre 1.21.9)
                        || xp == FLASH;
        if (!allowed) {
            String colorParticles = Stream.of(DUST, DUST_COLOR_TRANSITION, ENTITY_EFFECT)
                    .map(XParticle::get)
                    .map(Particle::name)
                    .map(String::toLowerCase)
                    .map(s -> "'" + s + "'")
                    .collect(Collectors.joining(", "));

            throw data.exception("Color", "'" + particle + "' cannot use the 'Color' argument",
                    "Only " + colorParticles + " (and particles whose data type is Color) can use 'Color'");
        }
    }

    private void noFade(Particle particle, SerializeData data) throws SerializerException {
        if (data.has("Fade_Color")) {
            throw data.exception("Fade_Color", "'" + particle + "' cannot use the 'Fade_Color' argument",
                    "Only 'DUST_COLOR_TRANSITION' can use 'Fade_Color'");
        }
    }

    private @NotNull VectorProvider parseVector(SerializeData data, String relative) throws SerializerException {
        VectorProvider zero = new AnyVectorProvider(false, new ImmutableVector());
        return data.of(relative).serialize(VectorSerializer.class).orElse(zero);
    }

    private static final java.lang.reflect.Field RELATIVE_USE_TARGET_FIELD;

    static {
        java.lang.reflect.Field f = null;
        try {
            f = me.deecaad.core.mechanics.targeters.RelativeTargeter.class.getDeclaredField("isUseTarget");
            f.setAccessible(true);
        } catch (Throwable ignored) {
        }
        RELATIVE_USE_TARGET_FIELD = f;
    }

    private static void forceUseTarget(@Nullable Targeter targeter) {
        if (targeter == null || RELATIVE_USE_TARGET_FIELD == null) return;
        if (!(targeter instanceof me.deecaad.core.mechanics.targeters.RelativeTargeter)) return;

        try {
            RELATIVE_USE_TARGET_FIELD.setBoolean(targeter, true);
        } catch (Throwable ignored) {
        }
    }
}
