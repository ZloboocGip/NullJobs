package org.nooll.nulljobs.util;

public enum JobRewardMode {
    ITEMS,
    MONEY,
    BOTH;

    public static JobRewardMode from(String input) {
        if (input != null && input.equalsIgnoreCase("money")) {
            return MONEY;
        }

        if (input != null && input.equalsIgnoreCase("both")) {
            return BOTH;
        }

        return ITEMS;
    }
}