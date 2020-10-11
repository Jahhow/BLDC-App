package c.jahhow.bldc;

import android.Manifest;
import android.bluetooth.BluetoothDevice;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.media.MediaRecorder;
import android.os.Bundle;
import android.os.Parcelable;
import android.view.View;
import android.widget.Button;
import android.widget.SeekBar;
import android.widget.TextView;

import androidx.annotation.MainThread;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.ViewModelProvider;

import com.github.mikephil.charting.charts.LineChart;
import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.data.LineData;
import com.github.mikephil.charting.data.LineDataSet;
import com.github.mikephil.charting.formatter.IFillFormatter;
import com.github.mikephil.charting.interfaces.dataprovider.LineDataProvider;
import com.github.mikephil.charting.interfaces.datasets.ILineDataSet;

import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;

public class MainActivity extends AppCompatActivity {
    static final String TAG = MainActivity.class.getSimpleName();
    static final String PATH_NAME = "/dev/null";
    static final int REQUEST_CODE_MIC = 37189;
    static final int REQUEST_CODE_START_SELECT_MOTOR_ACTIVITY = 11947;

    LineChart lineChart;
    TextView txNoise;
    TextView txSpinPeriod;
    MainViewModel viewModel;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        viewModel = new ViewModelProvider(this).get(MainViewModel.class);
        setContentView(R.layout.activity_main);
        lineChart = findViewById(R.id.chart);
        txNoise = findViewById(R.id.tx);
        txSpinPeriod = findViewById(R.id.tx2);
        Button bt = findViewById(R.id.button);
        bt.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startActivityForResult(new Intent(v.getContext(), SelectMotorActivity.class), REQUEST_CODE_START_SELECT_MOTOR_ACTIVITY);
            }
        });
        viewModel.onCreateActivity(this);

        ArrayList<Entry> values = new ArrayList<>(viewModel.arrSize);
        for (int x = 0; x < viewModel.arrSize; x++) {
            values.add(new Entry(x, viewModel.bestWave[x]));
        }
        // create a dataset and give it a type
        LineDataSet set1;
        set1 = new LineDataSet(values, "Best Wave");

        set1.setMode(LineDataSet.Mode.CUBIC_BEZIER);
        set1.setCubicIntensity(0.2f);
        set1.setDrawFilled(true);
        set1.setDrawCircles(false);
        set1.setLineWidth(1.8f);
        set1.setCircleRadius(4f);
        set1.setCircleColor(Color.GREEN);
        set1.setHighLightColor(Color.rgb(244, 117, 117));
        set1.setColor(Color.GREEN);
        set1.setFillColor(Color.GREEN);
        set1.setFillAlpha(100);
        set1.setDrawHorizontalHighlightIndicator(false);
        set1.setFillFormatter(new IFillFormatter() {
            @Override
            public float getFillLinePosition(ILineDataSet dataSet, LineDataProvider dataProvider) {
                return lineChart.getAxisLeft().getAxisMinimum();
            }
        });

        LineDataSet set2;
        set2 = new LineDataSet(values, "Trying Wave");

        set2.setMode(LineDataSet.Mode.CUBIC_BEZIER);
        set2.setCubicIntensity(0.2f);
        set2.setDrawFilled(true);
        set2.setDrawCircles(false);
        set2.setLineWidth(1.8f);
        set2.setCircleRadius(4f);
        set2.setCircleColor(Color.RED);
        set2.setHighLightColor(Color.rgb(244, 117, 117));
        set2.setColor(Color.RED);
        set2.setFillColor(Color.RED);
        set2.setFillAlpha(100);
        set2.setDrawHorizontalHighlightIndicator(false);
        set2.setFillFormatter(new IFillFormatter() {
            @Override
            public float getFillLinePosition(ILineDataSet dataSet, LineDataProvider dataProvider) {
                return lineChart.getAxisLeft().getAxisMinimum();
            }
        });

        // create a data object with the data sets
        LineData data = new LineData(set1, set2);
        data.setValueTextSize(9f);
        data.setDrawValues(false);

        // set data
        lineChart.setData(data);
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
            //Log.i(TAG, "onRequestPermissionsResult()");
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
        int visibility = connected ? View.VISIBLE : View.INVISIBLE;
        txSpinPeriod.setVisibility(visibility);
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
        setConnected(false);
    }

    void onUpdateAmplitude(int maxAmplitude) {
        txNoise.setText("Noise: " + maxAmplitude);
    }

    void onReceiveSpinPeriod(String spinPeriod) {
        txSpinPeriod.setText(getString(R.string.spin_period) + spinPeriod);
    }

    void onUpdateBestWave(byte[] wave) {
        updateWave(wave, 0);
    }

    void onUpdateTryWave(byte[] wave) {
        updateWave(wave, 1);
    }

    private void updateWave(byte[] wave, int dataSetIndex) {
        ArrayList<Entry> values = new ArrayList<>(wave.length);
        for (int x = 0; x < wave.length; x++) {
            values.add(new Entry(x, wave[x]));
        }

        LineDataSet set1;
        set1 = (LineDataSet) lineChart.getData().getDataSetByIndex(dataSetIndex);
        set1.setValues(values);
        lineChart.getData().notifyDataChanged();
        lineChart.notifyDataSetChanged();
        lineChart.invalidate();
    }
}
