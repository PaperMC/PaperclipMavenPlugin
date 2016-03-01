/*
 * Paperclip Maven Plugin - Generates the Data class + patch file for Paperclip
 *
 * Copyright (c) 2016 Kyle Wood (DemonWav)
 * https://github.com/PaperSpigot/Paper
 *
 * MIT License
 */

package com.destroystokyo.paperclipmavenplugin;

import com.google.gson.Gson;
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

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

@Mojo(name = "generate-data", defaultPhase = LifecyclePhase.GENERATE_SOURCES)
public class GenerateDataMojo extends AbstractMojo {

    @Parameter(required = true)
    private File vanillaMinecraft;

    @Parameter(required = true)
    private File paperMinecraft;

    @Parameter(defaultValue = "src/main/resources", required = false)
    private File generatedResourceLocation;

    @Parameter(required = true)
    private String mcVersion;

    @Parameter(defaultValue = "${project}", readonly = true, required = true)
    private MavenProject project;

    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {
        final File patch = new File(generatedResourceLocation, "paperMC.patch");
        final File json = new File(generatedResourceLocation, "patch.json");

        // Create the directory if needed
        if (!generatedResourceLocation.exists()) {
            try {
                FileUtils.forceMkdir(generatedResourceLocation);
                try {
                    FileUtils.forceDelete(patch);
                } catch (FileNotFoundException ignored) {}
            } catch (IOException e) {
                throw new MojoExecutionException("Could not create source directory", e);
            }
        }

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
        try (final FileOutputStream paperMinecraftPatch = new FileOutputStream(patch)) {
            Diff.diff(vanillaMinecraftBytes, paperMinecraftBytes, paperMinecraftPatch);
        } catch (InvalidHeaderException | IOException | CompressorException e) {
            throw new MojoExecutionException("Error creating patches", e);
        }

        // Add the SHA-256 hashes for the files
        final MessageDigest digest;
        try {
            digest = MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new MojoExecutionException("Could not create SHA-256 hasher.", e);
        }

        getLog().info("Hashing files");
        final byte[] vanillaMinecraftHash = digest.digest(vanillaMinecraftBytes);
        final byte[] paperMinecraftHash = digest.digest(paperMinecraftBytes);

        final PatchData data = new PatchData();
        data.setOriginalHash(toHex(vanillaMinecraftHash));
        data.setPatchedHash(toHex(paperMinecraftHash));
        data.setPatch("paperMC.patch");
        data.setSourceUrl("https://s3.amazonaws.com/Minecraft.Download/versions/" + mcVersion + "/minecraft_server." + mcVersion + ".jar");

        data.setVersion(mcVersion);

        getLog().info("Writing json file");
        Gson gson = new Gson();
        String jsonString = gson.toJson(data);

        try (
            final FileOutputStream fs = new FileOutputStream(json);
            final OutputStreamWriter writer = new OutputStreamWriter(fs)
        ) {
            writer.write(jsonString);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private String toHex(final byte[] hash) {
        final StringBuilder sb = new StringBuilder(hash.length * 2);
        for (byte aHash : hash) {
            sb.append(String.format("%02X", aHash & 0xFF));
        }
        return sb.toString();
    }
}
