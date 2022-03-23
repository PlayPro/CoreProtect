package net.coreprotect.utility.serialize;

/**
 * @author Pavithra Gunasekaran
 */
class BindingCurse extends Name {

    @Override
    String getName(String param) {
        if (param.equals("binding_curse")) {
            name = "Curse of Binding";
        }
        return null;
    }
}
