package frc.robot.utils.talonutils;

import com.ctre.phoenix.motorcontrol.FeedbackDevice;
import com.ctre.phoenix.motorcontrol.can.WPI_TalonSRX;

public class BB_Motor extends WPI_TalonSRX {
    private final int ID;

    private static final int IDX_SLOTS = 2;
    private boolean doesPID = false;
    private double[] kp = new double[IDX_SLOTS];
    private double[] ki = new double[IDX_SLOTS];
    private double[] kd = new double[IDX_SLOTS];
    private double[] kf = new double[IDX_SLOTS];
    private int[] iZone = new int[IDX_SLOTS];
    private int cruiseVel_tp100;
    private int cruiseAcc_tp100;

    private boolean hasEncoder = false;
    private int encoderTicksPerRev = 8192; // unless otherwise specified
    private boolean sensorPhase = false;
    private int statusFramePeriod = 0; // 0 --> not set yet, assume default set by TalonUtils
    private boolean inverted = false;

    // IMPORTANT: this is the radius of anything the motor has to move.
    // if we only had a motor by itself, then the radius would be the axle radius
    // with a scoring arm, for example, it would be the length of the scoring arm
    private double radius;
    private double circumference;



    public BB_Motor(int id) {
        super(id);

        ID = id;
    }



    // PIDF + IZone
    public void setKp(double val) {
        kp[0] = val;
    }

    public void setKi(double val) {
        ki[0] = val;
    }

    public void setKd(double val) {
        kd[0] = val;
    }

    public void setKf(double val) {
        kf[0] = val;
    }

    public void setIZone(int val) {
        iZone[0] = val;
    }

    public void setCruiseVelocity(int val) {
        cruiseVel_tp100 = val;
    }

    public void setCruiseAcceleration(int val) {
        cruiseVel_tp100 = val;
    }

    public void finalizePID() {
        TalonUtils.initializeMotorFPID(this, kf[0], kp[0], ki[0], kd[0], iZone[0]);

        doesPID = true;
    }

    public void finalizePID(int idx) {
        TalonUtils.initializeMotorFPID(this, kf[idx], kp[idx], ki[idx], kd[idx], iZone[idx], idx);

        doesPID = true;
    }



    public void setEncoderTicksPerRev(int val) {
        encoderTicksPerRev = val;
    }

    public void invert() {
        inverted = true;
    }

    public void sensorPhase() {
        sensorPhase = true;
    }

    public void finalizeEncoder() {
        this.setSensorPhase(sensorPhase);
        this.setInverted(inverted);
        
        // initialize quad encoders
        if (statusFramePeriod != 0) {
            TalonUtils.initializeQuadEncoderMotor(this, statusFramePeriod);
        } else {
            TalonUtils.initializeQuadEncoderMotor(this);
        }
    }
}