/*
 * Copyright (c) 2026. All rights reserved. Distribution of this file, similar
 * files, related files, or related projects is strictly controlled.
 */

package com.cjcrafter.weaponmechanicscosmetics.mechanics;

import me.deecaad.core.MechanicsCore;
import me.deecaad.core.compatibility.CompatibilityAPI;
import me.deecaad.core.compatibility.entity.EntityCompatibility;
import me.deecaad.core.compatibility.entity.FakeDisplayEntity;
import me.deecaad.core.compatibility.entity.FakeEntity;
import me.deecaad.core.file.SerializeData;
import me.deecaad.core.file.SerializerException;
import me.deecaad.core.mechanics.CastData;
import me.deecaad.core.mechanics.defaultmechanics.Mechanic;
import me.deecaad.core.tick.Tickable;
import me.deecaad.core.utils.NumberUtil;
import me.deecaad.core.utils.VectorUtil;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.block.BlockType;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Display;
import org.bukkit.util.Transformation;
import org.bukkit.util.Vector;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.concurrent.ThreadLocalRandom;

/**
 * Satellite-style orbital laser
 */
public class SkyBeamMechanic extends Mechanic {

    private BlockData innerBlock;
    private BlockData outerBlock;
    private double skyHeight;
    private double beamWidth;
    private double glowMinWidth;
    private double glowMaxWidth;
    private int glowPeriod;
    private double flySpeed;
    private int duration;
    private int beamStartFrames;
    private int beamEndFrames;
    private double driftMagnitude;
    private int driftPeriod;

    public SkyBeamMechanic() {
    }

    public SkyBeamMechanic(BlockData innerBlock, BlockData outerBlock, double skyHeight,
                           double beamWidth, double glowMinWidth, double glowMaxWidth,
                           int glowPeriod, double flySpeed, int duration,
                           int beamStartFrames, int beamEndFrames,
                           double driftMagnitude, int driftPeriod) {
        this.innerBlock = innerBlock;
        this.outerBlock = outerBlock;
        this.skyHeight = skyHeight;
        this.beamWidth = beamWidth;
        this.glowMinWidth = glowMinWidth;
        this.glowMaxWidth = glowMaxWidth;
        this.glowPeriod = glowPeriod;
        this.flySpeed = flySpeed;
        this.duration = duration;
        this.beamStartFrames = beamStartFrames;
        this.beamEndFrames = beamEndFrames;
        this.driftMagnitude = driftMagnitude;
        this.driftPeriod = driftPeriod;
    }

    @Override
    protected void use0(CastData cast) {
        Location target;
        if (cast.hasTargetLocation())
            target = cast.getTargetLocation();
        else if (cast.getTarget() != null)
            target = cast.getTarget().getLocation();
        else
            target = cast.getSourceLocation();

        if (target == null || target.getWorld() == null)
            return;

        // Random sky origin: a sphere of radius skyHeight above the target
        ThreadLocalRandom rng = ThreadLocalRandom.current();
        double azimuth = rng.nextDouble() * Math.PI * 2.0;
        double elevation = Math.toRadians(rng.nextDouble(78.0, 90.0));
        Vector offset = new Vector(
                Math.cos(azimuth) * Math.cos(elevation),
                Math.sin(elevation),
                Math.sin(azimuth) * Math.cos(elevation)
        ).multiply(skyHeight);

        Vector origin = target.toVector().add(offset);
        Vector hit = target.toVector();
        Vector velocityDirection = new Vector(
                Math.cos(azimuth + Math.PI / 2.0), 0.0, Math.sin(azimuth + Math.PI / 2.0));

        // Anchor both fake displays at the target permanently. The visible beam is drawn entirely
        // via Transformation.translation; the entity never moves, so it can't slide outside view
        // distance and flicker on/off. setDisplayWidth/Height widen the client-side culling box
        // so the translated geometry stays inside the frustum check.
        EntityCompatibility ec = CompatibilityAPI.getCompatibility().getEntityCompatibility();
        Location anchor = target.clone();
        FakeEntity inner = ec.generateFakeBlockDisplay(anchor, innerBlock);
        FakeEntity outer = ec.generateFakeBlockDisplay(anchor, outerBlock);
        FakeDisplayEntity innerDisplay = (FakeDisplayEntity) inner;
        FakeDisplayEntity outerDisplay = (FakeDisplayEntity) outer;

        Display.Brightness fullBright = new Display.Brightness(15, 15);
        float cullSize = (float) (skyHeight * 4.0);
        innerDisplay.setBrightness(fullBright);
        innerDisplay.setViewRange(4.0f);
        innerDisplay.setDisplayWidth(cullSize);
        innerDisplay.setDisplayHeight(cullSize);
        outerDisplay.setBrightness(fullBright);
        outerDisplay.setViewRange(4.0f);
        outerDisplay.setDisplayWidth(cullSize);
        outerDisplay.setDisplayHeight(cullSize);

        BeamTicker ticker = new BeamTicker(target.getWorld(), target.toVector(), origin, hit,
                velocityDirection, inner, outer);
        // Apply the first frame's transformation before show() so the beam doesn't pop in at the
        // anchor for one tick.
        ticker.applyFrame();
        inner.updateMeta();
        outer.updateMeta();
        inner.show();
        outer.show();
        MechanicsCore.getInstance().getTickManager().add(ticker);
    }

    @Override
    public @NotNull NamespacedKey getKey() {
        return new NamespacedKey(MechanicsCore.getInstance(), "skybeam");
    }

    @Override
    public @NotNull Mechanic serialize(@NotNull SerializeData data) throws SerializerException {
        BlockData inner = data.of("Inner_Block").getBukkitRegistry(BlockType.class)
            .map(BlockType::createBlockData).orElse(Material.SMOOTH_QUARTZ.createBlockData());
        BlockData outer = data.of("Outer_Block").getBukkitRegistry(BlockType.class)
            .map(BlockType::createBlockData).orElse(Material.WHITE_STAINED_GLASS.createBlockData());
        double skyHeight = data.of("Sky_Height").assertRange(1.0, null).getDouble().orElse(60.0);
        double beamWidth = data.of("Beam_Width").assertRange(0.0, null).getDouble().orElse(0.8);
        double glowMinWidth = data.of("Glow_Min_Width").assertRange(0.0, null).getDouble().orElse(beamWidth - 0.1);
        double glowMaxWidth = data.of("Glow_Max_Width").assertRange(0.0, null).getDouble().orElse(beamWidth + 0.5);
        int glowPeriod = data.of("Glow_Period").assertRange(1, null).getInt().orElse(3);
        double flySpeed = data.of("Fly_Speed").assertRange(0.0, null).getDouble().orElse(1.0);
        int duration = data.of("Duration").assertRange(1, null).getInt().orElse(30);
        int beamStartFrames = data.of("Beam_Start_Frames").assertRange(1, null).getInt().orElse(4);
        int beamEndFrames = data.of("Beam_End_Frames").assertRange(1, null).getInt().orElse(16);
        double driftMagnitude = data.of("Drift_Magnitude").assertRange(0.0, null).getDouble().orElse(0.5);
        int driftPeriod = data.of("Drift_Period").assertRange(1, null).getInt().orElse(12);

        return applyParentArgs(data, new SkyBeamMechanic(
            inner, outer, skyHeight, beamWidth, glowMinWidth, glowMaxWidth, glowPeriod,
            flySpeed, duration, beamStartFrames, beamEndFrames, driftMagnitude, driftPeriod));
    }

    /**
     * Owns the per-tick beam state. Both fake displays follow {@link #applyFrame()} each tick;
     * cleanup despawns both and marks the handle finished so the command-side registry can prune.
     */
    private final class BeamTicker implements Tickable {

        // The fake displays sit at this world position for the entire lifetime; the visible beam
        // is moved around purely via Transformation.translation. Keeping the entity stationary
        // avoids the per-tick teleport packets that were causing the beam to flicker out of
        // render-distance culling.
        private final Vector entityAnchor;
        private final org.bukkit.World world;
        private final Vector origin;
        private final Vector hit;
        private final Vector originalHit;
        private final Vector velocityDirection;
        private final FakeEntity inner;
        private final FakeEntity outer;

        private int time;
        private int startFrame;
        private int endTime;
        private double driftTimeX;
        private double driftTimeZ;
        private boolean retracting;
        private boolean onHitFired;
        private boolean removed;

        BeamTicker(org.bukkit.World world, Vector entityAnchor, Vector origin, Vector hit,
                   Vector velocityDirection, FakeEntity inner, FakeEntity outer) {
            this.world = world;
            this.entityAnchor = entityAnchor;
            this.origin = origin;
            this.hit = hit;
            this.originalHit = hit.clone();
            this.velocityDirection = velocityDirection;
            this.inner = inner;
            this.outer = outer;
        }

        @Override
        public boolean tick() {

            // Advance state for the NEXT frame, then render.
            time++;
            ThreadLocalRandom rng = ThreadLocalRandom.current();
            driftTimeX += rng.nextDouble(2.0);
            driftTimeZ += rng.nextDouble(2.2);

            origin.add(velocityDirection.clone().multiply(flySpeed));
            hit.setX(originalHit.getX() + Math.sin(driftTimeX / driftPeriod * Math.PI) * driftMagnitude);
            hit.setZ(originalHit.getZ() + Math.sin(driftTimeZ / driftPeriod * Math.PI) * driftMagnitude);

            startFrame++;
            if (!onHitFired && startFrame >= beamStartFrames) {
                onHitFired = true;
            }

            if (retracting) {
                endTime++;
                if (endTime >= beamEndFrames) {
                    cleanup();
                    return true;
                }
            } else if (time >= duration) {
                retracting = true;
            }

            applyFrame();
            return false;
        }

        void applyFrame() {
            // Startup: tip grows from origin to hit over beamStartFrames. End stays at origin so
            // the beam is full-length the moment startup completes. During retract, both tip and
            // end stay anchored (full length); the beam dissolves by tapering its WIDTH to zero
            // instead of shrinking lengthwise, which would read as the laser visibly slowing.
            Vector tip = VectorUtil.lerp(origin, hit, NumberUtil.clamp01(startFrame / (double) beamStartFrames));
            Vector end = origin;
            Vector vector = tip.clone().subtract(end);
            double length = vector.length();
            if (length < 1.0e-6) return;

            Vector3f direction = new Vector3f(
                (float) (vector.getX() / length),
                (float) (vector.getY() / length),
                (float) (vector.getZ() / length));

            // The visible beam's back-end sits at `end` in world space. Translation = end - anchor
            // moves the rendered geometry's local origin from the entity anchor to `end`.
            Vector3f baseTranslation = new Vector3f(
                (float) (end.getX() - entityAnchor.getX()),
                (float) (end.getY() - entityAnchor.getY()),
                (float) (end.getZ() - entityAnchor.getZ()));

            float retractFactor = retracting && beamEndFrames > 0
                ? (float) (1.0 - NumberUtil.clamp01(endTime / (double) beamEndFrames))
                : 1.0f;
            float coreWidth = (float) beamWidth * retractFactor;
            double glowSin = Math.sin(time / (double) glowPeriod * Math.PI) / 2.0 + 0.5;
            float currentGlow = (float) (glowMinWidth + (glowMaxWidth - glowMinWidth) * glowSin) * retractFactor;

            ((FakeDisplayEntity) inner).setTransformation(buildTransformation(baseTranslation, direction, coreWidth, (float) length));
            ((FakeDisplayEntity) outer).setTransformation(buildTransformation(baseTranslation, direction, currentGlow, (float) length));
            ((FakeDisplayEntity) inner).setInterpolationDuration(1);
            ((FakeDisplayEntity) outer).setInterpolationDuration(1);
            inner.updateMeta();
            outer.updateMeta();
        }

        /**
         * Builds the Transformation that renders a centered slab of {@code width x length x width}
         * starting at the entity origin + {@code baseTranslation} (which puts it at the world
         * position {@code end}) and extending along {@code direction}. The leftRotation maps the
         * block's local +Y axis to {@code direction}; perpendicular translation centers the slab
         * on the beam line.
         */
        private @NotNull Transformation buildTransformation(@NotNull Vector3f baseTranslation,
                                                            @NotNull Vector3f direction,
                                                            float width, float length) {
            Quaternionf rot = new Quaternionf().rotationTo(new Vector3f(0f, 1f, 0f), direction);
            Vector3f perpX = rot.transform(new Vector3f(1f, 0f, 0f), new Vector3f());
            Vector3f perpZ = rot.transform(new Vector3f(0f, 0f, 1f), new Vector3f());
            Vector3f translation = new Vector3f(baseTranslation)
                .add(perpX.mul(-width / 2f))
                .add(perpZ.mul(-width / 2f));
            Vector3f scale = new Vector3f(width, length, width);
            return new Transformation(translation, rot, scale, new Quaternionf());
        }

        @Override
        public @NotNull Location getTickLocation() {
            return new Location(world, hit.getX(), hit.getY(), hit.getZ());
        }

        @Override
        public void remove() {
            cleanup();
        }

        private void cleanup() {
            if (removed) return;
            removed = true;
            inner.remove();
            outer.remove();
        }
    }
}
