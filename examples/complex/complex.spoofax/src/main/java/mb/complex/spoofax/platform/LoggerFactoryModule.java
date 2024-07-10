package mb.complex.spoofax.platform;

import dagger.Module;
import dagger.Provides;
import mb.log.api.LoggerFactory;

import javax.inject.Singleton;

@Module
public class LoggerFactoryModule {
    private final LoggerFactory loggerFactory;

    public LoggerFactoryModule(LoggerFactory loggerFactory) {
        this.loggerFactory = loggerFactory;
    }

    @Provides @Singleton LoggerFactory provideLoggerFactory() {
        return loggerFactory;
    }
}

