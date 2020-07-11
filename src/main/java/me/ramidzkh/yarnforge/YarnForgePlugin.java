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

import me.ramidzkh.yarnforge.task.ForgeRemapTask;
import me.ramidzkh.yarnforge.task.UserRemapTask;
import me.ramidzkh.yarnforge.util.MappingBridge;
import net.minecraftforge.gradle.common.task.ExtractMCPData;
import net.minecraftforge.gradle.common.util.MavenArtifactDownloader;
import net.minecraftforge.gradle.common.util.McpNames;
import net.minecraftforge.gradle.mcp.MCPRepo;
import net.minecraftforge.gradle.mcp.task.GenerateSRG;
import net.minecraftforge.gradle.userdev.UserDevExtension;
import org.cadixdev.lorenz.MappingSet;
import org.cadixdev.lorenz.io.MappingFormats;
import org.gradle.api.Plugin;
import org.gradle.api.Project;

import java.io.BufferedReader;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;

public class YarnForgePlugin implements Plugin<Project> {

    @Override
    public void apply(Project target) {
        target.getRepositories().maven(maven -> {
            maven.setName("FabricMC");
            maven.setUrl(URI.create("https://maven.fabricmc.net/"));
        });

        if (target.getPluginManager().hasPlugin("net.minecraftforge.gradle")) {
            ExtractMCPData extractData = (ExtractMCPData) target.getTasks().getByName("extractSrg");
            target.getTasks().register("userRemapYarn", UserRemapTask.class, task -> {
                task.dependsOn(extractData);
                task.setNamesProvider(() -> {
                    try (BufferedReader reader = Files.newBufferedReader(extractData.getOutput().toPath())) {
                        MappingSet obf2Srg = MappingFormats.TSRG.createReader(reader).read();
                        return MappingBridge.mergeMcpNames(obf2Srg, findNames(target, target.getExtensions().getByType(UserDevExtension.class).getMappings()));
                    } catch (IOException exception) {
                        throw new RuntimeException(exception);
                    }
                });
            });
        } else {
            GenerateSRG createMcp2Obf = (GenerateSRG) target.project("forge").getTasks().getByName("createMcp2Obf");
            target.getTasks().register("forgeRemapYarn", ForgeRemapTask.class, task -> {
                task.dependsOn(createMcp2Obf);
                task.setNamesProvider(() -> {
                    try (BufferedReader reader = Files.newBufferedReader(createMcp2Obf.getOutput().toPath())) {
                        return MappingFormats.TSRG.createReader(reader).read().reverse();
                    } catch (IOException exception) {
                        throw new RuntimeException(exception);
                    }
                });
            });
        }
    }

    private static McpNames findNames(Project project, String mapping) throws IOException {
        int idx = mapping.lastIndexOf('_');
        String channel = mapping.substring(0, idx);
        String version = mapping.substring(idx + 1);
        String desc = MCPRepo.getMappingDep(channel, version);

        return McpNames.load(MavenArtifactDownloader.generate(project, desc, false));
    }
}
