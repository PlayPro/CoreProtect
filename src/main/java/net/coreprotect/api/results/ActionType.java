package net.coreprotect.api.results;

import net.coreprotect.language.Phrase;
import net.coreprotect.language.Selector;

import java.util.Arrays;
import java.util.List;

public enum ActionType {
    REMOVED(0, Phrase.LOOKUP_CONTAINER, Selector.SECOND, "remove"),
    ADDED(1, Phrase.LOOKUP_CONTAINER, Selector.FIRST, "add"),

    DROPPED(2, Phrase.LOOKUP_ITEM, Selector.SECOND, "drop"),
    PICKED_UP(3, Phrase.LOOKUP_ITEM, Selector.FIRST, "pick up"),

    DEPOSITED(5, Phrase.LOOKUP_STORAGE, Selector.FIRST, "deposit"),
    WITHDREW(4, Phrase.LOOKUP_STORAGE, Selector.SECOND, "withdraw"),

    BROKE(0, Phrase.LOOKUP_BLOCK, Selector.SECOND, "break"),
    PLACED(1, Phrase.LOOKUP_BLOCK, Selector.FIRST, "place"),

    CLICKED(2, Phrase.LOOKUP_INTERACTION, Selector.FIRST, "click"),
    KILLED(3, Phrase.LOOKUP_INTERACTION, Selector.SECOND, "kill"),

    UNKNOWN(-1, null, null, "unknown");

    private final int id;
    private final Phrase phrase;
    private final String string;
    private final String selector;

    ActionType(int id, Phrase phrase, String selector, String string) {
        this.id = id;
        this.phrase = phrase;
        this.selector = selector;
        this.string = string;
    }

    public Phrase getPhrase() {
        return phrase;
    }

    public String getSelector() {
        return selector;
    }

    public int getId() {
        return id;
    }

    public String getBuiltPhrase() {
        return Phrase.build(getPhrase(), "", "", "", getSelector());
    }

    public String getBuiltPhrase(String... params) {
        List<String> arr = Arrays.asList(params);
        arr.add(getSelector());
        return Phrase.build(getPhrase(), arr.toArray(params));
    }

    public String getString() {
        return this.string;
    }

}
