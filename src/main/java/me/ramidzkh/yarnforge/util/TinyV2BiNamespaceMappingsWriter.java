package me.ramidzkh.yarnforge.util;

import org.cadixdev.bombe.type.FieldType;
import org.cadixdev.lorenz.MappingSet;
import org.cadixdev.lorenz.io.TextMappingsWriter;
import org.cadixdev.lorenz.model.FieldMapping;
import org.cadixdev.lorenz.model.MethodMapping;

import java.io.Writer;

public class TinyV2BiNamespaceMappingsWriter extends TextMappingsWriter {

    private final String namespaceFrom;
    private final String namespaceTo;

    public TinyV2BiNamespaceMappingsWriter(Writer writer, String namespaceFrom, String namespaceTo) {
        super(writer);
        this.namespaceFrom = namespaceFrom;
        this.namespaceTo = namespaceTo;
    }

    @Override
    public void write(MappingSet mappings) {
        writer.println("tiny\t2\t0\t" + namespaceFrom + "\t" + namespaceTo);

        MappingBridge.iterateClasses(mappings, classMapping -> {
            writer.println("c\t" + classMapping.getFullObfuscatedName() + "\t" + classMapping.getFullDeobfuscatedName());

            for (FieldMapping fieldMapping : classMapping.getFieldMappings()) {
                FieldType fieldType = fieldMapping.getType().orElse(null);
                writer.println("\tf\t" + fieldType + "\t" + fieldMapping.getObfuscatedName() + "\t" + fieldMapping.getDeobfuscatedName());
            }

            for (MethodMapping methodMapping : classMapping.getMethodMappings()) {
                writer.println("\tm\t" + methodMapping.getSignature().getDescriptor() + "\t" + methodMapping.getObfuscatedName() + "\t" + methodMapping.getDeobfuscatedName());
            }
        });
    }
}
