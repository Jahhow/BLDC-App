package c.jahhow.bldc;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import android.Manifest;
import android.content.pm.PackageManager;
import android.media.MediaRecorder;
import android.os.Bundle;
import android.util.Log;
import android.widget.TextView;

import java.io.IOException;

public class MainActivity extends AppCompatActivity {
    static final String TAG = MainActivity.class.getSimpleName();
    static final String PATH_NAME = "/dev/null";
    static final int REQUEST_CODE_MIC = 37189;
    TextView tx;

    final Thread thr = new Thread(new Runnable() {
        @Override
        public void run() {
            synchronized (thr) {
                while (runThr) {
                    if (recorder == null) {
                        try {
                            thr.wait();
                        } catch (InterruptedException e) {
                            //e.printStackTrace();
                        }
                        continue;
                    }
                    long timeoutMs;
                    if (pauseThread) timeoutMs = 0;
                    else {
                        timeoutMs = 300;
                        tx.post(new Runnable() {
                            @Override
                            public void run() {
                                tx.setText(String.valueOf(recorder.getMaxAmplitude()));
                            }
                        });
                    }
                    try {
                        thr.wait(timeoutMs);
                    } catch (InterruptedException e) {
                        //e.printStackTrace();
                    }
                }
                if (recorder != null) {
                    recorder.stop();
                    recorder = null;
                }
            }
        }
    });//lock
    MediaRecorder recorder = null;//locked by thread thr
    boolean pauseThread = true;  //locked by thread thr
    boolean runThr = true;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        tx = findViewById(R.id.tx);
        thr.start();
    }

    @Override
    protected void onStart() {
        Log.i(TAG, "onStart()");
        super.onStart();
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.RECORD_AUDIO}, REQUEST_CODE_MIC);
        } else {
            synchronized (thr) {
                if (recorder == null) startRecorder();
                pauseThread = false;
                thr.notifyAll();
            }
        }
    }

    @Override
    protected void onStop() {
        super.onStop();
        synchronized (thr) {
            pauseThread = true;
            thr.notifyAll();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        synchronized (thr) {
            runThr = false;
            thr.notifyAll();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_CODE_MIC) {
            Log.i(TAG, "onRequestPermissionsResult()");
            for (int i = 0; i < permissions.length; ++i) {
                if (permissions[i].equals(Manifest.permission.RECORD_AUDIO))
                    if (grantResults[i] == PackageManager.PERMISSION_GRANTED) {
                        startRecorder();
                    } else {
                        tx.setText(R.string.No_Permission);
                    }
            }
        }
    }

    void startRecorder() {
        synchronized (thr) {
            recorder = new MediaRecorder();
            recorder.setAudioSource(MediaRecorder.AudioSource.MIC);
            recorder.setOutputFormat(MediaRecorder.OutputFormat.THREE_GPP);
            recorder.setAudioEncoder(MediaRecorder.AudioEncoder.AMR_NB);
            recorder.setOutputFile(PATH_NAME);
            try {
                recorder.prepare();
                recorder.start();
                pauseThread = false;
                thr.notifyAll();
            } catch (IOException e) {
                //e.printStackTrace();
                recorder = null;
                tx.setText(R.string.ERROR);
            }
        }
    }
}
