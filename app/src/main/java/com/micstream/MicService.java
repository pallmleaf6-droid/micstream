package com.micstream;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Intent;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.os.IBinder;
import android.util.Log;

import java.io.IOException;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;

public class MicService extends Service {

    private static final String TAG = "MicStream";
    private static final String CHANNEL_ID = "micstream";
    private static final int PORT = 28200;
    private static final int SAMPLE_RATE = 16000;
    private static final int CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO;
    private static final int AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT;

    private Thread serverThread;
    private volatile boolean running = true;

    @Override
    public void onCreate() {
        super.onCreate();
        createNotificationChannel();
        Notification notification = new Notification.Builder(this, CHANNEL_ID)
                .setContentTitle("MicStream")
                .setContentText("Streaming mic on port " + PORT)
                .setSmallIcon(android.R.drawable.ic_btn_speak_now)
                .build();
        startForeground(1, notification);
        startServer();
    }

    private void startServer() {
        serverThread = new Thread(() -> {
            int bufferSize = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT);
            while (running) {
                try (ServerSocket serverSocket = new ServerSocket(PORT)) {
                    Log.d(TAG, "Waiting for connection on port " + PORT);
                    Socket client = serverSocket.accept();
                    Log.d(TAG, "Client connected: " + client.getRemoteSocketAddress());
                    streamMic(client, bufferSize);
                } catch (IOException e) {
                    Log.e(TAG, "Server error: " + e.getMessage());
                }
            }
        });
        serverThread.start();
    }

    private void streamMic(Socket client, int bufferSize) {
        AudioRecord recorder = new AudioRecord(
                MediaRecorder.AudioSource.MIC,
                SAMPLE_RATE,
                CHANNEL_CONFIG,
                AUDIO_FORMAT,
                bufferSize * 4
        );

        byte[] buffer = new byte[bufferSize];
        try {
            OutputStream out = client.getOutputStream();
            recorder.startRecording();
            Log.d(TAG, "Recording started");
            while (running && !client.isClosed()) {
                int read = recorder.read(buffer, 0, buffer.length);
                if (read > 0) {
                    out.write(buffer, 0, read);
                }
            }
        } catch (IOException e) {
            Log.d(TAG, "Client disconnected: " + e.getMessage());
        } finally {
            recorder.stop();
            recorder.release();
            try { client.close(); } catch (IOException ignored) {}
        }
    }

    private void createNotificationChannel() {
        NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID, "MicStream", NotificationManager.IMPORTANCE_LOW);
        getSystemService(NotificationManager.class).createNotificationChannel(channel);
    }

    @Override
    public IBinder onBind(Intent intent) { return null; }

    @Override
    public void onDestroy() {
        running = false;
        serverThread.interrupt();
        super.onDestroy();
    }
}
