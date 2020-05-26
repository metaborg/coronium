package mb.complex.spoofax.eclipse;

import mb.complex.spoofax.platform.LoggerFactoryModule;
import mb.log.slf4j.SLF4JLoggerFactory;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.eclipse.ui.plugin.AbstractUIPlugin;
import org.osgi.framework.BundleContext;

public class SpoofaxPlugin extends AbstractUIPlugin {
    public static final String id = "complex.spoofax.eclipse";

    private static @Nullable SpoofaxPlugin plugin;
    private static @Nullable EclipsePlatformComponent component;


    public static SpoofaxPlugin getPlugin() {
        if(plugin == null) {
            throw new RuntimeException(
                "Cannot access SpoofaxPlugin instance; it has not been started yet, or has been stopped");
        }
        return plugin;
    }

    public static EclipsePlatformComponent getComponent() {
        if(component == null) {
            throw new RuntimeException(
                "Cannot access EclipsePlatformComponent; SpoofaxPlugin has not been started yet, or has been stopped");
        }
        return component;
    }


    @Override public void start(@NonNull BundleContext context) throws Exception {
        super.start(context);
        plugin = this;
        component = DaggerEclipsePlatformComponent
            .builder()
            .loggerFactoryModule(new LoggerFactoryModule(new SLF4JLoggerFactory()))
            .build();
    }

    @Override public void stop(@NonNull BundleContext context) throws Exception {
        super.stop(context);
        component = null;
        plugin = null;
    }
}
