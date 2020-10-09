package c.jahhow.bldc;

import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.media.MediaRecorder;
import android.util.Log;

import androidx.annotation.MainThread;
import androidx.lifecycle.ViewModel;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.util.UUID;

public class MainViewModel extends ViewModel {
    static final String TAG = MainViewModel.class.getSimpleName();
    static final UUID BLUETOOTH_SPP = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");

    final byte initDuty = 70;
    final int arrSize = 32;
    byte[] bestWave = new byte[arrSize];
    byte[] tryWave = new byte[arrSize];

    // These variables should be maintained on main thread only.
    private MainActivity mainActivity;
    ConnectThread connectThread;
    BluetoothDevice bluetoothDevice;
    BluetoothSocket socket;
    OutputStream outputStream;
    InputStream inputStream;

    public MainViewModel() {
        thr.start();
        for (int i = 0; i < arrSize; ++i) {
            bestWave[i] = initDuty;
            tryWave[i] = initDuty;
        }
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
                        timeoutMs = 300;
                        mainActivity.runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                mainActivity.onUpdateAmplitude(recorder.getMaxAmplitude());
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

    @MainThread
    boolean isConnected() {
        return socket != null && socket.isConnected();
    }

    @MainThread
    void onCreateActivity(MainActivity activity) {
        mainActivity = activity;
        mainActivity.setConnected(isConnected());
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
