package io.github.michelbr84.flapforge.core;

import java.nio.charset.StandardCharsets;

/**
 * Small numeric helpers used by the pure packages.
 *
 * <p>Only functions with bit-exact, platform-independent results are offered here so that
 * simulations stay deterministic across operating systems and JDKs.
 */
public final class MathUtil {

    private static final long FNV64_OFFSET = 0xcbf29ce484222325L;
    private static final long FNV64_PRIME = 0x100000001b3L;

    private MathUtil() {
    }

    /**
     * Clamps {@code value} into {@code [min, max]}.
     *
     * @param value the value to clamp
     * @param min the inclusive lower bound
     * @param max the inclusive upper bound
     * @return the clamped value
     */
    public static double clamp(double value, double min, double max) {
        return value < min ? min : (value > max ? max : value);
    }

    /**
     * Clamps {@code value} into {@code [min, max]}.
     *
     * @param value the value to clamp
     * @param min the inclusive lower bound
     * @param max the inclusive upper bound
     * @return the clamped value
     */
    public static int clamp(int value, int min, int max) {
        return value < min ? min : (value > max ? max : value);
    }

    /**
     * Clamps {@code value} into {@code [min, max]}.
     *
     * @param value the value to clamp
     * @param min the inclusive lower bound
     * @param max the inclusive upper bound
     * @return the clamped value
     */
    public static long clamp(long value, long min, long max) {
        return value < min ? min : (value > max ? max : value);
    }

    /**
     * Linear interpolation between {@code a} and {@code b}.
     *
     * @param a the value at {@code t = 0}
     * @param b the value at {@code t = 1}
     * @param t the blend factor (not clamped)
     * @return {@code a + (b - a) * t}
     */
    public static double lerp(double a, double b, double t) {
        return a + (b - a) * t;
    }

    /**
     * 64-bit FNV-1a hash of the UTF-8 encoding of {@code text}.
     *
     * @param text the text to hash
     * @return the hash
     */
    public static long fnv1a64(String text) {
        return fnv1a64(text.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * 64-bit FNV-1a hash of raw bytes.
     *
     * @param bytes the bytes to hash
     * @return the hash
     */
    public static long fnv1a64(byte[] bytes) {
        long hash = FNV64_OFFSET;
        for (byte b : bytes) {
            hash ^= b & 0xffL;
            hash *= FNV64_PRIME;
        }
        return hash;
    }

    /**
     * Folds a 64-bit value into an existing FNV-1a hash (used for per-tick state hashes).
     *
     * @param hash the running hash
     * @param value the value to fold
     * @return the updated hash
     */
    public static long fold(long hash, long value) {
        long h = hash;
        for (int i = 0; i < 8; i++) {
            h ^= (value >>> (i * 8)) & 0xffL;
            h *= FNV64_PRIME;
        }
        return h;
    }
}
