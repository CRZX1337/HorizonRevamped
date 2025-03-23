package xzr.hkf;

import androidx.appcompat.app.AlertDialog;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.AccelerateInterpolator;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.OvershootInterpolator;
import android.widget.Button;
import androidx.appcompat.widget.AppCompatButton;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.coordinatorlayout.widget.CoordinatorLayout;
import androidx.core.splashscreen.SplashScreen;
import androidx.core.widget.NestedScrollView;

import com.google.android.material.card.MaterialCardView;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton;
import com.google.android.material.progressindicator.LinearProgressIndicator;
import com.google.android.material.snackbar.Snackbar;
import com.google.android.material.transition.platform.MaterialSharedAxis;

import android.view.Gravity;

import android.app.Activity;
import android.app.ProgressDialog;

import androidx.documentfile.provider.DocumentFile;

import java.io.IOException;
import java.io.InputStream;
import java.io.File;

import android.widget.CheckBox;
import android.widget.RadioGroup;
import android.widget.LinearLayout;

import android.content.DialogInterface;

import android.widget.ArrayAdapter;
import android.widget.ListView;

import java.util.List;
import java.util.Map;
import java.util.ArrayList;
import java.util.HashMap;
import java.io.BufferedReader;
import java.io.FileReader;
import android.util.Log;

public class MainActivity extends AppCompatActivity {
    static final boolean DEBUG = false;

    TextView logView;
    NestedScrollView scrollView;

    enum status {
        flashing,
        flashing_done,
        error,
        normal
    }

    static status cur_status;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        // Apply splash screen
        SplashScreen splashScreen = SplashScreen.installSplashScreen(this);
        
        // Set up transitions
        getWindow().setEnterTransition(new MaterialSharedAxis(MaterialSharedAxis.Z, true));
        getWindow().setExitTransition(new MaterialSharedAxis(MaterialSharedAxis.Z, false));
        
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        
        // Initialize views
        scrollView = findViewById(R.id.scrollView);
        logView = findViewById(R.id.logView);
        
        // Set up toolbar
        setSupportActionBar(findViewById(R.id.toolbar));
        
        // Set up FAB with animation
        ExtendedFloatingActionButton fabFlash = findViewById(R.id.fab_flash);
        fabFlash.setVisibility(View.INVISIBLE); // Hide initially for animation
        fabFlash.postDelayed(() -> {
            fabFlash.setVisibility(View.VISIBLE);
            fabFlash.startAnimation(android.view.animation.AnimationUtils.loadAnimation(this, R.anim.fab_scale_up));
        }, 500); // Delay for a smoother entry
        
        fabFlash.setOnClickListener(v -> {
            // Add click animation
            ObjectAnimator scaleDownX = ObjectAnimator.ofFloat(v, "scaleX", 0.9f);
            ObjectAnimator scaleDownY = ObjectAnimator.ofFloat(v, "scaleY", 0.9f);
            scaleDownX.setDuration(100);
            scaleDownY.setDuration(100);
            AnimatorSet scaleDown = new AnimatorSet();
            scaleDown.play(scaleDownX).with(scaleDownY);
            
            ObjectAnimator scaleUpX = ObjectAnimator.ofFloat(v, "scaleX", 1.0f);
            ObjectAnimator scaleUpY = ObjectAnimator.ofFloat(v, "scaleY", 1.0f);
            scaleUpX.setDuration(100);
            scaleUpY.setDuration(100);
            AnimatorSet scaleUp = new AnimatorSet();
            scaleUp.play(scaleUpX).with(scaleUpY);
            
            scaleDown.start();
            scaleDown.addListener(new android.animation.AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(android.animation.Animator animation) {
                    scaleUp.start();
                    flash_new();
                }
            });
        });
        
        // Initialize status
        cur_status = status.normal;
        update_title();
        
        // Apply card animation
        MaterialCardView logCard = findViewById(R.id.log_card);
        logCard.setAlpha(0f);
        logCard.setTranslationY(100f);
        logCard.animate()
                .alpha(1f)
                .translationY(0f)
                .setDuration(500)
                .setInterpolator(new DecelerateInterpolator())
                .start();
    }

    void update_title() {
        runOnUiThread(() -> {
            switch (cur_status) {
                case error:
                    if (getSupportActionBar() != null) {
                        getSupportActionBar().setTitle(R.string.failed);
                    }
                    break;
                case flashing:
                    if (getSupportActionBar() != null) {
                        getSupportActionBar().setTitle(R.string.flashing);
                    }
                    break;
                case flashing_done:
                    if (getSupportActionBar() != null) {
                        getSupportActionBar().setTitle(R.string.flashing_done);
                    }
                    break;
                default:
                    if (getSupportActionBar() != null) {
                        getSupportActionBar().setTitle(R.string.app_name);
                    }
            }
        });
    }

    void flash_new() {
        if (cur_status == MainActivity.status.flashing) {
            // Show animated toast instead of regular Toast
            Snackbar.make(findViewById(R.id.fab_flash), R.string.task_running, Snackbar.LENGTH_SHORT)
                    .setAnimationMode(Snackbar.ANIMATION_MODE_SLIDE)
                    .show();
            return;
        }

        // Animate log clearing
        if (logView.getText().length() > 0) {
            logView.animate()
                    .alpha(0f)
                    .setDuration(200)
                    .withEndAction(() -> {
                        logView.setText("");
                        logView.animate().alpha(1f).setDuration(200).start();
                    })
                    .start();
        } else {
            logView.setText("");
        }
        
        cur_status = status.normal;
        update_title();
        
        // Show animated message
        Snackbar.make(findViewById(R.id.fab_flash), R.string.please_select_kzip, Snackbar.LENGTH_LONG)
                .setAnimationMode(Snackbar.ANIMATION_MODE_SLIDE)
                .show();
                
        runWithFilePath(this, new Worker(this));
    }

    @Override
    public void onBackPressed() {
        if (cur_status != status.flashing) {
            // Add exit animation
            findViewById(R.id.log_card).startAnimation(
                    android.view.animation.AnimationUtils.loadAnimation(this, R.anim.fade_out));
            findViewById(R.id.fab_flash).startAnimation(
                    android.view.animation.AnimationUtils.loadAnimation(this, R.anim.fab_scale_down));
            
            // Delay the actual back action to allow animations to play
            new android.os.Handler().postDelayed(() -> super.onBackPressed(), 200);
        } else {
            // Show message that flashing is in progress
            Snackbar.make(findViewById(R.id.fab_flash), R.string.task_running, Snackbar.LENGTH_SHORT)
                    .setAnimationMode(Snackbar.ANIMATION_MODE_SLIDE)
                    .show();
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == R.id.flash_new) {
            Intent chooserIntent = new Intent(Intent.ACTION_GET_CONTENT);
            chooserIntent.setType("application/zip");
            chooserIntent.addCategory(Intent.CATEGORY_OPENABLE);
            startActivityForResult(Intent.createChooser(chooserIntent, "Select File"), 42);
        } else if (item.getItemId() == R.id.create_backup) {
            createManualBackup();
        } else if (item.getItemId() == R.id.restore_backup) {
            showRestoreSourceDialog();
        } else if (item.getItemId() == R.id.manage_backups) {
            showManageBackupsDialog();
        } else if (item.getItemId() == R.id.about) {
            showAboutDialog();
        }
        return true;
    }

    public static void _appendLog(String log, Activity activity) {
        activity.runOnUiThread(() -> {
            TextView logView = ((MainActivity) activity).logView;
            NestedScrollView scrollView = ((MainActivity) activity).scrollView;
            
            // Animate new log entries
            int startPosition = logView.getText().length();
            logView.append(log + "\n");
            int endPosition = logView.getText().length();
            
            if (android.text.Spannable.class.isAssignableFrom(logView.getText().getClass())) {
                android.text.Spannable spannable = (android.text.Spannable) logView.getText();
                android.text.style.ForegroundColorSpan highlightSpan = new android.text.style.ForegroundColorSpan(
                        activity.getResources().getColor(R.color.md_theme_light_primary));
                spannable.setSpan(highlightSpan, startPosition, endPosition, android.text.Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
                
                // Remove highlight after a delay
                new android.os.Handler().postDelayed(() -> {
                    try {
                        spannable.removeSpan(highlightSpan);
                    } catch (Exception e) {
                        // Ignore if span was already removed
                    }
                }, 1500);
            }
            
            scrollView.fullScroll(View.FOCUS_DOWN);
        });
    }

    public static void appendLog(String log, Activity activity) {
        if (DEBUG) {
            _appendLog(log, activity);
            return;
        }
        if (!log.startsWith("ui_print"))
            return;
        log = log.replace("ui_print", "");
        _appendLog(log, activity);
    }

    static class fileWorker extends Thread {
        public Uri uri;
    }

    private static fileWorker file_worker;

    public static void runWithFilePath(Activity activity, fileWorker what) {
        MainActivity.file_worker = what;
        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.setType("*/*");
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        activity.startActivityForResult(intent, 0);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode == Activity.RESULT_OK) {
            file_worker.uri = data.getData();
            if (file_worker != null) {
                // Check if file is a ZIP file first
                if (isValidZipFile(data.getData())) {
                    file_worker.start();
                    file_worker = null;
                } else {
                    // Show error message if not a valid file
                    Snackbar.make(findViewById(R.id.fab_flash), R.string.invalid_zip_file, Snackbar.LENGTH_LONG)
                            .setAnimationMode(Snackbar.ANIMATION_MODE_SLIDE)
                            .show();
                }
            }
        }
    }
    
    // Method to validate that file is a ZIP and possibly an AnyKernel3 zip
    private boolean isValidZipFile(Uri uri) {
        try {
            // First check file extension
            String fileName = getFileNameFromUri(uri);
            if (fileName == null || !fileName.toLowerCase().endsWith(".zip")) {
                return false;
            }
            
            // Then check file signature (ZIP magic number)
            InputStream inputStream = getContentResolver().openInputStream(uri);
            if (inputStream == null) {
                return false;
            }
            
            byte[] buffer = new byte[4];
            int bytesRead = inputStream.read(buffer, 0, 4);
            inputStream.close();
            
            if (bytesRead != 4) {
                return false;
            }
            
            // Check ZIP file signature (PK..)
            return buffer[0] == 0x50 && buffer[1] == 0x4B && 
                   (buffer[2] == 0x03 || buffer[2] == 0x05 || buffer[2] == 0x07) && 
                   (buffer[3] == 0x04 || buffer[3] == 0x06 || buffer[3] == 0x08);
            
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }
    
    // Helper method to get file name from URI
    private String getFileNameFromUri(Uri uri) {
        try {
            DocumentFile documentFile = DocumentFile.fromSingleUri(this, uri);
            return documentFile != null ? documentFile.getName() : null;
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }
    
    // Handle manually creating a backup
    private void createManualBackup() {
        if (cur_status == status.flashing) {
            Snackbar.make(findViewById(R.id.fab_flash), 
                R.string.unable_process_ongoing, Snackbar.LENGTH_LONG).show();
            return;
        }
        
        // Check for storage permission first
        if (!checkStoragePermission()) {
            return;
        }
        
        // Clear logs and show progress message
        logView.setText("");
        _appendLog(getString(R.string.backup_progress), this);
        
        // Create and show partition selection dialog
        View dialogView = getLayoutInflater().inflate(R.layout.dialog_backup_selection, null);
        
        new AlertDialog.Builder(this)
            .setTitle(R.string.create_backup)
            .setView(dialogView)
            .setPositiveButton(android.R.string.ok, (dialog, which) -> {
                // Get selected partitions
                java.util.List<String> selectedPartitions = new java.util.ArrayList<>();
                if (((CheckBox) dialogView.findViewById(R.id.checkbox_boot)).isChecked())
                    selectedPartitions.add("boot");
                if (((CheckBox) dialogView.findViewById(R.id.checkbox_dtbo)).isChecked())
                    selectedPartitions.add("dtbo");
                if (((CheckBox) dialogView.findViewById(R.id.checkbox_init_boot)).isChecked())
                    selectedPartitions.add("init_boot");
                if (((CheckBox) dialogView.findViewById(R.id.checkbox_system_dlkm)).isChecked())
                    selectedPartitions.add("system_dlkm");
                if (((CheckBox) dialogView.findViewById(R.id.checkbox_vbmeta)).isChecked())
                    selectedPartitions.add("vbmeta");
                if (((CheckBox) dialogView.findViewById(R.id.checkbox_vendor_boot)).isChecked())
                    selectedPartitions.add("vendor_boot");
                if (((CheckBox) dialogView.findViewById(R.id.checkbox_vendor_dlkm)).isChecked())
                    selectedPartitions.add("vendor_dlkm");
                if (((CheckBox) dialogView.findViewById(R.id.checkbox_vendor_kernel_boot)).isChecked())
                    selectedPartitions.add("vendor_kernel_boot");
                
                // Check if at least one partition is selected
                if (selectedPartitions.isEmpty()) {
                    Toast.makeText(this, "Please select at least one partition", Toast.LENGTH_SHORT).show();
                    return;
                }
                
                // Always use external storage
                boolean useExternalStorage = true;
                
                // Show progress dialog
                ProgressDialog progressDialog = new ProgressDialog(this);
                progressDialog.setMessage(getString(R.string.backup_progress));
                progressDialog.setCancelable(false);
                progressDialog.show();
                
                // Create worker thread for backup
                new Thread(() -> {
                    boolean success = false;
                    try {
                        // Create Worker instance
                        Worker worker = new Worker(this);
                        
                        // Convert list to array
                        String[] partitionsArray = selectedPartitions.toArray(new String[0]);
                        
                        // Perform backup
                        success = worker.createMultiPartitionBackup(partitionsArray, useExternalStorage);
                        
                    } catch (Exception e) {
                        e.printStackTrace();
                        _appendLog("Error: " + e.getMessage(), this);
                    } finally {
                        // Update UI on main thread
                        boolean finalSuccess = success;
                        runOnUiThread(() -> {
                            if (progressDialog.isShowing()) {
                                progressDialog.dismiss();
                            }
                            
                            int messageResId = finalSuccess ? 
                                R.string.backup_complete : 
                                R.string.backup_partial_error;
                                
                            Snackbar.make(findViewById(R.id.fab_flash), 
                                messageResId, Snackbar.LENGTH_LONG).show();
                        });
                    }
                }).start();
            })
            .setNegativeButton(android.R.string.cancel, null)
            .create()
            .show();
    }
    
    // Check for storage permission
    private boolean checkStoragePermission() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
            // For Android 11+, check for managed storage permission
            if (!android.os.Environment.isExternalStorageManager()) {
                Snackbar.make(findViewById(R.id.fab_flash), 
                    R.string.storage_permission_required, Snackbar.LENGTH_LONG)
                    .setAction("Settings", v -> {
                        Intent intent = new Intent(android.provider.Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION);
                        Uri uri = Uri.fromParts("package", getPackageName(), null);
                        intent.setData(uri);
                        startActivity(intent);
                    })
                    .show();
                return false;
            }
            return true;
        } else {
            // For Android 10 and below
            if (androidx.core.content.ContextCompat.checkSelfPermission(this, 
                    android.Manifest.permission.WRITE_EXTERNAL_STORAGE) != 
                    android.content.pm.PackageManager.PERMISSION_GRANTED) {
                
                androidx.core.app.ActivityCompat.requestPermissions(this,
                    new String[]{android.Manifest.permission.WRITE_EXTERNAL_STORAGE},
                    1001);
                return false;
            }
            return true;
        }
    }
    
    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == 1001) {
            if (grantResults.length > 0 && grantResults[0] == android.content.pm.PackageManager.PERMISSION_GRANTED) {
                // Permission granted, recreate the backup dialog
                createManualBackup();
            } else {
                Toast.makeText(this, R.string.storage_permission_required, Toast.LENGTH_LONG).show();
            }
        }
    }

    // Show dialog to select restore source
    private void showRestoreSourceDialog() {
        if (cur_status == status.flashing) {
            Snackbar.make(findViewById(R.id.fab_flash), 
                R.string.unable_process_ongoing, Snackbar.LENGTH_LONG).show();
            return;
        }

        // Check for storage permission first
        if (!checkStoragePermission()) {
            return;
        }

        // Directly show external backups without dialog
        showBackupFolderSelection(true);
    }

    // Show dialog to select which backup folder to restore from
    private void showBackupFolderSelection(boolean external) {
        // Show progress dialog while loading folders
        ProgressDialog progressDialog = new ProgressDialog(this);
        progressDialog.setMessage(getString(R.string.select_backup_folder));
        progressDialog.setCancelable(false);
        progressDialog.show();

        new Thread(() -> {
            final java.util.List<File> backupFolders = new java.util.ArrayList<>();
            final java.util.List<String> folderNames = new java.util.ArrayList<>();
            
            try {
                File baseDir;
                if (external) {
                    baseDir = new File(android.os.Environment.getExternalStorageDirectory(), "HorizonRevamped/backups");
                } else {
                    baseDir = new File(getFilesDir(), "backups");
                }
                
                if (baseDir.exists() && baseDir.isDirectory()) {
                    File[] folders = baseDir.listFiles(File::isDirectory);
                    if (folders != null) {
                        for (File folder : folders) {
                            // Check if folder contains any .img files
                            File[] imgFiles = folder.listFiles((dir, name) -> name.endsWith(".img"));
                            if (imgFiles != null && imgFiles.length > 0) {
                                backupFolders.add(folder);
                                
                                // Format folder name (timestamp) for display
                                String folderName = folder.getName();
                                // Try to format as date if it's a timestamp pattern
                                if (folderName.matches("\\d{4}-\\d{2}-\\d{2}_\\d{2}-\\d{2}-\\d{2}")) {
                                    try {
                                        java.text.SimpleDateFormat inputFormat = new java.text.SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", java.util.Locale.US);
                                        java.text.SimpleDateFormat outputFormat = new java.text.SimpleDateFormat("MMM dd, yyyy HH:mm", java.util.Locale.US);
                                        java.util.Date date = inputFormat.parse(folderName);
                                        if (date != null) {
                                            folderName = outputFormat.format(date);
                                        }
                                    } catch (Exception e) {
                                        // Use original folder name if parsing fails
                                    }
                                }
                                folderNames.add(folderName);
                            }
                        }
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
            
            // Update UI on main thread
            runOnUiThread(() -> {
                progressDialog.dismiss();
                
                if (backupFolders.isEmpty()) {
                    Toast.makeText(this, R.string.no_backups, Toast.LENGTH_SHORT).show();
                    return;
                }
                
                // Show folder selection dialog
                AlertDialog.Builder builder = new AlertDialog.Builder(this);
                builder.setTitle(R.string.select_backup_folder)
                    .setItems(folderNames.toArray(new CharSequence[0]), (dialog, which) -> {
                        showRestorePartitionSelection(backupFolders.get(which));
                    })
                    .setNegativeButton(android.R.string.cancel, null)
                    .show();
            });
        }).start();
    }

    // Show dialog to select which partitions to restore
    private void showRestorePartitionSelection(File backupFolder) {
        // Show progress dialog while loading partition info
        ProgressDialog progressDialog = new ProgressDialog(this);
        progressDialog.setMessage(getString(R.string.select_backup_folder));
        progressDialog.setCancelable(false);
        progressDialog.show();
        
        new Thread(() -> {
            // Create Worker to access backup methods
            Worker worker = new Worker(this);
            
            // Get available partition backups in the folder
            final java.util.List<String> availablePartitions = worker.getAvailablePartitionBackups(backupFolder);
            
            // Update UI on main thread
            runOnUiThread(() -> {
                progressDialog.dismiss();
                
                if (availablePartitions.isEmpty()) {
                    Toast.makeText(this, R.string.no_partition_backups, Toast.LENGTH_SHORT).show();
                    return;
                }
                
                // Inflate dialog layout
                View dialogView = getLayoutInflater().inflate(R.layout.dialog_restore_selection, null);
                LinearLayout checkboxContainer = dialogView.findViewById(R.id.checkbox_container);
                
                // Add a checkbox for each available partition
                boolean[] selected = new boolean[availablePartitions.size()];
                for (int i = 0; i < availablePartitions.size(); i++) {
                    String partitionName = availablePartitions.get(i);
                    
                    CheckBox checkBox = new CheckBox(this);
                    checkBox.setText(partitionName);
                    checkBox.setChecked(true); // Select all by default
                    selected[i] = true;
                    
                    final int position = i;
                    checkBox.setOnCheckedChangeListener((buttonView, isChecked) -> {
                        selected[position] = isChecked;
                    });
                    
                    checkboxContainer.addView(checkBox);
                }
                
                // Create and show dialog
                AlertDialog dialog = new AlertDialog.Builder(this)
                    .setTitle(R.string.restore_backup)
                    .setView(dialogView)
                    .setPositiveButton(android.R.string.ok, (dialogInterface, which) -> {
                        // Get selected partitions
                        java.util.List<String> selectedPartitions = new java.util.ArrayList<>();
                        for (int i = 0; i < availablePartitions.size(); i++) {
                            if (selected[i]) {
                                selectedPartitions.add(availablePartitions.get(i));
                            }
                        }
                        
                        if (selectedPartitions.isEmpty()) {
                            Toast.makeText(this, R.string.no_partitions_selected, Toast.LENGTH_SHORT).show();
                            return;
                        }
                        
                        // Perform restore
                        performRestore(backupFolder, selectedPartitions.toArray(new String[0]));
                    })
                    .setNegativeButton(android.R.string.cancel, null)
                    .create();
                    
                dialog.show();
            });
        }).start();
    }

    // Perform the actual restore process
    private void performRestore(File backupFolder, String[] selectedPartitions) {
        if (cur_status == status.flashing) {
            Snackbar.make(findViewById(R.id.fab_flash), 
                R.string.unable_process_ongoing, Snackbar.LENGTH_LONG).show();
            return;
        }
        
        // Clear logs and show progress message
        logView.setText("");
        _appendLog(getString(R.string.restoring), this);
        
        // Show progress dialog
        ProgressDialog progressDialog = new ProgressDialog(this);
        progressDialog.setMessage(getString(R.string.restoring));
        progressDialog.setCancelable(false);
        progressDialog.show();
        
        // Create worker thread for restore
        new Thread(() -> {
            boolean success = false;
            try {
                // Create Worker instance
                Worker worker = new Worker(this);
                
                // Perform restore
                success = worker.restoreMultiplePartitions(backupFolder, selectedPartitions);
                
            } catch (Exception e) {
                e.printStackTrace();
                _appendLog("Error: " + e.getMessage(), this);
            } finally {
                // Update UI on main thread
                boolean finalSuccess = success;
                runOnUiThread(() -> {
                    if (progressDialog.isShowing()) {
                        progressDialog.dismiss();
                    }
                    
                    int messageResId = finalSuccess ? 
                        R.string.restore_complete : 
                        R.string.restore_partial_error;
                        
                    Snackbar.make(findViewById(R.id.fab_flash), 
                        messageResId, Snackbar.LENGTH_LONG).show();
                });
            }
        }).start();
    }
    
    // Show dialog to reboot after restore
    private void showRebootDialog() {
        new AlertDialog.Builder(this)
            .setTitle(R.string.reboot_complete_title)
            .setMessage(R.string.reboot_complete_msg)
            .setPositiveButton(R.string.yes, (dialog, which) -> {
                try {
                    Worker worker = new Worker(this);
                    worker.reboot();
                } catch (Exception e) {
                    Toast.makeText(this, R.string.failed_reboot, Toast.LENGTH_SHORT).show();
                }
            })
            .setNegativeButton(R.string.no, null)
            .show();
    }

    // Show about dialog
    private void showAboutDialog() {
        View dialogView = getLayoutInflater().inflate(R.layout.dialog_about, null);
        androidx.appcompat.app.AlertDialog dialog = new MaterialAlertDialogBuilder(this)
                .setView(dialogView)
                .setPositiveButton(R.string.ok, null)
                .create();
        
        Button btnMainDev = dialogView.findViewById(R.id.btn_main_dev);
        Button btnTelegram = dialogView.findViewById(R.id.btn_telegram);
        
        // Add null checks to prevent crashes
        
        if (btnMainDev != null) {
            btnMainDev.setOnClickListener(v -> {
                try {
                    startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/CRZX1337")));
                } catch (Exception e) {
                    Toast.makeText(this, "Could not open link", Toast.LENGTH_SHORT).show();
                }
            });
        }
        
        if (btnTelegram != null) {
            btnTelegram.setOnClickListener(v -> {
                try {
                    startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("https://t.me/HorizonRevamped")));
                } catch (Exception e) {
                    Toast.makeText(this, "Could not open link", Toast.LENGTH_SHORT).show();
                }
            });
        }
        
        dialog.show();
    }

    // Show dialog to manage backups
    private void showManageBackupsDialog() {
        if (cur_status == status.flashing) {
            Snackbar.make(findViewById(R.id.fab_flash), 
                R.string.unable_process_ongoing, Snackbar.LENGTH_LONG).show();
            return;
        }

        // Check for storage permission first
        if (!checkStoragePermission()) {
            return;
        }

        // Show a loading dialog while we scan for backups
        ProgressDialog loadingDialog = new ProgressDialog(this);
        loadingDialog.setMessage(getString(R.string.scanning_backups));
        loadingDialog.setCancelable(false);
        loadingDialog.show();

        // Use a worker thread to avoid blocking the UI
        new Thread(() -> {
            try {
                // Get all available backups from external storage
                Worker worker = new Worker(this);
                java.util.List<File> backupFolders = worker.getAvailableBackupFolders();
                
                // Sort folders by timestamp (newest first)
                java.util.Collections.sort(backupFolders, (f1, f2) -> {
                    return f2.getName().compareTo(f1.getName());
                });
                
                // Create list items for the dialog
                final String[] items = new String[backupFolders.size()];
                final File[] folders = backupFolders.toArray(new File[0]);
                
                for (int i = 0; i < backupFolders.size(); i++) {
                    File folder = backupFolders.get(i);
                    String folderName = folder.getName();
                    
                    // Try to format the backup name nicely
                    if (folderName.matches("\\d{4}-\\d{2}-\\d{2}_\\d{2}-\\d{2}-\\d{2}")) {
                        // Parse timestamp format
                        String date = folderName.substring(0, 10).replace('_', ' ');
                        String time = folderName.substring(11).replace('-', ':');
                        items[i] = date + " " + time;
                    } else {
                        items[i] = folderName;
                    }
                    
                    // Add folder path for context
                    items[i] += " (" + (folder.getAbsolutePath().contains("sdcard") ? 
                        "External Storage" : "Internal Storage") + ")";
                    
                    // Count image files to show how many partitions are backed up
                    File[] imgFiles = folder.listFiles(file -> file.isFile() && file.getName().endsWith(".img"));
                    int partitionCount = imgFiles != null ? imgFiles.length : 0;
                    items[i] += " - " + partitionCount + " partition" + (partitionCount != 1 ? "s" : "");
                }
                
                // Update UI on main thread
                runOnUiThread(() -> {
                    loadingDialog.dismiss();
                    
                    if (items.length == 0) {
                        // No backups found
                        Snackbar.make(findViewById(R.id.fab_flash),
                            R.string.no_backups_found, Snackbar.LENGTH_LONG).show();
                        return;
                    }
                    
                    // Show dialog with backup list
                    AlertDialog.Builder builder = new AlertDialog.Builder(this);
                    builder.setTitle(R.string.manage_backups)
                        .setItems(items, (dialog, which) -> {
                            showBackupDetailsDialog(folders[which]);
                        })
                        .setNegativeButton(android.R.string.cancel, null)
                        .show();
                });
            } catch (Exception e) {
                e.printStackTrace();
                runOnUiThread(() -> {
                    loadingDialog.dismiss();
                    Snackbar.make(findViewById(R.id.fab_flash),
                        "Error scanning backups: " + e.getMessage(), Snackbar.LENGTH_LONG).show();
                });
            }
        }).start();
    }

    // Show details for a specific backup folder
    private void showBackupDetailsDialog(File backupFolder) {
        try {
            // Get worker instance
            Worker worker = new Worker(this);
            
            // Show a loading dialog while we scan partition details
            ProgressDialog loadingDialog = new ProgressDialog(this);
            loadingDialog.setMessage(getString(R.string.loading_backup_details));
            loadingDialog.setCancelable(false);
            loadingDialog.show();
            
            // Use a worker thread to avoid blocking the UI
            new Thread(() -> {
                try {
                    // Get available partitions in this backup
                    java.util.List<String> partitions = worker.getAvailablePartitionBackups(backupFolder);
                    
                    // Create array for display
                    final String[] partitionNames = partitions.toArray(new String[0]);
                    
                    // Read summary file if available
                    File summaryFile = new File(backupFolder, "backup_summary.txt");
                    StringBuilder summary = new StringBuilder();
                    if (summaryFile.exists()) {
                        try {
                            java.io.BufferedReader reader = new java.io.BufferedReader(
                                new java.io.InputStreamReader(new java.io.FileInputStream(summaryFile)));
                            String line;
                            while ((line = reader.readLine()) != null) {
                                summary.append(line).append("\n");
                            }
                            reader.close();
                        } catch (Exception e) {
                            summary.append("Error reading summary: ").append(e.getMessage());
                        }
                    } else {
                        summary.append("No summary file available");
                    }
                    
                    // Update UI on main thread
                    final String summaryText = summary.toString();
                    runOnUiThread(() -> {
                        loadingDialog.dismiss();
                        
                        // Create custom dialog view
                        View dialogView = getLayoutInflater().inflate(R.layout.dialog_backup_details, null);
                        TextView summaryView = dialogView.findViewById(R.id.backup_summary);
                        ListView partitionList = dialogView.findViewById(R.id.partition_list);
                        
                        // Set summary text
                        summaryView.setText(summaryText);
                        
                        // Create adapter for partition list
                        ArrayAdapter<String> adapter = new ArrayAdapter<>(this,
                            android.R.layout.simple_list_item_1, partitionNames);
                        partitionList.setAdapter(adapter);
                        
                        // Show dialog
                        AlertDialog dialog = new AlertDialog.Builder(this)
                            .setTitle(backupFolder.getName())
                            .setView(dialogView)
                            .setPositiveButton(R.string.restore_backup, (d, whichButton) -> {
                                // Show restore confirmation dialog
                                showRestoreConfirmationDialog(backupFolder, partitionNames);
                            })
                            .setNeutralButton(R.string.delete_backup, (d, whichButton) -> {
                                // Show delete confirmation dialog
                                showDeleteBackupConfirmationDialog(backupFolder);
                            })
                            .setNegativeButton(android.R.string.cancel, null)
                            .create();
                        dialog.show();
                    });
                } catch (Exception e) {
                    e.printStackTrace();
                    runOnUiThread(() -> {
                        loadingDialog.dismiss();
                        Snackbar.make(findViewById(R.id.fab_flash),
                            "Error loading backup details: " + e.getMessage(), Snackbar.LENGTH_LONG).show();
                    });
                }
            }).start();
            
        } catch (Exception e) {
            e.printStackTrace();
            Snackbar.make(findViewById(R.id.fab_flash),
                "Error: " + e.getMessage(), Snackbar.LENGTH_LONG).show();
        }
    }

    // Show confirmation dialog before restoring
    private void showRestoreConfirmationDialog(File backupFolder, String[] partitionNames) {
        // Create the selection dialog with checkboxes for each partition
        View dialogView = getLayoutInflater().inflate(R.layout.dialog_restore_selection, null);
        LinearLayout checkboxContainer = dialogView.findViewById(R.id.checkbox_container);
        
        // Add checkbox for each partition
        java.util.Map<String, CheckBox> checkboxMap = new java.util.HashMap<>();
        for (String partition : partitionNames) {
            CheckBox checkBox = new CheckBox(this);
            checkBox.setText(partition);
            checkBox.setChecked(true);
            checkboxContainer.addView(checkBox);
            checkboxMap.put(partition, checkBox);
        }
        
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(R.string.restore_confirmation)
            .setMessage(R.string.restore_confirmation_message)
            .setView(dialogView)
            .setPositiveButton(R.string.restore, (dialog, which) -> {
                // Get selected partitions
                java.util.List<String> selectedPartitions = new java.util.ArrayList<>();
                for (String partition : partitionNames) {
                    CheckBox checkBox = checkboxMap.get(partition);
                    if (checkBox != null && checkBox.isChecked()) {
                        selectedPartitions.add(partition);
                    }
                }
                
                if (selectedPartitions.isEmpty()) {
                    Snackbar.make(findViewById(R.id.fab_flash),
                        R.string.no_partitions_selected, Snackbar.LENGTH_LONG).show();
                    return;
                }
                
                // Execute the restore operation
                performRestore(backupFolder, selectedPartitions.toArray(new String[0]));
            })
            .setNegativeButton(android.R.string.cancel, null)
            .show();
    }

    // Show confirmation dialog before deleting backup
    private void showDeleteBackupConfirmationDialog(File backupFolder) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(R.string.delete_backup_confirmation_title)
            .setMessage(getString(R.string.delete_backup_confirmation_message, backupFolder.getName()))
            .setPositiveButton(R.string.delete, (dialog, which) -> {
                try {
                    // Show progress dialog
                    ProgressDialog progressDialog = new ProgressDialog(this);
                    progressDialog.setMessage(getString(R.string.deleting_backup));
                    progressDialog.setCancelable(false);
                    progressDialog.show();
                    
                    // Delete folder in worker thread
                    new Thread(() -> {
                        boolean success = deleteRecursive(backupFolder);
                        
                        runOnUiThread(() -> {
                            progressDialog.dismiss();
                            
                            if (success) {
                                Snackbar.make(findViewById(R.id.fab_flash),
                                    R.string.backup_deleted, Snackbar.LENGTH_LONG).show();
                            } else {
                                Snackbar.make(findViewById(R.id.fab_flash),
                                    R.string.backup_delete_failed, Snackbar.LENGTH_LONG).show();
                            }
                        });
                    }).start();
                } catch (Exception e) {
                    e.printStackTrace();
                    Snackbar.make(findViewById(R.id.fab_flash),
                        "Error: " + e.getMessage(), Snackbar.LENGTH_LONG).show();
                }
            })
            .setNegativeButton(android.R.string.cancel, null)
            .show();
    }

    // Helper method to recursively delete a directory
    private boolean deleteRecursive(File fileOrDirectory) {
        if (fileOrDirectory.isDirectory()) {
            File[] children = fileOrDirectory.listFiles();
            if (children != null) {
                for (File child : children) {
                    deleteRecursive(child);
                }
            }
        }
        return fileOrDirectory.delete();
    }
}