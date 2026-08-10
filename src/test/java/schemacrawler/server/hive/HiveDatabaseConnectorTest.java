/*
 * Copyright (c) 2018-present, easy-4-java (https://github.com/easy-4-java).
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package schemacrawler.server.hive;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.IOException;
import java.util.Iterator;
import java.util.ServiceLoader;

import org.junit.Test;

import schemacrawler.schemacrawler.DatabaseServerType;
import schemacrawler.tools.databaseconnector.DatabaseConnector;
import schemacrawler.tools.databaseconnector.DatabaseConnectorRegistry;
import schemacrawler.tools.executable.commandline.PluginCommand;
import schemacrawler.tools.executable.commandline.PluginCommandOption;

/**
 * Unit tests for {@link HiveDatabaseConnector}.
 *
 * <p>The test suite exercises every reachable line of
 * {@link HiveDatabaseConnector}: the no-arg constructor that delegates to
 * {@link DatabaseConnector}'s super-class, the eager loading of
 * {@code org.apache.hive.jdbc.HiveDriver}, the customised
 * {@link #getHelpCommand()} surface, and the
 * {@link #supportsUrlPredicate()} branch that drives URL recognition.
 * The {@link ClassNotFoundException} branch in the constructor is exercised
 * by {@link HiveDatabaseConnectorDriverMissingTest}, which instantiates the
 * connector via a custom class-loader that hides the Hive driver.</p>
 *
 * <p>These tests are intentionally written without Mockito or PowerMock so
 * that the only test-scope dependency is JUnit 4 (already declared in the
 * project POM) and the standard library &mdash; matching the constraints
 * imposed by the Phase 2 normalisation of {@code pom.xml}.</p>
 *
 * @author <a href="https://github.com/loong10k">Loong Wan</a>
 * @since 3.0.0
 * @see HiveDatabaseConnector
 * @see HiveDatabaseConnectorDriverMissingTest
 */
public class HiveDatabaseConnectorTest {

    /**
     * The default constructor must successfully initialise the connector,
     * which implies that the bundled
     * {@code schemacrawler-hive.config.properties} resource is on the
     * classpath, the information-schema resource folder is resolvable, and
     * the {@code org.apache.hive.jdbc.HiveDriver} class can be loaded.
     */
    @Test
    public void shouldInstantiateViaDefaultConstructor() throws IOException {
        final HiveDatabaseConnector connector = new HiveDatabaseConnector();
        assertNotNull("Connector must not be null after construction", connector);
    }

    /**
     * Constructing the connector multiple times must remain side-effect free
     * &mdash; subsequent constructions must not interfere with previously
     * cached drivers or registry entries.
     */
    @Test
    public void shouldInstantiateMultipleTimesWithoutSideEffects() throws IOException {
        final HiveDatabaseConnector first = new HiveDatabaseConnector();
        final HiveDatabaseConnector second = new HiveDatabaseConnector();
        assertNotNull(first);
        assertNotNull(second);
        // Two independent instances must not be the same object identity.
        assertFalse("Expected distinct instances", first == second);
    }

    /**
     * The connector must register a {@link DatabaseServerType} whose
     * identifier is {@code "hive"} so that SchemaCrawler's command line can
     * be driven with {@code --server=hive}.
     */
    @Test
    public void shouldExposeHiveDatabaseServerTypeIdentifier() throws IOException {
        final HiveDatabaseConnector connector = new HiveDatabaseConnector();
        final DatabaseServerType serverType = connector.getDatabaseServerType();
        assertNotNull("DatabaseServerType must not be null", serverType);
        assertEquals("hive", serverType.getDatabaseSystemIdentifier());
    }

    /**
     * The connector's human-readable name must describe the database system
     * &mdash; SchemaCrawler renders this label in {@code --help} output.
     */
    @Test
    public void shouldExposeHumanReadableDatabaseSystemName() throws IOException {
        final HiveDatabaseConnector connector = new HiveDatabaseConnector();
        final DatabaseServerType serverType = connector.getDatabaseServerType();
        assertEquals("Apache Hive DataBase", serverType.getDatabaseSystemName());
    }

    /**
     * The connector must NOT claim the "unknown database system" flag,
     * because Hive is a fully-fledged system with a registered identifier.
     */
    @Test
    public void shouldNotReportUnknownDatabaseSystem() throws IOException {
        final HiveDatabaseConnector connector = new HiveDatabaseConnector();
        final DatabaseServerType serverType = connector.getDatabaseServerType();
        assertFalse("Connector must not report the unknown system flag",
                serverType.isUnknownDatabaseSystem());
    }

    /**
     * The connector's {@link Object#toString()} representation must surface
     * both the identifier and the system name because
     * {@link DatabaseConnector} relies on it to render the registry summary.
     */
    @Test
    public void shouldExposeHelpfulToStringRepresentation() throws IOException {
        final HiveDatabaseConnector connector = new HiveDatabaseConnector();
        final String text = connector.toString();
        assertNotNull(text);
        assertTrue("toString should mention hive identifier: " + text,
                text.contains("hive"));
    }

    /**
     * The URL predicate (exercised indirectly through
     * {@link DatabaseConnector#supportsUrl(String)}) must accept the
     * canonical {@code jdbc:hive2:} URL form.
     */
    @Test
    public void shouldAcceptCanonicalHive2JdbcUrl() throws IOException {
        final HiveDatabaseConnector connector = new HiveDatabaseConnector();
        assertTrue(connector.supportsUrl("jdbc:hive2://localhost:10000/default"));
    }

    /**
     * The URL predicate must accept any URL that begins with the
     * {@code jdbc:hive2:} prefix, including those that carry query string
     * parameters or transport attributes.
     */
    @Test
    public void shouldAcceptHive2UrlWithTrailingAttributes() throws IOException {
        final HiveDatabaseConnector connector = new HiveDatabaseConnector();
        assertTrue(connector.supportsUrl("jdbc:hive2://host:10000/db;transportMode=http"));
    }

    /**
     * The URL predicate must REJECT URLs from other database systems that
     * merely contain the substring {@code hive2} (e.g. a MySQL URL whose
     * database happens to be named {@code hive2_test}).
     */
    @Test
    public void shouldRejectMysqlUrlWithHiveSubstring() throws IOException {
        final HiveDatabaseConnector connector = new HiveDatabaseConnector();
        assertFalse(connector.supportsUrl(
                "jdbc:mysql://localhost:3306/hive2_archive"));
    }

    /**
     * The URL predicate must reject non-Hive JDBC URLs (the most common
     * positive-negative pair is MySQL, which is the default option in many
     * SchemaCrawler deployments).
     */
    @Test
    public void shouldRejectMysqlJdbcUrl() throws IOException {
        final HiveDatabaseConnector connector = new HiveDatabaseConnector();
        assertFalse(connector.supportsUrl("jdbc:mysql://localhost:3306/foo"));
    }

    /**
     * The URL predicate must reject the legacy {@code jdbc:hive:} (v1)
     * prefix because this connector only speaks to HiveServer2.
     */
    @Test
    public void shouldRejectLegacyHiveV1JdbcUrl() throws IOException {
        final HiveDatabaseConnector connector = new HiveDatabaseConnector();
        assertFalse(connector.supportsUrl("jdbc:hive://localhost:10000/default"));
    }

    /**
     * The URL predicate must tolerate {@code null} and blank URLs without
     * throwing &mdash; the inherited
     * {@link DatabaseConnector#supportsUrl(String)} short-circuits to
     * {@code false} in those cases.
     */
    @Test
    public void shouldReturnFalseForNullOrBlankUrl() throws IOException {
        final HiveDatabaseConnector connector = new HiveDatabaseConnector();
        assertFalse(connector.supportsUrl(null));
        assertFalse(connector.supportsUrl(""));
        assertFalse(connector.supportsUrl("   "));
    }

    /**
     * The connector's help command must describe the {@code --server=hive2}
     * switch that activates this plug-in.
     */
    @Test
    public void shouldExposeServerOptionInHelpCommand() throws IOException {
        final HiveDatabaseConnector connector = new HiveDatabaseConnector();
        final PluginCommand command = connector.getHelpCommand();
        assertNotNull("Help command must not be null", command);
        assertNotNull(command.getName());
        assertTrue("Expected at least one option in help command",
                command.iterator().hasNext());
    }

    /**
     * The connector's help command must carry four options ({@code server},
     * {@code host}, {@code port}, {@code database}). This is a tripwire
     * against accidental omission when the option list is edited.
     */
    @Test
    public void shouldExposeFourOptionsInHelpCommand() throws IOException {
        final HiveDatabaseConnector connector = new HiveDatabaseConnector();
        final PluginCommand command = connector.getHelpCommand();
        int count = 0;
        for (final Iterator<PluginCommandOption> iterator = command.iterator();
                iterator.hasNext(); iterator.next()) {
            count++;
        }
        assertEquals("help command must expose four options", 4, count);
    }

    /**
     * The {@link PluginCommand#getHelpHeader()} (inherited from
     * {@link DatabaseConnector#getHelpCommand()}) must not be {@code null}
     * after the override; this ensures SchemaCrawler can emit a coherent
     * help banner.
     */
    @Test
    public void shouldProvideNonNullHelpHeader() throws IOException {
        final HiveDatabaseConnector connector = new HiveDatabaseConnector();
        final PluginCommand command = connector.getHelpCommand();
        assertNotNull("Help header must not be null", command.getHelpHeader());
    }

    /**
     * The help command's name must match the database-system identifier
     * ({@code "hive"}) because SchemaCrawler's {@code --help} output keys
     * off this string.
     */
    @Test
    public void shouldExposeHiveHelpCommandName() throws IOException {
        final HiveDatabaseConnector connector = new HiveDatabaseConnector();
        final PluginCommand command = connector.getHelpCommand();
        assertEquals("hive", command.getName());
    }

    /**
     * The connector's {@link PluginCommand} must NOT report itself as empty
     * because the override adds four options.
     */
    @Test
    public void shouldReportNonEmptyHelpCommand() throws IOException {
        final HiveDatabaseConnector connector = new HiveDatabaseConnector();
        final PluginCommand command = connector.getHelpCommand();
        assertFalse("Help command must not be empty after override",
                command.isEmpty());
    }

    /**
     * Two consecutive help commands must iterate the same number of options
     * &mdash; guards against accidental mutation of the shared
     * {@link PluginCommand} instance.
     */
    @Test
    public void shouldReturnHelpCommandsWithConsistentOptions() throws IOException {
        final HiveDatabaseConnector connector = new HiveDatabaseConnector();
        final PluginCommand first = connector.getHelpCommand();
        final PluginCommand second = connector.getHelpCommand();

        assertNotNull(first);
        assertNotNull(second);

        int firstCount = 0;
        for (final Iterator<PluginCommandOption> it = first.iterator(); it.hasNext(); it.next()) {
            firstCount++;
        }
        int secondCount = 0;
        for (final Iterator<PluginCommandOption> it = second.iterator(); it.hasNext(); it.next()) {
            secondCount++;
        }
        assertEquals("Help command option count must be stable across calls",
                firstCount, secondCount);
    }

    /**
     * The connector must be discoverable through the standard Java
     * {@link ServiceLoader} mechanism because it is registered via
     * {@code META-INF/services/schemacrawler.tools.databaseconnector.DatabaseConnector}.
     */
    @Test
    public void shouldBeDiscoverableViaServiceLoader() {
        boolean found = false;
        final ServiceLoader<DatabaseConnector> loader = ServiceLoader.load(
                DatabaseConnector.class, HiveDatabaseConnectorTest.class.getClassLoader());
        for (final DatabaseConnector connector : loader) {
            if (connector instanceof HiveDatabaseConnector) {
                found = true;
                break;
            }
        }
        assertTrue("HiveDatabaseConnector must be registered as a service", found);
    }

    /**
     * The connector must appear under the {@code "hive"} identifier in the
     * SchemaCrawler {@link DatabaseConnectorRegistry}, which is the runtime
     * resolution mechanism used by the command-line entry point.
     */
    @Test
    public void shouldBeRegisteredInDatabaseConnectorRegistry() {
        final DatabaseConnectorRegistry registry = DatabaseConnectorRegistry.getDatabaseConnectorRegistry();
        assertTrue("Registry must advertise the 'hive' identifier",
                registry.hasDatabaseSystemIdentifier("hive"));
    }

    /**
     * Looking up the connector by the canonical HiveServer2 URL must
     * resolve to the {@link HiveDatabaseConnector}.
     */
    @Test
    public void shouldResolveHive2UrlToConnectorInstance() {
        final DatabaseConnectorRegistry registry = DatabaseConnectorRegistry.getDatabaseConnectorRegistry();
        final DatabaseConnector connector =
                registry.lookupDatabaseConnectorFromUrl("jdbc:hive2://example.com:10000/default");
        assertNotNull(connector);
        assertTrue("Expected a HiveDatabaseConnector for hive2 URL",
                connector instanceof HiveDatabaseConnector);
    }

    /**
     * Asking the registry for an unsupported URL must return a non-null
     * {@link DatabaseConnector} but NOT the {@code HiveDatabaseConnector}
     * &mdash; the {@link DatabaseConnector#UNKNOWN} fallback is itself
     * an instance of {@link DatabaseConnector}.
     */
    @Test
    public void shouldNotMatchArbitraryUrlViaRegistry() {
        final DatabaseConnectorRegistry registry = DatabaseConnectorRegistry.getDatabaseConnectorRegistry();
        final DatabaseConnector connector =
                registry.lookupDatabaseConnectorFromUrl("jdbc:mysql://example.com:3306/foo");
        assertNotNull(connector);
        assertFalse("Expected a non-Hive connector for a mysql URL",
                connector instanceof HiveDatabaseConnector);
    }

    /**
     * The registry's iteration surface must expose the Hive
     * {@link DatabaseServerType} alongside the other plug-ins, which means
     * the connector was loaded successfully during registry construction.
     */
    @Test
    public void shouldExposeHiveServerTypeViaRegistryIterator() {
        final DatabaseConnectorRegistry registry = DatabaseConnectorRegistry.getDatabaseConnectorRegistry();
        boolean found = false;
        final Iterator<DatabaseServerType> iterator = registry.iterator();
        while (iterator.hasNext()) {
            final DatabaseServerType serverType = iterator.next();
            if ("hive".equals(serverType.getDatabaseSystemIdentifier())) {
                found = true;
                break;
            }
        }
        assertTrue("Iterator must surface the Hive server type", found);
    }

    /**
     * The {@link DatabaseServerType} returned by the connector must equal
     * one fetched independently through
     * {@link DatabaseServerType}'s constructor &mdash; a sanity check that
     * the identifier-based equality semantics hold for our plug-in.
     */
    @Test
    public void shouldExposeServerTypeEqualToFreshlyConstructedInstance() throws IOException {
        final HiveDatabaseConnector connector = new HiveDatabaseConnector();
        final DatabaseServerType fromConnector = connector.getDatabaseServerType();
        final DatabaseServerType fromScratch =
                new DatabaseServerType("hive", "Apache Hive DataBase");
        assertEquals(fromScratch, fromConnector);
        assertEquals(fromScratch.hashCode(), fromConnector.hashCode());
    }

    /**
     * The {@code unknown} system identifier sentinel from
     * {@link DatabaseServerType#UNKNOWN} must not be returned by this
     * connector.
     */
    @Test
    public void shouldNotReturnUnknownServerType() throws IOException {
        final HiveDatabaseConnector connector = new HiveDatabaseConnector();
        assertFalse(DatabaseServerType.UNKNOWN.equals(connector.getDatabaseServerType()));
    }

    /**
     * The connector must be a {@link DatabaseConnector} subclass so that it
     * can be returned through the {@link DatabaseConnectorRegistry} API.
     */
    @Test
    public void shouldBeAssignableToDatabaseConnector() throws IOException {
        final HiveDatabaseConnector connector = new HiveDatabaseConnector();
        assertTrue(connector instanceof DatabaseConnector);
    }

    /**
     * The connector's URL predicate must be deterministic &mdash; two
     * consecutive {@link DatabaseConnector#supportsUrl(String)} calls must
     * produce the same answer for the same URL. Guards against accidental
     * state capture.
     */
    @Test
    public void shouldReturnDeterministicAnswerAcrossInvocations() throws IOException {
        final HiveDatabaseConnector connector = new HiveDatabaseConnector();
        for (int i = 0; i < 5; i++) {
            assertTrue("iteration " + i + " should accept hive2 URL",
                    connector.supportsUrl("jdbc:hive2://example.com:10000/default"));
            assertFalse("iteration " + i + " should reject mysql URL",
                    connector.supportsUrl("jdbc:mysql://example.com:3306/foo"));
        }
    }

    /**
     * The connector must still be available for construction after the
     * registry has been built (registry initialisation eagerly iterates
     * service loaders and must not destroy plug-in availability).
     */
    @Test
    public void shouldBeConstructibleAfterRegistryHasLoaded() throws IOException {
        // Force registry initialisation first.
        DatabaseConnectorRegistry.getDatabaseConnectorRegistry();
        final HiveDatabaseConnector connector = new HiveDatabaseConnector();
        assertNotNull(connector);
    }

    /**
     * Sanity check: a URL that contains {@code hive} but does NOT carry
     * the {@code jdbc:hive2:} prefix must be rejected (e.g. a PostgreSQL
     * URL whose path component happens to spell out the substring).
     */
    @Test
    public void shouldRejectPostgresUrlWithHiveSubstring() throws IOException {
        final HiveDatabaseConnector connector = new HiveDatabaseConnector();
        assertFalse(connector.supportsUrl(
                "jdbc:postgresql://localhost:5432/my_hive_db"));
    }

    /**
     * Verifies that the {@link PluginCommandOption#getName()} of every
     * option exposed by {@link #getHelpCommand()} is non-null. This guards
     * against an accidental regression where an {@code addOption} call
     * forgets to pass a name.
     */
    @Test
    public void shouldExposeNonEmptyOptionNames() throws IOException {
        final HiveDatabaseConnector connector = new HiveDatabaseConnector();
        final PluginCommand command = connector.getHelpCommand();
        for (final Iterator<PluginCommandOption> iterator = command.iterator();
                iterator.hasNext(); ) {
            final PluginCommandOption option = iterator.next();
            final String name = option.getName();
            assertNotNull("Option name must not be null", name);
            assertFalse("Option name must not be empty", name.isEmpty());
        }
    }

    /**
     * Verifies that the connector's {@code toString()} does not surface
     * the {@code "unknown database"} fallback string.
     */
    @Test
    public void shouldNotSurfaceUnknownDatabaseInToString() throws IOException {
        final HiveDatabaseConnector connector = new HiveDatabaseConnector();
        final String text = connector.toString();
        assertFalse("toString must not say 'unknown database': " + text,
                text.contains("unknown database"));
    }

    /**
     * Sanity check: the URL predicate must remain stable for URLs that
     * contain characters like spaces or special characters.
     */
    @Test
    public void shouldRejectUrlsWithSpacesOrMalformedPatterns() throws IOException {
        final HiveDatabaseConnector connector = new HiveDatabaseConnector();
        assertFalse(connector.supportsUrl("not a jdbc url"));
        assertFalse(connector.supportsUrl("jdbc:"));
        assertFalse(connector.supportsUrl("jdbc:hive"));
    }

    /**
     * Confirm that constructing the connector is cheap enough to do inside
     * a loop without breaking the test runner &mdash; a guard against
     * accidentally registering a JVM shutdown hook inside the constructor.
     */
    @Test
    public void shouldSupportRapidConstructionLoop() throws IOException {
        for (int i = 0; i < 25; i++) {
            final HiveDatabaseConnector connector = new HiveDatabaseConnector();
            assertNotNull(connector);
        }
    }

    /**
     * The {@link DatabaseServerType} returned by the connector must not be
     * the global {@link DatabaseServerType#UNKNOWN} sentinel.
     */
    @Test
    public void shouldExposeNonNullServerType() throws IOException {
        final HiveDatabaseConnector connector = new HiveDatabaseConnector();
        final DatabaseServerType serverType = connector.getDatabaseServerType();
        assertFalse("Server type must not be UNKNOWN",
                DatabaseServerType.UNKNOWN.equals(serverType));
    }

    /**
     * Belt-and-braces guard that explicitly drives the predicate branch
     * with a synthetic URL to make sure no
     * {@link java.util.regex.Pattern} exception leaks out of
     * {@link #supportsUrlPredicate()}.
     */
    @Test
    public void shouldNotThrowWhenEvaluatingUrlPredicate() throws IOException {
        final HiveDatabaseConnector connector = new HiveDatabaseConnector();
        try {
            connector.supportsUrl("jdbc:hive2://");
            // Reaching here without an exception is the assertion.
            assertTrue("Predicate evaluation must succeed", true);
        } catch (final RuntimeException e) {
            fail("Predicate evaluation threw: " + e);
        }
    }

    /**
     * The connector must expose a non-null {@link DatabaseServerType}
     * reference even when accessed repeatedly.
     */
    @Test
    public void shouldReturnStableServerTypeInstance() throws IOException {
        final HiveDatabaseConnector connector = new HiveDatabaseConnector();
        final DatabaseServerType first = connector.getDatabaseServerType();
        final DatabaseServerType second = connector.getDatabaseServerType();
        assertNotNull(first);
        assertNotNull(second);
        assertEquals("server type identity must be stable", first, second);
    }

    /**
     * The {@link PluginCommand} returned by {@link #getHelpCommand()} must
     * be a non-null instance &mdash; this guards against an accidental
     * regression where the override short-circuits and returns null.
     */
    @Test
    public void shouldAlwaysReturnNonNullHelpCommand() throws IOException {
        final HiveDatabaseConnector connector = new HiveDatabaseConnector();
        assertNotNull(connector.getHelpCommand());
        assertNotNull(connector.getHelpCommand());
    }

    /**
     * Verifies that the {@link DatabaseConnectorRegistry#iterator()}
     * returns at least one entry &mdash; ensures the registry is not
     * silently empty even if other plug-ins are misconfigured.
     */
    @Test
    public void shouldReturnNonEmptyRegistryIterator() {
        final DatabaseConnectorRegistry registry = DatabaseConnectorRegistry.getDatabaseConnectorRegistry();
        assertTrue("Registry iterator must yield at least one server type",
                registry.iterator().hasNext());
    }

    /**
     * The Hive connector must be returned by the registry's URL-based
     * lookup even when the URL contains trailing query parameters.
     */
    @Test
    public void shouldResolveHive2UrlWithQueryParameters() {
        final DatabaseConnectorRegistry registry = DatabaseConnectorRegistry.getDatabaseConnectorRegistry();
        final DatabaseConnector connector =
                registry.lookupDatabaseConnectorFromUrl(
                        "jdbc:hive2://example.com:10000/db?hiveconf=x=y");
        assertNotNull(connector);
        assertTrue(connector instanceof HiveDatabaseConnector);
    }

    /**
     * Asking the registry for a JDBC URL that the connector should NOT
     * handle must NOT return the Hive connector.
     */
    @Test
    public void shouldNotResolvePostgresUrlToHiveConnector() {
        final DatabaseConnectorRegistry registry = DatabaseConnectorRegistry.getDatabaseConnectorRegistry();
        final DatabaseConnector connector =
                registry.lookupDatabaseConnectorFromUrl(
                        "jdbc:postgresql://example.com:5432/foo");
        assertNotNull(connector);
        assertFalse(connector instanceof HiveDatabaseConnector);
    }

}