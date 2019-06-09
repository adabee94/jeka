package org.jerkar.api.ide.eclipse;


import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import org.jerkar.api.ide.eclipse.DotClasspathModel.ClasspathEntry;
import org.jerkar.api.ide.eclipse.DotClasspathModel.ClasspathEntry.Kind;
import dev.jeka.core.api.system.JkLog;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

class WstCommonComponent {

    public static final String FILE = ".settings/org.eclipse.wst.common.component";

    private static final String VAR_PREFIX = "module:/classpath/var/";

    private static final String LIB_PREFIX = "module:/classpath/lib/";

    public static final WstCommonComponent of(Path projectDir) {
        return new WstCommonComponent(parse(projectDir));
    }

    private final List<ClasspathEntry> dependentModules;

    private WstCommonComponent(List<ClasspathEntry> files) {
        this.dependentModules = Collections.unmodifiableList(files);
    }

    public List<ClasspathEntry> dependentModules() {
        return this.dependentModules;
    }

    public boolean contains(ClasspathEntry candidate) {
        for (final ClasspathEntry entry : this.dependentModules) {
            if (entry.sameTypeAndPath(candidate)) {
                return true;
            }
        }
        return false;
    }

    public static final boolean existIn(Path projectDir) {
        return Files.exists(componentFile(projectDir));
    }

    private static Path componentFile(Path projectDir) {
        return projectDir.resolve(FILE);
    }

    private static List<ClasspathEntry> parse(Path projectDir) {
        final Path file = componentFile(projectDir);
        if (!Files.exists(file)) {
            throw new IllegalStateException("Can't find " + FILE);
        }
        final Document document = getFileAsDom(file);
        return from(document);
    }

    private static Document getFileAsDom(Path file) {
        final DocumentBuilderFactory dbFactory = DocumentBuilderFactory.newInstance();
        try {
            final DocumentBuilder dBuilder = dbFactory.newDocumentBuilder();
            Document doc;
            doc = dBuilder.parse(file.toFile());
            doc.getDocumentElement().normalize();
            return doc;
        } catch (final Exception e) {
            throw new RuntimeException("Error while parsing " + FILE + " : ", e);
        }
    }

    private static List<ClasspathEntry> from(Document document) {
        final NodeList nodeList = document.getElementsByTagName("dependent-module");
        final List<ClasspathEntry> result = new LinkedList<>();
        for (int i = 0; i < nodeList.getLength(); i++) {
            final Node node = nodeList.item(i);
            final Element element = (Element) node;
            final String handle = element.getAttribute("handle");
            final ClasspathEntry entry = handleToFile(handle);
            if (entry != null) {
                result.add(entry);
            }
        }
        return result;
    }

    private static ClasspathEntry handleToFile(String handle) {
        final ClasspathEntry result;
        if (handle.startsWith(VAR_PREFIX)) {
            final String rest = handle.substring(VAR_PREFIX.length());
            return ClasspathEntry.of(ClasspathEntry.Kind.VAR, rest);
        } else if (handle.startsWith(LIB_PREFIX)) {
            final String rest = handle.substring(LIB_PREFIX.length());
            return ClasspathEntry.of(Kind.LIB, rest);
        } else {
            JkLog.warn("Ignoring handle " + handle);
            result = null;
        }
        return result;
    }

}
