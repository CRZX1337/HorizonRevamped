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
import android.widget.EditText;
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
import com.google.android.material.color.DynamicColors;

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

    NestedScrollView scrollView;
    private MaterialCardView flashKernelCard;
    private MaterialCardView backupCard;
    private MaterialCardView restoreCard;
    private MaterialCardView backupsCard;
    private MaterialCardView settingsCard;
    private MaterialCardView aboutCard;

    enum status {
        flashing,
        flashing_done,
        error,
        normal
    }

    static status cur_status;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        try {
            // Apply splash screen
            SplashScreen splashScreen = SplashScreen.installSplashScreen(this);
            
            // Apply Material You dynamic colors
            DynamicColors.applyToActivityIfAvailable(this);
            
            // Set up transitions
            getWindow().setEnterTransition(new MaterialSharedAxis(MaterialSharedAxis.Z, true));
            getWindow().setExitTransition(new MaterialSharedAxis(MaterialSharedAxis.Z, false));
            
            super.onCreate(savedInstanceState);
            setContentView(R.layout.activity_main);
            
            // Initialize views with null checks
            scrollView = findViewById(R.id.scrollView);
            
            if (scrollView == null) {
                // Handle missing ScrollView gracefully
                setContentView(R.layout.activity_main);
                scrollView = findViewById(R.id.scrollView);
                if (scrollView == null) {
                    Toast.makeText(this, "Error initializing UI", Toast.LENGTH_LONG).show();
                    finish();
                    return;
                }
            }
            
            flashKernelCard = findViewById(R.id.flash_kernel_card);
            backupCard = findViewById(R.id.backup_card);
            restoreCard = findViewById(R.id.restore_card);
            backupsCard = findViewById(R.id.backups_card);
            settingsCard = findViewById(R.id.settings_card);
            aboutCard = findViewById(R.id.about_card);
            
            // Set up toolbar
            setSupportActionBar(findViewById(R.id.toolbar));
            if (getSupportActionBar() != null) {
                getSupportActionBar().setTitle(R.string.app_name);
            }
            
            // Initialize status
            cur_status = status.normal;
            
            // Set up click listeners for the buttons
            setupButtonClickListeners();
            
            // Animate the cards entry
            animateCardsEntry();
        } catch (Exception e) {
            // Handle any exception during creation
            Log.e("HorizonRevamped", "Error during app initialization: " + e.getMessage(), e);
            Toast.makeText(this, "Error initializing app: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }
    
    private void setupButtonClickListeners() {
        flashKernelCard.setOnClickListener(v -> {
            animateCardClick(v);
            if (cur_status == status.flashing) {
                Snackbar.make(findViewById(R.id.scrollView), 
                    R.string.task_running, Snackbar.LENGTH_SHORT).show();
                return;
            }
            
            // Create a new Worker instance for flashing
            Worker worker = new Worker(this);
            file_worker = worker;
            
            // Flash new kernel functionality
            Intent chooserIntent = new Intent(Intent.ACTION_GET_CONTENT);
            chooserIntent.setType("application/zip");
            chooserIntent.addCategory(Intent.CATEGORY_OPENABLE);
            startActivityForResult(Intent.createChooser(chooserIntent, "Select File"), 42);
        });
        
        backupCard.setOnClickListener(v -> {
            animateCardClick(v);
            // Backup functionality
            createManualBackup();
        });
        
        restoreCard.setOnClickListener(v -> {
            animateCardClick(v);
            // Restore functionality
            showRestoreSourceDialog();
        });
        
        backupsCard.setOnClickListener(v -> {
            animateCardClick(v);
            // Manage backups functionality
            showManageBackupsDialog();
        });
        
        settingsCard.setOnClickListener(v -> {
            animateCardClick(v);
            // Settings functionality
            Toast.makeText(this, "Settings", Toast.LENGTH_SHORT).show();
        });
        
        aboutCard.setOnClickListener(v -> {
            animateCardClick(v);
            // About functionality
            showAboutDialog();
        });
    }
    
    private void animateCardClick(View v) {
        // Create a more pronounced 3D animation for card clicks
        AnimatorSet animatorSet = new AnimatorSet();
        
        ObjectAnimator scaleDownX = ObjectAnimator.ofFloat(v, "scaleX", 0.95f);
        ObjectAnimator scaleDownY = ObjectAnimator.ofFloat(v, "scaleY", 0.95f);
        ObjectAnimator rotateX = ObjectAnimator.ofFloat(v, "rotationX", 10f);
        ObjectAnimator elevate = ObjectAnimator.ofFloat(v, "translationZ", 12f);
        
        animatorSet.playTogether(scaleDownX, scaleDownY, rotateX, elevate);
        animatorSet.setDuration(100);
        animatorSet.start();
        
        // Return to normal state after a short delay
        v.postDelayed(() -> {
            AnimatorSet returnAnimatorSet = new AnimatorSet();
            ObjectAnimator scaleUpX = ObjectAnimator.ofFloat(v, "scaleX", 1f);
            ObjectAnimator scaleUpY = ObjectAnimator.ofFloat(v, "scaleY", 1f);
            ObjectAnimator rotateBackX = ObjectAnimator.ofFloat(v, "rotationX", 0f);
            ObjectAnimator lowerElevation = ObjectAnimator.ofFloat(v, "translationZ", 4f);
            
            returnAnimatorSet.playTogether(scaleUpX, scaleUpY, rotateBackX, lowerElevation);
            returnAnimatorSet.setDuration(200);
            returnAnimatorSet.setInterpolator(new OvershootInterpolator());
            returnAnimatorSet.start();
        }, 100);
    }
    
    private void animateCardsEntry() {
        // Get all cards in the layout
        LinearLayout mainLayout = (LinearLayout) scrollView.getChildAt(0);
        int childCount = mainLayout.getChildCount();
        
        // First, set initial state for all cards
        for (int i = 0; i < childCount; i++) {
            View child = mainLayout.getChildAt(i);
            if (child instanceof MaterialCardView) {
                child.setAlpha(0f);
                child.setTranslationY(200f);
                child.setTranslationZ(0f);
                child.setRotationX(15f);
                child.setScaleX(0.8f);
                child.setScaleY(0.8f);
            }
        }
        
        // Animate each card with a cascade effect
        for (int i = 0; i < childCount; i++) {
            View child = mainLayout.getChildAt(i);
            if (child instanceof MaterialCardView) {
                long delay = i * 150L; // 150ms delay between each card
                
                AnimatorSet animatorSet = new AnimatorSet();
                
                ObjectAnimator fadeIn = ObjectAnimator.ofFloat(child, "alpha", 0f, 1f);
                ObjectAnimator translateY = ObjectAnimator.ofFloat(child, "translationY", 200f, 0f);
                ObjectAnimator rotateX = ObjectAnimator.ofFloat(child, "rotationX", 15f, 0f);
                ObjectAnimator scaleX = ObjectAnimator.ofFloat(child, "scaleX", 0.8f, 1f);
                ObjectAnimator scaleY = ObjectAnimator.ofFloat(child, "scaleY", 0.8f, 1f);
                ObjectAnimator elevate = ObjectAnimator.ofFloat(child, "translationZ", 0f, 4f);
                
                animatorSet.playTogether(fadeIn, translateY, rotateX, scaleX, scaleY, elevate);
                animatorSet.setStartDelay(delay);
                animatorSet.setDuration(400);
                animatorSet.setInterpolator(new DecelerateInterpolator(1.5f));
                animatorSet.start();
            }
        }
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

    @Override
    public void onBackPressed() {
        // Add exit animation with 3D effect
        LinearLayout mainLayout = (LinearLayout) scrollView.getChildAt(0);
        int childCount = mainLayout.getChildCount();
        
        for (int i = 0; i < childCount; i++) {
            View child = mainLayout.getChildAt(i);
            if (child instanceof MaterialCardView) {
                long delay = i * 50L;
                
                AnimatorSet animatorSet = new AnimatorSet();
                
                ObjectAnimator fadeOut = ObjectAnimator.ofFloat(child, "alpha", 1f, 0f);
                ObjectAnimator translateY = ObjectAnimator.ofFloat(child, "translationY", 0f, 100f);
                ObjectAnimator rotateX = ObjectAnimator.ofFloat(child, "rotationX", 0f, 20f);
                ObjectAnimator scaleX = ObjectAnimator.ofFloat(child, "scaleX", 1f, 0.8f);
                ObjectAnimator scaleY = ObjectAnimator.ofFloat(child, "scaleY", 1f, 0.8f);
                
                animatorSet.playTogether(fadeOut, translateY, rotateX, scaleX, scaleY);
                animatorSet.setStartDelay(delay);
                animatorSet.setDuration(300);
                animatorSet.setInterpolator(new AccelerateInterpolator(1.5f));
                animatorSet.start();
            }
        }
        
        // Delay the actual back action to allow animations to play
        new android.os.Handler().postDelayed(() -> super.onBackPressed(), 400);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Don't inflate the menu to remove the 3-dot menu
        return false;
    }

    public static void _appendLog(String log, Activity activity) {
        activity.runOnUiThread(() -> {
            if (activity instanceof MainActivity) {
                // In our new UI, we don't have a log view, so just show a toast with the log message
                Toast.makeText(activity, log, Toast.LENGTH_SHORT).show();
            }
        });
    }

    public static void appendLog(String log, Activity activity) {
        if (DEBUG)
            Log.d("HKF", log);
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
        try {
            super.onActivityResult(requestCode, resultCode, data);
            if (resultCode == Activity.RESULT_OK && data != null) {
                // Make sure file_worker is initialized to avoid NullPointerException
                if (file_worker == null) {
                    Toast.makeText(this, "Error: operation not initialized properly", Toast.LENGTH_SHORT).show();
                    return;
                }
                
                Uri dataUri = data.getData();
                if (dataUri == null) {
                    Toast.makeText(this, "Error: no file selected", Toast.LENGTH_SHORT).show();
                    return;
                }
                
                file_worker.uri = dataUri;
                
                // Check if file is a ZIP file first
                if (isValidZipFile(dataUri)) {
                    try {
                        file_worker.start();
                    } catch (Exception e) {
                        Log.e("HorizonRevamped", "Error starting worker thread: " + e.getMessage(), e);
                        Toast.makeText(this, "Error starting process: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                    }
                    file_worker = null;
                } else {
                    // Show error message if not a valid file
                    Snackbar.make(findViewById(R.id.scrollView), R.string.invalid_zip_file, Snackbar.LENGTH_LONG)
                            .setAnimationMode(Snackbar.ANIMATION_MODE_SLIDE)
                            .show();
                }
            }
        } catch (Exception e) {
            Log.e("HorizonRevamped", "Error in activity result: " + e.getMessage(), e);
            Toast.makeText(this, "Error processing selected file: " + e.getMessage(), Toast.LENGTH_SHORT).show();
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
            Snackbar.make(findViewById(R.id.scrollView), 
                R.string.unable_process_ongoing, Snackbar.LENGTH_LONG).show();
            return;
        }
        
        // Check storage permission first
        if (!checkStoragePermission()) {
            Snackbar.make(findViewById(R.id.scrollView), R.string.storage_permission_required, Snackbar.LENGTH_LONG)
                    .setAction(R.string.grant, v -> {
                        requestStoragePermission();
                    })
                    .show();
            return;
        }
        
        // Clear logs and show progress message
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
                                
                            Snackbar.make(findViewById(R.id.scrollView), 
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
                Snackbar.make(findViewById(R.id.scrollView), 
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
                
                requestStoragePermission();
                return false;
            }
            return true;
        }
    }
    
    // Request storage permission for Android 10 and below
    private void requestStoragePermission() {
        androidx.core.app.ActivityCompat.requestPermissions(this,
            new String[]{android.Manifest.permission.WRITE_EXTERNAL_STORAGE},
            1001);
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
            Snackbar.make(findViewById(R.id.scrollView), 
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
            Snackbar.make(findViewById(R.id.scrollView), 
                R.string.unable_process_ongoing, Snackbar.LENGTH_LONG).show();
            return;
        }
        
        // Clear logs and show progress message
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
                        
                    Snackbar.make(findViewById(R.id.scrollView), 
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
            Snackbar.make(findViewById(R.id.scrollView), 
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
                        Snackbar.make(findViewById(R.id.scrollView),
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
                    Snackbar.make(findViewById(R.id.scrollView),
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
                        Snackbar.make(findViewById(R.id.scrollView),
                            "Error loading backup details: " + e.getMessage(), Snackbar.LENGTH_LONG).show();
                    });
                }
            }).start();
            
        } catch (Exception e) {
            e.printStackTrace();
            Snackbar.make(findViewById(R.id.scrollView),
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
                    Snackbar.make(findViewById(R.id.scrollView),
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
                                Snackbar.make(findViewById(R.id.scrollView),
                                    R.string.backup_deleted, Snackbar.LENGTH_LONG).show();
                            } else {
                                Snackbar.make(findViewById(R.id.scrollView),
                                    R.string.backup_delete_failed, Snackbar.LENGTH_LONG).show();
                            }
                        });
                    }).start();
                } catch (Exception e) {
                    e.printStackTrace();
                    Snackbar.make(findViewById(R.id.scrollView),
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