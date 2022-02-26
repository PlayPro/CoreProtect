package net.coreprotect.paper;

import org.bukkit.Server;

public class PaperHandler extends PaperAdapter implements PaperInterface {

    @Override
    public boolean isStopping(Server server) {
        return server.isStopping();
    }

}
