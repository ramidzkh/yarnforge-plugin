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
import net.fabricmc.stitch.commands.tinyv2.TinyClass;
import net.fabricmc.stitch.commands.tinyv2.TinyField;
import net.fabricmc.stitch.commands.tinyv2.TinyFile;
import net.fabricmc.stitch.commands.tinyv2.TinyHeader;
import net.fabricmc.stitch.commands.tinyv2.TinyMethod;
import net.fabricmc.stitch.commands.tinyv2.TinyMethodParameter;
import net.minecraftforge.gradle.common.util.McpNames;
import org.cadixdev.bombe.type.BaseType;
import org.cadixdev.bombe.type.FieldType;
import org.cadixdev.bombe.type.signature.FieldSignature;
import org.cadixdev.bombe.type.signature.MethodSignature;
import org.cadixdev.lorenz.MappingSet;
import org.cadixdev.lorenz.model.*;

import com.google.common.collect.Lists;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Utility for mappings
 */
public class MappingBridge {

    public static final ExtensionKey<String> COMMENT = new ExtensionKey<>(String.class, "comment");

    /**
     * 
     * @param tree the yarn mapping, format: official	intermediary(Yarn)	named(Yarn)
     * @param mapping obf-to-MCP mapping, format: mapping named(Yarn)	named(MCP)
     * @return
     */
    public static TinyFile saveTiny(TinyTree tree, MappingSet mapping) {
    	List<TinyClass> classes = new LinkedList<>();
    	TinyHeader header = new TinyHeader(Lists.newArrayList("official", "intermediary", "named"), 2, 0, new HashMap<String,String>());
    	TinyFile tinyFile = new TinyFile(header, classes);
  
    	for (ClassDef classDef : tree.getClasses()) {
    		String officialClsName = classDef.getName("official");
    		String yarnInterMClsName = classDef.getName("intermediary");
    		String yarnDeobfClsName = classDef.getName("named");
    		String MCPDeobfClsName = String.valueOf(yarnDeobfClsName);

    		ClassMapping<?, ?> classMapping = mapping.computeClassMapping(officialClsName).orElse(null);
    		if (classMapping != null) {
    			MCPDeobfClsName = classMapping.getDeobfuscatedName();
    		}
   
    		List<TinyMethod> methods = new LinkedList<>();
    		List<TinyField> fields = new LinkedList<>();
    		TinyClass classToWrite = new TinyClass(Lists.newArrayList(officialClsName, yarnInterMClsName, MCPDeobfClsName), methods, fields, new LinkedList<>());
    		classes.add(classToWrite);
 
    		for (FieldDef fieldDef : classDef.getFields()) {
        		String officialFldName = fieldDef.getName("official");
        		String officialFldDesc = fieldDef.getDescriptor("official");
        		String yarnInterMFldName = fieldDef.getName("intermediary");
        		String yarnDeobfFldName = fieldDef.getName("named");
        		String MCPDeobfFldName = String.valueOf(yarnDeobfFldName);

        		if (classMapping != null) {
	        		FieldMapping fieldMapping = classMapping.computeFieldMapping(FieldSignature.of(officialFldName, officialFldDesc)).orElse(null);
	        		if (fieldMapping != null) {
	        			MCPDeobfFldName = fieldMapping.getDeobfuscatedName();
	        		}
        		}

        		TinyField fieldToWrite = new TinyField(officialFldDesc, 
        				Lists.newArrayList(officialFldName, yarnInterMFldName, MCPDeobfFldName), 
        				new LinkedList<>());
        		classToWrite.getFields().add(fieldToWrite);
            }

    		for (MethodDef methodDef: classDef.getMethods()) {
        		String officialMtdName = methodDef.getName("official");
        		String officialMtdDesc = methodDef.getDescriptor("official");
        		String yarnInterMMtdName = methodDef.getName("intermediary");
        		String yarnDeobfMtdName = methodDef.getName("named");
        		String MCPDeobfMtdName = String.valueOf(yarnDeobfMtdName);

        		MethodMapping methodMapping = null;
        		if (classMapping != null) {
        			methodMapping = classMapping.getMethodMapping(officialMtdName, officialMtdDesc).orElse(null);
        			if (methodMapping != null) {
        				MCPDeobfMtdName = methodMapping.getDeobfuscatedName();
        			}
        		}

        		List<TinyMethodParameter> parameters = new LinkedList<TinyMethodParameter>();
        		TinyMethod methodToWrite = new TinyMethod(officialMtdDesc,
        				Lists.newArrayList(officialMtdName, yarnInterMMtdName, MCPDeobfMtdName),
        				parameters,
        				new LinkedList<>(),	// Local Vars
        				new LinkedList<>()	// Comments
        				);
        		classToWrite.getMethods().add(methodToWrite);

        		for (ParameterDef paramDef: methodDef.getParameters()) {
        			int paramIndex = paramDef.getLocalVariableIndex();
            		String officialParamName = paramDef.getName("official");
            		String yarnInterMParamName = paramDef.getName("intermediary");
            		String yarnDeobfParamName = paramDef.getName("named");
            		String MCPDeobfParamName = String.valueOf(yarnDeobfParamName);
            		
            		if (methodMapping != null) {
            			MethodParameterMapping paramMapping = methodMapping.getParameterMapping(paramIndex).orElse(null);
            			if (paramMapping != null) {
            				MCPDeobfParamName = paramMapping.getDeobfuscatedName();
            			}
            		}
 
            		parameters.add(new TinyMethodParameter(paramIndex,
            				Lists.newArrayList(officialParamName, yarnInterMParamName, MCPDeobfParamName),
            				new LinkedList<>()	// Comments
            				));
        		}
    		}
    	}
   
    	return tinyFile;
    }
    
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
                    methodMapping
                            .getOrCreateParameterMapping(normalizeIndex(types, parameter.getLocalVariableIndex()))
                            .setDeobfuscatedName(parameter.getName(b))
                            .set(COMMENT, parameter.getComment());
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
