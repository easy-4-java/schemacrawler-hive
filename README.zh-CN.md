# schemacrawler-hive

[English](./README.md) | [简体中文](./README.zh-CN.md)

[![Java](https://img.shields.io/badge/Java-17-orange)](https://github.com/easy-4-java/schemacrawler-hive) [![License](https://img.shields.io/badge/license-Apache%202.0-green)](./LICENSE)

schemacrawler-hive 是 SchemaCrawler 的数据库插件，将 Apache Hive（HiveServer2）注册为受支持的数据库类型。

## 目录

- [1. 项目概述](#1-项目概述)
- [2. 功能与状态](#2-功能与状态)
- [3. 环境要求与兼容性](#3-环境要求与兼容性)
- [4. 架构与模块](#4-架构与模块)
- [5. 安装](#5-安装)
- [6. 快速开始](#6-快速开始)
- [7. 配置](#7-配置)
- [8. 核心用法 / API](#8-核心用法--api)
- [9. 测试与构建](#9-测试与构建)
- [10. 版本线与分支](#10-版本线与分支)
- [11. 贡献与许可](#11-贡献与许可)

## 1. 项目概述

**schemacrawler-hive** 是 [SchemaCrawler](https://www.schemacrawler.com) 的数据库插件，将 **Apache Hive**（HiveServer2）注册为受支持的数据库类型。它通过 `META-INF/services/schemacrawler.tools.databaseconnector.DatabaseConnector` 服务加载机制被发现，使 SchemaCrawler 能够识别 `jdbc:hive2:*` URL。

| 是                                                         | 不是                                   |
| :--------------------------------------------------------- | :------------------------------------- |
| 面向 Hive 的 SchemaCrawler `DatabaseConnector` 插件        | Hive 客户端 SDK                        |
| 自动加载 `org.apache.hive.jdbc.HiveDriver`                 | Beeline / HiveServer2 的替代品         |
| 附带 Hive 的 information_schema SQL 视图                   | 完整的 Hive 元数据仓库                 |

典型场景：

| 场景             | 说明                                                    |
| :--------------- | :------------------------------------------------------ |
| 元数据探查       | 使用 SchemaCrawler 命令（`-command=list`、`-command=schema` 等）操作 Hive |
| 元数据导出       | 由 Hive 数据库生成模式图/文本报告                       |
| CI 文档同步      | 让 Hive 模式文档与数仓保持一致                          |

## 2. 功能与状态

| 能力                                            | 状态       | 说明                                                                                    |
| :---------------------------------------------- | :--------- | :-------------------------------------------------------------------------------------- |
| 数据库类型注册（`hive`）                        | 已实现     | `HiveDatabaseConnector`；SchemaCrawler 中显示为 "Apache Hive DataBase"                  |
| JDBC URL 识别                                   | 已实现     | `supportsUrlPredicate()` 匹配 `jdbc:hive2:.*`                                           |
| 驱动加载                                        | 已实现     | 连接器构造时加载 `org.apache.hive.jdbc.HiveDriver`                                      |
| information_schema SQL 视图                     | 已实现     | `hive.information_schema/` 资源目录（表、视图、例程、触发器、约束等）                    |
| 连接默认值                                      | 已实现     | `schemacrawler-hive.config.properties`：host/port/database/url                         |
| 帮助命令                                        | 已实现     | `--server=hive` 帮助文本，含 host/port/database 选项                                   |
| 单元测试                                        | 部分       | `TestBundledDistributions` 断言注册表包含 `hive`；`HiveJdbcClient` 为可运行 JDBC 示例    |

已知限制（沿袭自原 README——**假设**：未针对本分支驱动重新验证）：

> Hive 的 JDBC 驱动历史上只实现了部分 `java.sql.DatabaseMetaData`。依赖 `DatabaseMetaData` 的标准 SchemaCrawler 命令可能报 `Database access exception: Method not supported`。在依赖完整的模式探查能力之前，请先针对你的 Hive 版本验证。

## 3. 环境要求与兼容性

| 要求          | 版本              |
| :------------ | :---------------- |
| JDK           | 8+                |
| Maven         | 3.0+（已内置 wrapper） |
| SchemaCrawler | 16.7.2            |
| Hive JDBC     | 3.1.2（`hive-jdbc`）|
| Hadoop        | 3.2.1（`hadoop-common`，`provided`）|

easy4j 项目的版本线：

| 分支           | JDK  | 版本模式   | 说明                            |
| :------------- | :--- | :--------- | :------------------------------ |
| `feature/1.0.x` | 8    | `1.0.x.*`  | 本文档对应分支                   |
| `feature/2.0.x` | 17   | `2.0.x.*`  | JDK 17 版本线                   |
| `feature/3.0.x` | 21   | `3.0.x.*`  | JDK 21 版本线                   |

## 4. 架构与模块

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
  /hive.information_schema SQL 视图 + 配置属性
```

单模块 Maven 项目（`jar` 打包）：

| 包                                 | 职责                                          |
| :--------------------------------- | :-------------------------------------------- |
| `schemacrawler.server.hive`        | `HiveDatabaseConnector`（插件入口）           |
| `META-INF/services`                | SchemaCrawler 服务注册                        |
| `resources/hive.information_schema`| 面向 Hive 的元数据 SQL 视图                   |
| `resources/schemacrawler-hive.config.properties` | 连接默认值                     |

## 5. 安装

制品发布在阿里云私服与 GitHub Releases，**尚未发布到 Maven Central**。

```xml
<dependency>
    <groupId>io.github.easy4j</groupId>
    <artifactId>schemacrawler-hive</artifactId>
    <version>2.0.x.x.20260630-SNAPSHOT</version>
</dependency>
```

```groovy
implementation 'io.github.easy4j:schemacrawler-hive:2.0.x.x.20260630-SNAPSHOT'
```

插件必须与 SchemaCrawler 在同一 classpath 上（SchemaCrawler 通过 `ServiceLoader` 发现 `DatabaseConnector` 实现）。

## 6. 快速开始

```bash
# 列出 "default" 库中的表
./sc.sh -server=hive -database=default -host localhost -port 10000 \
        -infolevel=standard -command=list
```

或编程方式：

```java
DatabaseConnectorRegistry registry = DatabaseConnectorRegistry.getDatabaseConnectorRegistry();
System.out.println(registry.hasDatabaseSystemIdentifier("hive")); // true

// 通过连接器配置连接（host/port/database -> url）
Connection connection = DriverManager.getConnection(
        "jdbc:hive2://localhost:10000/default", "user", "password");
```

预期结果：`hasDatabaseSystemIdentifier("hive")` 返回 `true`；标准探查命令在 Hive JDBC 驱动支持 `DatabaseMetaData` 的范围内可用（见第 2 节限制说明）。

## 7. 配置

默认值位于 `schemacrawler-hive.config.properties`：

| 属性       | 默认值     | 说明                  |
| :--------- | :--------- | :-------------------- |
| `host`     | `localhost`| HiveServer2 主机       |
| `port`     | `10000`    | HiveServer2 端口       |
| `database` | `default`  | 数据库名               |
| `url`      | `jdbc:hive2://${host}:${port}/${database}` | JDBC URL 模板 |

命令行选项（`HiveDatabaseConnector.getHelpCommand()`）：`-host`（默认 `localhost`）、`-port`（帮助文本中默认 `3306`）、`-database`、`-user`、`-password`。

## 8. 核心用法 / API

| 类型                                               | 职责                                                             |
| :------------------------------------------------- | :--------------------------------------------------------------- |
| `schemacrawler.server.hive.HiveDatabaseConnector`  | 注册服务类型 `hive`、加载 `HiveDriver`、提供帮助与 URL 匹配      |

`hive.information_schema` 目录提供 SchemaCrawler 用于读取表、列、约束、例程、触发器和序列的 SQL 视图。

## 9. 测试与构建

```bash
./mvnw clean verify
```

- `TestBundledDistributions` 验证插件已注册（`registry.hasDatabaseSystemIdentifier("hive")`）。
- `HiveJdbcClient`（位于 `src/test`）是可运行的 JDBC 示例，需连接真实 HiveServer2。
- 已配置 JaCoCo 行覆盖率 90% 门禁（`haltOnFailure=false`）。
- 注意：`hive-jdbc` 依赖树较大；`hadoop-common` 声明为 `provided`，需由你的 Hadoop 环境提供。

## 10. 版本线与分支

| 分支           | JDK  | 版本模式   | 维护说明                              |
| :------------- | :--- | :--------- | :------------------------------------ |
| `feature/1.0.x` | 8    | `1.0.x.*`  | 当前分支（Hive JDBC 3.1.2）           |
| `feature/2.0.x` | 17   | `2.0.x.*`  | JDK 17 版本线                         |
| `feature/3.0.x` | 21   | `3.0.x.*`  | JDK 21 版本线                         |

制品通过阿里云 Maven 私服与 GitHub Releases 分发。请按 JDK 基线选择对应分支。

## 11. 贡献与许可

欢迎贡献——尤其是针对新版 Hive 的 `DatabaseMetaData` 行为验证。较大改动请先提交 issue 讨论。

本项目基于 [Apache License, Version 2.0](http://www.apache.org/licenses/LICENSE-2.0) 许可。
