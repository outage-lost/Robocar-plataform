package com.robocar.controller;

import android.Manifest;
import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Insets;
import android.graphics.Paint;
import android.graphics.RectF;
import android.os.Build;
import android.os.Bundle;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowInsets;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.ArrayAdapter;
import android.widget.TextView;
import android.widget.Toast;
import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.UUID;

public class MainActivity extends Activity {
    private static final int REQUEST_BT = 20;
    private static final UUID SPP_UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");
    private BluetoothAdapter adapter;
    private final ArrayList<BluetoothDevice> devices = new ArrayList<>();
    private Spinner deviceSpinner;
    private TextView status;
    private Button connectButton, ledButton;
    private Connection connection;
    private JoystickView driveStick, steerStick;
    private volatile boolean ledOn;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().setStatusBarColor(Color.rgb(11, 17, 24));
        adapter = BluetoothAdapter.getDefaultAdapter();
        buildUi();
        IntentFilter bluetoothEvents = new IntentFilter();
        bluetoothEvents.addAction(BluetoothDevice.ACTION_FOUND);
        bluetoothEvents.addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED);
        if (Build.VERSION.SDK_INT >= 33) registerReceiver(receiver, bluetoothEvents, Context.RECEIVER_NOT_EXPORTED);
        else registerReceiver(receiver, bluetoothEvents);
        requestBluetoothPermissions();
    }

    private void buildUi() {
        LinearLayout root = new LinearLayout(this); root.setOrientation(LinearLayout.VERTICAL); root.setPadding(28, 18, 28, 18); root.setBackgroundColor(Color.rgb(11,17,24));
        root.setOnApplyWindowInsetsListener((view, insets) -> {
            int left, top, right, bottom;
            if (Build.VERSION.SDK_INT >= 30) {
                Insets system = insets.getInsets(WindowInsets.Type.systemBars() | WindowInsets.Type.displayCutout());
                left = system.left; top = system.top; right = system.right; bottom = system.bottom;
            } else {
                left = insets.getSystemWindowInsetLeft(); top = insets.getSystemWindowInsetTop();
                right = insets.getSystemWindowInsetRight(); bottom = insets.getSystemWindowInsetBottom();
            }
            view.setPadding(28 + left, 18 + top, 28 + right, 18 + bottom);
            return insets;
        });
        LinearLayout top = new LinearLayout(this); top.setGravity(Gravity.CENTER_VERTICAL);
        TextView title = label("ROBOCAR", 20, Color.WHITE); top.addView(title, new LinearLayout.LayoutParams(0, -2, 1));
        status = label("Desconectado", 14, Color.rgb(255, 180, 90)); top.addView(status);
        root.addView(top, new LinearLayout.LayoutParams(-1, 54));
        TextView bluetoothTitle = label("CONEXIÓN BLUETOOTH", 12, Color.rgb(87,214,181));
        root.addView(bluetoothTitle, new LinearLayout.LayoutParams(-1, 28));
        LinearLayout tools = new LinearLayout(this); tools.setGravity(Gravity.CENTER_VERTICAL);
        deviceSpinner = new Spinner(this); tools.addView(deviceSpinner, new LinearLayout.LayoutParams(0, 52, 1));
        Button scan = button("ESCANEAR"); scan.setOnClickListener(v -> scan()); tools.addView(scan, new LinearLayout.LayoutParams(125, 52));
        connectButton = button("CONECTAR"); connectButton.setOnClickListener(v -> toggleConnection()); tools.addView(connectButton, new LinearLayout.LayoutParams(125, 52));
        ledButton = button("LED OFF"); ledButton.setOnClickListener(v -> toggleLed()); tools.addView(ledButton, new LinearLayout.LayoutParams(105, 52));
        root.addView(tools);
        LinearLayout sticks = new LinearLayout(this); sticks.setGravity(Gravity.CENTER); sticks.setWeightSum(2);
        driveStick = new JoystickView(this, "AVANCE"); steerStick = new JoystickView(this, "GIRO");
        sticks.addView(driveStick, new LinearLayout.LayoutParams(0, -1, 1)); sticks.addView(steerStick, new LinearLayout.LayoutParams(0, -1, 1));
        root.addView(sticks, new LinearLayout.LayoutParams(-1, 0, 1));
        setContentView(root);
        root.requestApplyInsets();
    }

    private TextView label(String text, int size, int color) { TextView v = new TextView(this); v.setText(text); v.setTextSize(size); v.setTextColor(color); v.setGravity(Gravity.CENTER_VERTICAL); return v; }
    private Button button(String text) { Button b = new Button(this); b.setText(text); b.setTextSize(11); b.setTextColor(Color.WHITE); return b; }

    private void requestBluetoothPermissions() {
        if (Build.VERSION.SDK_INT >= 31) requestPermissions(new String[]{Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT}, REQUEST_BT);
        else if (Build.VERSION.SDK_INT >= 23) requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, REQUEST_BT);
    }
    private boolean allowed(String p) { return Build.VERSION.SDK_INT < 23 || checkSelfPermission(p) == PackageManager.PERMISSION_GRANTED; }
    private void scan() {
        if (adapter == null) { toast("Este dispositivo no tiene Bluetooth"); return; }
        if (Build.VERSION.SDK_INT >= 31 && !allowed(Manifest.permission.BLUETOOTH_SCAN)) { requestBluetoothPermissions(); return; }
        if (!adapter.isEnabled()) { startActivity(new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)); return; }
        devices.clear(); updateDevices(); adapter.cancelDiscovery(); adapter.startDiscovery(); status.setText("Escaneando...");
    }
    private void updateDevices() { ArrayList<String> names = new ArrayList<>(); for (BluetoothDevice d : devices) names.add(safeName(d) + "\n" + d.getAddress()); if (names.isEmpty()) names.add("Escanea para buscar RoboCar-ESP32"); deviceSpinner.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, names)); }
    private String safeName(BluetoothDevice d) { try { return d.getName() == null ? "Dispositivo Bluetooth" : d.getName(); } catch (SecurityException e) { return "Dispositivo Bluetooth"; } }
    private void toggleConnection() { if (connection != null) { connection.close(); connection = null; setDisconnected(); return; } int selected = deviceSpinner.getSelectedItemPosition(); if (devices.isEmpty() || selected < 0 || selected >= devices.size()) { toast("Pulsa ESCANEAR y selecciona el auto"); return; } connection = new Connection(devices.get(selected)); connection.start(); status.setText("Conectando..."); }
    private void toggleLed() { ledOn = !ledOn; ledButton.setText(ledOn ? "LED ON" : "LED OFF"); if (connection != null) connection.send("L:" + (ledOn ? 1 : 0) + "\n"); }
    private void setConnected() { runOnUiThread(() -> { status.setText("Conectado"); status.setTextColor(Color.rgb(87,214,181)); connectButton.setText("DESCONECTAR"); }); }
    private void setDisconnected() { runOnUiThread(() -> { status.setText("Desconectado"); status.setTextColor(Color.rgb(255,180,90)); connectButton.setText("CONECTAR"); driveStick.reset(); steerStick.reset(); }); }
    private void toast(String s) { Toast.makeText(this, s, Toast.LENGTH_SHORT).show(); }

    private final BroadcastReceiver receiver = new BroadcastReceiver() { @Override public void onReceive(Context c, Intent i) { if (BluetoothDevice.ACTION_FOUND.equals(i.getAction())) { BluetoothDevice d = i.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE); if (d != null && !devices.contains(d)) { devices.add(d); updateDevices(); } } else if (BluetoothAdapter.ACTION_DISCOVERY_FINISHED.equals(i.getAction())) status.setText("Selecciona el auto"); } };

    private class Connection extends Thread {
        private final BluetoothDevice device; private BluetoothSocket socket; private OutputStream out;
        Connection(BluetoothDevice d) { device = d; }
        public void run() { try { if (adapter.isDiscovering()) adapter.cancelDiscovery(); socket = device.createRfcommSocketToServiceRecord(SPP_UUID); socket.connect(); out = socket.getOutputStream(); setConnected(); } catch (Exception e) { close(); runOnUiThread(() -> toast("No se pudo conectar")); } }
        synchronized void send(String command) { if (out == null) return; try { out.write(command.getBytes()); out.flush(); } catch (IOException e) { close(); } }
        synchronized void close() { try { if (socket != null) socket.close(); } catch (IOException ignored) {} out = null; if (connection == this) { connection = null; setDisconnected(); } }
    }

    private class JoystickView extends View {
        private final Paint paint = new Paint(1); private final String caption; private float x, y; private boolean active;
        JoystickView(Context c, String label) { super(c); caption = label; setFocusable(true); }
        protected void onDraw(Canvas c) { super.onDraw(c); float cx = getWidth()/2f, cy = getHeight()/2f, radius = Math.min(getWidth(), getHeight())*.29f; paint.setStyle(Paint.Style.FILL); paint.setColor(Color.rgb(24,34,45)); c.drawCircle(cx,cy,radius,paint); paint.setStyle(Paint.Style.STROKE); paint.setStrokeWidth(3); paint.setColor(Color.rgb(48,73,86)); c.drawCircle(cx,cy,radius,paint); float knobX=cx+x*radius*.72f, knobY=cy+y*radius*.72f; paint.setStyle(Paint.Style.FILL); paint.setColor(Color.rgb(87,214,181)); c.drawCircle(knobX,knobY,radius*.29f,paint); paint.setTextSize(15); paint.setTextAlign(Paint.Align.CENTER); paint.setColor(Color.LTGRAY); c.drawText(caption,cx,cy+radius+34,paint); }
        public boolean onTouchEvent(MotionEvent e) { float cx=getWidth()/2f, cy=getHeight()/2f, r=Math.min(getWidth(),getHeight())*.29f; if(e.getAction()==MotionEvent.ACTION_UP || e.getAction()==MotionEvent.ACTION_CANCEL){ reset(); return true; } float dx=(e.getX()-cx)/r, dy=(e.getY()-cy)/r, len=(float)Math.sqrt(dx*dx+dy*dy); if(len>1){dx/=len;dy/=len;} x=Math.max(-1,Math.min(1,dx)); y=Math.max(-1,Math.min(1,dy)); active=true; invalidate(); sendDrive(); return true; }
        void reset() { x=0; y=0; active=false; invalidate(); sendDrive(); }
        private void sendDrive() { if(connection == null) return; int throttleValue = Math.round(-driveStick.y*100); int steeringValue = Math.round(steerStick.x*100); connection.send("D:"+throttleValue+":"+steeringValue+":200\n"); }
    }

    @Override protected void onDestroy() { if (connection != null) connection.close(); unregisterReceiver(receiver); super.onDestroy(); }
}
