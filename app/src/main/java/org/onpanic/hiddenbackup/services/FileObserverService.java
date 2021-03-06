package org.onpanic.hiddenbackup.services;

import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.database.Cursor;
import android.os.FileObserver;
import android.os.IBinder;
import android.support.v4.content.LocalBroadcastManager;

import org.onpanic.hiddenbackup.constants.HiddenBackupConstants;
import org.onpanic.hiddenbackup.helpers.RecursiveFileObserver;
import org.onpanic.hiddenbackup.providers.DirsProvider;

import java.io.File;
import java.util.ArrayList;

public class FileObserverService extends Service {
    private final ArrayList<RecursiveFileObserver> fileObservers = new ArrayList<>();

    private LocalBroadcastManager localBroadcastManager;
    private int mStartId;
    private BroadcastReceiver stopReceiver;

    public FileObserverService() {
    }

    @Override
    public void onCreate() {
        super.onCreate();
        localBroadcastManager = LocalBroadcastManager.getInstance(getApplicationContext());
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        mStartId = startId;

        stopReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                localBroadcastManager.unregisterReceiver(stopReceiver);
                for (RecursiveFileObserver o : fileObservers) {
                    o.stopWatching();
                }
                FileObserverService.this.stopSelf(mStartId);
            }
        };

        localBroadcastManager.registerReceiver(
                stopReceiver, new IntentFilter(HiddenBackupConstants.ACTION_STOP_INSTANT));

        ContentResolver cr = getContentResolver();

        String[] mProjection = new String[]{
                DirsProvider.Dir._ID,
                DirsProvider.Dir.PATH,
                DirsProvider.Dir.OBSERVER,
                DirsProvider.Dir.ENABLED
        };

        String where = DirsProvider.Dir.ENABLED + "=1 AND " + DirsProvider.Dir.OBSERVER + "=1";
        Cursor files = cr.query(DirsProvider.CONTENT_URI, mProjection, where, null, null);

        if (files != null) {
            while (files.moveToNext()) {
                File current = new File(files.getString(files.getColumnIndex(DirsProvider.Dir.PATH)));

                if (current.exists()) {
                    RecursiveFileObserver observer = new RecursiveFileObserver(
                            current.getAbsolutePath(),
                            new RecursiveFileObserver.EventListener() {
                                @Override
                                public void onEvent(int event, File file) {
                                    if (event == FileObserver.CREATE) {
                                        if (!file.isDirectory()) {
                                            Intent backup = new Intent(getApplicationContext(), OrbotService.class);
                                            backup.setAction(HiddenBackupConstants.FILE_BACKUP);
                                            backup.putExtra(DirsProvider.Dir.PATH, file.getAbsolutePath());
                                            startService(backup);
                                        }
                                    }
                                }
                            }
                    );

                    observer.startWatching();
                    fileObservers.add(observer);

                } else {
                    cr.delete(DirsProvider.CONTENT_URI,
                            DirsProvider.Dir._ID + "=" + files.getInt(files.getColumnIndex(DirsProvider.Dir._ID)),
                            null);
                }
            }

            files.close();
        }

        return Service.START_STICKY;
    }
}
