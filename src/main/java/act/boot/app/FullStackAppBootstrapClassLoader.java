package act.boot.app;

/*-
 * #%L
 * ACT Framework
 * %%
 * Copyright (C) 2014 - 2017 ActFramework
 * %%
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *      http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * #L%
 */

import act.Constants;
import act.boot.BootstrapClassLoader;
import act.util.ActClassLoader;
import act.util.ClassInfoRepository;
import act.util.ClassNode;
import act.util.Jars;
import org.osgl.$;
import org.osgl.util.*;

import java.io.File;
import java.lang.reflect.Modifier;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static act.util.ClassInfoRepository.canonicalName;

/**
 * This class loader is responsible for loading Act classes
 */
public class FullStackAppBootstrapClassLoader extends BootstrapClassLoader implements ActClassLoader {

    private static final String KEY_CLASSPATH = "java.class.path";

    /**
     * the {@link System#getProperty(String) system property} key to get
     * the ignored jar file name prefix; multiple prefixes can be specified
     * with comma `,`
     *
     * The default value is defined in {@link #DEF_JAR_IGNORE}
     * ``
     */
    private static final String KEY_SYS_JAR_IGNORE = "act.jar.sys.ignore";

    /**
     * the {@link System#getProperty(String) system property} key to get
     * the ignored jar file name prefix; multiple prefixes can be specified
     * with comma `,`
     */
    private static final String KEY_APP_JAR_IGNORE = "act.jar.app.ignore";

    private static final String DEF_JAR_IGNORE = "act-asm,antlr,ecj-,cglib,commons-,hibernate-,jline-,kryo-,logback-," +
            "mongo-java-,mvel,newrelic,okio-,okhttp,pat-,proxytoys,rythm-engine,snakeyaml,undertow,xnio";

    private final Class<?> PLUGIN_CLASS;

    private List<File> jars;
    private Long jarsChecksum;
    private Map<String, byte[]> libBC = new HashMap<>();
    private List<Class<?>> actClasses = new ArrayList<>();
    private List<Class<?>> pluginClasses = new ArrayList<>();
    private String lineSeparator = OS.get().lineSeparator();
    private static final $.Predicate<File> jarFilter = jarFilter();

    public FullStackAppBootstrapClassLoader(ClassLoader parent) {
        super(parent);
        preload();
        PLUGIN_CLASS = $.classForName("act.plugin.Plugin", this);
    }

    @Override
    public List<Class<?>> pluginClasses() {
        if (classInfoRepository().isEmpty()) {
            restoreClassInfoRegistry();
            restorePluginClasses();
            if (classInfoRepository.isEmpty()) {
                for (String className : C.list(libBC.keySet())) {
                    try {
                        Class<?> c = loadClass(className, true);
                        cache(c);
                        int modifier = c.getModifiers();
                        if (Modifier.isAbstract(modifier) || !Modifier.isPublic(modifier) || c.isInterface()) {
                            continue;
                        }
                        if (PLUGIN_CLASS.isAssignableFrom(c)) {
                            pluginClasses.add(c);
                        }
                    } catch (ClassNotFoundException e) {
                        // ignore
                    } catch (NoClassDefFoundError e) {
                        // ignore
                    }
                }
                saveClassInfoRegistry();
                savePluginClasses();
            }
        }
        return pluginClasses;
    }

    public int libBCSize() {
        return libBC.size();
    }

    protected void preload() {
        buildIndex();
    }

    protected List<File> jars() {
        if (null == jars) {
            jars = jars(FullStackAppBootstrapClassLoader.class.getClassLoader());
            jarsChecksum = calculateChecksum(jars);
        }
        return jars;
    }

    protected long jarsChecksum() {
        jars();
        return jarsChecksum;
    }

    private static $.Predicate<File> jarFilter() {
        String ignores = System.getProperty(KEY_SYS_JAR_IGNORE);
        if (null == ignores) {
            ignores = DEF_JAR_IGNORE;
        }
        String appIgnores = System.getProperty(KEY_APP_JAR_IGNORE);
        if (null != appIgnores) {
            ignores += ("," + appIgnores);
        }
        final String[] sa = ignores.split(",");
        return new $.Predicate<File>() {
            @Override
            public boolean test(File file) {
                String name = file.getName();
                for (String prefix : sa) {
                    if (S.notBlank(prefix) && name.startsWith(prefix)) {
                        return false;
                    }
                }
                return true;
            }
        };
    }

    private static List<File> filterJars(List<File> jars) {
        if (null == jarFilter) {
            return null;
        }
        return C.list(jars).filter(jarFilter);
    }

    public static List<File> jars(ClassLoader cl) {
        List<File> jars = null;
        C.List<String> path = C.listOf(System.getProperty(KEY_CLASSPATH).split(File.pathSeparator));
        if (path.size() < 10) {
            if (cl instanceof URLClassLoader) {
                URLClassLoader realm = (URLClassLoader) cl;
                C.List<URL> urlList = C.listOf(realm.getURLs());
                urlList = urlList.filter(new $.Predicate<URL>() {
                    @Override
                    public boolean test(URL url) {
                        return url.getFile().endsWith(".jar");
                    }
                });
                jars = urlList.map(new $.Transformer<URL, File>() {
                    @Override
                    public File transform(URL url) {
                        try {
                            return new File(url.toURI());
                        } catch (Exception e) {
                            throw E.unexpected(e);
                        }
                    }
                }).sorted();
            }
        }
        if (null == jars) {
            path = path.filter(S.F.contains("jre" + File.separator + "lib").negate().and(S.F.endsWith(".jar")));
            jars = path.map(new $.Transformer<String, File>() {
                @Override
                public File transform(String s) {
                    return new File(s);
                }
            }).sorted();
        }
        return filterJars(jars);
    }

    private void saveClassInfoRegistry() {
        saveToFile(".act.class-registry", classInfoRepository().toJSON());
    }

    private void savePluginClasses() {
        if (pluginClasses.isEmpty()) {
            return;
        }
        StringBuilder sb = S.builder();
        for (Class c : pluginClasses) {
            sb.append(c.getName()).append(lineSeparator);
        }
        sb.deleteCharAt(sb.length() - 1);
        saveToFile(".act.plugins", sb.toString());
    }

    private void saveToFile(String name, String content) {
        File file = new File(name);
        String fileContent = S.concat("#", S.string(jarsChecksum), lineSeparator, content);
        IO.writeContent(fileContent, file);
    }

    private void restoreClassInfoRegistry() {
        List<String> list = restoreFromFile(".act.class-registry");
        if (list.isEmpty()) {
            return;
        }
        String json = S.join(lineSeparator, list);
        classInfoRepository = ClassInfoRepository.parseJSON(json);
    }

    private void restorePluginClasses() {
        List<String> list = restoreFromFile(".act.plugins");
        if (list.isEmpty()) {
            return;
        }
        for (String s : list) {
            pluginClasses.add($.classForName(s, this));
        }
    }

    private List<String> restoreFromFile(String name) {
        File file = new File(name);
        if (file.canRead()) {
            String content = IO.readContentAsString(file);
            String[] sa = content.split(lineSeparator);
            long fileChecksum = Long.parseLong(sa[0].substring(1));
            if (jarsChecksum.equals(fileChecksum)) {
                return C.listOf(sa).drop(1);
            }
        }
        return C.list();
    }

    private synchronized ClassNode cache(Class<?> c) {
        String cname = canonicalName(c);
        if (null == cname) {
            return null;
        }
        String name = c.getName();
        ClassInfoRepository repo = (ClassInfoRepository)classInfoRepository();
        if (repo.has(cname)) {
            return repo.node(name, cname);
        }
        actClasses.add(c);
        ClassNode node = repo.node(name);
        node.modifiers(c.getModifiers());
        Class[] ca = c.getInterfaces();
        for (Class pc: ca) {
            if (pc == Object.class) continue;
            String pcname = canonicalName(pc);
            if (null != pcname) {
                cache(pc);
                node.addInterface(pcname);
            }
        }
        Class pc = c.getSuperclass();
        if (null != pc && Object.class != pc) {
            String pcname = canonicalName(pc);
            if (null != pcname) {
                cache(pc);
                node.parent(pcname);
            }
        }
        return node;
    }

    private void buildIndex() {
        libBC.putAll(Jars.buildClassNameIndex(jars()));
    }

    @Override
    protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
        Class<?> c = findLoadedClass(name);
        if (null != c) {
            return c;
        }

        if (name.startsWith("java") || name.startsWith("org.osgl") || name.startsWith("org.slf4j")) {
            return super.loadClass(name, resolve);
        }

        if (!protectedClasses.contains(name)) {
            c = loadActClass(name, resolve);
        }

        if (null == c) {
            return super.loadClass(name, resolve);
        } else {
            return c;
        }
    }

    protected byte[] tryLoadResource(String name) {
        return null;
    }

    protected Class<?> loadActClass(String name, boolean resolve) {
        byte[] ba = libBC.remove(name);

        if (null == ba) {
            ba = tryLoadResource(name);
        }

        if (null == ba) {
            return findLoadedClass(name);
        }

        Class<?> c = null;
        if (name.startsWith(Constants.ACT_PKG) || name.startsWith(Constants.ASM_PKG)) {
            // skip bytecode enhancement for asm classes or non Act classes
            try {
                c = super.defineClass(name, ba, 0, ba.length, DOMAIN);
            } catch (NoClassDefFoundError e) {
                return null;
            }
        }

        if (null == c) {
            c = defineClass(name, ba);
        }
        if (resolve) {
            super.resolveClass(c);
        }
        return c;
    }

    public Class<?> createClass(String name, byte[] b) throws ClassFormatError {
        return super.defineClass(name, b, 0, b.length, DOMAIN);
    }

    public static long calculateChecksum(List<File> files) {
        long l = 0;
        for (File file : files) {
            l += file.hashCode() + file.lastModified();
        }
        return l;
    }

}
