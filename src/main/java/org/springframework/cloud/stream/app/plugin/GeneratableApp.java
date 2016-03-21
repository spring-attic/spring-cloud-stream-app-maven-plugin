package org.springframework.cloud.stream.app.plugin;

import java.util.List;

/**
 * @author Soby Chacko
 */
public class GeneratableApp {

    List<Dependency> dependencies;

    String groupId;
    String description;
    String packageName;

    public List<Dependency> getDependencies() {
        return dependencies;
    }

    public void setDependencies(List<Dependency> dependencies) {
        this.dependencies = dependencies;
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

    public void setPackageName(String packageName) {
        this.packageName = packageName;
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
}
