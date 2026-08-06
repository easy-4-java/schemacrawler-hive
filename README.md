# schemacrawler-hive

[English](./README.md) | [简体中文](./README.zh-CN.md)

[![Java](https://img.shields.io/badge/Java-21-orange)](https://github.com/easy-4-java/schemacrawler-hive) [![License](https://img.shields.io/badge/license-Apache%202.0-green)](./LICENSE)

## Table of Contents

- [1. Project Overview](#1-project-overview)
- [2. Features & Status](#2-features--status)
- [3. Requirements & Compatibility](#3-requirements--compatibility)
- [4. Architecture & Modules](#4-architecture--modules)
- [5. Installation](#5-installation)
- [6. Quick Start](#6-quick-start)
- [7. Configuration](#7-configuration)
- [8. Core Usage / API](#8-core-usage--api)
- [9. Testing & Build](#9-testing--build)
- [10. Versioning & Branches](#10-versioning--branches)
- [11. Contributing & License](#11-contributing--license)

## 1. Project Overview

**schemacrawler-hive** is a [SchemaCrawler](https://www.schemacrawler.com) database plug-in that registers
**Apache Hive** (HiveServer2) as a supported database type. It is discovered through the
`META-INF/services/schemacrawler.tools.databaseconnector.DatabaseConnector` service loader entry and makes
SchemaCrawler recognize `jdbc:hive2:*` URLs.

| Is                                                         | Is not                                   |
| :--------------------------------------------------------- | :--------------------------------------- |
| A SchemaCrawler `DatabaseConnector` plug-in for Hive       | A Hive client SDK                        |
| Auto-loads `org.apache.hive.jdbc.HiveDriver`               | A replacement for Beeline / HiveServer2 |
| Ships information-schema SQL views for Hive                | A full Hive metadata repository          |

Typical scenarios:

| Scenario                         | Description                                                |
| :------------------------------- | :--------------------------------------------------------- |
| Schema introspection             | Use SchemaCrawler commands (`-command=list`, `-command=schema`, ...) against Hive |
| Metadata export                  | Generate schema diagrams/text reports from Hive databases  |
| CI documentation                 | Keep Hive schema docs in sync with the warehouse           |

## 2. Features & Status

| Capability                                      | Status      | Notes                                                                                |
| :---------------------------------------------- | :---------- | :----------------------------------------------------------------------------------- |
| Database server type registration (`hive`)      | Implemented | `HiveDatabaseConnector`; SchemaCrawler reports it as "Apache Hive DataBase"          |
| JDBC URL recognition                            | Implemented | `supportsUrlPredicate()` matches `jdbc:hive2:.*`                                     |
| Driver loading                                  | Implemented | Loads `org.apache.hive.jdbc.HiveDriver` at connector construction                    |
| Information-schema SQL views                    | Implemented | `hive.information_schema/` resource folder (tables, views, routines, triggers, constraints, ...) |
| Connection defaults                             | Implemented | `schemacrawler-hive.config.properties`: host/port/database/url                      |
| Help command                                    | Implemented | `--server=hive` help text with host/port/database options                            |
| Unit tests                                      | Partial     | `TestBundledDistributions` asserts the registry knows `hive`; `HiveJdbcClient` is a runnable JDBC example |

Known limitation (carried over from the original README — **Assumption**: not re-verified against this
branch's driver):

> Hive's JDBC driver historically implements only part of `java.sql.DatabaseMetaData`. Standard
> SchemaCrawler commands that rely on `DatabaseMetaData` may fail with `Database access exception:
> Method not supported`. Verify against your Hive version before relying on full schema introspection.

## 3. Requirements & Compatibility

| Requirement | Version            |
| :---------- | :----------------- |
| JDK         | 8+                 |
| Maven       | 3.0+ (wrapper included) |
| SchemaCrawler | 16.7.2           |
| Hive JDBC   | 3.1.2 (`hive-jdbc`)|
| Hadoop      | 3.2.1 (`hadoop-common`, `provided`) |

Version lines of the easy4j project:

| Branch        | JDK  | Version pattern | Notes                       |
| :------------ | :--- | :-------------- | :-------------------------- |
| `feature/1.0.x` | 8    | `1.0.x.*`       | This README, current branch |
| `feature/2.0.x` | 17   | `2.0.x.*`       | JDK 17 line                 |
| `feature/3.0.x` | 21   | `3.0.x.*`       | JDK 21 line                 |

## 4. Architecture & Modules

```text
  SchemaCrawler (16.7.2)
        |
        v
  ServiceLoader -> HiveDatabaseConnector
        |
        v
  supportsUrlPredicate: jdbc:hive2:*
        |
        v
  HiveDriver (org.apache.hive.jdbc) -> HiveServer2
        |
        v
  /hive.information_schema SQL views + config properties
```

Single-module Maven project (`jar` packaging):

| Package                        | Responsibility                                  |
| :----------------------------- | :---------------------------------------------- |
| `schemacrawler.server.hive`    | `HiveDatabaseConnector` (the plug-in entry)     |
| `META-INF/services`            | Service registration for SchemaCrawler          |
| `resources/hive.information_schema` | Metadata SQL views for Hive                 |
| `resources/schemacrawler-hive.config.properties` | Connection defaults          |

## 5. Installation

Artifacts are published to the aliyun repository and GitHub Releases; they are **not** on Maven Central yet.

```xml
<dependency>
    <groupId>io.github.easy4j</groupId>
    <artifactId>schemacrawler-hive</artifactId>
    <version>3.0.x.x.20260630-SNAPSHOT</version>
</dependency>
```

```groovy
implementation 'io.github.easy4j:schemacrawler-hive:3.0.x.x.20260630-SNAPSHOT'
```

The plug-in must be on the same classpath as SchemaCrawler (SchemaCrawler discovers `DatabaseConnector`
implementations via `ServiceLoader`).

## 6. Quick Start

```bash
# List tables in the "default" database
./sc.sh -server=hive -database=default -host localhost -port 10000 \
        -infolevel=standard -command=list
```

or programmatically:

```java
DatabaseConnectorRegistry registry = DatabaseConnectorRegistry.getDatabaseConnectorRegistry();
System.out.println(registry.hasDatabaseSystemIdentifier("hive")); // true

// Connect via the connector's configuration (host/port/database -> url)
Connection connection = DriverManager.getConnection(
        "jdbc:hive2://localhost:10000/default", "user", "password");
```

Expected result: `hasDatabaseSystemIdentifier("hive")` returns `true`; standard introspection commands work
to the extent the Hive JDBC driver supports `DatabaseMetaData` (see the limitation note in Section 2).

## 7. Configuration

Defaults live in `schemacrawler-hive.config.properties`:

| Property   | Default       | Meaning                        |
| :--------- | :------------ | :----------------------------- |
| `host`     | `localhost`   | HiveServer2 host               |
| `port`     | `10000`       | HiveServer2 port               |
| `database` | `default`     | Database name                  |
| `url`      | `jdbc:hive2://${host}:${port}/${database}` | JDBC URL template |

Command-line options (`HiveDatabaseConnector.getHelpCommand()`): `-host` (default `localhost`), `-port`
(default `3306` in the help text), `-database`, `-user`, `-password`.

## 8. Core Usage / API

| Type                                       | Role                                                             |
| :----------------------------------------- | :--------------------------------------------------------------- |
| `schemacrawler.server.hive.HiveDatabaseConnector` | Registers server type `hive`, loads `HiveDriver`, provides help and URL matching |

The information-schema folder (`hive.information_schema`) supplies the SQL views SchemaCrawler uses to read
tables, columns, constraints, routines, triggers and sequences from the Hive catalog.

## 9. Testing & Build

```bash
./mvnw clean verify
```

- `TestBundledDistributions` verifies the plug-in is registered (`registry.hasDatabaseSystemIdentifier("hive")`).
- `HiveJdbcClient` (in `src/test`) is a runnable JDBC example against a live HiveServer2.
- JaCoCo is configured with a line-coverage rule of 90% (`haltOnFailure=false`).
- Note: `hive-jdbc` pulls a large dependency tree; `hadoop-common` is declared `provided` and must come from
  your Hadoop environment.

## 10. Versioning & Branches

| Branch        | JDK  | Version pattern | Maintenance                          |
| :------------ | :--- | :-------------- | :----------------------------------- |
| `feature/1.0.x` | 8    | `1.0.x.*`       | Current branch (Hive JDBC 3.1.2)     |
| `feature/2.0.x` | 17   | `2.0.x.*`       | JDK 17 line                          |
| `feature/3.0.x` | 21   | `3.0.x.*`       | JDK 21 line                          |

Artifacts are distributed via the aliyun Maven repository and GitHub Releases. Use the branch matching your
JDK baseline.

## 11. Contributing & License

Contributions are welcome — especially verification of `DatabaseMetaData` behavior against newer Hive
versions. Please open an issue before larger changes.

This project is licensed under the [Apache License, Version 2.0](http://www.apache.org/licenses/LICENSE-2.0).
