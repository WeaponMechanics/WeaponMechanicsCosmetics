/*
 * Copyright (c) 2022-2026. All rights reserved. Distribution of this file, similar
 * files, related files, or related projects is strictly controlled.
 */

package com.cjcrafter.weaponmechanicscosmetics.scripts;

import com.cjcrafter.foliascheduler.ServerImplementation;
import com.cjcrafter.weaponmechanicscosmetics.WeaponMechanicsCosmetics;
import com.cjcrafter.weaponmechanicscosmetics.config.BlockParticleSerializer;
import com.cjcrafter.weaponmechanicscosmetics.config.BlockSoundSerializer;
import me.deecaad.core.file.Configuration;
import me.deecaad.core.utils.ray.BlockTraceResult;
import me.deecaad.core.utils.ray.RayTraceResult;
import me.deecaad.weaponmechanics.WeaponMechanics;
import me.deecaad.weaponmechanics.weapon.damage.BlockDamageData;
import me.deecaad.weaponmechanics.weapon.explode.BlockDamage;
import me.deecaad.weaponmechanics.weapon.projectile.ProjectileScript;
import me.deecaad.weaponmechanics.weapon.projectile.weaponprojectile.WeaponProjectile;
import org.bukkit.block.BlockState;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;

public class BlockDamageScript extends ProjectileScript<WeaponProjectile> {

    private BlockDamage damage;
    private BlockSoundSerializer sounds;
    private BlockParticleSerializer particles;
    private int regenDelay;

    public BlockDamageScript(@NotNull Plugin owner, @NotNull WeaponProjectile projectile) {
        super(owner, projectile);

        Configuration config = WeaponMechanics.getInstance().getWeaponConfigurations();
        this.damage = config.getObject(projectile.getWeaponTitle() + ".Cosmetics.Block_Damage", BlockDamage.class);
        this.regenDelay = config.getInt(projectile.getWeaponTitle() + ".Cosmetics.Block_Damage.Ticks_Before_Regenerate", -1);

        config = WeaponMechanicsCosmetics.getInstance().getConfiguration();
        this.sounds = config.getObject("Break_Sounds", BlockSoundSerializer.class);
        this.particles = config.getObject("Break_Particles", BlockParticleSerializer.class);
    }

    public BlockDamageScript(@NotNull Plugin owner, @NotNull WeaponProjectile projectile, BlockDamage damage,
                             BlockSoundSerializer sounds, BlockParticleSerializer particles, int regenDelay) {
        super(owner, projectile);

        this.damage = damage;
        this.sounds = sounds;
        this.particles = particles;
        this.regenDelay = regenDelay;
    }

    public BlockDamage getDamage() {
        return damage;
    }

    public void setDamage(BlockDamage damage) {
        this.damage = damage;
    }

    public int getRegenDelay() {
        return regenDelay;
    }

    public void setRegenDelay(int regenDelay) {
        this.regenDelay = regenDelay;
    }

    @Override
    public void onCollide(@NotNull RayTraceResult hit) {
        if (damage == null || !(hit instanceof BlockTraceResult blockHit) || blockHit.getBlock().isLiquid())
            return;

        // Save the state of the block before it is broken
        BlockState state = blockHit.getBlockState();

        LivingEntity shooter = projectile.getShooter();
        Player player = shooter != null && shooter.getType() == EntityType.PLAYER ? (Player) shooter : null;
        BlockDamageData.DamageData data = damage.damage(blockHit.getBlock(), player, regenDelay != -1);

        // Didn't damage block
        if (data == null)
            return;

        // Play effects from config.yml
        if (data.isBroken() && damage.getBreakMode(state.getType().asBlockType()) == BlockDamage.BreakMode.BREAK) {
            sounds.play(projectile, state);
            particles.play(projectile, state, null, null);
        }

        boolean broken = data.isBroken();
        BlockDamage.BreakMode mode = damage.getBreakMode(state.getType().asBlockType());

        if (regenDelay != -1) {
            if (mode == BlockDamage.BreakMode.BREAK && broken) {
                ServerImplementation scheduler = WeaponMechanicsCosmetics.getInstance().getFoliaScheduler();
                scheduler.region(state.getLocation()).runDelayed(() -> {
                    data.regenerate();
                    data.remove();
                }, regenDelay);
            }
        } else if (broken) {
            data.remove();
        }

        if (broken && mode == BlockDamage.BreakMode.BREAK) {
            try {
                sounds.play(projectile, state);
                particles.play(projectile, state, null, null);
            } catch (Throwable t) {
                WeaponMechanicsCosmetics.getInstance().getDebugger().warning("Couldn't play block damage: " + t.getMessage());
            }
        }
    }
}
