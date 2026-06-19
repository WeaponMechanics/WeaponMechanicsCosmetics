/*
 * Copyright (c) 2022-2026. All rights reserved. Distribution of this file, similar
 * files, related files, or related projects is strictly controlled.
 */

package com.cjcrafter.weaponmechanicscosmetics;

import com.cjcrafter.foliascheduler.TaskImplementation;
import com.cjcrafter.foliascheduler.util.ConstructorInvoker;
import com.cjcrafter.foliascheduler.util.ReflectionUtil;
import com.cjcrafter.weaponmechanicscosmetics.listeners.*;
import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.event.EventManager;
import com.github.retrooper.packetevents.event.PacketListenerPriority;
import com.cjcrafter.weaponmechanicscosmetics.commands.SkinCommand;
import com.cjcrafter.weaponmechanicscosmetics.timer.TimerSpawner;
import me.deecaad.core.MechanicsPlugin;
import me.deecaad.core.file.*;
import me.deecaad.core.mechanics.Conditions;
import me.deecaad.core.mechanics.Mechanics;
import me.deecaad.core.mechanics.Targeters;
import me.deecaad.core.mechanics.conditions.Condition;
import me.deecaad.core.mechanics.defaultmechanics.Mechanic;
import me.deecaad.core.mechanics.targeters.Targeter;
import me.deecaad.core.utils.FileUtil;
import me.deecaad.weaponmechanics.WeaponMechanics;
import me.deecaad.weaponmechanics.weapon.projectile.ProjectileSpawner;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.Style;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.permissions.PermissionDefault;
import org.bukkit.plugin.PluginManager;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.jar.JarFile;

public class WeaponMechanicsCosmetics extends MechanicsPlugin {

    private static WeaponMechanicsCosmetics INSTANCE;

    private @NotNull ClassLoader langLoader;

    private CrossbowPacketListener crossbowPacketListener;

    public WeaponMechanicsCosmetics() {
        super(Style.style(NamedTextColor.GOLD), Style.style(NamedTextColor.GRAY), 15790);
    }

    @Override
    public void onLoad() {
        INSTANCE = this;

        try {
            JarSearcher searcher = new JarSearcher(new JarFile(getFile()));
            searcher.findAllSubclasses(Mechanic.class, getClassLoader(), SearchMode.ON_DEMAND)
                    .stream()
                    .map(ReflectionUtil::getConstructor)
                    .map(ConstructorInvoker::newInstance)
                    .forEach(Mechanics.REGISTRY::add);
            searcher.findAllSubclasses(Targeter.class, getClassLoader(), SearchMode.ON_DEMAND)
                    .stream()
                    .map(ReflectionUtil::getConstructor)
                    .map(ConstructorInvoker::newInstance)
                    .forEach(Targeters.REGISTRY::add);
            searcher.findAllSubclasses(Condition.class, getClassLoader(), SearchMode.ON_DEMAND)
                    .stream()
                    .map(ReflectionUtil::getConstructor)
                    .map(ConstructorInvoker::newInstance)
                    .forEach(Conditions.REGISTRY::add);

        } catch (IOException ex) {
            debugger.severe("Failed to load mechanics/targeters/conditions", ex);
        }

        super.onLoad();
    }

    @Override
    public void onEnable() {
        super.onEnable();

        File langFolder = new File(getDataFolder(), "lang");
        if (!langFolder.exists() || langFolder.listFiles() == null || langFolder.listFiles().length == 0) {
            FileUtil.copyResourcesTo(getClassLoader().getResource("WeaponMechanicsCosmetics/lang"), langFolder.toPath());
        }
        try {
            langLoader = new URLClassLoader(new URL[]{ langFolder.toURI().toURL() });
        } catch (MalformedURLException e) {
            debugger.severe("Error while loading Lang", e);
        }
    }

    @Override
    public @NotNull CompletableFuture<Void> handleCommands() {
        SkinCommand.register();
        return super.handleCommands();
    }

    @Override
    public @NotNull CompletableFuture<Void> handleListeners() {
        PluginManager pm = getServer().getPluginManager();
        pm.registerEvents(new DeathMessageListener(), this);
        pm.registerEvents(new ExplosionEffectSpawner(), this);
        pm.registerEvents(new HitMarkerSpawner(), this);
        pm.registerEvents(new MuzzleFlashSpawner(), this);
        pm.registerEvents(new PumpkinScopeOverlay(), this);
        pm.registerEvents(new TimerSpawner(), this);
        pm.registerEvents(new WeaponMechanicsSerializerListener(), this);
        pm.registerEvents(new WeaponSkinListener(), this);
        if (crossbowPacketListener == null) {
            crossbowPacketListener = new CrossbowPacketListener(this);
        }
        pm.registerEvents(crossbowPacketListener, this);
        return super.handleListeners();
    }

    @Override
    public @NotNull CompletableFuture<Void> handlePacketListeners() {
        debugger.fine("Creating packet listeners");

        if (crossbowPacketListener == null) {
            crossbowPacketListener = new CrossbowPacketListener(this);
            Bukkit.getPluginManager().registerEvents(crossbowPacketListener, this);
        }

        EventManager em = PacketEvents.getAPI().getEventManager();
        em.registerListener(crossbowPacketListener, PacketListenerPriority.NORMAL);
        return super.handlePacketListeners();
    }

    @Override
    public @NotNull CompletableFuture<Void> handlePermissions() {
        debugger.fine("Registering permissions");
        foliaScheduler.global().runDelayed(() -> {
            SkinCommand.registerPermissions("Skin");
            SkinCommand.registerPermissions("Hand");
            Bukkit.getPluginManager().getPermission("weaponmechanicscosmetics.commands.handskin").setDefault(PermissionDefault.TRUE);
            Bukkit.getPluginManager().getPermission("weaponmechanicscosmetics.commands.skin").setDefault(PermissionDefault.TRUE);
        }, 2L);

        return super.handlePermissions();
    }

    @Override
    public @NotNull CompletableFuture<TaskImplementation<Void>> reload() {
        return super.reload().thenCompose((ignore) -> {
            ProjectileSpawner spawner = WeaponMechanics.getInstance().getProjectileSpawner();
            spawner.addScriptManager(new CosmeticsScriptManager(this));
            return CompletableFuture.completedFuture(null);
        });
    }

    public @NotNull String getLang(@NotNull String key) {
        Locale locale = Locale.forLanguageTag(configuration == null ? "en-US" : configuration.getString("Language", "en-US"));
        ResourceBundle lang = ResourceBundle.getBundle("Lang", locale, langLoader);

        try {
            return lang.getString(key);
        } catch (MissingResourceException ex) {
            debugger.fine("Missing key '" + key + "'", ex);
            debugger.warning("Found a missing language key '" + key + "' in 'Lang_" + locale + ".properties'");
            return "missing-lang-key";
        }
    }

    public void sendLang(@NotNull CommandSender sender, @NotNull String key) {
        sendLang(sender, key, Collections.emptyMap());
    }

    public void sendLang(@NotNull CommandSender sender, @NotNull String key, @NotNull Map<String, String> variables) {
        String msg = getLang(key);

        int startIndex = -1;

        StringBuilder temp = new StringBuilder(msg.length());
        for (int i = 0; i < msg.length(); i++) {
            char c = msg.charAt(i);
            if (c != '%') {
                if (startIndex == -1) temp.append(c);
                continue;
            }

            // Start tracking for substring
            if (startIndex == -1) {
                startIndex = i;
                continue;
            }

            // %% counts as escaped character
            if (i - startIndex == 1) {
                temp.append('%');
                continue;
            }

            String substring = msg.substring(startIndex + 1, i);
            temp.append(variables.get(substring));
            startIndex = -1;
        }

        Component component = MiniMessage.miniMessage().deserialize(temp.toString());
        sender.sendMessage(component);
    }

    public static WeaponMechanicsCosmetics getInstance() {
        return INSTANCE;
    }
}
