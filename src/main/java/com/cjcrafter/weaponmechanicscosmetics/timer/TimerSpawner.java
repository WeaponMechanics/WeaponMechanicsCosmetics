/*
 * Copyright (c) 2022-2026. All rights reserved. Distribution of this file, similar
 * files, related files, or related projects is strictly controlled.
 */

package com.cjcrafter.weaponmechanicscosmetics.timer;

import com.cjcrafter.foliascheduler.ServerImplementation;
import com.cjcrafter.weaponmechanicscosmetics.WeaponMechanicsCosmetics;
import me.deecaad.core.events.EntityEquipmentEvent;
import me.deecaad.core.file.Configuration;
import me.deecaad.weaponmechanics.WeaponMechanics;
import me.deecaad.weaponmechanics.weapon.reload.ammo.AmmoConfig;
import me.deecaad.weaponmechanics.weapon.weaponevents.*;
import me.deecaad.weaponmechanics.wrappers.PlayerWrapper;
import org.bukkit.inventory.ItemStack;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class TimerSpawner implements Listener {

    private final Set<Player> playersReloading;
    private final Map<Player, TimerData> tasks;

    public TimerSpawner() {
        playersReloading = new HashSet<>();
        tasks = new HashMap<>();
    }

    @EventHandler
    public void onEquip(WeaponEquipEvent event) {
        // We have to run this 1 tick later, since otherwise the timer would
        // be cancelled by onDequip(PlayerItemHeldEvent).
        ServerImplementation scheduler = WeaponMechanicsCosmetics.getInstance().getFoliaScheduler();
        scheduler.entity(event.getShooter()).run(() -> {
            playTimer(event, ".Show_Time.Weapon_Equip_Delay", ".Info.Weapon_Equip_Delay");
        });
    }

    @EventHandler (priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onMeleeHit(WeaponMeleeHitEvent event) {
        playTimer(event, ".Show_Time.Melee_Hit_Delay", event.getMeleeHitDelay());
    }

    @EventHandler (priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onMeleeMiss(WeaponMeleeMissEvent event) {
        playTimer(event, ".Show_Time.Melee_Miss_Delay", event.getMeleeMissDelay());
    }

    @EventHandler (priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onReload(WeaponReloadEvent event) {
        if (!(event.getShooter() instanceof Player player)) {
            playTimer(event, ".Show_Time.Reload", event.getReloadCompleteTime());
            return;
        }

        Configuration config = WeaponMechanics.getInstance().getWeaponConfigurations();
        String weaponTitle = event.getWeaponTitle();
        ItemStack weaponStack = event.getWeaponStack();

        PlayerWrapper wrapper = WeaponMechanics.getInstance().getPlayerWrapper(player);
        AmmoConfig ammo = config.getObject(weaponTitle + ".Reload.Ammo", AmmoConfig.class);

        // Show time should not get triggered if there's no ammo ready to be reloaded
        if (ammo != null && !ammo.hasAmmo(weaponTitle, weaponStack, wrapper)) {
            return;
        }

        playTimer(event, ".Show_Time.Reload", event.getReloadCompleteTime());
    }

    @EventHandler (priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onScope(WeaponScopeEvent event) {
        if (event.getScopeType() != WeaponScopeEvent.ScopeType.IN)
            return;

        playTimer(event, ".Show_Time.Shoot_Delay_After_Scope", ".Scope.Shoot_Delay_After_Scope");
    }

    @EventHandler (priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onFirearm(WeaponFirearmEvent event) {
        playTimer(event, ".Show_Time.Firearm_Actions", event.getTime());
    }

    @EventHandler
    public void onShoot(WeaponPostShootEvent event) {
        // weaponStack is null, it was shot through the API
        if (event.getWeaponStack() == null)
            return;

        playTimer(event, ".Show_Time.Delay_Between_Shots", ".Shoot.Delay_Between_Shots");
    }

    private void playTimer(WeaponEvent event, String timerPath, String delayPath) {
        Configuration config = WeaponMechanics.getInstance().getWeaponConfigurations();
        int ticks = config.getInt(event.getWeaponTitle() + delayPath) / 50; // divide by 50 for millis -> ticks
        playTimer(event, timerPath, ticks);
    }

    /**
     * Pulls the timer from config and starts it with the given amount of time,
     * in ticks. If the amount of ticks is defined in config, use
     * {@link #playTimer(WeaponEvent, String, String)}.
     *
     * @param event     The non-null event that triggered the timer.
     * @param timerPath The path to the {@link Timer} in config.
     * @param ticks     The time, in ticks, to play the timer.
     */
    private void playTimer(WeaponEvent event, String timerPath, int ticks) {
        if (!(event.getShooter() instanceof Player player))
            return;

        Configuration config = WeaponMechanics.getInstance().getWeaponConfigurations();
        Timer timer = config.getObject(event.getWeaponTitle() + timerPath, Timer.class);
        if (timer == null)
            return;

        // Keep track of when we add a reload task, so we don't override it
        if (event instanceof WeaponReloadEvent) {
            playersReloading.add(player);
        } else if (playersReloading.contains(player)) {
            // If the player is reloading, we don't want to override the reload
            // task with a different task.
            return;
        }

        TimerData task = timer.play(player, event.getWeaponStack().clone(), ticks);
        TimerData old = tasks.put(task.player, task);
        if (old != null)
            old.cancel();
    }


    //
    // * TIMER CANCEL HANDLER CODE
    //
    // The following code handlers cancelling the timer(s) when the player
    // stops it (Via swapping hands, for example).

    @EventHandler
    public void onReloadCancel(WeaponReloadCancelEvent event) {
        if (!(event.getShooter() instanceof Player player))
            return;

        playersReloading.remove(player);
        TimerData task = tasks.get(player);
        if (task == null)
            return;

        task.cancel();
    }

    @EventHandler
    public void onReloadComplete(WeaponReloadCompleteEvent event) {
        if (!(event.getShooter() instanceof Player player))
            return;

        playersReloading.remove(player);
        tasks.remove(player);
    }

    @EventHandler
    public void onDequip(EntityEquipmentEvent event) {
        if (!event.isDequipping() || event.isArmor())
            return;
        if (!(event.getEntity() instanceof Player player))
            return;

        playersReloading.remove(player);
        TimerData task = tasks.remove(player);
        if (task == null)
            return;

        task.cancel();
    }
}
