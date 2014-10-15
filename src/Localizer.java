import java.util.ArrayList;
import java.util.Iterator;
import java.util.Random;
import lejos.nxt.UltrasonicSensor;

public class Localizer {
	/** Localization type to use */
	public static enum LocalizationType{
		/** Stochastic localization - 50-50 chance of turning or goign forward when
		 *  the robot is not blocked */
		STOCHASTIC, 
		/** Deterministic localization - If it sees a block, it will turn -90 deg. 
		 *  If it does not, it will move forward 1 tile */
		DETERMINISTIC};
	
	private static boolean[][] map;			// map is used map[x][y]. True if there is a block there
	private UltrasonicSensor us;			// Ultrasonic Sensor for block recognition
	private Odometer odo;					// Odometer to maintain position
	private Driver driver;					// Driver to control movement
	private LocalizationType locType;		// Localization type of this instance of Localizer
	private Position startingPoint;			// Where we started - null until localization finished
	private ArrayList<Position> possible;	// List of possible starting states

	/*********
	 * Create a new Localizer using a known map
	 * 
	 * @param us Ultrasonic sensor for block detetction
	 * @param odo Odometer to correct
	 * @param driver Driver to control movement
	 * @param locType Type of localization to use
	 */
	public Localizer(UltrasonicSensor us, Odometer odo, Driver driver, LocalizationType locType){
		this.us = us;
		this.odo = odo;
		this.driver = driver;
		this.locType = locType;
		this.startingPoint = null;
		possible = new ArrayList<Position>();
		
		// Initialize possible states based on map
		for (int x = 0; x < 4; x++){
			for(int y = 0; y < 4; y++){
				if (!map[x][y]){
					possible.add(new Position(x, y, Direction.UP, y == 3 || map[x][y+1]));
					possible.add(new Position(x, y, Direction.DOWN, y == 0 || map[x][y-1]));
					possible.add(new Position(x, y, Direction.RIGHT, x == 3 || map[x+1][y]));
					possible.add(new Position(x, y, Direction.LEFT, x == 0 || map[x-1][y]));}
			}
		}

	}

	/********
	 * Perform Localization using a known map
	 * 
	 * @return Number of observations made
	 */
	public int localize() {
		// Only used for stochastic localization
		Random r = new Random(System.nanoTime());	
		
		// Current direction relative to where we started
		Direction current = Direction.UP;	
		
		// Current X and Y relative to where we started, # of observations
		int x = 0, y = 0, observations = 0;	
		
		while (possible.size() > 1) { // Narrow down list of states until we know where we started
			boolean blocked = getFilteredData() < Odometer.TILE_SIZE;
			observations++;

			// Filter out now invalid orientations
			Iterator<Position> iter = possible.iterator();
			while (iter.hasNext()){
				Position s = iter.next();
				if (!Localizer.valid(s, new Position(x, y, current, blocked))){
					iter.remove();}}
			
			Display.printLocalizationInfo(x, y, current, blocked, possible.size());
			
			if (possible.size() == 1) break;
			
			switch(locType){
			case STOCHASTIC:
				if (blocked) {
					driver.turnTo(-90);
					current = Position.rotateLeft(current); }
				else {
					if (r.nextBoolean()){
						driver.turnTo(-90);
						current = Position.rotateLeft(current);
					} else{
						driver.goForward(Odometer.TILE_SIZE);
						switch(current){
						case DOWN: y--; break;
						case LEFT: x--; break;
						case RIGHT: x++; break;
						case UP: y++; break;
						default: throw new RuntimeException("Shouldn't Happen");}}}
				break;
			case DETERMINISTIC:
				if (blocked) {
					driver.turnTo(-90);
					current = Position.rotateLeft(current);
				} else {
					driver.goForward(Odometer.TILE_SIZE);
					switch(current){
					case DOWN: y--; break;
					case LEFT: x--; break;
					case RIGHT: x++; break;
					case UP: y++; break;
					default: throw new RuntimeException("Shouldn't Happen");}}
				break;
			}
		}

		if (possible.size() != 1)
			throw new RuntimeException("No possible states");
		
		startingPoint = possible.get(0);

		synchronized(odo){		// Update odometer based on known starting location
			double odo_x, odo_y;
			switch(startingPoint.getDir()){	// Need to get absolute change from starting point relative to where we are
			case DOWN:
				odo_x = -odo.getX();
				odo_y = -odo.getY();
				break;
			case LEFT:
				odo_x = -odo.getY();
				odo_y = odo.getX();
				break;
			case RIGHT:
				odo_x = odo.getY();
				odo_y = -odo.getX();
				break;
			case UP:
				odo_x = odo.getX();
				odo_y = odo.getY();
				break;
			default: throw new RuntimeException("Shouldn't Happen");}
			odo.setX((((double)startingPoint.getX()) - 0.5)*Odometer.TILE_SIZE + odo_x);
			odo.setY((((double)startingPoint.getY()) - 0.5)*Odometer.TILE_SIZE + odo_y);
			odo.setThetaRad(odo.getTheta() - (0.5 * ((double)startingPoint.getDir().v) * (Math.PI)));}
		return observations;	
	}
	
	/***
	 * Get the starting point resulting from localization or
	 * null if localization has not occurred yet
	 * 
	 * @return The starting point resulting from localization or
	 * null if localization has not occured yet
	 */
	public Position getStartingPoint(){
		return startingPoint;}
	
	/*******
	 * Get a value from the ultrasonic sensor for the current distance from the
	 * wall
	 * 
	 * @return A filtered value of the distance from the wall
	 */
	private int getFilteredData() {
		int dist;

		// do a ping
		us.ping();
		// wait for the ping to complete
		try {
			Thread.sleep(50);
		} catch (InterruptedException e) {
		}

		// there will be a delay here
		dist = us.getDistance();
		return dist > 50 ? 50 : dist;
	}

	/****
	 * Check whether a position r is possible from position s
	 * @param s The starting position to use as a 'base'
	 * @param r Where the robot is relative to where it started (current observation)
	 * @return True iff the position the position r is possible relative to position s
	 */
	private static boolean valid(Position s, Position r){
		// Get real direction to check based on Position checking and 
		// where we are facing based on where we started
		Direction realDir = s.getDir();
		for (int i = 0; i < r.getDir().v; i++)
			realDir = Position.rotateLeft(realDir);
		
		// Get position
		int x = Position.relativeX(s, r);
		int y = Position.relativeY(s, r);
		
		if (x < 0 || x > 3 || y < 0 || y > 3 || map[x][y]) return false;
		
		switch(realDir){
		case UP:
			if (r.isBlocked())
				return (y == 3 || map[x][y+1]);
			else
				return (y < 3 && !map[x][y+1]);
		case DOWN:
			if (r.isBlocked())
				return (y == 0 || map[x][y-1]);
			else
				return (y > 0 && !map[x][y-1]);
		case LEFT:
			if (r.isBlocked())
				return (x == 0 || map[x-1][y]);
			else
				return (x > 0 && !map[x-1][y]);
		case RIGHT:
			if (r.isBlocked())
				return (x == 3 || map[x+1][y]);
			else
				return (x < 3 && !map[x+1][y]);}
		return true;
	}
	
	static { 
		// Intiailization of static variables (map)
		// BOTTOM LEFT TILE IS (0, 0)
		// TOP RIGHT IS (3, 3)
		// null tile => block
		
		map = new boolean[4][4];
		
		for (int x = 0; x < 4; x++){
			for (int y = 0; y<4; y++){
					map[x][y] = false;
			}
		}
		map[1][0] = true; // Block (1,0)
		map[0][3] = true; // Block (0,3)
		map[2][2] = true; // Block (2,2)
		map[3][2] = true; // Block (3,2)
	}
}