package mb.complex.tiger;

import mb.complex.spoofax.language.LanguageScope;
import mb.log.api.Logger;
import mb.log.api.LoggerFactory;

import javax.inject.Inject;

@LanguageScope
public class Tiger {
    private final Logger logger;

    @Inject public Tiger(LoggerFactory loggerFactory) {
        this.logger = loggerFactory.create(this.getClass());
    }

    public void doStuff() {
        logger.info("Tiger does stuff");
    }
}
