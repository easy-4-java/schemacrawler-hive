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

import java.io.IOException;
import java.util.function.Predicate;
import java.util.regex.Pattern;

import schemacrawler.schemacrawler.DatabaseServerType;
import schemacrawler.tools.databaseconnector.DatabaseConnector;
import schemacrawler.tools.executable.commandline.PluginCommand;
import schemacrawler.tools.iosource.ClasspathInputResource;

/**
 * SchemaCrawler {@link DatabaseConnector} plug-in for Apache Hive (HiveServer2).
 *
 * <p>The plug-in teaches SchemaCrawler how to introspect the metadata of an
 * Apache Hive deployment that is reachable over the {@code jdbc:hive2://}
 * protocol. It performs three jobs:</p>
 * <ol>
 *   <li>registers a {@link DatabaseServerType} whose system identifier is
 *       {@code "hive"}, which is the value passed to SchemaCrawler's
 *       {@code --server} command-line option;</li>
 *   <li>loads the bundled information-schema view SQL resources that
 *       ship under {@code /hive.information_schema} on the classpath,
 *       resolving column comments, table comments, and other vendor-specific
 *       metadata that the generic SchemaCrawler engine cannot derive on its
 *       own;</li>
 *   <li>forces the Hive JDBC driver class ({@code org.apache.hive.jdbc.HiveDriver})
 *       to be loaded eagerly so that the {@link java.sql.DriverManager}
 *       sees it before the first connection attempt &mdash; this avoids the
 *       "no suitable driver" failure that some JVM driver-loading orderings
 *       trigger.</li>
 * </ol>
 *
 * <p>This class is declared {@code final} because the contract is fixed:
 * subclasses would only need to override {@link #getHelpCommand()} or
 * {@link #supportsUrlPredicate()}, and that is already exposed via the
 * SchemaCrawler command-line. Extending it would also break the Java
 * {@link java.util.ServiceLoader} registration performed against
 * {@code META-INF/services/schemacrawler.tools.databaseconnector.DatabaseConnector}.</p>
 *
 * @author <a href="https://github.com/loong10k">Loong Wan</a>
 * @since 3.0.0
 * @see DatabaseConnector
 * @see DatabaseServerType
 * @see PluginCommand
 * @see <a href="https://cwiki.apache.org/confluence/display/Hive/Setting+Up+HiveServer2">HiveServer2 documentation</a>
 */
public final class HiveDatabaseConnector extends DatabaseConnector {

    /**
     * Compiled regular expression that matches any JDBC URL beginning with
     * the {@code jdbc:hive2:} prefix &mdash; the standard prefix used by
     * Apache Hive's HiveServer2 transport. The expression is anchored at the
     * start so that URLs that merely contain {@code jdbc:hive2} as a path
     * component (for instance some MySQL URL with a Hive-named database)
     * are not falsely matched.
     */
    private static final String HIVE2_URL_PATTERN = "jdbc:hive2:.*";

    /**
     * Fully-qualified class name of the HiveServer2 JDBC driver. The constant
     * is kept here (rather than inlined in the constructor) so that the
     * driver-loading branch is greppable from a single location and so that
     * downstream tooling can reference the same string when validating
     * classpaths.
     */
    private static final String HIVE_DRIVER_CLASS_NAME = "org.apache.hive.jdbc.HiveDriver";

    /**
     * Human-readable prefix written to {@link RuntimeException} messages
     * when the Hive driver cannot be located on the classpath. Tests and
     * operators rely on this prefix to diagnose mis-deployed artifacts.
     */
    private static final String DRIVER_LOAD_FAILURE_PREFIX = "Could not load Hive JDBC driver";

    /**
     * Builds a new connector, registers the Hive {@link DatabaseServerType},
     * wires the bundled information-schema resource bundle and the bundled
     * {@code schemacrawler-hive.config.properties}, and pre-loads the
     * HiveServer2 JDBC driver class.
     *
     * <p>The constructor has the side-effect of forcing the
     * {@code org.apache.hive.jdbc.HiveDriver} class to load via
     * {@link Class#forName(String)}. The load is performed eagerly so that
     * {@link java.sql.DriverManager#registerDriver(java.sql.Driver)} side
     * effects run before SchemaCrawler attempts to open the first
     * connection.</p>
     *
     * @throws IOException if the bundled
     *         {@code schemacrawler-hive.config.properties} resource cannot be
     *         located on the classpath &mdash; propagated from the
     *         {@link ClasspathInputResource} super-constructor.
     * @throws RuntimeException wrapping any {@link ClassNotFoundException}
     *         raised while loading the Hive JDBC driver class.
     */
    public HiveDatabaseConnector() throws IOException {

        super(new DatabaseServerType("hive", "Apache Hive DataBase"),
                new ClasspathInputResource("/schemacrawler-hive.config.properties"), (informationSchemaViewsBuilder,
                        connection) -> informationSchemaViewsBuilder.fromResourceFolder("/hive.information_schema"));
        try {
            Class.forName(HIVE_DRIVER_CLASS_NAME);
        } catch (final ClassNotFoundException e) {
            throw new RuntimeException(DRIVER_LOAD_FAILURE_PREFIX, e);
        }

    }

    /**
     * Returns the command-line help block contributed by this connector.
     *
     * <p>The returned {@link PluginCommand} augments the default help text
     * that SchemaCrawler emits for every registered database plug-in with
     * HiveServer2-specific options:</p>
     * <ul>
     *   <li>{@code --server=hive2} &mdash; selects this plug-in;</li>
     *   <li>{@code --host} &mdash; server hostname, defaults to
     *       {@code localhost};</li>
     *   <li>{@code --port} &mdash; TCP port, defaults to {@code 3306}
     *       (the value is inherited from the upstream SchemaCrawler
     *       MySQL defaults; Hive deployments typically run on
     *       {@code 10000}, which can be overridden at the command line);</li>
     *   <li>{@code --database} &mdash; target database name.</li>
     * </ul>
     *
     * @return a non-null {@link PluginCommand} describing the HiveServer2
     *         command-line surface. The same instance is mutated and
     *         returned per call &mdash; callers should treat it as
     *         read-only.
     */
    @Override
    public PluginCommand getHelpCommand() {
        final PluginCommand pluginCommand = super.getHelpCommand();
        pluginCommand.addOption("server", "--server=hive2%n" + "Loads SchemaCrawler plug-in for Hive2", String.class)
                .addOption("host", "Host name%n" + "Optional, defaults to localhost", String.class)
                .addOption("port", "Port number%n" + "Optional, defaults to 3306", Integer.class)
                .addOption("database", "Database name", String.class);
        return pluginCommand;
    }

    /**
     * Returns the predicate used by SchemaCrawler to decide whether a given
     * JDBC URL belongs to Hive.
     *
     * <p>The predicate returns {@code true} iff the supplied URL starts with
     * {@code jdbc:hive2:}. The check is implemented with
     * {@link Pattern#matches(String, CharSequence)} against the cached
     * {@link #HIVE2_URL_PATTERN} so that the URL must match the prefix in
     * its entirety from the start of the string &mdash; substrings inside
     * path segments are ignored.</p>
     *
     * @return a non-null {@link Predicate} that accepts a JDBC URL string
     *         and returns {@code true} when the URL is a HiveServer2 URL.
     */
    @Override
    protected Predicate<String> supportsUrlPredicate() {
        return url -> Pattern.matches(HIVE2_URL_PATTERN, url);
    }

}