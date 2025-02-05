package dev.jeka.core.api.project;

import dev.jeka.core.api.depmanagement.*;
import dev.jeka.core.api.depmanagement.JkDependencySet.Hint;
import dev.jeka.core.api.file.JkPathTree;
import dev.jeka.core.api.file.JkZipTree;
import org.junit.Assert;
import org.junit.Test;

import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.stream.Collectors;

public class JkProjectTest {

    @Test
    public void getTestDependencies_containsCompileDependencies() {
        JkProject javaProject = JkProject.of()
                .flatFacade()
                .configureCompileDependencies(deps -> deps.and("a:a"))
                .configureTestDependencies(deps -> deps.and("b:b"))
                .getProject();
        JkDependencySet compileDeps = javaProject
                .prodCompilation.getDependencies();
        JkDependencySet testCompileDeps = javaProject.testing
                .testCompilation.getDependencies();
        Assert.assertEquals(1, compileDeps.getEntries().stream()
                .filter(JkCoordinateDependency.class::isInstance)
                .collect(Collectors.toList())
                .size());
        Assert.assertNotNull(compileDeps.get("a:a"));
        Assert.assertEquals(2, testCompileDeps.getEntries().stream()
                .filter(JkCoordinateDependency.class::isInstance)
                .collect(Collectors.toList())
                .size());
        Assert.assertNotNull(testCompileDeps.get("a:a"));
        Assert.assertNotNull(testCompileDeps.get("b:b"));
    }

    public void addDependencies() {
        JkProject javaProject = JkProject.of()
                .flatFacade()
                .addCompileDeps("a:a", "a:a1")
                .addCompileOnlyDeps("a:a2")
                .addRuntimeDeps("c:c")
                .addTestDeps("b:b")
                .getProject();
        JkDependencySet compileDeps = javaProject
                .prodCompilation.getDependencies();
        JkDependencySet testCompileDeps = javaProject.testing
                .testCompilation.getDependencies();
        JkDependencySet runtimeDeps = javaProject.packaging.getRuntimeDependencies();
        Assert.assertEquals(3, compileDeps.getEntries().stream()
                .filter(JkCoordinateDependency.class::isInstance)
                .collect(Collectors.toList())
                .size());
        Assert.assertEquals(3, runtimeDeps.getEntries().stream()
                .filter(JkCoordinateDependency.class::isInstance)
                .collect(Collectors.toList())
                .size());
        Assert.assertEquals(5, testCompileDeps.getEntries().stream()
                .filter(JkCoordinateDependency.class::isInstance)
                .collect(Collectors.toList())
                .size());
    }

    @Test
    public void getTestDependencies_usingSetTestDependency_ok() {
        JkProject project = JkProject.of().flatFacade()
                .configureCompileDependencies(deps -> deps
                        .and("com.google.guava:guava:23.0", JkTransitivity.NONE)
                        .and("javax.servlet:javax.servlet-api:4.0.1"))
                .configureRuntimeDependencies(deps -> deps
                        .and("org.postgresql:postgresql:42.2.19")
                        .withTransitivity("com.google.guava:guava", JkTransitivity.RUNTIME)
                        .minus("javax.servlet:javax.servlet-api"))
                .configureTestDependencies(deps -> deps
                        .and(Hint.first(), "org.mockito:mockito-core:2.10.0")
                )
                .setPublishedModuleId("my:project").setPublishedVersion("MyVersion")
                .getProject();
        JkDependencySet testDependencies = project.testing.testCompilation.getDependencies();
        System.out.println(project.getInfo());
        Assert.assertEquals(JkTransitivity.RUNTIME, testDependencies.get("com.google.guava:guava").getTransitivity());
        Assert.assertNotNull(testDependencies.get("javax.servlet:javax.servlet-api"));
        Assert.assertEquals("org.mockito:mockito-core", testDependencies.getCoordinateDependencies().get(0)
                .getCoordinate().getModuleId().toString());
    }

    @Test
    public void addVersionProviderOnCompile_testAndRuntimeHaveVersionProvider() {
        JkVersionProvider versionProvider = JkVersionProvider.of()
                .and("javax.servlet:javax.servlet-api", "4.0.1");
        JkProject project = JkProject.of().flatFacade()
                .configureCompileDependencies(deps -> deps
                        .andVersionProvider(versionProvider)
                        .and("javax.servlet:javax.servlet-api")
                ).getProject();
        JkDependencySet testDeps = project.testing.testCompilation.getDependencies();
        Assert.assertEquals("4.0.1",
                testDeps.getVersionProvider().getVersionOf("javax.servlet:javax.servlet-api"));
    }

    @Test
    public void getTestDependencies_usingAddTestDependency_ok() {
        JkProject project = JkProject.of().flatFacade()
                .configureCompileDependencies(deps -> deps
                        .and("com.google.guava:guava:23.0", JkTransitivity.NONE)
                        .and("javax.servlet:javax.servlet-api:4.0.1"))
                .configureRuntimeDependencies(deps -> deps
                        .and("org.postgresql:postgresql:42.2.19")
                        .withTransitivity("com.google.guava:guava", JkTransitivity.RUNTIME)
                        .minus("javax.servlet:javax.servlet-api"))
                .configureTestDependencies(deps -> deps
                        .and(Hint.first(), "io.rest-assured:rest-assured:4.3.3")
                        .and(Hint.first(), "org.mockito:mockito-core:2.10.0")
                )
                .setPublishedModuleId("my:project").setPublishedVersion("MyVersion")
                .getProject();
        JkDependencySet testDependencies = project.testing.testCompilation.getDependencies();
        System.out.println(project.getInfo());
        Assert.assertEquals(JkTransitivity.RUNTIME, testDependencies.get("com.google.guava:guava").getTransitivity());
        Assert.assertNotNull(testDependencies.get("javax.servlet:javax.servlet-api"));
        Assert.assertEquals("org.mockito:mockito-core", testDependencies.getCoordinateDependencies().get(0)
                .getCoordinate().getModuleId().toString());
        Assert.assertEquals("io.rest-assured:rest-assured", testDependencies.getCoordinateDependencies().get(1)
                .getCoordinate().getModuleId().toString());
    }

    @Test
    public void getPublishMavenDependencies_ok() {
        JkProject project = JkProject.of().flatFacade()
                .configureCompileDependencies(deps -> deps
                        .and("com.google.guava:guava:23.0", JkTransitivity.NONE)
                        .and("javax.servlet:javax.servlet-api:4.0.1"))
                .configureRuntimeDependencies(deps -> deps
                        .and("org.postgresql:postgresql:42.2.19")
                        .withTransitivity("com.google.guava:guava", JkTransitivity.RUNTIME)
                        .minus("javax.servlet:javax.servlet-api"))
                .configureTestDependencies(deps -> deps
                        .and(Hint.first(), "org.mockito:mockito-core:2.10.0")
                        .and(Hint.first(), "io.rest-assured:rest-assured:4.3.3")
                )
                .setPublishedModuleId("my:project").setPublishedVersion("MyVersion")
                .configurePublishedDeps(deps -> deps.minus("org.postgresql:postgresql"))
                .getProject();
        JkDependencySet publishDeps = project.publication.maven.getDependencies();
        publishDeps.getEntries().forEach(System.out::println);
        Assert.assertEquals(JkTransitivity.COMPILE, publishDeps.get("javax.servlet:javax.servlet-api").getTransitivity());
    }

    @Test
    public void getPublishIvyDependencies_ok() {
        JkProject project = JkProject.of().flatFacade()
                .configureCompileDependencies(deps -> deps
                        .and("com.google.guava:guava:23.0", JkTransitivity.NONE)
                        .and("javax.servlet:javax.servlet-api:4.0.1"))
                .configureRuntimeDependencies(deps -> deps
                        .and("org.postgresql:postgresql:42.2.19")
                        .withTransitivity("com.google.guava:guava", JkTransitivity.RUNTIME)
                        .minus("javax.servlet:javax.servlet-api"))
                .configureCompileDependencies(deps -> deps
                        .and(Hint.first(), "org.mockito:mockito-core:2.10.0")
                        .and(Hint.first(), "io.rest-assured:rest-assured:4.3.3")
                ).getProject();
        project.publication.ivy
                .setModuleId("my:module")
                .setVersion("0.1");
        System.out.println(project.prodCompilation.getDependencies());
        JkQualifiedDependencySet publishDeps = project.publication.ivy.getDependencies();
        publishDeps.getEntries().forEach(System.out::println);
    }

    @Test
    public void makeAllArtifacts() throws Exception {
        final Path top = unzipToDir("sample-multi-scriptless.zip");

        Path base = top.resolve("base");
        JkProject baseProject = JkProject.of().setBaseDir(base).flatFacade()
                .configureCompileDependencies(deps -> deps.and(JkPopularLibs.APACHE_HTTP_CLIENT.toCoordinate("4.5.6")))
                .getProject()
                    .prodCompilation
                        .layout
                            .emptySources().addSource("src")
                            .emptyResources().addResource("res")
                            .mixResourcesAndSources()
                        .__
                .__;
        baseProject.artifactProducer.makeAllArtifacts();

        final Path core = top.resolve("core");
        final JkProject coreProject = JkProject.of()
                .setBaseDir(core)
                    .prodCompilation
                        .configureDependencies(deps -> deps
                            .and(baseProject.toDependency())
                        )
                        .layout
                            .setSourceSimpleStyle(JkCompileLayout.Concern.PROD)
                        .__
                    .__;

        //Desktop.getDesktop().open(core.toFile());
        coreProject.artifactProducer.makeAllArtifacts();

        final Path desktop = top.resolve("desktop");
        final JkProject desktopProject = JkProject.of()
                .setBaseDir(desktop)
                .prodCompilation
                    .configureDependencies(deps -> deps
                            .and(coreProject.toDependency())).__
                .prodCompilation
                    .layout
                    .setSourceSimpleStyle(JkCompileLayout.Concern.PROD).__.__;
        //Desktop.getDesktop().open(desktop.toFile());
        //desktopProject.getArtifactProducer().makeAllArtifacts();

        // Desktop.getDesktop().open(desktop);
        JkPathTree.of(top).deleteRoot();
    }

    private static Path unzipToDir(String zipName) throws IOException, URISyntaxException {
        final Path dest = Files.createTempDirectory(JkProjectTest.class.getName());
        final Path zip = Paths.get(JkProjectTest.class.getResource(zipName).toURI());
        JkZipTree.of(zip).copyTo(dest);
        System.out.println("unzipped in " + dest);
        return dest;
    }

    @Test
    public void getRuntimeDependencies_usingDependenciesTxt_ok() {
        JkProject project = JkProject.of()
                    .setIncludeTextAndLocalDependencies(true);
        URL dependencyTxtUrl = JkProjectTest.class.getResource("simple-dependencies-simple.txt");
        project.setDependencyTxtUrl(dependencyTxtUrl);
        JkDependencySet runtimeDependencies = project.packaging.getRuntimeDependencies();
        JkCoordinateDependency lombokDep = runtimeDependencies.getMatching(JkCoordinateDependency.of("org.projectlombok:lombok"));
        runtimeDependencies.getEntries().forEach(System.out::println);
        Assert.assertNull(lombokDep);  // expect lombok not included


    }
}
