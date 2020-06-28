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

import net.minecraftforge.gradle.common.task.ExtractMCPData;
import net.minecraftforge.gradle.mcp.task.GenerateSRG;
import org.gradle.api.Plugin;
import org.gradle.api.Project;

import java.net.URI;

public class YarnForgePlugin implements Plugin<Project> {

    @Override
    public void apply(Project target) {
        target.getRepositories().maven(maven -> {
            maven.setName("FabricMC");
            maven.setUrl(URI.create("https://maven.fabricmc.net/"));
        });

        if (target.getPluginManager().hasPlugin("net.minecraftforge.gradle")) {
            // FG gives userdev "createMcpToSrg", but no direct mcp->obf so we have to merge it ourselves.
            ExtractMCPData createObfToSrg = (ExtractMCPData) target.getTasks().getByName("extractSrg");
            GenerateSRG createMcpToSrg = (GenerateSRG) target.getTasks().getByName("createMcpToSrg");
            target.getTasks().register("userRemapYarn", UserRemapTask.class, task -> {
                task.dependsOn(createObfToSrg);
                task.dependsOn(createMcpToSrg);
                task.setObfToSrg(createObfToSrg::getOutput);
                task.setMcpToSrg(createMcpToSrg::getOutput);
            });
        } else {
            // Thanks for the consistent task naming, ForgeGradle!
            GenerateSRG createMcp2Obf = (GenerateSRG) target.project("forge").getTasks().getByName("createMcp2Obf");
            target.getTasks().register("forgeRemapYarn", ForgeRemapTask.class, task -> {
                task.dependsOn(createMcp2Obf);
                task.setMcp2Obf(createMcp2Obf::getOutput);
            });
        }
    }
}
