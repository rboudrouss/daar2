package algorithms.others.Fadgiras;

import characteristics.IRadarResult;
import characteristics.Parameters;
import robotsimulator.Brain;

public class SecondaryRadar extends Brain {
    //---VARIABLES---//
    private double distanceTravelled, distanceTravelledRocky, distanceTravelledRockyToScan;
    private int whoAmI;
    private double myX,myY;

    private static final int TEAM = 0xBADDAD;
    private static final int UNDEFINED = 0xBADC0DE0;
    private int state;
    private boolean inPosition, inTurnPoint;
    private static final int FIRE = 0xB52;
    private static final int FALLBACK = 0xFA11BAC;
    private static final int ROGER = 0x0C0C0C0C;
    private static final int OVER = 0xC00010FF;

    private static final int TURNLEFTTASK = 1;
    private static final int MOVETASK = 2;
    private static final int TOTURNPOINT = 4;
    private static final int TURNRIGHTTASK = 3;
    private static final int SINK = 0xBADC0DE1;
    private static final double TARGET_DISTANCE = 700.0;
    private static final double TARGET_TURN_POINT = 200.0;
    private static final double HEADINGPRECISION = 0.1;
    private static final double ANGLEPRECISION = 0.001;
    private static final int ROCKY = 0x1EADDA;
    private static final int MARIO = 0x5EC0;

    //---CONSTRUCTORS---//
    public SecondaryRadar() { super(); }

    @Override
    public void activate() {
        whoAmI = ROCKY;
        for (IRadarResult o: detectRadar()){
            if (o.getObjectType()==IRadarResult.Types.TeamSecondaryBot){
                System.err.println("Secondary bot detected");
                System.err.println(isAbove(o.getObjectDirection(),Parameters.NORTH));
                System.err.println(isSameDirection(o.getObjectDirection(),Parameters.NORTH) && o.getObjectType()==IRadarResult.Types.TeamSecondaryBot);

                if (isAbove(o.getObjectDirection(),Parameters.NORTH) && o.getObjectType()==IRadarResult.Types.TeamSecondaryBot){
                    whoAmI=MARIO;
                }
            }
        }

        if (whoAmI == ROCKY){
            myX=Parameters.teamASecondaryBot1InitX;
            myY=Parameters.teamASecondaryBot1InitY;
            state=TURNLEFTTASK;
        }else {
            myX=Parameters.teamASecondaryBot2InitX;
            myY=Parameters.teamASecondaryBot2InitY;
            state=TURNRIGHTTASK;
        }

        move();
        sendLogMessage("Moving a head. Waza!");
    }

    @Override
    public void step() {

        if (whoAmI== ROCKY){
            System.err.println("I am Rocky "+ state);
            if (state==TURNLEFTTASK) {
                if (!(isSameDirection(getHeading(),Parameters.NORTH))) {
                    System.err.println("I am Rocky and I am turning left");
                    stepTurn(Parameters.Direction.LEFT);
                    return;
                }else {
                    state=TOTURNPOINT;
                }
            }

            if (state==TOTURNPOINT && !(isHeading(Parameters.EAST))) {
                System.err.println("TOTURNPOINT: Turning to EAST");
                if (distanceTravelledRocky < TARGET_TURN_POINT) {
                    System.err.println("TOTURNPOINT: Moving to turn point");
                    move();
                    distanceTravelledRocky += Parameters.teamBSecondaryBotSpeed;
                    sendLogMessage("Distance travelled: " + distanceTravelledRocky + " " + whoAmI);
                    return;
                }
                System.err.println("TOTURNPOINT: Reached turn point, turning RIGHT");
                stepTurn(Parameters.Direction.RIGHT);
                return;
            } else {
                inTurnPoint = true;
                System.err.println("TOTURNPOINT: In turn point");
            }


//            System.err.println(inTurnPoint && !(state==MOVETASK));
            if(inTurnPoint && (state!=MOVETASK)){
                System.err.println("Target turn point reached!");
                state=MOVETASK;
                System.err.println(state);
                return;
            }else {
                System.err.println("MOVETASK: INIT");
            }

            if (state==MOVETASK) {
                System.err.println("MOVETASK: In MOVETASK state");
                System.err.println("Distance travelled: " + distanceTravelledRockyToScan + " Target: " + TARGET_DISTANCE);
                if (distanceTravelledRockyToScan < TARGET_DISTANCE) {
                    System.err.println("MOVETASK: Moving to target");
                    move();
                    distanceTravelledRockyToScan += Parameters.teamBSecondaryBotSpeed;
                    sendLogMessage("Distance travelled: " + distanceTravelledRockyToScan + " " + whoAmI);
                    return;
                } else {
                    // Si la distance cible est atteinte, arrêtez le mouvement
                    sendLogMessage("Target distance reached!");
                    inPosition = true;
                }
            }

        }else {
            System.err.println("I am Mario "+ state);
            if (state==TURNRIGHTTASK) {
                if (!(isSameDirection(getHeading(),Parameters.SOUTH))) {
                    System.err.println("I am Mario and I am turning right");
                    stepTurn(Parameters.Direction.RIGHT);
                    return;
                }else {
                    state=TOTURNPOINT;
                }
            }

            if (state==TOTURNPOINT && !(isHeading(Parameters.EAST))) {
                System.err.println("TOTURNPOINT: Turning to EAST");
                if (distanceTravelledRocky < TARGET_TURN_POINT) {
                    System.err.println("TOTURNPOINT: Moving to turn point");
                    move();
                    distanceTravelledRocky += Parameters.teamBSecondaryBotSpeed;
                    sendLogMessage("Distance travelled: " + distanceTravelledRocky + " " + whoAmI);
                    return;
                }
                System.err.println("TOTURNPOINT: Reached turn point, turning LEFT");
                stepTurn(Parameters.Direction.LEFT);
                return;
            } else {
                inTurnPoint = true;
                System.err.println("TOTURNPOINT: In turn point");
            }


//            System.err.println(inTurnPoint && !(state==MOVETASK));
            if(inTurnPoint && (state!=MOVETASK)){
                System.err.println("Target turn point reached!");
                state=MOVETASK;
                System.err.println(state);
                return;
            }else {
                System.err.println("MOVETASK: INIT");
            }

            if (state==MOVETASK) {
                System.err.println("MOVETASK: In MOVETASK state");
                System.err.println("Distance travelled: " + distanceTravelledRockyToScan + " Target: " + TARGET_DISTANCE);
                if (distanceTravelledRockyToScan < TARGET_DISTANCE) {
                    System.err.println("MOVETASK: Moving to target");
                    move();
                    distanceTravelledRockyToScan += Parameters.teamBSecondaryBotSpeed;
                    sendLogMessage("Distance travelled: " + distanceTravelledRockyToScan + " " + whoAmI);
                    return;
                } else {
                    // Si la distance cible est atteinte, arrêtez le mouvement
                    sendLogMessage("Target distance reached!");
                    inPosition = true;
                }
            }
        }

        if (state==MOVETASK && inPosition){
            String name ;
            if (whoAmI==ROCKY){
                name = "ROCKY";
            }else {
                name = "MARIO";
            }
            sendLogMessage("In position. Ready to fire! " + (name));
            for (IRadarResult o: detectRadar()){
                if (o.getObjectType()==IRadarResult.Types.OpponentMainBot || o.getObjectType()==IRadarResult.Types.OpponentSecondaryBot) {
                    sendLogMessage("Enemy detected. Broadcasting !");
                    double enemyX=myX+o.getObjectDistance()*Math.cos(o.getObjectDirection());
                    double enemyY=myY+o.getObjectDistance()*Math.sin(o.getObjectDirection());
                    broadcast(whoAmI+":"+TEAM+":"+FIRE+":"+enemyX+":"+enemyY+":"+OVER);
                }
            }
        }
    }

    private boolean isHeading(double dir){
        return Math.abs(Math.sin(getHeading()-dir))<ANGLEPRECISION;
    }

    private double myGetHeading(){
        double result = getHeading();
        while(result<0) result+=2*Math.PI;
        while(result>2*Math.PI) result-=2*Math.PI;
        return result;
    }
    private boolean isSameDirection(double dir1, double dir2){
        return Math.abs(dir1-dir2)<ANGLEPRECISION;
    }

    private double normalize(double dir){
        double res=dir;
        while (res<0) res+=2*Math.PI;
        while (res>=2*Math.PI) res-=2*Math.PI;
        return res;
    }

    private boolean isAbove(double dir1, double dir2){
        return Math.abs(normalize(dir1)-normalize(dir2))<HEADINGPRECISION;
    }
}