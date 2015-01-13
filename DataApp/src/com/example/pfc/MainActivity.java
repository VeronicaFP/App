package com.example.pfc;

import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.Toast;

import com.androidplot.Plot;
import com.androidplot.util.Redrawer;
import com.androidplot.xy.*;

import java.text.DecimalFormat;
import java.util.Arrays;

public class MainActivity extends Activity {

	// Intent request codes
	private static final int REQUEST_CONNECT_DEVICE = 1;
	private static final int REQUEST_ENABLE_BT = 2;

	// Buttons
	private Button startButton, stopButton;

	// Name of connected device
	private String mConnectedDeviceName = null;

	/**
	 * Set to true to add debugging
	 */
	private static final boolean DEBUG = true;
	/**
	 * Tag when logging
	 */
	public static final String LOG_TAG = "MainActivity";

	// Message types sent form the BluetoothSerialService Handler
	public static final int MESSAGE_STATE_CHANGE = 1;
	public static final int MESSAGE_READ = 2;
	public static final int MESSAGE_WRITE = 3;
	public static final int MESSAGE_DEVICE_NAME = 4;
	public static final int MESSAGE_TOAST = 5;

	// Key names received from the BluetoothSerialService Handler
	public static final String DEVICE_NAME = "device_name";
	public static final String TOAST = "toast";

	private BluetoothAdapter mBluetoothAdapter = null;
	private ProcessData mProcessData;
	private static BluetoothSerialService mSerialService = null;

	private static final int HISTORY_SIZE = 300; // number of points to plot in
													// history

	private XYPlot xyzLevelsPlot = null;
	private XYPlot xyzHistoryPlot = null;

	// private SimpleXYSeries aprLevelsSeries = null;
	private SimpleXYSeries xLvlSeries;
	private SimpleXYSeries yLvlSeries;
	private SimpleXYSeries zLvlSeries;
	private SimpleXYSeries xHistorySeries = null;
	private SimpleXYSeries yHistorySeries = null;
	private SimpleXYSeries zHistorySeries = null;

	private Redrawer redrawer;

	/** Called when the activity is first created. */
	@Override
	public void onCreate(Bundle savedInstanceState) {
		if (DEBUG)
			Log.e(LOG_TAG, "onCreate()");
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_main);
		setPlots();
		findViews();
		mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
		if (mBluetoothAdapter == null) {
			Log.e(LOG_TAG, "Bluetooth is not supported");
			Toast.makeText(getApplicationContext(),
					"Bluetooth is not supported", Toast.LENGTH_LONG).show();
			finish();
			return;
		}
		mProcessData = new ProcessData(this);
		mSerialService = new BluetoothSerialService(this, mHandlerBT,
				mProcessData);

	}

	@Override
	public void onStart() {
		super.onStart();
		if (DEBUG)
			Log.e(LOG_TAG, "onStart()");
		if ((mBluetoothAdapter != null) && !(mBluetoothAdapter.isEnabled())) {
			Intent enableIntent = new Intent(
					BluetoothAdapter.ACTION_REQUEST_ENABLE);
			startActivityForResult(enableIntent, REQUEST_ENABLE_BT);
		}
	}

	@Override
	public void onResume() {
		super.onResume();
		if (DEBUG)
			Log.e(LOG_TAG, "onResume()");
		if (mSerialService != null) {
			// Only if the state is STATE_NONE, do we know that we haven't
			// starte already
			if (mSerialService.getState() == BluetoothSerialService.STATE_NONE) {
				mSerialService.start();
			}
		}
		redrawer.start();
	}

	@Override
	public void onPause() {
		redrawer.pause();
		super.onPause();
		if (DEBUG)
			Log.e(LOG_TAG, "onPause()");

	}

	@Override
	public void onDestroy() {
		redrawer.finish();
		super.onDestroy();
		if (DEBUG)
			Log.e(LOG_TAG, "onDestroy()");
		if (mSerialService != null) {
			mSerialService.stop();
		}

	}

	@Override
	public void onStop() {
		super.onStop();
		if (DEBUG)
			Log.e(LOG_TAG, "onStop()");

	}

	/**
	 *  Find Views
	 */
	private void findViews() {
		startButton = (Button) findViewById(R.id.buttonStart);
		startButton.setOnClickListener(new View.OnClickListener() {

			@Override
			public void onClick(View v) {
				// TODO Auto-generated method stub
				String message = "S";
				sendMessage(message);
			}
		});
		stopButton = (Button) findViewById(R.id.buttonStop);
		stopButton.setOnClickListener(new View.OnClickListener() {

			@Override
			public void onClick(View v) {
				// TODO Auto-generated method stub
				String message = "X";
				sendMessage(message);
			}
		});
		startButton.setEnabled(false);
		stopButton.setEnabled(false);
	}
	
	/**
	 * Send a message to the remote device
	 * @param message
	 */
	public void sendMessage(String message) {

		if (message.length() > 0) {
			byte[] send = message.getBytes();
			mSerialService.write(send);
		}
	}

	/**
	 * Set the plots
	 */
	private void setPlots() {
		// setup the XYZ Levels plot:
		xyzLevelsPlot = (XYPlot) findViewById(R.id.xyzLevelsPlot);
		xyzLevelsPlot.setDomainBoundaries(-2, 2, BoundaryMode.FIXED);
		xyzLevelsPlot.getGraphWidget().getDomainLabelPaint()
				.setColor(Color.TRANSPARENT);

		// Create series with his names
		xLvlSeries = new SimpleXYSeries("X");
		yLvlSeries = new SimpleXYSeries("Y");
		zLvlSeries = new SimpleXYSeries("Z");

		// Add series to the levels plot
		xyzLevelsPlot.addSeries(xLvlSeries,
				new BarFormatter(Color.rgb(200, 100, 100), Color.rgb(80, 0, 0)));
		xyzLevelsPlot.addSeries(yLvlSeries,
				new BarFormatter(Color.rgb(100, 200, 100), Color.rgb(0, 80, 0)));
		xyzLevelsPlot.addSeries(zLvlSeries,
				new BarFormatter(Color.rgb(100, 100, 200), Color.rgb(0, 0, 80)));

		xyzLevelsPlot.setDomainStepValue(1);
		xyzLevelsPlot.setTicksPerRangeLabel(1);

	
		// If we did not do this, the plot would auto-range which can be
		// visually confusing in the case of dynamic plots.
		xyzLevelsPlot.setRangeBoundaries(-1.2, 1.5, BoundaryMode.FIXED);

		// update our domain and range axis labels:
		xyzLevelsPlot.setDomainLabel("");
		xyzLevelsPlot.getDomainLabelWidget().pack();
		xyzLevelsPlot.setRangeLabel("Acc (m/s^2)");
		xyzLevelsPlot.getRangeLabelWidget().pack();
		xyzLevelsPlot.setGridPadding(10, 0, 10, 0);
		xyzLevelsPlot.setRangeValueFormat(new DecimalFormat("#.##"));

		// setup the XYZ History plot:
		xyzHistoryPlot = (XYPlot) findViewById(R.id.xyzHistoryPlot);

		xHistorySeries = new SimpleXYSeries("X");
		xHistorySeries.useImplicitXVals();
		yHistorySeries = new SimpleXYSeries("Y");
		yHistorySeries.useImplicitXVals();
		zHistorySeries = new SimpleXYSeries("Z");
		zHistorySeries.useImplicitXVals();

		xyzHistoryPlot.setRangeBoundaries(-1.2, 1.5, BoundaryMode.FIXED);
		xyzHistoryPlot.setDomainBoundaries(0, HISTORY_SIZE, BoundaryMode.FIXED);
		xyzHistoryPlot.addSeries(xHistorySeries, new LineAndPointFormatter(
				Color.rgb(200, 100, 100), null, null, null));
		xyzHistoryPlot.addSeries(yHistorySeries, new LineAndPointFormatter(
				Color.rgb(100, 200, 100), null, null, null));
		xyzHistoryPlot.addSeries(zHistorySeries, new LineAndPointFormatter(
				Color.rgb(100, 100, 200), null, null, null));
		xyzHistoryPlot.setDomainStepMode(XYStepMode.INCREMENT_BY_VAL);
		xyzHistoryPlot.setDomainStepValue(HISTORY_SIZE / 10);
		xyzHistoryPlot.setTicksPerRangeLabel(1);
		xyzHistoryPlot.setDomainLabel("Sample Index");
		xyzHistoryPlot.getDomainLabelWidget().pack();
		xyzHistoryPlot.setRangeLabel("Acc (m/s2)");
		xyzHistoryPlot.getRangeLabelWidget().pack();

		xyzHistoryPlot.setRangeValueFormat(new DecimalFormat("#.##"));
		xyzHistoryPlot.setDomainValueFormat(new DecimalFormat("###"));

		// get a ref to the BarRenderer so we can make some changes to it:
		BarRenderer barRenderer = (BarRenderer) xyzLevelsPlot
				.getRenderer(BarRenderer.class);
		if (barRenderer != null) {
			// make our bars a little thicker than the default so they can be
			// seen better:
			barRenderer.setBarWidth(25);
		}

		redrawer = new Redrawer(Arrays.asList(new Plot[] { xyzHistoryPlot,
				xyzLevelsPlot }), 100, false);
	}

	/**
	 * Serial service connection state
	 * @return state
	 */
	public int getConnectionState() {
		return mSerialService.getState();
	}


	/**
	 * The Handler that get information back form the BluetoothSerialService
	 */
	private final Handler mHandlerBT = new Handler() {

		@Override
		public void handleMessage(Message msg) {
			switch (msg.what) {
			case MESSAGE_STATE_CHANGE:
				if (DEBUG)
					Log.i(LOG_TAG, "MESSAGE_STATE_CHANGE: " + msg.arg1);
				switch (msg.arg1) {
				case BluetoothSerialService.STATE_CONNECTED:
					break;
				case BluetoothSerialService.STATE_CONNECTING:
					break;
				case BluetoothSerialService.STATE_LISTEN:
					break;
				case BluetoothSerialService.STATE_NONE:
					break;
				}
				break;
			case MESSAGE_WRITE:
				break;
			case MESSAGE_READ:
				if (DEBUG)
					Log.i(LOG_TAG, "Message read");
				// resolveData(msg);
				break;
			case MESSAGE_DEVICE_NAME:
				mConnectedDeviceName = msg.getData().getString(DEVICE_NAME);
				Toast.makeText(getApplicationContext(),
						"Connected to: " + mConnectedDeviceName,
						Toast.LENGTH_SHORT).show();
				startButton.setEnabled(true);
				stopButton.setEnabled(true);
				break;
			case MESSAGE_TOAST:
				Toast.makeText(getApplicationContext(),
						msg.getData().getString(TOAST), Toast.LENGTH_SHORT)
						.show();
				break;

			}
		}
	};


	public void onActivityResult(int requestCode, int resultCode, Intent data) {
		super.onActivityResult(requestCode, resultCode, data);
		if (DEBUG)
			Log.d(LOG_TAG, "onActivityResult " + resultCode);
		switch (requestCode) {
		case REQUEST_CONNECT_DEVICE:
			// When DeviceListActivity returns with a device to connect
			if (resultCode == Activity.RESULT_OK) {
				// Get the device MAC address
				String address = data.getExtras().getString(
						DeviceListActivity.EXTRA_DEVICE_ADDRESS);
				// Get the bluetoothDevice object
				BluetoothDevice device = mBluetoothAdapter
						.getRemoteDevice(address);
				// Attempt to connect to the device
				mSerialService.connect(device);
			}
			break;
		case REQUEST_ENABLE_BT:
			// When the request to enable Bluetooth returns
			if (resultCode == Activity.RESULT_OK) {
				// Bluetooth is now enabled
				Log.d(LOG_TAG, "BT enabled");
				Toast.makeText(this, "Bluetooth Enabled", Toast.LENGTH_SHORT)
						.show();

			} else {
				// User did not enable Bluetooth or an error ocurred
				Log.d(LOG_TAG, "BT not enabled");
				Toast.makeText(getApplicationContext(),
						"Bluetooth is not enabled, leaving", Toast.LENGTH_SHORT)
						.show();
				finish();
			}
		}
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		// Infalte the menu; this adds itemas to the action bar
		getMenuInflater().inflate(R.menu.main, menu);
		return true;
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		// Handle action bar item clicks here. The action bar will automatically
		// handle clicks on the Home/Up button, so long as you specify a parent
		// activity in AndroidManifest.xml
		int id = item.getItemId();
		if (id == R.id.connect) {
			Intent scanIntent = new Intent(this, DeviceListActivity.class);
			startActivityForResult(scanIntent, REQUEST_CONNECT_DEVICE);
			return true;
		}
		return super.onOptionsItemSelected(item);
	}

	/**
	 * Represent the sensor values
	 * @param X acc X axe
	 * @param Y acc Y axe
	 * @param Z acc Z axe
	 */
	public synchronized void paintData(double X, double Y, double Z) {

		// update level data:
		xLvlSeries.setModel(Arrays.asList(new Number[] { X }),
				SimpleXYSeries.ArrayFormat.Y_VALS_ONLY);

		yLvlSeries.setModel(Arrays.asList(new Number[] { Y }),
				SimpleXYSeries.ArrayFormat.Y_VALS_ONLY);

		zLvlSeries.setModel(Arrays.asList(new Number[] { Z }),
				SimpleXYSeries.ArrayFormat.Y_VALS_ONLY);

		// get rid the oldest sample in history:
		if (zHistorySeries.size() > HISTORY_SIZE) {
			zHistorySeries.removeFirst();
			yHistorySeries.removeFirst();
			xHistorySeries.removeFirst();
		}

		// add the latest history sample:
		xHistorySeries.addLast(null, X);
		yHistorySeries.addLast(null, Y);
		zHistorySeries.addLast(null, Z);
	}

}

class ProcessData {
	public static final int UPDATE = 1;
	private ByteQueue mByteQueue;
	private Processor mProcessor;
	private MainActivity mMainActivity;
	private byte[] mReceiveBuffer;

	/**
	 * Constructor
	 * @param mainActivity
	 */
	public ProcessData(MainActivity mainActivity) {
		mMainActivity = mainActivity;
		mReceiveBuffer = new byte[4 * 1024];
		mByteQueue = new ByteQueue(4 * 1024);
		mProcessor = new Processor(mMainActivity);
	}

	private final Handler mHandler = new Handler() {
		/**
		 * Handler the callback message
		 */
		@Override
		public void handleMessage(Message msg) {
			if (msg.what == UPDATE) {
				update();
			}
		}
	};

	/**
	 * 
	 * @param buffer
	 * @param length
	 */
	public void write(byte[] buffer, int length) {
		try {
			mByteQueue.write(buffer, 0, length);
		} catch (InterruptedException e) {
		}
		mHandler.sendMessage(mHandler.obtainMessage(UPDATE));
	}

	/**
	 * Look for new input
	 */
	private void update() {
		int bytesAvailable = mByteQueue.getBytesAvailable();
		int bytesToRead = Math.min(bytesAvailable, mReceiveBuffer.length);
		try {
			int bytesRead = mByteQueue.read(mReceiveBuffer, 0, bytesToRead);
			mProcessor.append(mReceiveBuffer, 0, bytesRead);

		} catch (InterruptedException e) {
		}
	}

	public void startProcess() {
		mProcessor.start();
	}

}

/**
 * A multi-thread-safe produce-consumer byte array. Only allows one producer and
 * one consumer.
 */
class ByteQueue {
	private byte[] mBuffer;
	private int mStoredBytes;
	private int mHead;

	public ByteQueue(int size) {
		mBuffer = new byte[size];
	}

	public int read(byte[] buffer, int offset, int length)
			throws InterruptedException {
		if (length + offset > buffer.length) {
			throw new IllegalArgumentException(
					"length + offset > buffer.length");
		}
		if (length < 0) {
			throw new IllegalArgumentException("length<0");
		}
		if (length == 0) {
			return 0;
		}
		synchronized (this) {
			while (mStoredBytes == 0) {
				wait();
			}
			int totalRead = 0;
			int bufferLength = mBuffer.length;
			boolean wasFull = mStoredBytes == bufferLength;
			while (length > 0 && mStoredBytes > 0) {
				int oneRun = Math.min(bufferLength - mHead, mStoredBytes);
				int bytesToCopy = Math.min(length, oneRun);
				System.arraycopy(mBuffer, mHead, buffer, offset, bytesToCopy);
				mHead += bytesToCopy;
				if (mHead >= bufferLength) {
					mHead = 0;
				}
				offset += bytesToCopy;
				mStoredBytes -= bytesToCopy;
				length -= bytesToCopy;
				totalRead += bytesToCopy;
			}
			if (wasFull) {
				notify();
			}
			return totalRead;
		}
	}

	public int getBytesAvailable() {
		synchronized (this) {
			return mStoredBytes;
		}
	}

	public void write(byte[] buffer, int offset, int length)
			throws InterruptedException {
		if (length + offset > buffer.length) {
			throw new IllegalArgumentException(
					"length + offset > buffer.length");
		}
		if (length < 0) {
			throw new IllegalArgumentException("length<0");
		}
		if (length == 0) {
			return;
		}
		synchronized (this) {

			int bufferLength = mBuffer.length;
			boolean wasEmpty = mStoredBytes == 0;
			while (length > 0) {
				while (bufferLength == mStoredBytes) {
					wait();
				}
				int tail = mHead + mStoredBytes;
				int oneRun;
				if (tail >= bufferLength) {
					tail = tail - bufferLength;
					oneRun = mHead - tail;
				} else {
					oneRun = bufferLength - tail;
				}
				int bytesToCopy = Math.min(oneRun, length);
				System.arraycopy(buffer, offset, mBuffer, tail, bytesToCopy);
				offset += bytesToCopy;
				mStoredBytes += bytesToCopy;
				length -= bytesToCopy;

			}
			if (wasEmpty) {
				notify();
			}
		}
	}
}

class Processor {
	private int mProcessedCharCount;
	private MainActivity mMainActivity;
	private int FLAG;
	private static final int FLAGX = 1;
	private static final int FLAGY = 2;
	private static final int FLAGZ = 3;
	private double g = 1;
	//private double g = 9.80665; // acceleración gravedad
	private double as = (double) 3.8 / 1000; // sensitividad
	private double dt = 0.0005; // periodo de muestreo
	private double alpha = 0.05; // valor filtro

	private byte[] byteX = new byte[6], byteY = new byte[6],
			byteZ = new byte[6];
	private int digit = 0;
	private int X, Y, Z;
	private int X_1, Y_1, Z_1;
	private double dX,dY,dZ,dX_1,dY_1,dZ_1;
	private int pos = 0;

	public Processor(MainActivity mainActivity) {
		mMainActivity = mainActivity;
	}

	private double convert(int data) {
		double out;
		out = (double) data * g * as;
		//out =(double)data*as;
		return out;
	}
	
	private double filtro(int value1, int value2) {

		double v = convert(value1);
		double v_1 = convert(value2);
		double out = (1 - alpha) * v_1 + alpha * v;
		return out;
	}

	/**
	 * Accept bytes
	 * 
	 * @param buffer
	 *            a byte array containing the bytes to be processed
	 * @param base
	 *            the first index of the array to process
	 * @param length
	 *            the number of bytes in the array to process
	 */
	public void append(byte[] buffer, int base, int length) {
		for (int i = 0; i < length; i++) {
			byte b = buffer[base + i];
			try {
				process(b);
				mProcessedCharCount++;
			} catch (Exception e) {
				Log.e(MainActivity.LOG_TAG,
						"Exception while processing character "
								+ Integer.toString(mProcessedCharCount)
								+ " code " + Integer.toString(b), e);
			}
		}

	}

	private void process(byte b) {
		switch (b) {
		case 0:
			break;
		case '#': // start
			if (pos < 5)
				pos ++;
			break;
		case '+': // next
			processPlus();
			break;
		case '!': // end
			processEnd();
			break;
		case 'X':
			FLAG = FLAGX;
			digit = 0;
			break;
		case 'Y':
			FLAG = FLAGY;
			digit = 0;
			break;
		case 'Z':
			FLAG = FLAGZ;
			digit = 0;
			break;
		default:
			processNumber(b);
			break;

		}

	}

	private void processNumber(byte b) {
		switch (FLAG) {
		case FLAGX:
			byteX[digit] = b;
			digit++;
			break;
		case FLAGY:
			byteY[digit] = b;
			digit++;
			break;
		case FLAGZ:
			byteZ[digit] = b;
			digit++;
			break;
		}
	}

	private void processPlus() {
		byte[] value = new byte[digit];
		String valueS;
		switch (FLAG) {
		case FLAGX:
			System.arraycopy(byteX, 0, value, 0, digit);
			valueS = new String(value, 0, digit);
			X_1 = X;
			X = Integer.parseInt(valueS);
			break;
		case FLAGY:
			System.arraycopy(byteY, 0, value, 0, digit);
			valueS = new String(value, 0, digit);
			Y_1 = Y;
			Y = Integer.parseInt(valueS);
			break;
		case FLAGZ:
			System.arraycopy(byteZ, 0, value, 0, digit);
			valueS = new String(value, 0, digit);
			Z_1 = Z;
			Z = Integer.parseInt(valueS);
			break;
		}
	}


	private void processEnd(){
		switch(pos){
		case 1:
			break;
		case 2:
			dX = filtro(X, X_1);
			dY = filtro(Y, Y_1);
			dZ = filtro(Z, Z_1);
			break;
		default:
			dX_1 = dX;
			dY_1 = dY;
			dZ_1 = dZ;
			dX = filtro2(X,dX_1);
			dY = filtro2(Y,dY_1);
			dZ = filtro2(Z,dZ_1);
			mMainActivity.paintData(dX, dY, dZ);
			break;
		}
			
	}
	private double filtro2(int value,double value_1){
		double v = convert(value);
		double out = (1-alpha)*value_1+alpha*v;
		return out;
	}
	public void start(){
		pos = 0;
	}
}