package dev.jeka.core.api.depmanagement;

import dev.jeka.core.api.utils.JkUtilsAssert;

import java.util.*;
import java.util.stream.Collectors;

/**
 * A bunch of {@link JkQualifiedDependency}
 */
public class JkQualifiedDependencySet {

    public static final String COMPILE_SCOPE = "compile"; // compile scope for published dependencies

    public static final String RUNTIME_SCOPE = "runtime";  // runtime scope for published dependencies

    public static final String PROVIDED_SCOPE = "provided";  // provided scope for published dependencies

    public static final String TEST_SCOPE = "test";  // provided scope for published dependencies

    public static final String MASTER_TARGET_CONF = "archives(master)";

    public static final String COMPILE_TARGET_CONF = "compile(default)";

    public static final String RUNTIME_TARGET_CONF = "runtime(default)";

    public static final String TEST_TARGET_CONF = "test(default)";

    private static final Map<JkTransitivity, String> TRANSITIVITY_TARGET_CONF_MAP = new HashMap<>();

    static {
        TRANSITIVITY_TARGET_CONF_MAP.put(JkTransitivity.NONE, MASTER_TARGET_CONF);
        TRANSITIVITY_TARGET_CONF_MAP.put(JkTransitivity.COMPILE, MASTER_TARGET_CONF + ", " + COMPILE_TARGET_CONF);
        TRANSITIVITY_TARGET_CONF_MAP.put(JkTransitivity.RUNTIME, MASTER_TARGET_CONF + ", " + COMPILE_TARGET_CONF
                + ", " + RUNTIME_TARGET_CONF);

    }

    private final List<JkQualifiedDependency> entries;

    // Transitive dependencies globally excluded
    private final Set<JkDependencyExclusion> globalExclusions;

    private final JkVersionProvider versionProvider;

    private JkQualifiedDependencySet(List<JkQualifiedDependency> qualifiedDependencies, Set<JkDependencyExclusion>
            globalExclusions, JkVersionProvider versionProvider) {
        this.entries = Collections.unmodifiableList(qualifiedDependencies);
        this.globalExclusions = Collections.unmodifiableSet(globalExclusions);
        this.versionProvider = versionProvider;
    }

    public static JkQualifiedDependencySet of() {
        return new JkQualifiedDependencySet(Collections.emptyList(), Collections.emptySet(), JkVersionProvider.of());
    }

    public static JkQualifiedDependencySet ofDependencies(List<JkDependency> dependencies) {
        return of(dependencies.stream().map(dep -> JkQualifiedDependency.of(null, dep)).collect(Collectors.toList()));
    }

    public static JkQualifiedDependencySet of(List<JkQualifiedDependency> qualifiedDependencies) {
        return new JkQualifiedDependencySet(qualifiedDependencies, Collections.emptySet(), JkVersionProvider.of());
    }

    public static JkQualifiedDependencySet of(JkDependencySet dependencySet) {
        return ofDependencies(dependencySet.getEntries())
                .withGlobalExclusions(dependencySet.getGlobalExclusions())
                .withVersionProvider(dependencySet.getVersionProvider());
    }

    public List<JkQualifiedDependency> getEntries() {
        return entries;
    }

    public List<JkDependency> getDependencies() {
        return entries.stream()
                .map(JkQualifiedDependency::getDependency)
                .collect(Collectors.toList());
    }

    public List<JkCoordinateDependency> getCoordinateDependencies() {
        return entries.stream()
                .map(JkQualifiedDependency::getDependency)
                .filter(JkCoordinateDependency.class::isInstance)
                .map(JkCoordinateDependency.class::cast)
                .collect(Collectors.toList());
    }

    public Set<JkDependencyExclusion> getGlobalExclusions() {
        return globalExclusions;
    }

    public JkVersionProvider getVersionProvider() {
        return versionProvider;
    }

    public List<JkQualifiedDependency> findByModule(String moduleId) {
        return this.entries.stream()
                .filter(qDep -> qDep.getDependency() instanceof JkCoordinateDependency)
                .filter(qDep -> qDep.getCoordinateDependency().getCoordinate().getModuleId().getColonNotation()
                        .equals(moduleId))
                .collect(Collectors.toList());
    }

    public JkQualifiedDependencySet remove(JkDependency dependency) {
        List<JkQualifiedDependency> dependencies = entries.stream()
                .filter(qDep -> !qDep.equals(dependency))
                .collect(Collectors.toList());
        return new JkQualifiedDependencySet(dependencies, globalExclusions, versionProvider);
    }

    public JkQualifiedDependencySet and(JkQualifiedDependency qualifiedDependency) {
        List<JkQualifiedDependency> result = new LinkedList<>(this.entries);
        result.add(qualifiedDependency);
        return new JkQualifiedDependencySet(result, globalExclusions, versionProvider);
    }

    public JkQualifiedDependencySet and(String qualifier, JkDependency dependency) {
        return and(JkQualifiedDependency.of(qualifier, dependency));
    }

    public JkQualifiedDependencySet and(String qualifier, String moduleDependencyDescriptor) {
        return and(qualifier, JkCoordinateDependency.of(moduleDependencyDescriptor));
    }

    public JkQualifiedDependencySet remove(String dep) {
        return remove(JkCoordinateDependency.of(dep));
    }

    public JkQualifiedDependencySet replaceQualifier(JkDependency dependency, String qualifier) {
        List<JkQualifiedDependency> dependencies = entries.stream()
                .map(qDep -> qDep.getDependency().equals(dependency) ? qDep.withQualifier(qualifier) : qDep)
                .collect(Collectors.toList());
        return new JkQualifiedDependencySet(dependencies, globalExclusions, versionProvider);
    }

    public JkQualifiedDependencySet replaceQualifier(String dependency, String qualifier) {
        return replaceQualifier(JkCoordinateDependency.of(dependency), qualifier);
    }

    public JkQualifiedDependencySet withQualifiersOnly(String ... qualifiers) {
        List<JkQualifiedDependency> dependencies = entries.stream()
                .filter(dep -> Arrays.asList(qualifiers).contains(dep.getQualifier()))
                .collect(Collectors.toList());
        return new JkQualifiedDependencySet(dependencies, globalExclusions, versionProvider);
    }

    public JkQualifiedDependencySet withModuleDependenciesOnly() {
        List<JkQualifiedDependency> dependencies = entries.stream()
                .filter(qDep -> qDep.getDependency() instanceof JkCoordinateDependency)
                .collect(Collectors.toList());
        return new JkQualifiedDependencySet(dependencies, globalExclusions, versionProvider);
    }

    /**
     * These exclusions only stands for dependencies that are retrieved transitively. This means that
     * this not involves dependencies explicitly declared here.
     */
    public JkQualifiedDependencySet withGlobalExclusions(Set<JkDependencyExclusion> exclusions) {
        Set<JkDependencyExclusion> newExclusions = new HashSet<>(this.globalExclusions);
        newExclusions.addAll(exclusions);
        return new JkQualifiedDependencySet(entries, Collections.unmodifiableSet(newExclusions),
                versionProvider);
    }

    /**
     * These exclusions only stands for dependencies that are retrieved transitively. This means that
     * this not involves dependencies explicitly declared here.
     */
    public JkQualifiedDependencySet withVersionProvider(JkVersionProvider versionProvider) {
        return new JkQualifiedDependencySet(entries, globalExclusions, this.versionProvider
            .and(versionProvider));
    }

    /**
     * Replace the version provider of this object
     */
    public JkQualifiedDependencySet withResolvedBoms(JkRepoSet repos) {
        JkVersionProvider resolvedVersionProvider = versionProvider.withResolvedBoms(repos);
        return new JkQualifiedDependencySet(this.entries, globalExclusions, resolvedVersionProvider);
    }

    public static JkQualifiedDependencySet computeIdeDependencies(
            JkDependencySet allCompileDeps,
            JkDependencySet allRuntimeDeps,
            JkDependencySet allTestDeps,
            JkCoordinate.ConflictStrategy strategy) {
        JkDependencySetMerge prodMerge = allCompileDeps.merge(allRuntimeDeps);
        JkDependencySetMerge testMerge = prodMerge.getResult().merge(allTestDeps);
        List<JkQualifiedDependency> result = new LinkedList<>();
        List<JkDependency> dependencies = testMerge.getResult()
                .normalised(strategy)
                //.assertNoUnspecifiedVersion()
                .getEntries();
        for (JkDependency dependency : dependencies) {
            final String scope;
            if (prodMerge.getResult().getEntries().contains(dependency)) {
                if (prodMerge.getAbsentDependenciesFromRight().contains(dependency)) {
                    scope = PROVIDED_SCOPE;
                } else if (prodMerge.getAbsentDependenciesFromLeft().contains(dependency)) {
                    scope = RUNTIME_SCOPE;
                } else {
                    scope = COMPILE_SCOPE;
                }
            } else {
                scope = TEST_SCOPE;
            }
            JkDependency versionedDependency = testMerge.getResult().getVersionProvider().version(dependency);
            result.add(JkQualifiedDependency.of(scope, versionedDependency));

        }
        return new JkQualifiedDependencySet(result, testMerge.getResult().getGlobalExclusions(),
                testMerge.getResult().getVersionProvider());
    }

    public static JkQualifiedDependencySet computeIdeDependencies(
            JkDependencySet allCompileDeps,
            JkDependencySet allRuntimeDeps,
            JkDependencySet allTestDeps) {
        return computeIdeDependencies(allCompileDeps, allRuntimeDeps, allTestDeps, JkCoordinate.ConflictStrategy.FAIL);
    }

    public static JkQualifiedDependencySet computeIvyPublishDependencies(
            JkDependencySet allCompileDeps,
            JkDependencySet allRuntimeDeps,
            JkDependencySet allTestDeps,
            JkCoordinate.ConflictStrategy strategy) {
        JkDependencySetMerge mergeWithProd = allCompileDeps.merge(allRuntimeDeps);
        JkDependencySetMerge mergeWithTest = mergeWithProd.getResult().merge(allTestDeps);
        List<JkQualifiedDependency> result = new LinkedList<>();
        for (JkCoordinateDependency dependency : mergeWithTest.getResult().normalised(strategy)
                .assertNoUnspecifiedVersion().getVersionResolvedCoordinateDependencies()) {
            final String configurationSource;
            String configurationTarget;
            if (mergeWithProd.getResult().getMatching(dependency) != null) {
                if (mergeWithProd.getAbsentDependenciesFromRight().contains(dependency)) {
                    configurationSource = COMPILE_SCOPE;
                    configurationTarget = MASTER_TARGET_CONF + ", " + COMPILE_TARGET_CONF;
                } else if (mergeWithProd.getAbsentDependenciesFromLeft().contains(dependency)) {
                    configurationSource = RUNTIME_SCOPE;
                    configurationTarget = MASTER_TARGET_CONF + ", " + RUNTIME_TARGET_CONF;
                } else {
                    configurationSource = COMPILE_SCOPE + "," + RUNTIME_SCOPE;
                    configurationTarget = MASTER_TARGET_CONF + ", " + COMPILE_TARGET_CONF + ", " + RUNTIME_TARGET_CONF;
                }
            } else {
                configurationSource = TEST_SCOPE;
                configurationTarget = MASTER_TARGET_CONF + ", " + COMPILE_TARGET_CONF + ", "
                        + RUNTIME_TARGET_CONF + ", " + TEST_TARGET_CONF;
            }
            if (dependency.getTransitivity() != null) {
                configurationTarget = getIvyTargetConfigurations(dependency.getTransitivity());
            }
            String configuration = configurationSource + " -> " + configurationTarget;
            result.add(JkQualifiedDependency.of(configuration, dependency));
        }
        return new JkQualifiedDependencySet(result, mergeWithTest.getResult().getGlobalExclusions(),
                mergeWithTest.getResult().getVersionProvider());
    }


    public static String getIvyTargetConfigurations(JkTransitivity transitivity) {
        return TRANSITIVITY_TARGET_CONF_MAP.get(transitivity);
    }

    public List<JkDependency> getDependenciesHavingQualifier(String ... qualifiers) {
        List<String> list = Arrays.asList(qualifiers);
        return entries.stream()
                .filter(qDep -> list.contains(qDep.getQualifier()))
                .map(JkQualifiedDependency::getDependency)
                .collect(Collectors.toList());
    }

    public JkQualifiedDependencySet assertNoUnspecifiedVersion() {
        final List<JkCoordinateDependency> unspecifiedVersionModules = getCoordinateDependencies().stream()
                .filter(dep -> this.versionProvider.getVersionOfOrUnspecified(
                        dep.getCoordinate().getModuleId()).isUnspecified())
                .filter(dep -> dep.getCoordinate().getVersion().isUnspecified())
                .collect(Collectors.toList());
        JkUtilsAssert.state(unspecifiedVersionModules.isEmpty(), "Following module does not specify version : "
                + unspecifiedVersionModules);
        return this;
    }

    /**
     * Fills the dependencies without specified version with the version supplied by the {@link JkVersionProvider}.
     */
    public JkQualifiedDependencySet toResolvedModuleVersions() {
        List<JkQualifiedDependency> dependencies = entries.stream()
                .map(qDep -> {
                    if (qDep.getDependency() instanceof JkCoordinateDependency) {
                        JkCoordinateDependency coordinateDependency = (JkCoordinateDependency) qDep.getDependency();
                        JkVersion providedVersion = this.versionProvider
                                .getVersionOfOrUnspecified(coordinateDependency.getCoordinate().getModuleId());
                        if (coordinateDependency.getCoordinate().getVersion().isUnspecified()
                                && !providedVersion.isUnspecified()) {
                            return JkQualifiedDependency.of(qDep.getQualifier(),
                                    coordinateDependency.withVersion(providedVersion));
                        }
                    }
                    return qDep;
                })
                .collect(Collectors.toList());
        return new JkQualifiedDependencySet(dependencies, this.globalExclusions, this.versionProvider);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        JkQualifiedDependencySet that = (JkQualifiedDependencySet) o;

        if (!entries.equals(that.entries)) return false;
        if (!globalExclusions.equals(that.globalExclusions)) return false;
        return versionProvider.equals(that.versionProvider);
    }

    @Override
    public int hashCode() {
        int result = entries.hashCode();
        result = 31 * result + globalExclusions.hashCode();
        result = 31 * result + versionProvider.hashCode();
        return result;
    }
}
