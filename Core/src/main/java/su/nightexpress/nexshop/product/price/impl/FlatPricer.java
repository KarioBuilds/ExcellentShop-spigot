package su.nightexpress.nexshop.product.price.impl;

import org.jetbrains.annotations.NotNull;
import su.nightexpress.nexshop.api.shop.type.PriceType;
import su.nightexpress.nexshop.api.shop.type.TradeType;
import su.nightexpress.nexshop.product.price.AbstractProductPricer;
import su.nightexpress.nightcore.config.FileConfig;

import java.util.function.UnaryOperator;

public class FlatPricer extends AbstractProductPricer {

    public FlatPricer() {
        super(PriceType.FLAT);
    }

    @NotNull
    public static FlatPricer read(@NotNull FileConfig cfg, @NotNull String path) {
        FlatPricer pricer = new FlatPricer();
        for (TradeType tradeType : TradeType.values()) {
            pricer.setPrice(tradeType, cfg.getDouble(path + "." + tradeType.name()));
        }
        return pricer;
    }

    @Override
    protected void writeAdditional(@NotNull FileConfig cfg, @NotNull String path) {
        this.priceCurrent.forEach(((tradeType, amount) -> cfg.set(path + "." + tradeType.name(), amount)));
    }

    @Override
    @NotNull
    public UnaryOperator<String> replacePlaceholders() {
        return str -> str;
    }
}
