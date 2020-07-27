package c.jahhow.bldc;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.Intent;
import android.os.Bundle;
import android.provider.Settings;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.Set;

public class SelectMotorActivity extends AppCompatActivity {
    static final BluetoothAdapter bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
    static final String BT_DEVICE = "BT_DEVICE";
    static final int REQUEST_ENABLE_BT = 28482;

    ArrayAdapter<BluetoothDevice> arrayAdapter;
    ListView listView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_select_motor);
        Button button=findViewById(R.id.button);
        if (bluetoothAdapter == null) {
            // Device doesn't support Bluetooth
            button.setVisibility(View.GONE);
        } else {
            button.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    Intent intent=new Intent();
                    intent.setAction(Settings.ACTION_BLUETOOTH_SETTINGS);
                    startActivity(intent);
                }
            });
            listView = findViewById(R.id.list);
            arrayAdapter = new ArrayAdapter<BluetoothDevice>(this, R.layout.nearby_bluetooth_device, new ArrayList<BluetoothDevice>()) {
                @NonNull
                @Override
                public View getView(int position, @Nullable View convertView, @NonNull ViewGroup parent) {
                    LinearLayout view = (LinearLayout) convertView;
                    if (view == null) {
                        view = (LinearLayout) getLayoutInflater().inflate(R.layout.nearby_bluetooth_device, parent, false);
                    }
                    BluetoothDevice bluetoothDevice = getItem(position);
                    assert bluetoothDevice != null;
                    ((TextView) view.getChildAt(0)).setText(bluetoothDevice.getName());
                    ((TextView) view.getChildAt(1)).setText(bluetoothDevice.getAddress());
                    return view;
                }
            };
            listView.setAdapter(arrayAdapter);
            listView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
                @Override
                public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                    BluetoothDevice bluetoothDevice = (BluetoothDevice) parent.getItemAtPosition(position);
                    Intent intent = new Intent();
                    intent.putExtra(BT_DEVICE, bluetoothDevice);
                    setResult(RESULT_OK, intent);
                    finish();
                }
            });
        }
    }

    @Override
    protected void onStart() {
        super.onStart();
        if (!bluetoothAdapter.isEnabled()) {
            Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshList();
    }

    void refreshList() {
        arrayAdapter.clear();
        arrayAdapter.addAll(bluetoothAdapter.getBondedDevices());
    }
}
