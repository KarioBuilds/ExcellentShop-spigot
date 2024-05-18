package su.nightexpress.nexshop.module;

import org.jetbrains.annotations.NotNull;
import su.nightexpress.nexshop.ShopPlugin;
import su.nightexpress.nexshop.api.shop.ShopModule;
import su.nightexpress.nexshop.config.Config;
import su.nightexpress.nightcore.command.experimental.RootCommand;
import su.nightexpress.nightcore.command.experimental.ServerCommand;
import su.nightexpress.nightcore.command.experimental.builder.ChainedNodeBuilder;
import su.nightexpress.nightcore.config.FileConfig;
import su.nightexpress.nightcore.manager.AbstractManager;
import su.nightexpress.nightcore.util.StringUtil;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;

public abstract class AbstractShopModule extends AbstractManager<ShopPlugin> implements ShopModule {

    public static final String CONFIG_NAME = "settings.yml";

    private final String   id;
    private final String   name;
    private final String[] aliases;

    private ServerCommand moduleCommand;

    public AbstractShopModule(@NotNull ShopPlugin plugin, @NotNull String id, @NotNull String[] aliases) {
        super(plugin);
        this.id = id;
        this.name = StringUtil.capitalizeUnderscored(this.getId());
        this.aliases = aliases;
    }

    @Override
    protected final void onLoad() {
        // ---------- MOVE OUT OF /MODULES/ START ----------
        File dirOld = new File(plugin.getDataFolder().getAbsolutePath() + "/modules/" + this.getId());
        File dirNew = new File(plugin.getDataFolder().getAbsolutePath() + "/" + this.getId());
        if (dirOld.exists() && !dirNew.exists()) {
            try {
                Files.move(dirOld.toPath(), dirNew.toPath(), StandardCopyOption.REPLACE_EXISTING);
            }
            catch (IOException e) {
                e.printStackTrace();
            }
        }
        // ---------- MOVE OUT OF /MODULES/ END ----------

        FileConfig config = this.getConfig();

        this.loadModule(config);

        this.moduleCommand = RootCommand.chained(this.plugin, this.aliases, builder -> {
            builder.localized(this.getName());
            ReloadCommand.build(this, builder);
            this.addCommands(builder);
        });
        this.plugin.getCommandManager().registerCommand(this.moduleCommand);

        config.saveChanges();
    }

    @Override
    protected final void onShutdown() {
        this.disableModule();

        this.plugin.getCommandManager().unregisterCommand(this.moduleCommand);
    }

    protected abstract void loadModule(@NotNull FileConfig config);

    protected abstract void disableModule();

    protected abstract void addCommands(@NotNull ChainedNodeBuilder builder);

    @NotNull
    public String getId() {
        return id;
    }

    @NotNull
    public String getName() {
        return name;
    }

    @NotNull
    public FileConfig getConfig() {
        return FileConfig.loadOrExtract(plugin, this.getLocalPath(), CONFIG_NAME);
    }

    @NotNull
    public final String getLocalPath() {
        return this.getId();
    }

    @NotNull
    public final String getMenusPath() {
        return this.getLocalPath() + Config.DIR_MENU;
    }

    @NotNull
    public final String getAbsolutePath() {
        return this.plugin.getDataFolder() + "/" + this.getLocalPath();
    }

    @NotNull
    private String buildLog(@NotNull String msg) {
        return "[" + this.getName() + "] " + msg;
    }

    public final void info(@NotNull String msg) {
        this.plugin.info(this.buildLog(msg));
    }

    public final void warn(@NotNull String msg) {
        this.plugin.warn(this.buildLog(msg));
    }

    public final void error(@NotNull String msg) {
        this.plugin.error(this.buildLog(msg));
    }
}
