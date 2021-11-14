package dev.jeka.core.tool;

import dev.jeka.core.api.depmanagement.JkDependency;
import dev.jeka.core.api.depmanagement.JkFileSystemDependency;
import dev.jeka.core.api.depmanagement.JkModuleDependency;
import dev.jeka.core.api.system.JkInfo;
import dev.jeka.core.api.utils.JkUtilsAssert;
import dev.jeka.core.api.utils.JkUtilsString;
import dev.jeka.core.api.utils.JkUtilsSystem;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

/*
 * Holds information carried by the command line.
 */
// TODO suppress 'option' concept in favor of System properties and KBean properties
final class CommandLine {

    private static final String KBEAN_SYMBOL = "#";

    private static final char KBEAN_SYMBOL_CHAR =  KBEAN_SYMBOL.charAt(0);

    private static final String AT_SYMBOL_CHAR = "@";

    private Map<String, String> commandOptions;

    private Map<String, String> systemProperties;

    private List<MethodInvocation> masterMethods;

    private List<MethodInvocation> subProjectMethods;

    private List<JkBeanOptions> jkBeanOptions;

    private List<JkDependency> defDependencies;

    private String[] rawArgs;

    private CommandLine() {
        super();
    }

    static CommandLine parse(String[] words) {
        final CommandLine result = new CommandLine();
        result.commandOptions = extractOptions(words);
        result.systemProperties = extractSystemProperties(words);
        result.masterMethods = extractMethods(words, true);
        result.subProjectMethods = extractMethods(words, false);
        result.jkBeanOptions = extractPluginOptions(words);
        result.defDependencies = dependencies(words);
        result.rawArgs = words;
        return result;
    }


    private static List<JkDependency> dependencies(String[] words) {
        final List<JkDependency> result = new LinkedList<>();
        for (final String word : words) {
            if (word.startsWith(AT_SYMBOL_CHAR)) {
                    result.add(toModuleDependency(word.substring(1)));
            }
        }
        return result;
    }

    private static JkDependency toModuleDependency(String depDescription) {
        boolean hasDoubleDotes = JkModuleDependency.isModuleDependencyDescription(depDescription);
        if (!hasDoubleDotes || (JkUtilsSystem.IS_WINDOWS && depDescription.substring(1).startsWith(":\\"))) {
            Path candidatePath = Paths.get(depDescription);
            if (Files.exists(candidatePath)) {
                return JkFileSystemDependency.of(candidatePath);
            } else {
                throw new JkException("Command line argument "
                        + depDescription + " cannot be recognized as a file. " +
                        "Is " + candidatePath.toAbsolutePath() + " an existing file ?");
            }
        } else {
            JkModuleDependency moduleDependency = JkModuleDependency.of(depDescription);
            boolean specifiedVersion = !moduleDependency.hasUnspecifiedVersion();
            if (!specifiedVersion && moduleDependency.getModuleId().getGroup().equals("dev.jeka")) {
                moduleDependency = moduleDependency.withVersion(JkInfo.getJekaVersion());
                return moduleDependency;
            } else {
                throw new JkException("Command line argument "
                        + depDescription + " does not mention a version. " +
                        "Use description as groupId:artefactId:version. Version can be '+' for taking the latest.");
            }
        }
    }

    private static List<MethodInvocation> extractMethods(String[] words, boolean master) {
        final List<MethodInvocation> result = new LinkedList<>();
        for (final String word : words) {
            if (!word.startsWith("-") && !word.startsWith("@") && !word.endsWith(KBEAN_SYMBOL)) {
                result.add(MethodInvocation.parse(word));
            }
        }
        return result;
    }

    private static Map<String, String> extractOptions(String[] words) {
        final Map<String, String> result = new HashMap<>();
        for (final String word : words) {
            if (word.startsWith("-") && !word.startsWith("-D")) {
                final int equalIndex = word.indexOf("=");
                if (equalIndex <= -1) { // no '=' so we just associate the key with a null value
                    final String key = word.substring(1);
                    if (!key.contains(KBEAN_SYMBOL)) { // if '#' is present
                        result.put(key, null);
                    }
                } else {
                    final String key = word.substring(1, equalIndex);
                    if (!key.contains(KBEAN_SYMBOL)) {
                        final String value = word.substring(equalIndex + 1);
                        result.put(key, value);
                    }
                }
            }
        }
        return Collections.unmodifiableMap(result);
    }

    private static Map<String, String> extractSystemProperties(String[] args) {
        final Map<String, String> result = new HashMap<>();
        for (final String arg : args) {
            if (arg.startsWith("-D")) {
                final int equalIndex = arg.indexOf("=");
                if (equalIndex <= -1) {
                    result.put(arg.substring(2), "");
                } else {
                    final String name = arg.substring(2, equalIndex);
                    final String value = arg.substring(equalIndex + 1);
                    result.put(name, value);
                }
            }
        }
        return Collections.unmodifiableMap(result);
    }

    private static List<JkBeanOptions> extractPluginOptions(String[] words) {
        final Map<String, JkBeanOptions> setups = new LinkedHashMap<>();
        for (final String word : words) {
            if (MethodInvocation.isPluginMethodInvokation(word)) {
                final String pluginName = JkUtilsString.substringBeforeFirst(word, KBEAN_SYMBOL);
                if (!setups.containsKey(pluginName)) {
                    setups.put(pluginName, JkBeanOptions.of(pluginName));
                }
            } else if (MethodInvocation.isPluginActivation(word)) {
                final String pluginName = JkUtilsString.substringBeforeFirst(word, KBEAN_SYMBOL);
                final JkBeanOptions setup = setups.get(pluginName);
                if (setup == null) {
                    setups.put(pluginName, JkBeanOptions.of(pluginName));
                } else {
                    setups.put(pluginName, setup);
                }
            } else if (isPluginOption(word)) {
                final String pluginName = JkUtilsString.substringBeforeFirst(word, KBEAN_SYMBOL)
                        .substring(1);
                final JkBeanOptions setup = setups.computeIfAbsent(pluginName, n -> JkBeanOptions.of(n));
                final int equalIndex = word.indexOf("=");
                if (equalIndex <= -1) {
                    final String key = JkUtilsString.substringAfterFirst(word, KBEAN_SYMBOL);
                    setups.put(pluginName, setup.with(key, null));
                } else {
                    final String key = JkUtilsString.substringBeforeFirst(
                            JkUtilsString.substringAfterFirst(word, KBEAN_SYMBOL), "=");
                    final String value = word.substring(equalIndex + 1);
                    setups.put(pluginName, setup.with(key, value));
                }
            }
        }
        return new LinkedList<>(setups.values());
    }

    private static boolean isPluginOption(String word) {
        return word.startsWith("-") && word.indexOf(KBEAN_SYMBOL) > 2;
    }

    static final class MethodInvocation {

        public final String methodName;

        public final String beanName;

        private MethodInvocation(String methodName, String beanName) {
            super();
            JkUtilsAssert.argument(methodName != null && !methodName.isEmpty(),
                    "PluginName can' t be null or empty");
            this.methodName = methodName;
            this.beanName = beanName;
        }

        static MethodInvocation parse(String word) {
            if (isPluginMethodInvokation(word)) {
                return pluginMethod(JkUtilsString.substringBeforeFirst(word, KBEAN_SYMBOL),
                        JkUtilsString.substringAfterLast(word, KBEAN_SYMBOL));
            }
            return normal(word);
        }

        static MethodInvocation normal(String name) {
            return new MethodInvocation(name, null);
        }

        static MethodInvocation pluginMethod(String pluginName, String methodName) {
            JkUtilsAssert.argument(pluginName != null && !pluginName.isEmpty(),
                    "PluginName can't be null or empty");
            return new MethodInvocation(methodName, pluginName);
        }

        private static boolean isPluginMethodInvokation(String word) {
            if (word.startsWith("-")) {
                return false;
            }
            return JkUtilsString.countOccurrence(word, KBEAN_SYMBOL_CHAR) == 1
                    && !word.startsWith(KBEAN_SYMBOL) && !word.endsWith(KBEAN_SYMBOL);
        }

        private static boolean isPluginActivation(String word) {
            return JkUtilsString.countOccurrence(word, KBEAN_SYMBOL_CHAR) == 1
                    && !word.startsWith(KBEAN_SYMBOL) && word.endsWith(KBEAN_SYMBOL);
        }

        public boolean isMethodPlugin() {
            return beanName != null;
        }

        @Override
        public String toString() {
            if (beanName == null) {
                return methodName;
            }
            return beanName + "#" + methodName;
        }

    }

    Map<String, String> getCommandOptions() {
        return commandOptions;
    }

    Map<String, String> getSystemProperties() {
        return systemProperties;
    }

    List<MethodInvocation> getMasterMethods() {
        return masterMethods;
    }

    List<MethodInvocation> getSubProjectMethods() {
        return subProjectMethods;
    }

    List<JkBeanOptions> getPluginOptions() {
        return jkBeanOptions;
    }

    List<JkDependency> getDefDependencies() {
        return this.defDependencies;
    }

    String[] rawArgs() {
        return rawArgs;
    }

}
