/*----------------------------------------------------------------------------*/
/* Copyright (c) 2018 FIRST. All Rights Reserved.                             */
/* Open Source Software - may be modified and shared by FRC teams. The code   */
/* must be accompanied by the FIRST BSD license file in the root directory of */
/* the project.                                                               */
/*----------------------------------------------------------------------------*/

package frc.robot.subsystem.drive;

import com.ctre.phoenix.motorcontrol.ControlMode;
import com.ctre.phoenix.motorcontrol.NeutralMode;
import com.ctre.phoenix.motorcontrol.StatusFrameEnhanced;
import com.ctre.phoenix.motorcontrol.can.WPI_TalonSRX;

import edu.wpi.first.wpilibj.drive.DifferentialDrive;
import edu.wpi.first.wpilibj.smartdashboard.SendableChooser;
import edu.wpi.first.wpilibj.smartdashboard.SmartDashboard;
import frc.robot.MotorId;
import frc.robot.subsystem.drive.DriveConstants;
import frc.robot.operatorinterface.OI;
import frc.robot.subsystem.BitBucketSubsystem;
import frc.robot.subsystem.navigation.NavigationSubsystem;
import frc.robot.utils.Deadzone;
import frc.robot.utils.JoystickScale;//for sam <3
import frc.robot.utils.talonutils.TalonUtils;


/**
 * Add your docs here.
 */
public class DriveSubsystem extends BitBucketSubsystem {

	// Singleton method; use DriveSubsystem.instance() to get the DriveSubsystem instance.
	public static DriveSubsystem instance() {
		if(inst == null)
			inst = new DriveSubsystem();
		return inst;
	}
	private static DriveSubsystem inst;

	private final OI oi = OI.instance();

	// drive styles that driver can choose on the shuffleboard
	public enum DriveStyle {
		WPI_Arcade,
		BB_Arcade,
		Velocity
		// add in curvature & velocity later
	}


  	// Put methods for controlling this subsystem
	// here. Call these from Commands.

	private final NavigationSubsystem navigation;

  	private final double INCH_PER_WHEEL_ROT = DriveConstants.WHEEL_CIRCUMFERENCE_INCHES;
	

	// Can adjust these to help the robot drive straight with zero turn stick.
	// +Values will add +yaw correct (CCW viewed from top) when going forward.
	private final double YAW_CORRECT_VELOCITY = 0.0;  // Multiplied by inch/sec so value will be small!
	private final double YAW_CORRECT_ACCEL = 0.0;
	
	private final double LOW_SENS_GAIN = 0.6;		
	private final double ALIGN_LOOP_GAIN = 0.04;
  
	private final int EDGES_PER_ENCODER_COUNT = 4;	// Always for quadrature
	
	// they always be saying "yee haw" but never "yaw hee" :(
	private double yawSetPoint;
	
	private final WPI_TalonSRX leftFrontMotor;		// User follower mode
	private final WPI_TalonSRX leftRearMotor;

	private final WPI_TalonSRX rightFrontMotor;		// Use follower mode
	private final WPI_TalonSRX rightRearMotor;
		
	private static SendableChooser<JoystickScale> forwardJoystickScaleChooser;
	private static SendableChooser<JoystickScale> turnJoystickScaleChooser;

	private static SendableChooser<DriveStyle> driveStyleChooser;



	private static DifferentialDrive differentialDrive;


	
	enum TestSubmodes
	{
		NONE,
		DIAGNOSTICS,
		MOVE_TEST,
		TURN_TEST,
		PROFILE_TEST
  	}
  	private static SendableChooser<TestSubmodes> testModeChooser;
	
	private static double testModePeriod_sec = 2.0;

	Idle initialCommand;

  private DriveSubsystem()
  {
	setName("DriveSubsystem");
	
	// Get the instance references
	navigation = NavigationSubsystem.instance();
              
    // Make joystick scale chooser and put it on the dashboard
    forwardJoystickScaleChooser = new SendableChooser<JoystickScale>();
    forwardJoystickScaleChooser.setDefaultOption( "Linear",    JoystickScale.LINEAR);
    forwardJoystickScaleChooser.addOption(  "Square",    JoystickScale.SQUARE);
    forwardJoystickScaleChooser.addOption(  "Cube",      JoystickScale.CUBE);
    forwardJoystickScaleChooser.addOption(  "Sine",      JoystickScale.SINE);

    SmartDashboard.putData( getName()+"/Forward Joystick Scale", forwardJoystickScaleChooser);

    turnJoystickScaleChooser = new SendableChooser<JoystickScale>();
    turnJoystickScaleChooser.addOption( "Linear",    JoystickScale.LINEAR);
    turnJoystickScaleChooser.setDefaultOption(  "Square",    JoystickScale.SQUARE);
    turnJoystickScaleChooser.addOption(  "Cube",      JoystickScale.CUBE);
    turnJoystickScaleChooser.addOption(  "Sine",      JoystickScale.SINE);
    
	SmartDashboard.putData( getName()+"/Turn Joystick Scale", turnJoystickScaleChooser);
	

	driveStyleChooser = new SendableChooser<DriveStyle>();
	driveStyleChooser.setDefaultOption("WPI Arcade", DriveStyle.WPI_Arcade);
	driveStyleChooser.addOption("Bit Buckets Arcade", DriveStyle.BB_Arcade);
	driveStyleChooser.addOption("Velocity", DriveStyle.Velocity);

	SmartDashboard.putData( getName()+"/Drive Style", driveStyleChooser);


	
	// TODO: These may need to be removed
    testModeChooser = new SendableChooser<TestSubmodes>();
    testModeChooser.setDefaultOption("None", TestSubmodes.NONE);
    testModeChooser.addOption("Diagnostics", TestSubmodes.DIAGNOSTICS);
    testModeChooser.addOption("Move Test", TestSubmodes.MOVE_TEST);
    testModeChooser.addOption("Turn Test", TestSubmodes.TURN_TEST);
    testModeChooser.addOption("Profile Test", TestSubmodes.PROFILE_TEST);
    
    DIAG_LOOPS_RUN = (int) SmartDashboard.getNumber("DIAG_LOOPS_RUN", 10);
      
    testModePeriod_sec = SmartDashboard.getNumber("Test Mode Period (sec)", 2.0);
      
    leftFrontMotor = new WPI_TalonSRX(MotorId.LEFT_DRIVE_MOTOR_FRONT_ID);
    leftRearMotor = new WPI_TalonSRX(MotorId.LEFT_DRIVE_MOTOR_REAR_ID);
    TalonUtils.initializeMotorDefaults(leftFrontMotor);
    TalonUtils.initializeMotorDefaults(leftRearMotor);

    leftRearMotor.follow(leftFrontMotor);
    
      
    /// TODO: Create setupMasterMotor function
    /// TODO: Create setupSlaveMotor function
    /// Each function should take a list of argument constants for inversion, sense, sensor type, deadbands, etc
    
    leftFrontMotor.setInverted(DriveConstants.LEFT_DRIVE_MOTOR_INVERSION_FLAG);
    leftRearMotor.setInverted(DriveConstants.LEFT_DRIVE_MOTOR_INVERSION_FLAG);
      
    leftFrontMotor.setSensorPhase(DriveConstants.LEFT_DRIVE_MOTOR_SENSOR_PHASE);
      
    // Set relevant frame periods to be at least as fast as periodic rate
    // NOTE: This increases load on CAN bus, so pay attention as more motor
    // controllers are added to the system
    leftFrontMotor.setStatusFramePeriod(StatusFrameEnhanced.Status_13_Base_PIDF0, 
                                    DriveConstants.HIGH_STATUS_FRAME_PERIOD_MS, 
                                    DriveConstants.CONTROLLER_TIMEOUT_MS);
    leftFrontMotor.setStatusFramePeriod(StatusFrameEnhanced.Status_10_MotionMagic, 
                                    DriveConstants.HIGH_STATUS_FRAME_PERIOD_MS, 
                                    DriveConstants.CONTROLLER_TIMEOUT_MS);
    
    leftFrontMotor.configNeutralDeadband(DriveConstants.LEFT_DRIVE_MOTOR_NEUTRAL_DEADBAND,
                                      DriveConstants.CONTROLLER_TIMEOUT_MS);
    leftRearMotor.configNeutralDeadband(DriveConstants.LEFT_DRIVE_MOTOR_NEUTRAL_DEADBAND, 
                                    DriveConstants.CONTROLLER_TIMEOUT_MS);
    
    leftFrontMotor.configOpenloopRamp(DriveConstants.DRIVE_MOTOR_OPEN_LOOP_RAMP_SEC, 
                                  DriveConstants.CONTROLLER_TIMEOUT_MS);
    leftRearMotor.configOpenloopRamp(DriveConstants.DRIVE_MOTOR_OPEN_LOOP_RAMP_SEC, 
                                           DriveConstants.CONTROLLER_TIMEOUT_MS);
    leftFrontMotor.configClosedloopRamp(DriveConstants.DRIVE_MOTOR_CLOSED_LOOP_RAMP_SEC, 
                                              DriveConstants.CONTROLLER_TIMEOUT_MS);
    leftRearMotor.configClosedloopRamp(DriveConstants.DRIVE_MOTOR_CLOSED_LOOP_RAMP_SEC, 
                                             DriveConstants.CONTROLLER_TIMEOUT_MS);


    // Configure for closed loop control
    // Our drives use the "front" motor in a group for control; i.e., where the sensor is located
	  TalonUtils.initializeQuadEncoderMotor(leftFrontMotor);

    // Set closed loop gains in slot0 - see documentation (2018 SRM Section 12.6)
    // The gains are determined empirically following the Software Reference Manual
    // Summary:
    //	Run drive side at full speed, no-load, forward and initiate SelfTest on System Configuration web page
    //  Observe the number of encoder ticks per 100 ms, the % output, and voltage
    //  Collect data in both forward and backwards (e.g., 5 fwd, 5 back)
    //  Average the absolute value of that number, adjust as measured_ticks / percentage_factor
    //  Compute Kf = 1023 / adjusted_tick_average
    //  The using that value, run the Motion Magic forward 10 revolutions at the encoder scale
    //  Note the error (in ticks)
    //  Compute Kp = 0.1 * 1023 / error as a starting point
    //  Command any position through Motion Magic and attempt to turn the motor by hand while holding the command
    //  If the axle turns, keep doubling the Kp until it stops turning (or at leasts resists vigorously without
    //  oscillation); if it oscillates, you must drop the gain.
    //  Run the Motion Magic for at least 10 rotations in each direction
    //  Make not of any misses or overshoot.
    //  If there is unacceptable overshoot then set Kd = 10 * Kp as a starting point and re-test
    //
    //  Put drive train on ground with weight and re-test to see if position is as commanded.
    //  If not, then add SMALL amounts of I-zone and Ki until final error is removed.
    TalonUtils.initializeMotorFPID(leftFrontMotor, DriveConstants.driveMotorKf, DriveConstants.driveMotorKp, DriveConstants.driveMotorKi, DriveConstants.driveMotorKd, DriveConstants.driveMotorIZone);

    /* set acceleration and vcruise velocity - see documentation */
    leftFrontMotor.configMotionCruiseVelocity(DriveConstants.DRIVE_MOTOR_MOTION_CRUISE_SPEED_NATIVE_TICKS, 
                                          DriveConstants.CONTROLLER_TIMEOUT_MS);
    leftFrontMotor.configMotionAcceleration(DriveConstants.DRIVE_MOTOR_MOTION_ACCELERATION_NATIVE_TICKS, 
                                        DriveConstants.CONTROLLER_TIMEOUT_MS);
    
    // Use follower mode to minimize shearing commands that could occur if
    // separate commands are sent to each motor in a group
    leftRearMotor.set(ControlMode.Follower, leftFrontMotor.getDeviceID());
    
    rightFrontMotor  = new WPI_TalonSRX(MotorId.RIGHT_DRIVE_MOTOR_FRONT_ID);
    rightRearMotor   = new WPI_TalonSRX(MotorId.RIGHT_DRIVE_MOTOR_REAR_ID);
	TalonUtils.initializeMotorDefaults(rightFrontMotor);
	TalonUtils.initializeMotorDefaults(rightRearMotor);

	rightRearMotor.follow(rightFrontMotor);
    
    rightFrontMotor.setInverted(DriveConstants.RIGHT_DRIVE_MOTOR_INVERSION_FLAG);
    rightRearMotor.setInverted(DriveConstants.RIGHT_DRIVE_MOTOR_INVERSION_FLAG);

    rightFrontMotor.setSensorPhase(DriveConstants.RIGHT_DRIVE_MOTOR_SENSOR_PHASE);

    rightFrontMotor.setStatusFramePeriod(StatusFrameEnhanced.Status_13_Base_PIDF0, 
                                              DriveConstants.HIGH_STATUS_FRAME_PERIOD_MS, 
                                              DriveConstants.CONTROLLER_TIMEOUT_MS);
    rightFrontMotor.setStatusFramePeriod(StatusFrameEnhanced.Status_10_MotionMagic, 
                                              DriveConstants.HIGH_STATUS_FRAME_PERIOD_MS, 
                                              DriveConstants.CONTROLLER_TIMEOUT_MS);
    
    rightFrontMotor.configNeutralDeadband(DriveConstants.RIGHT_DRIVE_MOTOR_NEUTRAL_DEADBAND, 
                                      DriveConstants.CONTROLLER_TIMEOUT_MS);
    rightRearMotor.configNeutralDeadband(DriveConstants.RIGHT_DRIVE_MOTOR_NEUTRAL_DEADBAND, 
                                      DriveConstants.CONTROLLER_TIMEOUT_MS);
  
    rightFrontMotor.configOpenloopRamp(DriveConstants.DRIVE_MOTOR_OPEN_LOOP_RAMP_SEC, 
                          DriveConstants.CONTROLLER_TIMEOUT_MS);
    rightRearMotor.configOpenloopRamp(DriveConstants.DRIVE_MOTOR_OPEN_LOOP_RAMP_SEC, 
                      DriveConstants.CONTROLLER_TIMEOUT_MS);
    
    rightFrontMotor.configClosedloopRamp(DriveConstants.DRIVE_MOTOR_CLOSED_LOOP_RAMP_SEC, 
                                      DriveConstants.CONTROLLER_TIMEOUT_MS);
    rightRearMotor.configClosedloopRamp(DriveConstants.DRIVE_MOTOR_CLOSED_LOOP_RAMP_SEC, 
                                    DriveConstants.CONTROLLER_TIMEOUT_MS);


    // Configure for closed loop control
    // Our drives use the "front" motor in a group for control; i.e., where the sensor is located
    TalonUtils.initializeQuadEncoderMotor(rightFrontMotor);

    // Set closed loop gains in slot0 - see documentation (2018 SRM Section 12.6)
    // The gains are determined empirically following the Software Reference Manual
    // Summary:
    //	Run drive side at full speed, no-load, forward and initiate SelfTest on System Configuration web page
    //  Observe the number of encoder ticks per 100 ms, the % output, and voltage
    //  Collect data in both forward and backwards (e.g., 5 fwd, 5 back)
    //  Average the absolute value of that number, adjust as measured_ticks / percentage_factor
    //  Compute Kf = 1023 / adjusted_tick_average
    //  The using that value, run the Motion Magic forward 10 revolutions at the encoder scale
    //  Note the error (in ticks)
    //  Compute Kp = 0.1 * 1023 / error as a starting point
    //  Command any position through Motion Magic and attempt to turn the motor by hand while holding the command
    //  If the axle turns, keep doubling the Kp until it stops turning (or at leasts resists vigorously without
    //  oscillation); if it oscillates, you must drop the gain.
    //  Run the Motion Magic for at least 10 rotations in each direction
    //  Make not of any misses or overshoot.
    //  If there is unacceptable overshoot then set Kd = 10 * Kp as a starting point and re-test
    //
    //  Put drive train on ground with weight and re-test to see if position is as commanded.
    //  If not, then add SMALL amounts of I-zone and Ki until final error is removed.
    TalonUtils.initializeMotorFPID(rightFrontMotor, DriveConstants.driveMotorKf, DriveConstants.driveMotorKp, DriveConstants.driveMotorKi, DriveConstants.driveMotorKd, DriveConstants.driveMotorIZone);

    /* set acceleration and vcruise velocity - see documentation */
    rightFrontMotor.configMotionCruiseVelocity(DriveConstants.DRIVE_MOTOR_MOTION_CRUISE_SPEED_NATIVE_TICKS, 
                                           DriveConstants.CONTROLLER_TIMEOUT_MS);
    rightFrontMotor.configMotionAcceleration(DriveConstants.DRIVE_MOTOR_MOTION_ACCELERATION_NATIVE_TICKS, 
                                         DriveConstants.CONTROLLER_TIMEOUT_MS);
  

    // Use follower mode to minimize shearing commands that could occur if
    // separate commands are sent to each motor in a group
    rightRearMotor.set(ControlMode.Follower, rightFrontMotor.getDeviceID());

    // Now get the other modes set up
    setNeutral(NeutralMode.Brake);
	
	// Now that we have the motor instances set up the differential drive
	// as a 2-motor solution regardless of how manu actual motors we have
	// We are taking advantage of the follower mode to minimize CAN traffic
	// !!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!
	// NOTE: This only works on drives where all motors on a side drive the
	// same wheels
	// !!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!
	differentialDrive = new DifferentialDrive(leftFrontMotor, rightFrontMotor);

	// Since we going to use the TalonSRX in this class, the inversion, if needed is
	// going to be passed to controllers so positive commands on left and right both
	// move the wheels in the same direction. This means we don't want the diff drive
	// algorithm to also do the inversion
	differentialDrive.setRightSideInverted(false);
    
    // Create the motion profile driver
  }
  

  public double getTestModePeriod_sec()
    {
    	return testModePeriod_sec;
    }
    public TestSubmodes getTestSubmode()
    {
    	return testModeChooser.getSelected();
    }
    
        
    /// TODO: Should provide more control, see junk bot example for an enumerated
    /// selector that can be different per axis
    public void setMotionVelocity(double fraction_full_speed) 
    {
    	leftFrontMotor.configMotionCruiseVelocity((int)(fraction_full_speed * DriveConstants.DRIVE_MOTOR_MOTION_CRUISE_SPEED_NATIVE_TICKS), 
					                                    DriveConstants.CONTROLLER_TIMEOUT_MS);
    	rightFrontMotor.configMotionCruiseVelocity((int)(fraction_full_speed * DriveConstants.DRIVE_MOTOR_MOTION_CRUISE_SPEED_NATIVE_TICKS), 
    													DriveConstants.CONTROLLER_TIMEOUT_MS);
    }

    private double shapeAxis( double x) 
    {
		  x = Deadzone.f( x, .05);
		  return Math.signum(x) * (x*x);
  	}

	/**
	 * drive - takes a speed and turn factor and passes to the selected drive algorithm
	 * Context depends upon which algorithm is selected, but is generally [-1,1] domain
	 * unless otherwise indicated.
	 * 
	 * Velocity drive selection requires inputs of feet/sec and deg/sec (TBD)
	 */
	public void drive(double speed, double turn) {

		/// TODO: Should these scaler be in the OI hidden under speed() and turn() calls??
		speed = forwardJoystickScaleChooser.getSelected().rescale(speed);
		if (Math.abs(speed) < 0.25)
		{
			speed = 0.0;
		}
		turn  = turnJoystickScaleChooser.getSelected().rescale(turn);
		if (Math.abs(turn) < 0.25)
		{
			turn = 0.0;
		}

		if (ds.isTest())
		{
			testDrive();
		}
		else
		{
			DriveStyle style = driveStyleChooser.getSelected();

			switch (style) {
				// Even though the enumeration should be correct
				// it is a best practice to always explicitly set a default
				// just in case the interface has a glitch and the wrong
				// signal reaches here. The default can either fall through
				// or do something else, but now we made a choice
				default:
				case WPI_Arcade: {
					// DO NOT let the diff drive square the inputs itself
					// All scaling is external to this drive function
					differentialDrive.arcadeDrive(speed, turn, false);

					break;
				}

				case BB_Arcade: {
					arcadeDrive(speed, turn);

					break;
				}

				case Velocity: {
					velocityDrive(speed, turn);

					break;
				}
			}
		}
	}

	/**
	 * testDrive - special test features for tuning and testing
	 * the drive train.
	 * 
	 * Include dashboard input/output for helping with FPID tuning
	 * by allowing trigger and control of 
	 * 		forward/reverse speed sample for Kf = (%v * 1023)/tp100
	 * 			Where
	 * 				%v is percent of full power (ideally 100%)
	 * 				tp100 is ticks per 100 ms
	 *      identification of initial cruise speed (85% of max)
	 * 			Cs = 0.85 * tp100
	 * 		invocation of motion magic mode and command some rotations, R (e.g., 10)
	 * 		Make note of the error in ticks (terr)
	 * 		Compute initial Kp
	 * 			Kp = (0.1 * 1023)/terr
	 * 		Command another +/- R rotations
	 * 			Test for oscillation or manually test for backdrive
	 * 			Keep doubling Kp until oscillations start and then back off a little
	 * 			Make note of overshoot
	 * 		Estimate initial Kd
	 * 			Kd = 10 * Kp
	 * 		Command another +/- R rotations
	 * 			Test for oscilation and overshoot
	 * 			Test for steady state error (sserr)
	 * 			Set I-Zone to define when Ki is needed
	 * 				Iz = sserr * 2.5
	 * 			Estimate Ki
	 * 				Ki = 0.001
	 * 			Keep doubling Ki until sserr gets sufficiently close to zero
	 * 				Stop and back off if oscillations appear
	 * 		
	 */
	private void testDrive() {

	}

	// +turnStick produces right turn (CW from above, -yaw angle)
    /// TODO: Consider re-designing this to reduce turn by up to 50% at full forward speed
	private void arcadeDrive(double fwdStick, double turnStick) 
	{
		
		// Shape axis for human control
		/// TODO: axis shaping should be controllable via dashboard
		/// see examples of selector for linear, square, cube, and sine
		/// TODO: May want different shapes on fwd and turn
		
		fwdStick = forwardJoystickScaleChooser.getSelected().rescale(fwdStick);
		turnStick = turnJoystickScaleChooser.getSelected().rescale(turnStick);
		
		if(oi.lowSensitivity()) 
		{
			fwdStick *= LOW_SENS_GAIN;
			turnStick *= LOW_SENS_GAIN;
		}

		double maxSteer = 1.0 - Math.abs(fwdStick) / 2.0;	// Reduce steering by up to 50%
		double steer = maxSteer * turnStick;
		
		leftFrontMotor.set(ControlMode.PercentOutput, fwdStick + steer);
		rightFrontMotor.set(ControlMode.PercentOutput, fwdStick - steer);
//		
//		/// TODO: Probably harmless. It is not clear that this 0,0 check will actually
//		/// do anything unless shapeAxis actually forces zero for some
//		/// shapes. In general, if the value is below the neutral deadband, nothing will move
//		/// so the minimum of both left and right deadbands is the determining factor
//		if( fwdStick == 0.0 && turnStick == 0.0) {
//			setAllMotorsZero();
//		}
//		else {
//			// Turn stick is + to the right;
//			// but arcadeDrive 2nd arg + produces left turn
//			// (this is +yaw when yaw is defined according to right-hand-rule
//			// with z-axis up, so arguably correct).
//			// Anyhow need the - sign on turnStick to make it turn correctly.
//			drive.arcadeDrive( fwdStick, turnStick + yawCorrect(), false);
//		}
	}



	/**
	 * @param vel   inches  / sec
	 * @param omega radians / sec
	 */
	public void velocityDrive(double vel, double omega) {
		// velocity mode <-- value in change in position per 100ms

		double vL = vel + omega * DriveConstants.TRACK / 2;
		double vR = vel - omega * DriveConstants.TRACK / 2;

		// "
		// Basically the encoder (quadrature in our case) measure angle; velocity is average angle over small delta-t
		// Our encoders have a 2048 pulses per rev, to 8192 quad edged per rev
		// " - Mike

		// convert to rev/sec
		vL /= DriveConstants.WHEEL_CIRCUMFERENCE_INCHES;
		vR /= DriveConstants.WHEEL_CIRCUMFERENCE_INCHES;

		// convert to rev/100ms
		vL /= 10;
		vR /= 10;

		// convert to native ticks/100ms
		vL *= DriveConstants.DRIVE_MOTOR_NATIVE_TICKS_PER_REV;
		vR *= DriveConstants.DRIVE_MOTOR_NATIVE_TICKS_PER_REV;

		leftFrontMotor.set(ControlMode.Velocity, vL);
		rightFrontMotor.set(ControlMode.Velocity, vR);
	}

	public void doAutoTurn( double turn) {
		arcadeDrive( 0.0, turn);				
	}
	
	public void setAlignDrive(boolean start) {
		if(start) {
			yawSetPoint = navigation.getYaw_deg();
		} 
	}
	
	public void doAlignDrive(double fwdStick, double turnStick) {
					
		if(oi.lowSensitivity())
			fwdStick *= LOW_SENS_GAIN;
		
		fwdStick = shapeAxis(fwdStick);
		turnStick = shapeAxis(turnStick);
					
		if( fwdStick == 0.0 && turnStick == 0.0) {
			setAllMotorsZero();
		}
		else {
			
			// Turn stick is + to the right,
			// +yaw is CCW looking down,
			// so + stick should lower the setpoint. 
			yawSetPoint += -0.3 * turnStick;
			
			double error = -ALIGN_LOOP_GAIN * (yawSetPoint - navigation.getYaw_deg());
			error = -ALIGN_LOOP_GAIN * -navigation.getYawRate_degPerSec();
			SmartDashboard.putNumber(getName()+"/IMU_ERROR", error);
			arcadeDrive( fwdStick, error + yawCorrect());
		}
	}
	
	// Autonomous: drive in straight line
	public void doAutoStraight( double fwd) {
		if( fwd == 0.0)
			setAllMotorsZero();
		else {
			double error = ALIGN_LOOP_GAIN * (yawSetPoint - navigation.getYaw_deg());				
			arcadeDrive( fwd, error + yawCorrect());				
		}			
	}
	@Override
	protected void initDefaultCommand() 
	{
		// NOTE NOTE NOTE: Moved to initialize so it does not automatically interfere
		// setDefaultCommand(new Idle());		
		
	}

	public void initialize() 
	{		
		initializeBaseDashboard();

		// System.out.println("Setting default command Mike's way");	// Don't use default commands as they can catch you by surprise
		// initialCommand = new Idle();	// Only create it once
		// initialCommand.start();
    }
  
  
	@Override
	public void periodic() {
		// Even though a disabled state should shut things down, we want to be clear that
		// the drive subsystem is explicitly disabled if this function is called while
		// a disabled is detected; this protects us from changes in the upper-level design
		// that we have little control of.
		if (ds.isDisabled())
		{
			disable();
		}
		else if (ds.isOperatorControl()) // TODO: and ! AutoAssist
		{
			// Precedence as follows
			//	DriveLock
			//	AlignLock
			//	Drive
			// TODO: Figure out how to respond to quick turn request, continuing until complete or canceled
			// Could be a command but commands seem overly complicated for some of this
			if (oi.driveLock())
			{
				doLockDrive(0.0);
			}
			else if (oi.alignLock())
			{
				doAlignDrive(oi.speed(), 0.0);
			}
			else
			{
				drive(oi.speed(), oi.turn());
			}
		}
		else if (! ds.isTest()) // or AutoAssist
		{
			// TBD
		}

		updateBaseDashboard();
		if (getTelemetryEnabled())
		{

		}
		if (getDiagnosticsEnabled())
		{

		}
		
	}
  	
	public void disable() {
		setAllMotorsZero();
	}
	
	// Might need to change from .set(value) to .set(mode, value)
	private void setAllMotorsZero() 
	{
		leftRearMotor.follow(leftFrontMotor);
		leftFrontMotor.set(ControlMode.PercentOutput, 0.0);

		rightRearMotor.follow(rightFrontMotor);
		rightFrontMotor.set(ControlMode.PercentOutput, 0.0);
	}
	
	/// TODO: This is redundant with other similar functions
	public void doLockDrive(double value) 
	{
		leftFrontMotor.set(ControlMode.MotionMagic, value);
		rightFrontMotor.set(ControlMode.MotionMagic, value);			
	}


	/** 
	 * setNeutral is a pass through interface to each motor in the subsystem
	 * 
	 * @param neutralMode is either Coast or Brake. Braking will apply force to come to a stop at zero input
	 */
	private void setNeutral(NeutralMode neutralMode) 
	{	
		leftFrontMotor.setNeutralMode(neutralMode);
		leftRearMotor.setNeutralMode(neutralMode);
		rightFrontMotor.setNeutralMode(neutralMode);
		rightRearMotor.setNeutralMode(neutralMode);
		
	}
	private double yawCorrect() {
		return YAW_CORRECT_VELOCITY * getFwdVelocity_ips() 
				+ YAW_CORRECT_ACCEL * getFwdCurrent();
	}
	public double getRightPosition_inch() {
		// Right motor encoder reads -position when going forward!
		// TODO: This is wrong! Need new constants
		return -INCH_PER_WHEEL_ROT * rightFrontMotor.getSelectedSensorPosition(DriveConstants.PRIMARY_PID_LOOP);						
	}
	
	private int getMotorNativeUnits(WPI_TalonSRX m) {
		return m.getSelectedSensorPosition(DriveConstants.PRIMARY_PID_LOOP);
	}
	
	public int getRightNativeUnits() {
		return getMotorNativeUnits(rightFrontMotor);
	}
	
	public int getLeftNativeUnits() {
		return getMotorNativeUnits(leftFrontMotor);
	}
	
	private double getMotorEncoderUnits(WPI_TalonSRX m) {
		return getMotorNativeUnits(m)/EDGES_PER_ENCODER_COUNT;
	}
	
	public double getRightEncoderUnits() {
		return getMotorEncoderUnits(rightFrontMotor);
	}
	
	public double getLeftEncoderUnits() {
		return getMotorEncoderUnits(leftFrontMotor);
	}
	
	private ControlMode getMotorMode(WPI_TalonSRX m) {
		return m.getControlMode();
	}
	
	public ControlMode getRightFrontMode() {
		return getMotorMode(rightFrontMotor);
	}
	
	public ControlMode getLeftFrontMode() {
		return getMotorMode(leftFrontMotor);
	}
	
	public ControlMode getLeftRearMode() {
		return getMotorMode(leftRearMotor);
	}
	
	public ControlMode getRightRearMode() {
		return getMotorMode(rightRearMotor);
	}
	
	public double inchesToNativeTicks(double inches) {
		return (double)DriveConstants.DRIVE_MOTOR_NATIVE_TICKS_PER_REV * (inches / DriveConstants.WHEEL_CIRCUMFERENCE_INCHES);
	}

	public double getFwdVelocity_ips() {
		// Right side motor reads -velocity when going forward!
		double fwdSpeedRpm = (leftFrontMotor.getSelectedSensorVelocity(DriveConstants.PRIMARY_PID_LOOP) - rightFrontMotor.getSelectedSensorVelocity(DriveConstants.PRIMARY_PID_LOOP))/2.0;
		return (INCH_PER_WHEEL_ROT / 60.0) * fwdSpeedRpm;
	}
	public double getFwdCurrent() {
		// OutputCurrent always positive so apply sign of drive voltage to get real answer.
		// Also, right side has -drive when going forward!
		double leftFront = leftFrontMotor.getOutputCurrent() * Math.signum( leftFrontMotor.getMotorOutputVoltage());
		double leftRear = leftRearMotor.getOutputCurrent() * Math.signum( leftRearMotor.getMotorOutputVoltage());
		double rightFront = -rightFrontMotor.getOutputCurrent() * Math.signum( rightFrontMotor.getMotorOutputVoltage());
		double rightRear = -rightRearMotor.getOutputCurrent() * Math.signum( rightRearMotor.getMotorOutputVoltage());
		return (leftFront + leftRear + rightFront + rightRear)/4.0;
	}
	
	public double getPosition_inch() {
		// TODO Auto-generated method stub
		return 0;
	}
	
	// Set up a single motor for position control
	private void resetMotion(WPI_TalonSRX m) 
	{
		// Stop as quickly as possible
		m.set(ControlMode.PercentOutput, 0.0);
		
		// Clear the encoder to start a motion relative to "here"
		m.setSelectedSensorPosition(0, DriveConstants.PRIMARY_PID_LOOP, DriveConstants.CONTROLLER_TIMEOUT_MS);
	}
	
	// Set up the entire drive system for position control
	public void resetMotion() 
	{
		resetMotion(leftFrontMotor);
		resetMotion(rightFrontMotor);
	}
	
	// Set a specific motor for a motion magic position
	private void setPosition(WPI_TalonSRX m, double nativeTicks) {
		
		m.set(ControlMode.MotionMagic, nativeTicks);
	}
	
	// Set all motors to drive in the same direction for same distance
	public void move_inches(double value_inches) 
	{
		setPosition(leftFrontMotor,  inchesToNativeTicks(value_inches));
		setPosition(rightFrontMotor, inchesToNativeTicks(value_inches));
	}
	
	/* Any hardware devices used in this subsystem must
	*  have a check here to see if it is still connected and 
	*  working properly. For motors check for current draw.
	*  Return true iff all devices are working properly. Otherwise
	*  return false. This sets all motors to percent output
	*/
	@Override
	public void diagnosticsInit() {
		
	}
	
	@Override
	public void diagnosticsExecute() {

		/* Init Diagnostics */
		SmartDashboard.putBoolean(getName()+"/RunningDiag", true);
		
		rightFrontMotor.set(ControlMode.PercentOutput, DriveConstants.MOTOR_TEST_PERCENT);
		rightRearMotor.set(ControlMode.PercentOutput, -DriveConstants.MOTOR_TEST_PERCENT);
		leftFrontMotor.set(ControlMode.PercentOutput, -DriveConstants.MOTOR_TEST_PERCENT);
		leftRearMotor.set(ControlMode.PercentOutput, DriveConstants.MOTOR_TEST_PERCENT);
	}
	
	@Override
	public void diagnosticsCheck() {
		/* Reset flag */
		
	}

	// Move is complete when we are within tolerance and can consider starting the next move
	public boolean isMoveComplete(double distance_inches)	// At timeout should be used with this
	{
		int ticks = (int)inchesToNativeTicks(distance_inches);
		int errorL = (int) Math.abs(ticks - leftFrontMotor.getSelectedSensorPosition(DriveConstants.PRIMARY_PID_LOOP));
		int errorR = (int) Math.abs(ticks - rightFrontMotor.getSelectedSensorPosition(DriveConstants.PRIMARY_PID_LOOP));
		return (errorL  < DriveConstants.DRIVE_MOTOR_MAX_CLOSED_LOOP_ERROR_TICKS) &&
			   (errorR < DriveConstants.DRIVE_MOTOR_MAX_CLOSED_LOOP_ERROR_TICKS);
	}

	public void turn_degrees(double angle_degrees)
	{
		// Use motion magic to run both sides in opposite directions
		double targetPos_ticks = (angle_degrees * DriveConstants.WHEEL_ROTATION_PER_FRAME_DEGREES) * DriveConstants.DRIVE_MOTOR_NATIVE_TICKS_PER_REV;
		
		// Assuming rotation is right hand rule about nadir (i.e., down vector is Z because X is out front and Y is out right side)
		// then Right Motor back and Left Motor forward is rotate to right (which is a positive rotation)
		
		leftFrontMotor.set(ControlMode.MotionMagic,  targetPos_ticks);
		rightFrontMotor.set(ControlMode.MotionMagic, -targetPos_ticks);		
		
	}
	
	public boolean isTurnComplete(double  angle_degrees) // A timeout should be used with this
	{
		// Using the same drive error for move and turn is not a universal thing
		// In this case if the wheels are 6.25 and track is 24.25 and tolerance is 0.125 inches on move
		// then the equivalent angle is about 0.6 degrees of frame rotation.
		
		double targetPos_ticks = (angle_degrees * DriveConstants.WHEEL_ROTATION_PER_FRAME_DEGREES) * DriveConstants.DRIVE_MOTOR_NATIVE_TICKS_PER_REV;
		int errorL = (int) Math.abs(targetPos_ticks - (leftFrontMotor.getSelectedSensorPosition(DriveConstants.PRIMARY_PID_LOOP)));
		int errorR = (int) Math.abs(-targetPos_ticks - (rightFrontMotor.getSelectedSensorPosition(DriveConstants.PRIMARY_PID_LOOP)));
		return (errorL  < DriveConstants.DRIVE_MOTOR_MAX_CLOSED_LOOP_ERROR_TICKS_ROTATION) &&
			   (errorR < DriveConstants.DRIVE_MOTOR_MAX_CLOSED_LOOP_ERROR_TICKS_ROTATION);
		
  }
}
