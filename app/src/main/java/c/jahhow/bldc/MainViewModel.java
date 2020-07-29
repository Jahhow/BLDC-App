package c.jahhow.bldc;

import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.media.MediaRecorder;
import android.util.Log;

import androidx.lifecycle.ViewModel;

import java.io.IOException;
import java.io.OutputStream;
import java.util.UUID;

public class MainViewModel extends ViewModel {
    static final String TAG = MainViewModel.class.getSimpleName();
    static final UUID BLUETOOTH_SPP = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");

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

    // These variables should be maintained on main thread only.
    private MainActivity mainActivity;
    ConnectThread connectThread;
    BluetoothDevice bluetoothDevice;
    BluetoothSocket socket;
    OutputStream outputStream;

    public MainViewModel() {
        thr.start();
    }

    void onCreateActivity(MainActivity activity) {
        mainActivity = activity;
        mainActivity.onConnectResult(outputStream);
    }

    void connect(BluetoothDevice device) {
        bluetoothDevice = device;
        if (connectThread != null) connectThread.cancel();
        connectThread = new ConnectThread(device);
        connectThread.start();
    }

    private void onConnectResult(OutputStream outputStream2, BluetoothSocket bluetoothSocket,
                                 ConnectThread thread) {
        if (thread == connectThread) {
            if (socket != null) {
                try {
                    socket.close();
                } catch (IOException e) {
                    //e.printStackTrace();
                }
            }
            socket = bluetoothSocket;
            outputStream = outputStream2;
            mainActivity.onConnectResult(outputStream2);
        }
    }

    private class ConnectThread extends Thread {
        BluetoothDevice bluetoothDevice;
        BluetoothSocket mmSocket;

        ConnectThread(BluetoothDevice device) {
            bluetoothDevice = device;
        }

        public void run() {
            OutputStream outputStream = null;
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
                }
            } catch (IOException connectException) {
                // Unable to connect; close the socket and return.
                cancel();
            }

            // return result
            final OutputStream outputStream1 = outputStream;
            final BluetoothSocket mmSocket1 = mmSocket;
            mainActivity.runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    onConnectResult(outputStream1, mmSocket1, ConnectThread.this);
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
            mmSocket = null;
        }
    }
}
