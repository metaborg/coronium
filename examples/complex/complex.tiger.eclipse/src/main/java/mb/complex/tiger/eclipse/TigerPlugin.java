package mb.complex.tiger.eclipse;

import mb.complex.spoofax.eclipse.SpoofaxPlugin;
import mb.complex.tiger.DaggerTigerComponent;
import mb.complex.tiger.TigerComponent;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.eclipse.ui.plugin.AbstractUIPlugin;
import org.osgi.framework.BundleContext;

public class TigerPlugin extends AbstractUIPlugin {
    public static final String id = "complex.tiger.eclipse";

    private static @Nullable TigerPlugin plugin;
    private static @Nullable TigerComponent component;

    public static TigerPlugin getPlugin() {
        if(plugin == null) {
            throw new RuntimeException(
                "Cannot access SpoofaxPlugin instance; it has not been started yet, or has been stopped");
        }
        return plugin;
    }

    public static TigerComponent getComponent() {
        if(component == null) {
            throw new RuntimeException(
                "Cannot access EclipsePlatformComponent; SpoofaxPlugin has not been started yet, or has been stopped");
        }
        return component;
    }


    @Override public void start(@NonNull BundleContext context) throws Exception {
        super.start(context);
        plugin = this;
        component = DaggerTigerComponent
            .builder()
            .platformComponent(SpoofaxPlugin.getComponent())
            .build();
    }

    @Override public void stop(@NonNull BundleContext context) throws Exception {
        super.stop(context);
        component = null;
        plugin = null;
    }
}
