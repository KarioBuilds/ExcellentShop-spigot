package su.nightexpress.nexshop.shop.virtual.impl.product;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import su.nexmedia.engine.api.config.JYML;
import su.nightexpress.nexshop.ShopAPI;
import su.nightexpress.nexshop.api.IScheduled;
import su.nightexpress.nexshop.api.currency.ICurrency;
import su.nightexpress.nexshop.api.shop.CommandProduct;
import su.nightexpress.nexshop.api.shop.ItemProduct;
import su.nightexpress.nexshop.api.shop.Product;
import su.nightexpress.nexshop.api.shop.ProductPricer;
import su.nightexpress.nexshop.api.type.StockType;
import su.nightexpress.nexshop.api.type.TradeType;
import su.nightexpress.nexshop.currency.CurrencyId;
import su.nightexpress.nexshop.shop.FlatProductPricer;
import su.nightexpress.nexshop.shop.FloatProductPricer;
import su.nightexpress.nexshop.shop.virtual.editor.menu.ProductMainEditor;
import su.nightexpress.nexshop.shop.virtual.impl.shop.VirtualShop;

import java.util.List;

public abstract class VirtualProduct extends Product<VirtualProduct, VirtualShop, VirtualProductStock> {

    private int shopSlot;
    private int shopPage;

    private ProductMainEditor editor;

    public VirtualProduct(@NotNull String id, @NotNull ICurrency currency) {
        super(id, currency);
    }

    @NotNull
    public static VirtualProduct read(@NotNull JYML cfg, @NotNull String path, @NotNull String id) {
        // ------------ OLD DATA START ------------
        if (cfg.contains(path + ".Purchase")) {
            cfg.addMissing(path + ".Currency", cfg.getString(path + ".Purchase.Currency"));
            cfg.addMissing(path + ".Item_Meta_Enabled", cfg.getBoolean(path + ".Purchase.Item_Meta_Enabled"));
            double buyMin = cfg.getDouble(path + ".Purchase.BUY.Price_Min");
            double buyMax = cfg.getDouble(path + ".Purchase.BUY.Price_Max");
            double sellMin = cfg.getDouble(path + ".Purchase.SELL.Price_Min");
            double sellMax = cfg.getDouble(path + ".Purchase.SELL.Price_Max");

            if (cfg.getBoolean(path + ".Purchase.Randomizer.Enabled")) {
                FloatProductPricer pricer = new FloatProductPricer();
                pricer.setPriceMin(TradeType.BUY, buyMin);
                pricer.setPriceMax(TradeType.BUY, buyMax);
                pricer.setPriceMin(TradeType.SELL, sellMin);
                pricer.setPriceMin(TradeType.SELL, sellMax);
                pricer.setDays(IScheduled.parseDays(cfg.getString(path + ".Purchase.Randomizer.Times.Days", "")));
                pricer.setTimes(IScheduled.parseTimesOld(cfg.getStringList(path + ".Purchase.Randomizer.Times.Times")));
                cfg.addMissing(path + ".Price.Type", pricer.getType().name());
                pricer.write(cfg, path + ".Price");
            }
            else {
                FlatProductPricer pricer = new FlatProductPricer();
                pricer.setPrice(TradeType.BUY, buyMin);
                pricer.setPrice(TradeType.SELL, sellMin);
                cfg.addMissing(path + ".Price.Type", pricer.getType().name());
                pricer.write(cfg, path + ".Price");
            }

            cfg.remove(path + ".Purchase");
            cfg.saveChanges();
        }
        if (cfg.contains(path + ".Limit")) {
            VirtualProductStock stock = new VirtualProductStock();
            for (TradeType tradeType : TradeType.values()) {
                int init = cfg.getInt(path + ".Limit." + tradeType.name() + ".Amount");
                int cooldown = cfg.getInt(path + ".Limit." + tradeType.name() + ".Cooldown");

                stock.setInitialAmount(StockType.PLAYER, tradeType, init);
                stock.setRestockCooldown(StockType.PLAYER, tradeType, cooldown);
            }
            cfg.addMissing(path + ".Stock", stock);
            cfg.remove(path + ".Limit");
        }
        if (cfg.contains(path + ".Reward.Commands")) {
            cfg.set(path + ".Content.Commands", cfg.getStringList(path + ".Reward.Commands"));
            cfg.remove(path + ".Reward.Commands");
        }
        if (cfg.contains(path + ".Reward.Item")) {
            cfg.setItemEncoded(path + ".Content.Item", cfg.getItemEncoded(path + ".Reward.Item"));
            cfg.remove(path + ".Reward.Item");
        }
        if (cfg.contains(path + ".Shop_View.Preview")) {
            cfg.setItemEncoded(path + ".Content.Preview", cfg.getItemEncoded(path + ".Shop_View.Preview"));
            cfg.remove(path + ".Shop_View.Preview");
        }
        cfg.saveChanges();
        // ------------ OLD DATA END ------------

        String currencyId = cfg.getString(path + ".Currency", CurrencyId.VAULT);
        ICurrency currency = ShopAPI.getCurrencyManager().getCurrency(currencyId);
        if (currency == null) {
            throw new IllegalStateException("Invalid currency!");
        }

        List<String> commands = cfg.getStringList(path + ".Content.Commands");
        ItemStack item = cfg.getItemEncoded(path + ".Content.Item");

        VirtualProduct product;
        if (item != null && !item.getType().isAir()) {
            product = new VirtualItemProduct(id, item, currency);
            ((ItemProduct)product).setRespectItemMeta(cfg.getBoolean(path + ".Item_Meta_Enabled"));
        }
        else {
            ItemStack preview = cfg.getItemEncoded(path + ".Content.Preview");
            if (preview == null) preview = new ItemStack(Material.COMMAND_BLOCK);

            product = new VirtualCommandProduct(id, preview, commands, currency);
        }

        product.setSlot(cfg.getInt(path + ".Shop_View.Slot", -1));
        product.setPage(cfg.getInt(path + ".Shop_View.Page", -1));
        product.setDiscountAllowed(cfg.getBoolean(path + ".Discount.Allowed"));
        product.setPricer(ProductPricer.read(cfg, path + ".Price"));
        product.setStock(VirtualProductStock.read(cfg, path + ".Stock"));
        return product;
    }

    //@Override
    public static void write(@NotNull VirtualProduct product, @NotNull JYML cfg, @NotNull String path) {
        cfg.remove(path + ".Content");
        if (product instanceof CommandProduct commandProduct) {
            cfg.setItemEncoded(path + ".Content.Preview", commandProduct.getPreview());
            cfg.set(path + ".Content.Commands", commandProduct.getCommands());
        }
        else if (product instanceof ItemProduct itemProduct) {
            cfg.setItemEncoded(path + ".Content.Item", itemProduct.getItem());
            cfg.set(path + ".Item_Meta_Enabled", itemProduct.isRespectItemMeta());
        }
        cfg.set(path + ".Shop_View.Slot", product.getSlot());
        cfg.set(path + ".Shop_View.Page", product.getPage());
        cfg.set(path + ".Discount.Allowed", product.isDiscountAllowed());
        //cfg.set(path + ".Stock", product.getStock());
        VirtualProductStock.write(product.getStock(), cfg, path + ".Stock");
        ProductPricer pricer = product.getPricer();
        cfg.set(path + ".Currency", product.getCurrency().getId());
        cfg.set(path + ".Price.Type", pricer.getType().name());
        //cfg.set(path + ".Price", pricer);
        pricer.write(cfg, path + ".Price");
    }

    @Override
    public void clear() {
        if (this.editor != null) {
            this.editor.clear();
            this.editor = null;
        }
    }

    @NotNull
    public ProductMainEditor getEditor() {
        if (this.editor == null) {
            this.editor = new ProductMainEditor(this.getShop().plugin(), this);
        }
        return this.editor;
    }

    @NotNull
    protected VirtualProduct get() {
        return this;
    }

    @Override
    @NotNull
    public VirtualPreparedProduct getPrepared(@NotNull TradeType buyType) {
        return new VirtualPreparedProduct(this, buyType);
    }

    public int getSlot() {
        return this.shopSlot;
    }

    public void setSlot(int slot) {
        this.shopSlot = slot;
    }

    public int getPage() {
        return this.shopPage;
    }

    public void setPage(int page) {
        this.shopPage = page;
    }
}