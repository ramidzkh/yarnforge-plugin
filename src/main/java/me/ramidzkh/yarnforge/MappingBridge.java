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

import net.fabricmc.mapping.tree.*;
import net.minecraftforge.gradle.common.util.MappingFile;
import net.minecraftforge.gradle.common.util.McpNames;
import org.cadixdev.bombe.type.signature.FieldSignature;
import org.cadixdev.bombe.type.signature.MethodSignature;
import org.cadixdev.lorenz.MappingSet;
import org.cadixdev.lorenz.model.*;

import java.util.function.Consumer;

public class MappingBridge {

    public static final ExtensionKey<String> COMMENT = new ExtensionKey<>(String.class, "comment");

    public static MappingSet loadTiny(MappingSet mappings, TinyTree tree, String a, String b) {
        for (ClassDef classDef : tree.getClasses()) {
            ClassMapping<?, ?> classMapping = mappings
                    .getOrCreateClassMapping(classDef.getName(a))
                    .setDeobfuscatedName(classDef.getName(b));
            classMapping.set(COMMENT, classDef.getComment());

            for (FieldDef field : classDef.getFields()) {
                classMapping
                        .getOrCreateFieldMapping(FieldSignature.of(field.getName(a), field.getDescriptor(a)))
                        .setDeobfuscatedName(field.getName(b))
                        .set(COMMENT, field.getComment());
            }

            for (MethodDef method : classDef.getMethods()) {
                MethodMapping methodMapping = classMapping
                        .getOrCreateMethodMapping(MethodSignature.of(method.getName(a), method.getDescriptor(a)))
                        .setDeobfuscatedName(method.getName(b));
                methodMapping.set(COMMENT, method.getComment());

                for (ParameterDef parameter : method.getParameters()) {
                    methodMapping
                            .getOrCreateParameterMapping(parameter.getLocalVariableIndex())
                            .setDeobfuscatedName(parameter.getName(b))
                            .set(COMMENT, parameter.getComment());
                }
            }
        }

        return mappings;
    }

    public static MappingSet loadMappingFile(MappingSet mappings, MappingFile file) {
        for (MappingFile.Cls cls : file.getClasses()) {
            ClassMapping<?, ?> classMapping = mappings
                    .getOrCreateClassMapping(cls.getOriginal())
                    .setDeobfuscatedName(cls.getMapped());

            for (MappingFile.Cls.Field field : cls.getFields()) {
                classMapping
                        .getOrCreateFieldMapping(field.getOriginal())
                        .setDeobfuscatedName(field.getMapped());
            }

            for (MappingFile.Cls.Method method : cls.getMethods()) {
                classMapping
                        .getOrCreateMethodMapping(MethodSignature.of(method.getOriginal(), method.getDescriptor()))
                        .setDeobfuscatedName(method.getMapped());
            }
        }

        return mappings;
    }
}
