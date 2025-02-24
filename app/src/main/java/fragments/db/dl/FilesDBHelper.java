package fragments.db.dl;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

/**
 * Created by mt.karimi on 15-4-19.
 * if you decompiled my application for any reason
 * let me know and we can be friend :)
 * email me at mtk.irib@gmail.com
 */
public class FilesDBHelper extends SQLiteOpenHelper {
    private static final String DB_NAME = "filesdownloaded.db";
    private static final int DB_VERSION = 3;

    public FilesDBHelper(Context context) {
        super(context, DB_NAME, null, DB_VERSION);
    }

    void createTable(SQLiteDatabase db) {
        FilesInfoDao.createTable(db);
    }

    void dropTable(SQLiteDatabase db) {
        FilesInfoDao.dropTable(db);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        createTable(db);
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        dropTable(db);
        createTable(db);
    }
}
