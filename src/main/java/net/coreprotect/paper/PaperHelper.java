package net.coreprotect.paper;

import org.bukkit.Server;

/**
 * @author Pavithra Gunasekaran
 */
public class PaperHelper extends PaperHandler{
    public PaperHelper() {
    }

    @Override
    public boolean isStopping(Server server) {
        return super.isStopping(server);
    }
}
