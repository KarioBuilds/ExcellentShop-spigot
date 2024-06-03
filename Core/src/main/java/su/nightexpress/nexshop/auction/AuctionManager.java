package su.nightexpress.nexshop.auction;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import su.nightexpress.nexshop.ShopPlugin;
import su.nightexpress.nexshop.api.currency.Currency;
import su.nightexpress.nexshop.api.shop.packer.PluginItemPacker;
import su.nightexpress.nexshop.auction.command.child.*;
import su.nightexpress.nexshop.auction.config.AuctionConfig;
import su.nightexpress.nexshop.auction.config.AuctionLang;
import su.nightexpress.nexshop.auction.config.AuctionPerms;
import su.nightexpress.nexshop.auction.data.AuctionDataHandler;
import su.nightexpress.nexshop.auction.listener.AuctionListener;
import su.nightexpress.nexshop.auction.listing.ActiveListing;
import su.nightexpress.nexshop.auction.listing.CompletedListing;
import su.nightexpress.nexshop.auction.menu.*;
import su.nightexpress.nexshop.config.Config;
import su.nightexpress.nexshop.currency.CurrencyManager;
import su.nightexpress.nexshop.module.AbstractShopModule;
import su.nightexpress.nexshop.shop.ProductHandlerRegistry;
import su.nightexpress.nightcore.command.experimental.builder.ChainedNodeBuilder;
import su.nightexpress.nightcore.config.FileConfig;
import su.nightexpress.nightcore.database.DatabaseType;
import su.nightexpress.nightcore.language.LangAssets;
import su.nightexpress.nightcore.menu.MenuViewer;
import su.nightexpress.nightcore.util.*;

import java.util.*;
import java.util.stream.Collectors;

public class AuctionManager extends AbstractShopModule {

    public static final String ID = "auction";

    private final Listings      listings;
    private final Set<Currency> allowedCurrencies;
    private final Map<String, ListingCategory> categoryMap;

    private AuctionDataHandler dataHandler;

    private AuctionMenu           mainMenu;
    private PurchaseConfirmMenu   purchaseConfirmMenu;
    private ExpiredListingsMenu   expiredMenu;
    private SalesHistoryMenu      historyMenu;
    private UnclaimedListingsMenu unclaimedMenu;
    private PlayerListingsMenu    sellingMenu;
    private CurrencySelectMenu    currencySelectMenu;

    public AuctionManager(@NotNull ShopPlugin plugin) {
        super(plugin, ID, Config.getAuctionAliases());
        this.listings = new Listings();
        this.allowedCurrencies = new HashSet<>();
        this.categoryMap = new LinkedHashMap<>();
    }

    @Override
    protected void loadModule(@NotNull FileConfig config) {
        config.initializeOptions(AuctionConfig.class);

        this.loadCurrencies();

        if (this.getDefaultCurrency() == CurrencyManager.DUMMY_CURRENCY) {
            this.error("Invalid default currency. Adding items to the auction will be disabled until fixed.");
        }

        this.loadCategories();

        this.plugin.registerPermissions(AuctionPerms.class);
        this.plugin.getLangManager().loadEntries(AuctionLang.class);

        this.dataHandler = new AuctionDataHandler(this, config);
        this.dataHandler.setup();
        this.getDataHandler().onSynchronize();

        this.mainMenu = new AuctionMenu(this.plugin, this);
        this.purchaseConfirmMenu = new PurchaseConfirmMenu(this.plugin, this);
        this.expiredMenu = new ExpiredListingsMenu(this.plugin, this);
        this.historyMenu = new SalesHistoryMenu(this.plugin, this);
        this.unclaimedMenu = new UnclaimedListingsMenu(this.plugin, this);
        this.sellingMenu = new PlayerListingsMenu(this.plugin, this);
        this.currencySelectMenu = new CurrencySelectMenu(this.plugin, this);

        this.addListener(new AuctionListener(this.plugin, this));

        //AuctionUtils.fillDummy(this);
    }

    @Override
    protected void disableModule() {
        if (this.currencySelectMenu != null) this.currencySelectMenu.clear();
        if (this.purchaseConfirmMenu != null) this.purchaseConfirmMenu.clear();
        if (this.mainMenu != null) this.mainMenu.clear();
        if (this.expiredMenu != null) this.expiredMenu.clear();
        if (this.historyMenu != null) this.historyMenu.clear();
        if (this.sellingMenu != null) this.sellingMenu.clear();
        if (this.unclaimedMenu != null) this.unclaimedMenu.clear();

        if (this.dataHandler != null) {
            this.dataHandler.shutdown();
            this.dataHandler = null;
        }

        this.listings.clear();
        this.allowedCurrencies.clear();
        this.categoryMap.clear();
    }

    @Override
    protected void addCommands(@NotNull ChainedNodeBuilder builder) {
        builder.fallback(context -> {
            if (!context.checkPermission(AuctionPerms.COMMAND_OPEN)) {
                context.errorPermission();
                return false;
            }
            return OpenCommand.executes(this.plugin, this, context);
        });

        ExpiredCommand.build(this.plugin, this, builder);
        HistoryCommand.build(this.plugin, this, builder);
        ListingsCommand.build(this.plugin, this, builder);
        OpenCommand.build(this.plugin, this, builder);
        SellCommand.build(this.plugin, this, builder);
        UnclaimedCommand.build(this.plugin, this, builder);
        FillDummyCommand.build(this.plugin, this, builder);
    }

    private void loadCurrencies() {
        Set<String> allowedList = AuctionConfig.ALLOWED_CURRENCIES.get();
        if (allowedList.contains(Placeholders.WILDCARD)) {
            this.allowedCurrencies.addAll(this.plugin.getCurrencyManager().getCurrencies());
        }
        else {
            for (String id : AuctionConfig.ALLOWED_CURRENCIES.get()) {
                Currency currency = this.plugin.getCurrencyManager().getCurrency(id);
                if (currency == null) {
                    this.error("Unknown currency: '" + id + "'. Skipping.");
                    continue;
                }
                this.allowedCurrencies.add(currency);
            }
        }

        Currency defaultCurrency = this.getDefaultCurrency();
        if (defaultCurrency != CurrencyManager.DUMMY_CURRENCY) {
            this.allowedCurrencies.add(defaultCurrency);
        }

        this.info("Allowed currencies: [" + this.allowedCurrencies.stream().map(Currency::getId).collect(Collectors.joining(", ")) + "]");
    }

    private void loadCategories() {
        FileConfig cfg = FileConfig.loadOrExtract(this.plugin, this.getLocalPath(), "categories.yml");
        for (String sId : cfg.getSection("")) {
            ListingCategory category = ListingCategory.read(cfg, sId, sId);
            this.categoryMap.put(category.getId(), category);
        }
    }

    @NotNull
    public AuctionDataHandler getDataHandler() {
        return dataHandler;
    }

    @NotNull
    public Listings getListings() {
        return listings;
    }

    @NotNull
    public Collection<ListingCategory> getCategories() {
        return this.categoryMap.values();
    }

    @NotNull
    public ListingCategory getDefaultCategory() {
        ListingCategory category = this.getCategories().stream().filter(ListingCategory::isDefault).findFirst().orElse(null);
        if (category == null) category = this.getCategories().stream().findFirst().orElseThrow();

        return category;
    }

    @NotNull
    public Currency getDefaultCurrency() {
        Currency currency = this.plugin.getCurrencyManager().getCurrency(AuctionConfig.DEFAULT_CURRENCY.get());
        return currency == null ? CurrencyManager.DUMMY_CURRENCY : currency;
    }

    @NotNull
    public Set<Currency> getAllowedCurrencies() {
        return this.allowedCurrencies;
    }

    @NotNull
    public Set<Currency> getAllowedCurrencies(@NotNull Player player) {
        return this.getAllowedCurrencies();

        /*return AuctionConfig.CURRENCIES.values().stream()
            .filter(setting -> setting.hasPermission(player) || setting.isDefault())
            .map(CurrencySetting::getCurrency).collect(Collectors.toSet());*/
    }

    private boolean needEnsureListingExists() {
        return this.getDataHandler().getDatabaseType() != DatabaseType.SQLITE;
    }

    public boolean openAuction(@NotNull Player player, int page) {
        MenuViewer viewer = this.mainMenu.getViewerOrCreate(player);
        viewer.setPage(page);

        return this.openAuction(player);
    }

    public boolean openAuction(@NotNull Player player) {
        return this.openAuction(player, false);
    }

    public boolean openAuction(@NotNull Player player, boolean force) {
        return this.openAuctionMenu(player, player.getUniqueId(), this.mainMenu, force);
    }

    public boolean openExpiedListings(@NotNull Player player) {
        return this.openExpiedListings(player, player.getUniqueId(), false);
    }

    public boolean openExpiedListings(@NotNull Player player, @NotNull UUID target, boolean force) {
        return this.openAuctionMenu(player, target, this.expiredMenu, force);
    }

    public boolean openPlayerListings(@NotNull Player player) {
        return this.openPlayerListings(player, player.getUniqueId(), false);
    }

    public boolean openPlayerListings(@NotNull Player player, @NotNull UUID target, boolean force) {
        return this.openAuctionMenu(player, target, this.sellingMenu, force);
    }

    public boolean openSalesHistory(@NotNull Player player) {
        return this.openSalesHistory(player, player.getUniqueId(), false);
    }

    public boolean openSalesHistory(@NotNull Player player, @NotNull UUID target, boolean force) {
        return this.openAuctionMenu(player, target, this.historyMenu, force);
    }

    public boolean openUnclaimedListings(@NotNull Player player) {
        return this.openUnclaimedListings(player, player.getUniqueId(), false);
    }

    public boolean openUnclaimedListings(@NotNull Player player, @NotNull UUID target, boolean force) {
        return this.openAuctionMenu(player, target, this.unclaimedMenu, force);
    }

    public void openCurrencySelection(@NotNull Player player, @NotNull ItemStack itemStack, double price) {
        this.currencySelectMenu.open(player, Pair.of(itemStack, price));
    }

    public void openPurchaseConfirmation(@NotNull Player player, @NotNull ActiveListing listing) {
        this.purchaseConfirmMenu.open(player, listing);
    }

    private boolean openAuctionMenu(@NotNull Player player, @NotNull UUID target, @NotNull AbstractAuctionMenu<?> menu, boolean force) {
        if (!force) {
            if (!this.canBeUsedHere(player)) return false;
        }

        return menu.open(player, target);
    }

    public boolean isAllowedItem(@NotNull ItemStack item) {
        Set<String> bannedItems = AuctionConfig.LISTINGS_DISABLED_ITEMS.get().stream().map(String::toLowerCase).collect(Collectors.toSet());
        if (bannedItems.contains(BukkitThing.toString(item.getType()))) {
            return false;
        }

        for (PluginItemPacker packer : ProductHandlerRegistry.getPluginItemPackers()) {
            String id = packer.getItemId(item);
            if (id != null && bannedItems.contains(id.toLowerCase())) {
                return false;
            }
        }

        ItemMeta meta = item.getItemMeta();
        if (meta == null) return true;

        if (meta.hasDisplayName()) {
            String itemName = Colorizer.restrip(meta.getDisplayName().toLowerCase());
            Set<String> badNames = AuctionConfig.LISTINGS_DISABLED_NAMES.get();
            if (badNames.stream().map(String::toLowerCase).anyMatch(itemName::contains)) {
                return false;
            }
        }

        List<String> itemLore = meta.getLore();
        if (itemLore != null) {
            Set<String> badLores = AuctionConfig.LISTINGS_DISABLED_LORES.get().stream().map(String::toLowerCase).collect(Collectors.toSet());
            if (itemLore.stream().anyMatch(loreLine -> badLores.stream().anyMatch(loreLine::contains))) {
                return false;
            }
        }

        return true;
    }

    public static boolean checkItemModel(@NotNull ItemStack item) {
        ItemMeta meta = item.getItemMeta();
        if (meta == null || !meta.hasCustomModelData()) return true;

        int model = meta.getCustomModelData();

        Set<Integer> banned = AuctionConfig.LISTINGS_DISABLED_MODELS.get().getOrDefault(item.getType(), Collections.emptySet());
        return !banned.contains(model);
    }

    public boolean sell(@NotNull Player player, @NotNull ItemStack item, double price) {
        Set<Currency> currencies = this.getAllowedCurrencies(player);
        if (currencies.isEmpty()) {
            AuctionLang.ERROR_NO_PERMISSION.getMessage().send(player);
            return false;
        }

        if (!player.hasPermission(AuctionPerms.BYPASS_DISABLED_GAMEMODES)) {
            if (AuctionUtils.isBadGamemode(player.getGameMode())) {
                AuctionLang.LISTING_ADD_ERROR_DISABLED_GAMEMODE.getMessage().send(player);
                return false;
            }
        }

        if (!this.isAllowedItem(item) || !checkItemModel(item)) {
            AuctionLang.LISTING_ADD_ERROR_BAD_ITEM.getMessage()
                .replace(Placeholders.GENERIC_ITEM, ItemUtil.getItemName(item))
                .send(player);
            return false;
        }

        price = AuctionUtils.finePrice(price);
        if (price <= 0D) {
            AuctionLang.LISTING_ADD_ERROR_INVALID_PRICE.getMessage().send(player);
            return false;
        }

        if (!player.hasPermission(AuctionPerms.BYPASS_LISTING_PRICE)) {
            Material material = item.getType();
            double matPriceUnit = price / (double) item.getAmount();
            double matPriceMin = AuctionUtils.getMaterialPriceMin(material);
            double matPriceMax = AuctionUtils.getMaterialPriceMax(material);

            if (matPriceMin >= 0D && matPriceUnit < matPriceMin) {
                AuctionLang.LISTING_ADD_ERROR_PRICE_MATERIAL_MIN.getMessage()
                    .replace(Placeholders.GENERIC_ITEM, LangAssets.get(material))
                    .replace(Placeholders.GENERIC_AMOUNT, NumberUtil.format(matPriceMin))
                    .send(player);
                return false;
            }
            if (matPriceMax >= 0D && matPriceUnit > matPriceMax) {
                AuctionLang.LISTING_ADD_ERROR_PRICE_MATERIAL_MAX.getMessage()
                    .replace(Placeholders.GENERIC_ITEM, LangAssets.get(material))
                    .replace(Placeholders.GENERIC_AMOUNT, NumberUtil.format(matPriceMax))
                    .send(player);
                return false;
            }
        }

        int listingsHas = this.listings.getActive(player).size();
        int listingsMax = this.getListingsMaximum(player);
        if (listingsMax >= 0 && listingsHas >= listingsMax) {
            AuctionLang.LISTING_ADD_ERROR_LIMIT.getMessage().replace(Placeholders.GENERIC_AMOUNT, listingsMax).send(player);
            return false;
        }

        ItemStack copyStack = new ItemStack(item);
        player.getInventory().setItemInMainHand(null); // First of all take player's item.

        if (currencies.size() == 1) {
            Currency currency = currencies.stream().findFirst().orElse(null);
            if (this.add(player, copyStack, currency, price) == null) {
                if (player.isOnline()) {
                    Players.addItem(player, copyStack);
                }
                return false;
            }
            return true;
        }

        this.openCurrencySelection(player, item, price);
        return true;
    }

    @Nullable
    public ActiveListing add(@NotNull Player player, @NotNull ItemStack item, @NotNull Currency currency, double price) {
        if (!player.hasPermission(AuctionPerms.BYPASS_LISTING_PRICE)) {
            double curPriceMin = AuctionUtils.getCurrencyPriceMin(currency);
            double curPriceMax = AuctionUtils.getCurrencyPriceMax(currency);

            if (curPriceMax > 0 && price > curPriceMax) {
                AuctionLang.LISTING_ADD_ERROR_PRICE_CURRENCY_MAX.getMessage()
                    .replace(Placeholders.GENERIC_AMOUNT, currency.format(curPriceMax))
                    .replace(currency.replacePlaceholders())
                    .send(player);
                return null;
            }
            if (curPriceMin > 0 && price < curPriceMin) {
                AuctionLang.LISTING_ADD_ERROR_PRICE_CURRENCY_MIN.getMessage()
                    .replace(Placeholders.GENERIC_AMOUNT, currency.format(curPriceMin))
                    .replace(currency.replacePlaceholders())
                    .send(player);
                return null;
            }
        }

        double taxAmount = AuctionUtils.getSellTax(player);
        double taxPay = AuctionUtils.getTax(currency, price, taxAmount);
        if (taxPay > 0) {
            double balance = currency.getHandler().getBalance(player);
            if (balance < taxPay) {
                AuctionLang.LISTING_ADD_ERROR_PRICE_TAX.getMessage()
                    .replace(Placeholders.GENERIC_TAX, taxAmount)
                    .replace(Placeholders.GENERIC_AMOUNT, currency.format(taxPay))
                    .send(player);
                return null;
            }
            currency.getHandler().take(player, taxPay);
        }

        ActiveListing listing = ActiveListing.create(player, item, currency, price);
        this.listings.add(listing);
        this.plugin.runTaskAsync(task -> this.getDataHandler().addListing(listing));

        AuctionLang.LISTING_ADD_SUCCESS_INFO.getMessage()
            .replace(Placeholders.GENERIC_TAX, currency.format(taxPay))
            .replace(listing.replacePlaceholders())
            .send(player);

        if (AuctionConfig.LISTINGS_ANNOUNCE.get()) {
            AuctionLang.LISTING_ADD_SUCCESS_ANNOUNCE.getMessage()
                .replace(Placeholders.forPlayer(player))
                .replace(listing.replacePlaceholders())
                .broadcast();
        }

        this.mainMenu.flush();
        this.sellingMenu.flush();
        return listing;
    }

    public boolean buy(@NotNull Player buyer, @NotNull ActiveListing listing) {
        if (this.needEnsureListingExists() && !this.getDataHandler().isListingExist(listing.getId())) return false;
        if (!this.listings.hasListing(listing.getId())) return false;

        double balance = listing.getCurrency().getHandler().getBalance(buyer);
        double price = listing.getPrice();
        if (balance < price) {
            AuctionLang.LISTING_BUY_ERROR_NOT_ENOUGH_FUNDS.getMessage()
                .replace(Placeholders.GENERIC_BALANCE, listing.getCurrency().format(balance))
                .replace(listing.replacePlaceholders())
                .send(buyer);
            return false;
        }

        listing.getCurrency().getHandler().take(buyer, price);
        Players.addItem(buyer, listing.getItemStack());

        CompletedListing completedListing = CompletedListing.create(listing, buyer);

        this.listings.remove(listing);
        this.listings.addCompleted(completedListing);
        this.plugin.runTaskAsync(task -> {
            this.getDataHandler().addCompletedListing(completedListing);
            this.getDataHandler().deleteListing(listing);
        });
        AuctionLang.LISTING_BUY_SUCCESS_INFO.getMessage().replace(listing.replacePlaceholders()).send(buyer);

        // Notify the seller about the purchase.
        Player seller = plugin.getServer().getPlayer(listing.getOwner());
        if (seller != null) {
            if (AuctionConfig.LISINGS_AUTO_CLAIM.get()) {
                this.claimRewards(seller, completedListing);
            }
            else {
                int unclaimed = this.listings.getUnclaimed(seller).size();
                AuctionLang.NOTIFY_UNCLAIMED_LISTINGS.getMessage()
                    .replace(Placeholders.GENERIC_AMOUNT, unclaimed)
                    .send(seller);
            }
        }

        this.mainMenu.flush();
        this.sellingMenu.flush();
        this.unclaimedMenu.flush();
        return true;
    }

    public void takeListing(@NotNull Player player, @NotNull ActiveListing listing) {
        if (this.needEnsureListingExists() && !this.getDataHandler().isListingExist(listing.getId())) return;
        if (!this.listings.hasListing(listing.getId())) return;

        Players.addItem(player, listing.getItemStack());
        this.listings.remove(listing);
        this.plugin.runTaskAsync(task -> this.getDataHandler().deleteListing(listing));

        this.mainMenu.flush();
    }

    public void claimRewards(@NotNull Player player, @NotNull List<CompletedListing> listings) {
        this.claimRewards(player, listings.toArray(new CompletedListing[0]));
    }

    public void claimRewards(@NotNull Player player, @NotNull CompletedListing... listings) {
        for (CompletedListing listing : listings) {
            if (this.needEnsureListingExists() && this.getDataHandler().isCompletedListingClaimed(listing.getId())) {
                listing.setClaimed(true);
            }
            if (listing.isClaimed()) continue;

            listing.getCurrency().getHandler().give(player, listing.getPrice());
            listing.setClaimed(true);

            AuctionLang.LISTING_CLAIM_SUCCESS.getMessage()
                .replace(listing.replacePlaceholders())
                .send(player);
        }

        this.plugin.runTaskAsync(task -> this.getDataHandler().saveCompletedListings(listings));
    }

    public boolean canBeUsedHere(@NotNull Player player) {
        if (!player.hasPermission(AuctionPerms.BYPASS_DISABLED_WORLDS)) {
            if (AuctionUtils.isDisabledWorld(player.getWorld())) {
                AuctionLang.ERROR_DISABLED_WORLD.getMessage().send(player);
                return false;
            }
        }
        return true;
    }

    public int getListingsMaximum(@NotNull Player player) {
        return AuctionUtils.getPossibleListings(player);
    }
}
