package org.springframework.cloud.stream.app.plugin;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import org.springframework.cloud.stream.app.plugin.utils.MavenModelUtils;

import io.spring.initializr.generator.ProjectGenerator;
import io.spring.initializr.generator.ProjectRequest;

/**
 *
 * @author Gunnar Hillert
 *
 */
public class ScsProjectGenerator extends ProjectGenerator {

    @Override
    protected File doGenerateProjectStructure(ProjectRequest request) {

        final File rootDir = super.doGenerateProjectStructure(request);

        final File dir = new File(rootDir, request.getBaseDir());

        final File dockerDir = new File(dir, "src/main/docker");
        dockerDir.mkdirs();
        write(new File(dockerDir, "assembly.xml"), "assembly.xml", initializeModel(request));

        final File inputFile = new File(dir, "pom.xml");
        final File tempOutputFile = new File(dir, "pom_tmp.xml");

        try {
            final InputStream is = new FileInputStream(inputFile);
            final OutputStream os = new FileOutputStream(tempOutputFile);
            MavenModelUtils.addDockerPlugin(request.getArtifactId(), is, os);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }

        inputFile.delete();
        tempOutputFile.renameTo(inputFile);
        tempOutputFile.delete();

        return rootDir;

    }

}
