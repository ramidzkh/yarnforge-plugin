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

import net.minecraftforge.gradle.common.util.MappingFile;
import org.cadixdev.lorenz.MappingSet;
import org.cadixdev.mercury.Mercury;
import org.cadixdev.mercury.remapper.MercuryRemapper;
import org.gradle.api.Project;
import org.gradle.api.plugins.JavaPlugin;
import org.gradle.api.tasks.TaskAction;

import java.io.File;
import java.io.IOException;
import java.util.function.Supplier;

public class UserRemapTask extends BaseRemappingTask {
    Supplier<File> mcpToSrg;
    Supplier<File> ObfToSrg;

    public void setMcpToSrg(Supplier<File> mcpToSrg) {
        this.mcpToSrg = mcpToSrg;
    }

    public void setObfToSrg(Supplier<File> obfToSrg) {
        this.ObfToSrg = obfToSrg;
    }

    @Override
    public MappingSet getMcpToObf() throws IOException {
        MappingSet m2s = MappingBridge.loadMappingFile(MappingSet.create(), MappingFile.load(mcpToSrg.get()));
        return m2s.merge(MappingBridge.loadMappingFile(MappingSet.create(), MappingFile.load(ObfToSrg.get())).reverse());
    }
    
    @TaskAction
    public void doTask() throws Exception {
        Project project = getProject();

        try {
            Mercury mercury = new Mercury();
            mercury.getProcessors().add(MercuryRemapper.create(createMcpToYarn(), true));

            for (File file : project.getConfigurations().getByName(JavaPlugin.COMPILE_CLASSPATH_CONFIGURATION_NAME).getFiles()) {
                mercury.getClassPath().add(file.toPath());
            }

            project.getLogger().lifecycle(":remapping");
            mercury.rewrite(project.file("src/main/java").toPath(), project.file("remapped").toPath());
        } finally {
            System.gc();
        }
    }
    
}
