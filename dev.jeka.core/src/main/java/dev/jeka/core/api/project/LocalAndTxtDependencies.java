package dev.jeka.core.api.project;

import dev.jeka.core.api.depmanagement.*;
import dev.jeka.core.api.file.JkPathTree;
import dev.jeka.core.api.utils.JkUtilsIO;
import dev.jeka.core.api.utils.JkUtilsIterable;
import dev.jeka.core.api.utils.JkUtilsPath;
import dev.jeka.core.api.utils.JkUtilsString;

import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedList;
import java.util.List;


class LocalAndTxtDependencies {

    private final JkDependencySet compile;

    // Equals to Maven 'runtime' scope
    private final JkDependencySet runtime;

    // Equals to Maven 'test' scope
    private final JkDependencySet test;

    private LocalAndTxtDependencies(JkDependencySet compile,
                                    JkDependencySet runtime,
                                    JkDependencySet test) {
        this.compile = compile;
        this.runtime = runtime;
        this.test = test;
    }

    static LocalAndTxtDependencies of() {
        return new LocalAndTxtDependencies(JkDependencySet.of(),
                JkDependencySet.of(), JkDependencySet.of());
    }

    /**
     * Creates a {@link JkDependencySet} based on jars located under the specified directory. Jars are
     * supposed to lie in a directory structure standing for the different scopes they are intended.
     * So jars needed for compilation are supposed to be in <code>baseDir/compile</code>, jar needed for
     * test are supposed to be in <code>baseDir/test</code> and so on.
     */
    public static LocalAndTxtDependencies ofLocal(Path baseDir) {
        return Parser.inspectFileStructure(baseDir);
    }

    /**
     * @see #ofTextDescription(String)
     */
    public static LocalAndTxtDependencies ofOptionalTextDescription(Path path) {
        if (Files.notExists(path)) {
            return LocalAndTxtDependencies.of();
        }
        return ofTextDescription(JkUtilsPath.toUrl(path));
    }

    /**
     * @see #ofTextDescription(String)
     */
    public static LocalAndTxtDependencies ofTextDescription(URL url) {
        return ofTextDescription(JkUtilsIO.read(url));
    }

    /**
     * Creates a {@link LocalAndTxtDependencies} from a flat file formatted as :
     * <pre>
     * == COMPILE ==
     * org.springframework.boot:spring-boot-starter-thymeleaf
     * org.springframework.boot:spring-boot-starter-data-jpa
     * org.projectlombok:lombok:1.16.16
     *
     * == RUNTIME ==
     * com.h2database:h2
     * org.liquibase:liquibase-core
     * com.oracle:ojdbc6:12.1.0
     * - org.projectlombok:lombok
     *
     * == TEST ==
     * org.springframework.boot:spring-boot-starter-test
     * org.seleniumhq.selenium:selenium-chrome-driver:3.4.0
     * org.fluentlenium:fluentlenium-assertj:3.2.0
     * org.fluentlenium:fluentlenium-junit:3.2.0
     * </pre>
     *
     */
    public static LocalAndTxtDependencies ofTextDescription(String description) {
        return Parser.parseTxt(description);
    }

    public JkDependencySet getCompile() {
        return compile;
    }

    public JkDependencySet getRuntime() {
        return runtime;
    }

    public JkDependencySet getTest() {
        return test;
    }


    public LocalAndTxtDependencies and(LocalAndTxtDependencies other) {
        return new LocalAndTxtDependencies(
                compile.and(other.compile),
                runtime.and(other.runtime),
                test.and(other.test)
        );
    }

    private static class Parser {

        private static final String COMPILE = "compile";

        private static final String RUNTIME = "runtime";

        private static final String TEST = "test";

        private static final String MINUS_SYMBOL = "-";


        private static final String LOCAL_EXCLUDE_SYMBOL = "@";

        private static final String GLOBAL_EXCLUDE_SYMBOL = "@@";


        private static final List<String> KNOWN_QUALIFIER = JkUtilsIterable.listOf(COMPILE,
                RUNTIME, TEST);

        private static LocalAndTxtDependencies inspectFileStructure(Path baseDir) {
            final JkPathTree libDir = JkPathTree.of(baseDir);
            if (!libDir.exists()) {
                return LocalAndTxtDependencies.of();
            }
            JkDependencySet regular = JkDependencySet.of()
                    .andFiles(libDir.andMatching(true, "regular" + "/*.jar").getFiles());
            JkDependencySet compileOnly = JkDependencySet.of()
                    .andFiles(libDir.andMatching(true, "compile-only" + "/*.jar").getFiles());
            JkDependencySet runtimeOnly = JkDependencySet.of()
                    .andFiles(libDir.andMatching(true, "*.jar", "runtime-only" + "/*.jar").getFiles());
            JkDependencySet test = JkDependencySet.of()
                    .andFiles(libDir.andMatching(true, "*.jar", TEST + "/*.jar").getFiles());

            return new LocalAndTxtDependencies(
                    regular.and(compileOnly),
                    regular.and(runtimeOnly),
                    test.and(regular).and(compileOnly).and(runtimeOnly));
        }

        static LocalAndTxtDependencies parseTxt(String description) {
            final String[] lines = description.split(System.lineSeparator());
            List<String> compileEntries = new LinkedList<>();
            List<String> runtimeEntries = new LinkedList<>();
            List<String> testEntries = new LinkedList<>();

            String currentQualifier = COMPILE;
            for (final String line : lines) {
                if (line.trim().startsWith("#")) {
                    continue;
                }
                if (line.trim().isEmpty()) {
                    continue;
                }
                if (line.startsWith("==")) {
                    currentQualifier = readQualifier(line);
                    continue;
                }
                String effectiveLine = line.trim();
                if (line.contains("#")) {
                    effectiveLine = JkUtilsString.substringBeforeFirst(line, "#").trim();
                }
                //final JkCoordinateDependency dependency = JkCoordinateDependency.of(effectiveLine.trim());
                if (COMPILE.equals(currentQualifier)) {
                    compileEntries.add(effectiveLine);
                } else if (RUNTIME.equals(currentQualifier)) {
                    runtimeEntries.add(effectiveLine);
                } else if (TEST.equals(currentQualifier)) {
                    testEntries.add(effectiveLine);
                }
            }
            JkDependencySet compile = JkDependencySet.of();
            for (String line : compileEntries) {
                compile = apply(line, compile);
            }
            JkDependencySet runtime = compile;
            for (String line : runtimeEntries) {
                runtime = apply(line, runtime);
            }
            JkDependencySet test = runtime.merge(compile).getResult();
            for (String line : testEntries) {
                test = apply(line, test);
            }


            return new LocalAndTxtDependencies(compile, runtime, test);
        }

        private static JkDependencySet apply(String line, JkDependencySet current) {
            if (line.startsWith(MINUS_SYMBOL)) {
                String coordinate = line.substring(MINUS_SYMBOL.length()).trim();
                return current.minus(coordinate);
            }
            if (line.startsWith(LOCAL_EXCLUDE_SYMBOL)) {
                String coordinate = line.substring(LOCAL_EXCLUDE_SYMBOL.length()).trim();
                return current.withLocalExclusions(coordinate);
            }
            if (line.startsWith(GLOBAL_EXCLUDE_SYMBOL)) {
                String coordinate = line.substring(GLOBAL_EXCLUDE_SYMBOL.length()).trim();
                return current.withGlobalExclusions(coordinate);
            }
            else {
                JkCoordinate coordinate = JkCoordinate.of(line);
                JkCoordinate.JkArtifactSpecification spec = coordinate.getArtifactSpecification();
                if (spec.getClassifier() == null && "pom".equals(spec.getType())) {
                    JkVersionProvider versionProvider = current.getVersionProvider().andBom(coordinate);
                    return current.withVersionProvider(versionProvider);
                }
                return current.and(line);
            }
        }

        private static String readQualifier(String line) {
            String payload = JkUtilsString.substringAfterFirst(line,"== ").trim();
            if (payload.contains("=")) {
                payload = JkUtilsString.substringBeforeFirst(payload, "=").toLowerCase().trim();
            }
            if (KNOWN_QUALIFIER.contains(payload)) {
                return payload;
            }
            return COMPILE;
        }

    }

}
