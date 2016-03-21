/*
 * Copyright 2015-2016 the original author or authors.
 *
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
 */

package org.springframework.cloud.stream.app.plugin.utils;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Collection;
import java.util.Optional;

import org.apache.commons.io.FileUtils;

/**
 * @author Soby Chacko
 */
public class SpringCloudStreamPluginUtils {

    private SpringCloudStreamPluginUtils() {
        //prevents instantiation
    }

    public static void cleanupGenProjHome(File genProjecthome) throws IOException {
        FileUtils.cleanDirectory(genProjecthome);
        FileUtils.deleteDirectory(genProjecthome);
    }

    public static void ignoreUnitTestGeneratedByInitializer(String generatedAppHome, String generatedApp) throws IOException {
        String testDir = String.format("%s/%s", generatedAppHome, "src/test/java");

        Collection<File> files = FileUtils.listFiles(new File(testDir), null, true);
        Optional<File> first = files.stream()
                .filter(f -> f.getName().endsWith("ApplicationTests.java"))
                .findFirst();

        if (first.isPresent()){
            StringBuilder sb = new StringBuilder();
            File f1 = first.get();
            Files.readAllLines(f1.toPath()).forEach(l -> {
                if (l.startsWith("import") && !sb.toString().contains("import org.junit.Ignore")) {
                    sb.append("import org.junit.Ignore;\n");
                }
                else if (l.startsWith("@RunWith") && !sb.toString().contains("@Ignore")) {
                    sb.append("@Ignore\n");
                }
                sb.append(l);
                sb.append("\n");
            });
            Files.write(f1.toPath(), sb.toString().getBytes());
        }
    }

}
