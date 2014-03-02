package com.stagnationlab.reflow;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothServerSocket;
import android.bluetooth.BluetoothSocket;
import android.util.Log;

public class Bluetooth {
	private Client client;
	private BluetoothAdapter bt;
	private AcceptThread acceptThread;
	private ConnectThread connectThread;
	private ConnectedThread connectedThread;
	
	private boolean ready = false;
	private String sdpRecordName = "Bluetooth";
	private String remoteMessage = "";
	
	private static final String TAG = "Bluetooth";
	private static final UUID MY_UUID = UUID.fromString("00001101-0000-1000-8000-00805f9b34fb");
	
	public enum State {
		UNINITIALIZED,
		LISTEN,
		CONNECTING,
		CONNECTED
	};
	
	public State state;
	
	public interface Client {
		void onBluetoothStateChanged(State newState);
		void onBluetoothBytesReceived(byte[] buffer, int bytes);
		void onBluetoothMessageReceived(String message);
		void onBluetoothMessageSent(String message, byte[] buffer, int bytes);
		void onBluetoothConnected(String deviceName);
		void onBluetoothConnectionFailed();
		void onBluetoothConnectionLost();
	}
	
	private class AcceptThread extends Thread {
        // The local server socket
        private final BluetoothServerSocket serverSocket;

        public AcceptThread() {
            BluetoothServerSocket tmp = null;

            // Create a new listening server socket
            try {
                tmp = bt.listenUsingRfcommWithServiceRecord(sdpRecordName, MY_UUID);
            } catch (IOException e) {
                Log.e(TAG, "listen() failed", e);
            }
            
            serverSocket = tmp;
        }

        public void run() {
            BluetoothSocket socket = null;
            
            Log.i(TAG, "Start accept thread");

            // Listen to the server socket if we're not connected
            while (state != Bluetooth.State.CONNECTED) {
                try {
                    // This is a blocking call and will only return on a
                    // successful connection or an exception
                    socket = serverSocket.accept();
                } catch (IOException e) {
                    Log.e(TAG, "Accepting server socket failed", e);
                    
                    break;
                }

                // If a connection was accepted
                if (socket != null) {
                    synchronized (Bluetooth.this) {
                        switch (state) {
	                        case LISTEN:
	                        case CONNECTING:
	                            // Situation normal. Start the connected thread.
	                            onSocketOpened(socket, socket.getRemoteDevice());
                            break;
                            
	                        case UNINITIALIZED:
	                        case CONNECTED:
	                            // Either not ready or already connected. Terminate new socket.
	                            try {
	                                socket.close();
	                            } catch (IOException e) {
	                                Log.e(TAG, "Could not close unwanted socket", e);
	                            }
                            break;
                        }
                    }
                }
            }
            
            Log.i(TAG, "End accept thread");
        }

        public void cancel() {
        	Log.i(TAG, "Cancel accept thread");
        	
            try {
                serverSocket.close();
            } catch (IOException e) {
                Log.e(TAG, "close() of server failed", e);
            }
        }
    }
	
	private class ConnectThread extends Thread {
	    private final BluetoothSocket socket;
	    private final BluetoothDevice device;
	 
	    public ConnectThread(BluetoothDevice device) {
	    	this.device = device;
	    	
	        BluetoothSocket tempSocket = null;
	        
	        try {
	            //tempSocket = device.createRfcommSocketToServiceRecord(MY_UUID);
	            tempSocket = device.createInsecureRfcommSocketToServiceRecord(MY_UUID);
	        } catch (IOException e) {
	        	Log.e(TAG, "Creating bluetooth socket failed: " + e.getMessage());
	        }
	        
	        socket = tempSocket;
	    }
	 
	    public void run() {
	    	Log.i(TAG, "Start connect thread");
	    	
	        bt.cancelDiscovery();
	        
	        Log.i(TAG, "Attempting to connect to " + device.getName());
	 
	        try {
	            socket.connect();
	        } catch (IOException connectException) {
	            try {
	                socket.close();
	            } catch (IOException closeException) { }
	            
	            Log.e(TAG, "Opening connection failed: " + connectException.getMessage());
	            
	            onConnectionFailed();
	            
	            return;
	        }
	        
	        synchronized (Bluetooth.this) {
                connectThread = null;
            }
	        
	        onSocketOpened(socket, device);
	        
	        Log.i(TAG, "End connect thread");
	    }
	 
	    public void cancel() {
	    	Log.i(TAG, "Cancel connect thread");
	    	
	    	if (socket != null) {
		        try {
		            socket.close();
		        } catch (IOException e) {
		        	Log.e(TAG, "Closing socket failed");
		        }
	    	}
	    }
	}
	
	private class ConnectedThread extends Thread {
	    private final BluetoothSocket socket;
	    private final InputStream inputStream;
	    private final OutputStream outputStream;
	 
	    public ConnectedThread(BluetoothSocket socket) {
	        this.socket = socket;
	        
	        InputStream tempInputStream = null;
	        OutputStream tempOutputStream = null;
	 
	        try {
	            tempInputStream = socket.getInputStream();
	            tempOutputStream = socket.getOutputStream();
	        } catch (IOException e) {
	        	Log.e(TAG, "Getting IO streams failed failed: " + e.getMessage());
	        }
	 
	        inputStream = tempInputStream;
	        outputStream = tempOutputStream;
	    }
	 
	    public void run() {
	    	Log.i(TAG, "Start connected thread");
	    	
	        byte[] buffer = new byte[1024];  // buffer store for the stream
	        int bytes; // bytes returned from read()
	 
	        // Keep listening to the InputStream until an exception occurs
	        while (true) {
	            try {
	                // Read from the InputStream
	                bytes = inputStream.read(buffer);
	                
	                client.onBluetoothBytesReceived(buffer, bytes);
	                
	                for (int i = 0; i < bytes; i++) {
	                	if (buffer[i] == '\n') {
	                		client.onBluetoothMessageReceived(remoteMessage);
	                		remoteMessage = "";
	                	} else {
	                		remoteMessage += (char)buffer[i];
	                	}
	                }
	            } catch (IOException e) {
	            	Log.e(TAG, "disconnected", e);
	            	
	            	onConnectionLost();
	            	
	                break;
	            }
	        }
	        
	        Log.i(TAG, "End connected thread");
	    }
	 
	    /* Call this from the main activity to send data to the remote device */
	    public void write(byte[] buffer) {
	        try {
	        	String message = new String(buffer);
	            outputStream.write(buffer);
	            
	            client.onBluetoothMessageSent(message, buffer, buffer.length);
	        } catch (IOException e) { }
	    }
	 
	    /* Call this from the main activity to shutdown the connection */
	    public void cancel() {
	    	Log.i(TAG, "Cancel connected thread");
	    	
	        try {
	            socket.close();
	        } catch (IOException e) { }
	    }
	}
	
	public Bluetooth(Client client, String sdpRecordName) {
		this.client = client;
		this.sdpRecordName = sdpRecordName;
		state = State.UNINITIALIZED;
	}
	
	public boolean init() {
		Log.i(TAG, "Initiating bluetooth manager");
		
		if (bt == null) {
			Log.i(TAG, "Requesting default adapter");
			
			bt = BluetoothAdapter.getDefaultAdapter();
		}
		
		if (bt == null) {
			Log.w(TAG, "Failed to get adapter, no bluetooth support");
			
			return false;
		}
		
		if (!bt.isEnabled()) {
			Log.i(TAG, "Bluetooth is not enabled");
			
		    return false;
		}
		
		stop();
		
		acceptThread = new AcceptThread();
		acceptThread.start();
		
		setState(State.LISTEN);
		
		ready = true;
		
		Log.i(TAG, "Bluetooth initialized successfully");
		
		return true;
	}
	
	public synchronized List<String> getPairedDevices() {
		List<String> pairedDeviceNames = new ArrayList<String>();
		
		if (!ready) {
			Log.w(TAG, "Requested paired devices but bluetooth is not ready");
			
			return pairedDeviceNames;
		}
		
		Set<BluetoothDevice> pairedDevices = bt.getBondedDevices();

		Log.i(TAG, "Paired device count: " + pairedDevices.size());
		
		if (pairedDevices.size() > 0) {
		    for (BluetoothDevice device : pairedDevices) {
		    	pairedDeviceNames.add(device.getName());
		    	
		    	Log.i(TAG, "> " + device.getName() + " - " + device.getAddress());
		    }
		}
		
		return pairedDeviceNames;
	}
	
	public synchronized boolean connect(String deviceName) {
		if (connectThread != null) {
			connectThread.cancel();
			connectThread = null;
		}
		
		if (connectedThread != null) {
			connectedThread.cancel();
			connectedThread = null;
		}
		
		Set<BluetoothDevice> pairedDevices = bt.getBondedDevices();
		
		for (BluetoothDevice device : pairedDevices) {
	    	if (device.getName().equals(deviceName)) {
	    		//return connect(bt.getRemoteDevice(device.getAddress()));
	    		return connect(device);
	    	}
	    }
		
		return false;
	}
	
	public synchronized boolean connect(BluetoothDevice device) {
		if (connectThread != null) {
			connectThread.cancel();
			connectThread = null;
		}
		
		if (connectedThread != null) {
			connectedThread.cancel();
			connectedThread = null;
		}
		
		connectThread = new ConnectThread(device);
		connectThread.start();
		
		setState(State.CONNECTING);
		
		return true;
	}
	
	public synchronized void stop() {
		Log.i(TAG, "Stopping bluetooth threads");
		
		if (connectThread != null) {
			connectThread.cancel();
			connectThread = null;
		}
		
		if (connectedThread != null) {
			connectedThread.cancel();
			connectedThread = null;
		}
		
		if (acceptThread != null) {
			acceptThread.cancel();
			acceptThread = null;
		}
		
		setState(State.UNINITIALIZED);
	}
	
	public synchronized boolean send(String message) {
		Log.i(TAG, "Send message: " + message);
		
		// Create temporary object
        ConnectedThread syncConnectedThread;
        // Synchronize a copy of the ConnectedThread
        synchronized (this) {
            if (state != State.CONNECTED) return false;
            
            syncConnectedThread = connectedThread;
        }
        // Perform the write unsynchronized
        syncConnectedThread.write(message.getBytes());
        
        return true;
	}
	
	private void onSocketOpened(BluetoothSocket socket, BluetoothDevice device) {
		Log.i(TAG, "Socket opened");
		
		if (connectThread != null) {
			connectThread.cancel();
			connectThread = null;
		}
		
		if (connectedThread != null) {
			connectedThread.cancel();
			connectedThread = null;
		}
		
		if (acceptThread != null) {
			acceptThread.cancel();
			acceptThread = null;
		}
		
		setState(State.CONNECTED);
		
		connectedThread = new ConnectedThread(socket);
		connectedThread.start();
		
		client.onBluetoothConnected(device.getName());
	}
	
	public void onConnectionFailed() {
		Log.i(TAG, "Connection failed");
		
		client.onBluetoothConnectionFailed();
		
		init();
	}
	
	public void onConnectionLost() {
		Log.i(TAG, "Connection lost");
		
		client.onBluetoothConnectionLost();
		
		setState(State.LISTEN);
	}
	
	public synchronized State getState() {
		return state;
	}
	
	private synchronized void setState(State state) {
		if (state == this.state) {
			return;
		}
		
		Log.i(TAG, "New state: " + state);
		
		this.state = state;
		
		client.onBluetoothStateChanged(state);
	}
	
}
