package me.ramidzkh.yarnforge.task;

import com.google.common.collect.Iterables;
import org.cadixdev.mercury.Mercury;
import org.gradle.api.DefaultTask;
import org.gradle.api.Project;
import org.gradle.api.plugins.JavaPluginConvention;
import org.gradle.api.tasks.SourceSet;
import org.gradle.api.tasks.SourceSetContainer;
import org.gradle.api.tasks.TaskAction;
import org.gradle.api.tasks.options.Option;

import java.io.File;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public class SourceSetRemapTask extends BaseRemappingTask {

    private String sourceSets = "main";

    @Option(option = "sourceSets", description = "The source sets to remap")
    public void setSourceSets(String sourceSets) {
        this.sourceSets = sourceSets;
    }

    @TaskAction
    public void doTask() throws Exception {
        Project project = getProject();

        SourceSetContainer sourceSetContainer = project.getConvention().getPlugin(JavaPluginConvention.class).getSourceSets();
        List<SourceSet> sourceSets = Arrays.stream(this.sourceSets.split(";"))
                .map(sourceSetContainer::getByName)
                .collect(Collectors.toList());

        try {
            Mercury mercury = createRemapper();

            for (SourceSet sourceSet : sourceSets) {
                try {
                    DefaultTask.class.getMethod("execute").invoke(project.getTasks().getByName(sourceSet.getCompileJavaTaskName()));
                } catch (ReflectiveOperationException exception) {
                    throw new RuntimeException("SourceSetRemapTask is not available", exception);
                }

                for (File file : sourceSet.getCompileClasspath().getFiles()) {
                    if (file.exists()) {
                        mercury.getClassPath().add(file.toPath());
                    }
                }

                mercury.rewrite(Iterables.getOnlyElement(sourceSet.getAllJava().getSrcDirs()).toPath(), project.file("remapped/" + sourceSet.getName()).toPath());
            }
        } finally {
            System.gc();
        }
    }
}
