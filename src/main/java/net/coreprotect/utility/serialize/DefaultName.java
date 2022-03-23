package net.coreprotect.utility.serialize;

import net.coreprotect.utility.Util;

/**
 * @author Pavithra Gunasekaran
 */
class DefaultName extends Name {

    @Override
    String getName(String param) {
        name = Util.capitalize(param.replace("_", " "), true);

        return name;
    }
}
