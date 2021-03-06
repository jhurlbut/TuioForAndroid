package com.sigmusic.tacchi.tuio;

import java.lang.reflect.Method;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedList;
import java.util.Queue;

import TUIO.TuioClient;
import TUIO.TuioContainer;
import TUIO.TuioCursor;
import TUIO.TuioListener;
import TUIO.TuioObject;
import TUIO.TuioTime;
import android.os.Build;
import android.util.Log;
import android.view.MotionEvent;

public class TuioAndroidClient implements TuioListener {
	public final static String TAG = "TuioAndroidClient";
	TuioClient client;
	TuioService callback;
	int width, height;
	int port;
	
	MotionEvent currentEvent;
	
	Queue<MotionEvent> events = new LinkedList<MotionEvent>();
	
	public static final int NUM_SAMPLE_DATA = getNumSampleData();
	static int getNumSampleData() {
		try {
			return MotionEvent.class.getField("NUM_SAMPLE_DATA").getInt(null);
		} catch (Exception e) {
			e.printStackTrace();
			return 4;
		}
	}
	public static final Method obtainNano = getObtainNano();
	static Method getObtainNano() {
		try {
			if (Build.VERSION.SDK_INT < 10)
				return MotionEvent.class.getMethod("obtainNano", long.class, long.class, long.class,
			            int.class, int.class, int[].class, float[].class, int.class,
			            float.class, float.class, int.class, int.class);
			else 
				return MotionEvent.class.getMethod("obtain", long.class, long.class, long.class,
			            int.class, int.class, int[].class, float[].class, int.class,
			            float.class, float.class, int.class, int.class);
		} catch (Exception e) {
			e.printStackTrace();
			return null;
		}
	}
	public static final Method addBatch = getAddBatch();
	static Method getAddBatch() {
		try {
		return MotionEvent.class.getMethod("addBatch", long.class, float[].class, int.class);
		} catch (Exception e) {
			e.printStackTrace();
			return null;
		}
	}

	
	
	public TuioAndroidClient(TuioService callback,int port, int width, int height) {
		client = new TuioClient(port);
		client.addTuioListener(this);
		this.callback = callback;
		this.port = port;
		this.width = width;
		this.height = height;
	}
	
	private static boolean running = true;
	private class EventSender extends Thread {
		public void run() {
			while (running) {
				int eventssize = 0;
				synchronized(events) {
					eventssize = events.size();
					if (eventssize > 0) {
						callback.sendMotionEvent(events.remove());
					} 	
				}
				if (eventssize <= 0) {
//						Log.d(TAG, "Sleepinf");
					try {
						Thread.sleep(50);
					} catch (InterruptedException e) {
						e.printStackTrace();
					}
				}
			}
		}
	}
	
	private void sendUpdateEvent(MotionEvent update) {
//		if (update == null) return;
//		synchronized(events) {
//			events.add(update);
//		}
	}
	private void sendUpDownEvent(MotionEvent updownevent) {
//		if (updownevent == null) return;
//		synchronized(events) {
//			while (events.size() > 1 && events.peek().getAction() == MotionEvent.ACTION_MOVE) { //clear out all movement actions.
//				events.remove();
//			}
//			events.add(updownevent);
//		}
	}
	
	EventSender eventsender;
	
	public void addListener(TuioListener listener) {
		client.addTuioListener(listener);
	}
	
	public void cancelAll() {
		//TODO make this cancel everything
	}
	
	public void start() {
		Log.v(TAG, "Starting TUIO client on port:" +port);
		client.connect();
		eventsender = new EventSender();
		running = true;
		eventsender.start();
	}
	
	public void stop() {
		Log.v(TAG, "Stopping client");
		client.disconnect();
		running = false;
	}
	
	
	
	private int getNumCursors() {
		return client.getTuioCursors().size() + client.getTuioObjects().size();
	}
	private MotionEvent makeOrUpdateMotionEvent(TuioContainer point, long id, int action) {
		long startms = point.getStartTime().getTotalMilliseconds();
		long totalms = point.getTuioTime().getTotalMilliseconds();
		int totalcursors = getNumCursors();
		if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_POINTER_UP) {
			totalcursors -= 1;
			if (totalcursors <= 0) { //no cursors is a special case
				id = -1;
				totalcursors = 1;
				action = MotionEvent.ACTION_UP; //no more ACTION_POINTER_UP. need to be serious.
			}
		}
		int actionmasked = action ;//|( 0x1 << (7+id) & 0xff00);
		
		Log.d("TuioEvent", "TuioContainer: "+point.toString()+" startms: "+startms+" totalms: "+totalms+" cursors: "+totalcursors+" id: "+id+ " action: "+action+" Action masked: "+actionmasked);
		
	
		
		int[] pointerIds = new int[totalcursors];
		int i =0;
		float[] inData = new float[totalcursors * NUM_SAMPLE_DATA]; // 4 = NUM_SAMPLE_DATA;
		for (TuioObject obj : client.getTuioObjects()) {
			if ((action != MotionEvent.ACTION_UP && action != MotionEvent.ACTION_POINTER_UP)  || obj.getSessionID() != id) { //we need to get rid of the removed cursor.
				pointerIds[i] = obj.getSymbolID();
				
				inData[i*NUM_SAMPLE_DATA] = obj.getScreenX(width);
				inData[i*NUM_SAMPLE_DATA+1] = obj.getScreenY(height);
				inData[i*NUM_SAMPLE_DATA+2] = 1;//pressure
				inData[i*NUM_SAMPLE_DATA+3] = 0.1f; //size
				i++;
			}
		}
		Collections.sort(client.getTuioCursors(), new Comparator<TuioCursor>() {
			public int compare(TuioCursor object1, TuioCursor object2) {
				if (object1.getCursorID() > object2.getCursorID()) {
					return 1;
				} else if (object1.getCursorID() <  object2.getCursorID()) {
					return -1;
				}
				return 0;
			}});
		for (TuioCursor obj : client.getTuioCursors()) {
			if ((action != MotionEvent.ACTION_UP && action != MotionEvent.ACTION_POINTER_UP) || obj.getSessionID() != id) { //we need to get rid of the removed cursor.
				pointerIds[i] = i; //obj.getCursorID();
				inData[i*NUM_SAMPLE_DATA] = obj.getScreenX(width);
				inData[i*NUM_SAMPLE_DATA+1] = obj.getScreenY(height);
				inData[i*NUM_SAMPLE_DATA+2] = 1;//pressure
				inData[i*NUM_SAMPLE_DATA+3] = 0.1f; //size
				i++;
			}

		}
		
		try {
			if (action == MotionEvent.ACTION_MOVE) {
				if (currentEvent.getPointerCount() == totalcursors) {
					synchronized(events) {
						if (events.size() < 1) { //if our queue is empty
							currentEvent.setAction(MotionEvent.ACTION_MOVE);
							addBatch.invoke(currentEvent, totalms, inData, 0);
							events.add(currentEvent);
						}
					}
				}
				else {
					currentEvent = (MotionEvent)obtainNano.invoke(null, startms, totalms, System.nanoTime(), actionmasked, totalcursors, pointerIds, inData, 0, 0, 0, 0, 0);
	//				return null; //EEK! something happened in the middle of us doing stuff.
					synchronized(events) {
						if (currentEvent != null)
							events.add(currentEvent);
					}
				}
			}
			else if (action == MotionEvent.ACTION_UP && totalcursors <= 1) {
	//			currentEvent.setAction(actionmasked); //nope that didn't fix it
				totalcursors = 0;
				currentEvent = (MotionEvent)obtainNano.invoke(null,startms, totalms, System.nanoTime(), actionmasked, totalcursors, pointerIds, inData, 0, 0, 0, 0, 0);
				synchronized(events) {
					if (currentEvent != null)
						events.add(currentEvent);
				}
			
			} else {
				currentEvent = (MotionEvent)obtainNano.invoke(null,startms, totalms, System.nanoTime(), actionmasked, totalcursors, pointerIds, inData, 0, 0, 0, 0, 0);
				synchronized(events) {
					if (currentEvent != null)
						events.add(currentEvent);
				}
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
		
		
		
		
		return currentEvent;
	}
	
	

	@Override
	public void addTuioCursor(TuioCursor tcur) {
		Log.d(TAG, "Cursor down: "+tcur.toString());
		addTuioThing(tcur, tcur.getSessionID());
	}

	@Override
	public void addTuioObject(TuioObject tobj) {
		Log.d(TAG, "Fiducial down: "+tobj.toString());
		addTuioThing(tobj, tobj.getSessionID());
	}
	
	
	private void addTuioThing(TuioContainer point, long id) {
//		Log.d(TAG, "forwarding");
		int event = (id == id) ? MotionEvent.ACTION_DOWN : MotionEvent.ACTION_POINTER_DOWN;
		MotionEvent me = makeOrUpdateMotionEvent(point, id, event);
//		callback.sendMotionEvent(me);
		this.sendUpDownEvent(me);
	}
	

	@Override
	public void updateTuioCursor(TuioCursor tcur) {
		Log.d(TAG, "Cursor moved: "+tcur.toString());
		updateTuioThing(tcur, tcur.getSessionID());
	}

	@Override
	public void updateTuioObject(TuioObject tobj) {
		Log.d(TAG, "fiducial moved: "+tobj.toString());
		updateTuioThing(tobj, tobj.getSessionID());
	}
	
	private void updateTuioThing(TuioContainer point, long id) {
		int event = MotionEvent.ACTION_MOVE;
		MotionEvent me = makeOrUpdateMotionEvent(point, id, event);
//		callback.sendMotionEvent(me);
		this.sendUpdateEvent(me);

	}

	@Override
	public void removeTuioCursor(TuioCursor tcur) {
		Log.d(TAG, "Cursor up: "+tcur.toString());
		removeTuioThing(tcur, tcur.getSessionID());
	}

	@Override
	public void removeTuioObject(TuioObject tobj) {
		Log.d(TAG, "Fiducal up: "+tobj.toString());
		removeTuioThing(tobj, tobj.getSessionID());
	}
	
	private void removeTuioThing(TuioContainer point, long id) {
//		Log.d(TAG, "forwarding");
		int event = (id == 0) ? MotionEvent.ACTION_UP : MotionEvent.ACTION_POINTER_UP;
		MotionEvent me = makeOrUpdateMotionEvent(point, id, event);
		//callback.sendMotionEvent(me);
		this.sendUpDownEvent(me);
	}


	@Override
	public void refresh(TuioTime ftime) {		
	}


}
