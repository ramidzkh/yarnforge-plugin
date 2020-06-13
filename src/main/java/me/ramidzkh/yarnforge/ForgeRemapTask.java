/*
 * Copyright 2020 Ramid Khan
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package me.ramidzkh.yarnforge;

import com.cloudbees.diff.Diff;
import org.cadixdev.mercury.Mercury;
import org.cadixdev.mercury.remapper.MercuryRemapper;
import org.gradle.api.Project;
import org.gradle.api.artifacts.ConfigurationContainer;
import org.gradle.api.plugins.JavaPlugin;
import org.gradle.api.tasks.TaskAction;

import java.io.File;
import java.io.IOException;
import java.io.StringReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class ForgeRemapTask extends BaseRemappingTask {

    public ForgeRemapTask() {
        setDescription("(Forge specific) Remap sources and patches");
    }

    @TaskAction
    public void doTask() throws Exception {
        Project project = getProject();
        Path dir = project.getProjectDir().toPath();

        ConfigurationContainer configurations = project.project(":forge").getConfigurations();
        List<Path> compileClasspath = configurations.getByName(JavaPlugin.COMPILE_CLASSPATH_CONFIGURATION_NAME).getFiles().stream().map(File::toPath).collect(Collectors.toList());
        List<Path> testCompileClasspath = configurations.getByName(JavaPlugin.TEST_COMPILE_CLASSPATH_CONFIGURATION_NAME).getFiles().stream().map(File::toPath).collect(Collectors.toList());

        compileClasspath.add(dir.resolve("src/fmllauncher/java"));
        testCompileClasspath.removeAll(compileClasspath);

        Path main = dir.resolve("src/main/java");
        Path test = dir.resolve("src/test/java");
        Path clean = dir.resolve("projects/clean/src/main/java");
        Path patched = dir.resolve("projects/forge/src/main/java");

        Path mappedClean = dir.resolve("remapped/clean");
        Path mappedPatched = dir.resolve("remapped/patched");

        Mercury mercury = new Mercury();
        mercury.getProcessors().add(MercuryRemapper.create(createMcpToYarn()));
        mercury.getClassPath().addAll(compileClasspath);

        {
            mercury.getClassPath().add(patched);

            {
                project.getLogger().lifecycle(":remapping main");
                mercury.rewrite(main, dir.resolve("remapped/main"));
            }

            {
                project.getLogger().lifecycle(":remapping test");
                mercury.getClassPath().add(main);
                mercury.getClassPath().addAll(testCompileClasspath);
                mercury.rewrite(test, dir.resolve("remapped/test"));
                mercury.getClassPath().remove(main);
                mercury.getClassPath().removeAll(testCompileClasspath);
            }

            mercury.getClassPath().remove(patched);
        }

        {
            project.getLogger().lifecycle(":remapping patched");
            mercury.getClassPath().add(main);
            mercury.rewrite(patched, mappedPatched);
            mercury.getClassPath().remove(main);
        }

        {
            project.getLogger().lifecycle(":remapping clean");
            mercury.rewrite(clean, mappedClean);
        }

        {
            project.getLogger().lifecycle(":diffing");
            try (Stream<Path> stream = Files.walk(mappedClean)) {
                stream.filter(Files::isRegularFile)
                        .forEach(c -> {
                            Path file = mappedClean.relativize(c);

                            try {
                                String patch = makePatch(file.toString(), new String(Files.readAllBytes(c)), new String(Files.readAllBytes(mappedPatched.resolve(file))));

                                if (patch != null) {
                                    Path p = dir.resolve("remapped/patches").resolve(file + ".patch");
                                    Files.createDirectories(p.getParent());
                                    Files.write(p, patch.getBytes(), StandardOpenOption.CREATE);
                                }
                            } catch (IOException exception) {
                                exception.printStackTrace();
                            }
                        });
            }

        }
    }

    private static String makePatch(String relative, String a, String b) throws IOException {
        String originalRelative = "a/" + relative;
        String modifiedRelative = "b/" + relative;
        String originalData = a.replace("\r\n", "\n");
        String modifiedData = b.replace("\r\n", "\n");
        Diff diff = Diff.diff(new StringReader(originalData), new StringReader(modifiedData), false);
        return !diff.isEmpty() ? diff.toUnifiedDiff(originalRelative, modifiedRelative, new StringReader(originalData), new StringReader(modifiedData), 3).replaceAll("\r?\n", "\n") : null;
    }
}
