package mb.complex.spoofax.platform;

import dagger.Component;
import mb.log.api.LoggerFactory;

import javax.inject.Singleton;

@Singleton
@Component(modules = {LoggerFactoryModule.class})
public interface PlatformComponent {
    LoggerFactory getLoggerFactory();
}
