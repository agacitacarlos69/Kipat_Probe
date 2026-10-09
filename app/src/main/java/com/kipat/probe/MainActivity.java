package com.kipat.probe;

import android.app.PendingIntent;
import android.content.Intent;
import android.content.IntentFilter;
import android.nfc.NdefMessage;
import android.nfc.NdefRecord;
import android.nfc.NfcAdapter;
import android.nfc.Tag;
import android.nfc.tech.Ndef;
import android.nfc.tech.NdefFormatable;
import android.os.Build;
import android.os.Bundle;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;

public class MainActivity extends AppCompatActivity {

    private NfcAdapter nfcAdapter;
    private boolean isArmToWrite = false;
    private TextView tvStatus, tvLogs;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        nfcAdapter = NfcAdapter.getDefaultAdapter(this);
        tvStatus = findViewById(R.id.tv_status);
        tvLogs = findViewById(R.id.tv_logs);

        findViewById(R.id.btn_arm_tag).setOnClickListener(v -> {
            if (nfcAdapter == null || !nfcAdapter.isEnabled()) {
                Toast.makeText(this, "Please enable NFC first", Toast.LENGTH_SHORT).show();
                return;
            }
            isArmToWrite = true;
            tvStatus.setText("Status: Hold a blank NTAG 213/215/216 to phone...");
        });

        loadLogs();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (nfcAdapter != null) {
            PendingIntent pendingIntent = PendingIntent.getActivity(
                    this,
                    0,
                    new Intent(this, getClass()).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
                    PendingIntent.FLAG_MUTABLE
            );
            IntentFilter[] intentFiltersArray = new IntentFilter[]{};
            nfcAdapter.enableForegroundDispatch(this, pendingIntent, intentFiltersArray, null);
        }
        loadLogs();
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (nfcAdapter != null) {
            nfcAdapter.disableForegroundDispatch(this);
        }
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        if (isArmToWrite && (NfcAdapter.ACTION_TAG_DISCOVERED.equals(intent.getAction()) ||
                NfcAdapter.ACTION_NDEF_DISCOVERED.equals(intent.getAction()) ||
                NfcAdapter.ACTION_TECH_DISCOVERED.equals(intent.getAction()))) {

            Tag tag;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                tag = intent.getParcelableExtra(NfcAdapter.EXTRA_TAG, Tag.class);
            } else {
                tag = intent.getParcelableExtra(NfcAdapter.EXTRA_TAG);
            }

            if (tag != null) {
                writeProbeTag(tag);
            }
        }
    }

    private void writeProbeTag(Tag tag) {
        try {
            NdefRecord uriRecord = NdefRecord.createUri("https://probe.kipat.com");
            NdefRecord aarRecord = NdefRecord.createApplicationRecord(getPackageName());
            NdefMessage message = new NdefMessage(new NdefRecord[]{uriRecord, aarRecord});

            Ndef ndef = Ndef.get(tag);
            if (ndef != null) {
                try {
                    ndef.connect();
                    if (!ndef.isWritable()) {
                        tvStatus.setText("Status: Write failed - Tag is read-only");
                        return;
                    }
                    if (ndef.getMaxSize() < message.getByteArrayLength()) {
                        tvStatus.setText("Status: Write failed - Tag capacity too small");
                        return;
                    }
                    ndef.writeNdefMessage(message);
                    isArmToWrite = false;
                    tvStatus.setText("Status: Tag written successfully!");
                    Toast.makeText(this, "Tag Written Successfully!", Toast.LENGTH_SHORT).show();
                } finally {
                    try { ndef.close(); } catch (IOException ignored) {}
                }
            } else {
                NdefFormatable formatable = NdefFormatable.get(tag);
                if (formatable != null) {
                    try {
                        formatable.connect();
                        formatable.format(message);
                        isArmToWrite = false;
                        tvStatus.setText("Status: Tag formatted & written!");
                        Toast.makeText(this, "Tag Formatted & Written!", Toast.LENGTH_SHORT).show();
                    } finally {
                        try { formatable.close(); } catch (IOException ignored) {}
                    }
                } else {
                    tvStatus.setText("Status: Write failed - Tag does not support NDEF");
                }
            }
        } catch (Exception e) {
            tvStatus.setText("Status: Write failed - " + e.getLocalizedMessage());
        }
    }

    private void loadLogs() {
        File logFile = new File(getFilesDir(), "probe-log.txt");
        if (!logFile.exists() || logFile.length() == 0) return;

        StringBuilder builder = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new FileReader(logFile))) {
            String line;
            while ((line = reader.readLine()) != null) {
                builder.append(line).append("\n");
            }
            tvLogs.setText(builder.toString());
        } catch (IOException e) {
            e.printStackTrace();
        }

    }
}