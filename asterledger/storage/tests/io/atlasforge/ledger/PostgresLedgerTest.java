package io.atlasforge.ledger;
import java.math.BigInteger;
import java.sql.*;
import java.util.*;
import java.util.concurrent.*;
public final class PostgresLedgerTest {
 static int assertions=0;
 static void check(boolean ok){assertions++;if(!ok)throw new AssertionError("Check "+assertions);}
 static LedgerEngine.Posting posting(String tenant,String key,long amount){return new LedgerEngine.Posting(tenant,key,"USD",List.of(new LedgerEngine.Entry("cash",BigInteger.valueOf(amount)),new LedgerEngine.Entry("equity",BigInteger.valueOf(-amount))));}
 static void reject(Runnable action){try{action.run();throw new AssertionError("Expected rejection");}catch(IllegalArgumentException|IllegalStateException expected){assertions++;}}
 public static void main(String[] args)throws Exception {
  String url=System.getenv("ATLASFORGE_JDBC_URL");if(url==null)throw new IllegalStateException("ATLASFORGE_JDBC_URL required");
  var connections=(PostgresLedger.Connections)()->DriverManager.getConnection(url,System.getenv().getOrDefault("PGUSER","postgres"),System.getenv().getOrDefault("PGPASSWORD",""));
  var store=new PostgresLedger(connections);store.migrate();store.migrate();
  String tenant="test-"+UUID.randomUUID();
  for(String t:List.of(tenant,tenant+"-other")){store.openAccount(t,"cash","USD",false);store.openAccount(t,"equity","USD",true);}
  var initial=posting(tenant,"fund",1000);var receipt=store.post(initial);check(store.post(initial).equals(receipt));
  check(new PostgresLedger(connections).balance(tenant,"cash").equals(BigInteger.valueOf(1000)));
  var pool=Executors.newFixedThreadPool(8);var tasks=new ArrayList<Callable<LedgerEngine.Receipt>>();
  for(int i=0;i<32;i++)tasks.add(()->new PostgresLedger(connections).post(posting(tenant,"race",500)));
  var futures=pool.invokeAll(tasks);for(var f:futures)check(f.get().sequence()==2);pool.shutdown();
  check(store.balance(tenant,"cash").equals(BigInteger.valueOf(1500)));check(store.journal(tenant).size()==2);
  check(store.balance(tenant+"-other","cash").equals(BigInteger.ZERO));
  try{store.post(posting(tenant,"race",501));throw new AssertionError();}catch(IllegalStateException expected){assertions++;}
  int before=store.journal(tenant).size();
  try{new PostgresLedger(connections,()->{throw new IllegalStateException("Injected precommit failure");}).post(posting(tenant,"failure",7));throw new AssertionError();}catch(IllegalStateException expected){assertions++;}
  check(store.journal(tenant).size()==before);check(store.balance(tenant,"cash").equals(BigInteger.valueOf(1500)));
  try{store.post(posting(tenant,"overdraft",-1501));throw new AssertionError();}catch(IllegalStateException expected){assertions++;}
  check(store.balance(tenant,"cash").equals(BigInteger.valueOf(1500)));
  var rev=store.reverse(tenant,"race","reverse-race");check(store.reverse(tenant,"race","reverse-race").equals(rev));check(store.balance(tenant,"cash").equals(BigInteger.valueOf(1000)));
  try{store.reverse(tenant,"race","second-reversal");throw new AssertionError();}catch(IllegalStateException expected){assertions++;}
  // Independent integer oracle over 100 postings, read through a fresh adapter each time.
  long balance=1000;Random rng=new Random(151026);
  for(int i=0;i<100;i++){long delta=rng.nextInt(21)-10;if(delta==0)delta=1;balance+=delta;store.post(posting(tenant,"oracle-"+i,delta));check(new PostgresLedger(connections).balance(tenant,"cash").equals(BigInteger.valueOf(balance)));}
  System.out.println("PostgreSQL ledger slice passed: "+assertions+" checks; 32 concurrent identical posts; 100 independent balance steps; durable replay/reversal/rollback/tenant isolation.");
 }
}
