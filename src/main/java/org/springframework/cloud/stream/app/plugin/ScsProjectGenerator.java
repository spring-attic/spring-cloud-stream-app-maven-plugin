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
 * @author Soby Chacko
 *
 */
public class ScsProjectGenerator extends ProjectGenerator {

    private String dockerHubOrg;

    @Override
    protected File doGenerateProjectStructure(ProjectRequest request) {

        final File rootDir = super.doGenerateProjectStructure(request);

        final File dir = new File(rootDir, request.getBaseDir());

        final File dockerDir = new File(dir, "src/main/docker");
        dockerDir.mkdirs();
        write(new File(dockerDir, "assembly.xml"), "assembly.xml", initializeModel(request));

        final File inputFile = new File(dir, "pom.xml");
        final File tempOutputFile1 = new File(dir, "pom_tmp1.xml");
        final File tempOutputFile2 = new File(dir, "pom_tmp2.xml");

        try {
            final InputStream is = new FileInputStream(inputFile);
            final OutputStream os = new FileOutputStream(tempOutputFile1);
            MavenModelUtils.addDockerPlugin(request.getArtifactId(), request.getVersion(), dockerHubOrg, is, os);

            FileInputStream is1 = new FileInputStream(tempOutputFile1);
            FileOutputStream os1 = new FileOutputStream(tempOutputFile2);

            MavenModelUtils.addSurefirePlugin(is1, os1);

            is.close();
            is1.close();
            os.close();
            os1.close();
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }


        inputFile.delete();
        tempOutputFile2.renameTo(inputFile);
        tempOutputFile1.delete();
        tempOutputFile2.delete();

        return rootDir;

    }

    public void setDockerHubOrg(String dockerHubOrg) {
        this.dockerHubOrg = dockerHubOrg;
    }
}
