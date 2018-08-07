/*
 * Copyright (C) 2018 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.providers.media;

import static com.android.providers.media.MediaProvider.makePristine;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.provider.MediaStore.Files.FileColumns;
import android.support.test.InstrumentationRegistry;
import android.support.test.runner.AndroidJUnit4;

import com.android.providers.media.MediaProvider.DatabaseHelper;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class DatabaseHelperTest {
    private static final String TEST_DB = "test";

    private Context getContext() {
        return InstrumentationRegistry.getTargetContext();
    }

    @Before
    public void setUp() throws Exception {
        getContext().deleteDatabase(TEST_DB);
    }

    @Test
    public void testDowngrade() throws Exception {
        try (DatabaseHelper helper = new DatabaseHelperQ(getContext(), TEST_DB)) {
            SQLiteDatabase db = helper.getWritableDatabase();
            {
                final ContentValues values = new ContentValues();
                values.put(FileColumns.DATA,
                        "/storage/emulated/0/DCIM/global.jpg");
                values.put(FileColumns.DATE_ADDED, System.currentTimeMillis());
                values.put(FileColumns.DATE_MODIFIED, System.currentTimeMillis());
                values.put(FileColumns.DISPLAY_NAME, "global.jpg");
                values.put(FileColumns.MEDIA_TYPE, FileColumns.MEDIA_TYPE_IMAGE);
                assertFalse(db.insert("files", FileColumns.DATA, values) == -1);
            }
            try (Cursor c = db.query("files", null, null, null, null, null, null, null)) {
                assertEquals(1, c.getCount());
            }
        }

        // Downgrade will wipe data, but at least we don't crash
        try (DatabaseHelper helper = new DatabaseHelperP(getContext(), TEST_DB)) {
            SQLiteDatabase db = helper.getWritableDatabase();
            try (Cursor c = db.query("files", null, null, null, null, null, null, null)) {
                assertEquals(0, c.getCount());
            }
        }
    }

    @Test
    public void testPtoQ() throws Exception {
        try (DatabaseHelper helper = new DatabaseHelperP(getContext(), TEST_DB)) {
            SQLiteDatabase db = helper.getWritableDatabase();
            {
                final ContentValues values = new ContentValues();
                values.put(FileColumns.DATA,
                        "/storage/emulated/0/DCIM/global.jpg");
                values.put(FileColumns.DATE_ADDED, System.currentTimeMillis());
                values.put(FileColumns.DATE_MODIFIED, System.currentTimeMillis());
                values.put(FileColumns.DISPLAY_NAME, "global.jpg");
                values.put(FileColumns.MEDIA_TYPE, FileColumns.MEDIA_TYPE_IMAGE);
                assertFalse(db.insert("files", FileColumns.DATA, values) == -1);
            }
            {
                final ContentValues values = new ContentValues();
                values.put(FileColumns.DATA,
                        "/storage/emulated/0/Android/media/com.example/app.jpg");
                values.put(FileColumns.DATE_ADDED, System.currentTimeMillis());
                values.put(FileColumns.DATE_MODIFIED, System.currentTimeMillis());
                values.put(FileColumns.DISPLAY_NAME, "app.jpg");
                values.put(FileColumns.MEDIA_TYPE, FileColumns.MEDIA_TYPE_IMAGE);
                assertFalse(db.insert("files", FileColumns.DATA, values) == -1);
            }
        }

        try (DatabaseHelper helper = new DatabaseHelperQ(getContext(), TEST_DB)) {
            SQLiteDatabase db = helper.getWritableDatabase();
            try (Cursor c = db.query("files", null, FileColumns.DISPLAY_NAME + "='global.jpg'",
                    null, null, null, null)) {
                assertEquals(1, c.getCount());
                assertTrue(c.moveToFirst());
                assertEquals("/storage/emulated/0/DCIM/global.jpg",
                        c.getString(c.getColumnIndexOrThrow(FileColumns.DATA)));
                assertEquals(null,
                        c.getString(c.getColumnIndexOrThrow(FileColumns.OWNER_PACKAGE_NAME)));
            }
            try (Cursor c = db.query("files", null, FileColumns.DISPLAY_NAME + "='app.jpg'",
                    null, null, null, null)) {
                assertEquals(1, c.getCount());
                assertTrue(c.moveToFirst());
                assertEquals("/storage/emulated/0/Android/media/com.example/app.jpg",
                        c.getString(c.getColumnIndexOrThrow(FileColumns.DATA)));
                assertEquals("com.example",
                        c.getString(c.getColumnIndexOrThrow(FileColumns.OWNER_PACKAGE_NAME)));
            }
        }
    }

    private static class DatabaseHelperP extends DatabaseHelper {
        public DatabaseHelperP(Context context, String name) {
            super(context, name, MediaProvider.VERSION_P, false, false, null);
        }

        @Override
        public void onCreate(SQLiteDatabase db) {
            createPSchema(db, false);
        }
    }

    private static class DatabaseHelperQ extends DatabaseHelper {
        public DatabaseHelperQ(Context context, String name) {
            super(context, name, MediaProvider.VERSION_Q, false, false, null);
        }
    }

    /**
     * Snapshot of
     * {@link MediaProvider#createLatestSchema(SQLiteDatabase, boolean)} as of
     * {@link android.os.Build.VERSION_CODES#P}.
     */
    private static void createPSchema(SQLiteDatabase db, boolean internal) {
        makePristine(db);

        db.execSQL("CREATE TABLE android_metadata (locale TEXT)");
        db.execSQL("CREATE TABLE thumbnails (_id INTEGER PRIMARY KEY,_data TEXT,image_id INTEGER,"
                + "kind INTEGER,width INTEGER,height INTEGER)");
        db.execSQL("CREATE TABLE artists (artist_id INTEGER PRIMARY KEY,"
                + "artist_key TEXT NOT NULL UNIQUE,artist TEXT NOT NULL)");
        db.execSQL("CREATE TABLE albums (album_id INTEGER PRIMARY KEY,"
                + "album_key TEXT NOT NULL UNIQUE,album TEXT NOT NULL)");
        db.execSQL("CREATE TABLE album_art (album_id INTEGER PRIMARY KEY,_data TEXT)");
        db.execSQL("CREATE TABLE videothumbnails (_id INTEGER PRIMARY KEY,_data TEXT,"
                + "video_id INTEGER,kind INTEGER,width INTEGER,height INTEGER)");
        db.execSQL("CREATE TABLE files (_id INTEGER PRIMARY KEY AUTOINCREMENT,"
                + "_data TEXT UNIQUE COLLATE NOCASE,_size INTEGER,format INTEGER,parent INTEGER,"
                + "date_added INTEGER,date_modified INTEGER,mime_type TEXT,title TEXT,"
                + "description TEXT,_display_name TEXT,picasa_id TEXT,orientation INTEGER,"
                + "latitude DOUBLE,longitude DOUBLE,datetaken INTEGER,mini_thumb_magic INTEGER,"
                + "bucket_id TEXT,bucket_display_name TEXT,isprivate INTEGER,title_key TEXT,"
                + "artist_id INTEGER,album_id INTEGER,composer TEXT,track INTEGER,"
                + "year INTEGER CHECK(year!=0),is_ringtone INTEGER,is_music INTEGER,"
                + "is_alarm INTEGER,is_notification INTEGER,is_podcast INTEGER,album_artist TEXT,"
                + "duration INTEGER,bookmark INTEGER,artist TEXT,album TEXT,resolution TEXT,"
                + "tags TEXT,category TEXT,language TEXT,mini_thumb_data TEXT,name TEXT,"
                + "media_type INTEGER,old_id INTEGER,is_drm INTEGER,"
                + "width INTEGER, height INTEGER, title_resource_uri TEXT)");
        db.execSQL("CREATE TABLE log (time DATETIME, message TEXT)");
        if (!internal) {
            db.execSQL("CREATE TABLE audio_genres (_id INTEGER PRIMARY KEY,name TEXT NOT NULL)");
            db.execSQL("CREATE TABLE audio_genres_map (_id INTEGER PRIMARY KEY,"
                    + "audio_id INTEGER NOT NULL,genre_id INTEGER NOT NULL,"
                    + "UNIQUE (audio_id,genre_id) ON CONFLICT IGNORE)");
            db.execSQL("CREATE TABLE audio_playlists_map (_id INTEGER PRIMARY KEY,"
                    + "audio_id INTEGER NOT NULL,playlist_id INTEGER NOT NULL,"
                    + "play_order INTEGER NOT NULL)");
            db.execSQL("CREATE TRIGGER audio_genres_cleanup DELETE ON audio_genres BEGIN DELETE"
                    + " FROM audio_genres_map WHERE genre_id = old._id;END");
            db.execSQL("CREATE TRIGGER audio_playlists_cleanup DELETE ON files"
                    + " WHEN old.media_type=4"
                    + " BEGIN DELETE FROM audio_playlists_map WHERE playlist_id = old._id;"
                    + "SELECT _DELETE_FILE(old._data);END");
            db.execSQL("CREATE TRIGGER files_cleanup DELETE ON files"
                    + " BEGIN SELECT _OBJECT_REMOVED(old._id);END");
            db.execSQL("CREATE VIEW audio_playlists AS SELECT _id,_data,name,date_added,date_modified"
                    + " FROM files WHERE media_type=4");
        }

        db.execSQL("CREATE INDEX image_id_index on thumbnails(image_id)");
        db.execSQL("CREATE INDEX album_idx on albums(album)");
        db.execSQL("CREATE INDEX albumkey_index on albums(album_key)");
        db.execSQL("CREATE INDEX artist_idx on artists(artist)");
        db.execSQL("CREATE INDEX artistkey_index on artists(artist_key)");
        db.execSQL("CREATE INDEX video_id_index on videothumbnails(video_id)");
        db.execSQL("CREATE INDEX album_id_idx ON files(album_id)");
        db.execSQL("CREATE INDEX artist_id_idx ON files(artist_id)");
        db.execSQL("CREATE INDEX bucket_index on files(bucket_id,media_type,datetaken, _id)");
        db.execSQL("CREATE INDEX bucket_name on files(bucket_id,media_type,bucket_display_name)");
        db.execSQL("CREATE INDEX format_index ON files(format)");
        db.execSQL("CREATE INDEX media_type_index ON files(media_type)");
        db.execSQL("CREATE INDEX parent_index ON files(parent)");
        db.execSQL("CREATE INDEX path_index ON files(_data)");
        db.execSQL("CREATE INDEX sort_index ON files(datetaken ASC, _id ASC)");
        db.execSQL("CREATE INDEX title_idx ON files(title)");
        db.execSQL("CREATE INDEX titlekey_index ON files(title_key)");

        db.execSQL("CREATE VIEW audio_meta AS SELECT _id,_data,_display_name,_size,mime_type,"
                + "date_added,is_drm,date_modified,title,title_key,duration,artist_id,composer,"
                + "album_id,track,year,is_ringtone,is_music,is_alarm,is_notification,is_podcast,"
                + "bookmark,album_artist FROM files WHERE media_type=2");
        db.execSQL("CREATE VIEW artists_albums_map AS SELECT DISTINCT artist_id, album_id"
                + " FROM audio_meta");
        db.execSQL("CREATE VIEW audio as SELECT * FROM audio_meta LEFT OUTER JOIN artists"
                + " ON audio_meta.artist_id=artists.artist_id LEFT OUTER JOIN albums"
                + " ON audio_meta.album_id=albums.album_id");
        db.execSQL("CREATE VIEW album_info AS SELECT audio.album_id AS _id, album, album_key,"
                + " MIN(year) AS minyear, MAX(year) AS maxyear, artist, artist_id, artist_key,"
                + " count(*) AS numsongs,album_art._data AS album_art FROM audio"
                + " LEFT OUTER JOIN album_art ON audio.album_id=album_art.album_id WHERE is_music=1"
                + " GROUP BY audio.album_id");
        db.execSQL("CREATE VIEW searchhelpertitle AS SELECT * FROM audio ORDER BY title_key");
        db.execSQL("CREATE VIEW artist_info AS SELECT artist_id AS _id, artist, artist_key,"
                + " COUNT(DISTINCT album_key) AS number_of_albums, COUNT(*) AS number_of_tracks"
                + " FROM audio"
                + " WHERE is_music=1 GROUP BY artist_key");
        db.execSQL("CREATE VIEW search AS SELECT _id,'artist' AS mime_type,artist,NULL AS album,"
                + "NULL AS title,artist AS text1,NULL AS text2,number_of_albums AS data1,"
                + "number_of_tracks AS data2,artist_key AS match,"
                + "'content://media/external/audio/artists/'||_id AS suggest_intent_data,"
                + "1 AS grouporder FROM artist_info WHERE (artist!='<unknown>')"
                + " UNION ALL SELECT _id,'album' AS mime_type,artist,album,"
                + "NULL AS title,album AS text1,artist AS text2,NULL AS data1,"
                + "NULL AS data2,artist_key||' '||album_key AS match,"
                + "'content://media/external/audio/albums/'||_id AS suggest_intent_data,"
                + "2 AS grouporder FROM album_info"
                + " WHERE (album!='<unknown>')"
                + " UNION ALL SELECT searchhelpertitle._id AS _id,mime_type,artist,album,title,"
                + "title AS text1,artist AS text2,NULL AS data1,"
                + "NULL AS data2,artist_key||' '||album_key||' '||title_key AS match,"
                + "'content://media/external/audio/media/'||searchhelpertitle._id"
                + " AS suggest_intent_data,"
                + "3 AS grouporder FROM searchhelpertitle WHERE (title != '')");
        db.execSQL("CREATE VIEW audio_genres_map_noid AS SELECT audio_id,genre_id"
                + " FROM audio_genres_map");
        db.execSQL("CREATE VIEW images AS SELECT _id,_data,_size,_display_name,mime_type,title,"
                + "date_added,date_modified,description,picasa_id,isprivate,latitude,longitude,"
                + "datetaken,orientation,mini_thumb_magic,bucket_id,bucket_display_name,width,"
                + "height FROM files WHERE media_type=1");
        db.execSQL("CREATE VIEW video AS SELECT _id,_data,_display_name,_size,mime_type,"
                + "date_added,date_modified,title,duration,artist,album,resolution,description,"
                + "isprivate,tags,category,language,mini_thumb_data,latitude,longitude,datetaken,"
                + "mini_thumb_magic,bucket_id,bucket_display_name,bookmark,width,height"
                + " FROM files WHERE media_type=3");

        db.execSQL("CREATE TRIGGER albumart_cleanup1 DELETE ON albums BEGIN DELETE FROM album_art"
                + " WHERE album_id = old.album_id;END");
        db.execSQL("CREATE TRIGGER albumart_cleanup2 DELETE ON album_art"
                + " BEGIN SELECT _DELETE_FILE(old._data);END");
    }


}
