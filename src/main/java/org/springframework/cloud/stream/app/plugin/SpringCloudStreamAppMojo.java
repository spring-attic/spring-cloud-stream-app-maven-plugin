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

import io.spring.initializr.generator.ProjectRequest;
import io.spring.initializr.metadata.Dependency;
import io.spring.initializr.metadata.InitializrMetadata;
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
import org.springframework.util.CollectionUtils;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static java.util.stream.Collectors.toList;

/**
 * @author Soby Chacko
 */
@Mojo(name = "generate-app")
public class SpringCloudStreamAppMojo extends AbstractMojo {

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

	@Parameter
	private String applicationType;

	@Parameter
	private List<Repository> extraRepositories;

	@Parameter
	private Map<String, String> binders = new HashMap<>();

	private ScsProjectGenerator projectGenerator = new ScsProjectGenerator();

	public void execute() throws MojoExecutionException, MojoFailureException {

		for (String b : binders.keySet()) {
			System.out.println("soby foo" + b);
		}

		projectGenerator.setDockerHubOrg("springcloud" + applicationType);

		final InitializrDelegate initializrDelegate = new InitializrDelegate();
		final String generatedAppGroupId = getApplicationGroupId(applicationType);

		initializrDelegate.prepareProjectGenerator();

		try {
			for (Map.Entry<String, GeneratableApp> entry : generatedApps.entrySet()) {
				for (String binder : binders.keySet()) {
					GeneratableApp value = entry.getValue();
					List<org.springframework.cloud.stream.app.plugin.Dependency> dependencies = value.getDependencies();
					File project;

					List<Dependency> deps = new ArrayList<>();
					List<String> artifactIds = new ArrayList<>();

					String appArtifactId = entry.getKey() + "-" + binder;
					String starterArtifactId = getStarterArtifactId(value, appArtifactId);
					Dependency starterDep = getDependency(starterArtifactId, generatedAppGroupId);
					deps.add(starterDep);
					artifactIds.add(starterArtifactId);
					if (applicationType.equals("stream")) { //TODO: convert enum
						String binderArtifactId = constructBinderArtifact(binder);
						Dependency binderDep = getDependency(binderArtifactId, SPRING_CLOUD_STREAM_BINDER_GROUP_ID);
						deps.add(binderDep);
						artifactIds.add(binderArtifactId);
					}
					if (StringUtils.isNotEmpty(value.getExtraTestConfigClass())) {
						deps.add(getDependency("app-starters-test-support", "org.springframework.cloud.stream.app"));
						artifactIds.add("app-starters-test-support");
					}
					Dependency[] depArray = deps.toArray(new Dependency[deps.size()]);
					String[] artifactNames = artifactIds.toArray(new String[artifactIds.size()]);
					List<Repository> extraReposToAdd = new ArrayList<>();
					List<String> extraRepoIds = value.extraRepository;
					if (!CollectionUtils.isEmpty(extraRepositories) && !CollectionUtils.isEmpty(extraRepoIds)) {
						extraReposToAdd = extraRepositories.stream().filter(e -> extraRepoIds.contains(e.getId()))
								.collect(Collectors.toList());
					}
					String[] repoIds = new String[]{};
					if (!CollectionUtils.isEmpty(extraRepoIds)) {
						repoIds = new String[extraRepoIds.size()];
						repoIds = extraRepoIds.toArray(repoIds);
					}
					InitializrMetadata metadata = SpringCloudStreamAppMetadataBuilder.withDefaults()
							.addRepositories(extraReposToAdd)
							.addBom(bom.getName(), bom.getGroupId(), bom.getArtifactId(), bom.getVersion(), repoIds)
							.addJavaVersion(javaVersion)
							.addDependencyGroup(appArtifactId, depArray).build();
					initializrDelegate.applyMetadata(metadata);
					ProjectRequest projectRequest = initializrDelegate.getProjectRequest(appArtifactId, generatedAppGroupId,
							getDescription(appArtifactId), getPackageName(appArtifactId),
							generatedProjectVersion, artifactNames);
					project = projectGenerator.doGenerateProjectStructure(projectRequest);


					File generatedProjectHome = StringUtils.isNotEmpty(this.generatedProjectHome) ?
							new File(this.generatedProjectHome) :
							StringUtils.isNotEmpty(value.getGeneratedProjectHome()) ? new File(value.generatedProjectHome) :
									null;

					if (generatedProjectHome != null && project != null) {
						String generatedAppHome = moveProjectWithMavenModelsUpdated(appArtifactId, project, generatedProjectHome, value.isTestsIgnored());
						Stream<Path> pathStream =
								Files.find(Paths.get(System.getProperty("user.dir")), 3,
										(path, attr) -> String.valueOf(path).contains(getStarterArtifactId(value, appArtifactId)));

						Optional<Path> first = pathStream.findFirst();
						if (first.isPresent()) {
							Path path = first.get();

							Path readmePath = Paths.get(path.toString(), "README.adoc");
							File readmeDoc = readmePath.toFile();
							if (readmeDoc.exists()) {
								Files.copy(readmePath, Paths.get(generatedAppHome, "README.adoc"));
							}
						}
						if (StringUtils.isNotEmpty(value.getExtraTestConfigClass())) {
							String s = StringUtils.removeAndHump(appArtifactId, "-");
							String s1 = StringUtils.capitalizeFirstLetter(s);
							String clazzInfo = "classes = {\n" +
									"\t\t" + value.getExtraTestConfigClass() + ",\n" +
									"\t\t" + s1 + "Application.class" + " }";
							SpringCloudStreamPluginUtils.addExtraTestConfig(generatedAppHome, clazzInfo);
						}
						addCopyrightToJavaFiles(generatedAppHome);
					} else if (project != null) {
						//no user provided generated project home, fall back to the default used by the Initailzr
						getLog().info("Project is generated at " + project.toString());
					}
				}
			}
		} catch (IOException | XmlPullParserException e) {
			throw new IllegalStateException(e);
		}
	}

	private void addCopyrightToJavaFiles(String generatedAppHome) throws IOException {
		Stream<Path> javaStream =
				Files.find(Paths.get(generatedAppHome), 20,
						(path, attr) -> String.valueOf(path).endsWith(".java"));

		javaStream.forEach(p -> {
			try {
				SpringCloudStreamPluginUtils.addCopyRight(p);
			} catch (IOException e) {
				getLog().warn("Issues adding copyright", e);
			}
		});
	}

	private String getStarterArtifactId(GeneratableApp value, String appArtifactId) {
		return StringUtils.isEmpty(value.getStarterArtifactSuffix()) ?
				constructStarterArtifactId(appArtifactId) : "spring-cloud-starter-stream-" + value.getStarterArtifactSuffix();
	}

	private static String getApplicationGroupId(String applicationType) {
		return String.format("%s.%s.%s", "org.springframework.cloud", applicationType, "app");
	}

	private String getPackageName(String artifactId) {
		String[] strings = Stream.of(artifactId.split("-"))
				.toArray(String[]::new);

		String join = StringUtils.join(strings, ".");
		return String.format("%s.%s", getApplicationGroupId(applicationType), join);
	}

	private String getDescription(String artifactId) {
		String[] strings = Stream.of(artifactId.split("-"))
				.map(StringUtils::capitalizeFirstLetter)
				.toArray(String[]::new);
		String join = StringUtils.join(strings, " ");
		String appSuffix = applicationType.equals("stream") ? "Binder Application" : "Application";
		return String.format("%s %s %s %s", "Spring Cloud", StringUtils.capitalizeFirstLetter(applicationType),
				join, appSuffix);
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
		//stream app starters follow a specific naming pattern - for ex: spring-cloud-starter-source-time-kafka
		//but the artifact id is time-source-kafka
		if (applicationType.equals("stream")) {
			Stream.of(artifactId.split("-"))
					.limit(countSep)
					.collect(Collectors.toCollection(LinkedList::new))
					.descendingIterator()
					.forEachRemaining(s::add);
		} else {
			s.addAll(Stream.of(artifactId.split("-"))
					.limit(countSep)
					.collect(toList()));
		}

		String collect = s.stream().collect(Collectors.joining("-"));
		return String.format("%s-%s-%s", "spring-cloud-starter", applicationType, collect);
	}

	private String constructBinderArtifact(String binder) {
		return String.format("%s-%s", "spring-cloud-stream-binder", binder);
	}

	private String moveProjectWithMavenModelsUpdated(String key, File project,
													 File generatedProjectHome, boolean testIgnored) throws IOException, XmlPullParserException {

		Model model = isNewDir(generatedProjectHome) ? MavenModelUtils.populateModel(generatedProjectHome.getName(),
				getApplicationGroupId(applicationType), "1.0.0.BUILD-SNAPSHOT")
				: MavenModelUtils.getModelFromContainerPom(generatedProjectHome, getApplicationGroupId(applicationType), "1.0.0.BUILD-SNAPSHOT");

		if (model != null && MavenModelUtils.addModuleIntoModel(model, key)) {
			String containerPomFile = String.format("%s/%s", generatedProjectHome, "pom.xml");
			MavenModelUtils.writeModelToFile(model, new FileOutputStream(containerPomFile));
		}

		try {
			String generatedAppHome = String.format("%s/%s", generatedProjectHome, key);
			removeExistingContent(Paths.get(generatedAppHome));

			Files.move(Paths.get(project.toString(), key), Paths.get(generatedAppHome));
			if (testIgnored) {
				SpringCloudStreamPluginUtils.ignoreUnitTestGeneratedByInitializer(generatedAppHome);
			}
			MavenModelUtils.addModuleInfoToContainerPom(generatedProjectHome);
			return generatedAppHome;
		} catch (IOException e) {
			getLog().error("Error during plugin execution", e);
			throw new IllegalStateException(e);
		}
	}

	private boolean isNewDir(File genProjecthome) {
		return (!genProjecthome.exists() && genProjecthome.mkdir());
	}

	private void removeExistingContent(Path path) {
		if (path.toFile().exists()) {
			try {
				SpringCloudStreamPluginUtils.cleanupGenProjHome(path.toFile());
			} catch (IOException e) {
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
