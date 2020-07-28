package c.jahhow.bldc;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.ViewModelProvider;

import android.Manifest;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.media.MediaRecorder;
import android.os.Bundle;
import android.os.Parcelable;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.SeekBar;
import android.widget.TextView;

import java.io.IOException;
import java.io.OutputStream;

public class MainActivity extends AppCompatActivity {
    static final String TAG = MainActivity.class.getSimpleName();
    static final String PATH_NAME = "/dev/null";
    static final int REQUEST_CODE_MIC = 37189;
    static final int REQUEST_CODE_START_SELECT_MOTOR_ACTIVITY = 11947;
    TextView tx;
    SeekBar seekBar;
    MainViewModel viewModel;
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
        seekBar = findViewById(R.id.seekBar);
        seekBar.setVisibility(View.INVISIBLE);
        seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                OutputStream outputStream = viewModel.outputStream;
                if (outputStream != null) {
                    try {
                        outputStream.write(progress);
                    } catch (IOException e) {
                        //e.printStackTrace();
                        disconnect();
                    }
                } else {
                    disconnect();
                }
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {

            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {

            }
        });
        thr.start();
        Button bt = findViewById(R.id.button);
        bt.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startActivityForResult(new Intent(v.getContext(), SelectMotorActivity.class), REQUEST_CODE_START_SELECT_MOTOR_ACTIVITY);
            }
        });
        viewModel = new ViewModelProvider(this).get(MainViewModel.class);
        viewModel.mainActivity = this;
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

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_CODE_START_SELECT_MOTOR_ACTIVITY) {
            if (resultCode == RESULT_OK) {
                if (data != null) {
                    Parcelable parcelableExtra = data.getParcelableExtra(SelectMotorActivity.BT_DEVICE);
                    if (parcelableExtra instanceof BluetoothDevice) {
                        BluetoothDevice bluetoothDevice = (BluetoothDevice) parcelableExtra;
                        setTitle(bluetoothDevice.getName());
                        viewModel.connect(bluetoothDevice);
                    }
                }
            }
        }
    }

    void onConnected() {
        seekBar.setVisibility(View.VISIBLE);
    }

    void disconnect() {
        try {
            viewModel.socket.close();
        } catch (IOException e) {
            //e.printStackTrace();
        }
        viewModel.outputStream = null;
        seekBar.setVisibility(View.INVISIBLE);
    }
}
