/*
 * Paperclip Maven Plugin - Generates the Data class + patch file for Paperclip
 *
 * Copyright (c) 2019 Kyle Wood (DemonWav)
 * https://github.com/PaperMC/PaperclipMavenPlugin
 *
 * MIT License
 */

package io.papermc.paperclipmavenplugin;

import com.google.gson.Gson;
import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashMap;
import org.apache.commons.compress.compressors.CompressorException;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.jbsdiff.Diff;
import org.jbsdiff.InvalidHeaderException;

@Mojo(name = "generate-data", defaultPhase = LifecyclePhase.GENERATE_SOURCES)
public class GenerateDataMojo extends AbstractMojo {

    private static final String PROTOCOL_FILE = "io.papermc.paper.daemon.protocol";

    @Parameter(required = true)
    private Path vanillaMinecraft;

    @Parameter(required = true)
    private Path paperMinecraft;

    @Parameter(defaultValue = "target/generated-resources")
    private Path generatedResourceLocation;

    @Parameter(required = true)
    private String mcVersion;

    @Override
    public void execute() throws MojoExecutionException {
        final Path patch = generatedResourceLocation.resolve("paperMC.patch");
        final Path json = generatedResourceLocation.resolve("patch.json");
        final Path protocol = generatedResourceLocation.resolve("META-INF/" + PROTOCOL_FILE);

        // Create the directory if needed
        if (Files.notExists(generatedResourceLocation)) {
            try {
                Files.createDirectories(generatedResourceLocation);
                Files.deleteIfExists(patch);
            } catch (IOException e) {
                throw new MojoExecutionException("Could not create source directory", e);
            }
        }

        if (Files.notExists(vanillaMinecraft)) {
            throw new MojoExecutionException("vanillaMinecraft jar does not exist!");
        }

        if (Files.notExists(paperMinecraft)) {
            throw new MojoExecutionException("paperMinecraft jar does not exist!");
        }

        final URI zipUri;
        try {
            final URI jarUri = paperMinecraft.toUri();
            zipUri = new URI("jar:" + jarUri.getScheme(), jarUri.getPath(), null);
        } catch (final URISyntaxException e) {
            throw new MojoExecutionException("Failed to create jar URI for " + paperMinecraft);
        }

        protocolCheck:
        try (final FileSystem zipFs = FileSystems.newFileSystem(zipUri, new HashMap<>())) {
            final Path protocolPath = zipFs.getPath("META-INF", PROTOCOL_FILE);
            if (Files.notExists(protocolPath)) {
                Files.deleteIfExists(protocol);
                break protocolCheck;
            }

            Files.createDirectories(protocol.getParent());
            Files.copy(protocolPath, protocol, StandardCopyOption.REPLACE_EXISTING);
        } catch (final IOException e) {
            throw new MojoExecutionException("Failed to read " + paperMinecraft + " contents", e);
        }

        // Read the files into memory
        getLog().info("Reading jars into memory");
        final byte[] vanillaMinecraftBytes;
        final byte[] paperMinecraftBytes;
        try {
            vanillaMinecraftBytes = Files.readAllBytes(vanillaMinecraft);
            paperMinecraftBytes = Files.readAllBytes(paperMinecraft);
        } catch (IOException e) {
            throw new MojoExecutionException("Error reading jars", e);
        }

        getLog().info("Creating patch");
        try (final OutputStream paperMinecraftPatch = Files.newOutputStream(patch)) {
            Diff.diff(vanillaMinecraftBytes, paperMinecraftBytes, paperMinecraftPatch);
        } catch (final InvalidHeaderException | IOException | CompressorException e) {
            throw new MojoExecutionException("Error creating patches", e);
        }

        // Add the SHA-256 hashes for the files
        final MessageDigest digest;
        try {
            digest = MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new MojoExecutionException("Could not create SHA-256 hasher.", e);
        }

        // Vanilla's URL uses a SHA1 hash of the vanilla server jar
        final MessageDigest digestSha1;
        try {
            digestSha1 = MessageDigest.getInstance("SHA1");
        } catch (NoSuchAlgorithmException e) {
            throw new MojoExecutionException("Could not create SHA1 hasher.", e);
        }

        getLog().info("Hashing files");
        final byte[] vanillaSha1 = digestSha1.digest(vanillaMinecraftBytes);
        final byte[] vanillaMinecraftHash = digest.digest(vanillaMinecraftBytes);
        final byte[] paperMinecraftHash = digest.digest(paperMinecraftBytes);

        final PatchData data = new PatchData();
        data.originalHash = toHex(vanillaMinecraftHash);
        data.patchedHash = toHex(paperMinecraftHash);
        data.patch = "paperMC.patch";
        data.sourceUrl = "https://launcher.mojang.com/v1/objects/" + toHex(vanillaSha1).toLowerCase() + "/server.jar";
        data.version = mcVersion;

        getLog().info("Writing json file");
        try (final BufferedWriter writer = Files.newBufferedWriter(json)) {
            new Gson().toJson(data, writer);
        } catch (final IOException e) {
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

    @SuppressWarnings("unused")
    public void setVanillaMinecraft(final File vanillaMinecraft) {
        this.vanillaMinecraft = vanillaMinecraft.toPath();
    }

    @SuppressWarnings("unused")
    public void setPaperMinecraft(final File paperMinecraft) {
        this.paperMinecraft = paperMinecraft.toPath();
    }

    @SuppressWarnings("unused")
    public void setGeneratedResourceLocation(final File generatedResourceLocation) {
        this.generatedResourceLocation = generatedResourceLocation.toPath();
    }
}
