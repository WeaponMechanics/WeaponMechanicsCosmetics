/*
 * Copyright (c) 2026. All rights reserved. Distribution of this file, similar
 * files, related files, or related projects is strictly controlled.
 */

package com.cjcrafter.weaponmechanicscosmetics.mechanics;

import me.deecaad.core.MechanicsCore;
import me.deecaad.core.emitter.DisplayEntityEmitter;
import me.deecaad.core.emitter.DisplayEntityEmitterSettings;
import me.deecaad.core.file.SerializeData;
import me.deecaad.core.file.SerializerException;
import me.deecaad.core.file.serializers.Direction;
import me.deecaad.core.mechanics.CastData;
import me.deecaad.core.mechanics.defaultmechanics.Mechanic;
import me.deecaad.core.transition.Easing;
import me.deecaad.core.transition.Interpolators;
import me.deecaad.core.transition.Keyframe;
import me.deecaad.core.transition.Transition;
import me.deecaad.core.utils.shape.CircleShape;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.EntityType;
import org.jetbrains.annotations.NotNull;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Dense ring of cloud puffs travelling rapidly outward along the ground.
 */
public class ShockwaveMechanic extends Mechanic {

    private static final List<BlockData> DEFAULT_BLOCKS = List.of(
        Material.WHITE_STAINED_GLASS.createBlockData(),
        Material.LIGHT_GRAY_STAINED_GLASS.createBlockData());

    private List<BlockData> blocks;
    private int count;
    private double speed;
    private double heightOffset;
    private int duration;
    private double startSize;
    private double endSize;
    private boolean randomRotation;

    public ShockwaveMechanic() {
    }

    public ShockwaveMechanic(List<BlockData> blocks, int count, double speed, double heightOffset,
                             int duration, double startSize, double endSize, boolean randomRotation) {
        this.blocks = blocks;
        this.count = count;
        this.speed = speed;
        this.heightOffset = heightOffset;
        this.duration = duration;
        this.startSize = startSize;
        this.endSize = endSize;
        this.randomRotation = randomRotation;
    }

    @Override
    protected void use0(CastData cast) {
        Location target = cast.hasTargetLocation() ? cast.getTargetLocation()
            : (cast.getTarget() != null ? cast.getTarget().getLocation() : cast.getSourceLocation());

        if (target == null || target.getWorld() == null)
            return;

        Location origin = target.clone().add(0.0, heightOffset, 0.0);

        DisplayEntityEmitterSettings settings = DisplayEntityEmitterSettings.builder()
                .displayType(EntityType.BLOCK_DISPLAY)
                .displayData(blocks.get(ThreadLocalRandom.current().nextInt(blocks.size())))
                .shape(new CircleShape(0.0, CircleShape.Axis.Y))
                .direction(Direction.UP)
                .speed(speed)
                .burst(count, Integer.MAX_VALUE)
                .durationTicks(1)
                .emittedLifetimeTicks(duration)
                .lifetimeJitterTicks(Math.max(1, duration / 8))
                .scaleJitter(new Vector3f(0.25f, 0.25f, 0.25f))
                .cyclePhaseJitterTicks(duration / 2)
                .scale(scaleTransition())
                .blockCycle(blockTransition())
                .rotation(rotationTransition())
                .build();

        DisplayEntityEmitter emitter = new DisplayEntityEmitter(settings);
        emitter.spawnAt(origin);
        MechanicsCore.getInstance().getTickManager().add(emitter);
    }

    private Transition<Vector3f> scaleTransition() {
        // Each puff shrinks smoothly from startSize to endSize over its lifetime as it travels
        // outward, so the wave reads as dissolving into nothing rather than ballooning to a giant
        // size and popping out.
        float s = (float) startSize;
        float e = (float) endSize;
        return Transition.lerp(new Vector3f(s, s, s), new Vector3f(e, e, e),
            Interpolators.VECTOR3F, duration, Easing.EASE_IN_CUBIC);
    }

    private Transition<org.joml.Quaterniond> rotationTransition() {
        if (!randomRotation) return null;
        ThreadLocalRandom rng = ThreadLocalRandom.current();
        org.joml.Quaterniond start = new org.joml.Quaterniond().rotateXYZ(
            rng.nextDouble() * Math.PI * 2.0,
            rng.nextDouble() * Math.PI * 2.0,
            rng.nextDouble() * Math.PI * 2.0);
        // Slow tumble across lifetime - a partial rotation on each axis is enough to read as
        // "rolling" without spinning so fast it strobes.
        org.joml.Quaterniond end = new org.joml.Quaterniond(start).rotateXYZ(
            (rng.nextDouble() - 0.5) * Math.PI * 2.0,
            (rng.nextDouble() - 0.5) * Math.PI * 2.0,
            (rng.nextDouble() - 0.5) * Math.PI * 2.0);
        return new Transition<>(List.of(
            new Keyframe<>(0.0, start, Easing.LINEAR),
            new Keyframe<>(1.0, end, Easing.LINEAR)
        ), Interpolators.QUATERNIOND, duration);
    }

    private Transition<BlockData> blockTransition() {
        List<Keyframe<BlockData>> frames = new ArrayList<>(blocks.size());
        int n = blocks.size();
        if (n == 1) {
            frames.add(new Keyframe<>(0.0, blocks.get(0), Easing.LINEAR));
            frames.add(new Keyframe<>(1.0, blocks.get(0), Easing.LINEAR));
        } else {
            for (int i = 0; i < n; i++) {
                double t = i / (double) (n - 1);
                frames.add(new Keyframe<>(t, blocks.get(i), Easing.LINEAR));
            }
        }
        return new Transition<>(frames, Interpolators.stepped(), duration);
    }

    @Override
    public @NotNull NamespacedKey getKey() {
        return new NamespacedKey(MechanicsCore.getInstance(), "shockwave");
    }

    @Override
    public @NotNull Mechanic serialize(@NotNull SerializeData data) throws SerializerException {
        List<BlockData> blocks = ExplosionCloudMechanic.parseBlockList(data);
        if (blocks.isEmpty())
            blocks = DEFAULT_BLOCKS;
        int count = data.of("Count").assertRange(1, null).getInt().orElse(120);
        double speed = data.of("Speed").assertRange(0.0, null).getDouble().orElse(3.2);
        double heightOffset = data.of("Height_Offset").getDouble().orElse(0.2);
        int duration = data.of("Duration").assertRange(1, null).getInt().orElse(30);
        double startSize = data.of("Start_Size").assertRange(0.0, null).getDouble().orElse(1.5);
        double endSize = data.of("End_Size").assertRange(0.0, null).getDouble().orElse(0.0);
        boolean randomRotation = data.of("Random_Rotation").getBool().orElse(true);

        return applyParentArgs(data, new ShockwaveMechanic(
            blocks, count, speed, heightOffset, duration, startSize, endSize, randomRotation));
    }
}
