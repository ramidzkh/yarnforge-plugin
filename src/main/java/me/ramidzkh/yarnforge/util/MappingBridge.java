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

package me.ramidzkh.yarnforge.util;

import net.fabricmc.mapping.tree.*;
import net.minecraftforge.gradle.common.util.McpNames;
import org.cadixdev.bombe.type.BaseType;
import org.cadixdev.bombe.type.FieldType;
import org.cadixdev.bombe.type.signature.MethodSignature;
import org.cadixdev.lorenz.MappingSet;
import org.cadixdev.lorenz.model.*;

import java.util.List;
import java.util.function.Consumer;

/**
 * Utility for mappings
 */
public class MappingBridge {

    public static final ExtensionKey<String> COMMENT = new ExtensionKey<>(String.class, "comment");

    /**
     * Loads a {@link TinyTree} to a new {@link MappingSet}, from the <code>a</code> namespace to the <code>b</code>
     * namespace. Writes comments to the {@link #COMMENT} extension key when possible. Does not support local variable
     * renames.
     *
     * @param tree The tree
     * @param a    Obfuscated namespace
     * @param b    De-obfuscated namespace
     * @return A newly constructed obfuscation mapping
     */
    public static MappingSet loadTiny(TinyTree tree, String a, String b) {
        MappingSet mappings = MappingSet.create();

        for (ClassDef classDef : tree.getClasses()) {
            ClassMapping<?, ?> classMapping = mappings
                    .getOrCreateClassMapping(classDef.getName(a))
                    .setDeobfuscatedName(classDef.getName(b));
            classMapping.set(COMMENT, classDef.getComment());

            for (FieldDef field : classDef.getFields()) {
                classMapping
                        // .getOrCreateFieldMapping(FieldSignature.of(field.getName(a), field.getDescriptor(a)))
                        .getOrCreateFieldMapping(field.getName(a)) // TODO: Fix this later...
                        .setDeobfuscatedName(field.getName(b))
                        .set(COMMENT, field.getComment());
            }

            for (MethodDef method : classDef.getMethods()) {
                MethodMapping methodMapping = classMapping
                        .getOrCreateMethodMapping(MethodSignature.of(method.getName(a), method.getDescriptor(a)))
                        .setDeobfuscatedName(method.getName(b));
                methodMapping.set(COMMENT, method.getComment());

                List<FieldType> types = methodMapping.getDescriptor().getParamTypes();

                for (ParameterDef parameter : method.getParameters()) {
                    // methodMapping
                    //         .getOrCreateParameterMapping(normalizeIndex(types, parameter.getLocalVariableIndex()))
                    //         .setDeobfuscatedName(parameter.getName(b))
                    //         .set(COMMENT, parameter.getComment());
                }
            }
        }

        return mappings;
    }

    /**
     * Merges a {@link McpNames} to an obfuscation mapping. This does not clone the mappings so care should be taken
     *
     * @param mappings The mapping to process
     * @param names    The names to merge with
     * @return The same mappings
     */
    public static MappingSet mergeMcpNames(MappingSet mappings, McpNames names) {
        iterateClasses(mappings, classMapping -> {
            for (FieldMapping fieldMapping : classMapping.getFieldMappings()) {
                fieldMapping.setDeobfuscatedName(names.rename(fieldMapping.getDeobfuscatedName()));
            }

            for (MethodMapping methodMapping : classMapping.getMethodMappings()) {
                methodMapping.setDeobfuscatedName(names.rename(methodMapping.getDeobfuscatedName()));
            }
        });

        return mappings;
    }

    /**
     * Iterates through all the {@link TopLevelClassMapping} and {@link InnerClassMapping} in a {@link MappingSet}
     *
     * @param mappings The mappings
     * @param consumer The consumer of the {@link ClassMapping}
     */
    public static void iterateClasses(MappingSet mappings, Consumer<ClassMapping<?, ?>> consumer) {
        for (TopLevelClassMapping classMapping : mappings.getTopLevelClassMappings()) {
            iterateClass(classMapping, consumer);
        }
    }

    private static void iterateClass(ClassMapping<?, ?> classMapping, Consumer<ClassMapping<?, ?>> consumer) {
        consumer.accept(classMapping);

        for (InnerClassMapping innerClassMapping : classMapping.getInnerClassMappings()) {
            iterateClass(innerClassMapping, consumer);
        }
    }

    private static int normalizeIndex(List<FieldType> types, int index) {
        int i = 0;

        for (FieldType paramType : types) {
            if (index == 0) {
                break;
            }

            if (paramType == BaseType.LONG || paramType == BaseType.DOUBLE) {
                index -= 2;
            } else {
                index--;
            }

            i++;
        }

        return i;
    }
}
