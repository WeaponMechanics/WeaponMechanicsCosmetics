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
import me.deecaad.core.file.serializers.ColorSerializer;
import me.deecaad.core.mechanics.CastData;
import me.deecaad.core.mechanics.defaultmechanics.Mechanic;
import me.deecaad.core.tick.Tickable;
import net.kyori.adventure.text.Component;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Display;
import org.bukkit.entity.Player;
import org.bukkit.util.Transformation;
import org.jetbrains.annotations.NotNull;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Mechanic that puts a box of display entities around the head of a player,
 * simulating a flash blinding effect.
 */
public class BlindingMechanic extends Mechanic {

    private static final Transformation[] FACE_TRANSFORMS = buildFaceTransforms();


    private Color color;
    private int fadeInTicks;
    private int holdTicks;
    private int fadeOutTicks;

    public BlindingMechanic() {
    }

    public BlindingMechanic(Color color, int fadeInTicks, int holdTicks, int fadeOutTicks) {
        this.color = color;
        this.fadeInTicks = fadeInTicks;
        this.holdTicks = holdTicks;
        this.fadeOutTicks = fadeOutTicks;
    }

    private static Transformation[] buildFaceTransforms() {
        float halfPi = (float) (Math.PI / 2.0);
        Quaternionf[] faces = new Quaternionf[]{
                new Quaternionf(),                       // front
                new Quaternionf().rotateY(halfPi),       // right
                new Quaternionf().rotateY((float) Math.PI), // back
                new Quaternionf().rotateY(3f * halfPi),  // left
                new Quaternionf().rotateX(halfPi),       // top
                new Quaternionf().rotateX(-halfPi)       // bottom
        };
        Vector3f baseT = new Vector3f(-0.25f, -1.25f, -1.25f);
        Transformation[] out = new Transformation[faces.length];
        for (int i = 0; i < faces.length; i++) {
            Vector3f rotated = faces[i].transform(new Vector3f(baseT));
            out[i] = new Transformation(
                    rotated,
                    new Quaternionf(faces[i]),
                    new Vector3f(20f, 10f, 1f),
                    new Quaternionf());
        }
        return out;
    }

    @Override
    protected void use0(CastData cast) {
        Player player = cast.getTarget() instanceof Player p ? p : null;
        if (player == null)
            return;

        EntityCompatibility compatibility = CompatibilityAPI.getCompatibility().getEntityCompatibility();

        FakeEntity[] faces = new FakeEntity[FACE_TRANSFORMS.length];
        for (int i = 0; i < FACE_TRANSFORMS.length; i++) {
            FakeEntity panel = compatibility.generateFakeTextDisplay(player.getEyeLocation(), Component.text(" "));
            FakeDisplayEntity display = (FakeDisplayEntity) panel;
            display.setBrightness(new Display.Brightness(15, 15));
            display.setTeleportDuration(1);
            display.setTransformation(FACE_TRANSFORMS[i]);
            display.setDisplayWidth(5f);
            display.setDisplayHeight(5f);
            display.setViewRange(4f);
            display.setBackgroundColor(Color.fromARGB(0, color.getRed(), color.getGreen(), color.getBlue()));
            panel.updateMeta();
            panel.show(player);
            faces[i] = panel;
        }

        BlindingTicker ticker = new BlindingTicker(player, faces);
        MechanicsCore.getInstance().getTickManager().add(ticker);
    }

    @Override
    public @NotNull NamespacedKey getKey() {
        return new NamespacedKey(MechanicsCore.getInstance(), "blinding");
    }

    @Override
    public @NotNull Mechanic serialize(@NotNull SerializeData data) throws SerializerException {
        Color color = data.of("Color").serialize(ColorSerializer.class).orElse(Color.WHITE);
        int fadeInTicks = data.of("Fade_In_Ticks").assertRange(0, null).getInt().orElse(3);
        int holdTicks = data.of("Hold_Ticks").assertRange(0, null).getInt().orElse(20);
        int fadeOutTicks = data.of("Fade_Out_Ticks").assertRange(0, null).getInt().orElse(10);

        return applyParentArgs(data, new BlindingMechanic(color, fadeInTicks, holdTicks, fadeOutTicks));
    }

    private final class BlindingTicker implements Tickable {

        private final Player player;
        private final FakeEntity[] faces;
        private final int total;
        private int age;
        private boolean removed;

        BlindingTicker(Player player, FakeEntity[] faces) {
            this.player = player;
            this.faces = faces;
            this.total = fadeInTicks + holdTicks + fadeOutTicks;
        }

        @Override
        public boolean tick() {
            if (age >= total || !player.isOnline()) {
                cleanup();
                return true;
            }

            double opacity;
            if (age < fadeInTicks) {
                opacity = fadeInTicks == 0 ? 1.0 : age / (double) fadeInTicks;
            } else if (age < fadeInTicks + holdTicks) {
                opacity = 1.0;
            } else {
                opacity = fadeOutTicks == 0 ? 0.0
                        : 1.0 - (age - fadeInTicks - holdTicks) / (double) fadeOutTicks;
            }
            int alpha = (int) Math.max(0, Math.min(255, Math.round(opacity * 255)));
            Color tinted = Color.fromARGB(alpha, color.getRed(), color.getGreen(), color.getBlue());

            Location eye = player.getEyeLocation();
            for (FakeEntity panel : faces) {
                panel.setPosition(eye.toVector(), 0f, 0f);
                FakeDisplayEntity display = (FakeDisplayEntity) panel;
                display.setBackgroundColor(tinted);
                panel.updateMeta();
            }

            age++;
            return false;
        }

        @Override
        public @NotNull Location getTickLocation() {
            return player.getLocation();
        }

        @Override
        public void remove() {
            cleanup();
        }

        private void cleanup() {
            if (removed) return;
            removed = true;
            for (FakeEntity panel : faces)
                panel.remove();
        }
    }
}
