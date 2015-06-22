/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.jackhuang.hellominecraft.launcher.utils.version;

import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import org.jackhuang.hellominecraft.C;
import org.jackhuang.hellominecraft.HMCLog;
import org.jackhuang.hellominecraft.launcher.launch.GameLauncher;
import org.jackhuang.hellominecraft.launcher.launch.GameLauncher.DownloadLibraryJob;
import org.jackhuang.hellominecraft.launcher.launch.IMinecraftLoader;
import org.jackhuang.hellominecraft.launcher.launch.IMinecraftProvider;
import org.jackhuang.hellominecraft.launcher.launch.MinecraftLoader;
import org.jackhuang.hellominecraft.utils.BaseLauncherProfile;
import org.jackhuang.hellominecraft.utils.FileUtils;
import org.jackhuang.hellominecraft.launcher.utils.MCUtils;
import org.jackhuang.hellominecraft.launcher.utils.auth.UserProfileProvider;
import org.jackhuang.hellominecraft.launcher.utils.download.DownloadType;
import org.jackhuang.hellominecraft.launcher.utils.settings.Profile;
import org.jackhuang.hellominecraft.launcher.utils.settings.Settings;
import org.jackhuang.hellominecraft.utils.IOUtils;
import org.jackhuang.hellominecraft.utils.MessageBox;
import org.jackhuang.hellominecraft.utils.StrUtils;
import org.jackhuang.hellominecraft.utils.Utils;

/**
 *
 * @author hyh
 */
public final class MinecraftVersionManager extends IMinecraftProvider {

    private File baseFolder;
    private final Profile profile;
    private final Map<String, MinecraftVersion> versions = new TreeMap();
    private final Gson gson = Utils.getDefaultGsonBuilder().create();

    /**
     *
     * @param p
     */
    public MinecraftVersionManager(Profile p) {
        super(p);
        this.profile = p;
        refreshVersions();
    }

    public File getFolder() {
        return baseFolder;
    }

    @Override
    public Collection<MinecraftVersion> getVersions() {
        return versions.values();
    }

    @Override
    public int getVersionCount() {
        return versions.size();
    }

    @Override
    public void refreshVersions() {
        baseFolder = profile.getCanonicalGameDirFile();
        try {
            BaseLauncherProfile.tryWriteProfile(baseFolder);
        } catch (IOException ex) {
            HMCLog.warn("Failed to create launcher_profiles.json, Forge/LiteLoader installer will not work.", ex);
        }

        versions.clear();
        File oldDir = new File(baseFolder, "bin");
        if (oldDir.exists()) {
            MinecraftClassicVersion v = new MinecraftClassicVersion();
            versions.put(v.id, v);
        }

        File version = new File(baseFolder, "versions");
        File[] files = version.listFiles();
        if (files == null || files.length == 0) return;

        for (File dir : files) {
            String id = dir.getName();
            File jsonFile = new File(dir, id + ".json");

            if (!dir.isDirectory()) continue;
            boolean ask = false;
            File[] jsons = null;
            if (!jsonFile.exists()) {
                jsons = FileUtils.searchSuffix(dir, "json");
                if (jsons.length == 1) ask = true;
            }
            if (ask) {
                HMCLog.warn("Found not matched filenames version: " + id + ", json: " + jsons[0].getName());
                if (MessageBox.Show(String.format(C.i18n("launcher.versions_json_not_matched"), id, jsons[0].getName()), MessageBox.YES_NO_OPTION) == MessageBox.YES_OPTION)
                    jsons[0].renameTo(new File(jsons[0].getParent(), id + ".json"));
            }
            if (!jsonFile.exists()) {
                if (StrUtils.formatVersion(id) == null) {
                    if (MessageBox.Show(C.i18n("launcher.versions_json_not_matched_cannot_auto_completion", id), MessageBox.YES_NO_OPTION) == MessageBox.YES_OPTION)
                        FileUtils.deleteDirectoryQuietly(dir);
                } else if (MessageBox.Show(C.i18n("launcher.versions_json_not_matched_needs_auto_completion", id), MessageBox.YES_NO_OPTION) == MessageBox.YES_OPTION) {
                    if (!refreshJson(id)) {
                        if (MessageBox.Show(C.i18n("launcher.versions_json_not_matched_cannot_auto_completion", id), MessageBox.YES_NO_OPTION) == MessageBox.YES_OPTION)
                            FileUtils.deleteDirectoryQuietly(dir);
                    }
                }
                continue;
            }
            MinecraftVersion mcVersion = null;
            try {
                mcVersion = gson.fromJson(FileUtils.readFileToString(jsonFile), MinecraftVersion.class);
                if (mcVersion == null) throw new RuntimeException("Wrong json format, got null.");
            } catch (IOException | RuntimeException e) {
                HMCLog.warn("Found wrong format json, try to fix it.", e);
                if (MessageBox.Show(C.i18n("launcher.versions_json_not_formattedn", id), MessageBox.YES_NO_OPTION) == MessageBox.YES_OPTION) {
                    refreshJson(id);
                    try {
                        mcVersion = gson.fromJson(FileUtils.readFileToString(jsonFile), MinecraftVersion.class);
                        if (mcVersion == null) throw new RuntimeException("Wrong json format, got null.");
                    } catch (IOException | RuntimeException ex) {
                        HMCLog.err("Retried but still failed.");
                        HMCLog.warn("Ignoring: " + dir + ", the json of this Minecraft is malformed.", ex);
                        continue;
                    }
                }
            }
            try {
                if (!id.equals(mcVersion.id)) {
                    HMCLog.warn("Found: " + dir + ", it contains id: " + mcVersion.id + ", expected: " + id + ", the launcher will fix this problem.");
                    mcVersion.id = id;
                    FileUtils.writeQuietly(jsonFile, gson.toJson(mcVersion));
                }

                if (mcVersion.libraries != null)
                    for (MinecraftLibrary ml : mcVersion.libraries)
                        ml.init();
                versions.put(id, mcVersion);
            } catch (Exception e) {
                HMCLog.warn("Ignoring: " + dir + ", the json of this Minecraft is malformed.");
            }
        }
    }

    @Override
    public boolean removeVersionFromDisk(String name) {
        File version = new File(baseFolder, "versions/" + name);
        if (!version.exists()) return true;

        versions.remove(name);
        return FileUtils.deleteDirectoryQuietly(version);
    }

    @Override
    public boolean renameVersion(String from, String to) {
        try {
            File fromJson = new File(baseFolder, "versions/" + from + "/" + from + ".json");
            MinecraftVersion mcVersion = gson.fromJson(FileUtils.readFileToString(fromJson), MinecraftVersion.class);
            mcVersion.id = to;
            FileUtils.writeQuietly(fromJson, gson.toJson(mcVersion));
            File toDir = new File(baseFolder, "versions/" + to);
            new File(baseFolder, "versions/" + from).renameTo(toDir);
            File toJson = new File(toDir, to + ".json");
            File toJar = new File(toDir, to + ".jar");
            new File(toDir, from + ".json").renameTo(toJson);
            new File(toDir, from + ".jar").renameTo(toJar);
            return true;
        } catch (IOException | JsonSyntaxException e) {
            HMCLog.warn("Failed to rename " + from + " to " + to + ", the json of this Minecraft is malformed.", e);
            return false;
        }
    }

    @Override
    public boolean refreshJson(String id) {
        return MCUtils.downloadMinecraftVersionJson(baseFolder, id, Settings.s().getDownloadSource());
    }

    @Override
    public boolean refreshAssetsIndex(String id) {
        MinecraftVersion mv = getVersionById(id);
        if (mv == null) return false;
        return MCUtils.downloadMinecraftAssetsIndex(new File(baseFolder, "assets"), mv.assets, Settings.s().getDownloadSource());
    }

    @Override
    public boolean install(String id, DownloadType sourceType) {
        MinecraftVersion v = MCUtils.downloadMinecraft(baseFolder, id, sourceType);
        if (v != null) {
            versions.put(v.id, v);
            return true;
        }
        return false;
    }

    @Override
    public File getRunDirectory(String id) {
        switch (profile.getGameDirType()) {
        case VERSION_FOLDER:
            return new File(baseFolder, "versions/" + id + "/");
        default:
            return baseFolder;
        }
    }

    @Override
    public List<GameLauncher.DownloadLibraryJob> getDownloadLibraries(DownloadType downloadType) {
        ArrayList<DownloadLibraryJob> downloadLibraries = new ArrayList<>();
        MinecraftVersion v = profile.getSelectedMinecraftVersion().resolve(this, Settings.s().getDownloadSource());
        for (IMinecraftLibrary l : v.libraries) {
            l.init();
            if (l.allow()) {
                File ff = l.getFilePath(baseFolder);
                if (!ff.exists()) {
                    String libURL = downloadType.getProvider().getLibraryDownloadURL() + "/";
                    libURL = l.getDownloadURL(libURL, downloadType);
                    if (libURL != null)
                        downloadLibraries.add(new DownloadLibraryJob(l.name, libURL, ff));
                }
            }
        }
        return downloadLibraries;
    }

    @Override
    public void openSelf(String mv) {
        Utils.openFolder(getRunDirectory(mv));
    }

    @Override
    public void open(String mv, String name) {
        Utils.openFolder(new File(getRunDirectory(mv), name));
    }

    @Override
    public File getAssets() {
        return new File(profile.getCanonicalGameDirFile(), "assets");
    }

    @Override
    public GameLauncher.DecompressLibraryJob getDecompressLibraries() {
        MinecraftVersion v = profile.getSelectedMinecraftVersion().resolve(this, Settings.s().getDownloadSource());
        ArrayList<File> unzippings = new ArrayList<>();
        ArrayList<String[]> extractRules = new ArrayList<>();
        for (IMinecraftLibrary l : v.libraries) {
            l.init();
            if (l.isRequiredToUnzip() && v.isAllowedToUnpackNatives()) {
                unzippings.add(IOUtils.tryGetCanonicalFile(l.getFilePath(baseFolder)));
                extractRules.add(l.getDecompressExtractRules());
            }
        }
        return new GameLauncher.DecompressLibraryJob(unzippings.toArray(new File[0]), extractRules.toArray(new String[0][]), getDecompressNativesToLocation());
    }

    @Override
    public File getDecompressNativesToLocation() {
        MinecraftVersion v = profile.getSelectedMinecraftVersion();
        return v.getNatives(profile.getCanonicalGameDirFile());
    }

    @Override
    public File getMinecraftJar() {
        return profile.getSelectedMinecraftVersion().getJar(baseFolder);
    }

    @Override
    public IMinecraftLoader provideMinecraftLoader(UserProfileProvider p, DownloadType type)
            throws IllegalStateException {
        return new MinecraftLoader(profile, this, p, getMinecraftJar(), type);
    }

    @Override
    public MinecraftVersion getOneVersion() {
        return versions.isEmpty() ? null : versions.values().iterator().next();
    }

    @Override
    public MinecraftVersion getVersionById(String id) {
        return id == null ? null : versions.get(id);
    }

    @Override
    public File getResourcePacks() {
        return new File(profile.getCanonicalGameDirFile(), "resourcepacks");
    }

    @Override
    public File getBaseFolder() {
        return baseFolder;
    }

    @Override
    public void onLaunch() {
        File resourcePacks = getResourcePacks();
        if (!resourcePacks.exists()) resourcePacks.mkdirs();
    }
}
