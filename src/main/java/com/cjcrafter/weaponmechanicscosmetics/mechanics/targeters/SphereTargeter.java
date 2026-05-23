/*
 * Copyright (c) 2026. All rights reserved. Distribution of this file, similar
 * files, related files, or related projects is strictly controlled.
 */

package com.cjcrafter.weaponmechanicscosmetics.mechanics.targeters;

import me.deecaad.core.MechanicsCore;
import me.deecaad.core.file.SerializeData;
import me.deecaad.core.file.SerializerException;
import me.deecaad.core.mechanics.CastData;
import me.deecaad.core.mechanics.Targeters;
import me.deecaad.core.mechanics.targeters.ShapeTargeter;
import me.deecaad.core.mechanics.targeters.Targeter;
import org.bukkit.NamespacedKey;
import org.bukkit.util.Vector;
import org.jetbrains.annotations.NotNull;

import java.util.Iterator;

public class SphereTargeter extends ShapeTargeter {

    public static final double GOLDEN_RATIO = (1.0 + Math.sqrt(5)) / 2.0;

    private Vector[] points;

    /**
     * Default constructor for serializer
     */
    public SphereTargeter() {
    }

    public SphereTargeter(int points, double radius) {
        this.points = new Vector[points];

        double phi = GOLDEN_RATIO;

        for (int i = 0; i < points; i++) {
            double y = 1 - (i / ((double) points - 1)) * 2;
            double r = Math.sqrt(1 - y * y);

            // y *= (radius / r); // Creates a cool diamond like shape

            double theta = phi * i;

            double x = r * Math.cos(theta);
            double z = r * Math.sin(theta);
            this.points[i] = new Vector(x * radius, y * radius, z * radius);
        }
    }

    @Override
    public @NotNull Iterator<Vector> getPoints(@NotNull CastData data) {
        return new Iterator<>() {
            int i = 0;
            @Override
            public boolean hasNext() {
                return i < points.length;
            }

            @Override
            public Vector next() {
                return points[i++];
            }
        };
    }

    public @NotNull NamespacedKey getKey() {
        return new NamespacedKey(MechanicsCore.getInstance(), "sphere");
    }

    @Override
    public @NotNull Targeter serialize(@NotNull SerializeData data) throws SerializerException {
        Targeter shape = data.of("Shape").serializeRegistry(Targeters.REGISTRY).orElse(null);

        if (shape != null && !(shape instanceof me.deecaad.core.mechanics.targeters.ShapeTargeter)) {
            throw data.exception("Shape", "Expected a shape targeter (Sphere, Scatter, Helix, etc.)");
        }

        int points = data.of("Points").assertExists().getInt().getAsInt();
        double radius = data.of("Radius").assertExists().getDouble().getAsDouble();

        return applyParentArgs(data, new SphereTargeter(points, radius));
    }
}
