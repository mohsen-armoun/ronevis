package multithreaddownload.core;

import android.os.Handler;

import java.util.concurrent.Executor;

import multithreaddownload.CallBack;
import multithreaddownload.DownloadException;
import multithreaddownload.architecture.DownloadStatus;
import multithreaddownload.architecture.DownloadStatusDelivery;

/**
 * Created by mt.karimi on 2015/7/15.
 * if you decompiled my application for any reason
 * let me know and we can be friend :)
 * email me at mtk.irib@gmail.com
 */
public class DownloadStatusDeliveryImpl implements DownloadStatusDelivery {
    private Executor mDownloadStatusPoster;

    public DownloadStatusDeliveryImpl(final Handler handler) {
        mDownloadStatusPoster = new Executor() {
            @Override
            public void execute(Runnable command) {
                handler.post(command);
            }
        };
    }

    @Override
    public void post(DownloadStatus status) {
        mDownloadStatusPoster.execute(new DownloadStatusDeliveryRunnable(status));
    }

    private static class DownloadStatusDeliveryRunnable implements Runnable {
        private final DownloadStatus mDownloadStatus;
        private final CallBack mCallBack;

        public DownloadStatusDeliveryRunnable(DownloadStatus downloadStatus) {
            this.mDownloadStatus = downloadStatus;
            this.mCallBack = mDownloadStatus.getCallBack();
        }

        @Override
        public void run() {
            switch (mDownloadStatus.getStatus()) {
                case DownloadStatus.STATUS_CONNECTING:
                    mCallBack.onConnecting();
                    break;
                case DownloadStatus.STATUS_CONNECTED:
                    mCallBack.onConnected(mDownloadStatus.getLength(), mDownloadStatus.isAcceptRanges());
                    break;
                case DownloadStatus.STATUS_PROGRESS:
                    mCallBack.onProgress(mDownloadStatus.getFinished(), mDownloadStatus.getLength(), mDownloadStatus.getPercent());
                    break;
                case DownloadStatus.STATUS_COMPLETED:
                    mCallBack.onCompleted();
                    break;
                case DownloadStatus.STATUS_PAUSED:
                    mCallBack.onDownloadPaused();
                    break;
                case DownloadStatus.STATUS_CANCELED:
                    mCallBack.onDownloadCanceled();
                    break;
                case DownloadStatus.STATUS_FAILED:
                    mCallBack.onFailed((DownloadException) mDownloadStatus.getException());
                    break;
            }
        }
    }
}
