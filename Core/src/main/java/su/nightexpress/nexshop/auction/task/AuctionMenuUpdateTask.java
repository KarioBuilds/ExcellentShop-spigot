package su.nightexpress.nexshop.auction.task;

import org.jetbrains.annotations.NotNull;
import su.nexmedia.engine.api.server.AbstractTask;
import su.nightexpress.nexshop.ExcellentShop;
import su.nightexpress.nexshop.auction.AuctionManager;
import su.nightexpress.nexshop.auction.config.AuctionConfig;

@Deprecated
public class AuctionMenuUpdateTask extends AbstractTask<ExcellentShop> {

    private final AuctionManager auctionManager;

    public AuctionMenuUpdateTask(@NotNull AuctionManager auctionManager) {
        super(auctionManager.plugin(), AuctionConfig.MENU_UPDATE_INTERVAL.get(), false);
        this.auctionManager = auctionManager;
    }

    @Override
    public void action() {
        this.auctionManager.getMainMenu().update();
        this.auctionManager.getExpiredMenu().update();
        this.auctionManager.getUnclaimedMenu().update();
        this.auctionManager.getSellingMenu().update();
    }
}
