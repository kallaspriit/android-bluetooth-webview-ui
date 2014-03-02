package com.stagnationlab.reflow;

import java.util.List;

import org.json.JSONArray;

import android.util.Log;
import android.webkit.JavascriptInterface;
import android.widget.Toast;

public class JavascriptPlugin {
	private MainActivity activity;
	private Bluetooth bluetooth;
	
	private static final String TAG = "Plugin";
	
	public JavascriptPlugin(MainActivity activity, Bluetooth bluetooth) {
		this.activity = activity;
		this.bluetooth = bluetooth;
	}
	
	@JavascriptInterface
	public void showMessage(String message) {
		Toast.makeText(activity, message, Toast.LENGTH_SHORT).show();
	}
	
	@JavascriptInterface
	public String getPairedDevices() {
		List<String> pairedDevices = bluetooth.getPairedDevices();
		
		return (new JSONArray(pairedDevices)).toString();
	}
	
	@JavascriptInterface
	public void connectDevice(String deviceName) {
		showMessage("Connecting '" + deviceName + "'");
		
		bluetooth.connect(deviceName);
	}
	
	@JavascriptInterface
	public void sendMessage(String message) {
		Log.i(TAG, "Sending message: " + message);
		
		bluetooth.send(message);
	}
}