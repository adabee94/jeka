package dev.jeka.core.api.depmanagement.embedded.ivy;

import dev.jeka.core.api.depmanagement.JkCoordinate;
import dev.jeka.core.api.depmanagement.artifact.JkArtifactId;
import dev.jeka.core.api.depmanagement.artifact.JkArtifactLocator;
import dev.jeka.core.api.depmanagement.publication.JkMavenMetadata;
import dev.jeka.core.api.depmanagement.publication.JkPomMetadata;
import dev.jeka.core.api.depmanagement.publication.JkPomTemplateGenerator;
import dev.jeka.core.api.system.JkLog;
import dev.jeka.core.api.utils.*;
import org.apache.ivy.core.module.descriptor.DefaultModuleDescriptor;
import org.apache.ivy.core.module.descriptor.ModuleDescriptor;
import org.apache.ivy.core.module.id.ModuleId;
import org.apache.ivy.core.module.id.ModuleRevisionId;
import org.apache.ivy.plugins.parser.m2.PomModuleDescriptorWriter;
import org.apache.ivy.plugins.parser.m2.PomWriterOptions;
import org.apache.ivy.plugins.repository.Repository;
import org.apache.ivy.plugins.repository.Resource;
import org.apache.ivy.plugins.resolver.DependencyResolver;
import org.apache.ivy.plugins.resolver.IBiblioResolver;
import org.apache.ivy.plugins.resolver.RepositoryResolver;
import org.apache.ivy.util.ChecksumHelper;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Set;
import java.util.function.UnaryOperator;

/**
 * {@link IvyInternalPublisher} delegates to this class for publishing to Maven
 * repositories.
 */
final class IvyPublisherForMaven {

    private final RepositoryResolver resolver;

    private final UnaryOperator<Path> signer;

    private final Path descriptorOutputDir;

    private final boolean uniqueSnapshot;

    private final Set<String> checksumAlgos;

    IvyPublisherForMaven(UnaryOperator<Path> signer, RepositoryResolver dependencyResolver,
                         Path descriptorOutputDir, boolean uniqueSnapshot, Set<String> checksumAlgos) {
        super();
        this.resolver = dependencyResolver;
        this.descriptorOutputDir = descriptorOutputDir;
        this.signer = signer;
        this.uniqueSnapshot = uniqueSnapshot;
        this.checksumAlgos = checksumAlgos;
    }

    void publish(DefaultModuleDescriptor moduleDescriptor, JkArtifactLocator artifactLocator, JkPomMetadata metadata) {
        final ModuleRevisionId ivyModuleRevisionId = moduleDescriptor.getModuleRevisionId();
        try {
            resolver.beginPublishTransaction(ivyModuleRevisionId, true);
        } catch (final IOException e) {
            throw new RuntimeException(e);
        }

        // publish artifacts
        final JkCoordinate coordinate = IvyTranslatorToDependency.toJkCoordinate(ivyModuleRevisionId);
        final JkMavenMetadata returnedMetaData = publish(coordinate, artifactLocator);

        // publish pom
        final Path pomXml = makePom(moduleDescriptor, artifactLocator, metadata);
        final String version;
        if (coordinate.getVersion().isSnapshot() && this.uniqueSnapshot) {
            final String path = snapshotMetadataPath(coordinate);
            final JkMavenMetadata mavenMetadata = JkUtilsObject.firstNonNull(loadMavenMetadata(path),
                    returnedMetaData);
            final JkMavenMetadata.Versioning.JkSnapshot snap = mavenMetadata.currentSnapshot();
            version = versionForUniqueSnapshot(coordinate.getVersion().getValue(), snap.timestamp,
                    snap.buildNumber);
            final String pomDest = destination(coordinate, "pom", JkArtifactId.MAIN_ARTIFACT_NAME,
                    version);
            putInRepo(pomXml, pomDest, true);
            mavenMetadata.addSnapshotVersion("pom", JkArtifactId.MAIN_ARTIFACT_NAME);
            push(mavenMetadata, path);
        } else {
            version = coordinate.getVersion().getValue();
            final String pomDest = destination(coordinate, "pom", JkArtifactId.MAIN_ARTIFACT_NAME, version);
            putInRepo(pomXml, pomDest, true);
        }
        if (this.descriptorOutputDir == null) {
            JkUtilsPath.deleteFile(pomXml);
        }

        // update maven-metadata
        if (returnedMetaData != null) {
            updateMetadata(ivyModuleRevisionId.getModuleId(), ivyModuleRevisionId.getRevision(),
                    returnedMetaData.lastUpdateTimestamp());
        }

        commitPublication(resolver);
    }

    private JkMavenMetadata publish(JkCoordinate coordinate, JkArtifactLocator artifactLocator) {
        if (!coordinate.getVersion().isSnapshot()) {
            final String existing = checkNotExist(coordinate, artifactLocator);
            if (existing != null) {
                throw new IllegalArgumentException("Artifact " + existing
                        + " already exists on repo.");
            }
        }
        if (coordinate.getVersion().isSnapshot() && this.uniqueSnapshot) {
            final String path = snapshotMetadataPath(coordinate);
            JkMavenMetadata mavenMetadata = loadMavenMetadata(path);
            final String timestamp = JkUtilsTime.nowUtc("yyyyMMdd.HHmmss");
            if (mavenMetadata == null) {
                mavenMetadata = JkMavenMetadata.of(coordinate, timestamp);
            }
            mavenMetadata.updateSnapshot(timestamp);
            push(mavenMetadata, path);
            final int buildNumber = mavenMetadata.currentBuildNumber();
            final String versionUniqueSnapshot = versionForUniqueSnapshot(coordinate.getVersion()
                    .getValue(), timestamp, buildNumber);
            for (final JkArtifactId artifactId : artifactLocator.getArtifactIds()) {
                publishUniqueSnapshot(coordinate, artifactId.getName(),
                    artifactLocator.getArtifactPath(artifactId), versionUniqueSnapshot, mavenMetadata);
            }
            return mavenMetadata;
        } else {
            for (final JkArtifactId artifactId : artifactLocator.getArtifactIds()) {
                publishNormal(coordinate, artifactId.getName(), artifactLocator.getArtifactPath(artifactId));
            }
            return null;
        }
    }

    private Path makePom(ModuleDescriptor moduleDescriptor, JkArtifactLocator artifactLocator, JkPomMetadata metadata) {
        final ModuleRevisionId ivyModuleRevisionId = moduleDescriptor.getModuleRevisionId();
        final String artifactName = ivyModuleRevisionId.getName();
        final Path pomXml;
        if (this.descriptorOutputDir != null) {
            pomXml = Paths.get(targetDir()).resolve("published-pom-" + ivyModuleRevisionId.getOrganisation()
            + "-" + artifactName + "-" + ivyModuleRevisionId.getRevision() + ".xml");
        } else {
            pomXml = JkUtilsPath.createTempFile("published-pom-", ".xml");
        }
        final String packaging = JkUtilsString.substringAfterLast(artifactLocator
                .getMainArtifactPath().getFileName().toString(), ".");
        final PomWriterOptions pomWriterOptions = new PomWriterOptions();
        pomWriterOptions.setMapping(new ScopeMapping());
        pomWriterOptions.setArtifactPackaging(packaging);
        Path fileToDelete = null;
        if (metadata != null) {
            final Path template = JkPomTemplateGenerator.generateTemplate(metadata);
            pomWriterOptions.setTemplate(template.toFile());
            fileToDelete = template;
        }
        try {
            PomModuleDescriptorWriter.write(moduleDescriptor, pomXml.toFile(), pomWriterOptions);
            if (fileToDelete != null) {
                Files.deleteIfExists(fileToDelete);
            }
            return pomXml;
        } catch (final IOException e) {
            throw JkUtilsThrowable.unchecked(e);
        }
    }

    private String checkNotExist(JkCoordinate coordinate, JkArtifactLocator artifactLocator) {
        final String pomDest = destination(coordinate, "pom", JkArtifactId.MAIN_ARTIFACT_NAME);
        if (existOnRepo(pomDest)) {
            throw new IllegalArgumentException("The main artifact already exist for " + coordinate);
        }
        for (final JkArtifactId artifactId : artifactLocator.getArtifactIds()) {
            Path artifactFile = artifactLocator.getArtifactPath(artifactId);
            final String ext = JkUtilsString.substringAfterLast(artifactFile.getFileName().toString(), ".");
            final String dest = destination(coordinate, ext, null);
            if (existOnRepo(dest)) {
                return dest;
            }
        }
        return null;
    }

    private boolean existOnRepo(String dest) {
        try {
            final String path = completePath(dest);
            final Resource resource = resolver.getRepository().getResource(path);
            return resource.exists();
        } catch (final IOException e) {
            throw new RuntimeException(e);
        }
    }

    private void publishUniqueSnapshot(JkCoordinate coordinate, String classifier,
            Path source, String versionForUniqueSnapshot, JkMavenMetadata mavenMetadata) {

        final String extension = JkUtilsString.substringAfterLast(source.getFileName().toString(), ".");
        final String dest = destination(coordinate, extension, classifier,
                versionForUniqueSnapshot);
        putInRepo(source, dest, false);
        final String path = snapshotMetadataPath(coordinate);
        mavenMetadata.addSnapshotVersion(extension, classifier);
        push(mavenMetadata, path);
    }

    private void publishNormal(JkCoordinate coordinate, String classifier, Path source) {

        final String extension = JkUtilsString.substringAfterLast(source.getFileName().toString(), ".");
        final String version = coordinate.getVersion().getValue();
        final String dest = destination(coordinate.withVersion(version), extension, classifier);
        final boolean overwrite = coordinate.getVersion().isSnapshot();
        putInRepo(source, dest, overwrite);
    }

    private static String destination(JkCoordinate coordinate, String ext,
            String classifier) {
        return destination(coordinate, ext, classifier, coordinate.getVersion().getValue());
    }

    private static String destination(JkCoordinate coordinate, String ext,
            String classifier, String uniqueVersion) {
        final JkCoordinate.GroupAndName groupAndName = coordinate.getGroupAndName();
        final String version = coordinate.getVersion().getValue();
        final StringBuilder result = new StringBuilder(moduleBasePath(groupAndName)).append("/")
                .append(version).append("/").append(groupAndName.getName()).append("-")
                .append(uniqueVersion);
        if (!JkArtifactId.MAIN_ARTIFACT_NAME.equals(classifier)) {
            result.append("-").append(classifier);
        }
        result.append(".").append(ext);
        return result.toString();
    }

    private static String versionForUniqueSnapshot(String version, String timestamp, int buildNumber) {
        return version.endsWith("-SNAPSHOT") ? JkUtilsString.substringBeforeLast(version,
                "-SNAPSHOT") + "-" + timestamp + "-" + buildNumber : version;
    }

    private void updateMetadata(ModuleId moduleId, String version, String timestamp) {
        final String path = versionMetadataPath(of(moduleId, version));
        JkMavenMetadata mavenMetadata = loadMavenMetadata(path);
        if (mavenMetadata == null) {
            mavenMetadata = JkMavenMetadata.of(JkCoordinate.GroupAndName.of(moduleId.getOrganisation(),
                    moduleId.getName()));
        }
        mavenMetadata.addVersion(version, timestamp);
        push(mavenMetadata, path);
    }

    private void push(JkMavenMetadata metadata, String path) {
        final Path file = JkUtilsPath.createTempFile("metadata-", ".xml");

        try (OutputStream outputStream = Files.newOutputStream(file)) {
            metadata.output(outputStream);
            outputStream.flush();
        } catch (final IOException e) {
            throw new UncheckedIOException(e);
        }
        putInRepo(file, path, true);
    }


    private static String versionMetadataPath(JkCoordinate coordinate) {
        return moduleBasePath(coordinate.getGroupAndName()) + "/maven-metadata.xml";
    }

    private static String moduleBasePath(JkCoordinate.GroupAndName groupAndName) {
        return groupAndName.getGroup().replace(".", "/") + "/" + groupAndName.getName();
    }

    private static String snapshotMetadataPath(JkCoordinate coordinate) {
        return moduleBasePath(coordinate.getGroupAndName()) + "/" + coordinate.getVersion().getValue()
                + "/maven-metadata.xml";
    }

    private JkMavenMetadata loadMavenMetadata(String path) {
        try {
            final Resource resource = resolver.getRepository().getResource(completePath(path));
            if (resource.exists()) {
                final InputStream inputStream = resource.openStream();
                final JkMavenMetadata mavenMetadata = JkMavenMetadata.of(inputStream);
                inputStream.close();
                return mavenMetadata;
            }
            return null;
        } catch (final IOException e) {
            throw new RuntimeException(e);
        }
    }

    private String completePath(String path) {
        if (this.resolver instanceof IBiblioResolver) {
            final IBiblioResolver iBiblioResolver = (IBiblioResolver) this.resolver;
            return iBiblioResolver.getRoot() + path;
        }
        return path;
    }


    private void putInRepo(Path source, String destination, boolean overwrite) {
        final Repository repository = this.resolver.getRepository();
        final String dest = completePath(destination);
        JkLog.info("Publish file " + dest);
        try {
            repository.put(null, source.toFile(), dest, overwrite);
            for (final String algo : checksumAlgos) {
                final Path temp = Files.createTempFile("jk-checksum-", algo);
                final String checkSum = ChecksumHelper.computeAsString(source.toFile(), algo);
                Files.write(temp, checkSum.getBytes());
                final String csDest = dest + "." + algo;
                JkLog.info("Publish file " + csDest);
                repository.put(null, temp.toFile(), csDest, overwrite);
                Files.deleteIfExists(temp);
            }
            if (this.signer != null) {
                final Path signed = signer.apply(source);
                final String signedDest = dest + ".asc";
                JkLog.info("Publish file " + signedDest);
                repository.put(null, signed.toFile(), signedDest, overwrite);
            }
        } catch (final IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private String targetDir() {
        return this.descriptorOutputDir.toAbsolutePath().toString();
    }

    private static void commitPublication(DependencyResolver resolver) {
        try {
            resolver.commitPublishTransaction();
        } catch (final Exception e) {
            throw JkUtilsThrowable.unchecked(e);
        }
    }

    private static JkCoordinate of(ModuleId moduleId, String version) {
        return JkCoordinate.GroupAndName.of(moduleId.getOrganisation(), moduleId.getName()).toCoordinate(version);
    }


    private static class ScopeMapping extends PomWriterOptions.ConfigurationScopeMapping {

        public ScopeMapping() {
            super(new HashMap<>());
        }

        public String getScope(String[] confs) {
            List<String> confLists = Arrays.asList(confs);
            if (confLists.contains("compile")) {
                return "compile";
            }
            if (confLists.contains("runtime")) {
                return "runtime";
            }
            if (confLists.contains("provided")) {
                return "provided";
            }
            if (confLists.contains("test")) {
                return "test";
            }
            return null;
        }


    }

}
