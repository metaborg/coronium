package mb.complex.tiger.eclipse.internal;

import mb.complex.spoofax.eclipse.SpoofaxPlugin;
import org.eclipse.core.commands.AbstractHandler;
import org.eclipse.core.commands.ExecutionEvent;
import org.eclipse.core.commands.ExecutionException;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.ui.handlers.HandlerUtil;

public class Handler extends AbstractHandler {
    @Override public Object execute(ExecutionEvent event) throws ExecutionException {
        final String sender = "Tiger";
        final String message = "Hello, world! From Tiger!";
        SpoofaxPlugin.getComponent().getLoggerFactory().create(sender).info(message);
        MessageDialog.openInformation(HandlerUtil.getActiveWorkbenchWindowChecked(event).getShell(), sender, message);
        return null;
    }
}
