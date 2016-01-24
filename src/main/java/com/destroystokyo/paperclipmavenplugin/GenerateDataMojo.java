package com.destroystokyo.paperclipmavenplugin;

import com.sun.codemodel.ClassType;
import com.sun.codemodel.JAnnotationUse;
import com.sun.codemodel.JArray;
import com.sun.codemodel.JClassAlreadyExistsException;
import com.sun.codemodel.JCodeModel;
import com.sun.codemodel.JDefinedClass;
import com.sun.codemodel.JDocComment;
import com.sun.codemodel.JExpr;
import com.sun.codemodel.JMod;
import org.apache.commons.compress.compressors.CompressorException;
import org.apache.commons.io.FileUtils;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;
import org.jbsdiff.Diff;
import org.jbsdiff.InvalidHeaderException;

import javax.annotation.Generated;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Date;

@Mojo(name = "generate-data", defaultPhase = LifecyclePhase.GENERATE_SOURCES)
public class GenerateDataMojo extends AbstractMojo {

    @Parameter(required = true)
    private File vanillaMinecraft;

    @Parameter(required = true)
    private File paperMinecraft;

    @Parameter(defaultValue = "target/generated-sources/java", required = false)
    private File generatedSourceLocation;

    @Parameter(defaultValue = "src/main/resources", required = false)
    private File generatedResourceLocation;

    @Parameter(defaultValue = "${project}", readonly = true, required = true)
    private MavenProject project;

    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {
        // Create the directory if needed
        if (!generatedSourceLocation.exists()) {
            try {
                FileUtils.forceMkdir(generatedSourceLocation);
                FileUtils.forceDelete(generatedResourceLocation);
                FileUtils.forceMkdir(generatedResourceLocation);
            } catch (IOException e) {
                throw new MojoExecutionException("Could not create source directory", e);
            }
        }
        project.addCompileSourceRoot(generatedSourceLocation.getAbsolutePath());

        if (!vanillaMinecraft.exists()) {
            throw new MojoExecutionException("vanillaMinecraft jar does not exist!");
        }

        if (!paperMinecraft.exists()) {
            throw new MojoExecutionException("paperMinecraft jar does not exist!");
        }

        // Read the files into memory
        getLog().info("Reading jars into memory");
        final byte[] vanillaMinecraftBytes;
        final byte[] paperMinecraftBytes;
        try {
            vanillaMinecraftBytes = Files.readAllBytes(vanillaMinecraft.toPath());
            paperMinecraftBytes = Files.readAllBytes(paperMinecraft.toPath());
        } catch (IOException e) {
            throw new MojoExecutionException("Error reading jars", e);
        }

        getLog().info("Creating patch");
        try {
            final FileOutputStream paperMinecraftPatch = new FileOutputStream(new File(generatedResourceLocation, "paperMC.patch"));

            Diff.diff(vanillaMinecraftBytes, paperMinecraftBytes, paperMinecraftPatch);
        } catch (InvalidHeaderException | IOException | CompressorException e) {
            throw new MojoExecutionException("Error creating patches", e);
        }

        final JCodeModel model = new JCodeModel();
        final JDefinedClass definedClass;

        try {
            Date now = new Date();

            definedClass = model._class("com.destroystokyo.paperclip.Data", ClassType.CLASS);

            // Add generated comment
            JDocComment comment = definedClass.javadoc();
            comment.append("Data generated on " + now.toString() + "\n\n");

            // Add annotations
            JAnnotationUse generated = definedClass.annotate(Generated.class);
            generated.param("value", GenerateDataMojo.class.getCanonicalName());
            generated.param("date", now.toString());

            // Add the SHA-256 hashes for the files
            MessageDigest digest = MessageDigest.getInstance("SHA-256");

            getLog().info("Hashing files");
            final byte[] vanillaMinecraftHash = digest.digest(vanillaMinecraftBytes);
            final byte[] paperMinecraftHash = digest.digest(paperMinecraftBytes);

            JArray vanillaMinecraftHashArray = JExpr.newArray(model.BYTE);
            for (byte b : vanillaMinecraftHash) {
                vanillaMinecraftHashArray.add(JExpr.lit(b));
            }
            definedClass.field(JMod.PUBLIC | JMod.STATIC | JMod.FINAL, byte[].class, "vanillaMinecraftHash", vanillaMinecraftHashArray);

            JArray paperMinecraftHashArray = JExpr.newArray(model.BYTE);
            for (byte b : paperMinecraftHash) {
                paperMinecraftHashArray.add(JExpr.lit(b));
            }
            definedClass.field(JMod.PUBLIC | JMod.STATIC | JMod.FINAL, byte[].class, "paperMinecraftHash", paperMinecraftHashArray);

            Date end = new Date();
            long time = end.getTime() - now.getTime();
            comment.append("Generated in: " + time + " ms");

            getLog().info("Generating Data class");
            PrintStream tempStream = System.out;
            System.setOut(new PrintStream(new OutputStream() {
                @Override
                public void write(int b) throws IOException {
                    // do nothing
                }
            }));
            model.build(generatedSourceLocation);
            System.setOut(tempStream);
        } catch (JClassAlreadyExistsException | NoSuchAlgorithmException | IOException e) {
            throw new MojoExecutionException("Error generating Data.java", e);
        }
    }
}
