package dev.jeka.plugins.springboot;

import dev.jeka.core.api.file.JkPathSequence;
import dev.jeka.core.api.file.JkZipTree;
import dev.jeka.core.api.java.JkManifest;
import dev.jeka.core.api.utils.JkUtilsObject;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;

class SpringbootPacker {

    private final JkPathSequence nestedLibs;

    private final Path bootLoaderJar;

    private final JkManifest manifestToMerge;

    private final String mainClassName;

    private SpringbootPacker(JkPathSequence nestedLibs, Path loader, String mainClassNeme, JkManifest manifestToMerge) {
        super();
        this.nestedLibs = nestedLibs;
        this.bootLoaderJar = loader;
        this.manifestToMerge = manifestToMerge;
        this.mainClassName = mainClassNeme;
    }

    public static final SpringbootPacker of(JkPathSequence nestedLibs, Path loader, String mainClassName,
                                            String springbootVersion) {
        return new SpringbootPacker(nestedLibs, loader, mainClassName, null);
    }

    public void makeExecJar(Path original, Path target) {
        try {
            makeBootJarChecked(original, target);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private void makeBootJarChecked(Path original, Path target) throws IOException {

        JarWriter jarWriter = new JarWriter(target);

        // Manifest
        try (JkZipTree zipTree = JkZipTree.of(original)) {
            Path path = zipTree.goTo("META-INF").get("MANIFEST.MF");
            final JkManifest manifest = Files.exists(path) ? JkManifest.of().loadFromFile(path) : JkManifest.of();
            jarWriter.writeManifest(createManifest(manifest, mainClassName).getManifest());
        }

        // Add nested jars
        for (Path nestedJar : this.nestedLibs.withoutDuplicates()) {
            jarWriter.writeNestedLibrary("BOOT-INF/lib/", nestedJar);
        }

        // Add loader
        jarWriter.writeLoaderClasses(bootLoaderJar.toUri().toURL());

        // Add original jar
        writeClasses(original, jarWriter);

        jarWriter.close();
        jarWriter.setExecutableFilePermission(target);
    }

    private void writeClasses(Path original, JarWriter jarWriter) {
        JkZipTree originalJar = JkZipTree.of(original);
        Stream<Path> stream = originalJar.stream();
        stream
                .filter(path -> !path.toString().endsWith("/"))
                .filter(path -> !Files.isDirectory(path))
                .forEach(path -> {
                    String entryName = "BOOT-INF/classes" + path.toString();
                    try (InputStream inputStream = Files.newInputStream(path)){
                        jarWriter.writeEntry(entryName, inputStream);
                    } catch (IOException e) {
                        throw new UncheckedIOException(e);
                    }
                });
    }

    private JkManifest createManifest(JkManifest original, String startClassName) {
        JkManifest result = JkUtilsObject.firstNonNull(original, JkManifest.of())
            .addMainClass("org.springframework.boot.loader.JarLauncher")
            .addMainAttribute("Start-Class", startClassName)
            .addMainAttribute("Spring-Boot-Classes", "BOOT-INF/classes/")
            .addMainAttribute("Spring-Boot-Lib", "BOOT-INF/lib/");
        result.addContextualInfo();
        if (this.manifestToMerge != null) {
            result.merge(manifestToMerge.getManifest());
        }
        return result;
    }

}
