package io.atlasforge.ledger;

import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;

/** An in-memory reference kernel; PostgreSQL and transport adapters follow in M1. */
public final class LedgerEngine {
    public record AccountKey(String tenant, String account) {}
    public record Account(AccountKey key, String currency, boolean allowNegative) {}
    public record Entry(String account, BigInteger minorUnits) {
        public Entry { Objects.requireNonNull(minorUnits); }
    }
    public record Posting(String tenant, String idempotencyKey, String currency, List<Entry> entries) {
        public Posting { entries = List.copyOf(entries); }
    }
    public record Receipt(long sequence, String tenant, String key, String currency,
                          List<Entry> entries, String payloadHash, String previousHash,
                          String hash, String reverses) {
        public Receipt { entries = List.copyOf(entries); }
    }
    private record TransactionKey(String tenant, String key) {}
    private final Map<AccountKey, Account> accounts = new HashMap<>();
    private final Map<AccountKey, BigInteger> balances = new HashMap<>();
    private final Map<TransactionKey, Receipt> transactions = new HashMap<>();
    private final Set<TransactionKey> reversed = new HashSet<>();
    private final List<Receipt> journal = new ArrayList<>();
    private final Runnable beforeCommit;
    private String head = "0".repeat(64);

    public LedgerEngine() { this(() -> {}); }
    public LedgerEngine(Runnable beforeCommit) { this.beforeCommit = Objects.requireNonNull(beforeCommit); }

    private static void identifier(String value) {
        if (value == null || !value.matches("[A-Za-z0-9_.-]{1,80}"))
            throw new IllegalArgumentException("Invalid identifier");
    }
    private static void currency(String value) {
        if (value == null || !value.matches("[A-Z]{3}")) throw new IllegalArgumentException("Invalid currency");
    }
    public synchronized void openAccount(String tenant, String account, String currency, boolean allowNegative) {
        identifier(tenant); identifier(account); currency(currency);
        var key = new AccountKey(tenant, account);
        var specification = new Account(key, currency, allowNegative);
        var existing = accounts.get(key);
        if (existing != null && !existing.equals(specification)) throw new IllegalStateException("Account conflict");
        accounts.putIfAbsent(key, specification); balances.putIfAbsent(key, BigInteger.ZERO);
    }
    public synchronized BigInteger balance(String tenant, String account) {
        var value = balances.get(new AccountKey(tenant, account));
        if (value == null) throw new IllegalArgumentException("Unknown account");
        return value;
    }
    public synchronized Receipt post(Posting posting) { return commit(posting, null); }

    private Receipt commit(Posting posting, String reverses) {
        Objects.requireNonNull(posting); identifier(posting.tenant()); identifier(posting.idempotencyKey());
        currency(posting.currency());
        var entries = new ArrayList<>(posting.entries());
        entries.sort(Comparator.comparing(Entry::account, Comparator.nullsFirst(Comparator.naturalOrder())));
        if (entries.size() < 2 || entries.size() > 1000) throw new IllegalArgumentException("Require 2..1000 entries");
        BigInteger sum = BigInteger.ZERO; var seen = new HashSet<String>();
        for (var entry : entries) {
            identifier(entry.account());
            if (!seen.add(entry.account()) || entry.minorUnits().signum() == 0)
                throw new IllegalArgumentException("Duplicate account or zero entry");
            if (entry.minorUnits().bitLength() > 127) throw new IllegalArgumentException("Amount exceeds 127-bit limit");
            sum = sum.add(entry.minorUnits());
        }
        if (sum.signum() != 0) throw new IllegalArgumentException("Unbalanced posting");
        String payload = posting.tenant() + "|" + posting.currency() + "|" + (reverses == null ? "" : reverses);
        for (var entry : entries) payload += "|" + entry.account() + ":" + entry.minorUnits();
        String fingerprint = digest(payload);
        var txKey = new TransactionKey(posting.tenant(), posting.idempotencyKey());
        var previous = transactions.get(txKey);
        if (previous != null) {
            if (!previous.payloadHash().equals(fingerprint)) throw new IllegalStateException("Idempotency payload conflict");
            return previous;
        }
        var proposed = new HashMap<AccountKey, BigInteger>();
        for (var entry : entries) {
            var key = new AccountKey(posting.tenant(), entry.account()); var account = accounts.get(key);
            if (account == null || !account.currency().equals(posting.currency()))
                throw new IllegalArgumentException("Unknown tenant account or currency mismatch");
            var amount = balances.get(key).add(entry.minorUnits());
            if (!account.allowNegative() && amount.signum() < 0) throw new IllegalStateException("Insufficient balance");
            proposed.put(key, amount);
        }
        long sequence = Math.addExact(journal.size(), 1L);
        String hash = digest(head + "|" + sequence + "|" + txKey.tenant() + "|" + txKey.key() + "|" + fingerprint);
        var receipt = new Receipt(sequence, posting.tenant(), posting.idempotencyKey(), posting.currency(), entries,
                                  fingerprint, head, hash, reverses);
        // The hook models failure before the atomic state transition, without partial balance changes.
        beforeCommit.run();
        balances.putAll(proposed); transactions.put(txKey, receipt); journal.add(receipt); head = hash;
        return receipt;
    }
    public synchronized Receipt reverse(String tenant, String originalKey, String reversalKey) {
        identifier(tenant); identifier(originalKey); identifier(reversalKey);
        var originalTx = new TransactionKey(tenant, originalKey); var original = transactions.get(originalTx);
        if (original == null || original.reverses() != null) throw new IllegalArgumentException("Unknown original posting");
        var reversalTx = new TransactionKey(tenant, reversalKey);
        var previous = transactions.get(reversalTx);
        if (previous != null && originalKey.equals(previous.reverses())) return previous;
        if (reversed.contains(originalTx)) throw new IllegalStateException("Already reversed");
        var entries = original.entries().stream().map(e -> new Entry(e.account(), e.minorUnits().negate())).toList();
        var receipt = commit(new Posting(tenant, reversalKey, original.currency(), entries), originalKey);
        reversed.add(originalTx); return receipt;
    }
    public synchronized List<Receipt> journal() { return List.copyOf(journal); }
    public synchronized String auditHead() { return head; }
    private static String digest(String text) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8))); }
        catch (Exception error) { throw new IllegalStateException(error); }
    }
}
