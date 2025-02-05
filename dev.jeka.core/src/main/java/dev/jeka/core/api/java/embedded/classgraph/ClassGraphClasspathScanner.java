package dev.jeka.core.api.java.embedded.classgraph;

import dev.jeka.core.api.file.JkPathSequence;
import dev.jeka.core.api.java.JkInternalClasspathScanner;
import dev.jeka.core.api.utils.JkUtilsPath;
import io.github.classgraph.*;

import java.io.File;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;

class ClassGraphClasspathScanner implements JkInternalClasspathScanner {

    static ClassGraphClasspathScanner of() {
        return new ClassGraphClasspathScanner();
    }

    @Override
    public Set<Class<?>> loadClassesHavingSimpleNameMatching(Predicate<String> predicate) {
        return loadClassesMatching(hasSimpleName(predicate), false);
    }

    @Override
    public <T> Class<T> loadFirstFoundClassHavingNameOrSimpleName(String name, Class<T> superClass) {
        for (Class<?> clazz : loadClassesMatching(classInfo -> classInfo.getName().equals(name), true)) {
            if (superClass.isAssignableFrom(clazz)) {
                return (Class<T>) clazz;
            }
        }
        for (Class<?> clazz : loadClassesMatching(classInfo -> classInfo.getSimpleName().equals(name), true)) {
            if (superClass.isAssignableFrom(clazz)) {
                return (Class<T>) clazz;
            }
        }
        return null;
    }

    private static Predicate<ClassInfo> hasSimpleName(Predicate<String> namePredicate) {
        return classInfo ->  namePredicate.test(classInfo.getSimpleName());

    }

    private Set<Class<?>> loadClassesMatching(Predicate<ClassInfo> predicate, boolean ignoreClassVisibility) {
        ClassGraph classGraph = new ClassGraph()
                .ignoreClassVisibility()
                .enableClassInfo()
                .blacklistPackages("java", "org.apache.ivy", "org.bouncycastle", "nonapi.io.github.classgraph",
                        "org.commonmark", "io.github.classgraph");
        if (ignoreClassVisibility) {
            classGraph = classGraph.ignoreClassVisibility();
        }
        final ScanResult scanResult = classGraph.scan();
        final Set<Class<?>> result = new HashSet<>();
        for (final ClassInfo classInfo : scanResult.getAllClasses()) {
            if (predicate.test(classInfo)) {
                result.add(classInfo.loadClass());
            }
        }
        return result;
    }

    @Override
    public List<String> findClassesHavingMainMethod(ClassLoader extraClassLoader) {
        final ClassGraph classGraph = new ClassGraph()
                .enableClassInfo()
                .enableMethodInfo()
                .overrideClassLoaders(extraClassLoader)
                .ignoreParentClassLoaders();
        final ScanResult scanResult = classGraph.scan();
        final List<String> result = new LinkedList<>();
        for (final ClassInfo classInfo : scanResult.getAllClasses()) {
            MethodInfoList methodInfoList = classInfo.getMethodInfo("main");
            for (MethodInfo methodInfo : methodInfoList) {
                if (methodInfo.isPublic() && methodInfo.isStatic() && methodInfo.getParameterInfo().length == 1) {
                    MethodParameterInfo methodParameterInfo = methodInfo.getParameterInfo()[0];
                    if (methodParameterInfo.getTypeDescriptor() instanceof ArrayTypeSignature) {
                        ArrayTypeSignature arrayTypeSignature = (ArrayTypeSignature) methodParameterInfo.getTypeDescriptor();
                        if ("java.lang.String[]".equals(arrayTypeSignature.toString())) {
                            result.add(classInfo.getName());
                        }
                    }
                }
            }
        }
        return result;
    }

    @Override
    public List<String> findClassesMatchingAnnotations(ClassLoader classloader,
                                                       Predicate<List<String>> annotationPredicate) {
        final ClassGraph classGraph = new ClassGraph()
                .enableClassInfo()
                .overrideClassLoaders(classloader)
                .enableAnnotationInfo()
                .ignoreParentClassLoaders();
        final ScanResult scanResult = classGraph.scan();
        final List<String> result = new LinkedList<>();
        for (final ClassInfo classInfo : scanResult.getAllClasses()) {
            AnnotationInfoList annotationInfoList = classInfo.getAnnotationInfo();
            List<String> annotationNames = annotationInfoList.getNames();
            if (annotationPredicate.test(annotationNames)) {
                result.add(classInfo.getName());
            }
        }
        return result;
    }

    @Override
    public List<String> findClassedExtending(ClassLoader classLoader, Class<?> baseClass,
                                             Predicate<String> classpathElementFilter, boolean ignoreVisibility,
                                             boolean ignoreParentClassLoaders) {
        ClassGraph classGraph = new ClassGraph()
                .enableClassInfo()
                .blacklistPackages("java", "org.apache.ivy", "org.bouncycastle", "nonapi.io.github.classgraph",
                        "org.commonmark", "io.github.classgraph")
                .disableNestedJarScanning()
                .disableModuleScanning()
                .filterClasspathElements(classpathElementPath -> classpathElementFilter.test(classpathElementPath));
        if (ignoreParentClassLoaders) {
            classGraph
            .ignoreParentClassLoaders()
            .overrideClassLoaders(classLoader);
        }
        if (ignoreVisibility) {
            classGraph = classGraph.ignoreClassVisibility();
        }
        final ScanResult scanResult = classGraph.scan();
        return scanResult.getAllClasses().stream()
                .filter(classInfo -> !classInfo.isAbstract())
                .filter(classInfo -> classInfo.extendsSuperclass(baseClass.getName()))
                .map(classInfo -> classInfo.getName())
                .collect(Collectors.toList());
    }

    public JkPathSequence getClasspath(ClassLoader classLoader) {
        List<File> files = new ClassGraph().scan().getClasspathFiles();
        return JkPathSequence.of(JkUtilsPath.toPaths(files));
    }


}
