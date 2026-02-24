package com.qh.ai_agent.exception;

public class BannedWordException extends RuntimeException {

    private final String bannedWord;

    public BannedWordException(String message, String bannedWord) {
        super(message);
        this.bannedWord = bannedWord;
    }

    public BannedWordException(String message, String bannedWord, Throwable cause) {
        super(message, cause);
        this.bannedWord = bannedWord;
    }

    public String getBannedWord() {
        return bannedWord;
    }
}
