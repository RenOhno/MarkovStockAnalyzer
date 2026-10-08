# Persistence profiles

The default profile starts the existing in-memory API without a database. The
`mysql` profile selects JPA adapters for the existing repository interfaces and
enables Flyway. Compose selects `mysql`; services and HTTP contracts are shared.

Set `DB_URL`, `DB_USERNAME`, and `DB_PASSWORD` in the process environment, then run:

```powershell
.\mvnw.cmd spring-boot:run "-Dspring-boot.run.profiles=mysql"
```

MySQL sessions, Connector/J, and Hibernate use UTC. Hibernate validates mappings;
Flyway owns schema creation. V1 is the executable DDL authority, and V2 registers
only the same two development stocks as the in-memory repository. No price data
is seeded.

JSON columns use `@JdbcTypeCode(SqlTypes.JSON)` with raw JSON strings. The database
validates JSON syntax; adapters reuse business validation and deliberately
serialize stored snapshots. HTTP DTOs are not entities.

Parent aggregates expose persist cascades for dataset/prices, analysis/nine
transitions, and backtest/predictions. Cascade delete and orphan removal are not
enabled; foreign keys protect saved history.

`MySqlPersistenceTests` uses an ephemeral MySQL 8.4 Testcontainer. It runs with
ordinary `.\mvnw.cmd verify`, together with `MySqlRepositoryAdapterTests`.
Docker is required. The foundation class retains its original
`disabledWithoutDocker=true` behavior; the adapter class requires Docker and
fails if it is unavailable. CI additionally rejects every skipped report, so a
missing Docker environment cannot satisfy the database gate. Migration, mapping,
constraint, JSON, and transaction failures fail the tests. No H2 is used.

To rerun DB tests after enabling Docker:

```powershell
.\mvnw.cmd test -Dtest=MySqlPersistenceTests
```

CI rejects any skipped Java test. Separate transactional writers atomically save
dataset/prices, analysis/nine transitions and backtest/predictions. Python calls
occur before those transactions.
