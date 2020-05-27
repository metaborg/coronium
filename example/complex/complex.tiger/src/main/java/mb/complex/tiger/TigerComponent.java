package mb.complex.tiger;

import dagger.Component;
import mb.complex.spoofax.language.LanguageScope;
import mb.complex.spoofax.platform.PlatformComponent;

@LanguageScope @Component(modules = TigerModule.class, dependencies = PlatformComponent.class)
public interface TigerComponent {
    Tiger getTiger();
}
