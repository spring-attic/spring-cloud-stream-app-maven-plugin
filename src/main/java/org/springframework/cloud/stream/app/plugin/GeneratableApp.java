package org.springframework.cloud.stream.app.plugin;

import java.util.List;

/**
 * @author Soby Chacko
 */
public class GeneratableApp {

    List<Dependency> dependencies;
    List<String> extraRepository;

    String groupId;

    String description;
    String packageName;
    String generatedProjectHome;

    String starterArtifactSuffix;

    boolean testsIgnored;

    String extraTestConfigClass;

    public String getExtraTestConfigClass() {
        return extraTestConfigClass;
    }

    public void setExtraTestConfigClass(String extraTestConfigClass) {
        this.extraTestConfigClass = extraTestConfigClass;
    }

    public boolean isTestsIgnored() {
        return testsIgnored;
    }

    public void setTestsIgnored(boolean testsIgnored) {
        this.testsIgnored = testsIgnored;
    }

    public List<Dependency> getDependencies() {
        return dependencies;
    }

    public void setDependencies(List<Dependency> dependencies) {
        this.dependencies = dependencies;
    }

    public List<String> getExtraRepository() {
        return extraRepository;
    }

    public void setExtraRepository(List<String> extraRepository) {
        this.extraRepository = extraRepository;
    }

    public String getGroupId() {
        return groupId;
    }

    public void setGroupId(String groupId) {
        this.groupId = groupId;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getPackageName() {
        return packageName;
    }

    public void setPackageName(String packageName)
    {
        this.packageName = packageName;
    }

    public String getStarterArtifactSuffix() {
        return starterArtifactSuffix;
    }

    public void setStarterArtifactSuffix(String starterArtifactSuffix) {
        this.starterArtifactSuffix = starterArtifactSuffix;
    }

    @Override
    public String toString() {
        return "GeneratableApp{" +
                "dependencies=" + dependencies +
                ", groupId='" + groupId + '\'' +
                ", description='" + description + '\'' +
                ", packageName='" + packageName + '\'' +
                '}';
    }

    public void setGeneratedProjectHome(String generatedProjectHome) {
        this.generatedProjectHome = generatedProjectHome;
    }

    public String getGeneratedProjectHome() {
        return generatedProjectHome;
    }
}
