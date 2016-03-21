/*
 * Copyright 2015-2016 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.springframework.cloud.stream.app.plugin;

import static java.util.stream.Collectors.toList;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import org.apache.maven.model.Model;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Component;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;
import org.codehaus.plexus.util.xml.pull.XmlPullParserException;

import org.springframework.cloud.stream.app.plugin.utils.MavenModelUtils;
import org.springframework.cloud.stream.app.plugin.utils.SpringCloudStreamPluginUtils;
import org.springframework.context.ApplicationEvent;
import org.springframework.context.ApplicationEventPublisher;

import io.spring.initializr.generator.ProjectRequest;
import io.spring.initializr.metadata.Dependency;
import io.spring.initializr.metadata.InitializrMetadata;

/**
 * @author Soby Chacko
 */
@Mojo(name = "generate-app")
public class SpringCloudStreamAppMojo extends AbstractMojo {

    @Component
    private MavenProject project;

    @Parameter
    private String basedir;

    @Parameter
    private Map<String, GeneratableApp> generatedApps;

    @Parameter
    private org.springframework.cloud.stream.app.plugin.Dependency bom;

    @Parameter
    private String generatedProjectHome;

    @Parameter
    private String javaVersion;

    private ScsProjectGenerator projectGenerator = new ScsProjectGenerator();

    public void execute() throws MojoExecutionException, MojoFailureException {

        final File genProjecthome = new File(generatedProjectHome);
        final InitializrDelegate initializrDelegate = new InitializrDelegate();

        initializrDelegate.prepareProjectGenerator();
        try {
            for (Map.Entry<String, GeneratableApp> entry : generatedApps.entrySet()) {
                Path path = Paths.get(generatedProjectHome, entry.getKey());
                initialCleanup(path);

                GeneratableApp value = entry.getValue();
                List<org.springframework.cloud.stream.app.plugin.Dependency> dependencies = value.getDependencies();

                String[] artifactNames = artifactNames(dependencies);
                Dependency[] artifacts = artifacts(dependencies);

                InitializrMetadata metadata = SpringCloudStreamAppMetadataBuilder.withDefaults()
                        .addBom(bom.getName(), bom.getGroupId(), bom.getArtifactId(), bom.getVersion())
                        .addJavaVersion(javaVersion)
                        .addDependencyGroup(entry.getKey(), artifacts).build();

                initializrDelegate.applyMetadata(metadata);

                ProjectRequest projectRequest = initializrDelegate.getProjectRequest(entry, value, artifactNames);
                File project = projectGenerator.doGenerateProjectStructure(projectRequest);

                Model model = isNewDir(genProjecthome) ? MavenModelUtils.populateModel(generatedProjectHome,
                        value.getGroupId(), "1.0.0.BUILD-SNAPSHOT")
                        : MavenModelUtils.getModelFromContainerPom(genProjecthome);

                if (MavenModelUtils.addModuleIntoModel(model, entry.getKey())) {
                    String containerPomFile = String.format("%s/%s", generatedProjectHome, "pom.xml");
                    MavenModelUtils.writeModelToFile(model, new FileOutputStream(containerPomFile));
                }

                String generatedAppHome = String.format("%s/%s", generatedProjectHome, entry.getKey());
                try {
                    Files.move(Paths.get(project.toString(), entry.getKey()), Paths.get(generatedAppHome));
                    SpringCloudStreamPluginUtils.ignoreUnitTestGeneratedByInitializer(generatedAppHome, entry.getKey());
                }
                catch (IOException e) {
                    getLog().error("Error during plugin execution", e);
                    throw new IllegalStateException(e);
                }
            }
            MavenModelUtils.addModuleInfoToContainerPom(genProjecthome);
        }
        catch (IOException | XmlPullParserException e) {
            throw new IllegalStateException(e);
        }
    }

    private boolean isNewDir(File genProjecthome) {
        boolean newDir = false;
        if (!genProjecthome.exists()) {
            genProjecthome.mkdir();
            newDir = true;
        }
        return newDir;
    }

    private Dependency[] artifacts(List<org.springframework.cloud.stream.app.plugin.Dependency> dependencies) {
        return dependencies.stream()
                .map(org.springframework.cloud.stream.app.plugin.Dependency::toInitializerMetadataDependency)
                .collect(toList())
                .stream()
                .map(d -> {
                    d.setBom(bom.getName());
                    return d;
                })
                .collect(toList())
                .stream()
                .toArray(Dependency[]::new);
    }

    private String[] artifactNames(List<org.springframework.cloud.stream.app.plugin.Dependency> dependencies) {
        return dependencies.stream()
                .map(org.springframework.cloud.stream.app.plugin.Dependency::getArtifactId)
                .collect(toList())
                .stream()
                .toArray(String[]::new);
    }

    private void initialCleanup(Path path) {
        if (path.toFile().exists()) {
            try {
                SpringCloudStreamPluginUtils.cleanupGenProjHome(path.toFile());
            }
            catch (IOException e) {
                getLog().error("Error", e);
                throw new IllegalStateException(e);
            }
        }
    }

    private class InitializrDelegate {

        private void applyMetadata(final InitializrMetadata metadata) {
            projectGenerator.setMetadataProvider(() -> metadata);
        }

        private ProjectRequest getProjectRequest(Map.Entry<String, GeneratableApp> entry, GeneratableApp value, String[] artifactNames) {
            ProjectRequest projectRequest = createProjectRequest(artifactNames);
            projectRequest.setBaseDir(entry.getKey());

            projectRequest.setGroupId(value.getGroupId());
            projectRequest.setArtifactId(entry.getKey());
            projectRequest.setName(entry.getKey());
            projectRequest.setDescription(value.getDescription());
            projectRequest.setPackageName(value.getPackageName());
            return projectRequest;
        }

        private ProjectRequest createProjectRequest(String... styles) {
            ProjectRequest request = new ProjectRequest();
            request.initialize(projectGenerator.getMetadataProvider().get());
            request.getStyle().addAll(Arrays.asList(styles));
            return request;
        }

        private void prepareProjectGenerator() {
            String tmpdir = System.getProperty("java.io.tmpdir");
            projectGenerator.setTmpdir(tmpdir);
            projectGenerator.setEventPublisher(new ApplicationEventPublisher() {
                public void publishEvent(ApplicationEvent applicationEvent) {
                    getLog().info("Generated project : " + applicationEvent.toString());
                }

                public void publishEvent(Object o) {
                    getLog().info("Generated project : " + o.toString());
                }
            });
        }
    }

}
