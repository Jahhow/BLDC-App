package c.jahhow.bldc;

import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.Context;
import android.content.SharedPreferences;
import android.media.MediaRecorder;
import android.util.Log;
import android.widget.Toast;

import androidx.annotation.MainThread;
import androidx.lifecycle.ViewModel;

import com.github.mikephil.charting.data.Entry;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.util.LinkedList;
import java.util.Random;
import java.util.UUID;

public class MainViewModel extends ViewModel {
    static final String TAG = MainViewModel.class.getSimpleName();
    static final UUID BLUETOOTH_SPP = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");
    static SharedPreferences preferences;

    byte squareWaveDuty = 80;
    static final int arrSize = 32;
    boolean getBestWave = true;
    byte[] bestWave = new byte[arrSize];
    byte[] tryWave = new byte[arrSize];
    int spinPeriod = -1;
    int targetPeriod = 27;
    float minDb = Float.MAX_VALUE;
    float tempMinDb = Float.MAX_VALUE;
    static final int numAmpSample = 80;
    int countNumSample = 0;
    float bestDb = 0;
    Random random = new Random();
    boolean learnEnabled = false;
    boolean sendBestWave = true;
    int biasFixPeriod = 0;
    int tryIndex = 0;
    private int nTimesTriedCurIndex = 0;
    static final int nTryPerIndex = 6;
    int learnMode = 0;
    static final int maxSizeNoiseList = 1000;
    LinkedList<Entry> noiseList = new LinkedList<>();
    int noiseDataTime = 0;
    int mosOnLength = 10;
    int firstOnIndex = 0;
    int tryingMosOnLength = mosOnLength;
    int tryingFirstOnIndex = firstOnIndex;
//    float smoothFactor = .3f;
//    float smoothedDb = 0;

    // These variables should be maintained on main thread only.
    private MainActivity mainActivity;
    ConnectThread connectThread;
    BluetoothDevice bluetoothDevice;
    BluetoothSocket socket;
    OutputStream outputStream;
    InputStream inputStream;

    public MainViewModel() {
        thr.start();
    }

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
                        timeoutMs = 20;
                        final float amp = recorder.getMaxAmplitude();
                        if (amp > 0) {
                            final float db = (float) (20 * Math.log10(amp));
                            //smoothedDb = (1 - smoothFactor) * smoothedDb + smoothFactor * db;
                            mainActivity.runOnUiThread(new Runnable() {
                                @Override
                                public void run() {
                                    mainActivity.onUpdateAmplitude(db);
                                }
                            });
                            if (db < tempMinDb)
                                tempMinDb = db;
                            ++countNumSample;
                            if (countNumSample >= numAmpSample) {
                                minDb = tempMinDb;
                                countNumSample = 0;
                                tempMinDb = Float.MAX_VALUE;
                                switch (learnMode) {
                                    case 0://Noise Canceling
                                        if (learnEnabled) {
                                            if (spinPeriod == targetPeriod && minDb < bestDb) {
                                                //new best wave found
                                                bestDb = minDb;
                                                System.arraycopy(tryWave, 0, bestWave, 0, arrSize);
                                                mainActivity.runOnUiThread(new Runnable() {
                                                    @Override
                                                    public void run() {
                                                        mainActivity.onUpdateBestWave(bestWave);
                                                        mainActivity.onUpdateBestNoise(bestDb);
                                                    }
                                                });
                                                biasFixPeriod = 0;
                                            } else if (spinPeriod >= 0) {
                                                if (spinPeriod < targetPeriod) {
                                                    --biasFixPeriod;
                                                } else if (spinPeriod > targetPeriod) {
                                                    ++biasFixPeriod;
                                                }
                                            }
                                    /*for (int i = 0; i < arrSize; ++i) {
                                        tryWave[i] = (byte) ((int) bestWave[i] + tryBias + random.nextInt(9) - 4);// += rand( -4 ~ 4 )
                                    }*/

                                            for (int i = 0; i < arrSize; ++i) {
                                                tryWave[i] = (byte) ((int) bestWave[i] + biasFixPeriod);
                                            }
                                            int randAddition = random.nextInt(8) - 4;
                                            if (randAddition >= 0) randAddition++;
                                            tryWave[tryIndex] = (byte) ((int) tryWave[tryIndex] + randAddition);
                                            if (nTimesTriedCurIndex < nTryPerIndex) {
                                                nTimesTriedCurIndex++;
                                            } else {
                                                tryIndex = (tryIndex + 1) % arrSize;
                                                nTimesTriedCurIndex = 0;
                                            }
                                            mainActivity.onUpdateTryWave(tryWave, 0);
                                            try {
                                                final OutputStream outputStream1 = outputStream;
                                                if (outputStream1 != null) {
                                                    //outputStream.write(91543278);
                                                    outputStream1.write(tryWave);
                                                }
                                            } catch (IOException e) {
                                                mainActivity.disconnect();
                                            }
                                            sendBestWave = true;
                                        } else {
                                            if (sendBestWave) {
                                                try {
                                                    final OutputStream outputStream1 = outputStream;
                                                    if (outputStream1 != null) {
                                                        outputStream1.write(bestWave);
                                                    }
                                                } catch (IOException e) {
                                                    mainActivity.disconnect();
                                                }
                                                sendBestWave = false;
                                            }
                                            bestDb = minDb;
                                            mainActivity.runOnUiThread(new Runnable() {
                                                @Override
                                                public void run() {
                                                    mainActivity.onUpdateBestNoise(bestDb);
                                                }
                                            });
                                        }
                                        break;
                                    case 1://Square Wave
                                        if (learnEnabled) {
                                            boolean sendNewWave = false;
                                            if (spinPeriod >= 0) {
                                                if (spinPeriod < targetPeriod) {
                                                    --squareWaveDuty;
                                                    sendNewWave = true;
                                                } else if (spinPeriod > targetPeriod) {
                                                    ++squareWaveDuty;
                                                    sendNewWave = true;
                                                }
                                            }
                                            if (sendNewWave) {
                                                for (int i = 0; i < arrSize; ++i) {
                                                    tryWave[i] = squareWaveDuty;
                                                }
                                                try {
                                                    final OutputStream outputStream1 = outputStream;
                                                    if (outputStream1 != null) {
                                                        //outputStream.write(91543278);
                                                        outputStream1.write(tryWave);
                                                    }
                                                } catch (IOException e) {
                                                    mainActivity.disconnect();
                                                }
                                                mainActivity.onUpdateTryWave(tryWave, 1);
                                            }
                                        }
                                        break;
                                    case 2://Power Saving
                                        /*if (learnEnabled) {
                                            int i;
                                            for (i = 0; i < tryingFirstOnIndex; ++i) {
                                                tryWave[i] = 0;
                                            }
                                            int offFirstIndex = tryingFirstOnIndex + tryingMosOnLength;
                                            for (; i < offFirstIndex; ++i) {
                                                tryWave[i] = (byte) 255;
                                            }
                                            for (; i < arrSize; ++i) {
                                                tryWave[i] = 0;
                                            }
                                        }*/
                                        break;
                                }
                            }
                        }
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

    @MainThread
    boolean isConnected() {
        return socket != null && socket.isConnected();
    }

    void setLearnMode(int learnMode) {
        this.learnMode = learnMode;
        switch (learnMode) {
            case 0:// Noise canceling
                break;
            case 1://Square wave
                for (int i = 0; i < MainViewModel.arrSize; ++i) {
                    tryWave[i] = squareWaveDuty;
                }
                mainActivity.onUpdateTryWave(tryWave, 1);
                break;
            case 2:
                break;
        }
    }

    @MainThread
    void onCreateActivity(MainActivity activity) {
        mainActivity = activity;
        if (preferences == null)
            preferences = activity.getSharedPreferences("BLDC preferences", Context.MODE_PRIVATE);
        if (getBestWave) {
            getBestWave = false;
            boolean readSuccess = false;
            try {
                FileInputStream f = activity.openFileInput(MainActivity.bestWaveFileName);
                if (f.read(bestWave, 0, bestWave.length) == bestWave.length)
                    readSuccess = true;
            } catch (FileNotFoundException e) {
                //e.printStackTrace();
            } catch (IOException e) {
                //e.printStackTrace();
            }
            if (readSuccess) {
                System.arraycopy(bestWave, 0, tryWave, 0, arrSize);
            } else {
                for (int i = 0; i < arrSize; ++i) {
                    bestWave[i] = squareWaveDuty;
                    tryWave[i] = squareWaveDuty;
                }
            }
        }
        activity.setConnected(isConnected());
        activity.setLearnEnabled(learnEnabled);
        activity.onUpdateBestNoise(bestDb);
        activity.onUpdateTryWave(tryWave, learnMode);
        activity.onUpdateBestWave(bestWave);
        activity.spinner.setSelection(learnMode);
    }

    void connect(BluetoothDevice device) {
        bluetoothDevice = device;
        if (connectThread != null) connectThread.cancel();
        connectThread = new ConnectThread(device);
        connectThread.start();
    }

    private class ConnectThread extends Thread {
        BluetoothDevice bluetoothDevice;
        BluetoothSocket mmSocket;

        ConnectThread(BluetoothDevice device) {
            bluetoothDevice = device;
        }

        public void run() {
            OutputStream outputStream = null;
            InputStream inputStream = null;
            try {
                // Get a BluetoothSocket to connect with the given BluetoothDevice.
                // MY_UUID is the app's UUID string, also used in the server code.
                mmSocket = bluetoothDevice.createRfcommSocketToServiceRecord(BLUETOOTH_SPP);
                if (mmSocket != null) {
                    // Cancel discovery because it otherwise slows down the connection.
                    //bluetoothAdapter.cancelDiscovery();

                    // Connect to the remote device through the socket. This call blocks
                    // until it succeeds or throws an exception.
                    mmSocket.connect();

                    // The connection attempt succeeded. Perform work associated with
                    // the connection in a separate thread.
                    outputStream = mmSocket.getOutputStream();
                    inputStream = mmSocket.getInputStream();
                }
            } catch (IOException connectException) {
                // Unable to connect; close the socket and return.
                cancel();
            }

            // return result
            final OutputStream outputStream1 = outputStream;
            final InputStream inputStream1 = inputStream;
            mainActivity.runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    if (ConnectThread.this != connectThread)
                        return;
                    if (socket != null) {
                        try {
                            socket.close();
                        } catch (IOException e) {
                            //e.printStackTrace();
                        }
                    }
                    if (mmSocket == null || !mmSocket.isConnected()
                            || outputStream1 == null || inputStream1 == null) {
                        // failed
                        MainViewModel.this.socket = null;
                        MainViewModel.this.outputStream = null;
                        MainViewModel.this.inputStream = null;
                        mainActivity.setConnected(false);
                        Toast.makeText(mainActivity, "Failed", Toast.LENGTH_SHORT).show();
                    } else {
                        MainViewModel.this.socket = mmSocket;
                        MainViewModel.this.outputStream = outputStream1;
                        MainViewModel.this.inputStream = inputStream1;
                        mainActivity.setConnected(true);

                        Thread threadInputStream = new Thread(new Runnable() {
                            @Override
                            public void run() {
                                try {
                                    BufferedReader reader = new BufferedReader(new InputStreamReader(MainViewModel.this.inputStream));
                                    while (true) {
                                        final String s = reader.readLine();
                                        if (s == null) {
                                            mainActivity.disconnect();
                                            return;
                                        }
                                        try {
                                            spinPeriod = Integer.parseInt(s);
                                        } catch (NumberFormatException e) {
                                            spinPeriod = -1;
                                        }
                                        mainActivity.runOnUiThread(new Runnable() {
                                            @Override
                                            public void run() {
                                                mainActivity.onReceiveSpinPeriod(s);
                                            }
                                        });
                                    }
                                } catch (IOException e) {
                                    //e.printStackTrace();
                                }
                            }
                        });
                        threadInputStream.start();
                    }
                }
            });
        }

        // Closes the client socket and causes the thread to finish.
        public void cancel() {
            if (mmSocket == null) return;
            try {
                mmSocket.close();
            } catch (IOException e) {
                Log.e(TAG, "Could not close the client socket", e);
            }
        }
    }
}
