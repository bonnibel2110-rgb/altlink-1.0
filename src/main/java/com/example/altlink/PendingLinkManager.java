package com.example.altlink;

import java.security.SecureRandom;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Holds short-lived, in-memory pending link requests waiting for a 6-digit
 * confirmation code to be entered by the alt account.
 */
public final class PendingLinkManager {

    /** How long a generated code remains valid, in milliseconds. */
    private static final long CODE_EXPIRY_MILLIS = 5 * 60 * 1000L; // 5 minutes

    private final SecureRandom random = new SecureRandom();

    /**
     * Pending requests keyed by the alt player's UUID (the player who must confirm).
     */
    private final Map<UUID, PendingLink> pendingByAltUuid = new ConcurrentHashMap<>();

    public record PendingLink(String code, UUID mainUuid, String mainName,
                               UUID altUuid, String altName, long createdAt) {

        public boolean isExpired() {
            return System.currentTimeMillis() - createdAt > CODE_EXPIRY_MILLIS;
        }
    }

    /**
     * Generates a new 6-digit code and registers a pending link for the given alt player.
     * Any previous pending link for that alt is overwritten.
     */
    public String createPending(UUID mainUuid, String mainName, UUID altUuid, String altName) {
        String code = generateCode();
        pendingByAltUuid.put(altUuid, new PendingLink(code, mainUuid, mainName, altUuid, altName, System.currentTimeMillis()));
        return code;
    }

    /**
     * Attempts to confirm a pending link for the given alt player using the supplied code.
     *
     * @return the matching PendingLink if the code is correct and not expired, otherwise empty.
     */
    public java.util.Optional<PendingLink> confirm(UUID altUuid, String code) {
        PendingLink pending = pendingByAltUuid.get(altUuid);
        if (pending == null) {
            return java.util.Optional.empty();
        }
        if (pending.isExpired()) {
            pendingByAltUuid.remove(altUuid);
            return java.util.Optional.empty();
        }
        if (!pending.code().equalsIgnoreCase(code)) {
            return java.util.Optional.empty();
        }
        pendingByAltUuid.remove(altUuid);
        return java.util.Optional.of(pending);
    }

    /**
     * Removes any pending link request for the given alt player without checking the code.
     */
    public void clearPending(UUID altUuid) {
        pendingByAltUuid.remove(altUuid);
    }

    private String generateCode() {
        int number = 100000 + random.nextInt(900000); // 100000-999999 inclusive
        return Integer.toString(number);
    }
}
