/*
 * Copyright (c) 2022-2026. All rights reserved. Distribution of this file, similar
 * files, related files, or related projects is strictly controlled.
 */

package com.cjcrafter.weaponmechanicscosmetics.commands;

import dev.jorel.commandapi.CommandAPICommand;
import dev.jorel.commandapi.IStringTooltip;
import dev.jorel.commandapi.StringTooltip;
import dev.jorel.commandapi.SuggestionInfo;
import dev.jorel.commandapi.arguments.ArgumentSuggestions;
import dev.jorel.commandapi.arguments.StringArgument;
import com.cjcrafter.weaponmechanicscosmetics.WeaponMechanicsCosmetics;
import me.deecaad.core.file.Configuration;
import me.deecaad.weaponmechanics.WeaponMechanics;
import me.deecaad.weaponmechanics.WeaponMechanicsAPI;
import me.deecaad.weaponmechanics.weapon.skin.SkinSelector;
import me.deecaad.weaponmechanics.weapon.stats.WeaponStat;
import me.deecaad.weaponmechanics.wrappers.PlayerWrapper;
import me.deecaad.weaponmechanics.wrappers.StatsData;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.permissions.Permission;

import java.util.Locale;
import java.util.Map;
import java.util.List;
import java.util.LinkedHashSet;
import java.util.ArrayList;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;

public class SkinCommand {

    private static Function<SuggestionInfo<CommandSender>, IStringTooltip[]> SKIN_SUGGESTIONS(boolean hand) {
        return (data) -> {
            PlayerInventory inv = ((Player) data.sender()).getInventory();
            ItemStack weapon = empty(inv.getItemInMainHand()) ? inv.getItemInOffHand() : inv.getItemInMainHand();
            String title = weapon == null ? null : WeaponMechanicsAPI.getWeaponTitle(weapon);
            SkinSelector skins = WeaponMechanics.getInstance().getWeaponConfigurations().getObject(title + (hand ? ".Hand" : ".Skin"), SkinSelector.class);

            if (skins == null)
                return new IStringTooltip[]{ StringTooltip.ofString("N/A", title + " cannot have a skin") };

            // When giving player options, they shouldn't reselect a skin they are
            // already using.
            Set<String> options = skins.getCustomSkins();
            options.add("default");
            StatsData stats = WeaponMechanics.getInstance().getPlayerWrapper((Player) data.sender()).getStatsData();
            if (stats != null) {
                String skin = (String) stats.get(title, WeaponStat.SKIN, null);
                options.remove(skin);
            }

            if (options.isEmpty())
                return new IStringTooltip[]{ StringTooltip.ofString("N/A", title + " only has default skin") };

            return options.stream().map(option -> StringTooltip.ofString(option, option)).toArray(IStringTooltip[]::new);
        };
    }

    public static void registerPermissions(String key) {
        String keyLower = key.toLowerCase(Locale.ROOT);
        Configuration config = WeaponMechanics.getInstance().getWeaponConfigurations();

        Permission global = new Permission("weaponmechanics." + keyLower + ".*");
        global.setDescription("Ability to use all " + key + "s for any weapon");

        for (String weaponTitle : WeaponMechanics.getInstance().getWeaponHandler().getInfoHandler().getSortedWeaponList()) {
            SkinSelector skins = config.getObject(weaponTitle + "." + key, SkinSelector.class);
            if (skins == null)
                continue;

            Permission weapon = new Permission("weaponmechanics." + keyLower + "." + weaponTitle + ".*");
            weapon.setDescription("Ability to use all " + key + "s for " + weaponTitle);

            for (String skin : skins.getCustomSkins()) {

                // Default skin can be used by everyone, since it is default.
                if ("default".equalsIgnoreCase(skin))
                    continue;

                Permission permission = new Permission("weaponmechanics." + keyLower + "." + weaponTitle + "." + skin);
                permission.setDescription("Ability to use the " + skin + " " + key + " for " + weaponTitle);
                permission.addParent(global, true);
                permission.addParent(weapon, true);
                Bukkit.getPluginManager().addPermission(permission);

                WeaponMechanicsCosmetics.getInstance().getDebugger().fine("Registered: " + permission);
            }
            Bukkit.getPluginManager().addPermission(weapon);
        }
        Bukkit.getPluginManager().addPermission(global);
    }

    public static void register() {
        Configuration config = WeaponMechanicsCosmetics.getInstance().getConfiguration();

        String weaponSkinCommand = getCommandName(config, "Commands.Weapon_Skin.Command", "weaponskin");
        String[] weaponSkinAliases = getAliases(config, "Commands.Weapon_Skin.Aliases", weaponSkinCommand, List.of("wskin"));

        new CommandAPICommand(weaponSkinCommand)
            .withAliases(weaponSkinAliases)
            .withPermission("weaponmechanicscosmetics.commands.skin")
            .withShortDescription("Change the skin for your weapon")
            .withArguments(new StringArgument("skin").replaceSuggestions(ArgumentSuggestions.stringsWithTooltips(SKIN_SUGGESTIONS(false))))
            .executesPlayer((player, args) -> {
                String skin = args.getUnchecked("skin");
                applySkin(player, "Skin", skin);
            }).register();
        
        String handSkinCommand = getCommandName(config, "Commands.Hand_Skin.Command", "handskin");
        String[] handSkinAliases = getAliases(config, "Commands.Hand_Skin.Aliases", handSkinCommand, List.of("whandskin"));

        new CommandAPICommand(handSkinCommand)
            .withAliases(handSkinAliases)
            .withPermission("weaponmechanicscosmetics.commands.handskin")
            .withShortDescription("Change the skin for your hand")
            .withArguments(new StringArgument("skin").replaceSuggestions(ArgumentSuggestions.stringsWithTooltips(SKIN_SUGGESTIONS(true))))
            .executesPlayer((player, args) -> {
                String skin = args.getUnchecked("skin");
                applySkin(player, "Hand", skin);
            }).register();
    }

    public static void applySkin(Player player, String key, String skin) {
        String keylower = key.toLowerCase(Locale.ROOT);
        PlayerInventory inv = player.getInventory();
        boolean mainHand = !empty(inv.getItemInMainHand());
        ItemStack weapon = mainHand ? inv.getItemInMainHand() : inv.getItemInOffHand();

        if (empty(weapon) || WeaponMechanicsAPI.getWeaponTitle(weapon) == null) {
            WeaponMechanicsCosmetics.getInstance().sendLang(player, "skin-hold-weapon", Map.of("hand", key.equals("Hand") ? "hand" : ""));
            return;
        }

        String title = Objects.requireNonNull(WeaponMechanicsAPI.getWeaponTitle(weapon));
        PlayerWrapper wrapper = WeaponMechanics.getInstance().getPlayerWrapper(player);
        StatsData stats = wrapper.getStatsData();

        Map<String, String> variables = Map.of("hand", key.equals("Hand") ? "hand" : "", "weapon", title, "skin", skin);

        if (stats == null) {
            WeaponMechanicsCosmetics.getInstance().sendLang(player, "skin-player-data", variables);
            return;
        }

        SkinSelector skins = WeaponMechanics.getInstance().getWeaponConfigurations().getObject(title + "." + key, SkinSelector.class);
        if (skins == null) {
            WeaponMechanicsCosmetics.getInstance().sendLang(player, "skin-list", variables);
            return;
        }

        if (!skin.equals("default") && !skins.getCustomSkins().contains(skin)) {
            WeaponMechanicsCosmetics.getInstance().sendLang(player, "skin-option", variables);
            return;
        }

        if (!skin.equals("default") && !player.hasPermission("weaponmechanics." + keylower + "." + title + "." + skin)) {
            WeaponMechanicsCosmetics.getInstance().sendLang(player, "skin-permission", variables);
            return;
        }

        // Apply the skin
        stats.set(title, key.equals("Hand") ? WeaponStat.HAND_SKIN : WeaponStat.SKIN, skin);
        WeaponMechanicsCosmetics.getInstance().sendLang(player, "skin-success", variables);
        WeaponMechanics.getInstance().getWeaponHandler().getSkinHandler().tryUse(wrapper, title, weapon, mainHand ? EquipmentSlot.HAND : EquipmentSlot.OFF_HAND);
    }

    private static String getCommandName(Configuration config, String path, String fallback) {
        String command = config.getString(path, fallback);

        if (command == null || command.isBlank())
            return fallback;

        command = command.trim().toLowerCase(Locale.ROOT);

        while (command.startsWith("/")) {
            command = command.substring(1);
        }

        if (command.isBlank() || command.contains(" "))
            return fallback;

        return command;
    }

    private static String[] getAliases(Configuration config, String path, String command, List<String> fallback) {
        Object object = config.getObject(path, fallback);
        List<String> aliases = new ArrayList<>();

        if (object instanceof List<?> list) {
            for (Object element : list) {
                if (element instanceof String string) {
                    aliases.add(string);
                }
            }
        } else if (object instanceof String string) {
            aliases.add(string);
        } else {
            aliases.addAll(fallback);
        }

        return cleanAliases(command, aliases);
    }

    private static String[] cleanAliases(String command, List<String> aliases) {
        Set<String> cleaned = new LinkedHashSet<>();

        for (String alias : aliases) {
            if (alias == null || alias.isBlank())
                continue;

            alias = alias.trim().toLowerCase(Locale.ROOT);

            while (alias.startsWith("/")) {
                alias = alias.substring(1);
            }

            if (alias.isBlank())
                continue;

            if (alias.contains(" "))
                continue;

            if (alias.equalsIgnoreCase(command))
                continue;

            cleaned.add(alias);
        }

        return cleaned.toArray(String[]::new);
    }

    private static boolean empty(ItemStack item) {
        return item == null || item.getAmount() == 0 || item.getType().name().endsWith("AIR");
    }
}
