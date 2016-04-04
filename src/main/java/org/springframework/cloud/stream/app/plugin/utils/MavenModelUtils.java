package org.springframework.cloud.stream.app.plugin.utils;

import java.io.File;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.util.Collections;

import org.apache.maven.model.BuildBase;
import org.apache.maven.model.Model;
import org.apache.maven.model.Plugin;
import org.apache.maven.model.PluginExecution;
import org.apache.maven.model.Profile;
import org.apache.maven.model.io.xpp3.MavenXpp3Reader;
import org.apache.maven.model.io.xpp3.MavenXpp3Writer;
import org.codehaus.plexus.util.xml.Xpp3Dom;
import org.codehaus.plexus.util.xml.pull.XmlPullParserException;

/**
 * @author Soby Chacko
 * @author Gunnar Hillert
 */
public class MavenModelUtils {

    private MavenModelUtils() {

    }

    public static Model populateModel(String artifactId, String groupId, String version) {
        Model model = new Model();
        model.setGroupId(groupId);
        model.setArtifactId(artifactId);
        model.setPackaging("pom");
        model.setVersion(version);
        model.setModelVersion("4.0.0");
        return model;
    }

    public static Model getModelFromContainerPom(File genProjecthome) throws IOException, XmlPullParserException {
        File pom = new File(genProjecthome, "pom.xml");
        return pom.exists() ? getModel(pom) : null;
    }

    public static void addModuleInfoToContainerPom(File genProjecthome) throws IOException, XmlPullParserException {
        File parentFile = genProjecthome.getParentFile();
        File containerProjectPom = new File(parentFile, "pom.xml");
        if (containerProjectPom.exists()) {
            Model containerModel = getModel(containerProjectPom);
            if (addModuleIntoModel(containerModel, genProjecthome.getName())) {
                writeModelToFile(containerModel, new FileOutputStream(containerProjectPom));
            }
        }
    }

    public static boolean addModuleIntoModel(Model model, String module) {
        if (!model.getModules().contains(module)) {
            model.addModule(module);
        }
        return true;
    }

    public static void writeModelToFile(Model model, OutputStream os) throws IOException {
        final MavenXpp3Writer writer = new MavenXpp3Writer();
        OutputStreamWriter w = new OutputStreamWriter(os, "utf-8");
        writer.write(w, model);
    }

    private static Model getModel(File pom) throws IOException, XmlPullParserException {
        MavenXpp3Reader reader = new MavenXpp3Reader();
        return reader.read(new FileReader(pom));
    }

    public static void addDockerPlugin(String artifactId, InputStream is, OutputStream os) throws IOException {
        final MavenXpp3Reader reader = new MavenXpp3Reader();

        Model pomModel;
        try {
            pomModel = reader.read(is);
        }
        catch (IOException | XmlPullParserException e) {
            throw new IllegalStateException(e);
        }

        final Plugin dockerPlugin = new Plugin();
        dockerPlugin.setGroupId("io.fabric8");
        dockerPlugin.setArtifactId("docker-maven-plugin");
        dockerPlugin.setVersion("0.14.2");

        final Xpp3Dom mavenPluginConfiguration = new Xpp3Dom("configuration");

        final Xpp3Dom images = addElement(mavenPluginConfiguration, "images");

        final Xpp3Dom image = addElement(images, "image");
        addElement(image, "name", "springcloudstream/${project.artifactId}");

        final Xpp3Dom build = addElement(image, "build");
        addElement(build, "from", "anapsix/alpine-java:8");

        final Xpp3Dom volumes = addElement(build, "volumes");
        addElement(volumes, "volume", "/tmp");

        final Xpp3Dom entryPoint = new Xpp3Dom("entryPoint");
        build.addChild(entryPoint);

        final Xpp3Dom exec = new Xpp3Dom("exec");
        entryPoint.addChild(exec);

        addElement(exec, "arg", "java");
        addElement(exec, "arg", "-jar");
        addElement(exec, "arg", "/maven/" + artifactId + ".jar");

        final Xpp3Dom assembly = addElement(build, "assembly");
        addElement(assembly, "descriptor", "assembly.xml");

        dockerPlugin.setConfiguration(mavenPluginConfiguration);
        //pomModel.getBuild().addPlugin(dockerPlugin);

        //pomModel.getProperties().setProperty("docker.image", artifactId);

        pomModel.getProperties().setProperty("default.docker.goal", "build");

        PluginExecution pluginExecution = new PluginExecution();
        pluginExecution.setPhase("package");
        pluginExecution.setGoals(Collections.singletonList("${default.docker.goal}"));
        dockerPlugin.addExecution(pluginExecution);

        final Profile dockerBuildProfile = new Profile();
        dockerBuildProfile.setId("docker");
        dockerBuildProfile.setBuild(new BuildBase());
        dockerBuildProfile.getBuild().addPlugin(dockerPlugin);

        pomModel.addProfile(dockerBuildProfile);
        pomModel.toString();
        writeModelToFile(pomModel, os);
    }

    private static Xpp3Dom addElement(Xpp3Dom parentElement, String elementName) {
        return addElement(parentElement, elementName, null);
    }

    private static Xpp3Dom addElement(Xpp3Dom parentElement, String elementName, String elementValue) {
        Xpp3Dom child = new Xpp3Dom(elementName);
        child.setValue(elementValue);
        parentElement.addChild(child);
        return child;
    }
}

