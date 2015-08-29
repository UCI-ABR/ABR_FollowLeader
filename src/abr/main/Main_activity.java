package abr.main;

import java.util.List;

import org.opencv.android.CameraBridgeViewBase;
import org.opencv.android.OpenCVLoader;
import org.opencv.android.CameraBridgeViewBase.CvCameraViewFrame;
import org.opencv.android.CameraBridgeViewBase.CvCameraViewListener2;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.MatOfPoint;
import org.opencv.core.Scalar;
import org.opencv.imgproc.Imgproc;

import ioio.lib.util.IOIOLooper;
import ioio.lib.util.IOIOLooperProvider;
import ioio.lib.util.android.IOIOAndroidApplicationHelper;
import android.media.AudioManager;
import android.media.ToneGenerator;
import android.os.Bundle;
import android.app.Activity;
import android.content.Intent;
import android.util.Log;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;
import app.main.follow2.R;

public class Main_activity extends Activity implements IOIOLooperProvider,
		CvCameraViewListener2 // implements IOIOLooperProvider: from IOIOActivity
{
	//variables for connection with ioio
	private final IOIOAndroidApplicationHelper helper_ = new IOIOAndroidApplicationHelper(
			this, this); // from IOIOActivity
	IOIO_thread m_ioio_thread;
	
	//variables and setup for opencv
	private CameraBridgeViewBase mOpenCvCameraView;
	private Mat mRgba;
	private Scalar mBlobColorRgba;
	private ColorBlobDetector mDetector;
	private Mat mSpectrum;
	private Scalar CONTOUR_COLOR;
	
	static {
		if (!OpenCVLoader.initDebug()) {
			// Handle initialization error
		}
	}
	
	//toggle on/off for autoMode
	public boolean autoMode;
	
	//ui variables
	TextView sonar1Text;
	TextView sonar2Text;
	TextView sonar3Text;
	TextView distanceText;
	TextView bearingText;
	TextView headingText;
	
	//pan and tilt variables
	int panVal=1500;
	int tiltVal=1500;
	boolean panningRight = false;
	boolean tiltingUp = false;
	int panInc;
	int tiltInc;
	
	//obstacle avoidance variable
	boolean avoidingObstacle;
	
	//indoor/outdoor testing, pwm values from 1000 to 2000
	boolean indoors = false;
	int indoor_forward_speed = 50;
	int indoor_turn_speed = 50;
	int outdoor_forward_speed = 100;
	int outdoor_turn_speed = 80;
	int forward_speed = 0;
	int turn_speed = 0;
	
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		
		requestWindowFeature(Window.FEATURE_NO_TITLE);
		getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
		setContentView(R.layout.main);
		helper_.create(); // from IOIOActivity

		mOpenCvCameraView = (CameraBridgeViewBase) findViewById(R.id.color_blob_detection_activity_surface_view);
		mOpenCvCameraView.setCvCameraViewListener(this);
		mOpenCvCameraView.enableView();
		
		//set indoor/outdoor
		if(indoors){
			forward_speed = indoor_forward_speed;
			turn_speed = indoor_turn_speed;
		} else{
			forward_speed = outdoor_forward_speed;
			turn_speed = outdoor_turn_speed;
		}
		
		//add functionality to autoMode button
		Button buttonAuto = (Button) findViewById(R.id.btnAuto);
		buttonAuto.setOnClickListener(new OnClickListener() {
			public void onClick(View v) {
				if (!autoMode) {
					v.setBackgroundResource(R.drawable.button_auto_on);
					autoMode = true;
				} else {
					v.setBackgroundResource(R.drawable.button_auto_off);
					m_ioio_thread.move(1500);
					m_ioio_thread.turn(1500);
					m_ioio_thread.pan(1500);
					m_ioio_thread.tilt(1500);
					panVal = 1500;
					tiltVal = 1500;
					autoMode = false;
				}
			}
		});
		
		//set starting autoMode button color
		if (autoMode) {
			buttonAuto.setBackgroundResource(R.drawable.button_auto_on);
		} else {
			buttonAuto.setBackgroundResource(R.drawable.button_auto_off);
		}

		//initialize textviews
		sonar1Text = (TextView) findViewById(R.id.sonar1);
		sonar2Text = (TextView) findViewById(R.id.sonar2);
		sonar3Text = (TextView) findViewById(R.id.sonar3);
		
		//set initial pan/tilt values
		panVal = 1500;
		tiltVal = 1500;
		panningRight = true;
		tiltingUp = true;
		
		avoidingObstacle = false;
	}
	
	@Override
	public void onResume() {
	    super.onResume();
	    if (mOpenCvCameraView != null)
			mOpenCvCameraView.enableView();
	}
	
	@Override
	public void onPause() {
		super.onPause();
		if (mOpenCvCameraView != null)
			mOpenCvCameraView.disableView();
	}

	public void onCameraViewStarted(int width, int height) {
		mRgba = new Mat(height, width, CvType.CV_8UC4);
		mDetector = new ColorBlobDetector();
		mSpectrum = new Mat();
		mBlobColorRgba = new Scalar(255);
		CONTOUR_COLOR = new Scalar(255, 255, 0, 255);

		//mDetector.setHsvColor(new Scalar(7, 196, 144)); //construction paper red
		//mDetector.setHsvColor(new Scalar(7.015625,255.0,239.3125)); //bucket orange
		mDetector.setHsvColor(new Scalar(1.0,244.828125,205)); //box red

		//mDetector.setHsvColor(new Scalar(8.0,195.21875,222.140625)); // ball orange
		//mDetector.setHsvColor(new Scalar(252.46875,219.15625,90.6875)); // cup red
		//mDetector.setHsvColor(new Scalar(108.0625,246.59375,170.734375)); //sparx green
		//mDetectorGreen.setHsvColor(new Scalar(58.09375,218.9375,107.75)); //medium aldrich green
	}

	public void onCameraViewStopped() {
		mRgba.release();
	}

	public Mat onCameraFrame(CvCameraViewFrame inputFrame) {
		mRgba = inputFrame.rgba();

		setText("sonar1: "+m_ioio_thread.get_sonar1_reading(), sonar1Text);
		setText("sonar2: "+m_ioio_thread.get_sonar2_reading(), sonar2Text);
		setText("sonar3: "+m_ioio_thread.get_sonar3_reading(), sonar3Text);
		
		mDetector.process(mRgba);
		List<MatOfPoint> contours = mDetector.getContours();
		Imgproc.drawContours(mRgba, contours, -1, CONTOUR_COLOR);
		
		Mat colorLabel = mRgba.submat(4, 68, 4, 68);
		colorLabel.setTo(mBlobColorRgba);

		Mat spectrumLabel = mRgba.submat(4, 4 + mSpectrum.rows(), 70,
				70 + mSpectrum.cols());
		mSpectrum.copyTo(spectrumLabel);
		

		if (autoMode) { // only move if autoMode is on
			//adjust pan/tilt
			double momentX = mDetector.getMomentX();
			double momentY = mDetector.getMomentY();
			
			if(mDetector.blobsDetected() == 0){
				if(panningRight){
					m_ioio_thread.pan(panVal+=30);
					if(panVal >= 2400)
						panningRight = false;
				} else {
					m_ioio_thread.pan(panVal-=30);
					if(panVal <= 600)
						panningRight = true;
				}
				/*
				if(tiltingUp){
					m_ioio_thread.tilt(tiltVal+=30);
					if(tiltVal >= 2000)
						tiltingUp = false;
				} else {
					m_ioio_thread.tilt(tiltVal-=30);
					if(tiltVal <= 1000)
						tiltingUp = true;
				}
				*/
			} else {
				panInc = 40 + (int)Math.exp(.03*Math.abs(momentX));
				if(momentX > 25){
					m_ioio_thread.pan(panVal+=panInc);
				}
				else if(momentX < -25){
					m_ioio_thread.pan(panVal-=panInc);
				}
				tiltInc = 20 + (int)Math.exp(.03*Math.abs(momentY));
				if(momentY > 25){
					m_ioio_thread.tilt(tiltVal+=tiltInc);
				}
				else if(momentY < -25){
					m_ioio_thread.tilt(tiltVal-=tiltInc);
				}
			}
			
			if(panVal > 2400) panVal = 2400;
			if(panVal < 600) panVal = 600;
			if(tiltVal > 2000) tiltVal = 2000;
			if(tiltVal < 1000) tiltVal = 1000;

			//move
			//handle obstacles
			int sonar1 = m_ioio_thread.get_sonar1_reading();
			int sonar2 = m_ioio_thread.get_sonar2_reading();
			int sonar3 = m_ioio_thread.get_sonar3_reading();
			
			if (sonar2 < 30){
				if(sonar2 < 20 && mDetector.getMaxArea() > .05 * 4 * mDetector.getCenterX() * mDetector.getCenterY()){
					m_ioio_thread.turn(1500);
					m_ioio_thread.move(1500-forward_speed);
				}else{
					avoidingObstacle = true;
				}
			}
			else if(avoidingObstacle){
				if(sonar2 < 30){
					if(sonar1 > sonar3){
						m_ioio_thread.turn(1500-80);
					} else {
						m_ioio_thread.turn(1500+80);
					}
				} else {
					m_ioio_thread.move(1500+forward_speed);
				}
				if(sonar1 >= 20 && sonar2 >= 20 && sonar3 >= 20){
					avoidingObstacle = false;
				}
					
			}
			//follow blob
			else {
				if (mDetector.blobsDetected() > 0) {
					if(!(panVal < 1650 && panVal > 1250)){
						m_ioio_thread.move(1500+forward_speed);
						if(panVal>1500){
							m_ioio_thread.turn(1500+turn_speed);
						}
						else {
							m_ioio_thread.turn(1500-turn_speed);
						}
					}
					else{
						m_ioio_thread.turn(1500);
						m_ioio_thread.move(1500+forward_speed);
					}
				} else  {
					m_ioio_thread.turn(1500);
					m_ioio_thread.move(1500);
				}
			}
			
		}
		return mRgba;
	}
	  
	
	public void setText(final String str, final TextView tv) {
		runOnUiThread(new Runnable() {
			@Override
			public void run() {
				tv.setText(str);
			}
		});
	}
	/****************************************************** functions from IOIOActivity *********************************************************************************/

	/**
	 * Create the {@link IOIO_thread}. Called by the
	 * {@link IOIOAndroidApplicationHelper}. <br>
	 * Function copied from original IOIOActivity.
	 * 
	 * @see {@link #get_ioio_data()} {@link #start_IOIO()}
	 * */
	@Override
	public IOIOLooper createIOIOLooper(String connectionType, Object extra) {
		if (m_ioio_thread == null
				&& connectionType
						.matches("ioio.lib.android.bluetooth.BluetoothIOIOConnection")) {
			m_ioio_thread = new IOIO_thread(this);
			return m_ioio_thread;
		} else
			return null;
	}

	@Override
	protected void onDestroy() {
		super.onDestroy();
		//Log.i("activity lifecycle","main activity being destroyed");
		helper_.destroy();
		if (mOpenCvCameraView != null)
			mOpenCvCameraView.disableView();
	}

	@Override
	protected void onStart() {
		super.onStart();
		//Log.i("activity lifecycle","main activity starting");
		helper_.start();
	}

	@Override
	protected void onStop() {
		//Log.i("activity lifecycle","main activity stopping");
		helper_.stop();
		super.onStop();		
	}

	@Override
	protected void onNewIntent(Intent intent) {
		super.onNewIntent(intent);
			if ((intent.getFlags() & Intent.FLAG_ACTIVITY_NEW_TASK) != 0) {
			helper_.restart();
		}
	}
}
