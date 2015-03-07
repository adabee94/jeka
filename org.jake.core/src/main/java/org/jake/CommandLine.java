package org.jake;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.jake.utils.JakeUtilsAssert;
import org.jake.utils.JakeUtilsString;

/*
 * Master and subproject notion are relevant only in a multi-project build.
 * When doing a multiproject build there is always 1 master and many subprojects.
 * 
 * So here we segregate settings appliable for the master project only from
 * settings appliable everywhere (master + subproject).
 * 
 * In other words sub-project settings are a subset of master settings.
 * 
 */
class CommandLine {

	private static final String MASTER_ONLY_SYMBOL = "@";

	private static final String PLUGIN_SYMBOL = "#";

	private static final char PLUGIN_SYMBOL_CHAR = '#';

	public static CommandLine of(String[] words) {
		final CommandLine result = new CommandLine();
		result.masterBuildOptions = extractOptions(words, true);
		result.subProjectBuildOptions = extractOptions(words, false);
		result.masterMethods = extractMethods(words, true);
		result.subProjectMethods = extractMethods(words, false);
		result.masterPluginSetups = extractPluginSetup(words, true);
		result.subProjectPluginSetups = extractPluginSetup(words, false);
		return result;
	}

	private static final String DEFAULT_METHOD = "base";

	private Map<String, String> masterBuildOptions;

	private Map<String, String> subProjectBuildOptions;

	private List<MethodInvocation> masterMethods;

	private List<MethodInvocation> subProjectMethods;

	private Collection<JakePluginSetup> masterPluginSetups;

	private Collection<JakePluginSetup> subProjectPluginSetups;

	private CommandLine() {
		super();
	}




	private static List<MethodInvocation> extractMethods(String[] words, boolean master) {
		final List<MethodInvocation> result = new LinkedList<MethodInvocation>();
		for (final String word : words) {
			if (!word.startsWith("-") && !word.endsWith(PLUGIN_SYMBOL) && !word.endsWith(PLUGIN_SYMBOL+MASTER_ONLY_SYMBOL)) {
				if (word.endsWith(MASTER_ONLY_SYMBOL) && master) {
					result.add(MethodInvocation.parse(JakeUtilsString.substringAfterLast(word, MASTER_ONLY_SYMBOL)));
				} else if (!word.endsWith(MASTER_ONLY_SYMBOL)) {
					result.add(MethodInvocation.parse(word));
				}
			}
		}
		if (result.isEmpty() && master) {
			result.add(MethodInvocation.normal(DEFAULT_METHOD));
		}
		return result;
	}

	private static Map<String, String> extractOptions(String[] words, boolean master) {
		final Map<String, String> result = new HashMap<String, String>();
		for (final String word : words) {
			if (word.startsWith("-") && !word.startsWith("-D")) {
				final int equalIndex = word.indexOf("=");
				if (equalIndex <= -1) {  // no '=' so we just associate the key with a null value
					final String key = word.substring(1);
					if (!key.contains(PLUGIN_SYMBOL)) {  // if   '#' is present this means that it concerns plugin option, not build options
						if (key.endsWith(MASTER_ONLY_SYMBOL) && master) {
							result.put(JakeUtilsString.substringBeforeLast(key, MASTER_ONLY_SYMBOL), null);
						} else if (!key.endsWith(MASTER_ONLY_SYMBOL)){
							result.put(key, null);
						}

					}
				} else {
					final String key = word.substring(1, equalIndex);
					if (!key.contains(PLUGIN_SYMBOL)) {
						final String value = word.substring(equalIndex+1);
						if (value.endsWith(MASTER_ONLY_SYMBOL) && master) {
							result.put(key, JakeUtilsString.substringBeforeLast(value, MASTER_ONLY_SYMBOL));
						} else if (!value.endsWith(MASTER_ONLY_SYMBOL)){
							result.put(key, value);
						}

					}
				}
			}
		}
		return Collections.unmodifiableMap(result);
	}

	private static Collection<JakePluginSetup> extractPluginSetup(String words[], boolean master) {
		final Map<String, JakePluginSetup> setups = new HashMap<String, JakePluginSetup>();
		for (String word : words) {
			if (word.endsWith(MASTER_ONLY_SYMBOL) && !master) {
				continue;
			}
			if (word.endsWith(MASTER_ONLY_SYMBOL)) {
				word = JakeUtilsString.substringAfterLast(word, MASTER_ONLY_SYMBOL);
			}
			if (MethodInvocation.isPluginMethodInvokation(word)) {
				final String pluginName = JakeUtilsString.substringBeforeFirst(word, PLUGIN_SYMBOL);
				if (!setups.containsKey(pluginName)) {
					setups.put(pluginName, JakePluginSetup.of(pluginName, false));
				}
			} else if (MethodInvocation.isPluginActivation(word)) {
				final String pluginName = JakeUtilsString.substringBeforeFirst(word, PLUGIN_SYMBOL);
				final JakePluginSetup setup = setups.get(pluginName);
				if (setup == null) {
					setups.put(pluginName, JakePluginSetup.of(pluginName, true));
				} else {
					setups.put(pluginName, setup.activated());
				}
			} else if (isPluginOption(word)) {
				final String pluginName = JakeUtilsString.substringBeforeFirst(word, PLUGIN_SYMBOL).substring(1);
				JakePluginSetup setup = setups.get(pluginName);
				if (setup == null) {
					setup = JakePluginSetup.of(pluginName, false);
					setups.put(pluginName, setup);
				}
				final int equalIndex = word.indexOf("=");
				if (equalIndex <= -1) {
					final String key = JakeUtilsString.substringAfterFirst(word, PLUGIN_SYMBOL);
					setups.put(pluginName, setup.with(key, null));
				} else {
					final String key = JakeUtilsString.substringBeforeFirst( JakeUtilsString.substringAfterFirst(word, PLUGIN_SYMBOL), "=");
					final String value = word.substring(equalIndex+1);
					setups.put(pluginName, setup.with(key, value));
				}
			}
		}
		return setups.values();
	}

	private static boolean isPluginOption(String word) {
		return word.startsWith("-") && word.indexOf(PLUGIN_SYMBOL) > 2;
	}




	public static final class MethodInvocation {

		public static MethodInvocation parse(String word) {
			if (isPluginMethodInvokation(word)) {
				return pluginMethod(
						JakeUtilsString.substringBeforeFirst(word, PLUGIN_SYMBOL),
						JakeUtilsString.substringAfterLast(word, PLUGIN_SYMBOL));
			}
			return normal(word);
		}

		public static MethodInvocation normal(String name) {
			return new MethodInvocation(name, null);
		}

		public static MethodInvocation pluginMethod(String pluginName, String methodName) {
			JakeUtilsAssert.isTrue(pluginName != null && !pluginName.isEmpty(), "PluginName can' t ne null or empty");
			return new MethodInvocation(methodName, pluginName);
		}

		public final String methodName;

		public final String pluginName;

		private MethodInvocation(String methodName, String pluginName) {
			super();
			JakeUtilsAssert.isTrue(methodName != null && !methodName.isEmpty(), "PluginName can' t be null or empty");
			this.methodName = methodName;
			this.pluginName = pluginName;
		}

		private static boolean isPluginMethodInvokation(String word) {
			return JakeUtilsString.countOccurence(word, PLUGIN_SYMBOL_CHAR) == 1 && !word.startsWith(PLUGIN_SYMBOL) && !word.endsWith(PLUGIN_SYMBOL);
		}

		private static boolean isPluginActivation(String word) {
			return JakeUtilsString.countOccurence(word, PLUGIN_SYMBOL_CHAR) == 1 && !word.startsWith(PLUGIN_SYMBOL) && word.endsWith(PLUGIN_SYMBOL);
		}

		public boolean isMethodPlugin() {
			return pluginName != null;
		}

		@Override
		public String toString() {
			if (pluginName == null) {
				return methodName;
			}
			return pluginName + "#" + methodName;
		}


	}

	public static class JakePluginSetup {

		public static Set<String> names(Iterable<JakePluginSetup> setups) {
			final Set<String> result = new HashSet<String>();
			for (final JakePluginSetup setup : setups) {
				result.add(setup.pluginName);
			}
			return result;
		}

		public static JakePluginSetup findOrFail(String name, Iterable<JakePluginSetup> setups) {
			for (final JakePluginSetup setup : setups) {
				if (name.equals(setup.pluginName)) {
					return setup;
				}
			}
			throw new IllegalArgumentException("No setup found with name " + name +" found in " + setups);
		}

		@SuppressWarnings("unchecked")
		public static JakePluginSetup of(String name, boolean activated) {
			return new JakePluginSetup(name, Collections.EMPTY_MAP,activated);
		}

		public final String pluginName;

		public final Map<String, String> options;

		public final boolean activated;

		private JakePluginSetup(String pluginName, Map<String, String> options, boolean activated) {
			super();
			this.pluginName = pluginName;
			this.options = Collections.unmodifiableMap(options);
			this.activated = activated;
		}

		public JakePluginSetup with(String key, String value) {
			final Map<String, String> map = new HashMap<String, String>(options);
			map.put(key, value);
			return new JakePluginSetup(pluginName, map, activated);
		}

		public JakePluginSetup activated(){
			return new JakePluginSetup(pluginName, options, true);
		}
	}

	public Map<String, String> getMasterBuildOptions() {
		return masterBuildOptions;
	}




	public void setMasterBuildOptions(Map<String, String> masterBuildOptions) {
		this.masterBuildOptions = masterBuildOptions;
	}




	public Map<String, String> getSubProjectBuildOptions() {
		return subProjectBuildOptions;
	}




	public void setSubProjectBuildOptions(Map<String, String> subProjectBuildOptions) {
		this.subProjectBuildOptions = subProjectBuildOptions;
	}

	public List<MethodInvocation> getMasterMethods() {
		return masterMethods;
	}

	public void setMasterMethods(List<MethodInvocation> masterMethods) {
		this.masterMethods = masterMethods;
	}

	public List<MethodInvocation> getSubProjectMethods() {
		return subProjectMethods;
	}

	public void setSubProjectMethods(List<MethodInvocation> subProjectMethods) {
		this.subProjectMethods = subProjectMethods;
	}

	public Collection<JakePluginSetup> getMasterPluginSetups() {
		return masterPluginSetups;
	}

	public void setMasterPluginSetups(Collection<JakePluginSetup> masterPluginSetups) {
		this.masterPluginSetups = masterPluginSetups;
	}

	public Collection<JakePluginSetup> getSubProjectPluginSetups() {
		return subProjectPluginSetups;
	}

	public void setSubProjectPluginSetups(
			Collection<JakePluginSetup> subProjectPluginSetups) {
		this.subProjectPluginSetups = subProjectPluginSetups;
	}

}
