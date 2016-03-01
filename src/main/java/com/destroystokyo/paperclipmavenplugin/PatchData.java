/*
 * Paperclip Maven Plugin - Generates the Data class + patch file for Paperclip
 *
 * Copyright (c) 2016 Kyle Wood (DemonWav)
 * https://github.com/PaperSpigot/Paper
 *
 * MIT License
 */

package com.destroystokyo.paperclipmavenplugin;

import com.google.gson.annotations.SerializedName;
import lombok.Data;

@Data
public class PatchData {

    @SerializedName("patch")
    private String patch;

    @SerializedName("sourceUrl")
    private String sourceUrl;

    @SerializedName("originalHash")
    private String originalHash;

    @SerializedName("patchedHash")
    private String patchedHash;

    @SerializedName("version")
    private String version;
}
