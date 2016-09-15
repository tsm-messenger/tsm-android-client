package com.tsm_messenger.activities.service;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.support.design.widget.FloatingActionButton;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import com.squareup.picasso.Picasso;
import com.tsm_messenger.activities.R;
import com.tsm_messenger.service.ActivityGlobalManager;
import com.tsm_messenger.service.SharedPreferencesAccessor;
import com.tsm_messenger.service.UniversalHelper;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.List;

public class OpenFileActivity extends AppCompatActivity {

    public static final String EXTRA_OPEN_FOR_WRITE = "open_for_write";
    public static final String EXTRA_SEND_MODE = "send_mode";
    public static final String EXTRA_URI = "uri";
    public static final String EXTRA_VIEW_MODE = "view_mode";
    public static final String EXTRA_HOME_DIRECTORY = "home_dir";
    public static final String MODE_FILES_ONLY = "files_only";
    public static final String MODE_DIRECTORIES_ONLY = "directories_only";
    private static final String STORAGE_PATH = "/storage";
    private static final String INTERNAL_MEMORY_PATH = "/storage/emulated/0";
    private static final String INTERNAL_MEMORY_0_PATH = "/storage/sdcard0";
    public static final String SDCARD_PATH = "storage/external_SD";
    public static final String SDCARD_1_PATH = "storage/sdcard1";
    private String currentPath = Environment.getExternalStorageDirectory().getPath();
    private List<File> files = new ArrayList<>();
    private int selectedIndex = -1;

    private ListView listView;
    private ImageButton btnBack;
    private ImageButton btnClose;
    private FloatingActionButton btnOk;
    private TextView lblEmpty;

    private boolean sdCardWritable;
    private boolean openForWrite;
    private boolean filesOnly;
    private boolean dirsOnly;
    private int iconSide = -1;

    private static int compareFiles(File file, File file2) {
        if (file.isDirectory() && file2.isFile())
            return -1;
        else if (file.isFile() && file2.isDirectory())
            return 1;
        else
            return file.getPath().compareTo(file2.getPath());
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_open_file);
        setTitle(currentPath);
        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        for (int i = 0; i < toolbar.getChildCount(); i++) {
            View view = toolbar.getChildAt(i);
            if (view instanceof TextView) {
                TextView tv = (TextView) view;
                tv.setEllipsize(TextUtils.TruncateAt.START);
            }
        }

        SharedPreferences prefs = getSharedPreferences(SharedPreferencesAccessor.PREFS_NAME,
                SharedPreferencesAccessor.PREFS_MODE);
        sdCardWritable = prefs.getBoolean(SharedPreferencesAccessor.SD_CARD_WRITABLE, false);
        openForWrite = getIntent().getBooleanExtra(EXTRA_OPEN_FOR_WRITE, false);
        String viewMode = getIntent().getStringExtra(EXTRA_VIEW_MODE);
        filesOnly = MODE_FILES_ONLY.equals(viewMode);
        dirsOnly = MODE_DIRECTORIES_ONLY.equals(viewMode);
        String homeDir;
        if (savedInstanceState != null) {
            homeDir = savedInstanceState.getString(EXTRA_HOME_DIRECTORY);
        } else {
            homeDir = getIntent().getStringExtra(EXTRA_HOME_DIRECTORY);
        }
        if (homeDir != null) {
            currentPath = homeDir;
            setTitle(homeDir);
        }
        initializeUI();
    }

    @Override
    protected void onResume() {
        super.onResume();
        requestGrants();
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        outState.putString(EXTRA_HOME_DIRECTORY, currentPath);
        super.onSaveInstanceState(outState);
    }

    @Override
    public void onBackPressed() {
        if (!currentPath.equals(STORAGE_PATH)) {
            File parentDirectory;
            if (currentPath.equals(INTERNAL_MEMORY_PATH) || currentPath.equals(SDCARD_1_PATH) ||
                    currentPath.equals(SDCARD_PATH) || currentPath.equals(INTERNAL_MEMORY_0_PATH)) {
                parentDirectory = new File(STORAGE_PATH);
                btnBack.setVisibility(View.GONE);
            } else {
                File file = new File(currentPath);
                parentDirectory = file.getParentFile();
            }

            selectedIndex = -1;

            if (parentDirectory != null) {
                currentPath = parentDirectory.getPath();
                rebuildFiles((FileAdapter) listView.getAdapter());
            }
        }
    }

    /**
     * processes the signal to close an OpenFileActivity and return the RESULT_CANCELED result
     */
    public void finishActivity() {
        Intent intent = new Intent();
        intent.putExtra(EXTRA_URI, RESULT_CANCELED);
        setResult(RESULT_CANCELED, intent);
        super.onBackPressed();
    }

    private void initializeUI() {
        findViews();

        if (filesOnly) {
            btnOk.setVisibility(View.GONE);
        }
        files.addAll(getFiles(currentPath));
        listView.setAdapter(new FileAdapter(this, files));
        listView.setOnItemClickListener(new FileItemClickListener());
        btnBack.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                onBackPressed();
            }
        });
        btnOk.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (selectedIndex != -1) {
//                    Intent intent = new Intent();
//                    intent.putExtra(EXTRA_URI, listView.getItemAtPosition(selectedIndex).toString());
//                    intent.putExtra(EXTRA_SEND_MODE, getIntent().getStringExtra(EXTRA_SEND_MODE));
//                    setResult(RESULT_OK, intent);

                    Uri resultUri = Uri.fromFile(new File(listView.getItemAtPosition(selectedIndex).toString() )) ;
                    Intent result = new Intent();
                    result.setData(resultUri);
                    setResult(Activity.RESULT_OK, result);

                    finish();
                } else if (dirsOnly) {
                    Intent intent = new Intent();
                    intent.putExtra(EXTRA_URI, currentPath);
                    intent.putExtra(EXTRA_SEND_MODE, getIntent().getStringExtra(EXTRA_SEND_MODE));
                    setResult(RESULT_OK, intent);
                    finish();
                }
            }
        });
        btnClose.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                finishActivity();
            }
        });
    }

    private void findViews() {
        listView = (ListView) findViewById(R.id.lv_files);
        btnBack = (ImageButton) findViewById(R.id.btnBack_toolbar);
        btnClose = (ImageButton) findViewById(R.id.btnClose);
        btnOk = (FloatingActionButton) findViewById(R.id.fab);
        lblEmpty = (TextView) findViewById(R.id.lbl_empty);

        TextView title = (TextView) findViewById(android.R.id.title);
        if (title != null) {
            title.setEllipsize(TextUtils.TruncateAt.START);
        }
    }

    private List<File> getFiles(String directoryPath) {
        if (directoryPath.equals(STORAGE_PATH)) {
            ArrayList<File> fileList = new ArrayList<>();
            if (new File(INTERNAL_MEMORY_PATH).exists()) {
                fileList.add(new File(INTERNAL_MEMORY_PATH));
            } else if (new File(INTERNAL_MEMORY_0_PATH).exists()) {
                fileList.add(new File(INTERNAL_MEMORY_0_PATH));
            }
            if (new File(SDCARD_PATH).exists()) {
                fileList.add(new File(SDCARD_PATH));
            } else if (new File(SDCARD_1_PATH).exists()) {
                fileList.add(new File(SDCARD_1_PATH));
            }
            return fileList;
        } else {
            File directory = new File(directoryPath);
            File[] array = directory.listFiles();
            if (array != null) {
                List<File> items = Arrays.asList(array);
                ArrayList<File> fileList = new ArrayList<>(items);
                Collections.sort(fileList, new Comparator<File>() {
                    @Override
                    public int compare(File file, File file2) {
                        return compareFiles(file, file2);
                    }
                });
                return fileList;
            } else {
                Toast.makeText(this, R.string.lbl_directory_not_exist, Toast.LENGTH_SHORT).show();
                currentPath = directoryPath.substring(0, directoryPath.lastIndexOf("/"));
                setTitle(currentPath);
                return getFiles(currentPath);
            }
        }
    }

    private boolean rebuildFiles(ArrayAdapter<File> adapter) {
        try {
            List<File> fileList = getFiles(currentPath);
            if (!fileList.isEmpty()) {
                listView.setVisibility(View.VISIBLE);
                lblEmpty.setVisibility(View.GONE);
                files.clear();
                selectedIndex = -1;
                files.addAll(fileList);
                adapter.notifyDataSetChanged();
            } else {
                listView.setVisibility(View.GONE);
                lblEmpty.setVisibility(View.VISIBLE);
            }
            setTitle(currentPath);
            if (filesOnly || currentPath.equals(STORAGE_PATH)) {
                btnOk.setVisibility(View.GONE);
            } else {
                btnOk.setVisibility(View.VISIBLE);
            }
            return true;
        } catch (NullPointerException e) {
            UniversalHelper.logException(e);
            String message = getResources().getString(R.string.error_access_denied);
            Toast.makeText(this, message, Toast.LENGTH_LONG).show();
            return false;
        }
    }

    private void requestGrants() {

        String[] grants = new String[1];

        if (openForWrite) {
            grants[0] = Manifest.permission.WRITE_EXTERNAL_STORAGE;
        } else {
            grants[0] = Manifest.permission.READ_EXTERNAL_STORAGE;
        }

        for (String perms : grants) {
            if (ContextCompat.checkSelfPermission(this, perms) != PackageManager.PERMISSION_GRANTED &&
                    !ActivityCompat.shouldShowRequestPermissionRationale(this, perms)) {

                ActivityCompat.requestPermissions(this, new String[]{perms}, 0);
            }
        }
    }

    private class FileAdapter extends ArrayAdapter<File> {

        public FileAdapter(Context context, List<File> files) {
            super(context, R.layout.open_file_item, files);
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            View v = convertView;
            DataHolder holder;
            if (v == null) {
                LayoutInflater vi = (LayoutInflater) getSystemService(Context.LAYOUT_INFLATER_SERVICE);
                v = vi.inflate(R.layout.open_file_item, parent, false);
                holder = new DataHolder(v);
                v.setTag(holder);
            } else {
                holder = (DataHolder) v.getTag();
            }
            holder.lblFileName.setTextColor(ContextCompat.getColor(getContext(), R.color.black));
            File file = getItem(position);
            String path = file.getPath();
            if (path.equals(INTERNAL_MEMORY_PATH) || path.equals(INTERNAL_MEMORY_0_PATH)) {
                holder.lblFileName.setText(R.string.lbl_internal_memory);
            } else if (path.equals(SDCARD_PATH) || path.equals(SDCARD_1_PATH)) {
                holder.lblFileName.setText(R.string.lbl_sdcard);
            } else {
                holder.lblFileName.setText(file.getName());
            }
            if (selectedIndex == position)
                holder.llBody.setBackgroundColor(ContextCompat.getColor(getContext(), R.color.sky_blue));
            else
                holder.llBody.setBackgroundColor(ContextCompat.getColor(getContext(), R.color.transparent));


            if (UniversalHelper.hasImageExtension(file.getName())) {
                if (iconSide <= 0) {
                    iconSide = v.getHeight();
                }
                if (iconSide > 0) {
                    ViewGroup.LayoutParams lp = holder.imvFileIcon.getLayoutParams();
                    lp.width = iconSide;
                    lp.height = iconSide;
                    holder.imvFileIcon.setLayoutParams(lp);
                    holder.imvFileIcon.setVisibility(View.VISIBLE);
                    Picasso.with(OpenFileActivity.this).load(file).resize(iconSide, iconSide).centerInside().into(holder.imvFileIcon);
                }
            } else {
                holder.imvFileIcon.setVisibility(View.GONE);
                holder.imvFileIcon.setImageDrawable(null);
            }

            if (file.isDirectory()) {
                holder.lblFileSize.setText(R.string.file_folder);
                holder.lblChangedTime.setText(null);
            } else {
                String fileSize = UniversalHelper.getVisibleFileSize(OpenFileActivity.this, file.length());
                String sizeString = getString(R.string.lbl_size) + " " + fileSize;
                holder.lblFileSize.setText(sizeString);
                String timeString = ActivityGlobalManager.getTimeString(new Date(file.lastModified()));
                String dateString = String.format(getString(R.string.lbl_last_changed), timeString);
                holder.lblChangedTime.setText(dateString);
            }
            return v;
        }

        private class DataHolder {

            public LinearLayout llBody;
            public TextView lblFileName;
            public TextView lblFileSize;
            public TextView lblChangedTime;
            public ImageView imvFileIcon;

            public DataHolder(View view) {
                llBody = (LinearLayout) view.findViewById(R.id.ll_body);
                lblFileName = (TextView) view.findViewById(R.id.lbl_filename);
                lblFileSize = (TextView) view.findViewById(R.id.tv_size);
                lblChangedTime = (TextView) view.findViewById(R.id.tv_changed);
                imvFileIcon = (ImageView) view.findViewById(R.id.imv_icon);
            }
        }
    }

    private class FileItemClickListener implements AdapterView.OnItemClickListener {
        @Override
        public void onItemClick(AdapterView<?> adapterView, View view, int index, long l) {
            final ArrayAdapter<File> adapter = (FileAdapter) adapterView.getAdapter();
            File file = adapter.getItem(index);
            String sdPath = file.getPath();
            if ((sdPath.equals(SDCARD_PATH) || sdPath.equals(SDCARD_1_PATH)) && !sdCardWritable && openForWrite) {
                Toast.makeText(OpenFileActivity.this, R.string.error_sd_locked, Toast.LENGTH_LONG).show();
            } else {
                btnBack.setVisibility(View.VISIBLE);
                if (file.isDirectory()) {
                    currentPath = file.getPath();
                    btnOk.setEnabled(rebuildFiles(adapter));
                } else {
                    if (index != selectedIndex) {
                        selectedIndex = index;
                        btnOk.setVisibility(dirsOnly ? View.GONE : View.VISIBLE);
                    } else {
                        selectedIndex = -1;
                        btnOk.setVisibility(dirsOnly ? View.VISIBLE : View.GONE);
                    }
                    adapter.notifyDataSetChanged();
                }
            }
        }
    }
}
