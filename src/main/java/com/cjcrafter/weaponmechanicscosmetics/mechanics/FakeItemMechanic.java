/*
 * Copyright (c) 2026. All rights reserved. Distribution of this file, similar
 * files, related files, or related projects is strictly controlled.
 */

package com.cjcrafter.weaponmechanicscosmetics.mechanics;

import com.cjcrafter.weaponmechanicscosmetics.WeaponMechanicsCosmetics;
import me.deecaad.core.MechanicsCore;
import me.deecaad.core.compatibility.CompatibilityAPI;
import me.deecaad.core.compatibility.entity.FakeEntity;
import me.deecaad.core.file.SerializeData;
import me.deecaad.core.file.SerializerException;
import me.deecaad.core.file.serializers.AnyVectorProvider;
import me.deecaad.core.file.serializers.ItemSerializer;
import me.deecaad.core.file.serializers.VectorProvider;
import me.deecaad.core.file.serializers.VectorSerializer;
import me.deecaad.core.mechanics.CastData;
import me.deecaad.core.mechanics.defaultmechanics.Mechanic;
import me.deecaad.core.utils.EntityTransform;
import me.deecaad.core.utils.ImmutableVector;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.joml.Quaterniond;

public class FakeItemMechanic extends Mechanic {

    private ItemStack item;
    private VectorProvider velocity;
    private int time;

    /**
     * Default constructor for serializers.
     */
    public FakeItemMechanic() {
    }

    public FakeItemMechanic(ItemStack item, VectorProvider velocity, int time) {
        this.item = item;
        this.velocity = velocity;
        this.time = time;
    }

    @Override
    public void use0(CastData cast) {
        Location location = cast.hasTargetLocation() ? cast.getTargetLocation() : cast.getTarget().getEyeLocation();

        FakeEntity entity = CompatibilityAPI.getEntityCompatibility().generateFakeEntity(location, item);
        EntityTransform localTransform = cast.getTarget() == null ? null : new EntityTransform(cast.getTarget());
        Quaterniond localRotation = localTransform == null ? null : localTransform.getLocalRotation();

        entity.setMotion(velocity.provide(localRotation).multiply(1.0 / 20.0));
        entity.show();

        // We do not need to consume this task since it only serves to delete
        // the existing entity.
        WeaponMechanicsCosmetics.getInstance().getFoliaScheduler().region(location).runDelayed(() -> entity.remove(), time);
    }

    @Override
    public @NotNull NamespacedKey getKey() {
        return new NamespacedKey(MechanicsCore.getInstance(), "fakeitem");
    }

    @Override
    public @NotNull Mechanic serialize(@NotNull SerializeData data) throws SerializerException {

        ItemStack item = new ItemSerializer().serialize(data);
        int time = data.of("Time").assertRange(1, null).getInt().orElse(100);
        VectorProvider zero = new AnyVectorProvider(false, new ImmutableVector());
        VectorProvider velocity = data.of("Velocity").serialize(VectorSerializer.class).orElse(zero);

        return applyParentArgs(data, new FakeItemMechanic(item, velocity, time));
    }
}