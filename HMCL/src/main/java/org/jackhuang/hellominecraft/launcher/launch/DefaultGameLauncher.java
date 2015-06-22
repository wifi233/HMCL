/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.jackhuang.hellominecraft.launcher.launch;

import java.io.IOException;
import org.jackhuang.hellominecraft.C;
import org.jackhuang.hellominecraft.HMCLog;
import org.jackhuang.hellominecraft.launcher.launch.GameLauncher.DownloadLibraryJob;
import org.jackhuang.hellominecraft.launcher.utils.auth.IAuthenticator;
import org.jackhuang.hellominecraft.launcher.utils.auth.LoginInfo;
import org.jackhuang.hellominecraft.launcher.utils.download.DownloadType;
import org.jackhuang.hellominecraft.launcher.utils.settings.Profile;
import org.jackhuang.hellominecraft.tasks.ParallelTask;
import org.jackhuang.hellominecraft.tasks.TaskWindow;
import org.jackhuang.hellominecraft.tasks.download.FileDownloadTask;
import org.jackhuang.hellominecraft.utils.Compressor;
import org.jackhuang.hellominecraft.utils.MessageBox;

/**
 *
 * @author hyh
 */
public class DefaultGameLauncher extends GameLauncher {

    public DefaultGameLauncher(Profile version, LoginInfo info, IAuthenticator lg) {
        super(version, info, lg);
        register();
    }

    public DefaultGameLauncher(Profile version, LoginInfo info, IAuthenticator lg, DownloadType downloadType) {
        super(version, info, lg, downloadType);
        register();
    }

    private void register() {
        downloadLibrariesEvent.register((sender, t) -> {
            final TaskWindow dw = TaskWindow.getInstance();
            ParallelTask parallelTask = new ParallelTask();
            for (DownloadLibraryJob o : t) {
                final DownloadLibraryJob s = (DownloadLibraryJob) o;
                parallelTask.addDependsTask(new FileDownloadTask(s.url, s.path).setTag(s.name));
            }
            dw.addTask(parallelTask);
            boolean flag = true;
            if (t.size() > 0) flag = dw.start();
            if (!flag && MessageBox.Show(C.i18n("launch.not_finished_downloading_libraries"), MessageBox.YES_NO_OPTION) == MessageBox.YES_OPTION)
                flag = true;
            return flag;
        });
        decompressNativesEvent.register((sender, value) -> {
            //boolean flag = true;
            for (int i = 0; i < value.decompressFiles.length; i++)
                try {
                    Compressor.unzip(value.decompressFiles[i], value.decompressTo, value.extractRules[i]);
                } catch (IOException ex) {
                    HMCLog.err("Unable to decompress library file: " + value.decompressFiles[i] + " to " + value.decompressTo, ex);
                    //flag = false;
                }
            /*if(!flag)
            if(MessageBox.Show(C.i18n("launch.not_finished_decompressing_natives"), MessageBox.YES_NO_OPTION) == MessageBox.YES_OPTION)
            flag = true;*/
            return true;
        });
    }

}
