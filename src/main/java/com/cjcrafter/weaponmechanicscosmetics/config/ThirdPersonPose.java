/*
 * Copyright (c) 2026. All rights reserved. Distribution of this file, similar
 * files, related files, or related projects is strictly controlled.
 */

package com.cjcrafter.weaponmechanicscosmetics.config;

import com.github.retrooper.packetevents.protocol.item.ItemStack;
import com.github.retrooper.packetevents.resources.ResourceLocation;
import io.github.retrooper.packetevents.util.SpigotConversionUtil;
import me.deecaad.core.file.SerializeData;
import me.deecaad.core.file.Serializer;
import me.deecaad.core.file.SerializerException;
import me.deecaad.core.file.serializers.ItemSerializer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Locale;

import static com.github.retrooper.packetevents.protocol.component.builtin.item.ItemConsumable.Animation;

public final class ThirdPersonPose implements Serializer<ThirdPersonPose> {

    private PoseOverride defaultPose;
    private PoseOverride scopePose;
    private PoseOverride reloadPose;
    private PoseOverride firearmActionPose;
    private PoseOverride runningPose;

    /**
     * Default constructor for serializer
     */
    public ThirdPersonPose() {
    }

    public ThirdPersonPose(@NotNull PoseOverride defaultPose,
                           @NotNull PoseOverride scopePose,
                           @NotNull PoseOverride reloadPose,
                           @NotNull PoseOverride firearmActionPose,
                           @NotNull PoseOverride runningPose) {
        this.defaultPose = defaultPose;
        this.scopePose = scopePose;
        this.reloadPose = reloadPose;
        this.firearmActionPose = firearmActionPose;
        this.runningPose = runningPose;
    }

    public @NotNull PoseOverride getDefaultPose() {
        return defaultPose;
    }

    public @NotNull PoseOverride getScopePose() {
        return scopePose;
    }

    public @NotNull PoseOverride getReloadPose() {
        return reloadPose;
    }

    public @NotNull PoseOverride getFirearmActionPose() {
        return firearmActionPose;
    }

    public @NotNull PoseOverride getRunningPose() {
        return runningPose;
    }

    @Override
    public @NotNull List<String> getParentKeywords() {
        return List.of("Cosmetics");
    }

    @Override
    public String getKeyword() {
        return "Third_Person_Pose";
    }

    @Override
    public @NotNull ThirdPersonPose serialize(@NotNull SerializeData data) throws SerializerException {
        PoseOverride defaultPose = data.of("Default").serialize(PoseOverride.class).orElse(new PoseOverride());
        PoseOverride scopePose = data.of("Scope").serialize(PoseOverride.class).orElse(defaultPose);
        PoseOverride reloadPose = data.of("Reload").serialize(PoseOverride.class).orElse(defaultPose);
        PoseOverride firearmActionPose = data.of("Firearm_Action").serialize(PoseOverride.class).orElse(defaultPose);
        PoseOverride runningPose = data.of("Running").serialize(PoseOverride.class).orElse(defaultPose);

        return new ThirdPersonPose(defaultPose, scopePose, reloadPose, firearmActionPose, runningPose);
    }

    public record PoseOverride(@NotNull Animation pose,
                               @Nullable ItemStack overrideItem, @Nullable ResourceLocation overrideItemModel, @Nullable Integer overrideCustomModelData) implements Serializer<PoseOverride> {
        public PoseOverride() {
            this(Animation.NONE, null, null, null);
        }

        @Override
        public @NotNull PoseOverride serialize(@NotNull SerializeData data) throws SerializerException {
            // simple formatting, no item override options
            if (data.of().is(String.class)) {
                Animation pose = parsePose(data, data.of());
                return new PoseOverride(pose, null, null, null);
            }

            Animation pose = parsePose(data, data.of("Pose"));

            ItemStack overrideItem = null;
            ResourceLocation itemModel = null;
            Integer cmd = null;

            if (data.has("Override_Visual_Item")) {
                var ov = data.of("Override_Visual_Item");

                if (ov.is(String.class)) {
                    String key = ov.get(String.class).orElse(null);
                    if (key != null) itemModel = new ResourceLocation(key);
                } else {
                    String key = data.of("Override_Visual_Item.Item_Model").get(String.class).orElse(null);
                    if (key != null) itemModel = new ResourceLocation(key);

                    var opt = data.of("Override_Visual_Item.Custom_Model_Data").getInt();
                    if (opt.isPresent()) cmd = opt.getAsInt();

                    if (data.has("Override_Visual_Item.Type")) {
                        org.bukkit.inventory.ItemStack overrideBukkit =
                                data.of("Override_Visual_Item").serialize(ItemSerializer.class).orElse(null);
                        if (overrideBukkit != null) {
                            overrideItem = SpigotConversionUtil.fromBukkitItemStack(overrideBukkit);
                        }
                    }
                }
            }
            return new PoseOverride(pose, overrideItem, itemModel, cmd);
        }

        private static final String USER_FACING_POSES =
                "NONE, EAT, DRINK, BLOCK, BOW, SPEAR, CROSSBOW, SPYGLASS, TOOT_HORN, BRUSH, BUNDLE, TRIDENT";

        private static @NotNull Animation parsePose(
                @NotNull SerializeData data,
                @NotNull SerializeData.ConfigAccessor accessor
        ) throws SerializerException {
            String raw = accessor.get(String.class).orElse(null);

            if (raw == null || raw.isBlank()) {
                return Animation.NONE;
            }

            String normalized = raw.trim()
                    .toUpperCase(Locale.ROOT)
                    .replace('-', '_')
                    .replace(' ', '_');

            // future-safe: if PacketEvents adds TRIDENT later, this will start using it automatically.
            try {
                return Animation.valueOf(normalized);
            } catch (IllegalArgumentException ignored) {
                return switch (normalized) {
                    case "TRIDENT" -> Animation.SPEAR;
                    case "GOAT_HORN", "HORN" -> Animation.TOOT_HORN;
                    default -> throw data.exception(
                            "Unknown Third_Person_Pose animation '" + raw + "' at '" + accessor.getLocation() + "'.",
                            "Supported values: " + USER_FACING_POSES,
                            "Aliases: GOAT_HORN and HORN map to TOOT_HORN."
                    );
                };
            }
        }

    }
}
