package act.util;

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

import act.Act;
import act.app.*;
import act.asm.AsmContext;
import org.osgl.util.E;
import org.osgl.util.S;

import java.lang.annotation.ElementType;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

public interface ActError {
    Throwable getCauseOrThis();
    Throwable getCause();
    SourceInfo sourceInfo();
    List<String> stackTrace();
    String getMessage();
    String getLocalizedMessage();
    boolean isErrorSpot(String traceLine, String nextTraceLine);

    class Util {

        public static List<String> stackTraceOf(ActError error) {
            Throwable cause = error.getCause();
            ActError root = error;
            if (null == cause) {
                cause = (Throwable) error;
                root = null;
            }
            return stackTraceOf(cause, root);
        }

        public static List<String> stackTraceOf(Throwable t, ActError root) {
            List<String> l = new ArrayList<>();
            while (null != t) {
                StackTraceElement[] a = t.getStackTrace();
                for (StackTraceElement e : a) {
                    String line = S.concat("at ", e.toString());
                    if (line.contains("org.osgl.util.E.")) {
                        // skip E util class
                        continue;
                    }
                    if (l.contains(line)) {
                        l.add(line);
                        // caused by stack trace stop at here
                        break;
                    }
                    l.add(line);
                }
                t = t.getCause();
                if (t == root) {
                    break;
                }
                if (null != t) {
                    l.add("Caused by " + t.toString());
                }
            }
            return l;
        }

        public static SourceInfo loadSourceInfo(StackTraceElement[] stackTraceElements, Class<? extends ActError> errClz) {
            E.illegalStateIf(Act.isProd());
            DevModeClassLoader cl = (DevModeClassLoader) App.instance().classLoader();
            for (StackTraceElement stackTraceElement : stackTraceElements) {
                int line = stackTraceElement.getLineNumber();
                if (line <= 0) {
                    continue;
                }
                String className = stackTraceElement.getClassName();
                if (S.eq(errClz.getName(), className)) {
                    continue;
                }
                Source source = cl.source(className);
                if (null == source) {
                    continue;
                }
                return new SourceInfoImpl(source, line);
            }
            return null;
        }

        public static SourceInfo loadSourceInfo(Method method) {
            return loadSourceInfo(method.getDeclaringClass().getName(), method.getName(), true, null);
        }

        public static SourceInfo loadSourceInfo(AsmContext asmContext) {
            return loadSourceInfo(asmContext.className(), asmContext.name(), ElementType.METHOD == asmContext.type(), asmContext.lineNo());
        }

        private static SourceInfo loadSourceInfo(String className, String elementName, boolean isMethod, Integer lineNo) {
            E.illegalStateIf(Act.isProd());
            DevModeClassLoader cl = (DevModeClassLoader) App.instance().classLoader();
            Source source = cl.source(className);
            if (null == source) {
                return null;
            }
            List<String> lines = source.lines();
            Line candidate = null;
            String pattern = isMethod ?
                    S.concat("^\\s*.*", elementName, "\\s*\\(.*") :
                    S.concat("^\\s*.*", elementName, "[^\\(\\{]*");
            if (null != lineNo) {
                return new SourceInfoImpl(source, lineNo);
            }
            for (int i = 0; i < lines.size(); ++i) {
                String line = lines.get(i);
                if (line.matches(pattern)) {
                    candidate = new Line(line, i + 1);
                    if (candidate.forSure) {
                        return new SourceInfoImpl(source, candidate.no);
                    }
                }
            }
            if (null != candidate) {
                return new SourceInfoImpl(source, candidate.no);
            }
            return new SourceInfoImpl(source, 1);
        }

        private static class Line {
            String line;
            int no;
            boolean forSure;
            Line(String line, int no) {
                this.line = line;
                this.no = no;
                forSure = line.contains("public ");
            }
        }
    }
}
