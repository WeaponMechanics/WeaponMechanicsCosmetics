/*
 * Copyright (c) 2026. All rights reserved. Distribution of this file, similar
 * files, related files, or related projects is strictly controlled.
 */

package com.cjcrafter.weaponmechanicscosmetics.listeners;

import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.event.PacketListener;
import com.github.retrooper.packetevents.event.PacketSendEvent;
import com.github.retrooper.packetevents.protocol.component.ComponentTypes;
import com.github.retrooper.packetevents.protocol.component.builtin.item.ItemConsumable;
import com.github.retrooper.packetevents.protocol.entity.data.EntityData;
import com.github.retrooper.packetevents.protocol.entity.data.EntityDataTypes;
import com.github.retrooper.packetevents.protocol.item.ItemStack;
import com.github.retrooper.packetevents.protocol.nbt.NBTCompound;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.protocol.player.Equipment;
import com.github.retrooper.packetevents.protocol.player.EquipmentSlot;
import com.github.retrooper.packetevents.protocol.sound.Sounds;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerEntityEquipment;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerEntityMetadata;
import io.github.retrooper.packetevents.util.SpigotConversionUtil;
import com.cjcrafter.weaponmechanicscosmetics.config.ThirdPersonPose;
import me.deecaad.weaponmechanics.WeaponMechanics;
import me.deecaad.weaponmechanics.utils.CustomTag;
import me.deecaad.weaponmechanics.weapon.weaponevents.WeaponScopeEvent;
import me.deecaad.weaponmechanics.weapon.weaponevents.WeaponReloadEvent;
import me.deecaad.weaponmechanics.weapon.weaponevents.WeaponReloadCompleteEvent;
import me.deecaad.weaponmechanics.weapon.weaponevents.WeaponReloadCancelEvent;
import me.deecaad.weaponmechanics.weapon.weaponevents.WeaponFirearmEvent;
import me.deecaad.weaponmechanics.wrappers.HandData;
import me.deecaad.weaponmechanics.wrappers.PlayerWrapper;
import org.bukkit.Material;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerToggleSprintEvent;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Iterator;
import java.util.List;

/**
 * This packet listener detects when an outgoing set entity equipment packet
 * contains a WeaponMechanics item. If that WeaponMechanics item has a
 * configured crossbow, we should replace the item with the crossbow. This way,
 * the player's arms stick up and appear to be "aiming"
 */
public class CrossbowPacketListener implements Listener, PacketListener {

    public CrossbowPacketListener(@NotNull Plugin plugin) {
    }

    @Override
    public void onPacketSend(PacketSendEvent event) {
        if (event.getPacketType() == PacketType.Play.Server.ENTITY_METADATA) {
            handleEntityMetadata(event);
        } else if (event.getPacketType() == PacketType.Play.Server.ENTITY_EQUIPMENT) {
            handleEntityEquipment(event);
        }
    }

    private void handleEntityEquipment(PacketSendEvent event) {
        WrapperPlayServerEntityEquipment equipmentPacket = new WrapperPlayServerEntityEquipment(event);
        Player receiver = event.getPlayer();

        // prevents the offhand weapon from visually appearing as a crossbow for the holder...
        if (receiver.getEntityId() == equipmentPacket.getEntityId())
            return;

        // Only players can have proper animations
        Entity shooter = SpigotConversionUtil.getEntityById(receiver.getWorld(), equipmentPacket.getEntityId());
        if (!(shooter instanceof Player))
            return;

        // Used to check if the shooter is reloading/scoping
        PlayerWrapper playerWrapper = WeaponMechanics.getInstance().getPlayerWrapper((Player) shooter);

        boolean shouldUpdatePacket = false;
        for (Equipment equipment : equipmentPacket.getEquipment()) {
            EquipmentSlot slot = equipment.getSlot();
            if (slot != EquipmentSlot.MAIN_HAND && slot != EquipmentSlot.OFF_HAND)
                continue;

            com.github.retrooper.packetevents.protocol.item.ItemStack item = equipment.getItem();
            String weaponTitle = getWeaponTitle(item);
            if (weaponTitle == null)
                continue;

            // The configuration if the weapon uses poses
            ThirdPersonPose poses = WeaponMechanics.getInstance().getWeaponConfigurations().getObject(weaponTitle + ".Cosmetics.Third_Person_Pose", ThirdPersonPose.class);
            if (poses == null)
                continue;

            // Determine which pose the gun should be in
            boolean isMainhand = slot == EquipmentSlot.MAIN_HAND;
            ThirdPersonPose.PoseOverride poseOverride = getPoseOverride(poses, playerWrapper, isMainhand);

            // No need to change the item if we have no pose/override
            boolean hasVisualOverride = poseOverride.overrideItem() != null
                    || poseOverride.overrideItemModel() != null
                    || poseOverride.overrideCustomModelData() != null;

            if (poseOverride.pose() == ItemConsumable.Animation.NONE && !hasVisualOverride) {
                scheduleItemUsePacket(receiver, equipmentPacket.getEntityId(), false, isMainhand);
                continue;
            }

            ItemStack visualItem = buildVisualItem(item, poseOverride);

            // Set the components to make the weapon "consumable" (yes, into food).
            // We can then send the "USE ITEM" packet to make the shooter hold the
            // weapon in the correct pose.
            ItemConsumable consumable = new ItemConsumable(
                Float.MAX_VALUE, // a really long time, so the player never eats the item
                poseOverride.pose(),
                Sounds.ITEM_CROSSBOW_QUICK_CHARGE_1,  // something quiet, should never be heard since consume time is high
                false, // no particles
                List.of() // no effects
            );
            visualItem.setComponent(ComponentTypes.CONSUMABLE, consumable);

            equipment.setItem(visualItem);

            shouldUpdatePacket = true;
            scheduleItemUsePacket(receiver, equipmentPacket.getEntityId(), true, isMainhand);
        }

        if (shouldUpdatePacket) {
            equipmentPacket.write();
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onReloadStart(WeaponReloadEvent event) {
        if (!(event.getShooter() instanceof Player shooter)) return;
        boolean isMainhand = event.isMainHand();

        WeaponMechanics.getInstance().getFoliaScheduler().entity(shooter).runDelayed(
                () -> refreshThirdPersonPose(shooter, isMainhand),
                1L
        );
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onReloadComplete(WeaponReloadCompleteEvent event) {
        if (!(event.getShooter() instanceof Player shooter)) return;
        boolean isMainhand = event.isMainHand();

        WeaponMechanics.getInstance().getFoliaScheduler().entity(shooter).runDelayed(
                () -> refreshThirdPersonPose(shooter, isMainhand),
                1L
        );
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onReloadCancel(WeaponReloadCancelEvent event) {
        if (!(event.getShooter() instanceof Player shooter)) return;
        boolean isMainhand = event.isMainHand();

        WeaponMechanics.getInstance().getFoliaScheduler().entity(shooter).runDelayed(
                () -> refreshThirdPersonPose(shooter, isMainhand),
                1L
        );
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onFirearmAction(WeaponFirearmEvent event) {
        if (!(event.getShooter() instanceof Player shooter)) return;

        boolean isMainhand = event.isMainHand();

        WeaponMechanics.getInstance().getFoliaScheduler().entity(shooter).runDelayed(
                () -> refreshThirdPersonPose(shooter, isMainhand),
                1L
        );

        int t = event.getTime();
        if (t > 0) {
            WeaponMechanics.getInstance().getFoliaScheduler().entity(shooter).runDelayed(
                    () -> refreshThirdPersonPose(shooter, isMainhand),
                    1L
            );
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onScope(WeaponScopeEvent event) {
        if (!(event.getShooter() instanceof Player shooter)) return;
        boolean isMainhand = event.isMainHand();

        WeaponMechanics.getInstance().getFoliaScheduler().entity(shooter).runDelayed(
                () -> refreshThirdPersonPose(shooter, isMainhand),
                1L
        );
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onSprint(PlayerToggleSprintEvent event) {
        Player shooter = event.getPlayer();

        WeaponMechanics.getInstance().getFoliaScheduler().entity(shooter).runDelayed(
                () -> refreshThirdPersonPose(shooter, true),
                1L
        );

        WeaponMechanics.getInstance().getFoliaScheduler().entity(shooter).runDelayed(
                () -> refreshThirdPersonPose(shooter, false),
                1L
        );
    }

    private ThirdPersonPose.PoseOverride getPoseOverride(ThirdPersonPose poses, PlayerWrapper wrapper, boolean isMainhand) {
        HandData handData = wrapper.getHandData(isMainhand);

        if (handData.hasRunningFirearmAction()) return poses.getFirearmActionPose();
        if (handData.isReloading()) return poses.getReloadPose();
        if (handData.getZoomData().isZooming()) return poses.getScopePose();
        if (wrapper.getPlayer().isSprinting()) return poses.getRunningPose();
        return poses.getDefaultPose();
    }

    private void refreshThirdPersonPose(Player shooter, boolean isMainhand) {
        PlayerWrapper wrapper = WeaponMechanics.getInstance().getPlayerWrapper(shooter);
        refreshHand(shooter, wrapper, isMainhand);
    }

    private void refreshHand(Player shooter, PlayerWrapper wrapper, boolean isMainhand) {
        org.bukkit.inventory.ItemStack bukkitStack = shooter.getEquipment().getItem(isMainhand ? org.bukkit.inventory.EquipmentSlot.HAND : org.bukkit.inventory.EquipmentSlot.OFF_HAND);

        if (bukkitStack == null || bukkitStack.getType().isAir() || !bukkitStack.hasItemMeta()) {
            return;
        }

        String weaponTitle = CustomTag.WEAPON_TITLE.getString(bukkitStack);
        if (weaponTitle == null) return;

        ThirdPersonPose poses = WeaponMechanics.getInstance().getWeaponConfigurations()
                .getObject(weaponTitle + ".Cosmetics.Third_Person_Pose", ThirdPersonPose.class);
        if (poses == null) return;

        ThirdPersonPose.PoseOverride ovr = getPoseOverride(poses, wrapper, isMainhand);

        boolean hasVisualOverride = ovr.overrideItem() != null
                || ovr.overrideItemModel() != null
                || ovr.overrideCustomModelData() != null;

        // Build the base packet item from the real held stack
        ItemStack original = SpigotConversionUtil.fromBukkitItemStack(bukkitStack);
        ItemStack visual = buildVisualItem(original, ovr);

        // If pose is NONE and no visual override, just “turn off” active hand for viewers
        if (ovr.pose() == ItemConsumable.Animation.NONE && !hasVisualOverride) {
            for (Player viewer : shooter.getWorld().getPlayers()) {
                if (viewer.getEntityId() == shooter.getEntityId()) continue;
                if (!viewer.canSee(shooter)) continue;
                scheduleItemUsePacket(viewer, shooter.getEntityId(), false, isMainhand);
            }
            return;
        }

        ItemConsumable consumable = new ItemConsumable(
                Float.MAX_VALUE,
                ovr.pose(),
                Sounds.ITEM_CROSSBOW_QUICK_CHARGE_1,
                false,
                List.of()
        );
        visual.setComponent(ComponentTypes.CONSUMABLE, consumable);

        EquipmentSlot peSlot = isMainhand ? EquipmentSlot.MAIN_HAND : EquipmentSlot.OFF_HAND;
        WrapperPlayServerEntityEquipment equipPacket =
                new WrapperPlayServerEntityEquipment(shooter.getEntityId(), List.of(new Equipment(peSlot, visual)));

        for (Player viewer : shooter.getWorld().getPlayers()) {
            if (viewer.getEntityId() == shooter.getEntityId()) continue;
            if (!viewer.canSee(shooter)) continue;

            PacketEvents.getAPI().getPlayerManager().sendPacket(viewer, equipPacket);
            scheduleItemUsePacket(viewer, shooter.getEntityId(), ovr.pose() != ItemConsumable.Animation.NONE, isMainhand);
        }
    }

    private ItemStack buildVisualItem(ItemStack original, ThirdPersonPose.PoseOverride ovr) {
        // If config provided a full override item (Type present), we use it
        if (ovr.overrideItem() != null)
            return ovr.overrideItem();

        // If the config is model-only we create a Bukkit stack and convert
        if (ovr.overrideItemModel() != null || ovr.overrideCustomModelData() != null)
            return buildModelOnlyVisual(ovr);

        // No visual override, just keep original
        return original;
    }

    private ItemStack buildModelOnlyVisual(ThirdPersonPose.PoseOverride type) {

        // Base material can be anything (feather is fine), because Item_Model controls rendering
        Material base = Material.FEATHER;
        if (type.overrideItemModel() != null && "minecraft".equalsIgnoreCase(type.overrideItemModel().getNamespace())) {
            Material m = Material.matchMaterial(type.overrideItemModel().getKey());
            if (m != null) base = m;
        }

        org.bukkit.inventory.ItemStack bukkit = new org.bukkit.inventory.ItemStack(base);
        org.bukkit.inventory.meta.ItemMeta meta = bukkit.getItemMeta();

        if (meta != null) {
            if (type.overrideItemModel() != null) {
                org.bukkit.NamespacedKey modelKey =
                        org.bukkit.NamespacedKey.fromString(type.overrideItemModel().toString());
                if (modelKey != null) {
                    meta.setItemModel(modelKey);
                }
            }

            if (type.overrideCustomModelData() != null) {
                meta.setCustomModelData(type.overrideCustomModelData());
                //setCustomModelDataComponent(CustomModelDataComponent);
            }

            bukkit.setItemMeta(meta);
        }

        return SpigotConversionUtil.fromBukkitItemStack(bukkit);
    }

    private void handleEntityMetadata(PacketSendEvent event) {
        WrapperPlayServerEntityMetadata metadataPacket = new WrapperPlayServerEntityMetadata(event);
        Player receiver = event.getPlayer();

        // If entity id is inverted, this is one of our packets... let it through
        if (metadataPacket.getEntityId() < 0) {
            metadataPacket.setEntityId(-metadataPacket.getEntityId());
            return;
        }

        // prevents the offhand weapon from visually appearing as a crossbow for the holder...
        if (receiver.getEntityId() == metadataPacket.getEntityId())
            return;

        // Only players can have proper animations
        Entity shooter = SpigotConversionUtil.getEntityById(receiver.getWorld(), metadataPacket.getEntityId());
        if (!(shooter instanceof Player))
            return;

        // Used to check if the shooter is reloading/scoping
        PlayerWrapper playerWrapper = WeaponMechanics.getInstance().getPlayerWrapper((Player) shooter);

        // If the metadata packet contains the "active hand" data, we need to remove it
        // so the player doesn't appear to be holding the weapon in the wrong hand
        List<EntityData<?>> metadata = metadataPacket.getEntityMetadata();
        for (Iterator<EntityData<?>> iterator = metadata.iterator(); iterator.hasNext(); ) {
            EntityData<?> data = iterator.next();
            if (data.getType() == EntityDataTypes.BYTE && data.getIndex() == 8) {
                byte value = (byte) data.getValue();
                boolean isActive = (value & 0b01) == 0b01;
                boolean isMainhand = (value & 0b10) == 0b00;

                // If the player is holding a weapon, we need to modify the packet
                org.bukkit.inventory.ItemStack itemInHand = playerWrapper.getPlayer().getEquipment().getItem(isMainhand ? org.bukkit.inventory.EquipmentSlot.HAND : org.bukkit.inventory.EquipmentSlot.OFF_HAND);
                if (!itemInHand.hasItemMeta())
                    break;
                String weaponTitle = CustomTag.WEAPON_TITLE.getString(itemInHand);
                if (weaponTitle == null)
                    break;

                // Determine which pose the gun should be in
                ThirdPersonPose poses = WeaponMechanics.getInstance().getWeaponConfigurations().getObject(weaponTitle + ".Cosmetics.Third_Person_Pose", ThirdPersonPose.class);
                if (poses == null)
                    break;

                // Now we know that poses are already handled, so we can remove the metadata
                iterator.remove();
                break;
            }
        }
    }

    private @Nullable String getWeaponTitle(com.github.retrooper.packetevents.protocol.item.ItemStack equipment) {
        NBTCompound customData = equipment.getComponent(ComponentTypes.CUSTOM_DATA).orElse(null);
        if (customData == null)
            return null;

        NBTCompound publicBukkitValues = customData.getCompoundTagOrNull("PublicBukkitValues");
        if (publicBukkitValues == null)
            return null;

        return publicBukkitValues.getStringTagValueOrNull("weaponmechanics:weapon-title");
    }

    private void scheduleItemUsePacket(Player receiver, int shooterId, boolean isActive, boolean isMainhand) {
        WeaponMechanics.getInstance().getFoliaScheduler().async().runDelayed(() -> {
            int activeMask = isActive ? 0b01 : 0b00;
            int handMask = isMainhand ? 0b00 : 0b10;
            EntityData<?> eatData = new EntityData<>(8, EntityDataTypes.BYTE, (byte) (activeMask | handMask));
            WrapperPlayServerEntityMetadata metadataPacket = new WrapperPlayServerEntityMetadata(-shooterId, List.of(eatData));

            PacketEvents.getAPI().getPlayerManager().sendPacket(receiver, metadataPacket);
        }, 1L);
    }
}
