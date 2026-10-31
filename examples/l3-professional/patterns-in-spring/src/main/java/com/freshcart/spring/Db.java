package com.freshcart.spring;

import javax.sql.DataSource;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseBuilder;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseType;

/** An in-memory H2 database for the examples, created from a SQL script on the classpath. */
public final class Db {
    private Db() { }

    public static DataSource h2(String script) {
        return new EmbeddedDatabaseBuilder().setType(EmbeddedDatabaseType.H2).generateUniqueName(true).addScript(script).build();
    }
}
