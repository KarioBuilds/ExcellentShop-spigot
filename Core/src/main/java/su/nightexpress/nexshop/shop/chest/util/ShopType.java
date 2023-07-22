package su.nightexpress.nexshop.shop.chest.util;

import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import su.nightexpress.nexshop.shop.chest.config.ChestPerms;

public enum ShopType {
    PLAYER, ADMIN;

    public boolean hasPermission(@NotNull Player player) {
        return player.hasPermission(ChestPerms.SHOP_TYPE)
            || player.hasPermission(ChestPerms.PREFIX_SHOP_TYPE + this.name().toLowerCase());
    }
}
