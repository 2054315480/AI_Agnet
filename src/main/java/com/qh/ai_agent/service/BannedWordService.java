package com.qh.ai_agent.service;

import com.qh.ai_agent.exception.BannedWordException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Pattern;

@Slf4j
@Service
public class BannedWordService {

    private final Set<String> bannedWords = new HashSet<>();

    private Pattern bannedWordPattern;

    public BannedWordService(@Value("${ai.banned-words:}") String bannedWordsConfig) {
        loadBannedWords(bannedWordsConfig);
    }

    private void loadBannedWords(String config) {
        if (config != null && !config.trim().isEmpty()) {
            String[] words = config.split("[,;\\s]+");
            bannedWords.addAll(Arrays.stream(words).filter(w -> !w.trim().isEmpty()).toList());
            updatePattern();
            log.info("Loaded {} banned words from configuration", bannedWords.size());
        }
    }

    private void updatePattern() {
        if (!bannedWords.isEmpty()) {
            String pattern = String.join("|", bannedWords);
            bannedWordPattern = Pattern.compile("(" + pattern + ")", Pattern.CASE_INSENSITIVE);
        }
    }

    public boolean containsBannedWord(String text) {
        if (text == null || text.isEmpty() || bannedWords.isEmpty()) {
            return false;
        }
        return bannedWordPattern.matcher(text).find();
    }

    public void checkBannedWord(String text) {
        if (containsBannedWord(text)) {
            var matcher = bannedWordPattern.matcher(text);
            if (matcher.find()) {
                String foundWord = matcher.group(1);
                throw new BannedWordException("Text contains banned word: " + foundWord, foundWord);
            }
        }
    }

    public String filterBannedWords(String text) {
        if (text == null || text.isEmpty() || bannedWords.isEmpty()) {
            return text;
        }
        return bannedWordPattern.matcher(text).replaceAll("***");
    }

    public Set<String> getBannedWords() {
        return new HashSet<>(bannedWords);
    }

    public void addBannedWord(String word) {
        if (word != null && !word.trim().isEmpty()) {
            bannedWords.add(word);
            updatePattern();
        }
    }

    public void removeBannedWord(String word) {
        bannedWords.remove(word);
        updatePattern();
    }
}
