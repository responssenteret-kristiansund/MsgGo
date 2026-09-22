/*
 * Copyright (C) 2026 yztz
 *
 * This program is free software; you can redistribute it and/or modify it under
 * the terms of the GNU General Public License as published by the Free Software
 * Foundation; either version 3 of the License, or (at your option) any later
 * version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A
 * PARTICULAR PURPOSE. See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with
 * this program.  If not, see <http://www.gnu.org/licenses/>.
 *
 */
package top.yztz.msggo.activities;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.text.TextUtils;
import android.util.Log;
import android.view.MenuItem;
import android.content.IntentFilter;
import android.os.CountDownTimer;
import android.widget.TextView;
import top.yztz.msggo.services.SMSResponseReceiver;

import androidx.activity.EdgeToEdge;
import androidx.activity.OnBackPressedCallback;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.progressindicator.LinearProgressIndicator;

import java.util.List;
import java.util.Locale;

import top.yztz.msggo.R;
import top.yztz.msggo.adapters.SendingListAdapter;
import top.yztz.msggo.data.DataModel;
import top.yztz.msggo.data.Message;
import top.yztz.msggo.data.MessageState;
import top.yztz.msggo.data.SettingManager;
import top.yztz.msggo.data.Settings;
import top.yztz.msggo.services.MessageService;
import top.yztz.msggo.util.FileUtil;

public class SendingActivity extends AppCompatActivity implements MessageService.Callback {
    private static final String TAG = "SendingActivity";

    // UI
    private RecyclerView rvList;
    private SendingListAdapter adapter;
    private MaterialToolbar topAppBar;
    private TextView tvSubmittedCount, tvConfirmedCount, tvTimer;
    private LinearProgressIndicator progressSubmitted, progressConfirmed;

    // Data
    private List<Message> messages;
    private int subId;
    private int delay;
    private boolean randomize;
    private int listenTimeoutMinutes;

    // Service
    private MessageService service = null;
    private boolean isBound = false;

    // Response Listener
    private SMSResponseReceiver responseReceiver;
    private CountDownTimer countDownTimer;

    // Sending state
    private final Handler handler = new Handler(Looper.getMainLooper());
    private int currentIndex = 0;
    private int confirmedCount = 0;
    private boolean isPaused = false;
    private boolean isStopped = false;

    public enum SendingState {
        IDLE, SENDING, PAUSED, LISTENING, COMPLETED, CANCELLED
    }

    private SendingState currentState = SendingState.IDLE;

    private final ServiceConnection connection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName componentName, IBinder iBinder) {
            MessageService.LocalBinder binder = (MessageService.LocalBinder) iBinder;
            service = binder.getService();
            isBound = true;
            service.setCallback(SendingActivity.this);

            Log.d(TAG, "Service connected. Starting sending session.");
            service.initSession(messages.size());
            startSending();
        }

        @Override
        public void onServiceDisconnected(ComponentName componentName) {
            service.removeCallback();
            service = null;
            isBound = false;
        }
    };

    private static List<Message> activeMessages;
    private static int activeSubId;
    private static int activeDelay;
    private static boolean activeRandomize;
    private static int activeCurrentIndex;
    private static int activeConfirmedCount;

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        Log.d(TAG, "onNewIntent: Sending session already active");
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        EdgeToEdge.enable(this);
        super.onCreate(savedInstanceState);
        
        // Handle Activity re-entry or recreation
        if (activeMessages != null) {
            messages = activeMessages;
            subId = activeSubId;
            delay = activeDelay;
            randomize = activeRandomize;
            currentIndex = activeCurrentIndex;
            confirmedCount = activeConfirmedCount;
            
            setContentView(R.layout.activity_sending);
            initViews();
            setupList();
            updateUI();
            
            // Re-bind to service to get updates
            Intent intent = new Intent(this, MessageService.class);
            bindService(intent, connection, Context.BIND_AUTO_CREATE);
            return;
        }

        setContentView(R.layout.activity_sending);

        // Load messages from serialized file
        Intent intent = getIntent();
        String serPath = intent.getStringExtra("to_send");
        if (TextUtils.isEmpty(serPath)) {
            Log.e(TAG, "No ser path found");
            finish();
            return;
        }

        messages = List.of(FileUtil.readMessageArrayFromFile(this, serPath));
        if (messages.isEmpty()) {
            Log.e(TAG, "No messages to send");
            finish();
            return;
        }

        // Load settings
        subId = DataModel.getSubId();
        delay = SettingManager.getDelay();
        randomize = SettingManager.isRandomizeDelay();

        initViews();
        setupList();

        // Handle Back Press
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                if (currentState == SendingState.SENDING || currentState == SendingState.PAUSED) {
                    showStopConfirmationDialog();
                } else {
                    navigateToHome();
                }
            }
        });

        // Bind to service
        intent = new Intent(this, MessageService.class);
        bindService(intent, connection, Context.BIND_AUTO_CREATE);
    }

    private void initViews() {
        topAppBar = findViewById(R.id.topAppBar);
        rvList = findViewById(R.id.rv_sending_list);
        tvSubmittedCount = findViewById(R.id.tv_sent_count);
        tvConfirmedCount = findViewById(R.id.tv_confirmed_count);
        tvTimer = findViewById(R.id.tv_timer);
        progressSubmitted = findViewById(R.id.progress_sent);
        progressConfirmed = findViewById(R.id.progress_confirmed);

        topAppBar.setNavigationOnClickListener(v -> getOnBackPressedDispatcher().onBackPressed());
        topAppBar.inflateMenu(R.menu.menu_sending);

        topAppBar.setOnMenuItemClickListener(item -> {
            if (item.getItemId() == R.id.action_pause_resume) {
                if (currentIndex >= messages.size()) {
                    navigateToHome();
                } else {
                    togglePauseResume();
                }
                return true;
            }
            return false;
        });
    }

    private void setupList() {
        rvList.setLayoutManager(new LinearLayoutManager(this));
        adapter = new SendingListAdapter(this);
        rvList.setAdapter(adapter);
        adapter.setMessages(messages);
    }

    // --- Sending Logic ---

    private void startSending() {
        currentState = SendingState.SENDING;
        
        // Store in static fields for persistence during this process life
        activeMessages = messages;
        activeSubId = subId;
        activeDelay = delay;
        activeRandomize = randomize;
        activeCurrentIndex = currentIndex;
        activeConfirmedCount = confirmedCount;

        // Start listening for responses immediately
        if (responseReceiver == null) {
            responseReceiver = new SMSResponseReceiver(this::handleIncomingSMS);
            IntentFilter filter = new IntentFilter("android.provider.Telephony.SMS_RECEIVED");
            registerReceiver(responseReceiver, filter);
            Log.d(TAG, "Response receiver registered at start of sending");
        }
        
        updateUI();
        sendNextMessage();
    }

    private void sendNextMessage() {
        if (isStopped) return;
        if (isPaused) return;
        if (currentIndex >= messages.size()) {
            // All submitted, wait for confirmations
            checkCompletion();
            return;
        }

        updateMessageState(currentIndex, MessageState.WAITING);

        int targetDelay = delay;
        if (randomize && delay > 1000) {
            targetDelay = (int) (1000 + Math.random() * (delay - 1000));
        }

        Log.d(TAG, "Scheduling message " + currentIndex + " with delay " + targetDelay + "ms");
        handler.postDelayed(this::executeCurrentSend, targetDelay);
    }

    private void executeCurrentSend() {
        if (isStopped || isPaused) return;
        if (currentIndex >= messages.size()) return;

        Message msg = messages.get(currentIndex);
        service.sendOne(msg, currentIndex, subId);
    }

    private void togglePauseResume() {
        if (currentState == SendingState.SENDING) {
            pauseSending();
        } else if (currentState == SendingState.PAUSED) {
            resumeSending();
        }
    }

    private void pauseSending() {
        isPaused = true;
        handler.removeCallbacksAndMessages(null);
        currentState = SendingState.PAUSED;
        if (currentIndex < messages.size()) {
            updateMessageState(currentIndex, MessageState.PAUSED);
        }
        if (isBound) service.notifyPaused();
        updateUI();
        Log.d(TAG, "Paused at index " + currentIndex);
    }

    private void resumeSending() {
        isPaused = false;
        currentState = SendingState.SENDING;
        if (isBound) service.notifyResumed();
        updateUI();
        sendNextMessage();
        Log.d(TAG, "Resumed at index " + currentIndex);
    }

    private void stopSending() {
        isStopped = true;
        handler.removeCallbacksAndMessages(null);
        currentState = SendingState.CANCELLED;
        
        // Clear active session cache
        activeMessages = null;

        if (countDownTimer != null) {
            countDownTimer.cancel();
        }

        if (responseReceiver != null) {
            try {
                unregisterReceiver(responseReceiver);
            } catch (Exception ignored) {}
            responseReceiver = null;
        }

        if (isBound) {
            service.finishSession(false);
            unbindService(connection);
            isBound = false;
        }
        updateUI();
    }

    private void checkCompletion() {
        if (currentIndex >= messages.size()) {
            if (confirmedCount >= messages.size()) {
                if (currentState != SendingState.LISTENING) {
                    startListeningMode();
                }
            } else {
                // All submitted, but waiting for confirmations.
                updateUI();
                
                // If it takes too long (e.g. 10s after all submitted), just start listening anyway
                handler.postDelayed(() -> {
                    if (currentState == SendingState.SENDING && currentIndex >= messages.size()) {
                        Log.w(TAG, "Confirmation timeout. Starting listening anyway.");
                        startListeningMode();
                    }
                }, 10000L);
            }
        }
    }

    private void startListeningMode() {
        currentState = SendingState.LISTENING;
        updateUI();
        
        if (isBound) {
            service.finishSession(true);
        }

        // Start Timer
        int minutes = SettingManager.getListenTimeout();
        tvTimer.setVisibility(android.view.View.VISIBLE);
        
        countDownTimer = new CountDownTimer(minutes * 60 * 1000L, 1000) {
            @Override
            public void onTick(long millisUntilFinished) {
                long totalSeconds = millisUntilFinished / 1000;
                long m = totalSeconds / 60;
                long s = totalSeconds % 60;
                tvTimer.setText(getString(R.string.timer_format, String.format(Locale.getDefault(), "%02d:%02d", m, s)));
            }

            @Override
            public void onFinish() {
                finishSessionCompletely();
            }
        }.start();
        
        Log.i(TAG, "Entering Listening Mode for " + minutes + " minutes");
    }

    private void handleIncomingSMS(String sender) {
        if (sender == null || messages == null) return;
        
        // Normalize sender: remove everything except digits
        String normalizedSender = sender.replaceAll("\\D", "");
        
        runOnUiThread(() -> {
            boolean found = false;
            for (int i = 0; i < messages.size(); i++) {
                Message msg = messages.get(i);
                if (msg.getState() == MessageState.RESPONDED) continue;
                
                String normalizedMsgPhone = msg.getPhone().replaceAll("\\D", "");
                
                // Match: check if one contains the other (handling prefix differences like +47)
                if (normalizedSender.endsWith(normalizedMsgPhone) || normalizedMsgPhone.endsWith(normalizedSender)) {
                    updateMessageState(i, MessageState.RESPONDED);
                    found = true;
                }
            }
            if (found) {
                Log.d(TAG, "Matched response from " + sender);
            }
        });
    }

    private void finishSessionCompletely() {
        if (currentState == SendingState.COMPLETED) return;
        
        currentState = SendingState.COMPLETED;
        updateUI();
        
        // Clear active session cache
        activeMessages = null;

        if (countDownTimer != null) {
            countDownTimer.cancel();
        }
        
        if (responseReceiver != null) {
            try {
                unregisterReceiver(responseReceiver);
            } catch (Exception ignored) {}
        }

        long completionDelay = SettingManager.getFinishDelay();
        handler.postDelayed(this::navigateToHome, completionDelay);
    }

    // --- Callbacks from MessageService ---

    @Override
    public void onMessageSubmitted(int index) {
        runOnUiThread(() -> {
            updateMessageState(index, MessageState.SUBMITTED);
            updateProgress(index + 1, messages.size(), tvSubmittedCount, progressSubmitted);
            currentIndex++;
            activeCurrentIndex = currentIndex;
            
            // Auto-scroll to the current message
            rvList.smoothScrollToPosition(currentIndex);
            
            sendNextMessage();
        });
    }

    @Override
    public void onMessageConfirmed(int index, boolean success) {
        runOnUiThread(() -> {
            updateMessageState(index, success ? MessageState.SENT : MessageState.FAILED);
            confirmedCount++;
            activeConfirmedCount = confirmedCount;
            updateProgress(confirmedCount, messages.size(), tvConfirmedCount, progressConfirmed);
            checkCompletion();
        });
    }

    // --- UI Updates ---

    private void updateMessageState(int index, MessageState state) {
        messages.get(index).setState(state);
        adapter.notifyItemChanged(index, state);
    }

    private void updateProgress(int current, int total, TextView tvText, LinearProgressIndicator progress) {
        progress.setMax(total);
        progress.setIndeterminate(false);
        progress.setProgress(current);
        tvText.setText(String.format(Locale.getDefault(), "%d/%d", current, total));
    }

    private void updateUI() {
        switch (currentState) {
            case SENDING:
                if (currentIndex >= messages.size()) {
                    topAppBar.setTitle(R.string.sending_completed);
                } else {
                    topAppBar.setTitle(R.string.sending);
                }
                updateMenuIcon(true);
                break;
            case PAUSED:
                topAppBar.setTitle(R.string.paused);
                updateMenuIcon(false);
                break;
            case LISTENING:
                topAppBar.setTitle(R.string.listening);
                updateMenuIcon(true);
                break;
            case COMPLETED:
                topAppBar.setTitle(R.string.done);
                topAppBar.getMenu().clear();
                break;
            case CANCELLED:
                topAppBar.setTitle(R.string.cancelled);
                topAppBar.getMenu().clear();
                break;
        }
    }

    private void updateMenuIcon(boolean isSending) {
        MenuItem item = topAppBar.getMenu().findItem(R.id.action_pause_resume);
        if (item != null) {
            if (currentIndex >= messages.size()) {
                item.setIcon(R.drawable.ic_check_circle);
                item.setEnabled(true);
            } else {
                item.setIcon(isSending ? R.drawable.ic_pause : R.drawable.ic_play);
            }
        }
    }

    private void showStopConfirmationDialog() {
        new MaterialAlertDialogBuilder(this)
                .setTitle(getString(R.string.cancel_send))
                .setPositiveButton(getString(R.string.ok), (dialog, which) -> {
                    stopSending();
                    navigateToHome();
                })
                .setNegativeButton(getString(R.string.cancel), null)
                .show();
    }

    private void navigateToHome() {
        Intent intent = new Intent(this, MainActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        startActivity(intent);
        finish();
    }

    @Override
    protected void onPause() {
        super.onPause();
        Log.d(TAG, "Paused!");
    }

    @Override
    protected void onStop() {
        super.onStop();
        Log.d(TAG, "Stopped!");
    }

    @Override
    protected void onRestart() {
        super.onRestart();
        Log.d(TAG, "Restart!");
        adapter.setMessages(messages);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        handler.removeCallbacksAndMessages(null);
        
        if (countDownTimer != null) {
            countDownTimer.cancel();
        }
        
        if (responseReceiver != null) {
            try {
                unregisterReceiver(responseReceiver);
            } catch (Exception ignored) {}
        }
        
        if (isBound) {
            stopSending();
        }
        Log.d(TAG, "Activity destroyed");
    }
}

