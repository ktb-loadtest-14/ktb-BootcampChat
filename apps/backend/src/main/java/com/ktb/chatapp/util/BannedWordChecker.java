package com.ktb.chatapp.util;

import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;
import org.ahocorasick.trie.Trie;
import org.springframework.util.Assert;

public class BannedWordChecker {

    private final Trie bannedWordTrie;
    
    public BannedWordChecker(Set<String> bannedWords) {
        Set<String> normalizedBannedWords =
                bannedWords.stream()
                        .filter(word -> word != null && !word.isBlank())
                        .map(word -> word.toLowerCase(Locale.ROOT))
                        .collect(Collectors.toUnmodifiableSet());
        Assert.notEmpty(normalizedBannedWords, "Banned words set must not be empty");

        this.bannedWordTrie =
                Trie.builder()
                        .addKeywords(normalizedBannedWords)
                        .build();
    }
    
    public boolean containsBannedWord(String message) {
        if (message == null || message.isBlank()) {
            return false;
        }
        
        String normalizedMessage = message.toLowerCase(Locale.ROOT);
        return bannedWordTrie.containsMatch(normalizedMessage);
    }
}
