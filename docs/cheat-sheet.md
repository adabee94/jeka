## Useful commands 

_Jeka_ has predefined methods that comes either from `JkClass` or built-in plugins. 

* `jeka` : Displays console methods and options invokable from command line, along with plugins available in the classpath.
* `jeka [kbean-name]#help` : Displays console methods and options invokable for the specified plugin (e.g. `jeka scaffold#help`).
* `jeka intellij#iml` : Generates iml file for Intellij. This is generated according to the dependencies declared for _Jeka_ project.
* `jeka intellij#iml -JKC=` : If the `jeka intellij#iml` fails due to the non-compile def classes, `-JKC=` will avoid def compilation phase.
* `jeka eclipse#files` : Same purpose as above to generate metadata files for Eclipse.
* `jeka scaffold#run` : Generates files to create a basic _Jeka_ project from scratch.
* `jeka scaffold#wrapper` : Generates wrapper files (jekaw/jekaw.bat and bootstrap jar).
* `jeka scaffold#run java#` : Generates files to create a _Jeka_ project to build a JVM language project.

## Useful standard options

You can add the below options to the command line.

* `-kb=[KBeanName]` : By default, _Jeka_ instantiates the first _KBean_ found under def directory to execute methods. 
It can be forced to instantiate a specific class by passing its long or short name.
If the class is already in classpath, then no def compilation occurs. `-JKC=` is simply equivalent to `-JKC=JkClass` which is the base class bundled in _Jeka_.  
* `-lri` : Displays runtime info which will be displayed meaningfully on the console about current _Jeka_ version, Java version, base directory, download repository, classpath, ...
* `-lsu` : Shows logs about _Jeka_ setup (compilation of def classes, plugin loading, ...). This information is not logged by default.
* `-ls=BRACE` : Alters console output by delimiting tasks with braces and mentioning the processing time for each task.
* `-ls=DEBUG` : Alters console output by showing the class name and line number where the log has been emitted.
* `-lv` : Alters console output by displaying trace logs (emitted by `JkLog#trace`).
* `-cw` : Clean _.work_ directory, forcing compilation of def classes, even if it is marked as being up-to-date.

## Change the JDK that Runs _Jeka_

To determine the JDK to run upon, _Jeka_ looks at, in order of priority :

* _JEKA_JDK_ environment variable ([_JEKA_JDK_]/bin/java must point on _java_ executable)
* _JAVA_HOME_ environment variable 

If none of the above varaiables are present, _Jeka_ will run on the _java_ executable accessible from the _PATH_ environment.

## Change the Repository _Jeka_ uses to Fetch Dependencies 

By default, _Jeka_ fetch dependencies from Maven central (https://repo.maven.apache.org/maven2).

Another default repository can be selected by setting the `jeka.repos.download.url` option. 
It has been recommended to store this value in your [USER DIR]/.jeka/options.properties file to be reused across projects.

For more details, please see `JkRepoFromOptions` javadoc.
