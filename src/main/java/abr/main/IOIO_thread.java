package abr.main;

import android.util.Log;

import java.io.InputStream;
import java.io.OutputStream;

import ioio.lib.api.AnalogInput;
import ioio.lib.api.DigitalOutput;
import ioio.lib.api.PwmOutput;
import ioio.lib.api.exception.ConnectionLostException;
import ioio.lib.util.BaseIOIOLooper;

public class IOIO_thread extends BaseIOIOLooper
{
	private PwmOutput pwm_pan_output, pwm_tilt_output, pwm_turn_output, pwm_vel_output;

	InputStream in;
	OutputStream out;
	
	private AnalogInput sonar1,sonar2,sonar3;
	private int sonarPulseCounter;
	private DigitalOutput sonar_pulse;
	private int sonar1_reading, sonar2_reading, sonar3_reading;

	static final int DEFAULT_PWM = 1500, MAX_PWM = 2000, MIN_PWM = 1000;

	public IOIO_thread(Main_activity gui)
	{
		sonar1_reading = 1000;
		sonar2_reading = 1000;
		sonar3_reading = 1000;
		
		Log.e("abr controller", "IOIO thread creation");
	}

	@Override
	public void setup() throws ConnectionLostException
	{
		try 
		{
			pwm_vel_output = ioio_.openPwmOutput(3, 50);
			pwm_turn_output = ioio_.openPwmOutput(4, 50);
			pwm_pan_output = ioio_.openPwmOutput(5, 50);
			pwm_tilt_output = ioio_.openPwmOutput(6, 50);
			
			pwm_pan_output.setPulseWidth(1500);
			pwm_tilt_output.setPulseWidth(1500);
			pwm_vel_output.setPulseWidth(1500);
			pwm_turn_output.setPulseWidth(1500);
			

			sonarPulseCounter = 0;
			sonar1 = ioio_.openAnalogInput(42);
			sonar2 = ioio_.openAnalogInput(43);
			sonar3 = ioio_.openAnalogInput(44);
			sonar_pulse = ioio_.openDigitalOutput(40,false);
		} 
		catch (ConnectionLostException e){throw e;}
	}

	@Override
	public void loop() throws ConnectionLostException
	{	
		ioio_.beginBatch();
		
		try {
			if (sonarPulseCounter==5){
				sonar_pulse.write(true);
				float reading1 = sonar1.getVoltage();
				sonar1_reading = (int)(reading1/(float)(3.3/512)); //Only works for sonar with 3.3V input
				
				reading1 = sonar2.getVoltage();
				sonar2_reading = (int)(reading1/(float)(3.3/512)); //Only works for sonar with 3.3V input
				
				reading1 = sonar3.getVoltage();
				sonar3_reading = (int)(reading1/(float)(3.3/512)); //Only works for sonar with 3.3V input
				
				sonarPulseCounter = 0;
				sonar_pulse.write(false);
			}	
			else{
				sonarPulseCounter++;
			}
			Thread.sleep(10);
		}
		catch (InterruptedException e) {ioio_.disconnect();}
		finally{ioio_.endBatch();}
	}
	
	public synchronized void move(int value)
	{
		try {
			if(value > MAX_PWM) 
				pwm_vel_output.setPulseWidth(MAX_PWM);
	    	else if(value < MIN_PWM) 
	    		pwm_vel_output.setPulseWidth(MIN_PWM);
			else
				pwm_vel_output.setPulseWidth(value);
		} catch (ConnectionLostException e) {
			ioio_.disconnect();
		}
	}
	
	public synchronized void turn(int value){
		try {
			if(value > MAX_PWM) 
				pwm_turn_output.setPulseWidth(MAX_PWM);
	    	else if(value < MIN_PWM) 
	    		pwm_turn_output.setPulseWidth(MIN_PWM);
			else
				pwm_turn_output.setPulseWidth(value);
		} catch (ConnectionLostException e) {
			ioio_.disconnect();
		}
	}

	
	public synchronized void pan(int value)
	{
		try {
			if(value > 2400) 
				pwm_pan_output.setPulseWidth(MAX_PWM);
	    	else if(value < 600) 
	    		pwm_pan_output.setPulseWidth(MIN_PWM);
			else
				pwm_pan_output.setPulseWidth(value);
		} catch (ConnectionLostException e) {
			ioio_.disconnect();
		}

	}
	
	public synchronized void tilt(int value)
	{
		try {
			if(value > MAX_PWM) 
				pwm_tilt_output.setPulseWidth(MAX_PWM);
	    	else if(value < MIN_PWM) 
	    		pwm_tilt_output.setPulseWidth(MIN_PWM);
			else
				pwm_tilt_output.setPulseWidth(value);
		} catch (ConnectionLostException e) {
			ioio_.disconnect();
		}
	}
	
	public int get_sonar1_reading(){
		return sonar1_reading;
	}
	
	public int get_sonar2_reading(){
		return sonar2_reading;
	}
	
	public int get_sonar3_reading(){
		return sonar3_reading;
	}
	
	@Override
	public void disconnected() {
		Log.i("blar","IOIO_thread disconnected");
	}
}
