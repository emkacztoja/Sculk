package dev.emkacz.sculk.util;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class CommandPrefixMatcherTest {

    @Test
    void exactMatchIsAllowed() {
        CommandPrefixMatcher m = new CommandPrefixMatcher(List.of("give %player% cookie"));
        assertTrue(m.isAllowed("give Steve cookie", "Steve"));
    }

    @Test
    void prefixMatchIsAllowed() {
        CommandPrefixMatcher m = new CommandPrefixMatcher(List.of("give %player% cookie"));
        assertTrue(m.isAllowed("give Steve cookie 5", "Steve"));
    }

    @Test
    void differentCommandWithSamePrefixIsBlocked() {
        // ensure matcher is strict about word boundaries
        CommandPrefixMatcher m = new CommandPrefixMatcher(List.of("give %player% cookie"));
        // "give Steve cookies" is NOT a prefix match of "give Steve cookie" + " "
        // (the s at the end means no trailing space after the prefix)
        assertFalse(m.isAllowed("give Steve cookies", "Steve"));
    }

    @Test
    void unrelatedCommandIsBlocked() {
        CommandPrefixMatcher m = new CommandPrefixMatcher(List.of("give %player% cookie"));
        assertFalse(m.isAllowed("op Steve", "Steve"));
    }

    @Test
    void multiplePrefixesAllRespected() {
        CommandPrefixMatcher m = new CommandPrefixMatcher(
                List.of("give %player% cookie", "effect give %player%"));
        assertTrue(m.isAllowed("give Steve cookie", "Steve"));
        assertTrue(m.isAllowed("effect give Steve speed 60 1", "Steve"));
        assertFalse(m.isAllowed("op Steve", "Steve"));
    }

    @Test
    void playerPlaceholderIsResolved() {
        CommandPrefixMatcher m = new CommandPrefixMatcher(List.of("give %player% cookie"));
        // If we don't resolve the placeholder, "give Steve cookie" wouldn't match
        // the literal "give %player% cookie" prefix.
        assertTrue(m.isAllowed("give Steve cookie", "Steve"));
    }

    @Test
    void caseInsensitive() {
        CommandPrefixMatcher m = new CommandPrefixMatcher(List.of("give %player% cookie"));
        assertTrue(m.isAllowed("GIVE STEVE COOKIE", "Steve"));
    }

    @Test
    void emptyCommandIsBlocked() {
        CommandPrefixMatcher m = new CommandPrefixMatcher(List.of("give"));
        assertFalse(m.isAllowed("", "Steve"));
        assertFalse(m.isAllowed(null, "Steve"));
        assertFalse(m.isAllowed("   ", "Steve"));
    }

    @Test
    void emptyPrefixListBlocksEverything() {
        CommandPrefixMatcher m = new CommandPrefixMatcher(List.of());
        assertFalse(m.isAllowed("give Steve cookie", "Steve"));
    }
}
