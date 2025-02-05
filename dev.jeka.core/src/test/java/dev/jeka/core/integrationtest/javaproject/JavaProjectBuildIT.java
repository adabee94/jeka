package dev.jeka.core.integrationtest.javaproject;

import dev.jeka.core.api.depmanagement.JkTransitivity;
import dev.jeka.core.api.depmanagement.resolution.JkResolveResult;
import dev.jeka.core.api.depmanagement.resolution.JkResolvedDependencyNode;
import dev.jeka.core.api.file.JkZipTree;
import dev.jeka.core.api.project.JkProject;
import org.junit.Assert;
import org.junit.Test;

import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class JavaProjectBuildIT {

    @Test
    public void resolve_dependencyOfTypeProject_resultWithProjectIdeDir() throws Exception {
        Path root = unzipToDir("sample-multiproject.zip");

        JkProject baseProject = JkProject.of().setBaseDir(root.resolve("base")).flatFacade()
                .useSimpleLayout()
                .configureCompileDependencies(deps -> deps
                        .and("com.google.guava:guava:23.0")).getProject();

        JkProject coreProject = JkProject.of().setBaseDir(root.resolve("core")).flatFacade()
                .useSimpleLayout()
                .configureCompileDependencies(deps -> deps.and(baseProject.toDependency()))
                .getProject();

        JkResolveResult resolveResult = coreProject.prodCompilation.resolveDependencies();

        Assert.assertEquals(2, resolveResult.getDependencyTree().getChildren().size()); // base dir and guava
        JkResolvedDependencyNode dependencyNode = resolveResult.getDependencyTree().getChildren().get(0);
        Assert.assertFalse(dependencyNode.isModuleNode());
        JkResolvedDependencyNode.JkFileNodeInfo nodeInfo = (JkResolvedDependencyNode.JkFileNodeInfo) dependencyNode.getNodeInfo();
        Assert.assertEquals(baseProject.getBaseDir(), nodeInfo.computationOrigin().getIdeProjectDir());
    }

    private static Path unzipToDir(String zipName) throws IOException, URISyntaxException {
        final Path dest = Files.createTempDirectory(JavaProjectBuildIT.class.getName());
        final Path zip = Paths.get(JavaProjectBuildIT.class.getResource(zipName).toURI());
        JkZipTree.of(zip).copyTo(dest);
        System.out.println("unzipped in " + dest);
        return dest;
    }

    @Test
    public void publish_maven_ok() throws IOException, URISyntaxException {
        Path root = unzipToDir("sample-multiproject.zip");
        JkProject project = JkProject.of().setBaseDir(root.resolve("base")).flatFacade()
                .useSimpleLayout()
                .configureCompileDependencies(deps -> deps
                        .and("com.google.guava:guava:23.0")
                        .and("javax.servlet:javax.servlet-api:4.0.1"))
                .configureRuntimeDependencies(deps -> deps
                        .and("org.postgresql:postgresql:42.2.19")
                        .withTransitivity("com.google.guava:guava", JkTransitivity.RUNTIME)
                        .minus("javax.servlet:javax.servlet-api"))
                .configureTestDependencies(deps -> deps
                        .and("org.mockito:mockito-core:2.10.0")
                )
                .setPublishedModuleId("my:project").setPublishedVersion("MyVersion-snapshot")
                .setPublishedVersion("1-SNAPSHOT")
                .getProject();
        project.artifactProducer.makeAllArtifacts();
        project.publication.maven.publishLocal();
        System.out.println(project.getInfo());
        Assert.assertEquals(JkTransitivity.COMPILE, project.publication.maven.getDependencies()
                .get("com.google.guava:guava").getTransitivity());

    }

    @Test
    public void publish_ivy_ok() throws IOException, URISyntaxException {
        Path root = unzipToDir("sample-multiproject.zip");
        JkProject project = JkProject.of().setBaseDir(root.resolve("base")).flatFacade()
                .useSimpleLayout()
                .configureCompileDependencies(deps -> deps
                        .and("com.google.guava:guava:23.0")
                        .and("javax.servlet:javax.servlet-api:4.0.1"))
                .configureRuntimeDependencies(deps -> deps
                        .and("org.postgresql:postgresql:42.2.19")
                        .withTransitivity("com.google.guava:guava", JkTransitivity.RUNTIME)
                        .minus("javax.servlet:javax.servlet-api"))
                .configureTestDependencies(deps -> deps
                        .and("org.mockito:mockito-core:2.10.0")
                ).getProject();
        project.publication.ivy
                .setModuleId("my:module")
                .setVersion("0.1");
        project.artifactProducer.makeAllArtifacts();
        project.publication.ivy.publishLocal();
        System.out.println(project.getInfo());


    }

}
