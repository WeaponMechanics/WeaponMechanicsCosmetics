/*
 * Copyright (c) 2026. All rights reserved. Distribution of this file, similar
 * files, related files, or related projects is strictly controlled.
 */

package com.cjcrafter.weaponmechanicscosmetics.mechanics;

import me.deecaad.core.MechanicsCore;
import me.deecaad.core.file.SerializeData;
import me.deecaad.core.file.SerializerException;
import me.deecaad.core.mechanics.CastData;
import me.deecaad.core.mechanics.defaultmechanics.Mechanic;
import me.deecaad.core.tick.Tickable;
import me.deecaad.weaponmechanics.compatibility.IWeaponCompatibility;
import me.deecaad.weaponmechanics.compatibility.WeaponCompatibilityAPI;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Smooth sinusoidal camera shake via pitch and yaw oscillation at independent
 * periods with linearly decaying amplitude.
 */
public class CameraShakeMechanic extends Mechanic {

    private double magnitude;
    private double decay;
    private double pitchPeriod;
    private double yawPeriod;
    private int maxDurationTicks;

    public CameraShakeMechanic() {
    }

    public CameraShakeMechanic(double magnitude, double decay, double pitchPeriod, double yawPeriod, int maxDurationTicks) {
        this.magnitude = magnitude;
        this.decay = decay;
        this.pitchPeriod = pitchPeriod;
        this.yawPeriod = yawPeriod;
        this.maxDurationTicks = maxDurationTicks;
    }

    @Override
    protected void use0(CastData cast) {
        Player player = cast.getTarget() instanceof Player p ? p : null;
        if (player == null)
            return;

        ShakeTicker ticker = new ShakeTicker(player);
        MechanicsCore.getInstance().getTickManager().add(ticker);
    }

    @Override
    public @NotNull NamespacedKey getKey() {
        return new NamespacedKey(MechanicsCore.getInstance(), "camerashake");
    }

    @Override
    public @NotNull Mechanic serialize(@NotNull SerializeData data) throws SerializerException {
        double magnitude = data.of("Magnitude").assertRange(0.0, null).getDouble().orElse(1.2);
        double decay = data.of("Decay").assertRange(0.0, null).getDouble().orElse(0.04);
        double pitchPeriod = data.of("Pitch_Period").assertRange(0.01, null).getDouble().orElse(3.7);
        double yawPeriod = data.of("Yaw_Period").assertRange(0.01, null).getDouble().orElse(3.0);
        int maxDurationTicks = data.of("Max_Duration").assertRange(1, null).getInt().orElse(200);

        return applyParentArgs(data, new CameraShakeMechanic(
            magnitude, decay, pitchPeriod, yawPeriod, maxDurationTicks));
    }

    private final class ShakeTicker implements Tickable {

        private final Player player;
        private double prevYaw;
        private double prevPitch;
        private double currentMagnitude;
        private int time;

        ShakeTicker(Player player) {
            this.player = player;
            this.currentMagnitude = magnitude;
        }

        @Override
        public boolean tick() {
            time++;
            currentMagnitude -= decay;
            if (currentMagnitude < 0.0 || time >= maxDurationTicks || !player.isOnline()) {
                return true;
            }

            double pitch = Math.sin(time / pitchPeriod * 2.0 * Math.PI) * currentMagnitude;
            double yaw = Math.cos(time / yawPeriod * 2.0 * Math.PI) * currentMagnitude;

            IWeaponCompatibility compat = WeaponCompatibilityAPI.getWeaponCompatibility();

            double relativePitch = pitch - prevPitch;
            double relativeYaw = yaw - prevYaw;
            prevPitch = pitch;
            prevYaw = yaw;
            compat.modifyCameraRotation(player, (float) relativeYaw, (float) relativePitch, false);
            return false;
        }

        @Override
        public @NotNull Location getTickLocation() {
            return player.getLocation();
        }
    }
}
