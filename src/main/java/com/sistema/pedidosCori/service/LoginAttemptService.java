package com.sistema.pedidosCori.service;

import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Locale;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Service
public class LoginAttemptService {
    private record Key(String username, String ip) { }
    private static class Entry {
        int failures;
        Instant lastAttempt;
        Instant blockedUntil;
    }

    private final Map<Key, Entry> entries = new HashMap<>();
    private final int maxAttempts;
    private final Duration blockDuration;
    private Instant lastCleanup = Instant.EPOCH;

    public LoginAttemptService(@Value("${app.security.login.max-attempts:5}") int maxAttempts,
            @Value("${app.security.login.block-minutes:15}") long blockMinutes) {
        if (maxAttempts < 1 || blockMinutes < 1) throw new IllegalArgumentException("Configuración de bloqueo de login inválida");
        this.maxAttempts = maxAttempts;
        this.blockDuration = Duration.ofMinutes(blockMinutes);
    }

    public synchronized void failed(String username, String ip) {
        Instant now = Instant.now();
        cleanup(now);
        Key key = key(username, ip);
        Entry entry = entries.computeIfAbsent(key, unused -> new Entry());
        if (entry.lastAttempt != null && !now.isBefore(entry.lastAttempt.plus(blockDuration))) entry.failures = 0;
        entry.lastAttempt = now;
        if (++entry.failures >= maxAttempts) entry.blockedUntil = now.plus(blockDuration);
    }

    public synchronized long remainingMinutes(String username, String ip) {
        Instant now = Instant.now();
        cleanup(now);
        Entry entry = entries.get(key(username, ip));
        if (entry == null || entry.blockedUntil == null || !now.isBefore(entry.blockedUntil)) return 0;
        return Math.max(1, (Duration.between(now, entry.blockedUntil).toSeconds() + 59) / 60);
    }

    public synchronized void reset(String username, String ip) {
        entries.remove(key(username, ip));
    }

    @Scheduled(fixedDelay = 60000)
    public synchronized void removeExpired() {
        cleanup(Instant.now());
    }

    private Key key(String username, String ip) {
        return new Key(username == null ? "" : username.trim().toLowerCase(Locale.ROOT), ip == null ? "" : ip);
    }

    private void cleanup(Instant now) {
        if (now.isBefore(lastCleanup.plusSeconds(60))) return;
        Iterator<Entry> it = entries.values().iterator();
        while (it.hasNext()) {
            Entry entry = it.next();
            if (entry.lastAttempt == null || !now.isBefore(entry.lastAttempt.plus(blockDuration))) it.remove();
        }
        lastCleanup = now;
    }
}
