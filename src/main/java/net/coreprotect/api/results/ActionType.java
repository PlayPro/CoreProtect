package net.coreprotect.api.results;

import net.coreprotect.language.Phrase;
import net.coreprotect.language.Selector;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

public enum ActionType {
    REMOVED(0, Phrase.LOOKUP_CONTAINER, Selector.SECOND),
    ADDED(1, Phrase.LOOKUP_CONTAINER, Selector.FIRST),

    DROPPED(2, Phrase.LOOKUP_ITEM, Selector.SECOND),
    PICKED_UP(3, Phrase.LOOKUP_ITEM, Selector.FIRST),

    DEPOSITED(5, Phrase.LOOKUP_STORAGE, Selector.FIRST),
    WITHDREW(4, Phrase.LOOKUP_STORAGE, Selector.SECOND),

    BROKE(0, Phrase.LOOKUP_BLOCK, Selector.SECOND),
    PLACED(1, Phrase.LOOKUP_BLOCK, Selector.FIRST),

    CLICKED(2, Phrase.LOOKUP_INTERACTION, Selector.FIRST),
    KILLED(3, Phrase.LOOKUP_INTERACTION, Selector.SECOND),

    UNKNOWN(-1, null, null);

    private final int id;
    private final Phrase phrase;
    private final String selector;

    ActionType(int id, Phrase phrase, String selector) {
        this.id = id;
        this.phrase = phrase;
        this.selector = selector;
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

    public String getActionString() {
        return this.name().toLowerCase(Locale.ROOT).replace("_", " ");
    }

}
