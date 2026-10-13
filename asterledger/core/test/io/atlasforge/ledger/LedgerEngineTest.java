package io.atlasforge.ledger;

import java.math.BigInteger;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

public final class LedgerEngineTest {
    private static int checks;
    private static void check(boolean value) { checks++; if (!value) throw new AssertionError("Check " + checks); }
    private static void rejects(Runnable operation) {
        checks++; try { operation.run(); } catch (IllegalArgumentException | IllegalStateException expected) { return; }
        throw new AssertionError("Expected rejection " + checks);
    }
    private static LedgerEngine.Posting payment(String tenant, String key, long amount) {
        return new LedgerEngine.Posting(tenant, key, "USD", List.of(
            new LedgerEngine.Entry("clearing", BigInteger.valueOf(-amount)),
            new LedgerEngine.Entry("wallet", BigInteger.valueOf(amount))));
    }
    public static void main(String[] args) throws Exception {
        var engine = new LedgerEngine();
        for (var tenant : List.of("a", "b")) {
            engine.openAccount(tenant, "clearing", "USD", true); engine.openAccount(tenant, "wallet", "USD", false);
        }
        var first = engine.post(payment("a", "pay", 500));
        check(engine.balance("a", "wallet").equals(BigInteger.valueOf(500)));
        check(engine.balance("b", "wallet").equals(BigInteger.ZERO));
        var reversedOrder = new ArrayList<>(payment("a", "pay", 500).entries()); Collections.reverse(reversedOrder);
        check(engine.post(new LedgerEngine.Posting("a", "pay", "USD", reversedOrder)).equals(first));
        rejects(() -> engine.post(payment("a", "pay", 501)));
        rejects(() -> engine.post(payment("a", "overspend", -501)));
        rejects(() -> engine.post(new LedgerEngine.Posting("a", "bad", "USD", List.of(
            new LedgerEngine.Entry("wallet", BigInteger.ONE),new LedgerEngine.Entry("clearing", BigInteger.TWO)))));
        rejects(() -> engine.post(new LedgerEngine.Posting("a", "bad", "EUR", payment("a", "x", 1).entries())));
        rejects(() -> engine.post(payment("missing", "x", 1)));
        check(engine.journal().size() == 1);
        var reversal = engine.reverse("a", "pay", "refund");
        check(engine.balance("a", "wallet").signum() == 0);
        check(engine.reverse("a", "pay", "refund").equals(reversal));
        rejects(() -> engine.reverse("a", "pay", "another-refund"));
        check(reversal.previousHash().equals(first.hash())); check(reversal.hash().equals(engine.auditHead()));
        var fail = new AtomicBoolean(true); var faulted = new LedgerEngine(() -> { if (fail.get()) throw new IllegalStateException("injected"); });
        faulted.openAccount("a", "clearing", "USD", true); faulted.openAccount("a", "wallet", "USD", false);
        rejects(() -> faulted.post(payment("a", "retry", 12)));
        check(faulted.journal().isEmpty() && faulted.balance("a", "wallet").signum() == 0);
        fail.set(false); check(faulted.post(payment("a", "retry", 12)).sequence() == 1);
        var pool = Executors.newFixedThreadPool(8); var tasks = new ArrayList<Callable<LedgerEngine.Receipt>>();
        for (int i = 0; i < 80; i++) tasks.add(() -> engine.post(payment("a", "concurrent", 10)));
        var receipts = pool.invokeAll(tasks); pool.shutdown();
        for (var result : receipts) check(result.get().equals(receipts.get(0).get()));
        check(engine.balance("a", "wallet").equals(BigInteger.TEN));
        var random = new Random(413); BigInteger oracle = BigInteger.TEN;
        for (int i = 0; i < 1000; i++) {
            long amount = random.nextInt(10000) + 1; engine.post(payment("a", "oracle-" + i, amount));
            oracle = oracle.add(BigInteger.valueOf(amount)); check(engine.balance("a", "wallet").equals(oracle));
            check(engine.balance("a", "wallet").add(engine.balance("a", "clearing")).signum() == 0);
        }
        System.out.println("AsterLedger: " + checks + " assertions passed; 80 concurrent replays; 1000 balance-oracle steps.");
    }
}
