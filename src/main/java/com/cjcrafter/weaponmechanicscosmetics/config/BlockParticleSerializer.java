/*
 * Copyright (c) 2026. All rights reserved. Distribution of this file, similar
 * files, related files, or related projects is strictly controlled.
 */

package com.cjcrafter.weaponmechanicscosmetics.config;

import com.cryptomorin.xseries.particles.XParticle;
import com.cjcrafter.weaponmechanicscosmetics.mechanics.ParticleMechanic;
import me.deecaad.core.file.SerializeData;
import me.deecaad.core.file.Serializer;
import me.deecaad.core.file.SerializerException;
import me.deecaad.core.file.simple.RegistryValueSerializer;
import me.deecaad.core.file.simple.StringSerializer;
import me.deecaad.core.mechanics.CastData;
import me.deecaad.core.mechanics.MechanicManager;
import me.deecaad.core.mechanics.Mechanics;
import me.deecaad.core.mechanics.defaultmechanics.Mechanic;
import me.deecaad.core.utils.EnumUtil;
import me.deecaad.core.utils.Quaternion;
import me.deecaad.core.utils.RandomUtil;
import me.deecaad.weaponmechanics.weapon.projectile.weaponprojectile.WeaponProjectile;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.BlockState;
import org.bukkit.block.BlockType;
import org.bukkit.util.Vector;
import org.jetbrains.annotations.NotNull;

import javax.annotation.Nullable;
import java.util.*;
import java.util.stream.Collectors;

public class BlockParticleSerializer implements Serializer<BlockParticleSerializer> {

    private static final Vector UP = new Vector(0, 1, 0);

    private int amount;
    private double spread;
    private Map<Material, ParticleMechanic> overrides;
    private Set<BlockType> materialBlacklist;
    private Set<String> weaponBlacklist;

    /**
     * Default constructor for serializer
     */
    public BlockParticleSerializer() {
    }

    public BlockParticleSerializer(int amount, double spread, Map<Material, ParticleMechanic> overrides,
                                   Set<BlockType> materialBlacklist, Set<String> weaponBlacklist) {
        this.amount = amount;
        this.spread = spread;
        this.overrides = overrides;
        this.materialBlacklist = materialBlacklist;
        this.weaponBlacklist = weaponBlacklist;
    }

    public void play(WeaponProjectile projectile, BlockState block, @Nullable Vector hitLocation, @Nullable Vector normal) {
        World world = projectile.getWorld();

        // Handle blacklists
        if (materialBlacklist.contains(block.getType().asBlockType()) || weaponBlacklist.contains(projectile.getWeaponTitle()))
            return;

        int amount = this.amount;
        double spread = this.spread;

        Vector safeUp = UP;

        // Handle overrides
        ParticleMechanic override = overrides.get(block.getType());
        if (override != null && projectile.getShooter() != null) {
            if (hitLocation == null)
                hitLocation = new Vector(block.getX() + 0.5, block.getY() + 0.5, block.getZ() + 0.5);

            Vector dir = normal;

            if (dir == null || dir.lengthSquared() == 0.0) {
                dir = projectile.getNormalizedMotion();
                if (dir == null || dir.lengthSquared() == 0.0) {
                    dir = new Vector(0, 0, 1); // last resort fallback
                }
            }

            dir = dir.clone().normalize();

            // If dir is (almost) parallel to UP, choose a different up vector to avoid degeneracy
            if (Math.abs(dir.dot(UP)) > 0.999) {
                safeUp = new Vector(1, 0, 0);
            }

            CastData cast = new CastData(projectile.getShooter(), projectile.getWeaponTitle(), projectile.getWeaponStack());
            cast.setTargetLocation(hitLocation.toLocation(world));

            Quaternion localRotation = Quaternion.lookAt(dir, safeUp);
            override.display(cast, localRotation);
            return;
        }

        // When there is no precise hit/normal, assume the block has been broken.
        // In this case, we want to spawn particles in all directions from the
        // center fo the block.
        if (hitLocation == null && normal == null) {
            Location spawnLoc = block.getLocation().add(0.5, 0.5, 0.5);
            world.spawnParticle(XParticle.BLOCK.get(), spawnLoc, amount, spread, spread, spread, block.getBlockData());
        }

        // We need both...
        else if (hitLocation == null || normal == null) {
            throw new IllegalArgumentException("Need both hitLocation and normal to be non-null (or both null)");
        }

        // When a precise hit/normal is defined, spray particles out from that
        // point and direction.
        else {
            Location spawnLoc = hitLocation.toLocation(world);
            for (int i = 0; i < amount; i++) {
                Vector direction = RandomUtil.onUnitSphere().multiply(spread).add(normal);
                world.spawnParticle(XParticle.BLOCK.get(), spawnLoc, 0, direction.getX(), direction.getY(), direction.getZ(), block.getBlockData());
            }
        }
    }

    @Override
    public String getKeyword() {
        return "Block_Particles";
    }

    @NotNull
    @Override
    public BlockParticleSerializer serialize(SerializeData data) throws SerializerException {

        // Let people completely disable the feature
        boolean enabled = data.of("Enabled").assertExists().getBool().get();
        if (!enabled) {
            return new BlockParticleSerializer() {
                @Override
                public void play(WeaponProjectile projectile, BlockState block, @Nullable Vector hitLocation, @Nullable Vector normal) {
                    // do nothing...
                }
            };
        }

        int amount = data.of("Amount").assertExists().assertRange(0, null).getInt().getAsInt();
        double spread = data.of("Spread").assertExists().assertRange(0.0, null).getDouble().getAsDouble();

        // Construct a list of overrides, so you can increase/decrease particles
        // per block. This could also be used to customize every block, so let's
        // take performance into consideration.
        Map<Material, ParticleMechanic> overrides = new EnumMap<>(Material.class);
        List<?> list = data.of("Overrides").get(List.class).orElse(List.of());
        for (int i = 0; i < list.size(); i++) {
            String str = list.get(i).toString();
            int split = str.indexOf(" ");

            if (split == -1)
                throw data.listException("Overrides", i, "Override format should be: <Material> <SoundMechanic>", "For value: " + str);

            // Extract and parse the data from the string
            String materialStr = str.substring(0, split);
            List<Material> materials = EnumUtil.parseEnums(Material.class, materialStr);
            String mechanicStr = str.substring(split + 1);
            Mechanic mechanic = new MechanicManager().serializeOne(data, mechanicStr);

            if (!(mechanic instanceof ParticleMechanic particleMechanic))
                throw data.listException("Overrides", i, "Override mechanic should only be ParticleMechanic, not '" + mechanic.getClass().getSimpleName() + "'");

            // Fill the overrides map
            for (Material mat : materials)
                overrides.put(mat, particleMechanic);
        }

        // Construct a list of materials that shouldn't have any effects.
        // We use a map since EnumMap is very fast, and there is no EnumSet
        // equivalent.
        Set<BlockType> materialBlacklist = new HashSet<>();
        List<List<Optional<Object>>> temp = data.ofList("Material_Blacklist")
            .addArgument(new RegistryValueSerializer<>(BlockType.class, true))
            .assertExists()
            .assertList();

        for (List<Optional<Object>> split : temp) {
            List<BlockType> materials = (List<BlockType>) split.get(0).get();

            for (BlockType mat : materials)
                materialBlacklist.add(mat);
        }

        // Construct a list of weapons that should not use the block
        // effects. We check each weapon to make sure it exists in
        // WeaponMechanics.
        Set<String> weaponBlacklist = data.ofList("Weapon_Blacklist")
            .addArgument(new StringSerializer())
            .assertExists()
            .assertList()
            .stream()
            .map(split -> split.get(0).get().toString())
            .collect(Collectors.toSet());

        return new BlockParticleSerializer(amount, spread, overrides, materialBlacklist, weaponBlacklist);
    }
}
