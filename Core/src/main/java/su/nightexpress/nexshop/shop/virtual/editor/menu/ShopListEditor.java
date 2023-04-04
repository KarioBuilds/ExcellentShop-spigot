package su.nightexpress.nexshop.shop.virtual.editor.menu;

import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import su.nexmedia.engine.api.menu.AutoPaged;
import su.nexmedia.engine.api.menu.click.ItemClick;
import su.nexmedia.engine.api.menu.impl.EditorMenu;
import su.nexmedia.engine.api.menu.impl.MenuOptions;
import su.nexmedia.engine.api.menu.impl.MenuViewer;
import su.nexmedia.engine.editor.EditorManager;
import su.nexmedia.engine.utils.ItemUtil;
import su.nexmedia.engine.utils.StringUtil;
import su.nightexpress.nexshop.ExcellentShop;
import su.nightexpress.nexshop.Placeholders;
import su.nightexpress.nexshop.api.shop.Shop;
import su.nightexpress.nexshop.shop.virtual.VirtualShopModule;
import su.nightexpress.nexshop.shop.virtual.config.VirtualLang;
import su.nightexpress.nexshop.shop.virtual.editor.EditorLocales;
import su.nightexpress.nexshop.shop.virtual.impl.shop.VirtualShop;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.IntStream;

public class ShopListEditor extends EditorMenu<ExcellentShop, VirtualShopModule> implements AutoPaged<VirtualShop> {

    public ShopListEditor(@NotNull VirtualShopModule module) {
        super(module.plugin(), module, Placeholders.EDITOR_VIRTUAL_TITLE, 45);

        this.addExit(39);
        this.addNextPage(44);
        this.addPreviousPage(36);

        this.addCreation(EditorLocales.SHOP_CREATE, 41).setClick((viewer, event) -> {
            this.startEdit(viewer.getPlayer(), plugin.getMessage(VirtualLang.EDITOR_ENTER_ID), chat -> {
                if (!module.createShop(StringUtil.lowerCaseUnderscore(chat.getMessage()))) {
                    EditorManager.error(viewer.getPlayer(), plugin.getMessage(VirtualLang.EDITOR_SHOP_CREATE_ERROR_EXIST).getLocalized());
                    return false;
                }
                return true;
            });
        });
    }

    @Override
    public void onPrepare(@NotNull MenuViewer viewer, @NotNull MenuOptions options) {
        super.onPrepare(viewer, options);
        this.getItemsForPage(viewer).forEach(this::addItem);
    }

    @Override
    @NotNull
    public List<VirtualShop> getObjects(@NotNull Player player) {
        return new ArrayList<>(this.object.getShops());
    }

    @Override
    public int[] getObjectSlots() {
        return IntStream.range(0, 36).toArray();
    }

    @Override
    @NotNull
    public Comparator<VirtualShop> getObjectSorter() {
        return Comparator.comparing(Shop::getId);
    }

    @Override
    @NotNull
    public ItemStack getObjectStack(@NotNull Player player, @NotNull VirtualShop shop) {
        ItemStack item = new ItemStack(shop.getIcon());
        ItemUtil.mapMeta(item, meta -> {
            meta.setDisplayName(EditorLocales.SHOP_OBJECT.getLocalizedName());
            meta.setLore(EditorLocales.SHOP_OBJECT.getLocalizedLore());
            meta.addItemFlags(ItemFlag.values());
            ItemUtil.replace(meta, shop.replacePlaceholders());
        });
        return item;
    }

    @Override
    @NotNull
    public ItemClick getObjectClick(@NotNull VirtualShop shop) {
        return (viewer, event) -> {
            Player player = viewer.getPlayer();

            if (event.isShiftClick() && event.isRightClick()) {
                this.object.delete(shop);
                this.plugin.runTask(task -> this.open(player, viewer.getPage()));
                return;
            }
            shop.getEditor().open(player, 1);
        };
    }
}