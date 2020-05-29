package mb.complex.spoofax.eclipse;

import dagger.Component;
import mb.complex.spoofax.platform.LoggerFactoryModule;
import mb.complex.spoofax.platform.PlatformComponent;

import javax.inject.Singleton;

@Singleton
@Component(modules = {LoggerFactoryModule.class})
public interface EclipsePlatformComponent extends PlatformComponent {

}
