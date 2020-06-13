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

plugins {
    `java-gradle-plugin`
    idea
    `maven-publish`
}

group = "me.ramidzkh"
version = "1.1.0-SNAPSHOT"

repositories {
    jcenter()

    maven {
        name = "Sonatype"
        url = uri("https://oss.sonatype.org/content/groups/public/")
    }

    maven {
        name = "FabricMC"
        url = uri("https://maven.fabricmc.net/")
    }

    maven {
        name = "MinecraftForge"
        url = uri("https://files.minecraftforge.net/maven")
    }
}

dependencies {
    implementation("com.google.guava", "guava", "29.0-jre")
    implementation("net.fabricmc", "tiny-mappings-parser", "0.2.2.14")
    implementation("org.cadixdev", "mercury", "0.1.0-SNAPSHOT")
    implementation("org.cadixdev", "lorenz", "0.5.2")
    implementation("org.ow2.asm", "asm-commons", "8.0.1")
    implementation("net.minecraftforge.gradle", "ForgeGradle", "3.0.174")
}

java {
    sourceCompatibility = JavaVersion.VERSION_1_8
    targetCompatibility = JavaVersion.VERSION_1_8
}

tasks.withType<Wrapper> {
    gradleVersion = "4.10.3"
    distributionType = Wrapper.DistributionType.ALL
}

gradlePlugin {
    plugins {
        create("remapper") {
            id = "yarnforge-plugin"
            implementationClass = "me.ramidzkh.yarnforge.YarnForgePlugin"
        }
    }
}
