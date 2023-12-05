package su.nightexpress.nexshop.shop.chest;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import su.nexmedia.engine.api.manager.AbstractManager;
import su.nexmedia.engine.api.server.AbstractTask;
import su.nexmedia.engine.utils.random.Rnd;
import su.nightexpress.nexshop.ExcellentShop;
import su.nightexpress.nexshop.Placeholders;
import su.nightexpress.nexshop.shop.chest.config.ChestConfig;
import su.nightexpress.nexshop.shop.chest.impl.ChestProduct;
import su.nightexpress.nexshop.shop.chest.impl.ChestShop;
import su.nightexpress.nexshop.shop.chest.nms.ChestNMS;
import su.nightexpress.nexshop.shop.chest.util.ShopType;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ChestDisplayHandler extends AbstractManager<ExcellentShop> {

    private final ChestShopModule chestShop;

    private Map<ChestShop, Integer> holograms;
    private Map<ChestShop, Integer> items;

    private Slider slider;

    public ChestDisplayHandler(@NotNull ChestShopModule chestShop) {
        super(chestShop.plugin());
        this.chestShop = chestShop;
    }

    class Slider extends AbstractTask<ExcellentShop> {

        private final Map<ShopType, Integer> count = new HashMap<>();

        Slider() {
            super(chestShop.plugin(), ChestConfig.DISPLAY_SLIDE_INTERVAL.get(), false);
        }

        @Override
        public void action() {
            chestShop.getShops().forEach(shop -> {
                remove(shop);
                create(shop);
            });

            for (ShopType chestType : ShopType.values()) {
                int count = this.count.getOrDefault(chestType, 0) + 1;
                if (count >= ChestUtils.getHologramLines(chestType).size()) count = 0;
                this.count.put(chestType, count);
            }
        }

        public int getCount(@NotNull ChestShop shop) {
            return this.count.getOrDefault(shop.getType(), 0);
        }
    }

    @Override
    protected void onLoad() {
        this.holograms = new HashMap<>();
        this.items = new HashMap<>();

        if (ChestConfig.DISPLAY_SLIDE_INTERVAL.get() > 0) {
            this.slider = new Slider();
            this.slider.start();
        }
    }

    @Override
    protected void onShutdown() {
        if (this.slider != null) {
            this.slider.stop();
            this.slider = null;
        }
        this.chestShop.getShops().forEach(this::remove);
        this.holograms.clear();
        this.items.clear();
    }

    private void addHologram(@NotNull ChestShop shop, @NotNull String name) {
        Location location = shop.getDisplayLocation();
        ItemStack showcase = ChestConfig.DISPLAY_SHOWCASE.get()
            .getOrDefault(shop.getLocation().getBlock().getType().name(), ChestConfig.DISPLAY_SHOWCASE.get().get(Placeholders.DEFAULT));
        if (showcase == null || showcase.getType().isAir()) return;

        if (!ChestConfig.DISPLAY_HOLOGRAM_ENABLED.get()) {
            name = "";
        }
        String name2 = name;

        this.holograms.computeIfAbsent(shop, i -> chestShop.getNMS().createHologram(location, showcase, name2));
    }

    private void deleteHologram(@NotNull ChestShop shop) {
        if (!holograms.containsKey(shop)) return;
        int stand = this.holograms.remove(shop);
        if (stand < 0) return;

        chestShop.getNMS().deleteEntity(stand);
    }

    private void addItem(@NotNull ChestShop shop) {
        Location location = shop.getDisplayItemLocation();
        List<ChestProduct> products = new ArrayList<>(shop.getProducts());
        ItemStack items = products.isEmpty() ? ChestNMS.UNKNOWN : Rnd.get(products).getPreview();

        int item = this.items.computeIfAbsent(shop, i -> chestShop.getNMS().createItem(location, items));
    }

    private void deleteItem(@NotNull ChestShop shop) {
        if (!items.containsKey(shop)) return;
        int item = this.items.remove(shop);

        chestShop.getNMS().deleteEntity(item);
    }

    public void create(@NotNull ChestShop chest) {
        Location location = chest.getLocation();
        World world = location.getWorld();
        if (world == null || !world.isChunkLoaded(chest.getChunkX(), chest.getChunkZ())) {
            //System.out.println("chunk is out, skipping");
            return;
        }
        //System.out.println("display set");
        if (!chest.getDisplayText().isEmpty()) {
            int count = this.slider != null ? this.slider.getCount(chest) : 0;
            if (chest.getDisplayText().size() > count) {
                this.addHologram(chest, chest.getDisplayText().get(count));
            }
        }
        this.addItem(chest);
    }

    public void remove(@NotNull ChestShop chest) {
        this.deleteHologram(chest);
        this.deleteItem(chest);
    }
}