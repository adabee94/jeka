package org.jerkar.api.depmanagement;

import java.io.File;
import java.io.Serializable;
import java.util.Arrays;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;

import org.jerkar.api.utils.JkUtilsIterable;

public final class JkRepos implements Iterable<JkRepo>, Serializable {

    private static final long serialVersionUID = 1L;

    public static JkRepos of(JkRepo... jkRepositories) {
        return new JkRepos(Arrays.asList(jkRepositories));
    }

    public static JkRepos maven(String... urls) {
        final List<JkRepo> list = new LinkedList<JkRepo>();
        for (final String url : urls) {
            list.add(JkRepo.maven(url));
        }
        return new JkRepos(list);
    }

    public static JkRepos maven(File... files) {
        final List<JkRepo> list = new LinkedList<JkRepo>();
        for (final File file : files) {
            list.add(JkRepo.maven(file));
        }
        return new JkRepos(list);
    }

    public static JkRepos ivy(String... urls) {
        final List<JkRepo> list = new LinkedList<JkRepo>();
        for (final String url : urls) {
            list.add(JkRepo.ivy(url));
        }
        return new JkRepos(list);
    }

    public static JkRepos ivy(File... files) {
        final List<JkRepo> list = new LinkedList<JkRepo>();
        for (final File file : files) {
            list.add(JkRepo.ivy(file));
        }
        return new JkRepos(list);
    }

    public static JkRepos mavenCentral() {
        return new JkRepos(JkUtilsIterable.listOf(JkRepo.mavenCentral()));
    }

    public static JkRepos mavenJCenter() {
        return new JkRepos(JkUtilsIterable.listOf(JkRepo.mavenJCenter()));
    }

    private final List<JkRepo> repos;

    private JkRepos(List<JkRepo> repos) {
        super();
        this.repos = Collections.unmodifiableList(repos);
    }

    public JkRepos and(Iterable<JkRepo> jkRepos) {
        return and(JkUtilsIterable.arrayOf(jkRepos, JkRepo.class));
    }

    public JkRepos and(JkRepo... jkRepoArray) {
        final List<JkRepo> list = new LinkedList<JkRepo>(this.repos);
        list.addAll(Arrays.asList(jkRepoArray));
        return new JkRepos(list);
    }

    public JkRepos andIf(boolean condition, JkRepo... repos) {
        if (condition) {
            return and(repos);
        }
        return this;
    }

    public JkRepos andIfEmpty(JkRepo... repos) {
        return andIfEmpty(Arrays.asList(repos));
    }

    public JkRepos andIfEmpty(Iterable<JkRepo> repos) {
        if (this.isEmpty()) {
            return and(repos);
        }
        return this;
    }

    public JkRepos andIvy(File... files) {
        final List<JkRepo> list = new LinkedList<JkRepo>(this.repos);
        list.addAll(JkRepos.ivy(files).repos);
        return new JkRepos(list);
    }

    public JkRepos andMaven(File... files) {
        final List<JkRepo> list = new LinkedList<JkRepo>(this.repos);
        list.addAll(maven(files).repos);
        return new JkRepos(list);
    }

    public JkRepos andMavenCentral() {
        final List<JkRepo> list = new LinkedList<JkRepo>(this.repos);
        list.add(JkRepo.mavenCentral());
        return new JkRepos(list);
    }

    public JkRepos andMavenJCenter() {
        final List<JkRepo> list = new LinkedList<JkRepo>(this.repos);
        list.add(JkRepo.mavenJCenter());
        return new JkRepos(list);
    }

    public boolean isEmpty() {
        return this.repos.isEmpty();
    }

    @Override
    public Iterator<JkRepo> iterator() {
        return repos.iterator();
    }

    @Override
    public String toString() {
        return repos.toString();
    }

    /**
     * Retrieve directly the file embodying the specified the external dependency.
     */
    public File get(JkModuleDependency moduleDependency) {
        return JkDependencyResolver.get(this, moduleDependency);
    }



}
