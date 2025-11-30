package games.mrlaki5.soundtest.DataTransfer;

import android.Manifest;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.preference.PreferenceManager;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.view.View;
import android.widget.AdapterView;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import java.io.BufferedInputStream;
import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.util.ArrayList;

import games.mrlaki5.soundtest.R;
import games.mrlaki5.soundtest.Settings.SettingsActivity;
import games.mrlaki5.soundtest.SoundClient.CallbackSendRec;
import games.mrlaki5.soundtest.SoundClient.Receiver.RecordTask;
import games.mrlaki5.soundtest.SoundClient.Sender.BufferSoundTask;

public class DataTransferActivity extends AppCompatActivity implements CallbackSendRec {

    // File browser state
    private File currentFolder;
    private File rootFolder;
    private ListView myList;
    private View myView;
    private AlertDialog myDialog;

    // File/folder to send/receive
    private File sendFile = null;
    private File receiveFolder = null;

    // Sending/receiving flags
    private boolean sendingData = false;
    private boolean listeningData = false;

    // Tasks
    private BufferSoundTask sendTask = null;
    private RecordTask listeningTask = null;

    private ProgressBar sendingBar = null;

    // --- Android 11+ MANAGE_EXTERNAL_STORAGE helpers ---
    private static final int REQUEST_MANAGE_ALL_FILES = 200;
    private static final int PENDING_NONE = 0;
    private static final int PENDING_FILE_BROWSE = 1;
    private static final int PENDING_FOLDER_BROWSE = 2;
    private int pendingBrowseRequest = PENDING_NONE;

    // ===========================
    // Listeners
    // ===========================
    private AdapterView.OnItemClickListener adapSendListener = (parent, view, position, id) -> {
        File[] files = safeListFiles(currentFolder);
        if (files == null) return;

        if (!currentFolder.getAbsolutePath().equals(rootFolder.getAbsolutePath())) {
            // Not root folder: first item is "Back"
            if (position == 0) {
                currentFolder = currentFolder.getParentFile();
                loadAdapter();
            } else {
                File selected = files[position - 1];
                if (selected.isDirectory()) {
                    currentFolder = selected;
                    loadAdapter();
                } else {
                    chooseSendFile(selected);
                }
            }
        } else {
            File selected = files[position];
            if (selected.isDirectory()) {
                currentFolder = selected;
                loadAdapter();
            } else {
                chooseSendFile(selected);
            }
        }
    };

    private AdapterView.OnItemClickListener adapReceiveListener = (parent, view, position, id) -> {
        File[] files = safeListFiles(currentFolder);
        if (files == null) return;

        if (!currentFolder.getAbsolutePath().equals(rootFolder.getAbsolutePath())) {
            if (position == 0) {
                currentFolder = currentFolder.getParentFile();
                loadAdapter();
            } else {
                File selected = files[position - 1];
                if (selected.isDirectory()) {
                    currentFolder = selected;
                    loadAdapter();
                }
            }
        } else {
            File selected = files[position];
            if (selected.isDirectory()) {
                currentFolder = selected;
                loadAdapter();
            }
        }
    };

    private View.OnClickListener receiveExplorerButtonListener = v -> {
        receiveFolder = currentFolder;
        ((TextView) findViewById(R.id.receiveDataText)).setText(receiveFolder.getName());
        ImageView iv = findViewById(R.id.receiveDataImage);
        iv.setImageResource(R.drawable.folder_image);
        findViewById(R.id.receiveDataButt).setVisibility(View.VISIBLE);
        dismissDialog();
    };

    // ===========================
    // Activity lifecycle
    // ===========================
    @Override
    protected void onStop() {
        super.onStop();
        if (listeningTask != null) {
            stopListen();
            listeningTask.setWorkFalse();
        }
        if (sendTask != null) {
            stopSend();
            sendTask.setWorkFalse();
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_data_transfer);
        android.support.v7.app.ActionBar ab = getSupportActionBar();
        if (ab != null) ab.setTitle(R.string.data_transfer);
        sendingBar = findViewById(R.id.sendDataProgressBar);
    }

    // ===========================
    // File/Folder browser
    // ===========================
    private void browseFileExplorer() {
        currentFolder = Environment.getExternalStorageDirectory();
        rootFolder = currentFolder;
        AlertDialog.Builder mBuilder = new AlertDialog.Builder(this);
        myView = getLayoutInflater().inflate(R.layout.dialog_file_explorer, null);
        myList = myView.findViewById(R.id.dialogFExFilesList);
        myList.setOnItemClickListener(adapSendListener);
        loadAdapter();
        mBuilder.setView(myView);
        mBuilder.setMessage(R.string.choose_file);
        myDialog = mBuilder.create();
        myDialog.show();
    }

    public void browseFileExplorer(View view) {
        if (Build.VERSION.SDK_INT >= 30) {
            if (!isManageAllFilesGranted()) {
                pendingBrowseRequest = PENDING_FILE_BROWSE;
                requestManageAllFilesPermission();
                return;
            }
        }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, 0);
        } else {
            browseFileExplorer();
        }
    }

    private void browseFolderExplorer() {
        currentFolder = Environment.getExternalStorageDirectory();
        rootFolder = currentFolder;
        AlertDialog.Builder mBuilder = new AlertDialog.Builder(this);
        myView = getLayoutInflater().inflate(R.layout.dialog_folder_explorer, null);
        myList = myView.findViewById(R.id.dialogFolderExFilesList);
        myList.setOnItemClickListener(adapReceiveListener);
        myView.findViewById(R.id.dialogFolderExButton).setOnClickListener(receiveExplorerButtonListener);
        loadAdapter();
        mBuilder.setView(myView);
        mBuilder.setMessage(R.string.choose_folder);
        myDialog = mBuilder.create();
        myDialog.show();
    }

    public void browseFolderExplorer(View view) {
        if (Build.VERSION.SDK_INT >= 30) {
            if (!isManageAllFilesGranted()) {
                pendingBrowseRequest = PENDING_FOLDER_BROWSE;
                requestManageAllFilesPermission();
                return;
            }
        }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, 1);
        } else {
            browseFolderExplorer();
        }
    }

    // ===========================
    // Permission helpers
    // ===========================
    private void requestManageAllFilesPermission() {
        try {
            Intent intent = new Intent("android.settings.MANAGE_APP_ALL_FILES_ACCESS_PERMISSION",
                    Uri.parse("package:" + getPackageName()));
            startActivityForResult(intent, REQUEST_MANAGE_ALL_FILES);
        } catch (Exception e) {
            Intent intent = new Intent("android.settings.MANAGE_ALL_FILES_ACCESS_PERMISSION");
            startActivityForResult(intent, REQUEST_MANAGE_ALL_FILES);
        }
    }

    private boolean isManageAllFilesGranted() {
        if (Build.VERSION.SDK_INT < 30) return false;
        try {
            Class<?> envClass = Class.forName("android.os.Environment");
            java.lang.reflect.Method m = envClass.getMethod("isExternalStorageManager");
            Object res = m.invoke(null);
            if (res instanceof Boolean) return (Boolean) res;
        } catch (Exception ignored) {}
        return false;
    }

    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        if ((requestCode == 0 || requestCode == 1) && grantResults.length > 0
                && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            if (requestCode == 0) browseFileExplorer();
            else browseFolderExplorer();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_MANAGE_ALL_FILES && Build.VERSION.SDK_INT >= 30) {
            if (isManageAllFilesGranted()) {
                if (pendingBrowseRequest == PENDING_FILE_BROWSE) browseFileExplorer();
                else if (pendingBrowseRequest == PENDING_FOLDER_BROWSE) browseFolderExplorer();
            } else {
                Toast.makeText(this, "Please grant All files access to use the file browser.", Toast.LENGTH_LONG).show();
            }
            pendingBrowseRequest = PENDING_NONE;
        }
    }

    // ===========================
    // Adapter helpers
    // ===========================
    private void loadAdapter() {
        File[] files = safeListFiles(currentFolder);
        ArrayList<FileExplorerElement> folders = new ArrayList<>();
        if (files == null) files = new File[0];
        if (!currentFolder.getAbsolutePath().equals(rootFolder.getAbsolutePath())) {
            folders.add(new FileExplorerElement("Back", "", false, true));
        }
        for (File file : files) {
            String fileNameTmp = file.getName();
            String fileSizeTmp = "" + file.length() + "B";
            boolean isFolder = file.isDirectory();
            folders.add(new FileExplorerElement(fileNameTmp, fileSizeTmp, !isFolder, false));
        }
        FileExplorerAdapter adapter = new FileExplorerAdapter(this, folders);
        myList.setAdapter(adapter);
    }

    private File[] safeListFiles(File folder) {
        if (folder == null) return null;
        File[] files = folder.listFiles();
        return files;
    }

    private void chooseSendFile(File file) {
        sendFile = file;
        ((TextView) findViewById(R.id.sendDataText)).setText(sendFile.getName());
        ImageView iv = findViewById(R.id.sendDataImage);
        iv.setImageResource(R.drawable.file_image);
        findViewById(R.id.sendDataButt).setVisibility(View.VISIBLE);
        dismissDialog();
    }

    private void dismissDialog() {
        if (myDialog != null) {
            myDialog.dismiss();
            myDialog = null;
            myView = null;
        }
    }

    // ===========================
    // Send/Listen
    // ===========================
    public void sendData(View view) {
        if (sendFile == null) return;
        if (listeningData) {
            stopListen();
            if (listeningTask != null) listeningTask.setWorkFalse();
        }
        if (!sendingData) {
            try {
                byte[] bytes = new byte[(int) sendFile.length()];
                BufferedInputStream bis = new BufferedInputStream(new FileInputStream(sendFile));
                DataInputStream dis = new DataInputStream(bis);
                dis.readFully(bytes);
                sendingData = true;
                sendingBar.setVisibility(View.VISIBLE);
                findViewById(R.id.sendDataField).setClickable(false);
                ((Button) view).setText(R.string.stop);

                String[] nameParts = sendFile.getName().split("\\.");
                String ext = nameParts[nameParts.length - 1];
                byte[] nameBytes = ext.getBytes("UTF-8");

                Integer[] sendArguments = getSettingsArguments();
                sendTask = new BufferSoundTask();
                sendTask.setProgressBar(sendingBar);
                sendTask.setCallbackSR(this);
                sendTask.setBuffer(nameBytes);
                sendTask.setFileBuffer(bytes);
                sendTask.execute(sendArguments);
            } catch (Exception e) {
                e.printStackTrace();
            }
        } else {
            if (sendTask != null) sendTask.setWorkFalse();
            stopSend();
        }
    }

    public void listenData(View view) {
        if (receiveFolder == null) return;
        if (sendingData) {
            stopSend();
            if (sendTask != null) sendTask.setWorkFalse();
        }
        if (!listeningData) {
            try {
                listeningData = true;
                findViewById(R.id.receiveDataField).setClickable(false);
                ((Button) view).setText(R.string.stop);
                Integer[] sendArguments = getSettingsArguments();
                listeningTask = new RecordTask();
                listeningTask.setCallbackRet(this);
                listeningTask.setFileName(receiveFolder.getAbsolutePath());
                listeningTask.execute(sendArguments);
            } catch (Exception e) {
                e.printStackTrace();
            }
        } else {
            if (listeningTask != null) listeningTask.setWorkFalse();
            stopListen();
        }
    }

    private void stopSend() {
        sendingData = false;
        sendingBar.setVisibility(View.INVISIBLE);
        sendingBar.setProgress(1);
        findViewById(R.id.sendDataField).setClickable(true);
        ((Button) findViewById(R.id.sendDataButt)).setText(R.string.send);
    }

    private void stopListen() {
        listeningData = false;
        findViewById(R.id.receiveDataTextReceive).setVisibility(View.INVISIBLE);
        findViewById(R.id.receiveDataField).setClickable(true);
        ((Button) findViewById(R.id.receiveDataButt)).setText(R.string.listen);
    }

    private Integer[] getSettingsArguments() {
        Integer[] tempArr = new Integer[6];
        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(this);
        tempArr[0] = Integer.parseInt(preferences.getString(SettingsActivity.KEY_START_FREQUENCY,
                SettingsActivity.DEF_START_FREQUENCY));
        tempArr[1] = Integer.parseInt(preferences.getString(SettingsActivity.KEY_END_FREQUENCY,
                SettingsActivity.DEF_END_FREQUENCY));
        tempArr[2] = Integer.parseInt(preferences.getString(SettingsActivity.KEY_BIT_PER_TONE,
                SettingsActivity.DEF_BIT_PER_TONE));
        tempArr[3] = preferences.getBoolean(SettingsActivity.KEY_ENCODING, SettingsActivity.DEF_ENCODING) ? 1 : 0;
        tempArr[4] = preferences.getBoolean(SettingsActivity.KEY_ERROR_DETECTION, SettingsActivity.DEF_ERROR_DETECTION) ? 1 : 0;
        tempArr[5] = Integer.parseInt(preferences.getString(SettingsActivity.KEY_ERROR_BYTE_NUM,
                SettingsActivity.DEF_ERROR_BYTE_NUM));
        return tempArr;
    }

    // ===========================
    // CallbackSendRec
    // ===========================
    @Override
    public void actionDone(int srFlag, String message) {
        if (srFlag == CallbackSendRec.SEND_ACTION && sendingData) {
            stopSend();
            findViewById(R.id.sendDataButt).setVisibility(View.INVISIBLE);
            sendFile = null;
            ((TextView) findViewById(R.id.sendDataText)).setText(R.string.no_file_selected);
            ((ImageView) findViewById(R.id.sendDataImage)).setImageResource(R.drawable.file_image_grey);
            Toast.makeText(this, R.string.data_was_sent, Toast.LENGTH_LONG).show();
        } else if (srFlag == CallbackSendRec.RECEIVE_ACTION && listeningData) {
            stopListen();
            findViewById(R.id.receiveDataButt).setVisibility(View.INVISIBLE);
            receiveFolder = null;
            ((TextView) findViewById(R.id.receiveDataText)).setText(R.string.folder_not_selected);
            ((ImageView) findViewById(R.id.receiveDataImage)).setImageResource(R.drawable.folder_image_grey);
            Toast.makeText(this, getString(R.string.data_received) + " " + message, Toast.LENGTH_LONG).show();
        }
    }

    @Override
    public void receivingSomething() {
        findViewById(R.id.receiveDataTextReceive).setVisibility(View.VISIBLE);
    }
}
