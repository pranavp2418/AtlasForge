package io.atlasforge.ledger;
import java.sql.*;
import java.util.*;
import java.math.BigInteger;
public final class PostgresRestartProbe {
 public static void main(String[] args)throws Exception {
  var db=new PostgresLedger(()->DriverManager.getConnection(System.getenv("ATLASFORGE_JDBC_URL"),System.getenv().getOrDefault("PGUSER","postgres"),System.getenv().getOrDefault("PGPASSWORD","")));
  if(args[0].equals("write")) {
   db.migrate();db.openAccount(args[1],"cash","USD",false);db.openAccount(args[1],"equity","USD",true);
   db.post(new LedgerEngine.Posting(args[1],"restart-fund","USD",List.of(new LedgerEngine.Entry("cash",BigInteger.valueOf(4242)),new LedgerEngine.Entry("equity",BigInteger.valueOf(-4242)))));
  } else if(!db.balance(args[1],"cash").equals(BigInteger.valueOf(4242))||db.journal(args[1]).size()!=1)throw new AssertionError("Cross-process recovery mismatch");
  System.out.println("Ledger process "+args[0]+" verified");
 }
}
