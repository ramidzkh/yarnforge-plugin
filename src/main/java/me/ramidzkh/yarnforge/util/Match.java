package me.ramidzkh.yarnforge.util;

import jdk.internal.dynalink.support.ClassMap;
import org.cadixdev.lorenz.MappingSet;
import org.cadixdev.lorenz.io.MappingFormats;
import org.cadixdev.lorenz.model.*;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Optional;
import java.util.function.BiFunction;
import java.util.function.Function;

/**
 * Shamelessly lifted from Alef, by phase.
 * Besides the lombok annotations being stripped it's the same as linked.
 * You can find the original code (and license) here:
 * https://github.com/phase/alef/tree/853a9787d077f767dab7fc4938b95a750d618885
 */
public class Match {
    private final List<ClassMatch> classMatches;
    private final HashMap<String, ClassMatch> oldObfToMatch = new HashMap<>();
    public Match(List<ClassMatch> matches) {
        this.classMatches = matches;
        matches.forEach(match -> oldObfToMatch.put(match.oldName, match));
    }

    public static class ClassMatch {
        private final String oldName;
        private final String newName;
        private final List<FieldMatch> fieldMatches = new ArrayList<>();
        private final List<MethodMatch> methodMatches = new ArrayList<>();
        private final HashMap<String, String> oldObfToNewFieldObf= new HashMap<>();
        private final HashMap<String, MethodMatch> oldObfWithDescToMethodMatch = new HashMap<>();
        public ClassMatch(String oldName, String newName) {
            this.oldName = oldName;
            this.newName = newName;
        }

        private void updateMaps() {
            fieldMatches.forEach(match -> oldObfToNewFieldObf.put(match.oldName, match.newName));
            methodMatches.forEach(match -> oldObfWithDescToMethodMatch.put(match.oldName + match.oldSignature, match));
        }
    }

    public static class FieldMatch {
        private final String oldName;
        private final String oldFieldType;
        private final String newName;
        private final String newFieldType;

        public FieldMatch(String oldName, String oldFieldType, String newName, String newFieldType) {
            this.oldName = oldName;
            this.oldFieldType = oldFieldType;
            this.newName = newName;
            this.newFieldType = newFieldType;
        }
    }

    public static class MethodMatch {
        private final String oldName;
        private final String oldSignature;
        private final String newName;
        private final String newSignature;

        public MethodMatch(String oldName, String oldSignature, String newName, String newSignature) {
            this.oldName = oldName;
            this.oldSignature = oldSignature;
            this.newName = newName;
            this.newSignature = newSignature;
        }
    }

    /**
     * Chain to matches together
     * If This: A -> B
     * and Other: B -> C
     * this method returns a match from A -> C
     *
     * @param newMatch other match to chain
     * @return new match from this' old to other's new
     */
    public Match chain(Match newMatch) {
        List<ClassMatch> chainedClasses = new ArrayList<>(this.classMatches.size());
        // chain classes
        for (ClassMatch oldClass : this.classMatches) {
            for (ClassMatch newClass : newMatch.classMatches) {
                if (oldClass.newName.equals(newClass.oldName)) {
                    ClassMatch chainedClass = new ClassMatch(oldClass.oldName, newClass.newName);

                    // chain fields
                    for (FieldMatch fieldMatch : oldClass.fieldMatches) {
                        for (FieldMatch otherFieldMatch : newClass.fieldMatches) {
                            if (fieldMatch.newName.equals(otherFieldMatch.oldName)) {
                                chainedClass.fieldMatches.add(new FieldMatch(fieldMatch.oldName, fieldMatch.oldFieldType,
                                        otherFieldMatch.newName, otherFieldMatch.newFieldType));
                                break;
                            }
                        }
                    }

                    // chain methods
                    for (MethodMatch methodMatch : oldClass.methodMatches) {
                        for (MethodMatch otherMethodMatch : newClass.methodMatches) {
                            if (methodMatch.newName.equals(otherMethodMatch.oldName) && methodMatch.newSignature.equals(otherMethodMatch.oldSignature)) {
                                chainedClass.methodMatches.add(new MethodMatch(methodMatch.oldName,
                                        methodMatch.oldSignature, otherMethodMatch.newName, otherMethodMatch.newSignature));
                                break;
                            }
                        }
                    }

                    chainedClasses.add(chainedClass);
                    break;
                }
            }
        }
        return new Match(chainedClasses);
    }

//    /**
//     * Combine Mapping Sets using this Match
//     *
//     * @param oldMappings old mappings, obf -> named
//     * @param newMappings new mappings, obf -> named
//     * @return old named -> new named
//     */
//    public MappingSet combineMappings(MappingSet oldMappings, MappingSet newMappings) {
//       MappingSet combine = MappingSet.create();
//        for (TopLevelClassMapping oldTopClass : oldMappings.getTopLevelClassMappings()) {
//            Optional<TopLevelClassMapping> newTopClass = newMappings.getTopLevelClassMapping(this.oldObfToMatch.get(oldTopClass.getFullObfuscatedName()).newName);
//
//            if (!newTopClass.isPresent()) {
//                continue;
//            }
//            processClass(oldTopClass, newTopClass.get(), combine::createTopLevelClassMapping, this.oldObfToMatch.get(oldTopClass.getFullObfuscatedName()));
//        }
//
//        return combine;
//    }
//
//    private void processClass(ClassMapping<?, ?> oldClass, ClassMapping<?, ?> newClass, BiFunction<String, String, ClassMapping<?, ?>> factory,
//                              ClassMatch classMatch) {
//        classMatch.updateMaps();
//        // TODO: get is nullable
//        ClassMapping<?,?> combineClass = factory.apply(oldClass.getFullObfuscatedName(), newClass.getFullDeobfuscatedName());
//
//        for (FieldMapping fieldMapping : oldClass.getFieldMappings()) {
//            Optional<FieldMapping> newFieldMapping = newClass.getFieldMapping(classMatch.oldObfToNewFieldObf.get(fieldMapping.getObfuscatedName()));
//
//            if (!newFieldMapping.isPresent()) {
//                continue;
//            }
//
//            combineClass.createFieldMapping(fieldMapping.getObfuscatedName(), newFieldMapping.get().getDeobfuscatedName());
//        }
//
//        for (MethodMapping methodMapping : oldClass.getMethodMappings()) {
//            MethodMatch match = classMatch.oldObfWithDescToMethodMatch.get(methodMapping.getObfuscatedName() + methodMapping.getObfuscatedDescriptor());
//            Optional<MethodMapping> newMethodMapping = newClass.getMethodMapping(match.newName, match.newSignature);
//
//            if (!newMethodMapping.isPresent()) {
//                continue;
//            }
//
//            combineClass.createMethodMapping(methodMapping.getSignature(), newMethodMapping.get().getDeobfuscatedName());
//        }
//
//        for (InnerClassMapping innerClassMapping : oldClass.getInnerClassMappings()) {
//            ClassMatch match = this.oldObfToMatch.get(innerClassMapping.getFullObfuscatedName());
//            match.
//        }
//    }

    /**
     * Updates Mapping Sets with this Match
     *
     * @param oldMappings old mapping set to use, old obf -> named
     * @return new mapping set, new obf -> named
     */
    public MappingSet updateMappings(MappingSet oldMappings) {
        MappingSet updatedMappings = MappingSet.create();
        for (ClassMatch oldClassMatch : this.classMatches) {
            oldMappings.getClassMapping(oldClassMatch.oldName).ifPresent(oldClassMapping -> {
                ClassMapping<?, ?> classMapping = updatedMappings.getOrCreateClassMapping(oldClassMatch.newName);
                classMapping.setDeobfuscatedName(oldClassMapping.getFullDeobfuscatedName());

                // add field mappings
                for (FieldMatch fieldMatch : oldClassMatch.fieldMatches) {
                    oldClassMapping.getFieldMapping(fieldMatch.oldName).ifPresent(fieldMapping -> {
                        classMapping.getOrCreateFieldMapping(fieldMatch.newName)
                                .setDeobfuscatedName(fieldMapping.getDeobfuscatedName());
                    });
                }

                // add method mappings
                for (MethodMatch methodMatch : oldClassMatch.methodMatches) {
                    oldClassMapping.getMethodMapping(methodMatch.oldName, methodMatch.oldSignature).ifPresent(oldMethodMapping -> {
                        classMapping.getOrCreateMethodMapping(methodMatch.newName, methodMatch.newSignature)
                                .setDeobfuscatedName(oldMethodMapping.getDeobfuscatedName());
                    });
                }
            });
        }
        return updatedMappings;
    }

    public Match reverse() {
        List<ClassMatch> reversedClassMatches = new ArrayList<>();
        for (ClassMatch classMatch : this.classMatches) {
            ClassMatch reversedClassMatch = new ClassMatch(classMatch.newName, classMatch.oldName);
            reversedClassMatches.add(reversedClassMatch);
            for (FieldMatch fieldMatch : classMatch.fieldMatches) {
                FieldMatch reversedFieldMatch = new FieldMatch(fieldMatch.newName, fieldMatch.newFieldType, fieldMatch.oldName, fieldMatch.oldFieldType);
                reversedClassMatch.fieldMatches.add(reversedFieldMatch);
            }
            for (MethodMatch methodMatch : classMatch.methodMatches) {
                MethodMatch reversedMethodMatch = new MethodMatch(methodMatch.newName, methodMatch.newSignature, methodMatch.oldName, methodMatch.newSignature);
                reversedClassMatch.methodMatches.add(reversedMethodMatch);
            }
        }

        return new Match(reversedClassMatches);
    }

    public static Match parse(File file) {
        assert file.exists() && file.isFile() : file.getAbsolutePath() + " is not a file!";

        try {
            List<ClassMatch> classMatches = new ArrayList<>();
            List<String> lines = Files.readAllLines(file.toPath());

            ClassMatch currentClass = null;

            for (String line : lines) {
                if (!line.contains("\t")) continue;
                String[] parts = line.split("\t");
                if (line.startsWith("c\t")) {
                    // parse class
                    String oldName = parts[1].substring(1, parts[1].length() - 1);
                    String newName = parts[2].substring(1, parts[2].length() - 1);
                    currentClass = new ClassMatch(oldName, newName);
                    classMatches.add(currentClass);
                } else if (line.startsWith("\tm\t")) {
                    if (currentClass == null) continue;
                    // parse method
                    String oldDesc = parts[2];
                    int pos = oldDesc.indexOf('(');
                    String oldName = oldDesc.substring(0, pos);
                    String oldSignature = oldDesc.substring(pos);

                    String newDesc = parts[3];
                    pos = newDesc.indexOf('(');
                    String newName = newDesc.substring(0, pos);
                    String newSignature = newDesc.substring(pos);

                    currentClass.methodMatches.add(new MethodMatch(oldName, oldSignature, newName, newSignature));
                } else if (line.startsWith("\tf\t")) {
                    if (currentClass == null) continue;
                    // parse field
                    String oldDesc = parts[2];
                    String[] oldDescParts = oldDesc.split(";;");
                    String oldName = oldDescParts[0];
                    String oldSignature = oldDescParts[1];
                    String newDesc = parts[3];
                    String[] newDescParts = newDesc.split(";;");
                    String newName = newDescParts[0];
                    String newSignature = newDescParts[1];
                    currentClass.fieldMatches.add(new FieldMatch(oldName, oldSignature, newName, newSignature));
                }

            }
            return new Match(classMatches);
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        }
    }

    private static void parseClassMapping(List<ClassMatch> classMatches, ClassMapping<?, ?> topLevelClassMapping) {
        ClassMatch classMatch = new ClassMatch(topLevelClassMapping.getFullObfuscatedName(), topLevelClassMapping.getFullDeobfuscatedName());
        classMatches.add(classMatch);
        for (FieldMapping fieldMapping : topLevelClassMapping.getFieldMappings()) {
            classMatch.fieldMatches.add(new FieldMatch(fieldMapping.getObfuscatedName(),
                    fieldMapping.getSignature().toString(), fieldMapping.getDeobfuscatedName(),
                    fieldMapping.getDeobfuscatedSignature().toString()));
        }
        for (MethodMapping methodMatch : topLevelClassMapping.getMethodMappings()) {
            classMatch.methodMatches.add(new MethodMatch(methodMatch.getObfuscatedName(),
                    methodMatch.getSignature().toString(), methodMatch.getDeobfuscatedName(),
                    methodMatch.getDeobfuscatedSignature().toString()));
        }
        for (InnerClassMapping innerClassMapping : topLevelClassMapping.getInnerClassMappings()) {
            parseClassMapping(classMatches, innerClassMapping);
        }
    }
}
