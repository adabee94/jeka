package org.jerkar.api.depmanagement;

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
import org.jerkar.api.depmanagement.JkMavenPublication.JkClassifiedFileArtifact;
import org.jerkar.api.depmanagement.MavenMetadata.Versioning.Snapshot;
import org.jerkar.api.system.JkLog;
import org.jerkar.api.utils.*;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Set;
import java.util.function.UnaryOperator;

/**
 * {@link IvyPublisher} delegates to this class for publishing to Maven
 * repositories.
 */
final class IvyPublisherForMaven {

    private final RepositoryResolver resolver;

    private final UnaryOperator<Path> signer;

    private final File descriptorOutputDir;

    private final boolean uniqueSnapshot;

    private final Set<String> checksumAlgos;

    IvyPublisherForMaven(UnaryOperator<Path> signer, RepositoryResolver dependencyResolver,
                         File descriptorOutputDir, boolean uniqueSnapshot, Set<String> checksumAlgos) {
        super();
        this.resolver = dependencyResolver;
        this.descriptorOutputDir = descriptorOutputDir;
        this.signer = signer;
        this.uniqueSnapshot = uniqueSnapshot;
        this.checksumAlgos = checksumAlgos;
    }

    void publish(DefaultModuleDescriptor moduleDescriptor, JkMavenPublication publication) {
        final ModuleRevisionId ivyModuleRevisionId = moduleDescriptor.getModuleRevisionId();
        try {
            resolver.beginPublishTransaction(ivyModuleRevisionId, true);
        } catch (final IOException e) {
            throw new RuntimeException(e);
        }

        // publish artifacts
        final JkVersionedModule versionedModule = IvyTranslations
                .toJkVersionedModule(ivyModuleRevisionId);
        final MavenMetadata returnedMetaData = publish(versionedModule, publication);

        // publish pom
        final Path pomXml = makePom(moduleDescriptor, publication);
        final String version;
        if (versionedModule.version().isSnapshot() && this.uniqueSnapshot) {
            final String path = snapshotMetadataPath(versionedModule);
            final MavenMetadata mavenMetadata = JkUtilsObject.firstNonNull(loadMavenMedatata(path),
                    returnedMetaData);
            final Snapshot snap = mavenMetadata.currentSnapshot();
            version = versionForUniqueSnapshot(versionedModule.version().value(), snap.timestamp,
                    snap.buildNumber);
            final String pomDest = destination(versionedModule, "pom", null, version);
            putInRepo(pomXml, pomDest, true);
            mavenMetadata.addSnapshotVersion("pom", null);
            push(mavenMetadata, path);
        } else {
            version = versionedModule.version().value();
            final String pomDest = destination(versionedModule, "pom", null, version);
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

    private MavenMetadata publish(JkVersionedModule versionedModule,
            JkMavenPublication mavenPublication) {
        if (!versionedModule.version().isSnapshot()) {
            final String existing = checkNotExist(versionedModule, mavenPublication);
            if (existing != null) {
                throw new IllegalArgumentException("Artifact " + existing
                        + " already exists on repo.");
            }
        }
        if (versionedModule.version().isSnapshot() && this.uniqueSnapshot) {
            final String path = snapshotMetadataPath(versionedModule);
            MavenMetadata mavenMetadata = loadMavenMedatata(path);
            final String timestamp = JkUtilsTime.nowUtc("yyyyMMdd.HHmmss");
            if (mavenMetadata == null) {
                mavenMetadata = MavenMetadata.of(versionedModule, timestamp);
            }
            mavenMetadata.updateSnapshot(timestamp);
            push(mavenMetadata, path);
            final int buildNumber = mavenMetadata.currentBuildNumber();

            final String versionUniqueSnapshot = versionForUniqueSnapshot(versionedModule.version()
                    .value(), timestamp, buildNumber);

            for (final Path file : mavenPublication.mainArtifactFiles()) {
                publishUniqueSnapshot(versionedModule, null, file, versionUniqueSnapshot,
                        mavenMetadata);
            }
            for (final JkClassifiedFileArtifact classifiedArtifact : mavenPublication
                    .classifiedArtifacts()) {
                publishUniqueSnapshot(versionedModule, classifiedArtifact.classifier(),
                        classifiedArtifact.file(), versionUniqueSnapshot, mavenMetadata);
            }
            return mavenMetadata;
        } else {
            for (final Path file : mavenPublication.mainArtifactFiles()) {
                publishNormal(versionedModule, null, file);
            }
            for (final JkClassifiedFileArtifact classifiedArtifact : mavenPublication
                    .classifiedArtifacts()) {
                publishNormal(versionedModule, classifiedArtifact.classifier(),
                        classifiedArtifact.file());
            }
            return null;
        }
    }

    private Path makePom(ModuleDescriptor moduleDescriptor, JkMavenPublication publication) {
        final ModuleRevisionId ivyModuleRevisionId = moduleDescriptor.getModuleRevisionId();
        final String artifactName = ivyModuleRevisionId.getName();
        final Path pomXml;
        if (this.descriptorOutputDir != null) {
            pomXml = Paths.get(targetDir()).resolve("published-pom-" + ivyModuleRevisionId.getOrganisation()
            + "-" + artifactName + "-" + ivyModuleRevisionId.getRevision() + ".xml");
        } else {
            pomXml = JkUtilsPath.createTempFile("published-pom-", ".xml");
        }
        final String packaging = JkUtilsString.substringAfterLast(publication.mainArtifactFiles()
                .get(0).getFileName().toString(), ".");
        final PomWriterOptions pomWriterOptions = new PomWriterOptions();
        pomWriterOptions.setArtifactPackaging(packaging);
        Path fileToDelete = null;
        if (publication.extraInfo() != null) {
            final Path template = PomTemplateGenerator.generateTemplate(publication.extraInfo());
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

    private String checkNotExist(JkVersionedModule versionedModule,
            JkMavenPublication mavenPublication) {
        if (!mavenPublication.mainArtifactFiles().isEmpty()) {
            final String pomDest = destination(versionedModule, "pom", null);
            if (existOnRepo(pomDest)) {
                throw new IllegalArgumentException("The main artifact as already exist for "
                        + versionedModule);
            }
            for (final Path file : mavenPublication.mainArtifactFiles()) {
                final String ext = JkUtilsString.substringAfterLast(file.getFileName().toString(), ".");
                final String dest = destination(versionedModule, ext, null);
                if (existOnRepo(dest)) {
                    return dest;
                }
            }
        }
        for (final JkClassifiedFileArtifact classifiedArtifact : mavenPublication.classifiedArtifacts()) {
            final String ext = JkUtilsString.substringAfterLast(
                    classifiedArtifact.file().getFileName().toString(), ".");
            final String dest = destination(versionedModule, ext, classifiedArtifact.classifier());
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

    private void publishUniqueSnapshot(JkVersionedModule versionedModule, String classifier,
            Path source, String versionForUniqueSpshot, MavenMetadata mavenMetadata) {

        final String extension = JkUtilsString.substringAfterLast(source.getFileName().toString(), ".");
        final String dest = destination(versionedModule, extension, classifier,
                versionForUniqueSpshot);
        putInRepo(source, dest, false);
        final String path = snapshotMetadataPath(versionedModule);
        mavenMetadata.addSnapshotVersion(extension, classifier);
        push(mavenMetadata, path);
    }

    private void publishNormal(JkVersionedModule versionedModule, String classifier, Path source) {

        final String extension = JkUtilsString.substringAfterLast(source.getFileName().toString(), ".");
        final String version = versionedModule.version().value();
        final String dest = destination(versionedModule.withVersion(version), extension, classifier);
        final boolean overwrite = versionedModule.version().isSnapshot();
        putInRepo(source, dest, overwrite);
    }

    private static String destination(JkVersionedModule versionedModule, String ext,
            String classifier) {
        return destination(versionedModule, ext, classifier, versionedModule.version().value());
    }

    private static String destination(JkVersionedModule versionedModule, String ext,
            String classifier, String uniqueVersion) {
        final JkModuleId moduleId = versionedModule.moduleId();
        final String version = versionedModule.version().value();
        final StringBuilder result = new StringBuilder(moduleBasePath(moduleId)).append("/")
                .append(version).append("/").append(moduleId.getName()).append("-")
                .append(uniqueVersion);
        if (classifier != null) {
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
        MavenMetadata mavenMetadata = loadMavenMedatata(path);
        if (mavenMetadata == null) {
            mavenMetadata = MavenMetadata.of(JkModuleId.of(moduleId.getOrganisation(),
                    moduleId.getName()));
        }
        mavenMetadata.addVersion(version, timestamp);
        push(mavenMetadata, path);
    }

    private void push(MavenMetadata metadata, String path) {
        final Path file = JkUtilsPath.createTempFile("metadata-", ".xml");

        try (OutputStream outputStream = Files.newOutputStream(file)) {
            metadata.output(outputStream);
            outputStream.flush();
        } catch (final IOException e) {
            throw new UncheckedIOException(e);
        }
        putInRepo(file, path, true);
    }

    private static JkVersionedModule of(ModuleId moduleId, String version) {
        return JkVersionedModule.of(JkModuleId.of(moduleId.getOrganisation(), moduleId.getName()),
                JkVersion.of(version));
    }

    private static String versionMetadataPath(JkVersionedModule module) {
        return moduleBasePath(module.moduleId()) + "/maven-metadata.xml";
    }

    private static String moduleBasePath(JkModuleId module) {
        return module.getGroup().replace(".", "/") + "/" + module.getName();
    }

    private static String snapshotMetadataPath(JkVersionedModule module) {
        return moduleBasePath(module.moduleId()) + "/" + module.version().value()
                + "/maven-metadata.xml";
    }

    private MavenMetadata loadMavenMedatata(String path) {
        try {
            final Resource resource = resolver.getRepository().getResource(completePath(path));
            if (resource.exists()) {
                final InputStream inputStream = resource.openStream();
                final MavenMetadata mavenMetadata = MavenMetadata.of(inputStream);
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
        try {
            final String dest = completePath(destination);
            JkLog.info("Publish file " + dest);
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
                final String signedDest = destination + ".asc";
                JkLog.info("Publish file " + signedDest);
                repository.put(null, signed.toFile(), signedDest, overwrite);
            }
        } catch (final IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private String targetDir() {
        return this.descriptorOutputDir.getAbsolutePath();
    }

    private static void commitPublication(DependencyResolver resolver) {
        try {
            resolver.commitPublishTransaction();
        } catch (final Exception e) {
            throw JkUtilsThrowable.unchecked(e);
        }
    }

}
