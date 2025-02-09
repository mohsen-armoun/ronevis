package multithreaddownload;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import androidx.annotation.NonNull;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import multithreaddownload.architecture.DownloadResponse;
import multithreaddownload.architecture.DownloadStatusDelivery;
import multithreaddownload.architecture.Downloader;
import multithreaddownload.core.DownloadResponseImpl;
import multithreaddownload.core.DownloadStatusDeliveryImpl;
import multithreaddownload.core.DownloaderImpl;
import multithreaddownload.db.DataBaseManager;
import multithreaddownload.db.ThreadInfo;
import multithreaddownload.util.L;

/**
 * Created by mt.karimi on 2015/7/14.
 * if you decompiled my application for any reason
 * let me know and we can be friend :)
 * email me at mtk.irib@gmail.com
 */
public class DownloadManager implements Downloader.OnDownloaderDestroyedListener {
    public static final String TAG = DownloadManager.class.getSimpleName();
    /**
     * singleton of DownloadManager
     */
    private static DownloadManager sDownloadManager;
    private DataBaseManager mDBManager;
    private Map<String, Downloader> mDownloaderMap;
    private DownloadConfiguration mConfig;
    private ExecutorService mExecutorService;
    private DownloadStatusDelivery mDelivery;
    private Handler mHandler = new Handler(Looper.getMainLooper());

    /**
     * private construction
     */
    private DownloadManager() {
        mDownloaderMap = new LinkedHashMap<>();
    }

    public static DownloadManager getInstance() {
        if (sDownloadManager == null) {
            synchronized (DownloadManager.class) {
                if (sDownloadManager == null) {
                    sDownloadManager = new DownloadManager();
                }
            }
        }
        return sDownloadManager;
    }

    private static String createKey(String tag) {
        if (tag == null) {
            throw new NullPointerException("Tag can't be null!");
        }
        return String.valueOf(tag.hashCode());
    }

    public void init(Context context) {
        init(context, new DownloadConfiguration());
    }

    public void init(Context context, @NonNull DownloadConfiguration config) {
        if (config.getThreadNum() > config.getMaxThreadNum()) {
            throw new IllegalArgumentException("thread num must < max thread num");
        }
        mConfig = config;
        mDBManager = DataBaseManager.getInstance(context);
        mExecutorService = Executors.newFixedThreadPool(mConfig.getMaxThreadNum());
        mDelivery = new DownloadStatusDeliveryImpl(mHandler);
    }

    @Override
    public void onDestroyed(final String key, Downloader downloader) {
        mHandler.post(new Runnable() {
            @Override
            public void run() {
                if (mDownloaderMap.containsKey(key)) {
                    mDownloaderMap.remove(key);
                }
            }
        });
    }

    public void download(DownloadRequest request, String tag, CallBack callBack) {
        final String key = createKey(tag);
        if (check(key)) {
            DownloadResponse response = new DownloadResponseImpl(mDelivery, callBack);
            Downloader downloader = new DownloaderImpl(request, response, mExecutorService, mDBManager, key, mConfig, this);
            mDownloaderMap.put(key, downloader);
            downloader.start();
        }
    }

    public void pause(String tag) {
        String key = createKey(tag);
        if (mDownloaderMap.containsKey(key)) {
            Downloader downloader = mDownloaderMap.get(key);
            if (downloader != null) {
                if (downloader.isRunning()) {
                    downloader.pause();
                }
            }
            mDownloaderMap.remove(key);
        }
    }

    public void cancel(String tag) {
        String key = createKey(tag);
        if (mDownloaderMap.containsKey(key)) {
            Downloader downloader = mDownloaderMap.get(key);
            if (downloader != null) {
                downloader.cancel();
            }
            mDownloaderMap.remove(key);
        }
    }

    public void pauseAll() {
        mHandler.post(new Runnable() {
            @Override
            public void run() {
                for (Downloader downloader : mDownloaderMap.values()) {
                    if (downloader != null) {
                        if (downloader.isRunning()) {
                            downloader.pause();
                        }
                    }
                }
            }
        });
    }

    public void cancelAll() {
        mHandler.post(new Runnable() {
            @Override
            public void run() {
                for (Downloader downloader : mDownloaderMap.values()) {
                    if (downloader != null) {
                        if (downloader.isRunning()) {
                            downloader.cancel();
                        }
                    }
                }
            }
        });
    }

    public void delete(String tag) {
        String key = createKey(tag);
        mDBManager.delete(key);
    }

    public boolean isRunning(String tag) {
        String key = createKey(tag);
        if (mDownloaderMap.containsKey(key)) {
            Downloader downloader = mDownloaderMap.get(key);
            if (downloader != null) {
                return downloader.isRunning();
            }
        }
        return false;
    }

    public DownloadInfo getDownloadInfo(String tag) {
        String key = createKey(tag);
        List<ThreadInfo> threadInfos = mDBManager.getThreadInfos(key);
        DownloadInfo downloadInfo = null;
        if (!threadInfos.isEmpty()) {
            int finished = 0;
            int progress = 0;
            int total = 0;
            for (ThreadInfo info : threadInfos) {
                finished += info.getFinished();
                total += (info.getEnd() - info.getStart());
            }
            progress = (int) ((long) finished * 100 / total);
            downloadInfo = new DownloadInfo();
            downloadInfo.setFinished(finished);
            downloadInfo.setLength(total);
            downloadInfo.setProgress(progress);
        }
        return downloadInfo;
    }

    private boolean check(String key) {
        if (mDownloaderMap.containsKey(key)) {
            Downloader downloader = mDownloaderMap.get(key);
            if (downloader != null) {
                if (downloader.isRunning()) {
                    L.w("Task has been started!");
                    return false;
                } else {
                    throw new IllegalStateException("Downloader instance with same tag has not been destroyed!");
                }
            }
        }
        return true;
    }
}
