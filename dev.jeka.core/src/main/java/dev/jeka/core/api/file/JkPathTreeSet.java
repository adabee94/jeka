package dev.jeka.core.api.file;

import dev.jeka.core.api.utils.JkUtilsIterable;
import dev.jeka.core.api.utils.JkUtilsPath;

import java.io.Closeable;
import java.nio.file.CopyOption;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.util.*;

/**
 * A set of {@link JkPathTree}.
 *
 * @author Jerome Angibaud
 */
public final class JkPathTreeSet implements Closeable {

    private final List<JkPathTree> pathTrees;

    private JkPathTreeSet(List<JkPathTree> dirs) {
        if (dirs == null) {
            throw new IllegalArgumentException("dirs can't be null.");
        }
        this.pathTrees = Collections.unmodifiableList(dirs);
    }

    /**
     * Creates a {@link JkPathTreeSet} from an iterable of {@link JkPathTree}.
     */
    public static JkPathTreeSet of(Iterable<JkPathTree> dirs) {
        return new JkPathTreeSet(JkUtilsIterable.listOf(dirs));
    }

    /**
     * Creates an empty {@link JkPathTreeSet}.
     */
    public static JkPathTreeSet ofEmpty() {
        return new JkPathTreeSet(Collections.emptyList());
    }

    /**
     * Creates a {@link JkPathTreeSet} to an array of {@link JkPathTree}.
     */
    public static JkPathTreeSet of(JkPathTree... trees) {
        return new JkPathTreeSet(Arrays.asList(trees));
    }

    /**
     * Creates a {@link JkPathTreeSet} from an array of folder.
     */
    public static JkPathTreeSet ofRoots(Path... folders) {
        final List<JkPathTree> dirs = new ArrayList<>(folders.length);
        for (final Path folder : folders) {
            dirs.add(JkPathTree.of(folder));
        }
        return new JkPathTreeSet(dirs);
    }

    /**
     * Creates a {@link JkPathTreeSet} from a {@link JkPathSequence}.
     */
    public static JkPathTreeSet ofRoots(List<Path> paths) {
        return ofRoots(paths.toArray(new Path[0]));
    }

    // -------------------------- additional elements in set ----------------------------------

    /**
     * Creates a {@link JkPathTreeSet} which is a concatenation of this
     * {@link JkPathTreeSet} and the {@link JkPathTree} array passed as
     * parameter.
     */
    public final JkPathTreeSet and(JkPathTree... trees) {
        final List<JkPathTree> list = new LinkedList<>(this.pathTrees);
        list.addAll(Arrays.asList(trees));
        return new JkPathTreeSet(list);
    }

    /**
     * Creates a {@link JkPathTreeSet} which is a concatenation of this
     * {@link JkPathTreeSet} and zip files passed as parameter.
     */
    public final JkPathTreeSet andZips(Iterable<Path> zipFiles) {
        Iterable<Path> paths = JkUtilsPath.disambiguate(zipFiles);
        final List<JkPathTree> list = new LinkedList<>(this.pathTrees);
        paths.forEach(zipFile -> list.add(JkZipTree.of(zipFile)));
        return new JkPathTreeSet(list);
    }

    /**
     * @see #andZips(Iterable)
     */
    public final JkPathTreeSet andZip(Path... zips) {
        return andZips(Arrays.asList(zips));
    }

    /**
     * Creates a {@link JkPathTreeSet} which is a concatenation of this
     * {@link JkPathTreeSet} and the folder array passed as parameter.
     */
    public final JkPathTreeSet and(Path... folders) {
        final List<JkPathTree> dirs = new ArrayList<>(folders.length);
        for (final Path folder : folders) {
            dirs.add(JkPathTree.of(folder));
        }
        return this.and(dirs.toArray(new JkPathTree[folders.length]));
    }

    /**
     * Creates a {@link JkPathTreeSet} which is a concatenation of this
     * {@link JkPathTreeSet} and the {@link JkPathTreeSet} array passed as
     * parameter.
     */
    public final JkPathTreeSet and(JkPathTreeSet... otherDirSets) {
        final List<JkPathTree> list = new LinkedList<>(this.pathTrees);
        for (final JkPathTreeSet otherDirSet : otherDirSets) {
            list.addAll(otherDirSet.pathTrees);
        }
        return new JkPathTreeSet(list);
    }

    // ------------------------ additional filters -------------------------------------------

    /**
     * Creates a {@link JkPathTree} which is a copy of this {@link JkPathTree}
     * augmented with the specified {@link JkPathMatcher}
     */
    public JkPathTreeSet andMatcher(PathMatcher matcher) {
        final List<JkPathTree> list = new LinkedList<>();
        for (final JkPathTree tree : this.pathTrees) {
            list.add(tree.andMatcher(matcher));
        }
        return new JkPathTreeSet(list);
    }

    // ------------------------- Replacing filter ----------------------------------

    /**
     * Creates a {@link JkPathTree} which is a copy of this {@link JkPathTreeSet}
     * replacing matcher by the specified one.
     */
    public JkPathTreeSet withMatcher(PathMatcher matcher) {
        final List<JkPathTree> list = new LinkedList<>();
        for (final JkPathTree tree : this.pathTrees) {
            list.add(JkPathTree.of(tree.getRoot()).withMatcher(JkPathMatcher.of(matcher)));
        }
        return new JkPathTreeSet(list);
    }

    // ---------------------------- iterate over files -----------------------------------

    /**
     * Returns a concatenation of {@link #getFiles()} for all trees involved in this set.
     */
    public List<Path> getFiles() {
        final LinkedList<Path> result = new LinkedList<>();
        for (final JkPathTree dirView : this.pathTrees) {
            if (dirView.exists()) {
                result.addAll(dirView.getFiles());
            }
        }
        return result;
    }

    /**
     * Returns a concatenation of {@link #getRelativeFiles()} () for all trees involved in this set.
     */
    public List<Path> getRelativeFiles() {
        final LinkedList<Path> result = new LinkedList<>();
        for (final JkPathTree dir : this.pathTrees) {
            if (dir.exists()) {
                result.addAll(dir.getRelativeFiles());
            }
        }
        return result;
    }

    /**
     * Returns a list of existing files having the specified relative path to its <code>JkPathTree</code> root.
     */
    public List<Path> getExistingFiles(String relativePath) {
        List<Path> result = new LinkedList<>();
        for (JkPathTree pathTree : this.pathTrees) {
            Path candidate = pathTree.get(relativePath);
            if (Files.exists(candidate)) {
                result.add(candidate);
            }
        }
        return result;
    }

    // ----------------------- write out ---------------------------------------------

    /**
     * Zips the content of all trees involved in this set.
     */
    public JkPathTreeSet zipTo(Path dir) {
        this.pathTrees.forEach(tree -> tree.zipTo(dir));
        return this;
    }

    /**
     * Copies the content of all trees involved in this set.
     */
    public JkPathTreeSet copyTo(Path dir, CopyOption... copyOptions) {
        this.pathTrees.forEach(tree -> tree.copyTo(dir, copyOptions));
        return this;
    }


    // -------------------------------------- iterates over trees ----------------------

    /**
     * Returns {@link JkPathTree} instances constituting this {@link JkPathTreeSet}.
     */
    public List<JkPathTree> toList() {
        return pathTrees;
    }

    /**
     * Returns root dir or zip file for each {@link JkPathTree} tree involved in this
     * {@link JkPathTreeSet}.
     */
    public List<Path> getRootDirsOrZipFiles() {
        final List<Path> result = new LinkedList<>();
        for (final JkPathTree tree : pathTrees) {
            result.add(tree.getRoot());
        }
        return result;
    }

    // ------------------ Misc -----------------------------------------

    /**
     * Returns <code>true</code> if no tree of this set has an existing baseTree.
     */
    public boolean hasNoExistingRoot() {
        for (final JkPathTree tree : pathTrees) {
            if (tree.exists()) {
                return false;
            }
        }
        return true;
    }

    /**
     * @see JkPathTree#resolvedTo(Path)
     */
    public JkPathTreeSet resolvedTo(Path newRoot) {
        List<JkPathTree> trees = new LinkedList<>();
        this.toList().forEach(tree -> trees.add(tree.resolvedTo(newRoot)));
        return JkPathTreeSet.of(trees);
    }

    /**
     * See {@link JkPathTree#count(int, boolean)}
     */
    public int count(int max, boolean includeFolder) {
        int result = 0;
        for (final JkPathTree dirView : pathTrees) {
            result += dirView.count(max - result, includeFolder);
        }
        return result;
    }

    public boolean containFiles() {
        return count(1, false) > 0;
    }

    /**
     * Merges trees having same root by comparing their respective matcher.
     */
    public JkPathTreeSet mergeDuplicateRoots() {
        List<JkPathTree> result = new ArrayList<>();
        for (JkPathTree tree : pathTrees) {
            Path root = tree.getRoot();
            boolean found = false;
            for (int i = 0; i < result.size(); i++) {
                JkPathTree resultTree = result.get(i);
                if (root.equals(resultTree.getRoot())) {
                    result.remove(i);
                    found = true;
                    JkPathMatcher pathMatcher = resultTree.getMatcher().or(tree.getMatcher());
                    resultTree = resultTree.withMatcher(pathMatcher);
                    result.add(i, resultTree);
                    break;
                }
            }
            if (!found) {
                result.add(tree);
            }
        }
        return JkPathTreeSet.of(result);
    }

    @Override
    public String toString() {
        return this.pathTrees.toString();
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + ((pathTrees == null) ? 0 : pathTrees.hashCode());
        return result;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null) {
            return false;
        }
        if (getClass() != obj.getClass()) {
            return false;
        }
        final JkPathTreeSet other = (JkPathTreeSet) obj;
        if (pathTrees == null) {
            return other.pathTrees == null;
        } else return pathTrees.equals(other.pathTrees);
    }

    @Override
    public void close()  {
        this.pathTrees.stream()
                .filter(JkZipTree.class::isInstance)
                .map(JkZipTree.class::cast)
                .forEach(JkZipTree::close);
    }
}
