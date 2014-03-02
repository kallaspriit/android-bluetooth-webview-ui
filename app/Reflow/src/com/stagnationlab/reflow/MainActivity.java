package com.stagnationlab.reflow;

import org.json.JSONArray;

import com.stagnationlab.reflow.Bluetooth.State;
import com.stagnationlab.reflow.JavascriptPlugin;
import com.stagnationlab.reflow.R;

import android.os.Build;
import android.os.Bundle;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.content.Intent;
import android.util.Log;
import android.view.Menu;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

public class MainActivity extends Activity implements Bluetooth.Client {
	private WebView webView;
	private JavascriptPlugin plugin;
	private Bluetooth bluetooth;
	
	private int REQUEST_ENABLE_BT = 1;
	private static final String TAG = "Activity";
	
	@SuppressLint({ "SetJavaScriptEnabled", "NewApi" })
	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		
		setContentView(R.layout.activity_main);
		
		bluetooth = new Bluetooth(this, "Reflow");
		plugin = new JavascriptPlugin(this, bluetooth);
 
		webView = (WebView)findViewById(R.id.webview);
		webView.getSettings().setJavaScriptEnabled(true);
		webView.getSettings().setAllowFileAccessFromFileURLs(true);
		webView.getSettings().setAllowUniversalAccessFromFileURLs(true);
		webView.getSettings().setCacheMode(WebSettings.LOAD_NO_CACHE);
		webView.getSettings().setAppCacheEnabled(false);
		webView.addJavascriptInterface(plugin, "native");
		
		if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
		    WebView.setWebContentsDebuggingEnabled(true);
		}

		webView.setWebViewClient(new WebViewClient() {
		    @Override  
		    public void onPageFinished(WebView view, String url) {
		        super.onPageFinished(webView, url);
		        
		        onViewLoaded();
		    }  

		    @Override
		    public void onReceivedError(WebView view, int errorCode, String description, String failingUrl) {
		    	super.onReceivedError(view, errorCode, description, failingUrl);
		        
		    	plugin.showMessage(description);
		    }
		});
		
		webView.loadUrl("file:///android_asset/app/index.html");
	}
	
	@Override
    public void onDestroy() {
        super.onDestroy();
        
        bluetooth.stop();
    }

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		getMenuInflater().inflate(R.menu.main, menu);
		
		return true;
	}
	
	@Override
	public void onActivityResult(int requestCode, int resultCode, Intent data) {
		if (requestCode == REQUEST_ENABLE_BT) {
			if (resultCode == RESULT_OK) {
				if (bluetooth.init()) {
					plugin.showMessage("Bluetooth is now enabled");
					
					notifyBluetoothReady();
				} else {
					plugin.showMessage("This application is not usable without bluetooth");
				}
			} else {
				plugin.showMessage("Enable bluetooth to use this application");
			}
		}
	}
	
	private void onViewLoaded() {
		if (bluetooth.init()) {
			notifyBluetoothReady();
		} else {
			plugin.showMessage("Bluetooth needs to be enabled");
			
			Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
		    startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT);
		}
	}
	
	private void executeJavascript(final String script) {
		//Log.i(TAG, "Call: '" + script + "'");
		
		webView.post(new Runnable() {
            @Override
            public void run() { 
            	webView.loadUrl("javascript:" + script); 
            }
        });
    }
	
	private void callJavascriptFunction(final String function, JSONArray arguments) {
		String script = "window." + function + ".apply(window." + function + ", " + arguments.toString() + ")";
		
		executeJavascript(script);
	}
	
	private void callJavascriptFunction(final String function) {
		JSONArray arguments = new JSONArray();
		
		callJavascriptFunction(function, arguments);
	}
	
	private void notifyBluetoothReady() {
		callJavascriptFunction("onBluetoothReady");
	}
	
	@Override
	public void onBluetoothConnected(String deviceName) {
		Log.i(TAG, "Bluetooth connected to: " + deviceName);
		
		JSONArray arguments = new JSONArray();
		arguments.put(deviceName);
		
		callJavascriptFunction("onBluetoothConnected", arguments);
	}

	@Override
	public void onBluetoothStateChanged(State newState) {
		Log.i(TAG, "Bluetooth state changed to: " + newState);
		
		JSONArray arguments = new JSONArray();
		arguments.put(newState.toString());
		
		callJavascriptFunction("onBluetoothStateChanged", arguments);
	}

	@Override
	public void onBluetoothBytesReceived(byte[] buffer, int bytes) {
		//Log.i(TAG, "Bluetooth received " + bytes + " bytes");
	}
	
	@Override
	public void onBluetoothMessageReceived(String message) {
		Log.i(TAG, "Bluetooth received message: " + message);
		
		JSONArray arguments = new JSONArray();
		arguments.put(message);
		
		callJavascriptFunction("onBluetoothMessageReceived", arguments);
	}
	
	@Override
	public void onBluetoothMessageSent(String message, byte[] buffer, int bytes) {
		Log.i(TAG, "Bluetooth message sent: " + message);
		
		JSONArray arguments = new JSONArray();
		arguments.put(message);
		
		callJavascriptFunction("onBluetoothMessageSent", arguments);
	}
	
	@Override
	public void onBluetoothConnectionFailed() {
		Log.i(TAG, "Bluetooth connection failed");
		
		callJavascriptFunction("onBluetoothConnectionFailed");
	}

	@Override
	public void onBluetoothConnectionLost() {
		Log.i(TAG, "Bluetooth connection lost");
		
		callJavascriptFunction("onBluetoothConnectionLost");
	}
}
