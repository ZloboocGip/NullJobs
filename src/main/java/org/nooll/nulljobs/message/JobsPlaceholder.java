package org.nooll.nulljobs.message;

public final class JobsPlaceholder {

    private final String key;
    private final String value;

    private JobsPlaceholder(String key, String value) {
        this.key = key;
        this.value = value;
    }

    public static JobsPlaceholder of(String key, String value) {
        return new JobsPlaceholder(key, value);
    }

    public String key() {
        return key;
    }

    public String value() {
        return value;
    }
}