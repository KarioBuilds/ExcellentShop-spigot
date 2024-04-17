package su.nightexpress.nexshop.data.object;

import org.jetbrains.annotations.NotNull;
import su.nightexpress.nexshop.api.shop.product.VirtualProduct;
import su.nightexpress.nexshop.api.shop.stock.StockValues;
import su.nightexpress.nexshop.api.shop.type.TradeType;

import java.util.UUID;

public class OwnedStockData extends StockData implements Ownable {

    private final UUID ownerId;

    public OwnedStockData(@NotNull UUID ownerId, @NotNull VirtualProduct product, @NotNull StockValues values, @NotNull TradeType tradeType) {
        this(ownerId, tradeType, product.getShop().getId(), product.getId(), 0, 0);
        this.restock(values);
    }

    public OwnedStockData(
        @NotNull UUID ownerId,
        @NotNull TradeType tradeType,
        @NotNull String shopId,
        @NotNull String productId,
        int itemsLeft,
        long restockDate) {
        super(tradeType, shopId, productId, itemsLeft, restockDate);
        this.ownerId = ownerId;
    }

    @Override
    @NotNull
    public UUID getOwnerId() {
        return this.ownerId;
    }
}
