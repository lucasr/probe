/*
 * Copyright (C) 2014 Lucas Rocha
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

package org.lucasr.probe

import com.android.annotations.NonNull
import com.android.build.gradle.AppPlugin

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.tasks.StopExecutionException
import org.gradle.internal.reflect.Instantiator
import org.gradle.tooling.provider.model.ToolingModelBuilderRegistry

import org.lucasr.probe.internal.BuildVariantConfigFactory

import javax.inject.Inject

class ProbePlugin implements Plugin<Project> {
    @NonNull
    private final Instantiator instantiator

    @Inject
    public ProbePlugin(@NonNull Instantiator instantiator,
                       @NonNull ToolingModelBuilderRegistry registry) {
        this.instantiator = instantiator
    }

    void apply(Project project) {
        if (!project.plugins.hasPlugin(AppPlugin)) {
            throw new StopExecutionException("'android' plugin has to be applied before")
        }

        def buildVariants = project.container(BuildVariantConfig,
                new BuildVariantConfigFactory(instantiator))
        project.extensions.create("probe", ProbeExtension, instantiator, buildVariants)

        project.afterEvaluate {
            addProbeTasks(project)
        }
    }

    private void addProbeTasks(project) {
        project.android.applicationVariants.each { variant ->
            def buildVariant = project.probe.buildVariants.findByName(variant.name)
            if (buildVariant == null || !buildVariant.getEnabled()) {
                project.logger.lifecycle "Probe disabled for ${variant.name}"
                return
            }

            def sourcePath = "${project.buildDir}/generated/source/probe/${variant.dirName}"
            def packageName = "${variant.mergedFlavor.applicationId}.probe"
            def task = project.tasks.create("probe${variant.name.capitalize()}Views", ProbeTask)

            // Set task properties
            task.variant = variant
            task.packageName = packageName
            task.outputDir = new File("${sourcePath}/${packageName.replace('.', '/')}")
            task.inputFiles = project.fileTree(dir: variant.mergeResources.outputDir)
                                     .matching { include 'layout*/*.xml' }

            // Set task dependencies
            task.dependsOn variant.mergeResources
            variant.javaCompile.source sourcePath
            variant.javaCompile.dependsOn task
        }
    }
}
