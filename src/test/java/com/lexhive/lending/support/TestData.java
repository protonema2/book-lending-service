package com.lexhive.lending.support;

import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/** Unique, valid test values so tests sharing a database never collide. */
public final class TestData {

    private TestData() {
    }

    /** A random ISBN-13 with a correct check digit (passes {@code @ISBN}). */
    public static String isbn13() {
        var digits = new StringBuilder("979");
        for (int i = 0; i < 9; i++) {
            digits.append(ThreadLocalRandom.current().nextInt(10));
        }
        int sum = 0;
        for (int i = 0; i < 12; i++) {
            int digit = digits.charAt(i) - '0';
            sum += (i % 2 == 0) ? digit : digit * 3;
        }
        return digits.append((10 - sum % 10) % 10).toString();
    }

    public static String email() {
        return "member-" + UUID.randomUUID() + "@example.com";
    }
}
