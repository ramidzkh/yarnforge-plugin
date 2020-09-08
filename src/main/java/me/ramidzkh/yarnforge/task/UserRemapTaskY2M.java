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

import org.cadixdev.mercury.Mercury;
import org.gradle.api.Project;
import org.gradle.api.tasks.TaskAction;

import java.io.File;

public class UserRemapTaskY2M extends BaseRemappingTask {
    @TaskAction
    public void doTask() throws Exception {
        Project project = getProject();

        try {
            Mercury mercury = createRemapper(true);

            for (File file : getAllDependencies()) {
            	String fileName = file.getName().toLowerCase();
            	// Do not add the MCP-mapped vanilla jar
            	if (!fileName.startsWith("forge-") && !fileName.endsWith("-recomp.jar")) {
            		mercury.getClassPath().add(file.toPath());
            	}
            }

            // Add the Yarn-mapped vanilla jar, copied from Fabric-loom
            File libsDir = project.file("libs");
            for (File file: libsDir.listFiles()) {
            	mercury.getClassPath().add(file.toPath());
            }

            project.getLogger().lifecycle(":remapping");
            mercury.rewrite(project.file("src_yarn/main/java").toPath(), project.file("remapped").toPath());
        } finally {
            System.gc();
        }
    }
}
