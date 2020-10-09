package c.jahhow.bldc;

import androidx.annotation.MainThread;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.ViewModelProvider;

import android.Manifest;
import android.bluetooth.BluetoothDevice;
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
    TextView txNoise;
    TextView txSpinPeriod;
    SeekBar seekBar;
    MainViewModel viewModel;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        viewModel = new ViewModelProvider(this).get(MainViewModel.class);
        setContentView(R.layout.activity_main);
        txNoise = findViewById(R.id.tx);
        txSpinPeriod = findViewById(R.id.tx2);
        Button bt = findViewById(R.id.button);
        bt.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startActivityForResult(new Intent(v.getContext(), SelectMotorActivity.class), REQUEST_CODE_START_SELECT_MOTOR_ACTIVITY);
            }
        });
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
                }
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {

            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {

            }
        });
        viewModel.onCreateActivity(this);
    }

    @Override
    protected void onStart() {
        //Log.i(TAG, "onStart()");
        super.onStart();
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.RECORD_AUDIO}, REQUEST_CODE_MIC);
        } else {
            synchronized (viewModel.thr) {
                if (viewModel.recorder == null) startRecorder();
                viewModel.pauseThread = false;
                viewModel.thr.notifyAll();
            }
        }
    }

    @Override
    protected void onStop() {
        super.onStop();
        synchronized (viewModel.thr) {
            viewModel.pauseThread = true;
            viewModel.thr.notifyAll();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (isChangingConfigurations()) return;
        synchronized (viewModel.thr) {
            viewModel.runThr = false;
            viewModel.thr.notifyAll();
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
                        txNoise.setText(R.string.No_Permission);
                    }
            }
        }
    }

    void startRecorder() {
        synchronized (viewModel.thr) {
            MediaRecorder recorder;
            recorder = new MediaRecorder();
            recorder.setAudioSource(MediaRecorder.AudioSource.MIC);
            recorder.setOutputFormat(MediaRecorder.OutputFormat.THREE_GPP);
            recorder.setAudioEncoder(MediaRecorder.AudioEncoder.AMR_NB);
            recorder.setOutputFile(PATH_NAME);
            try {
                recorder.prepare();
                recorder.start();
                viewModel.pauseThread = false;
                viewModel.thr.notifyAll();
            } catch (IOException e) {
                //e.printStackTrace();
                recorder = null;
                txNoise.setText(R.string.ERROR);
            }
            viewModel.recorder = recorder;
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

    @MainThread
    void setConnected(boolean connected) {
        seekBar.setVisibility(connected ? View.VISIBLE : View.INVISIBLE);
    }

    @MainThread
    void disconnect() {
        if (viewModel.socket == null) return;
        try {
            viewModel.socket.close();
        } catch (IOException e) {
            //e.printStackTrace();
        }
        viewModel.socket = null;
        viewModel.outputStream = null;
        viewModel.inputStream = null;
        seekBar.setVisibility(View.INVISIBLE);
    }

    void onUpdateAmplitude(int maxAmplitude) {
        txNoise.setText("Noise: " + String.valueOf(maxAmplitude));
    }

    void onReceiveSpinPeriod(String spinPeriod) {
        txSpinPeriod.setText(getString(R.string.spin_period) + spinPeriod);
    }
}
