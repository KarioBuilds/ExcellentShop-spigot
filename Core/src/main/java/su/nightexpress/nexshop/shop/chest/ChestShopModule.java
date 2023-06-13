package su.nightexpress.nexshop.shop.chest;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.Chest;
import org.bukkit.block.Container;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Directional;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import su.nexmedia.engine.Version;
import su.nexmedia.engine.api.config.JYML;
import su.nexmedia.engine.hooks.Hooks;
import su.nexmedia.engine.hooks.external.VaultHook;
import su.nexmedia.engine.utils.Pair;
import su.nightexpress.nexshop.ExcellentShop;
import su.nightexpress.nexshop.Perms;
import su.nightexpress.nexshop.Placeholders;
import su.nightexpress.nexshop.api.currency.ICurrency;
import su.nightexpress.nexshop.data.price.ProductPriceManager;
import su.nightexpress.nexshop.hooks.HookId;
import su.nightexpress.nexshop.module.ModuleId;
import su.nightexpress.nexshop.module.ShopModule;
import su.nightexpress.nexshop.shop.chest.command.*;
import su.nightexpress.nexshop.shop.chest.compatibility.ClaimHook;
import su.nightexpress.nexshop.shop.chest.compatibility.GriefPreventionHook;
import su.nightexpress.nexshop.shop.chest.compatibility.LandsHook;
import su.nightexpress.nexshop.shop.chest.compatibility.WorldGuardFlags;
import su.nightexpress.nexshop.shop.chest.config.ChestConfig;
import su.nightexpress.nexshop.shop.chest.config.ChestLang;
import su.nightexpress.nexshop.shop.chest.impl.ChestShop;
import su.nightexpress.nexshop.shop.chest.listener.ChestShopListener;
import su.nightexpress.nexshop.shop.chest.menu.ShopsListMenu;
import su.nightexpress.nexshop.shop.chest.menu.ShopsSearchMenu;
import su.nightexpress.nexshop.shop.chest.nms.*;
import su.nightexpress.nexshop.shop.chest.type.ChestShopType;

import java.util.*;
import java.util.stream.Stream;

public class ChestShopModule extends ShopModule {

    public static final String DIR_SHOPS = "/shops/";

    private final Map<Location, ChestShop> shops;

    private Set<ClaimHook> claimHooks;
    private ShopsListMenu   listMenu;
    private ShopsSearchMenu searchMenu;
    private ChestDisplayHandler displayHandler;
    private ChestNMS  chestNMS;

    public ChestShopModule(@NotNull ExcellentShop plugin) {
        super(plugin, ModuleId.CHEST_SHOP);
        this.shops = new HashMap<>();
    }

    @Override
    protected void onLoad() {
        super.onLoad();

        ChestConfig.load(this);
        if (ChestConfig.DEFAULT_CURRENCY == null) {
            this.interruptLoad("Invalid default currency!");
            return;
        }
        this.plugin.getLangManager().loadMissing(ChestLang.class);
        this.plugin.getLangManager().setupEnum(ChestShopType.class);
        this.plugin.getLang().saveChanges();
        this.plugin.registerPermissions(ChestPerms.class);

        this.chestNMS = switch (Version.CURRENT) {
            case V1_17_R1 -> new V1_17_R1();
            case V1_18_R2 -> new V1_18_R2();
            case V1_19_R1 -> new V1_19_R1();
            case V1_19_R2 -> new V1_19_R2();
            case V1_19_R3 -> new V1_19_R3();
            default ->
            {
                yield null;
            }
        };
        this.displayHandler = new ChestDisplayHandler(this);
        this.displayHandler.setup();

        // Setup Claim Hooks
        if (ChestConfig.SHOP_CREATION_CLAIM_ONLY) {
            this.claimHooks = new HashSet<>();
            if (Hooks.hasPlugin(HookId.LANDS)) this.claimHooks.add(new LandsHook(this.plugin));
            if (Hooks.hasPlugin(HookId.GRIEF_PREVENTION)) this.claimHooks.add(new GriefPreventionHook());
            if (Hooks.hasWorldGuard()) this.claimHooks.add(new WorldGuardFlags());
        }

        this.addListener(new ChestShopListener(this));

        this.listMenu = new ShopsListMenu(this);
        this.searchMenu = new ShopsSearchMenu(this);

        this.moduleCommand.addChildren(new CreateCommand(this));
        this.moduleCommand.addChildren(new RemoveCommand(this));
        this.moduleCommand.addChildren(new ListCommand(this));
        this.moduleCommand.addChildren(new SearchCommand(this));
        this.moduleCommand.addChildren(new OpenCommand(this));

        this.plugin.runTask(task -> this.loadShops());
    }

    public void loadShops() {
        for (JYML shopConfig : JYML.loadAll(this.getFullPath() + DIR_SHOPS, false)) {
            this.loadShop(shopConfig);
        }
        this.info("Shops Loaded: " + this.getShops().size());
    }

    public boolean loadShop(@NotNull JYML cfg) {
        ChestShop shop = new ChestShop(this, cfg);
        if (!shop.load()) {
            this.error("Shop not loaded '" + cfg.getFile().getName());
            if (ChestConfig.DELETE_INVALID_SHOP_CONFIGS && cfg.getFile().delete()) {
                this.info("Deleted invalid shop config.");
            }
            return false;
        }
        this.addShop(shop);
        ProductPriceManager.loadData(shop).thenRun(() -> shop.getProducts().forEach(product -> product.getPricer().update()));
        return true;
    }

    public boolean unloadShop(@NotNull ChestShop shop) {
        shop.clear();
        this.getShopsMap().remove(shop.getLocation());
        return true;
    }

    @Override
    protected void onShutdown() {
        super.onShutdown();

        if (this.listMenu != null) {
            this.listMenu.clear();
            this.listMenu = null;
        }
        if (this.searchMenu != null) {
            this.searchMenu.clear();
            this.searchMenu = null;
        }

        // Destroy shop editors and displays.
        this.getShops().forEach(ChestShop::clear);

        if (this.displayHandler != null) {
            this.displayHandler.shutdown();
            this.displayHandler = null;
        }
        if (this.claimHooks != null) {
            this.claimHooks.clear();
            this.claimHooks = null;
        }

        this.shops.clear();
    }

    @NotNull
    public ChestNMS getNMS() {
        return chestNMS;
    }

    @NotNull
    public ShopsListMenu getListMenu() {
        return this.listMenu;
    }

    @NotNull
    public ShopsSearchMenu getSearchMenu() {
        return this.searchMenu;
    }

    public boolean createShop(@NotNull Player player, @NotNull Block block, @NotNull ChestShopType type) {
        if (!isValidContainer(block)) {
            plugin.getMessage(ChestLang.SHOP_CREATION_ERROR_NOT_A_CHEST).send(player);
            return false;
        }

        if (this.isShop(block)) {
            plugin.getMessage(ChestLang.SHOP_CREATION_ERROR_ALREADY_SHOP).send(player);
            return false;
        }

        if (!this.isAllowedHere(player, block.getLocation())) {
            plugin.getMessage(ChestLang.SHOP_CREATION_ERROR_BAD_LOCATION).send(player);
            return false;
        }

        if (!this.isAllowedHereClaim(player, block)) {
            plugin.getMessage(ChestLang.SHOP_CREATION_ERROR_BAD_AREA).send(player);
            return false;
        }

        if (!type.hasPermission(player)) {
            plugin.getMessage(ChestLang.SHOP_CREATION_ERROR_TYPE_PERMISSION).send(player);
            return false;
        }

        Container bChest = (Container) block.getState();
        Inventory cInv = bChest.getInventory();
        if (Stream.of(cInv.getContents()).anyMatch(inside -> inside != null && !inside.getType().isAir())) {
            plugin.getMessage(ChestLang.SHOP_CREATION_ERROR_NOT_EMPTY).send(player);
            return false;
        }

        int allowed = this.getShopsAllowed(player);
        int has = this.getShopsAmount(player);
        if (allowed > 0 && has >= allowed) {
            plugin.getMessage(ChestLang.SHOP_CREATION_ERROR_LIMIT_REACHED).send(player);
            return false;
        }

        if (!this.payForCreate(player)) {
            plugin.getMessage(ChestLang.SHOP_CREATION_ERROR_NOT_ENOUGH_FUNDS).send(player);
            return false;
        }

        ItemStack hand = new ItemStack(player.getInventory().getItemInMainHand());
        ChestShop shop = new ChestShop(this, player, bChest, type);
        shop.createProduct(player, hand);
        shop.save();

        this.addShop(shop);
        plugin.getMessage(ChestLang.SHOP_CREATION_INFO_DONE).send(player);
        return true;
    }

    public boolean deleteShop(@NotNull Player player, @NotNull Block block) {
        ChestShop shop = this.getShop(block);
        if (shop == null) {
            plugin.getMessage(ChestLang.SHOP_REMOVAL_ERROR_NOT_A_SHOP).send(player);
            return false;
        }

        if (!shop.isOwner(player) && !player.hasPermission(ChestPerms.REMOVE_OTHERS)) {
            plugin.getMessage(ChestLang.SHOP_ERROR_NOT_OWNER).send(player);
            return false;
        }

        if (!this.payForRemoval(player)) {
            plugin.getMessage(ChestLang.SHOP_CREATION_ERROR_NOT_ENOUGH_FUNDS).send(player);
            return false;
        }
        
        for (ICurrency currency : ChestConfig.ALLOWED_CURRENCIES) {
            this.withdrawFromShop(player, shop, currency, -1);
        }

        this.removeShop(shop);
        plugin.getMessage(ChestLang.SHOP_REMOVAL_INFO_DONE).send(player);
        return true;
    }

    private void addShop(@NotNull ChestShop shop) {
        Pair<Container, Container> sides = shop.getSides();
        this.getShopsMap().put(sides.getFirst().getLocation(), shop);
        this.getShopsMap().put(sides.getSecond().getLocation(), shop);
        shop.updateDisplay();
    }

    void removeShop(@NotNull ChestShop shop) {
        if (!shop.getFile().delete()) return;
        shop.clear();

        Pair<Container, Container> sides = shop.getSides();
        this.getShopsMap().remove(sides.getFirst().getLocation());
        this.getShopsMap().remove(sides.getSecond().getLocation());
        //shop.getChestSides().stream().map(BlockState::getLocation).forEach(this.shops::remove);
    }

    public boolean depositToShop(@NotNull Player player, @NotNull ChestShop shop, @NotNull ICurrency currency, double amount) {
        if (!ChestConfig.ALLOWED_CURRENCIES.contains(currency)) {
            plugin.getMessage(ChestLang.SHOP_BANK_ERROR_INVALID_CURRENCY).send(player);
            return false;
        }

        if (amount < 0D) amount = currency.getBalance(player);

        if (currency.getBalance(player) < amount) {
            plugin.getMessage(ChestLang.SHOP_BANK_DEPOSIT_ERROR_NOT_ENOUGH).send(player);
            return false;
        }

        currency.take(player, amount);
        shop.getBank().deposit(currency, amount);
        shop.save();

        plugin.getMessage(ChestLang.SHOP_BANK_DEPOSIT_SUCCESS)
            .replace(Placeholders.GENERIC_AMOUNT, currency.format(amount))
            .send(player);
        return true;
    }

    public boolean withdrawFromShop(@NotNull Player player, @NotNull ChestShop shop, @NotNull ICurrency currency, double amount) {
        if (!ChestConfig.ALLOWED_CURRENCIES.contains(currency)) {
            plugin.getMessage(ChestLang.SHOP_BANK_ERROR_INVALID_CURRENCY).send(player);
            return false;
        }

        if (amount < 0D) amount = shop.getBank().getBalance(currency);

        if (!shop.getBank().hasEnough(currency, amount)) {
            plugin.getMessage(ChestLang.SHOP_BANK_WITHDRAW_ERROR_NOT_ENOUGH).send(player);
            return false;
        }

        currency.give(player, amount);
        shop.getBank().withdraw(currency, amount);
        shop.save();

        plugin.getMessage(ChestLang.SHOP_BANK_WITHDRAW_SUCCESS)
            .replace(Placeholders.GENERIC_AMOUNT, currency.format(amount))
            .send(player);
        return true;
    }

    @Nullable
    public ChestDisplayHandler getDisplayHandler() {
        return this.displayHandler;
    }

    @NotNull
    public Map<Location, ChestShop> getShopsMap() {
        return this.shops;
    }

    @NotNull
    public Collection<ChestShop> getShops() {
        return this.getShopsMap().values();
    }

    @NotNull
    public List<ChestShop> getShops(@NotNull Player player) {
        return this.getShops().stream().filter(shop -> shop.getOwnerId().equals(player.getUniqueId())).toList();
    }

    public int getShopsAllowed(@NotNull Player player) {
        return ChestConfig.getMaxShops(player);
    }

    public int getShopsAmount(@NotNull Player player) {
        return this.getShops(player).size();
    }

    @Nullable
    public ChestShop getShop(@NotNull Inventory inventory) {
        Location location = inventory.getLocation();
        if (location == null) return null;

        return this.getShop(location.getBlock());
    }

    @Nullable
    public ChestShop getShop(@NotNull Block block) {
        return this.getShop(block.getLocation());
    }

    @Nullable
    public ChestShop getShop(@NotNull Location location) {
        return this.getShopsMap().get(location);
    }

    @Nullable
    public ChestShop getShopSideChest(@NotNull Block block) {
        BlockData data = block.getBlockData();
        if (!(data instanceof Directional directional)) return null;
        if (!(block.getState() instanceof Chest chest)) return null;

        BlockFace face = directional.getFacing();
        BlockFace[] faces;
        if (face == BlockFace.NORTH || face == BlockFace.SOUTH) {
            faces = new BlockFace[]{BlockFace.EAST, BlockFace.WEST};
        }
        else {
            faces = new BlockFace[]{BlockFace.NORTH, BlockFace.SOUTH};
        }

        return Stream.of(faces).map(block::getRelative).filter(near -> {
            return near.getBlockData() instanceof Directional nearDir && nearDir.getFacing() == face;
        })
            .map(this::getShop).filter(shop -> shop != null && !shop.isDoubleChest()).findFirst().orElse(null);
    }

    public boolean isShop(@NotNull Block block) {
        return this.isShop(block.getLocation());
    }

    public boolean isShop(@NotNull Location location) {
        return this.getShop(location) != null;
    }

    public static boolean isValidContainer(@NotNull Block block) {
        if (!(block.getState() instanceof Container container)) return false;
        if (block.getType() == Material.ENDER_CHEST) return false;
        return ChestConfig.ALLOWED_CONTAINERS.get().contains(block.getType().name());
    }

    public boolean isAllowedHere(@NotNull Player player, @NotNull Location location) {
        World world = location.getWorld();
        if (world == null) return true;

        String name = world.getName();
        if (ChestConfig.SHOP_CREATION_WORLD_BLACKLIST.contains(name)) {
            return false;
        }
        if (Hooks.hasPlugin(Hooks.WORLD_GUARD) && !WorldGuardFlags.checkFlag(player, location)) {
            return false;
        }
        return true;
    }

    public boolean isAllowedHereClaim(@NotNull Player player, @NotNull Block block) {
        // TODO Permission
        if (player.hasPermission(Perms.ADMIN) || !ChestConfig.SHOP_CREATION_CLAIM_ONLY || this.claimHooks.isEmpty()) return true;

        return this.claimHooks.stream().anyMatch(claim -> claim.isInOwnClaim(player, block));
    }

    private boolean payForCreate(@NotNull Player player) {
        return this.payForShop(player, ChestConfig.SHOP_CREATION_COST_CREATE);
    }

    private boolean payForRemoval(@NotNull Player player) {
        return this.payForShop(player, ChestConfig.SHOP_CREATION_COST_REMOVE);
    }

    private boolean payForShop(@NotNull Player player, double price) {
        if (price <= 0 || !VaultHook.hasEconomy()) return true;

        double balance = VaultHook.getBalance(player);
        if (balance < price) return false;

        VaultHook.takeMoney(player, price);
        return true;
    }
}
