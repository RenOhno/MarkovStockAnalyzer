# Persistence foundation (STEP 8A)

The default profile starts the existing in-memory API without a database. The
`mysql` profile enables the separate JPA repositories and Flyway infrastructure;
API services still use their existing in-memory repositories until STEP 8B.

Set `DB_URL`, `DB_USERNAME`, and `DB_PASSWORD` in the process environment, then run:

```powershell
.\mvnw.cmd spring-boot:run "-Dspring-boot.run.profiles=mysql"
```

MySQL sessions, Connector/J, and Hibernate use UTC. Hibernate validates mappings;
Flyway owns schema creation. V1 is the executable DDL authority, and V2 registers
only the same two development stocks as the in-memory repository. No price data
is seeded.

JSON columns use `@JdbcTypeCode(SqlTypes.JSON)` with raw JSON strings. The database
validates JSON syntax; the next-step adapter must reuse the existing business
validation and serialize JSON deliberately. HTTP DTOs are not entities.

Parent aggregates expose persist cascades for dataset/prices, analysis/nine
transitions, and backtest/predictions. Cascade delete and orphan removal are not
enabled; foreign keys protect saved history.

`MySqlPersistenceTests` uses an ephemeral MySQL 8.4 Testcontainer. It runs with
ordinary `.\mvnw.cmd test`; if Docker is unavailable, JUnit reports these tests as
skipped via Testcontainers' `disabledWithoutDocker` condition. With Docker
available, migration, mapping, constraint, JSON, and transaction failures fail
the tests. No H2 substitution is used.

To rerun DB tests after enabling Docker:

```powershell
.\mvnw.cmd test -Dtest=MySqlPersistenceTests
```

A build with DB tests skipped confirms Java/unit-test readiness, not successful
MySQL verification. Run the real DB tests before integrating adapters in STEP 8B.
