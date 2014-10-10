import lejos.nxt.Button;
import lejos.nxt.Motor;
import lejos.nxt.NXTRegulatedMotor;
import lejos.nxt.SensorPort;
import lejos.nxt.UltrasonicSensor;

/******************************************************************************
 * Group 5
 * @author Scott Cooper	- 260503452
 * @author Liqing Ding - 260457392
 * 
 * The purpose of this class is to serve as the entry point for Lab4 - Localization
 * 
 * First, the angle will be determined using Ultrasonic Localization. Once 
 * the angle of the robot is determined, the robot moves to do light sensor
 * localization to determine accurate X and Y coordinates.
 * 
 * Once these values are determiend, the Robot moves to the point (0,0)
 * and rotates to an angle of Theta=0.
 */
public class Lab5 {
	public static NXTRegulatedMotor LEFT_MOTOR = Motor.A,
				  					RIGHT_MOTOR = Motor.B;
	
	public static final int
		STOCHASTIC = Button.ID_LEFT,
		DETERMINISTIC = Button.ID_RIGHT,
		DEMO = Button.ID_ENTER;
	
	public static void main(String[] args) {
		// setup the odometer, ultrasonic sensor, and light sensor
		Odometer odo = new Odometer();
		Driver driver = new Driver(odo);
		UltrasonicSensor us = new UltrasonicSensor(SensorPort.S2);
		Localizer l;
		
		Display.printMainMenu();
		int option = Button.waitForAnyPress(), observationsMade;
		odo.start();
		
		switch (option){
			case STOCHASTIC:
				l = new Localizer(us, odo, driver, Localizer.LocalizationType.STOCHASTIC);
				observationsMade = l.localize();
				Display.print(odo.getX(), odo.getY(), odo.getTheta(), observationsMade, l.getStartingPoint());
				break;
			case DETERMINISTIC:
				l = new Localizer(us, odo, driver, Localizer.LocalizationType.DETERMINISTIC);
				observationsMade = l.localize();
				Display.print(odo.getX(), odo.getY(), odo.getTheta(), observationsMade, l.getStartingPoint());
				break;
			case DEMO:
				l = new Localizer(us, odo, driver, Localizer.LocalizationType.DETERMINISTIC);
				observationsMade = l.localize();
				Display.print(odo.getX(), odo.getY(), odo.getTheta(), observationsMade, l.getStartingPoint());
				driver.demo();
				break;
			default:
				System.exit(0);}

		while (Button.waitForAnyPress() != Button.ID_ESCAPE);
		System.exit(0);
	}

}
