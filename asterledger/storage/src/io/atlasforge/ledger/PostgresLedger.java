package io.atlasforge.ledger;

import java.math.BigInteger;
import java.sql.*;
import java.util.*;

/** Local M1 persistence slice. Transport authentication and pooling remain separate gates. */
public final class PostgresLedger {
    @FunctionalInterface public interface Connections { Connection open() throws SQLException; }
    private final Connections connections;
    private final Runnable beforeCommit;
    public PostgresLedger(Connections connections) { this(connections, () -> {}); }
    public PostgresLedger(Connections connections, Runnable beforeCommit) {
        this.connections = Objects.requireNonNull(connections); this.beforeCommit = Objects.requireNonNull(beforeCommit);
    }
    private static void id(String s) {
        if (s == null || !s.matches("[A-Za-z0-9_.-]{1,80}")) throw new IllegalArgumentException("Invalid identifier");
    }
    public void migrate() throws SQLException {
        try (var c=connections.open()) {
            c.setAutoCommit(false);
            try (var s=c.createStatement()) {
                // Transaction-scoped migration fence, distinct from tenant posting locks.
                s.execute("SELECT pg_advisory_xact_lock(174151026)");
                s.execute("CREATE SCHEMA IF NOT EXISTS asterledger");
                s.execute("CREATE TABLE IF NOT EXISTS asterledger.schema_version(version integer PRIMARY KEY CHECK(version=1))");
                s.execute("INSERT INTO asterledger.schema_version VALUES(1) ON CONFLICT DO NOTHING");
                try(var versions=s.executeQuery("SELECT version FROM asterledger.schema_version")){if(!versions.next()||versions.getInt(1)!=1||versions.next())throw new IllegalStateException("Unsupported schema version");}
                s.execute("CREATE TABLE IF NOT EXISTS asterledger.tenants(tenant text PRIMARY KEY, revision bigint NOT NULL DEFAULT 0 CHECK(revision>=0))");
                s.execute("CREATE TABLE IF NOT EXISTS asterledger.accounts(tenant text NOT NULL REFERENCES asterledger.tenants(tenant), account text NOT NULL, currency text NOT NULL CHECK(currency ~ '^[A-Z]{3}$'), allow_negative boolean NOT NULL, PRIMARY KEY(tenant,account))");
                s.execute("CREATE TABLE IF NOT EXISTS asterledger.postings(tenant text NOT NULL REFERENCES asterledger.tenants(tenant), sequence bigint NOT NULL CHECK(sequence>0), key text NOT NULL, currency text NOT NULL, entries text NOT NULL, hash text NOT NULL CHECK(length(hash)=64), reverses text, PRIMARY KEY(tenant,sequence), UNIQUE(tenant,key))");
            }
            c.commit();
        }
    }
    private void lockTenant(Connection c,String tenant,boolean create) throws SQLException {
        id(tenant);
        if (create) try(var s=c.prepareStatement("INSERT INTO asterledger.tenants(tenant) VALUES(?) ON CONFLICT DO NOTHING")) {s.setString(1,tenant);s.executeUpdate();}
        try(var s=c.prepareStatement("SELECT revision FROM asterledger.tenants WHERE tenant=? FOR UPDATE")) {
            s.setString(1,tenant);try(var r=s.executeQuery()){if(!r.next())throw new IllegalArgumentException("Unknown tenant");}
        }
    }
    public void openAccount(String tenant,String account,String currency,boolean allowNegative) throws SQLException {
        id(tenant);id(account);
        // Validate against the existing independent kernel before SQL execution.
        new LedgerEngine().openAccount(tenant,account,currency,allowNegative);
        try(var c=connections.open()) {
            c.setAutoCommit(false);
            try {
                lockTenant(c,tenant,true);
                try(var s=c.prepareStatement("INSERT INTO asterledger.accounts VALUES(?,?,?,?) ON CONFLICT DO NOTHING")) {
                    s.setString(1,tenant);s.setString(2,account);s.setString(3,currency);s.setBoolean(4,allowNegative);s.executeUpdate();
                }
                try(var s=c.prepareStatement("SELECT currency,allow_negative FROM asterledger.accounts WHERE tenant=? AND account=?")) {
                    s.setString(1,tenant);s.setString(2,account);try(var r=s.executeQuery()) {
                        if(!r.next()||!r.getString(1).equals(currency)||r.getBoolean(2)!=allowNegative)throw new IllegalStateException("Account conflict");
                    }
                }
                c.commit();
            } catch(SQLException|RuntimeException error){c.rollback();throw error;}
        }
    }
    private LedgerEngine restore(Connection c,String tenant) throws SQLException {
        var kernel=new LedgerEngine();
        try(var s=c.prepareStatement("SELECT account,currency,allow_negative FROM asterledger.accounts WHERE tenant=? ORDER BY account")) {
            s.setString(1,tenant);try(var r=s.executeQuery()){while(r.next())kernel.openAccount(tenant,r.getString(1),r.getString(2),r.getBoolean(3));}
        }
        try(var s=c.prepareStatement("SELECT sequence,key,currency,entries,hash,reverses FROM asterledger.postings WHERE tenant=? ORDER BY sequence")) {
            s.setString(1,tenant);try(var r=s.executeQuery()) {
                while(r.next()) {
                    var entries=decode(r.getString(4));String reverses=r.getString(6);
                    var receipt=reverses==null?kernel.post(new LedgerEngine.Posting(tenant,r.getString(2),r.getString(3),entries)):kernel.reverse(tenant,reverses,r.getString(2));
                    if(receipt.sequence()!=r.getLong(1)||!receipt.hash().equals(r.getString(5))||!receipt.entries().equals(entries)||!receipt.currency().equals(r.getString(3)))throw new IllegalStateException("Persistent journal corruption");
                }
            }
        }
        try(var s=c.prepareStatement("SELECT revision FROM asterledger.tenants WHERE tenant=?")){s.setString(1,tenant);try(var r=s.executeQuery()){if(!r.next()||r.getLong(1)!=kernel.journal().size())throw new IllegalStateException("Tenant revision does not match journal");}}
        return kernel;
    }
    private static String encode(List<LedgerEngine.Entry> entries) {
        var join=new StringJoiner("\n");for(var e:entries)join.add(e.account()+":"+e.minorUnits());return join.toString();
    }
    private static List<LedgerEngine.Entry> decode(String body) {
        var result=new ArrayList<LedgerEngine.Entry>();
        for(String line:body.split("\n",-1)) {var pair=line.split(":",-1);if(pair.length!=2)throw new IllegalStateException("Invalid persisted entry");result.add(new LedgerEngine.Entry(pair[0],new BigInteger(pair[1])));}
        return result;
    }
    private LedgerEngine.Receipt write(String tenant,LedgerEngine.Posting posting,String original,String reversal) throws SQLException {
        id(tenant);
        try(var c=connections.open()) {
            c.setAutoCommit(false);
            try {
                lockTenant(c,tenant,false);var kernel=restore(c,tenant);int size=kernel.journal().size();
                var receipt=posting!=null?kernel.post(posting):kernel.reverse(tenant,original,reversal);
                if(kernel.journal().size()!=size) {
                    try(var s=c.prepareStatement("INSERT INTO asterledger.postings VALUES(?,?,?,?,?,?,?)")) {
                        s.setString(1,tenant);s.setLong(2,receipt.sequence());s.setString(3,receipt.key());s.setString(4,receipt.currency());s.setString(5,encode(receipt.entries()));s.setString(6,receipt.hash());s.setString(7,receipt.reverses());s.executeUpdate();
                    }
                    try(var s=c.prepareStatement("UPDATE asterledger.tenants SET revision=? WHERE tenant=?")){s.setLong(1,receipt.sequence());s.setString(2,tenant);s.executeUpdate();}
                    beforeCommit.run();
                }
                c.commit();return receipt;
            } catch(SQLException|RuntimeException error){c.rollback();throw error;}
        }
    }
    public LedgerEngine.Receipt post(LedgerEngine.Posting posting) throws SQLException {Objects.requireNonNull(posting);return write(posting.tenant(),posting,null,null);}
    public LedgerEngine.Receipt reverse(String tenant,String original,String reversal) throws SQLException {return write(tenant,null,original,reversal);}
    public BigInteger balance(String tenant,String account) throws SQLException {
        id(tenant);id(account);
        try(var c=connections.open()) {
            c.setAutoCommit(false);c.setTransactionIsolation(Connection.TRANSACTION_REPEATABLE_READ);
            try {var value=restore(c,tenant).balance(tenant,account);c.commit();return value;}
            catch(SQLException|RuntimeException e){c.rollback();throw e;}
        }
    }
    public List<LedgerEngine.Receipt> journal(String tenant) throws SQLException {
        id(tenant);
        try(var c=connections.open()) {
            c.setAutoCommit(false);c.setTransactionIsolation(Connection.TRANSACTION_REPEATABLE_READ);
            try {var value=restore(c,tenant).journal();c.commit();return value;}
            catch(SQLException|RuntimeException e){c.rollback();throw e;}
        }
    }
}
