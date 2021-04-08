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
version = "1.3.0-SNAPSHOT"

object Config {
    // If you are having issues with single files failing to remap but you wish to ignore those, you can set this to
    // true. You must also add https://maven.shedaniel.me/ to the buildscript repositories of the project which applies
    // this plugin
    const val ignoreConflicts = false
}

repositories {
    jcenter()

    if (Config.ignoreConflicts) {
        maven {
            name = "Shedaniel"
            url = uri("https://maven.shedaniel.me/")
        }
    }

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
    implementation("net.fabricmc", "stitch", "0.5.0+build.76")
    implementation("net.fabricmc", "tiny-mappings-parser", "0.3.0+build.17")
    implementation("org.cadixdev", "mercury", if (Config.ignoreConflicts) "0.2.8" else "0.1.0-SNAPSHOT")
    implementation("org.cadixdev", "mercurymixin", "0.1.0-SNAPSHOT")
    implementation("org.cadixdev", "lorenz", "0.5.3")
    implementation("org.cadixdev", "lorenz-asm", "0.5.3")
    implementation("net.minecraftforge.gradle", "ForgeGradle", "4.0.1")
    implementation("net.minecraftforge", "artifactural", "2.0.3")
    implementation("net.minecraftforge", "artifactural", "1.0.12")
    implementation("com.cloudbees", "diff4j", "1.2")
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
