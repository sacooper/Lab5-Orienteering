import java.util.ArrayList;
import java.util.Random;

import lejos.nxt.UltrasonicSensor;

public class Localizer {
	public static enum Direction {
		UP(0), RIGHT(1), DOWN(2), LEFT(3);
		public final int v;
		private Direction(int i){ v = i; }};

	public static enum LocalizationType{STOCHASTIC, DETERMINISTIC};
	
	private UltrasonicSensor us;
	private Odometer odo;
	private Driver driver;
	private LocalizationType locType;
	private static Tile[][] map;
	private Position startingPoint;
	
	private ArrayList<Position> possible;

	public Localizer(UltrasonicSensor us, Odometer odo, Driver driver, LocalizationType locType){
		this.us = us;
		this.odo = odo;
		this.driver = driver;
		this.locType = locType;
		this.startingPoint = null;
		
		// Initialize possible states based on map
		for (int x = 0; x < 4; x++){
			for(int y = 0; y < 4; y++){
				Tile current = map[x][y];
				if (current != null){
					possible.add(new Position(x, y, Direction.UP, current.blockedNorth()));
					possible.add(new Position(x, y, Direction.DOWN, current.blockedSouth()));
					possible.add(new Position(x, y, Direction.RIGHT, current.blockedEast()));
					possible.add(new Position(x, y, Direction.LEFT, current.blockedWest()));}
			}
		}
	}

	/********
	 * Perform Localization using a known map
	 */
	public int localize() {
		State state = new State();
		Random r = new Random();
		Direction current = Direction.UP;
		int x = 0, y = 0, observations = 0;

		while (possible.size() > 1) { // Narrow down list of states
			boolean blocked = getFilteredData() > Odometer.TILE_SIZE;
			observations++;
			state.addRelativePosition(new RelativePosition(x, y, current, blocked));

			// Filter out now invalid orientations
			for (Position s : possible){
				if (!state.valid(s))
					possible.remove(s);}
			switch(locType){
			case STOCHASTIC:
				if (blocked) {
					driver.turnTo(-90);
					current = State.rotateLeft(current); }
				else {
					if (r.nextBoolean()){
						driver.turnTo(-90);
						current = State.rotateLeft(current);
					} else
						driver.goForward(Odometer.TILE_SIZE);
						switch(current){
						case DOWN:
							x--;
							break;
						case LEFT:
							y--;
							break;
						case RIGHT:
							y++;
							break;
						case UP:
							x++;
							break;
						default:
							throw new RuntimeException("Shouldn't Happen");}}
				break;
			case DETERMINISTIC:
				if (blocked) {
					driver.turnTo(-90);
					current = State.rotateLeft(current);
				} else {
					driver.goForward(Odometer.TILE_SIZE);
					switch(current){
					case DOWN:
						x--;
						break;
					case LEFT:
						y--;
						break;
					case RIGHT:
						y++;
						break;
					case UP:
						x++;
						break;
					default:
						throw new RuntimeException("Shouldn't Happen");}}
				break;
			}
			
		}

		if (possible.size() != 1)
			throw new RuntimeException("No possible states");
		
		startingPoint = possible.get(0);
		
		synchronized(odo){		// Update odometer based on known starting location			
			odo.setX((((double)startingPoint.getX()) - 0.5)*Odometer.TILE_SIZE + odo.getX());
			odo.setY((((double)startingPoint.getY()) - 0.5)*Odometer.TILE_SIZE + odo.getY());
			odo.setThetaRad(0.5 * ((double)startingPoint.getDir().v) * (Math.PI) + odo.getTheta());}
		return observations;
		
	}
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

	public static class Tile {
		private boolean N, S, E, W; // Whether this direction is blocked
		private int x, y;

		public Tile(int x, int y, boolean N, boolean S, boolean E, boolean W) {
			this.x = x;
			this.y = y;
			this.N = N;
			this.S = S;
			this.E = E;
			this.W = W;}

		public int getX() {return x;}

		public int getY() {return y;}

		public boolean blockedNorth() {return N;}

		public boolean blockedSouth() {return S;}

		public boolean blockedEast() {return E;}

		public boolean blockedWest() {return W;}
	}

	public static class Position {
		private int x, y;
		private Direction dir; // UP = North, DOWN = South, etc.
		private boolean blocked;

		public Position(int x, int y, Direction dir, boolean blocked) {
			this.x = x;
			this.y = y;
			this.dir = dir;
			this.blocked = blocked;
		}

		public int getX() {
			return x;
		}

		public int getY() {
			return y;
		}

		public Direction getDir() {
			return dir;
		}

		public boolean isBlocked() {
			return blocked;
		}
	}
	
	public static class RelativePosition extends Position {
		public RelativePosition(int x, int y, Direction dir, boolean blocked) {
			super(x, y, dir, blocked);}	}
	
	public static class State {
		private ArrayList<RelativePosition> relativeInfo;
		
		private State() {
			relativeInfo = new ArrayList<RelativePosition>();
		}

		public static Direction rotateLeft(Direction d) {
			switch (d) {
			case UP:
				return Direction.LEFT;
			case LEFT:
				return Direction.DOWN;
			case DOWN:
				return Direction.RIGHT;
			case RIGHT:
				return Direction.UP;
			default:
				throw new RuntimeException("Shouldn't happen");}
		}

		public static Direction rotateRight(Direction d) {
			switch (d) {
			case UP:
				return Direction.RIGHT;
			case LEFT:
				return Direction.UP;
			case DOWN:
				return Direction.LEFT;
			case RIGHT:
				return Direction.DOWN;
			default:
				throw new RuntimeException("Shouldn't happen");}
		}

		public static int relativeX(Position start, RelativePosition r) {
			switch (start.getDir()) {
			case UP:
				return start.getX() + r.getX();
			case DOWN:
				return start.getX() - r.getX();
			case RIGHT:
				return start.getX() - r.getY();
			case LEFT:
				return start.getX() + r.getY(); 
			default:
				throw new RuntimeException("Shouldn't happen");}
		}

		public static int relativeY(Position start, RelativePosition r) {
			switch (start.getDir()) {
			case LEFT:
				return start.getY() + r.getX();
			case RIGHT:
				return start.getY() - r.getX();
			case UP:
				return start.getY() + r.getY();
			case DOWN:
				return start.getY() - r.getY();
			default:
				throw new RuntimeException("Shouldn't happen");}
		}

		public void addRelativePosition(RelativePosition r){
			this.relativeInfo.add(r);}
		
		private static Direction relativeDirection(Direction a, Direction b){
			int i = (a.v + b.v) % 4;
			switch(i){
			case 0: return Direction.UP;
			case 1: return Direction.RIGHT;
			case 2: return Direction.DOWN;
			case 3: return Direction.LEFT;
			default:
				throw new RuntimeException("Shouldn't happen");}
		}
		public boolean valid(Position s){
			for (RelativePosition r : relativeInfo){
				// Get real direction to check based on Position checking and 
				// where we are facing based on where we started
				Direction realDir = State.relativeDirection(s.getDir(), r.getDir());
				
				// Get position
				int x = State.relativeX(s, r);
				int y = State.relativeY(s, r);
				
				
				if (x < 0 || x > 4 || y < 0 || y > 4) return false;
				if (map[x][y]==null) return false;
				
				switch(realDir){
				case UP:
					if (r.isBlocked() != map[x][y].blockedNorth())
						return false;
				case DOWN:
					if (r.isBlocked() != map[x][y].blockedSouth())
						return false;
				case LEFT:
					if (r.isBlocked() != map[x][y].blockedWest())
						return false;
				case RIGHT:
					if (r.isBlocked() != map[x][y].blockedEast())
						return false;
				}
			}
			return true;
		}
		
	}
	
	static { // Intiailization of static variables (map)
		map = new Tile[4][4];

		// BOTTOM LEFT TILE IS (0, 0)
		// TOP RIGHT IS (3, 3)
		// null tile => block
		
		map[0][0] = new Tile(0, 0, false, true, true, true);
		map[0][1] = null;
		map[0][2] = new Tile(0, 2, false, true, false, true);
		map[0][3] = new Tile(0, 3, false, true, true, false);

		map[1][0] = new Tile(1, 0, false, false, false, true);
		map[1][1] = new Tile(1, 1, false, true, false, false);
		map[1][2] = new Tile(1, 2, true, false, false, false);
		map[1][3] = new Tile(1, 3, true, false, true, false);

		map[2][0] = new Tile(2, 0, true, false, true, false);
		map[2][1] = new Tile(2, 1, false, false, true, false);
		map[2][2] = null;
		map[2][3] = null;

		map[3][0] = null;
		map[3][1] = new Tile(3, 1, true, false, false, true);
		map[3][2] = new Tile(3, 2, true, true, false, false);
		map[3][3] = new Tile(3, 3, true, true, true, false);
	}
}