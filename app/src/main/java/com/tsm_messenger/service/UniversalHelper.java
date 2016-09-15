package com.tsm_messenger.service;

import android.annotation.TargetApi;
import android.app.Activity;
import android.content.ContentUris;
import android.content.Context;
import android.content.Intent;
import android.content.res.Configuration;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.provider.DocumentsContract;
import android.provider.MediaStore;
import android.support.design.widget.Snackbar;
import android.support.v4.content.ContextCompat;
import android.support.v7.widget.PopupMenu;
import android.util.Log;
import android.view.MenuItem;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.WriterException;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import com.tsm_messenger.activities.BuildConfig;
import com.tsm_messenger.activities.R;
import com.tsm_messenger.activities.TsmTemplateActivity;
import com.tsm_messenger.activities.control.TsmMessageDialog;
import com.tsm_messenger.activities.main.DownloadsActivity;
import com.tsm_messenger.activities.service.OpenFileActivity;
import com.tsm_messenger.connection.MessagePostman;
import com.tsm_messenger.data.storage.ChatUnit;
import com.tsm_messenger.data.storage.DbChatMessage;
import com.tsm_messenger.data.storage.DbGroupChat;
import com.tsm_messenger.data.storage.DbMessengerUser;
import com.tsm_messenger.data.storage.FileData;
import com.tsm_messenger.protocol.transaction.Param;
import com.tsm_messenger.protocol.transaction.Request;

import org.bouncycastle.util.encoders.Hex;

import java.io.File;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * **********************************************************************
 * <p/>
 * TELESENS CONFIDENTIAL
 * __________________
 * <p/>
 * [2014] Telesens International Limited
 * All Rights Reserved.
 * <p/>
 * NOTICE:  All information contained herein is, and remains
 * the property of Telesens International Limited and its suppliers,
 * if any.  The intellectual and technical concepts contained
 * herein are proprietary to Telesens International Limited
 * and its suppliers and may be covered by U.S. and Foreign Patents,
 * patents in process, and are protected by trade secret or copyright law.
 * Dissemination of this information or reproduction of this material
 * is strictly forbidden unless prior written permission is obtained
 * from Telesens International Limited.
 * <p/>
 */

public class UniversalHelper {

    private static TsmMessageDialog fileIncomingDialog;
    private static int incomingFilesCount = 0;

    private static boolean doubleBackToExitPressedOnce = false;

    /**
     * A constructor to initialize UniversalHelper instance     *
     * not used because of all members are static
     */
    private UniversalHelper() {
        //do nothing
    }

    /**
     * Checks if all provided parameters are not null
     *
     * @param parametersNumber a count of parameters to initialize the cycle
     * @param objectsToCheck   the Object instances that are needed to check for not-null
     * @return true if all parameters are not null, returns false if not
     */
    public static boolean checkNotNull(int parametersNumber, Object... objectsToCheck) {
        boolean result = true;
        if (parametersNumber != 0) {
            if (objectsToCheck != null && objectsToCheck.length > 0) {
                for (int i = 0; i < parametersNumber; i++) {
                    if (objectsToCheck[i] == null) {
                        result = false;
                        break;
                    }
                }
            } else {
                result = false;
            }
        }
        return result;
    }

    /**
     * Transforms a provided BitMatrix object to a bitmap
     *
     * @param matrix a matrix to transform
     * @return returns a bitmap generated from a BitMatrix object
     */
    public static Bitmap matrixToBitmap(BitMatrix matrix) {
        Bitmap bmp;
        int width, height;
        if (matrix != null) {
            height = matrix.getHeight();
            width = matrix.getWidth();
        } else {
            height = width = 1;
        }
        bmp = Bitmap.createBitmap(width, height, Bitmap.Config.RGB_565);
        for (int x = 0; x < width; x++) {
            for (int y = 0; y < height; y++) {
                boolean val = matrix != null && matrix.get(x, y);
                bmp.setPixel(x, y, val ? Color.BLACK : Color.WHITE);
            }
        }
        return bmp;
    }

    /**
     * transforms a provided byte array to a bitmap of requested size
     *
     * @param bytesToEncode byte array to make a QR-code
     * @param layoutSize    a size of a screen to show bitmap
     * @return returns a bitmap generated from a byte array
     */
    public static Bitmap bytesArrayToBitmap(byte[] bytesToEncode, int layoutSize) {
        if (bytesToEncode != null) {
            QRCodeWriter writer = new QRCodeWriter();
            BitMatrix matrix;
            try {
                int width;
                int height;
                switch (layoutSize) {
                    case Configuration.SCREENLAYOUT_SIZE_SMALL:
                        width = 200;
                        height = 200;
                        break;
                    case Configuration.SCREENLAYOUT_SIZE_LARGE:
                        width = 600;
                        height = 600;
                        break;
                    case Configuration.SCREENLAYOUT_SIZE_XLARGE:
                        width = 800;
                        height = 800;
                        break;
                    default:
                        width = 400;
                        height = 400;
                }
                matrix = writer.encode(Hex.toHexString(bytesToEncode), BarcodeFormat.QR_CODE, width, height);
            } catch (WriterException e) {
                UniversalHelper.logException(e);
                matrix = null;
            }

            return UniversalHelper.matrixToBitmap(matrix);
        } else {
            int[] colors = new int[3];
            Arrays.fill(colors, 0);
            return Bitmap.createBitmap(colors, 1, 1, Bitmap.Config.RGB_565);
        }
    }

    /**
     * Performs an exception logging
     *
     * @param e an exception to log
     */
    public static void logException(Throwable e) {
        if (e != null) {
            String message = e.getMessage();
            if (message == null) {
                message = "null";
            }
            Logger.getAnonymousLogger().log(Level.WARNING, message);
            if (BuildConfig.DEBUG) {
                for (StackTraceElement traceElement : e.getStackTrace()) {
                    message += "\n\tat " + traceElement.toString();
                }
            }
        }
    }

    /**
     * Show log message in debug mode only
     *
     * @param priority a type of message priority
     * @param tag      a group of message
     * @param message  a text of message
     */
    public static void debugLog(int priority, String tag, String message) {
        if (BuildConfig.DEBUG) {
            if (priority == Log.ERROR) {
                Log.println(priority, tag, "====================");
                Thread.dumpStack();
                Log.println(priority, tag, "====================");
            }
            Log.println(priority, tag, message);
        }
    }

    /**
     * Generates a popup menu with actions available for a provided file
     *
     * @param view     a view to anchor the popup menu
     * @param fileData a file to check available actions
     * @param activity an activity to generate a popup menu
     */
    public static void showFileOptionsPopup(View view, final FileData fileData, final Activity activity) {
        if (fileData != null && checkNotNull(4, view, activity, fileData.getFilePath(), fileData.getMessage())) {

            if ((new File(fileData.getFilePath()).exists()) &&
                    (fileData.getMessage().getServerstate() == DbChatMessage.FileServerStatus.ERROR) ||
                    fileData.getPercentcomplite() >= 100) {

                PopupMenu fileDoneMenu = new PopupMenu(activity, view);
                fileDoneMenu.inflate(R.menu.file_done_menu);

                MenuItem openItem = fileDoneMenu.getMenu().findItem(R.id.action_open);
                if (openItem != null) {
                    if (fileData.getMessage().getServerstate() == DbChatMessage.FileServerStatus.ERROR) {
                        openItem.setVisible(false);
                    }

                    openItem.setOnMenuItemClickListener(new MenuItem.OnMenuItemClickListener() {
                        @Override
                        public boolean onMenuItemClick(MenuItem item) {
                            openFile(fileData, activity);
                            return true;
                        }
                    });
                }

                MenuItem deleteItem = fileDoneMenu.getMenu().findItem(R.id.action_delete);
                if (deleteItem != null) {
                    deleteItem.setOnMenuItemClickListener(new MenuItem.OnMenuItemClickListener() {
                        @Override
                        public boolean onMenuItemClick(MenuItem item) {
                            deleteFile(fileData, activity);
                            return true;
                        }
                    });
                }
                fileDoneMenu.show();
            } else {
                Toast.makeText(activity, R.string.error_no_actions, Toast.LENGTH_SHORT).show();
            }
        }
    }

    /**
     * Generates and shows a dialog window informing about a new incoming file
     *
     * @param senderName   a name of the author of a file
     * @param incomingFile an incoming file
     * @param chat         a chat containing a message with an incoming file
     * @param message      a message containing an incoming file
     * @param context      a context to build a dialog
     */
    public static void showFileIncomingDialog(String senderName, final FileData incomingFile,
                                              final ChatUnit chat, final DbChatMessage message,
                                              Context context) {
        final Long fileSize = incomingFile.getFileSize();
        final String showFileSize = getVisibleFileSize(context, incomingFile.getFileSize());

        if (checkNotNull(7, senderName, incomingFile, chat, message, context,
                fileSize, showFileSize)) {
            if (fileIncomingDialog != null && fileIncomingDialog.isShowing()) {
                updateFileIncomingDialog(context);
            } else {
                createFileIncomingDialog(senderName, incomingFile, chat, message,
                        context, fileSize, showFileSize);
            }
        }
    }

    /**
     * Gets a string to display file size compactly
     *
     * @return a string representation of a file size
     */
    public static String getVisibleFileSize(Context context, long fileSize) {
        String showSize;

        String sizeDimension;
        int dimensionRate = 0;
        double doubleSize = fileSize;
        while (doubleSize / 1024 >= 1 && dimensionRate <= 4) {
            doubleSize = doubleSize / 1024;
            dimensionRate++;
        }
        switch (dimensionRate) {
            case 1:
                sizeDimension = context.getString(R.string.lbl_kbyte);
                break;
            case 2:
                sizeDimension = context.getString(R.string.lbl_mbyte);
                break;
            case 3:
                sizeDimension = context.getString(R.string.lbl_gbyte);
                break;
            case 4:
                sizeDimension = context.getString(R.string.lbl_tbyte);
                break;
            default:
                sizeDimension = context.getString(R.string.lbl_byte);
        }

        String format = "B".equals(sizeDimension) ? "%8.0f" : "%7.2f";

        showSize = String.format(format, doubleSize) + " " + sizeDimension;
        return showSize.trim();
    }

    private static void updateFileIncomingDialog(Context context) {
        if (checkNotNull(2, context, fileIncomingDialog)) {
            incomingFilesCount++;
            String incomingFilesCountString = context.getString(R.string.you_have) + " " +
                    String.format(context.getResources()
                            .getQuantityString(R.plurals.incoming_file, incomingFilesCount), incomingFilesCount);

            fileIncomingDialog.setMessage(incomingFilesCountString);
            fileIncomingDialog.hidePositiveButton();
            fileIncomingDialog.hideNegativeButton();
        }
    }

    private static void createFileIncomingDialog(String senderName, final FileData incomingFile,
                                                 final ChatUnit chat, final DbChatMessage message,
                                                 final Context context, final Long fileSize,
                                                 String showFileSize) {
        if (incomingFile != null &&
                checkNotNull(7, senderName, chat, message, context, fileSize, showFileSize,
                        incomingFile.getFileName())) {
            incomingFilesCount = 1;
            fileIncomingDialog = new TsmMessageDialog(context);
            fileIncomingDialog.setTitle(R.string.title_file_incoming);
            final String fileIncomingMessage = String.format(context.getString(R.string.info_file_receive),
                    senderName, incomingFile.getFileName(),
                    showFileSize);
            fileIncomingDialog.setMessage(fileIncomingMessage);
            fileIncomingDialog.setPositiveButton(R.string.btn_ok, new View.OnClickListener() {
                @Override
                public void onClick(View view) {

                    sendFileAcceptRequest(incomingFile, fileSize, chat, message);
                }
            });
            fileIncomingDialog.setNeutralButton(R.string.btn_details, new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    if (!(ActivityGlobalManager.getInstance().getCurrentActivity() instanceof DownloadsActivity)) {
                        Intent intent = new Intent(context, DownloadsActivity.class);
                        context.startActivity(intent);
                    }
                    fileIncomingDialog.dismiss();
                }
            });
            fileIncomingDialog.setNegativeButton(R.string.btn_cancel, new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    File file = new File(incomingFile.getFilePath());
                    if (file.exists()) {
                        file.delete();
                    }
                    sendFileDeclineRequest(incomingFile, fileSize);
                    fileIncomingDialog.dismiss();
                }
            });
            fileIncomingDialog.show();
        }
    }

    /**
     * Calls a needed program to open a file
     *
     * @param fileData a file to open
     * @param activity aAn activity to start a program opening file
     */
    public static void openFile(FileData fileData, Activity activity) {
        if (fileData != null && checkNotNull(2, activity, fileData.getFilePath())) {
            // open file
            File file = new File(fileData.getFilePath());
            String url = fileData.getFilePath().toLowerCase();
            Uri uri = Uri.fromFile(file);

            Intent intent = new Intent();

            intent.setAction(Intent.ACTION_VIEW);
            if (url.contains(".doc") || url.contains(".docx")) {
                // Word document
                intent.setDataAndType(uri, "application/msword");
            } else if (url.contains(".pdf")) {
                // PDF file
                intent.setDataAndType(uri, "application/pdf");
            } else if (url.contains(".ppt") || url.contains(".pptx")) {
                // Powerpoint file
                intent.setDataAndType(uri, "application/vnd.ms-powerpoint");
            } else if (url.contains(".xls") || url.contains(".xlsx")) {
                // Excel file
                intent.setDataAndType(uri, "application/vnd.ms-excel");
            } else if (url.contains(".zip") || url.contains(".rar")) {
                // WAV audio file
                intent.setDataAndType(uri, "application/x-wav");
            } else if (url.contains(".rtf")) {
                // RTF file
                intent.setDataAndType(uri, "application/rtf");
            } else if (url.contains(".wav") || url.contains(".mp3")) {
                // WAV audio file
                intent.setDataAndType(uri, "audio/x-wav");
            } else if (url.contains(".gif")) {
                // GIF file
                intent.setDataAndType(uri, "image/gif");
            } else if (url.contains(".jpg") || url.contains(".jpeg") || url.contains(".png")) {
                // JPG file
                intent.setDataAndType(uri, "image/jpeg");
            } else if (url.contains(".txt")) {
                // Text file
                intent.setDataAndType(uri, "text/plain");
            } else if (url.contains(".3gp") || url.contains(".mpg") || url.contains(".mpeg") ||
                    url.contains(".mpe") || url.contains(".mp4") || url.contains(".avi")) {
                // Video files
                intent.setDataAndType(uri, "video/*");
            } else {
                intent.setDataAndType(uri, "*/*");
            }
            activity.startActivityForResult(intent, 1);
        }
    }

    /**
     * Removes a file from file system
     *
     * @param fileData a file to delete
     * @param activity a toast to make a message about a result
     */
    public static void deleteFile(FileData fileData, Activity activity) {
        if (fileData != null && checkNotNull(2, fileData.getFilePath(), activity)) {
            File file = new File(fileData.getFilePath());
            if (file.delete()) {
                Toast.makeText(activity, R.string.info_file_deleted, Toast.LENGTH_LONG).show();
            } else {
                Toast.makeText(activity, R.string.info_file_delete_error, Toast.LENGTH_LONG).show();
            }
        }
    }

    /**
     * Sends a file decline request to the server to cancel file transfer
     *
     * @param incomingFile a file to decline
     * @param fileSize     a size of an incoming file
     */
    public static void sendFileDeclineRequest(FileData incomingFile, Long fileSize) {
        if (incomingFile != null &&
                checkNotNull(3, fileSize, incomingFile.getFileId(), incomingFile.getMessage())) {
            MessagePostman.getInstance().sendFileAnswerRequest(
                    Request.FileAnswerRequest.AnswerType.DECLINE,
                    incomingFile.getFileId(), incomingFile.getMode(),
                    fileSize.toString());
            incomingFile.getMessage().setServerstate(DbChatMessage.FileServerStatus.ERROR);
            incomingFile.getMessage().setDbStatus(DbChatMessage.MessageDatabaseStatus.NEW);
            incomingFile.setIsPending(false);
        }
    }

    /**
     * Sends a file decline request to the server to start file transfer
     *
     * @param incomingFile an incoming file
     * @param fileSize     a size of an incoming file
     * @param chat         a chat containing a message with the incoming file
     * @param message      a message containing an incoming file
     */
    public static void sendFileAcceptRequest(FileData incomingFile, Long fileSize, ChatUnit chat, DbChatMessage message) {
        if (incomingFile != null && chat != null &&
                checkNotNull(3, fileSize, message, incomingFile.getFileId(), chat.getOutQueue())) {
            MessagePostman.getInstance().sendFileAnswerRequest(
                    Request.FileAnswerRequest.AnswerType.ACCEPT,
                    incomingFile.getFileId(), incomingFile.getMode(),
                    fileSize.toString());
            incomingFile.setIsPending(false);

            chat.prepareChatMessageForsend(message);
            chat.getOutQueue().add(message);
            chat.activateOutThread();

            if (fileIncomingDialog != null) {
                try {
                    fileIncomingDialog.dismiss();
                } catch (IllegalArgumentException iae) {
                    UniversalHelper.logException(iae);
                }
            }
        }
    }

    /**
     * Processes the back-button click for double-back-to-close feature implementation
     *
     * @param activity an activity to finish if double click was done
     */
    public static void pressBackButton(Activity activity) {
        if (activity != null && activity.getApplication() != null) {
            if (doubleBackToExitPressedOnce) {
                activity.finish();
                ((ActivityGlobalManager) activity.getApplication()).finishApp();
                activity.getParent().onBackPressed();
            } else {
                doubleBackToExitPressedOnce = true;
                Toast.makeText(activity, R.string.press_back_to_exit, Toast.LENGTH_LONG).show();

                new Handler(Looper.getMainLooper()).postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        doubleBackToExitPressedOnce = false;
                    }
                }, 2000);
            }
        }
    }

    /**
     * Changes the color of an appbar to show the online state
     *
     * @param isOnline a flag showing users online state
     * @param activity an activity containing an appbar to repaint
     */
    public static void setAppbarColor(boolean isOnline, TsmTemplateActivity activity) {
        if (activity != null) {
            View appbar = activity.findViewById(R.id.appbar);
            int colorId = isOnline ? R.color.colorPrimary : R.color.grey_6;
            if (appbar != null) {
                appbar.setBackgroundColor(ContextCompat.getColor(activity, colorId));
            }
        }
    }

    /**
     * Sends a contact request to a user
     *
     * @param messengerId a user to send a contact request
     */
    public static void requestContact(String messengerId) {
        if (checkNotNull(2, messengerId, MessagePostman.getInstance())) {
            ArrayList<String> notificationAcceptors = new ArrayList<>(1);

            notificationAcceptors.add(messengerId);

            MessagePostman.getInstance().sendNotificationRequest(notificationAcceptors);
        }
        if (ActivityGlobalManager.getInstance() != null &&
                ActivityGlobalManager.getInstance().getDbContact() != null &&
                ActivityGlobalManager.getInstance().getDbContact().getMessengerDb() != null) {
            DbMessengerUser dbMessengerUser = ActivityGlobalManager.getInstance().getDbContact()
                    .getMessengerDb().get(messengerId);
            if (dbMessengerUser != null) {
                dbMessengerUser.setStatus(CustomValuesStorage.CATEGORY_CONFIRM_OUT);
            }
        }
    }

    /**
     * Shows an alert that there are too much people in chat to add more
     *
     * @param activity an activity to generate the dialog
     */
    public static void showGroupOverflowError(Activity activity) {
        if (activity != null) {
            String errorMessage = String.format(activity.getString(R.string.error_group_member_limit), DbGroupChat.MAX_GROUP_COUNT);
            Toast.makeText(activity, errorMessage, Toast.LENGTH_LONG).show();
        }
    }

    /**
     * Gets the resource id for a group icon depending on its chat security type
     *
     * @param currentChat a chat to get security type
     * @return an integer id of a resource
     */
    public static int getGroupBackgroundId(ChatUnit currentChat) {
        int result;
        if (currentChat == null || currentChat.getSecureType()
                .equals(Integer.valueOf(Param.ChatSecureLevel.ALL_HISTORY_KEEP))) {
            result = R.drawable.group_rect;
        } else if (Integer.valueOf(Param.ChatSecureLevel.NOTHING_KEEP).equals(currentChat.getSecureType())) {
            result = R.drawable.group_phantom_rect;
        } else {
            result = R.drawable.group_nohistory_rect;
        }
        return result;
    }

    /**
     * Gets the id of a string resource describing the chat secure type
     *
     * @param currentChat A chat to get a secure type
     * @return an integer resource id
     */
    public static int getChatSecureTypeDetails(ChatUnit currentChat) {
        int result;
        if (currentChat == null) {
            result = R.string.lbl_securetype_all_history_keep_details;
        } else result = getSecureTypeDetailsString(currentChat.getSecureType());
        return result;
    }

    /**
     * Gets the id of a string resource describing the provided secure type
     *
     * @param secureType a secureType to get a description
     * @return an integer resource ID
     */
    public static int getSecureTypeDetailsString(int secureType) {
        int result;
        if (secureType == Integer.valueOf(Param.ChatSecureLevel.ALL_HISTORY_KEEP)) {
            result = R.string.lbl_securetype_all_history_keep_details;
        } else if (secureType == Integer.valueOf(Param.ChatSecureLevel.NOTHING_KEEP)) {
            result = R.string.lbl_securetype_nothing_keep_details;
        } else if (secureType == 0) {
            result = R.string.lbl_securetype_keep_until_delivery_details;
        } else {
            result = R.string.lbl_securetype_keep_until_lifetime_details;
        }
        return result;
    }

    /**
     * Gets the genetive form of a message life time
     *
     * @param context    a context to get resources
     * @param typeCode a secureType to define the life time
     * @return a string containing the genetive form of a message life time
     */
    public static String getLifeTimeGenitive(Context context, Integer typeCode) {
        int timeMarker;
        int secureTypeToString;
        int secureType = typeCode == null ? 0 : typeCode;

        int lifeTimeMarker = getLifeTimeMarker(secureType);
        switch (lifeTimeMarker) {
            case 0:
                timeMarker = R.plurals.minute_genetive;
                secureTypeToString = secureType;
                break;
            case 1:
                timeMarker = R.plurals.hour_genetive;
                secureTypeToString = (int) TimeUnit.MINUTES.toHours(secureType);
                break;
            case 2:
                timeMarker = R.plurals.day_genetive;
                secureTypeToString = (int) TimeUnit.MINUTES.toDays(secureType);
                break;
            default:
                timeMarker = R.plurals.week_genetive;
                secureTypeToString = ((int) TimeUnit.MINUTES.toDays(secureType)) / 7;
        }

        if (context == null) {
            return String.valueOf(secureType);
        } else {
            return String.format(context.getResources().getQuantityString(timeMarker, secureTypeToString), secureTypeToString);
        }
    }

    /**
     * Gets the marker of a time unit for the provided secure type
     *
     * @param typeCode a secure type of a chat to calculate a message life time
     * @return an integer marker of a time unit
     */
    public static int getLifeTimeMarker(int typeCode) {
        int secureType = typeCode;
        int timeMarker = 0;
        int timeMax = 60;
        while (secureType > timeMax && timeMarker != 3) {
            secureType = secureType / timeMax;
            switch (timeMarker) {
                case 0:
                    timeMarker = 1;
                    timeMax = 24;
                    break;
                case 1:
                    timeMarker = 2;
                    timeMax = 7;
                    break;
                default:
                    timeMarker = 3;
                    timeMax = 4;
            }
        }
        return timeMarker;
    }

    /**
     * Gets the string label of a message life time for the provided secure type
     *
     * @param context    a context to get the resources
     * @param typeCode a secure type to get the message life time
     * @return a string label for a message life time
     */
    public static String getLifeTimeLabel(Context context, Integer typeCode) {
        int timeMarker;
        int secureTypeToString;
        int secureType = typeCode == null ? 0 : typeCode;
        int lifeTimeMarker = getLifeTimeMarker(secureType);
        switch (lifeTimeMarker) {
            case 0:
                timeMarker = R.plurals.minute;
                secureTypeToString = secureType;
                break;
            case 1:
                timeMarker = R.plurals.hour;
                secureTypeToString = (int) TimeUnit.MINUTES.toHours(secureType);
                break;
            case 2:
                secureTypeToString = (int) TimeUnit.MINUTES.toDays(secureType);
                timeMarker = R.plurals.day;
                break;
            default:
                secureTypeToString = ((int) TimeUnit.MINUTES.toDays(secureType)) / 7;
                timeMarker = R.plurals.week;
        }

        if (context == null) {
            return String.valueOf(secureType);
        } else {
            return String.format(
                    context.getResources().getQuantityString(timeMarker, secureTypeToString),
                    secureTypeToString);
        }
    }

    /**
     * Sets the needed label to a provided text view according to a provided secure type
     *
     * @param context         a context to get string resources
     * @param tvContactStatus a textView to set the label
     * @param typeCode      a secure type of a chat to find a needed label
     */
    public static void setLblSecureType(Context context, TextView tvContactStatus, Integer typeCode) {
        int secureType = typeCode == null ? 0 : typeCode;

        if (checkNotNull(2, context, tvContactStatus)) {
            switch (String.valueOf(secureType)) {
                case Param.ChatSecureLevel.ALL_HISTORY_KEEP:
                    tvContactStatus.setText(R.string.lbl_securetype_all_history_keep);
                    break;
                case Param.ChatSecureLevel.NOTHING_KEEP:
                    tvContactStatus.setText(R.string.lbl_securetype_nothing_keep);
                    break;
                default:
                    if (secureType == 0) {
                        tvContactStatus.setText(R.string.lbl_securetype_keep_until_delivery);
                    } else {
                        tvContactStatus.setText(
                                String.format(context.getString(R.string.lbl_securetype_keep_until_lifetime),
                                        UniversalHelper.getLifeTimeLabel(context, secureType)));
                    }
            }
        }
    }

    /**
     * Checks if current operation system version supports writing to an SD-card by apps
     *
     * @return true if sd-card is writable
     */
    public static boolean isSdCardWritable() {
        boolean result;
        try {
            File file = null;
            boolean fileCreated = false;
            int equalCount = 0;
            String EXT = ".tmp";
            String fileName = "tmp";
            String exportPath = fileName + EXT;
            while (!fileCreated) {
                file = new File("storage/external_SD", exportPath);
                if (file.exists()) {
                    exportPath = fileName + "(" + ++equalCount + ")" + EXT;
                } else {
                    fileCreated = file.createNewFile();
                }
            }
            FileOutputStream fos = new FileOutputStream(file);
            fos.write("Hello world".getBytes());
            fos.flush();
            fos.close();

            result = file.delete();
        } catch (Exception e) {
            UniversalHelper.logException(e);
            result = false;
        }
        return result;
    }

    /**
     * checks if the file represents an image by its extension
     *
     * @param fileName the name of a file
     * @return returns true if file has a valid image extension for android,
     * returns false if not
     */
    public static boolean hasImageExtension(String fileName) {
        boolean result = false;
        int lastDotIndex = fileName.lastIndexOf(".");
        if(lastDotIndex >= 0) {
            String ext = fileName.substring(lastDotIndex);
            if(".jpg".equalsIgnoreCase(ext) || ".gif".equalsIgnoreCase(ext) ||
                    ".png".equalsIgnoreCase(ext) || ".bmp".equalsIgnoreCase(ext) ||
                    ".webp".equalsIgnoreCase(ext)){
                result = true;
            }
        }
        return result;
    }

    public static void refreshOnlineStatus(TextView tvOnlineState, DbMessengerUser participant, Boolean... isForHeader) {
        boolean forHeader = isForHeader != null && isForHeader.length > 0 && isForHeader[0];
        Integer dbStatus = participant.getDbStatus();
        int resId = R.drawable.status_unknown;
        if (dbStatus == CustomValuesStorage.CATEGORY_CONNECT) {
            CustomValuesStorage.UserStatus statusOnline = participant.getStatusOnline();
            if (statusOnline == CustomValuesStorage.UserStatus.ONLINE) {
                resId = forHeader ? R.drawable.status_online_header : R.drawable.status_online;
            } else if (statusOnline == CustomValuesStorage.UserStatus.OFFLINE) {
                resId = forHeader ? R.drawable.status_offline_header : R.drawable.status_offline;
            } else {
                resId = forHeader ? R.drawable.status_unreachable_header : R.drawable.status_unreachable;
            }
        } else if (dbStatus == CustomValuesStorage.CATEGORY_DELETE) {
            resId = forHeader ? R.drawable.status_unreachable_header : R.drawable.status_unreachable;
        }
        tvOnlineState.setBackgroundResource(resId);
    }

    /**
     * Shows a snackBar with a custom text
     *
     * @param message a message to display
     */
    public static void showSnackBar(View anchor, Context context, String message) {
        final Snackbar snackbar = Snackbar
                .make(anchor, message, Snackbar.LENGTH_INDEFINITE);

        View sbView = snackbar.getView();
        TextView textView = (TextView) sbView.findViewById(android.support.design.R.id.snackbar_text);
        textView.setTextColor(Color.WHITE);
        textView.setSingleLine(false);

        snackbar.setAction(R.string.btn_ok, new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                snackbar.dismiss();
            }
        });
        snackbar.setActionTextColor(Color.WHITE);
        snackbar.getView().setBackgroundColor(ContextCompat.getColor(context, R.color.colorAccent));

        snackbar.show();

    }

    /**
     * Get a file path from a Uri. This will get the the path for Storage Access
     * Framework Documents, as well as the _data field for the MediaStore and
     * other file-based ContentProviders.
     *
     * @param context The context.
     * @param uri The Uri to query.
     */
    @TargetApi(19)
    public static String getPath(final Context context, final Uri uri) {

        final boolean isKitKat = Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT;

        // DocumentProvider
        if (isKitKat && DocumentsContract.isDocumentUri(context, uri)) {
            // ExternalStorageProvider
            if (isExternalStorageDocument(uri)) {
                final String docId = DocumentsContract.getDocumentId(uri);
                final String[] split = docId.split(":");
                final String type = split[0];

                if ("primary".equalsIgnoreCase(type)) {
                    return Environment.getExternalStorageDirectory() + "/" + split[1];
                }else{
                    String storage = null;
                    if(new File(OpenFileActivity.SDCARD_PATH).exists()){
                        storage = OpenFileActivity.SDCARD_PATH;
                    }else if(new File(OpenFileActivity.SDCARD_1_PATH).exists()){
                        storage = OpenFileActivity.SDCARD_1_PATH;
                    }
                    if(storage != null){
                        return storage + "/" + split[1];
                    }
                }
            }
            // DownloadsProvider
            else if (isDownloadsDocument(uri)) {

                final String id = DocumentsContract.getDocumentId(uri);
                final Uri contentUri = ContentUris.withAppendedId(
                        Uri.parse("content://downloads/public_downloads"), Long.valueOf(id));

                return getDataColumn(context, contentUri, null, null);
            }
            // MediaProvider
            else if (isMediaDocument(uri)) {
                final String docId = DocumentsContract.getDocumentId(uri);
                final String[] split = docId.split(":");
                final String type = split[0];

                Uri contentUri = null;
                if ("image".equals(type)) {
                    contentUri = MediaStore.Images.Media.EXTERNAL_CONTENT_URI;
                } else if ("video".equals(type)) {
                    contentUri = MediaStore.Video.Media.EXTERNAL_CONTENT_URI;
                } else if ("audio".equals(type)) {
                    contentUri = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI;
                }

                final String selection = "_id=?";
                final String[] selectionArgs = new String[] {
                        split[1]
                };

                return getDataColumn(context, contentUri, selection, selectionArgs);
            }
        }
        // MediaStore (and general)
        else if ("content".equalsIgnoreCase(uri.getScheme())) {
            return getDataColumn(context, uri, null, null);
        }
        // File
        else if ("file".equalsIgnoreCase(uri.getScheme())) {
            return uri.getPath();
        }

        return null;
    }

    /**
     * Get the value of the data column for this Uri. This is useful for
     * MediaStore Uris, and other file-based ContentProviders.
     *
     * @param context The context.
     * @param uri The Uri to query.
     * @param selection (Optional) Filter used in the query.
     * @param selectionArgs (Optional) Selection arguments used in the query.
     * @return The value of the _data column, which is typically a file path.
     */
    public static String getDataColumn(Context context, Uri uri, String selection,
                                       String[] selectionArgs) {

        Cursor cursor = null;
        final String column = "_data";
        final String[] projection = {
                column
        };

        try {
            cursor = context.getContentResolver().query(uri, projection, selection, selectionArgs,
                    null);
            if (cursor != null && cursor.moveToFirst()) {
                final int columnIndex = cursor.getColumnIndexOrThrow(column);
                return cursor.getString(columnIndex);
            }
        } finally {
            if (cursor != null)
                cursor.close();
        }
        return null;
    }


    /**
     * @param uri The Uri to check.
     * @return Whether the Uri authority is ExternalStorageProvider.
     */
    public static boolean isExternalStorageDocument(Uri uri) {
        return "com.android.externalstorage.documents".equals(uri.getAuthority());
    }

    /**
     * @param uri The Uri to check.
     * @return Whether the Uri authority is DownloadsProvider.
     */
    public static boolean isDownloadsDocument(Uri uri) {
        return "com.android.providers.downloads.documents".equals(uri.getAuthority());
    }

    /**
     * @param uri The Uri to check.
     * @return Whether the Uri authority is MediaProvider.
     */
    public static boolean isMediaDocument(Uri uri) {
        return "com.android.providers.media.documents".equals(uri.getAuthority());
    }
}
