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
import me.deecaad.core.file.simple.RegistryValueSerializer;
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
import org.bukkit.block.BlockType;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.EntityType;
import org.jetbrains.annotations.NotNull;
import org.joml.Quaterniond;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;

/**
 * An explosion effect built using a ring of display entities.
 *
 * @see <a href="https://github.com/TheCymaera/minecraft-sky-torch">SkyTorch GitHub</a>
 * @see <a href="https://youtu.be/OKXTGbp6AMk">SkyTorch Video</a>
 */
public class ExplosionCloudMechanic extends Mechanic {

    private static final List<BlockData> DEFAULT_BLOCKS = List.of(
        Material.ORANGE_STAINED_GLASS.createBlockData(),
        Material.ORANGE_TERRACOTTA.createBlockData(),
        Material.SHROOMLIGHT.createBlockData(),
        Material.ORANGE_CONCRETE.createBlockData(),
        Material.HONEYCOMB_BLOCK.createBlockData(),
        Material.ORANGE_STAINED_GLASS.createBlockData(),
        Material.BLACK_STAINED_GLASS.createBlockData(),
        Material.GRAY_STAINED_GLASS.createBlockData(),
        Material.LIGHT_GRAY_STAINED_GLASS.createBlockData());

    private List<BlockData> blocks;
    private int immediateCount;
    private int delayedCount;
    private int delayedSpawnTicks;
    private double speed;
    private double initialSize;
    private double peakSize;
    private int lifetimeTicks;
    private int lifetimeJitterTicks;
    private double scaleJitter;
    private double drag;
    private boolean randomRotation;

    public ExplosionCloudMechanic() {
    }

    public ExplosionCloudMechanic(List<BlockData> blocks, int immediateCount, int delayedCount,
                                  int delayedSpawnTicks, double speed, double initialSize,
                                  double peakSize, int lifetimeTicks, int lifetimeJitterTicks,
                                  double scaleJitter, double drag, boolean randomRotation) {
        this.blocks = blocks;
        this.immediateCount = immediateCount;
        this.delayedCount = delayedCount;
        this.delayedSpawnTicks = delayedSpawnTicks;
        this.speed = speed;
        this.initialSize = initialSize;
        this.peakSize = peakSize;
        this.lifetimeTicks = lifetimeTicks;
        this.lifetimeJitterTicks = lifetimeJitterTicks;
        this.scaleJitter = scaleJitter;
        this.drag = drag;
        this.randomRotation = randomRotation;
    }

    @Override
    protected void use0(CastData cast) {
        Location target = cast.hasTargetLocation() ? cast.getTargetLocation()
            : (cast.getTarget() != null ? cast.getTarget().getLocation() : cast.getSourceLocation());

        if (target == null || target.getWorld() == null)
            return;

        if (immediateCount > 0) {
            spawn(target, immediateCount, 0.0, 1);
        }
        if (delayedCount > 0 && delayedSpawnTicks > 0) {
            spawn(target, 0, delayedCount / (double) delayedSpawnTicks, delayedSpawnTicks);
        }
    }

    private void spawn(Location target, int burstCount, double rate, int durationTicks) {
        DisplayEntityEmitter emitter = buildEmitter(burstCount, rate, durationTicks);
        emitter.spawnAt(target);
        MechanicsCore.getInstance().getTickManager().add(emitter);
    }

    private DisplayEntityEmitter buildEmitter(int burstCount, double rate, int durationTicks) {
        float jitter = (float) scaleJitter;
        int paletteSize = Math.max(1, blocks.size());

        int cyclePhaseJitter = lifetimeTicks / paletteSize;
        DisplayEntityEmitterSettings.Builder b = DisplayEntityEmitterSettings.builder()
            .displayType(EntityType.BLOCK_DISPLAY)
            .displayData(blocks.get(0))
            .shape(new CircleShape(0.0, CircleShape.Axis.Y))
            .direction(Direction.UP)
            .speed(speed)
            .durationTicks(durationTicks)
            .emittedLifetimeTicks(lifetimeTicks)
            .lifetimeJitterTicks(lifetimeJitterTicks)
            .scaleJitter(new Vector3f(jitter, jitter, jitter))
            .cyclePhaseJitterTicks(cyclePhaseJitter)
            .drag(drag)
            .killWhen(d -> {
                if (paletteSize < 2) return false;
                return d.age() >= (long) d.lifetime() * (paletteSize - 1) / paletteSize;
            })
            .scale(scaleTransition())
            .blockCycle(blockTransition())
            .rotation(randomRotation ? rotationTransition() : null);

        if (burstCount > 0)
            b.burst(burstCount, Integer.MAX_VALUE);
        else
            b.rate(rate);

        return new DisplayEntityEmitter(b.build());
    }

    private Transition<Vector3f> scaleTransition() {
        // Clouds grow in scale over time
        float start = (float) initialSize;
        float peak = (float) peakSize;
        return Transition.lerp(new Vector3f(start, start, start), new Vector3f(peak, peak, peak),
            Interpolators.VECTOR3F, lifetimeTicks);
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
        return new Transition<>(frames, Interpolators.stepped(), lifetimeTicks);
    }

    private Transition<Quaterniond> rotationTransition() {
        ThreadLocalRandom rng = ThreadLocalRandom.current();
        Quaterniond start = new Quaterniond().rotateXYZ(
            rng.nextDouble() * Math.PI * 2.0,
            rng.nextDouble() * Math.PI * 2.0,
            rng.nextDouble() * Math.PI * 2.0);
        Quaterniond end = new Quaterniond(start).rotateXYZ(
            (rng.nextDouble() - 0.5) * Math.PI,
            (rng.nextDouble() - 0.5) * Math.PI,
            (rng.nextDouble() - 0.5) * Math.PI);
        return Transition.lerp(start, end, Interpolators.QUATERNIOND, lifetimeTicks);
    }

    @Override
    public @NotNull NamespacedKey getKey() {
        return new NamespacedKey(MechanicsCore.getInstance(), "explosioncloud");
    }

    @Override
    public @NotNull Mechanic serialize(@NotNull SerializeData data) throws SerializerException {
        List<BlockData> blocks = parseBlockList(data);
        if (blocks.isEmpty())
            blocks = DEFAULT_BLOCKS;
        int immediateCount = data.of("Immediate_Count").assertRange(0, null).getInt().orElse(20);
        int delayedCount = data.of("Delayed_Count").assertRange(0, null).getInt().orElse(90);
        int delayedSpawnTicks = data.of("Delayed_Spawn_Ticks").assertRange(0, null).getInt().orElse(35);
        double speed = data.of("Speed").assertRange(0.0, null).getDouble().orElse(0.78);
        double initialSize = data.of("Initial_Size").assertRange(0.0, null).getDouble().orElse(1.5);
        double peakSize = data.of("Peak_Size").assertRange(0.0, null).getDouble().orElse(30.0);
        int lifetimeTicks = data.of("Lifetime_Ticks").assertRange(1, null).getInt().orElse(120);
        int lifetimeJitterTicks = data.of("Lifetime_Jitter_Ticks").assertRange(0, null).getInt().orElse(20);
        double scaleJitter = data.of("Scale_Jitter").assertRange(0.0, null).getDouble().orElse(1.2);
        double drag = data.of("Drag").assertRange(0.0, 1.0).getDouble().orElse(0.005);
        boolean randomRotation = data.of("Random_Rotation").getBool().orElse(true);

        return applyParentArgs(data, new ExplosionCloudMechanic(
            blocks, immediateCount, delayedCount, delayedSpawnTicks, speed, initialSize,
            peakSize, lifetimeTicks, lifetimeJitterTicks, scaleJitter, drag, randomRotation));
    }

    @SuppressWarnings("unchecked")
    static @NotNull List<BlockData> parseBlockList(@NotNull SerializeData data) throws SerializerException {
        List<List<Optional<Object>>> raw = data.ofList("Blocks")
            .addArgument(new RegistryValueSerializer<>(BlockType.class, false))
            .requireAllPreviousArgs()
            .assertList();

        List<BlockData> blocks = new ArrayList<>();
        for (List<Optional<Object>> row : raw) {
            List<BlockType> types = (List<BlockType>) row.get(0).get();
            for (BlockType type : types)
                blocks.add(type.createBlockData());
        }
        return blocks;
    }
}
