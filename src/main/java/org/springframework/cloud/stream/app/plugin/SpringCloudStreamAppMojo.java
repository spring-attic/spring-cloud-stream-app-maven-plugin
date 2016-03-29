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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.apache.maven.model.Model;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.codehaus.plexus.util.StringUtils;
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

    private static final String SPRING_CLOUD_STREAM_APP_STARTER_GROUP_ID = "org.springframework.cloud.stream.app";

    private static final String SPRING_CLOUD_STREAM_BINDER_GROUP_ID = "org.springframework.cloud";

    @Parameter
    private Map<String, GeneratableApp> generatedApps;

    @Parameter
    private org.springframework.cloud.stream.app.plugin.Dependency bom;

    @Parameter
    private String generatedProjectHome;

    @Parameter
    private String javaVersion;

    @Parameter
    private String generatedProjectVersion;

    private ScsProjectGenerator projectGenerator = new ScsProjectGenerator();

    public void execute() throws MojoExecutionException, MojoFailureException {

        final InitializrDelegate initializrDelegate = new InitializrDelegate();

        initializrDelegate.prepareProjectGenerator();
        try {
            for (Map.Entry<String, GeneratableApp> entry : generatedApps.entrySet()) {
                GeneratableApp value = entry.getValue();
                List<org.springframework.cloud.stream.app.plugin.Dependency> dependencies = value.getDependencies();
                File project;
                if (dependencies == null) {
                    //No dependencies provided. Fall back to default mechanism and ignore any other attribute provided by the user
                    //This is specifically for the spring cloud stream app use cases. We expect the convention of
                    //<technology>-<source>|<sink>|...-<binder-type>

                    String appArtifactId = entry.getKey();
                    String starterArtifactId = constructStarterArtifactId(appArtifactId);
                    String binderArtifactId = constructBinderArtifact(appArtifactId);
                    Dependency starterDep = getDependency(starterArtifactId, SPRING_CLOUD_STREAM_APP_STARTER_GROUP_ID);
                    Dependency binderDep = getDependency(binderArtifactId, SPRING_CLOUD_STREAM_BINDER_GROUP_ID);

                    InitializrMetadata metadata = SpringCloudStreamAppMetadataBuilder.withDefaults()
                            .addBom(bom.getName(), bom.getGroupId(), bom.getArtifactId(), bom.getVersion())
                            .addJavaVersion(javaVersion)
                            .addDependencyGroup(appArtifactId, starterDep, binderDep).build();
                    initializrDelegate.applyMetadata(metadata);
                    ProjectRequest projectRequest = initializrDelegate.getProjectRequest(entry.getKey(), SPRING_CLOUD_STREAM_APP_STARTER_GROUP_ID,
                            getDescription(appArtifactId), getPackageName(appArtifactId),
                            generatedProjectVersion, starterArtifactId, binderArtifactId);
                    project = projectGenerator.doGenerateProjectStructure(projectRequest);
                }
                else {
                    //user provided dependencies and other metadata
                    String[] artifactNames = artifactNames(dependencies);
                    Dependency[] artifacts = artifacts(dependencies);

                    InitializrMetadata metadata = SpringCloudStreamAppMetadataBuilder.withDefaults()
                            .addBom(bom.getName(), bom.getGroupId(), bom.getArtifactId(), bom.getVersion())
                            .addJavaVersion(javaVersion)
                            .addDependencyGroup(entry.getKey(), artifacts).build();

                    initializrDelegate.applyMetadata(metadata);

                    ProjectRequest projectRequest = initializrDelegate.getProjectRequest(entry.getKey(), value.getGroupId(),
                            value.getDescription(), value.getPackageName(), generatedProjectVersion, artifactNames);
                    project = projectGenerator.doGenerateProjectStructure(projectRequest);
                }

                File generatedProjectHome = StringUtils.isNotEmpty(this.generatedProjectHome) ?
                        new File(this.generatedProjectHome) :
                        StringUtils.isNotEmpty(value.getGeneratedProjectHome()) ? new File(value.generatedProjectHome) :
                                null;

                if (generatedProjectHome != null && project != null) {
                    moveProjectWithMavenModelsUpdated(entry.getKey(), project, generatedProjectHome);
                }
                else if (project != null) {
                    //no user provided generated project home, fall back to the default used by the Initailzr
                    getLog().info("Project is generated at " + project.toString());
                }
            }
        }
        catch (IOException | XmlPullParserException e) {
            throw new IllegalStateException(e);
        }
    }

    private String getPackageName(String artifactId) {
        int countSep = StringUtils.countMatches(artifactId, "-");
        String[] strings = Stream.of(artifactId.split("-"))
                .limit(countSep)
                .toArray(String[]::new);

        String join = StringUtils.join(strings, ".");
        return String.format("%s.%s", SPRING_CLOUD_STREAM_APP_STARTER_GROUP_ID, join);
    }

    private String getDescription(String artifactId) {
        String[] strings = Stream.of(artifactId.split("-"))
                .map(StringUtils::capitalizeFirstLetter)
                .toArray(String[]::new);
        String join = StringUtils.join(strings, " ");
        return String.format("%s %s %s", "Spring Cloud Stream", join, "Binder Application");
    }

    private Dependency getDependency(String s, String groupId) {
        Dependency dependency = new Dependency();
        dependency.setId(s);
        dependency.setGroupId(groupId);
        dependency.setArtifactId(s);
        dependency.setBom(bom.getName());
        return dependency;
    }

    private String constructStarterArtifactId(String artifactId) {
        int countSep = StringUtils.countMatches(artifactId, "-");
        List<String> s = new ArrayList<>();
        Stream.of(artifactId.split("-"))
                .limit(countSep)
                .collect(Collectors.toCollection(LinkedList::new))
                .descendingIterator()
                .forEachRemaining(s::add);

        String collect = s.stream().collect(Collectors.joining("-"));
        return String.format("%s-%s", "spring-cloud-starter-stream", collect);
    }

    private String constructBinderArtifact(String artifactId) {
        int countSep = StringUtils.countMatches(artifactId, "-");
        Optional<String> first = Stream.of(artifactId.split("-"))
                .skip(countSep)
                .findFirst();
        return String.format("%s-%s", "spring-cloud-stream-binder", first.get());
    }

    private void moveProjectWithMavenModelsUpdated(String key, File project,
                                                   File generatedProjectHome) throws IOException, XmlPullParserException {
        Model model = isNewDir(generatedProjectHome) ? MavenModelUtils.populateModel(generatedProjectHome.getName(),
                "org.springframework.cloud.stream.apps", "1.0.0.BUILD-SNAPSHOT")
                : MavenModelUtils.getModelFromContainerPom(generatedProjectHome);

        if (model != null && MavenModelUtils.addModuleIntoModel(model, key)) {
            String containerPomFile = String.format("%s/%s", generatedProjectHome, "pom.xml");
            MavenModelUtils.writeModelToFile(model, new FileOutputStream(containerPomFile));
        }

        try {
            String generatedAppHome = String.format("%s/%s", generatedProjectHome, key);
            removeExistingContent(Paths.get(generatedAppHome));

            Files.move(Paths.get(project.toString(), key), Paths.get(generatedAppHome));
            SpringCloudStreamPluginUtils.ignoreUnitTestGeneratedByInitializer(generatedAppHome);
        }
        catch (IOException e) {
            getLog().error("Error during plugin execution", e);
            throw new IllegalStateException(e);
        }
        MavenModelUtils.addModuleInfoToContainerPom(generatedProjectHome);
    }

    private boolean isNewDir(File genProjecthome) {
        return (!genProjecthome.exists() && genProjecthome.mkdir());
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

    private void removeExistingContent(Path path) {
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

        private ProjectRequest getProjectRequest(String generatedArtifactId, String generatedAppGroupId,
                                                 String description, String packageName, String version,
                                                 String... artifactNames) {
            ProjectRequest projectRequest = createProjectRequest(artifactNames);
            projectRequest.setBaseDir(generatedArtifactId);

            projectRequest.setGroupId(generatedAppGroupId);
            projectRequest.setArtifactId(generatedArtifactId);
            projectRequest.setName(generatedArtifactId);
            projectRequest.setDescription(description);
            projectRequest.setPackageName(packageName);
            projectRequest.setVersion(version);
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
                    getLog().debug("Generated project : " + applicationEvent.toString());
                }

                public void publishEvent(Object o) {
                    getLog().debug("Generated project : " + o.toString());
                }
            });
        }
    }
}
