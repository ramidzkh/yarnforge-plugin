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

package me.ramidzkh.yarnforge.task;

import codechicken.diffpatch.cli.DiffOperation;
import codechicken.diffpatch.util.LoggingOutputStream;
import codechicken.diffpatch.util.Utils;
import codechicken.diffpatch.util.archiver.ArchiveFormat;
import org.cadixdev.mercury.Mercury;
import org.gradle.api.Project;
import org.gradle.api.artifacts.ConfigurationContainer;
import org.gradle.api.logging.LogLevel;
import org.gradle.api.plugins.JavaPlugin;
import org.gradle.api.tasks.TaskAction;
import org.gradle.api.tasks.options.Option;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Collectors;

public class ForgeRemapTask extends BaseRemappingTask {

    private boolean skipClean;

    public ForgeRemapTask() {
        setDescription("(Forge specific) Remap sources and patches");
    }

    @Option(description = "Skip mapping the clean project", option = "skip-clean")
    public void setSkipClean(boolean skip) {
        this.skipClean = skip;
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
        Path mappedMain = dir.resolve("remapped/main");
        Path mappedTest = dir.resolve("remapped/test");
        Path patches = dir.resolve("remapped/patches");
        Path patchesArchive = dir.resolve("remapped/patches.zip");

        Mercury mercury = createRemapper();
        mercury.getClassPath().addAll(compileClasspath);

        {
            mercury.getClassPath().add(patched);

            {
                project.getLogger().lifecycle(":remapping main");
                mercury.rewrite(main, mappedMain);
                System.gc();
            }

            {
                project.getLogger().lifecycle(":remapping test");
                mercury.getClassPath().add(main);
                mercury.getClassPath().addAll(testCompileClasspath);

                try {
                    mercury.rewrite(test, mappedTest);
                    System.gc();
                } catch (RuntimeException ex) {
                    project.getLogger().lifecycle("failed to remap test!");

                    try {
                        Utils.deleteFolder(mappedTest);
                    } catch (IOException ignored) {
                    }
                }

                mercury.getClassPath().remove(main);
                mercury.getClassPath().removeAll(testCompileClasspath);
            }

            mercury.getClassPath().remove(patched);
        }

        {
            project.getLogger().lifecycle(":remapping patched");
            mercury.getClassPath().add(main);
            mercury.rewrite(patched, mappedPatched);
            System.gc();
            mercury.getClassPath().remove(main);
        }

        if (!skipClean) {
            project.getLogger().lifecycle(":remapping clean");
            mercury.rewrite(clean, mappedClean);
            System.gc();
        }

        {
            project.getLogger().lifecycle(":diffing");
            DiffOperation.builder()
                    .logTo(new LoggingOutputStream(getLogger(), LogLevel.LIFECYCLE))
                    .aPath(mappedClean)
                    .bPath(mappedPatched)
                    .outputPath(patchesArchive, ArchiveFormat.ZIP).build().operate();
        }

        {
            project.getLogger().lifecycle(":extracting");

            project.copy(copy -> {
                copy.from(project.zipTree(patchesArchive));
                copy.into(patches);
            });
        }
    }
}
