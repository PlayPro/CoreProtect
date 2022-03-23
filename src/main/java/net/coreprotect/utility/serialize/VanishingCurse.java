package net.coreprotect.utility.serialize;

/**
 * @author Pavithra Gunasekaran
 */
class VanishingCurse extends Name {

    String getName(String param) {
        if (param.equals("vanishing_curse")) {
            name = "Curse of Vanishing";
        }
        return name;
    }

}
