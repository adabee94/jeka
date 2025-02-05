package dev.jeka.core.tool;

import dev.jeka.core.api.system.JkLog;
import dev.jeka.core.api.system.JkProperties;
import dev.jeka.core.api.utils.JkUtilsObject;
import dev.jeka.core.api.utils.JkUtilsString;

import java.nio.file.Paths;
import java.util.*;

import static dev.jeka.core.tool.JkConstants.*;

class Environment {

    private Environment() {
        // Can't be instantiated
    }

    static CommandLine commandLine = CommandLine.parse(new String[0]);

    static StandardOptions standardOptions = new StandardOptions(Collections.emptyMap());

    static String[] originalArgs;

    static void initialize(String[] commandLineArgs) {
        originalArgs = commandLineArgs;
        List<String> effectiveCommandLineArgs = new LinkedList<>(Arrays.asList(commandLineArgs));

        // Add arguments contained in cmd.properties '_append'
        JkProperties props = JkRuntime.readProjectPropertiesRecursively(Paths.get(""));
        List<String> appendedArgs = Arrays.asList(JkUtilsString.translateCommandline(props.get(CMD_APPEND_PROP)));
        effectiveCommandLineArgs.addAll(appendedArgs);

        // Interpolate arguments passed as $key to respective value
        for (ListIterator<String> it = effectiveCommandLineArgs.listIterator(); it.hasNext(); ) {
            String word = it.next();
            if (word.startsWith(CMD_SUBSTITUTE_SYMBOL)) {
                String token = word.substring(CMD_SUBSTITUTE_SYMBOL.length());
                String propName = CMD_PROP_PREFIX + token;
                String presetValue = props.get(propName);
                if (presetValue != null) {
                    String[] replacingItems = JkUtilsString.translateCommandline(presetValue);
                    it.remove();
                    Arrays.stream(replacingItems).forEach(item -> it.add(item));
                }
            }
        }
        JkLog.trace("Effective command line : " + effectiveCommandLineArgs);

        // Parse command line
        final CommandLine commandLine = CommandLine.parse(effectiveCommandLineArgs.toArray(new String[0]));

        final StandardOptions standardOptions = new StandardOptions(commandLine.getStandardOptions());
        if (standardOptions.logVerbose) {
            JkLog.setVerbosity(JkLog.Verbosity.VERBOSE);
        }
        if (standardOptions.logIvyVerbose) {
            JkLog.setVerbosity(JkLog.Verbosity.QUITE_VERBOSE);
        }
        Environment.commandLine = commandLine;
        Environment.standardOptions = standardOptions;
    }

    /**
     * By convention, standard options start with upper case.
     */
    static class StandardOptions {

        Set<String> acceptedOptions = new HashSet<>();

        boolean logIvyVerbose;

        boolean logVerbose;

        boolean logNoAnimation;

        boolean logBanner;

        boolean logDuration;

        boolean logSetup;

        boolean logStackTrace;

        JkLog.Style logStyle;

        boolean logRuntimeInformation;

        boolean ignoreCompileFail;

        private String jkBeanName;

        private boolean cleanWork;

        boolean noHelp;

        private final Set<String> names = new HashSet<>();

        StandardOptions (Map<String, String> map) {
            this.logVerbose = valueOf(boolean.class, map, false, "Log.verbose", "lv");
            this.logIvyVerbose = valueOf(boolean.class, map, false, "log.ivy.verbose", "liv");
            this.logNoAnimation = valueOf(boolean.class, map, false, "log.no.animation", "lna");
            this.logBanner = valueOf(boolean.class, map, false,"log.banner", "lb");
            this.logDuration = valueOf(boolean.class, map, false,"log.duration", "ld");
            this.logSetup = valueOf(boolean.class, map, false,"log.setup", "lsu");
            this.logStackTrace = valueOf(boolean.class, map,false, "log.stacktrace", "lst");
            this.logRuntimeInformation = valueOf(boolean.class, map, false, "log.runtime.info", "lri");
            this.logStyle = valueOf(JkLog.Style.class, map, JkLog.Style.INDENT, "log.style", "ls");
            this.jkBeanName = valueOf(String.class, map, null, "kbean", "kb");
            this.ignoreCompileFail = valueOf(boolean.class, map, false, "def.compile.ignore-failure", "dci");
            this.cleanWork = valueOf(boolean.class, map, false, "clean.work", "cw");
            this.noHelp = valueOf(boolean.class, map, false, "no.help");
        }

        String kBeanName() {
            return jkBeanName;
        }

        boolean workClean() {
            return cleanWork;
        }

        @Override
        public String toString() {
            return "JkBean" + JkUtilsObject.toString(jkBeanName) + ", LogVerbose=" + logVerbose
                    + ", LogHeaders=" + logBanner;
        }

        private <T> T valueOf(Class<T> type, Map<String, String> map, T defaultValue, String ... optionNames) {
            for (String name : optionNames) {
                acceptedOptions.add(name);
                this.names.add(name);
                if (map.containsKey(name)) {
                    String stringValue = map.get(name);
                    if (type.equals(boolean.class) && stringValue == null) {
                        return (T) Boolean.TRUE;
                    }
                    try {
                        return (T) FieldInjector.parse(type, stringValue);
                    } catch (IllegalArgumentException e) {
                        throw new JkException("Property " + name + " has been set with improper value '"
                                + stringValue + "' : " + e.getMessage());
                    }
                }
            }
            return defaultValue;
        }

    }


    static String originalCmdLineAsString() {
        return String.join(" ", originalArgs);
    }

    static boolean isPureHelpCmd() {
        return Environment.originalArgs.length == 1 &&
                (Environment.originalArgs[0].equals("-help") || Environment.originalArgs[0].equals("-h"));
    }


}
