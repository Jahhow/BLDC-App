package c.jahhow.bldc;

import android.Manifest;
import android.bluetooth.BluetoothDevice;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.media.MediaRecorder;
import android.os.Bundle;
import android.os.Parcelable;
import android.util.Log;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.Spinner;
import android.widget.Switch;
import android.widget.TextView;

import androidx.annotation.MainThread;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.ViewModelProvider;

import com.github.mikephil.charting.charts.LineChart;
import com.github.mikephil.charting.components.Description;
import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.data.LineData;
import com.github.mikephil.charting.data.LineDataSet;
import com.github.mikephil.charting.formatter.IFillFormatter;
import com.github.mikephil.charting.interfaces.dataprovider.LineDataProvider;
import com.github.mikephil.charting.interfaces.datasets.ILineDataSet;

import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;

public class MainActivity extends AppCompatActivity {
    static final String TAG = MainActivity.class.getSimpleName();
    static final String PATH_NAME = "/dev/null";
    static final int REQUEST_CODE_MIC = 37189;
    static final int REQUEST_CODE_START_SELECT_MOTOR_ACTIVITY = 11947;
    static final String bestWaveFileName = "bestWave";

    MainViewModel viewModel;
    LineChart noiseChart;
    LineChart waveChart;
    TextView txSpinPeriod;
    Switch toggle;
    Switch toggleECO;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        viewModel = new ViewModelProvider(this).get(MainViewModel.class);
        setContentView(R.layout.activity_main);
        noiseChart = findViewById(R.id.NoiseChart);
        waveChart = findViewById(R.id.chart);
        txSpinPeriod = findViewById(R.id.tx2);
        Button bt = findViewById(R.id.button);
        bt.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startActivityForResult(new Intent(v.getContext(), SelectMotorActivity.class), REQUEST_CODE_START_SELECT_MOTOR_ACTIVITY);
            }
        });
        toggle = findViewById(R.id.toggle);
        toggleECO = findViewById(R.id.toggleECO);
        toggle.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                viewModel.learnEnabled = isChecked;
            }
        });
        toggleECO.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                viewModel.ecoOn = isChecked;
            }
        });

        // Noise Chart ===========================================================
        Description noiseChartDescription = new Description();
        noiseChartDescription.setText("Noise");
        noiseChart.setDescription(noiseChartDescription);
        noiseChart.setAutoScaleMinMaxEnabled(true);
        noiseChart.getXAxis().setDrawLabels(false);

        // create a dataset and give it a type
        LineDataSet noise;
        noise = new LineDataSet(viewModel.noiseList, "Noise");

        //noise.setMode(LineDataSet.Mode.CUBIC_BEZIER);
        noise.setCubicIntensity(0.2f);
        noise.setDrawFilled(true);
        noise.setDrawCircles(false);
        noise.setLineWidth(1.8f);
        noise.setCircleRadius(4f);
        noise.setCircleColor(0xFF6200EE);
        noise.setHighLightColor(0xFF6200EE);
        noise.setColor(0xFF6200EE);
        noise.setFillColor(0xFF6200EE);
        noise.setFillAlpha(100);
        noise.setDrawHorizontalHighlightIndicator(false);
        /*noise.setFillFormatter(new IFillFormatter() {
            @Override
            public float getFillLinePosition(ILineDataSet dataSet, LineDataProvider dataProvider) {
                return noiseChart.getAxisLeft().getAxisMinimum();
            }
        });*/

        LineDataSet bestNoise;
        bestNoise = new LineDataSet(new ArrayList<Entry>(), "Best Noise");

        //bestNoise.setMode(LineDataSet.Mode.CUBIC_BEZIER);
        bestNoise.setCubicIntensity(0.2f);
        bestNoise.setDrawFilled(true);
        bestNoise.setDrawCircles(false);
        bestNoise.setLineWidth(1.8f);
        bestNoise.setCircleRadius(4f);
        bestNoise.setCircleColor(Color.GRAY);
        bestNoise.setHighLightColor(Color.GRAY);
        bestNoise.setColor(Color.GRAY);
        bestNoise.setFillColor(Color.GRAY);
        bestNoise.setFillAlpha(100);
        bestNoise.setDrawHorizontalHighlightIndicator(false);
        /*bestNoise.setFillFormatter(new IFillFormatter() {
            @Override
            public float getFillLinePosition(ILineDataSet dataSet, LineDataProvider dataProvider) {
                return noiseChart.getAxisLeft().getAxisMinimum();
            }
        });*/

        // create a data object with the data sets
        LineData noiseData = new LineData(noise, bestNoise);
        noiseData.setValueTextSize(9f);
        noiseData.setDrawValues(false);
        noiseChart.setData(noiseData);
        noiseChart.setScaleEnabled(false);

        // Current Wave Chart ============================================
        Description waveChartDescription = new Description();
        waveChartDescription.setText("Electric Current");
        waveChart.setDescription(waveChartDescription);
//        waveChart.getAxisLeft().setDrawGridLines(false);
//        waveChart.getAxisRight().setDrawGridLines(false);
//        waveChart.getXAxis().setDrawGridLines(false);

        // create a dataset and give it a type
        LineDataSet dataSetBestWave;
        dataSetBestWave = new LineDataSet(new ArrayList<Entry>(), "Best Wave");

        //dataSetBestWave.setMode(LineDataSet.Mode.CUBIC_BEZIER);
        dataSetBestWave.setCubicIntensity(0.2f);
        dataSetBestWave.setDrawFilled(true);
        dataSetBestWave.setDrawCircles(false);
        dataSetBestWave.setLineWidth(1.8f);
        dataSetBestWave.setCircleRadius(4f);
        int bestWaveColor = 0xFF50DA00;
        dataSetBestWave.setCircleColor(bestWaveColor);
        dataSetBestWave.setHighLightColor(Color.rgb(244, 117, 117));
        dataSetBestWave.setColor(bestWaveColor);
        dataSetBestWave.setFillColor(bestWaveColor);
        //dataSetBestWave.setFillAlpha(100);
        dataSetBestWave.setDrawHorizontalHighlightIndicator(false);
        dataSetBestWave.setFillFormatter(new IFillFormatter() {
            @Override
            public float getFillLinePosition(ILineDataSet dataSet, LineDataProvider dataProvider) {
                return waveChart.getAxisLeft().getAxisMinimum();
            }
        });

        LineDataSet dataSetTryWave;
        dataSetTryWave = new LineDataSet(new ArrayList<Entry>(), "Trying Wave");

        //dataSetTryWave.setMode(LineDataSet.Mode.CUBIC_BEZIER);
        dataSetTryWave.setCubicIntensity(0.2f);
        dataSetTryWave.setDrawFilled(true);
        dataSetTryWave.setDrawCircles(false);
        dataSetTryWave.setLineWidth(1.8f);
        dataSetTryWave.setCircleRadius(4f);
        int tryWaveColor = 0x99ff0000;
        dataSetTryWave.setCircleColor(tryWaveColor);
        dataSetTryWave.setHighLightColor(Color.rgb(244, 117, 117));
        dataSetTryWave.setColor(tryWaveColor);
        dataSetTryWave.setDrawFilled(false);
//        dataSetTryWave.setFillColor(tryWaveColor);
//        dataSetTryWave.setFillAlpha(100);
        dataSetTryWave.setDrawHorizontalHighlightIndicator(false);
        dataSetTryWave.setFillFormatter(new IFillFormatter() {
            @Override
            public float getFillLinePosition(ILineDataSet dataSet, LineDataProvider dataProvider) {
                return waveChart.getAxisLeft().getAxisMinimum();
            }
        });

        // create a data object with the data sets
        LineData data = new LineData(dataSetBestWave, dataSetTryWave);
        data.setValueTextSize(9f);
        data.setDrawValues(false);
        waveChart.setData(data);
        waveChart.setScaleEnabled(false);

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
        saveBestWave();
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
                    } /*else {
                        //txNoise.setText(R.string.No_Permission);
                    }*/
            }
        }
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        saveBestWave();
    }

    void saveBestWave() {
        try {
            FileOutputStream f = openFileOutput(bestWaveFileName, MODE_PRIVATE);
            f.write(viewModel.bestWave);
            f.close();
            Log.i(TAG, "Wave saved.");
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    void startRecorder() {
        synchronized (viewModel.thr) {
            MediaRecorder recorder;
            recorder = new MediaRecorder();
            recorder.setAudioSource(MediaRecorder.AudioSource.UNPROCESSED);
            recorder.setAudioSamplingRate(48000);
            recorder.setAudioEncodingBitRate(96000);
            recorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);
            recorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC);
            recorder.setOutputFile(PATH_NAME);
            try {
                recorder.prepare();
                recorder.start();
                viewModel.pauseThread = false;
                viewModel.thr.notifyAll();
            } catch (IOException e) {
                //e.printStackTrace();
                recorder = null;
                //txNoise.setText(R.string.ERROR);
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
                        //setTitle("BLDC: " + bluetoothDevice.getName());
                        viewModel.connect(bluetoothDevice);
                    }
                }
            }
        }
    }

    @MainThread
    void setConnected(boolean connected) {
        int visibility = connected ? View.VISIBLE : View.GONE;
        txSpinPeriod.setVisibility(visibility);
        toggle.setVisibility(visibility);
        toggleECO.setVisibility(visibility);
    }

    void setLearnEnabled(boolean enabled) {
        toggle.setChecked(enabled);
    }

    void disconnect() {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
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
        });
    }

    void onUpdateAmplitude(float noise) {
        //txNoise.setText("Noise: " + noise);
        if (viewModel.noiseList.size() > MainViewModel.maxSizeNoiseList)
            viewModel.noiseList.removeFirst();
        viewModel.noiseList.addLast(new Entry(viewModel.noiseDataTime++, noise));

        LineDataSet set1;
        set1 = (LineDataSet) noiseChart.getData().getDataSetByIndex(0);
        set1.notifyDataSetChanged();
        noiseChart.getXAxis().setAxisMinimum(viewModel.noiseList.getFirst().getX());
        onUpdateBestNoise(viewModel.bestDb);
    }

    void onUpdateBestNoise(float noise) {
        /*if (noise == 0) {
            if (toggle.isChecked()) {
                toggle.setChecked(false);
                toggleItBack = true;
            }
        } else {
            if (toggleItBack) {
                toggle.setChecked(true);
                toggleItBack = false;
            }
        }*/
        //txBestNoise.setText("Best Noise: " + amp);
        float xStart = 0;
        float xFinish = 1;
        if (viewModel.noiseList.size() >= 3) {
            xStart = viewModel.noiseList.getFirst().getX();
            xFinish = viewModel.noiseList.getLast().getX();
        }

        LineDataSet set1;
        set1 = (LineDataSet) noiseChart.getData().getDataSetByIndex(1);
        set1.clear();
        set1.addEntry(new Entry(xStart, noise));
        set1.addEntry(new Entry(xFinish, noise));
        noiseChart.getData().notifyDataChanged();
        noiseChart.notifyDataSetChanged();
        noiseChart.invalidate();
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
        set1 = (LineDataSet) waveChart.getData().getDataSetByIndex(dataSetIndex);
        set1.setValues(values);
        waveChart.getData().notifyDataChanged();
        waveChart.notifyDataSetChanged();
        waveChart.invalidate();
    }
}
