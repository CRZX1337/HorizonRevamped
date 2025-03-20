package xzr.hkf;

import android.app.Activity;
import android.app.AlertDialog;
import android.view.Gravity;
import android.view.View;
import android.view.animation.AccelerateInterpolator;
import android.view.animation.DecelerateInterpolator;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.coordinatorlayout.widget.CoordinatorLayout;
import androidx.documentfile.provider.DocumentFile;

import com.google.android.material.card.MaterialCardView;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;

import xzr.hkf.utils.AssetsUtil;

public class Worker extends MainActivity.fileWorker {
    Activity activity;
    String file_path;
    String binary_path;
    boolean is_error;

    public Worker(Activity activity) {
        this.activity = activity;
    }

    private androidx.appcompat.app.AlertDialog progressDialog;
    private TextView progressText;
    
    public void run() {
        MainActivity.cur_status = MainActivity.status.flashing;
        ((MainActivity) activity).update_title();
        is_error = false;
        file_path = activity.getFilesDir().getAbsolutePath() + "/" + DocumentFile.fromSingleUri(activity, uri).getName();
        binary_path = activity.getFilesDir().getAbsolutePath() + "/META-INF/com/google/android/update-binary";
        
        // Show flashing progress dialog
        activity.runOnUiThread(() -> {
            View dialogView = activity.getLayoutInflater().inflate(R.layout.dialog_flashing_progress, null);
            progressText = dialogView.findViewById(R.id.progress_text);
            progressDialog = new com.google.android.material.dialog.MaterialAlertDialogBuilder(activity)
                    .setView(dialogView)
                    .setCancelable(false)
                    .create();
            progressDialog.show();
            updateProgress("Preparing environment...");
        });

        try {
            cleanup();
        } catch (IOException ioException) {
            is_error = true;
        }
        if (is_error) {
            MainActivity._appendLog(activity.getResources().getString(R.string.unable_cleanup), activity);
            MainActivity.cur_status = MainActivity.status.error;
            ((MainActivity) activity).update_title();
            dismissProgressDialog();
            return;
        }
        updateProgress("Checking root access...");

        if (!rootAvailable()) {
            MainActivity._appendLog(activity.getResources().getString(R.string.unable_flash_root), activity);
            MainActivity.cur_status = MainActivity.status.error;
            ((MainActivity) activity).update_title();
            dismissProgressDialog();
            showStatusNotification(R.string.unable_flash_root, false);
            return;
        }

        updateProgress("Copying files...");
        try {
            copy();
        } catch (IOException ioException) {
            is_error = true;
        }
        if (!is_error)
            is_error = !new File(file_path).exists();
        if (is_error) {
            MainActivity._appendLog(activity.getResources().getString(R.string.unable_copy), activity);
            MainActivity.cur_status = MainActivity.status.error;
            ((MainActivity) activity).update_title();
            dismissProgressDialog();
            showStatusNotification(R.string.unable_copy, false);
            return;
        }

        updateProgress("Extracting binary...");
        try {
            getBinary();
        } catch (IOException ioException) {
            is_error = true;
        }
        if (is_error) {
            MainActivity._appendLog(activity.getResources().getString(R.string.unable_get_exe), activity);
            MainActivity.cur_status = MainActivity.status.error;
            ((MainActivity) activity).update_title();
            dismissProgressDialog();
            showStatusNotification(R.string.unable_get_exe, false);
            return;
        }

        updateProgress("Patching binary...");
        try {
            patch();
        } catch (IOException ignored) {
        }

        updateProgress("Flashing kernel...");
        showStatusNotification(R.string.flashing, true);
        try {
            flash(activity);
        } catch (IOException ioException) {
            is_error = true;
        }
        if (is_error) {
            MainActivity._appendLog(activity.getResources().getString(R.string.unable_flash_error), activity);
            MainActivity.cur_status = MainActivity.status.error;
            ((MainActivity) activity).update_title();
            dismissProgressDialog();
            showStatusNotification(R.string.unable_flash_error, false);
            return;
        }
        dismissProgressDialog();
        showStatusNotification(R.string.flashing_done, true);
        activity.runOnUiThread(() -> {
            new AlertDialog.Builder(activity)
                    .setTitle(R.string.reboot_complete_title)
                    .setMessage(R.string.reboot_complete_msg)
                    .setCancelable(false)
                    .setPositiveButton(R.string.yes, (dialog, which) -> {
                        try {
                            reboot();
                        } catch (IOException e) {
                            Toast.makeText(activity, R.string.failed_reboot, Toast.LENGTH_SHORT).show();
                        }
                    })
                    .setNegativeButton(R.string.no, null)
                    .create().show();
        });
        MainActivity.cur_status = MainActivity.status.flashing_done;
        ((MainActivity) activity).update_title();
    }

    boolean rootAvailable() {
        try {
            String ret = runWithNewProcessReturn(true, "id");
            return ret != null && ret.contains("root");
        } catch (IOException e) {
            return false;
        }
    }

    void copy() throws IOException {
        InputStream inputStream = activity.getContentResolver().openInputStream(uri);
        FileOutputStream fileOutputStream = new FileOutputStream(new File(file_path));
        byte[] buffer = new byte[1024];
        int count = 0;
        while ((count = inputStream.read(buffer)) != -1) {
            fileOutputStream.write(buffer, 0, count);
        }
        fileOutputStream.flush();
        inputStream.close();
        fileOutputStream.close();
    }

    void getBinary() throws IOException {
        runWithNewProcessNoReturn(false, "unzip \"" + file_path + "\" \"*/update-binary\" -d " + activity.getFilesDir().getAbsolutePath());
        if (!new File(binary_path).exists())
            throw new IOException();
    }

    void patch() throws IOException {
        final String mkbootfs_path = activity.getFilesDir().getAbsolutePath() + "/mkbootfs";
        AssetsUtil.exportFiles(activity, "mkbootfs", mkbootfs_path);

        runWithNewProcessNoReturn(false, "sed -i '/$BB chmod -R 755 tools bin;/i cp -f " + mkbootfs_path + " $AKHOME/tools;' " + binary_path);
    }

    void flash(Activity activity) throws IOException {
        Process process = new ProcessBuilder("su").redirectErrorStream(true).start();
        OutputStreamWriter outputStreamWriter = new OutputStreamWriter(process.getOutputStream());
        BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(process.getInputStream()));
        outputStreamWriter.write("export POSTINSTALL=" + activity.getFilesDir().getAbsolutePath() + "\n");
        outputStreamWriter.write("sh " + (MainActivity.DEBUG ? "-x " : "") + binary_path + " 3 1 \"" + file_path + "\"&& touch " + activity.getFilesDir().getAbsolutePath() + "/done\nexit\n");
        outputStreamWriter.flush();
        String line;
        while ((line = bufferedReader.readLine()) != null)
            MainActivity.appendLog(line, activity);

        bufferedReader.close();
        outputStreamWriter.close();
        process.destroy();

        if (!new File(activity.getFilesDir().getAbsolutePath() + "/done").exists())
            throw new IOException();
    }

    void cleanup() throws IOException {
        runWithNewProcessNoReturn(false, "rm -rf " + activity.getFilesDir().getAbsolutePath() + "/*");
    }

    void reboot() throws IOException {
        runWithNewProcessNoReturn(true, "svc power reboot");
    }

    void runWithNewProcessNoReturn(boolean su, String cmd) throws IOException {
        runWithNewProcessReturn(su, cmd);
    }

    // Helper methods for UI updates
    private void updateProgress(String message) {
        activity.runOnUiThread(() -> {
            if (progressText != null) {
                progressText.setText(message);
            }
        });
    }
    
    private void dismissProgressDialog() {
        activity.runOnUiThread(() -> {
            if (progressDialog != null && progressDialog.isShowing()) {
                progressDialog.dismiss();
            }
        });
    }
    
    private void showStatusNotification(int stringResId, boolean isSuccess) {
        activity.runOnUiThread(() -> {
            View rootView = activity.findViewById(android.R.id.content);
            View notificationView = activity.getLayoutInflater().inflate(R.layout.layout_status_notification, null);
            
            MaterialCardView cardView = notificationView.findViewById(R.id.status_notification_card);
            ImageView statusIcon = notificationView.findViewById(R.id.status_icon);
            TextView statusMessage = notificationView.findViewById(R.id.status_message);
            ImageButton closeButton = notificationView.findViewById(R.id.close_notification);
            
            statusMessage.setText(stringResId);
            statusIcon.setImageResource(isSuccess ? android.R.drawable.ic_dialog_info : android.R.drawable.ic_dialog_alert);
            
            // Set card color based on status
            int colorResId = isSuccess ? R.color.md_theme_light_primaryContainer : R.color.md_theme_light_errorContainer;
            cardView.setCardBackgroundColor(activity.getResources().getColor(colorResId));
            
            // Add to the root layout
            CoordinatorLayout.LayoutParams params = new CoordinatorLayout.LayoutParams(
                    CoordinatorLayout.LayoutParams.MATCH_PARENT,
                    CoordinatorLayout.LayoutParams.WRAP_CONTENT);
            params.gravity = Gravity.TOP;
            
            CoordinatorLayout rootLayout = (CoordinatorLayout) rootView;
            rootLayout.addView(notificationView, params);
            
            // Animate in
            cardView.setVisibility(View.VISIBLE);
            cardView.setAlpha(0f);
            cardView.setTranslationY(-100f);
            cardView.animate()
                    .alpha(1f)
                    .translationY(0f)
                    .setDuration(300)
                    .setInterpolator(new DecelerateInterpolator())
                    .start();
            
            // Set up auto-dismiss after 3 seconds
            cardView.postDelayed(() -> {
                cardView.animate()
                        .alpha(0f)
                        .translationY(-100f)
                        .setDuration(300)
                        .setInterpolator(new AccelerateInterpolator())
                        .withEndAction(() -> rootLayout.removeView(notificationView))
                        .start();
            }, 3000);
            
            // Set up close button
            closeButton.setOnClickListener(v -> {
                cardView.animate()
                        .alpha(0f)
                        .translationY(-100f)
                        .setDuration(300)
                        .setInterpolator(new AccelerateInterpolator())
                        .withEndAction(() -> rootLayout.removeView(notificationView))
                        .start();
            });
        });
    }
    
    String runWithNewProcessReturn(boolean su, String cmd) throws IOException {
        Process process = null;
        if (su) {
            process = new ProcessBuilder("su").redirectErrorStream(true).start();
        } else {
            process = new ProcessBuilder("sh").redirectErrorStream(true).start();
        }
        OutputStreamWriter outputStreamWriter = new OutputStreamWriter((process.getOutputStream()));
        BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(process.getInputStream()));
        outputStreamWriter.write(cmd + "\n");
        outputStreamWriter.write("exit\n");
        outputStreamWriter.flush();
        String tmp;
        StringBuilder ret = new StringBuilder();
        while ((tmp = bufferedReader.readLine()) != null) {
            ret.append(tmp);
            ret.append("\n");
        }
        outputStreamWriter.close();
        bufferedReader.close();
        process.destroy();
        return ret.toString();
    }

}
