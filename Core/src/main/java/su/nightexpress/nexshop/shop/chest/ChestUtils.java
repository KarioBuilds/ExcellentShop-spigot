package su.nightexpress.nexshop.shop.chest;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.Container;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import su.nightexpress.nexshop.api.shop.packer.PluginItemPacker;
import su.nightexpress.nexshop.shop.ProductHandlerRegistry;
import su.nightexpress.nexshop.shop.chest.config.ChestConfig;
import su.nightexpress.nexshop.shop.chest.config.ChestKeys;
import su.nightexpress.nexshop.shop.chest.impl.ChestShop;
import su.nightexpress.nightcore.language.LangAssets;
import su.nightexpress.nightcore.util.*;
import su.nightexpress.nightcore.util.text.tag.Tags;

import java.util.List;
import java.util.Set;

public class ChestUtils {

    @NotNull
    public static String generateShopId(@NotNull Player player, @NotNull Location location) {
        return (player.getName() + "_" + LocationUtil.getWorldName(location) + "_" + location.getBlockX() + "_" + location.getBlockY() + "_" + location.getBlockZ()).toLowerCase();
    }

    public static boolean canUseDisplayEntities() {
        return Version.isAtLeast(Version.V1_19_R3) && !ChestConfig.DISPLAY_HOLOGRAM_FORCE_ARMOR_STAND.get();
    }

    public static boolean bypassHandlerDetection(@NotNull InventoryClickEvent event) {
        if (!ChestConfig.SHOP_PRODUCT_BYPASS_DETECTION_ENABLED.get()) return false;

        boolean needShift = ChestConfig.SHOP_PRODUCT_BYPASS_DETECTION_HOLD_SHIFT.get();
        return !needShift || event.isShiftClick();
    }

    public static int getShopLimit(@NotNull Player player) {
        return ChestConfig.SHOP_CREATION_MAX_PER_RANK.get().getGreatestOrNegative(player);
    }

    public static int getProductLimit(@NotNull Player player) {
        return ChestConfig.SHOP_PRODUCTS_MAX_PER_RANK.get().getGreatestOrNegative(player);
    }

    public static boolean isAllowedItem(@NotNull ItemStack item) {
        Set<String> bannedItems = ChestConfig.SHOP_PRODUCT_BANNED_ITEMS.get();

        String material = item.getType().getKey().getKey();
        if (bannedItems.contains(material)) return false;

        for (PluginItemPacker pluginItem : ProductHandlerRegistry.getPluginItemPackers()) {
            String id = pluginItem.getItemId(item);
            if (id != null && bannedItems.contains(id.toLowerCase())) {
                return false;
            }
        }

        ItemMeta meta = item.getItemMeta();
        if (meta != null) {

            if (meta.hasDisplayName()) {
                String name = Colorizer.strip(meta.getDisplayName());
                if (ChestConfig.SHOP_PRODUCT_DENIED_NAMES.get().stream().anyMatch(name::contains)) {
                    return false;
                }
            }

            List<String> lore = meta.getLore();
            if (lore != null) {
                return lore.stream().noneMatch(line -> ChestConfig.SHOP_PRODUCT_DENIED_LORES.get().stream().anyMatch(line::contains));
            }
        }
        return true;
    }

    public static boolean isValidContainer(@NotNull Block block) {
        return isValidContainer(block.getType()) && block.getState() instanceof Container;
    }

    public static boolean isValidContainer(@NotNull Material material) {
        return material != Material.ENDER_CHEST && ChestConfig.ALLOWED_CONTAINERS.get().contains(material);
    }

    public static boolean isInfiniteStorage() {
        return ChestConfig.SHOP_INFINITE_STORAGE_ENABLED.get();
    }

    @Nullable
    public static ItemStack createShopItem(@NotNull Material material) {
        if (!isValidContainer(material)) return null;

        ItemStack itemStack = ChestConfig.SHOP_ITEM_CREATION_ITEMS.get().get(material);
        if (itemStack == null || itemStack.getType().isAir()) return null;

        ItemStack result = new ItemStack(itemStack);

        PDCUtil.set(result, ChestKeys.shopItemType, BukkitThing.toString(material));
        return result;
    }

    public static boolean isShopItem(@NotNull ItemStack itemStack) {
        return getShopItemType(itemStack) != null;
    }

    @Nullable
    public static Material getShopItemType(@NotNull ItemStack itemStack) {
        String name = PDCUtil.getString(itemStack, ChestKeys.shopItemType).orElse(null);
        return name == null ? null : BukkitThing.getMaterial(name);
    }

    @NotNull
    public static ItemStack getDefaultShopItem(@NotNull Material material, @NotNull String texture) {
        ItemStack itemStack = ItemUtil.getSkinHead(texture);
        ItemUtil.editMeta(itemStack, meta -> {
            meta.setDisplayName(Tags.LIGHT_YELLOW.enclose(Tags.BOLD.enclose("Shop Block")) + " " + Tags.GRAY.enclose("(" + Tags.WHITE.enclose(LangAssets.get(material)) + ")"));
            meta.setLore(Lists.newList(
                Tags.GRAY.enclose("Place down to create a shop!")
            ));
        });

        return itemStack;
    }

    @Nullable
    public static ItemStack getDefaultShowcase(@NotNull Material blockType) {
        var map = ChestConfig.DISPLAY_DEFAULT_SHOWCASE.get();

        ItemStack showcase = map.getOrDefault(BukkitThing.toString(blockType), map.get(Placeholders.DEFAULT));
        if (showcase == null || showcase.getType().isAir()) return null;

        return new ItemStack(showcase);
    }

    @Nullable
    public static ItemStack getCustomShowcase(@Nullable String type) {
        if (type == null) return null;

        var map = ChestConfig.DISPLAY_PLAYER_CUSTOMIZATION_SHOWCASE_LIST.get();

        ItemStack showcase = map.get(type.toLowerCase());
        if (showcase == null || showcase.getType().isAir()) return null;

        return new ItemStack(showcase);
    }

    @Nullable
    public static ItemStack getCustomShowcaseOrDefault(@NotNull ChestShop shop) {
        ItemStack showcase = getCustomShowcase(shop.getShowcaseType());
        return showcase == null ? getDefaultShowcase(shop.getBlockType()) : showcase;
    }
}
