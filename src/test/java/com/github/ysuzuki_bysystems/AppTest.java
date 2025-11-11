package com.github.ysuzuki_bysystems;

import java.io.File;
import java.lang.ProcessBuilder.Redirect;
import java.net.ConnectException;
import java.sql.Connection;
import java.sql.Driver;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;
import java.util.Random;

import junit.framework.TestCase;

/**
 * Unit test for simple App.
 */
public class AppTest extends TestCase {
    Process proc;

    Connection connection;

    @Override
    protected void setUp() throws Exception {
        String[] command = new String[] {
                "deno",
                "run",
                "--allow-net",
                "--allow-read",
                "npm:@electric-sql/pglite-socket@0.0.19",
                "-p5432",
                "-h127.0.0.1"
        };

        File devnull = new File("/dev/null");
        ProcessBuilder builder = new ProcessBuilder(command)
            .redirectInput(devnull)
            .redirectOutput(devnull)
            .redirectError(Redirect.INHERIT);
        Map<String, String> environment = builder.environment();
        environment.clear();
        environment.put("HOME", System.getenv("HOME"));
        environment.put("PATH", System.getenv("PATH"));
        proc = builder.start();

        try {
            String url = "jdbc:postgresql://localhost:5432/db";
            Properties info = new Properties();
            info.setProperty("sslmode", "disable");
            info.setProperty("password", "p");
            connection = connect(url, info);
        } catch (Exception e) {
            proc.destroy();
            throw e;
        }
    }

    @Override
    protected void tearDown() throws Exception {
        Optional<Exception> suppressed = Optional.empty();
        try {
            connection.close();
        } catch (Exception e) {
            suppressed = Optional.of(e);
        }

        proc.destroy();

        if (suppressed.isPresent()) {
            throw suppressed.get();
        }
    }

    public void test() throws Exception {
        try (PreparedStatement statement = connection.prepareStatement("CREATE TABLE test(val int not null)")) {
            int c = statement.executeUpdate();
            assertEquals(0, c);
        }

        try (PreparedStatement statement = connection.prepareStatement("INSERT INTO test VALUES (?)")) {
            for (int i = 0; i < 10; i++) {
                statement.setInt(1, i);
                int c = statement.executeUpdate();
                assertEquals(1, c);
            }
        }

        int total = 0;
        try (PreparedStatement statement = connection.prepareStatement("SELECT val FROM test");
                ResultSet results = statement.executeQuery()) {
            while (results.next()) {
                total += results.getInt(1);
            }
        }
        assertEquals(45, total);
    }

    // helper

    static Connection connect(String url, Properties info)
            throws SQLException, InterruptedException {
        Random rand = new Random();

        Driver driver = DriverManager.getDriver(url);
        for (int i = 0; i < 10; i++) {
            try {
                return driver.connect(url, info);
            } catch (Exception e) {
                if (e instanceof SQLException
                        && e.getCause() != null
                        && e.getCause() instanceof ConnectException) {
                    // Exponential Backoff style
                    Thread.sleep((i * 100L) + Math.abs(rand.nextLong() % 100L));
                    continue;
                }

                throw e;
            }
        }

        throw new RuntimeException("FAILED TO CONNECT");
    }
}
