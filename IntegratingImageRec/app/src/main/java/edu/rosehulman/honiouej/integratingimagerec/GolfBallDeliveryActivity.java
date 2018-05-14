package edu.rosehulman.honiouej.integratingimagerec;

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.graphics.Color;
import android.location.Location;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.NumberPicker;
import android.widget.TableLayout;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.ViewFlipper;

import me435.NavUtils;
import me435.RobotActivity;

public class GolfBallDeliveryActivity extends ImageRecActivity {

	/** Constant used with logging that you'll see later. */
	public static final String TAG = "GolfBallDelivery";
//    private int mFirebaseUpdateCounter;

    /**
     * An enum used for variables when a ball color needs to be referenced.
     */
    public enum BallColor {
        NONE, BLUE, RED, YELLOW, GREEN, BLACK, WHITE
    }

    /**
     * An enum used for the mState variable that tracks the robot's current state.
     */
    public enum State {
        READY_FOR_MISSION,
        NEAR_BALL_SCRIPT,
        DRIVE_TOWARDS_FAR_BALL,
        FAR_BALL_SCRIPT,
        DRIVE_TOWARDS_HOME,
        WAITING_FOR_PICKUP,
        SEEKING_HOME,
    }
    /**
     * Tracks the robot's current state.
     */
    public State mState;

    /**
     * An array (of size 3) that stores what color is present in each golf ball stand location.
     */
    public BallColor[] mLocationColors = new BallColor[]{BallColor.NONE, BallColor.NONE, BallColor.NONE};

    /**
     * Simple boolean that is updated when the Team button is pressed to switch teams.
     */
    public boolean mOnRedTeam = false;

    /**
     * Instance of a helper method class that implements various script driving functions.
     */
    private Scripts mScripts;


    // ---------------------- UI References ----------------------
    /**
     * An array (of size 3) that keeps a reference to the 3 balls displayed on the UI.
     */
    private ImageButton[] mBallImageButtons;

    /**
     * References to the buttons on the UI that can change color.
     */
    private Button mTeamChangeButton, mGoOrMissionCompleteButton;

    /**
     * An array constants (of size 7) that keeps a reference to the different ball color images resources.
     */
    // Note, the order is important and must be the same throughout the app.
    private static final int[] BALL_DRAWABLE_RESOURCES = new int[]{R.drawable.none_ball, R.drawable.blue_ball,
            R.drawable.red_ball, R.drawable.yellow_ball, R.drawable.green_ball, R.drawable.black_ball, R.drawable.white_ball};

    /**
     * TextViews that can change values.
     */
    private TextView mCurrentStateTextView, mStateTimeTextView, mGpsInfoTextView, mSensorOrientationTextView,
            mGuessXYTextView, mLeftDutyCycleTextView, mRightDutyCycleTextView, mMatchTimeTextView;

    private TextView mJumboXTextView, mJumboYTextView;

    private LinearLayout mJumbotronLinearLayout;

    // ---------------------- End of UI References ----------------------

	
	// ---------------------- Mission strategy values ----------------------
    /** Constants for the known locations. */
    public static final long NEAR_BALL_GPS_X = 90;
    public static final long FAR_BALL_GPS_X = 240;


    /** Variables that will be either 50 or -50 depending on the balls we get. */
    public double mNearBallGpsY, mFarBallGpsY;

    /**
     * If that ball is present the values will be 1, 2, or 3.
     * If not present the value will be 0.
     * For example if we have the black ball, then mWhiteBallLocation will equal 0.
     */
    public int mNearBallLocation, mFarBallLocation, mWhiteBallLocation;

    protected ViewFlipper mViewFlipper;
    // ----------------- End of mission strategy values ----------------------
	
	
    // ---------------------------- Timing area ------------------------------
	/**
     * Time when the state began (saved as the number of millisecond since epoch).
     */
    private long mStateStartTime;

    /**
     * Time when the match began, ie when Go! was pressed (saved as the number of millisecond since epoch).
     */
    private long mMatchStartTime;

    /**
     * Constant that holds the maximum length of the match (saved in milliseconds).
     */
    private long MATCH_LENGTH_MS = 300000; // 5 minutes in milliseconds (5 * 60 * 1000)
	// ----------------------- End of timing area --------------------------------
	
	
    // ---------------------------- Driving area ---------------------------------
	/**
     * When driving towards a target, using a seek strategy, consider that state a success when the
     * GPS distance to the target is less than (or equal to) this value.
     */
    public static final double ACCEPTED_DISTANCE_AWAY_FT = 10.0; // Within 10 feet is close enough.
	
	/**
     * Multiplier used during seeking to calculate a PWM value based on the turn amount needed.
     */
    private static final double SEEKING_DUTY_CYCLE_PER_ANGLE_OFF_MULTIPLIER = 3.0;  // units are (PWM value)/degrees

    /**
     * Variable used to cap the slowest PWM duty cycle used while seeking. Pick a value from -255 to 255.
    */
    private static final int LOWEST_DESIRABLE_SEEKING_DUTY_CYCLE = 150;

    /**
     * PWM duty cycle values used with the drive straight dialog that make your robot drive straightest.
     */
    public int mLeftStraightPwmValue = 255, mRightStraightPwmValue = 255;
	// ------------------------ End of Driving area ------------------------------

    /**
     * Method that is called 10 times per second for updates. Note, the setup was done within RobotActivity.
     */
    public void loop() {
        super.loop(); // Important to call super first
        // RobotActivity updated the mGuessX and mGuessY already. Here we need to display it.
        mStateTimeTextView.setText("" + getStateTimeMs() / 1000);
        mGuessXYTextView.setText("(" + (int) mGuessX + ", " + (int) mGuessY + ")");

        mJumboXTextView.setText("" + (int)mGuessX);
        mJumboYTextView.setText("" + (int)mGuessY);

        // Match timer.
        long timeRemainingSeconds = MATCH_LENGTH_MS / 1000;
        if (mState != State.READY_FOR_MISSION) {
            timeRemainingSeconds = (MATCH_LENGTH_MS - getMatchTimeMs()) / 1000;
            if (getMatchTimeMs() > MATCH_LENGTH_MS) {
                setState(State.READY_FOR_MISSION);
            }
        }
        mMatchTimeTextView.setText(getString(R.string.time_format, timeRemainingSeconds / 60, timeRemainingSeconds % 60));
        // Once every 2 seconds
//        mFirebaseUpdateCounter++;
//        if(mFirebaseUpdateCounter % 20 == 0 && mState != State.READY_FOR_MISSION){
//            mFirebaseRef.child("time").child("matchTime").setValue(getMatchTimeMs());
//
//        }

        //make backround of jumbotron orange if cone is seen
        if(mConeFound){
            mJumbotronLinearLayout.setBackgroundColor(Color.parseColor("#ff8000"));
            if(mConeLeftRightLocation < 0){
                //turn left some
            }
            if(mConeLeftRightLocation > 0){
                // turn right some
            }
            if(mConeSize > 0.1){
                setState(State.FAR_BALL_SCRIPT);
            }
        }
        else {
            mJumbotronLinearLayout.setBackgroundColor(Color.LTGRAY);
        }


        switch (mState) {
            case DRIVE_TOWARDS_FAR_BALL:
                seekTargetAt(FAR_BALL_GPS_X, mFarBallGpsY);
                if (getStateTimeMs() > 6000){
                    setState(State.FAR_BALL_SCRIPT);
                }
                break;
            case DRIVE_TOWARDS_HOME:
                seekTargetAt(0, 0);
                if (getStateTimeMs() > 6000){
                    setState(State.WAITING_FOR_PICKUP);
                }
                break;
            case WAITING_FOR_PICKUP:
                if (getStateTimeMs() > 8000) {
                    setState(State.SEEKING_HOME);
                }
                break;
            case SEEKING_HOME:
                seekTargetAt(0, 0);
                if (getStateTimeMs() > 8000) {
                    setState(State.WAITING_FOR_PICKUP);
                }
                break;
            default:
                // Other states don't need to do anything, but could.
                break;
        }

    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        mBallImageButtons = new ImageButton[]{(ImageButton) findViewById(R.id.location_1_image_button),
                (ImageButton) findViewById(R.id.location_2_image_button),
                (ImageButton) findViewById(R.id.location_3_image_button)};
        mTeamChangeButton = (Button) findViewById(R.id.team_change_button);
        mCurrentStateTextView = (TextView) findViewById(R.id.current_state_textview);
        mStateTimeTextView = (TextView) findViewById(R.id.state_time_textview);
        mGpsInfoTextView = (TextView) findViewById(R.id.gps_info_textview);
        mSensorOrientationTextView = (TextView) findViewById(R.id.orientation_textview);
        mGuessXYTextView = (TextView) findViewById(R.id.guess_location_textview);
        mLeftDutyCycleTextView = (TextView) findViewById(R.id.left_duty_cycle_textview);
        mRightDutyCycleTextView = (TextView) findViewById(R.id.right_duty_cycle_textview);
        mMatchTimeTextView = (TextView) findViewById(R.id.match_time_textview);
        mGoOrMissionCompleteButton = (Button) findViewById(R.id.go_or_mission_complete_button);
        mJumboXTextView = (TextView) findViewById(R.id.jumbo_x);
        mJumboYTextView = (TextView) findViewById(R.id.jumbo_y);
        mJumbotronLinearLayout = (LinearLayout) findViewById(R.id.jumbotron_linear_layout);


        // When you start using the real hardware you don't need test buttons.
        boolean hideFakeGpsButtons = false;
        if (hideFakeGpsButtons) {
            TableLayout fakeGpsButtonTable = (TableLayout) findViewById(R.id.fake_gps_button_table);
            fakeGpsButtonTable.setVisibility(View.GONE);
        }

        setLocationToColor(1, BallColor.RED);
        setLocationToColor(2, BallColor.WHITE);
        setLocationToColor(3, BallColor.BLUE);

        setState(State.READY_FOR_MISSION);
        mScripts = new Scripts(this);

        if (mOnRedTeam == false){
            mNearBallGpsY = -50;
            mFarBallGpsY = -50;
        }
        if (mOnRedTeam == true){
            mNearBallGpsY = 50;
            mFarBallGpsY = 50;
        }
    }

    /**
     * Use this helper method to set the color of a ball.
     * The location value here is 1 based.  Send 1, 2, or 3
     * Side effect: Updates the UI with the appropriate ball color resource image.
     */
    public void setLocationToColor(int location, BallColor ballColor) {
        mBallImageButtons[location - 1].setImageResource(BALL_DRAWABLE_RESOURCES[ballColor.ordinal()]);
        mLocationColors[location - 1] = ballColor;
    }

    /**
     * Used to get the state time in milliseconds.
     */
    private long getStateTimeMs() {
        return System.currentTimeMillis() - mStateStartTime;
    }

    /**
     * Used to get the match time in milliseconds.
     */
    private long getMatchTimeMs() {
        return System.currentTimeMillis() - mMatchStartTime;
    }


    // --------------------------- Methods added ---------------------------


	
	
	
	
	// --------------------------- Drive command ---------------------------
    @Override
    public void sendWheelSpeed(int leftDutyCycle, int rightDutyCycle) {
        super.sendWheelSpeed(leftDutyCycle, rightDutyCycle); // Send the values to the
        mLeftDutyCycleTextView.setText("Left\n" + leftDutyCycle);
        mRightDutyCycleTextView.setText("Right\n" + rightDutyCycle);
    }



    // --------------------------- Sensor listeners ---------------------------



    // --------------------------- Button Handlers ----------------------------


    /**
     * GPS sensor updates.
     */
    @Override
    public void onLocationChanged(double x, double y, double heading, Location location) {
        super.onLocationChanged(x, y, heading, location);

//        mFirebaseRef.child("gps").child("x").setValue(mCurrentGpsX);
//        mFirebaseRef.child("gps").child("y").setValue(mCurrentGpsY);


        String gpsInfo = getString(R.string.xy_format, mCurrentGpsX, mCurrentGpsY);
        if (mCurrentGpsHeading != NO_HEADING) {
            mJumbotronLinearLayout.setBackgroundColor(Color.GREEN);
            gpsInfo += " " + getString(R.string.degrees_format, mCurrentGpsHeading);
//            mFirebaseRef.child("gps").child("heading").setValue("No Heading");
        } else {
            mJumbotronLinearLayout.setBackgroundColor(Color.LTGRAY);
            gpsInfo += " ?º";

        }
        gpsInfo += "    " + mGpsCounter;
        mGpsInfoTextView.setText(gpsInfo);

        if (mState == State.DRIVE_TOWARDS_FAR_BALL) {
            double distanceFromTarget = NavUtils.getDistance(mCurrentGpsX, mCurrentGpsY,
                    FAR_BALL_GPS_X, mFarBallGpsY);
            if (distanceFromTarget < ACCEPTED_DISTANCE_AWAY_FT) {
                setState(State.FAR_BALL_SCRIPT);
            }
        }
        if (mState == State.DRIVE_TOWARDS_HOME) {
            // Shorter to write since the RobotActivity already calculates the distance to 0, 0.
            if (mCurrentGpsDistance < ACCEPTED_DISTANCE_AWAY_FT) {
                setState(State.WAITING_FOR_PICKUP);
            }
        }

    }

    /**
     * Field Orientation sensor updates.
     */
    @Override
    public void onSensorChanged(double fieldHeading, float[] orientationValues) {
        super.onSensorChanged(fieldHeading, orientationValues);
        mSensorOrientationTextView.setText(getString(R.string.degrees_format,
                mCurrentSensorHeading));
    }




    /**
     * Helper method that is called by all three golf ball clicks.
     */
    private void handleBallClickForLocation(final int location) {
        AlertDialog.Builder builder = new AlertDialog.Builder(GolfBallDeliveryActivity.this);
        builder.setTitle("What was the real color?").setItems(R.array.ball_colors,
            new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface dialog, int which) {
                    GolfBallDeliveryActivity.this.setLocationToColor(location, BallColor.values()[which]);
                }
            });
        builder.create().show();
    }

    /**
     * Click to the far left image button (Location 1).
     */
    public void handleBallAtLocation1Click(View view) {
        handleBallClickForLocation(1);
    }

    /**
     * Click to the center image button (Location 2).
     */
    public void handleBallAtLocation2Click(View view) {
        handleBallClickForLocation(2);
    }

    /**
     * Click to the far right image button (Location 3).
     */
    public void handleBallAtLocation3Click(View view) {
        handleBallClickForLocation(3);
    }

    /**
     * Sets the mOnRedTeam boolean value as appropriate
     * Side effects: Clears the balls
     * @param view
     *
     *
     */
    public void handleTeamChange(View view) {
        setLocationToColor(1, BallColor.NONE);
        setLocationToColor(2, BallColor.NONE);
        setLocationToColor(3, BallColor.NONE);
        if (mOnRedTeam) {
            mOnRedTeam = false;
            mTeamChangeButton.setBackgroundResource(R.drawable.blue_button);
            mTeamChangeButton.setText("Team Blue");
        } else {
            mOnRedTeam = true;
            mTeamChangeButton.setBackgroundResource(R.drawable.red_button);
            mTeamChangeButton.setText("Team Red");
        }
        // setTeamToRed(mOnRedTeam); // This call is optional. It will reset your GPS and sensor heading values.
    }

    /**
     * Sends a message to Arduino to perform a ball color test.
     */
    public void handlePerformBallTest(View view) {
//        Toast.makeText(this, "TODO: Implement handlePerformBallTest", Toast.LENGTH_SHORT).show();
//        sendCommand("CUSTOM Do a ball test");

        // Send a mock reply from the Arduino manually
        onCommandReceived("1R");
        mCommandHandler.postDelayed(new Runnable() {
            @Override
            public void run() {
                onCommandReceived("2W");
            }
        }, 1000);
        mCommandHandler.postDelayed(new Runnable() {
            @Override
            public void run() {
                onCommandReceived("3B");
            }
        }, 2000);    }

    AlertDialog alert;
    /**
     * Clicks to the red arrow image button that should show a dialog window.
     */


    public void setState(State newState) {
        // Make sure when the match ends that no scheduled timer events from scripts change the FSM state.
        if (mState == State.READY_FOR_MISSION && newState != State.NEAR_BALL_SCRIPT) {
            Toast.makeText(this, "Illegal state transition out of READY_FOR_MISSION", Toast.LENGTH_SHORT).show();
            return;
        }
        mStateStartTime = System.currentTimeMillis();
        mCurrentStateTextView.setText(newState.name());
        speak(newState.name().replace("_", " ").toLowerCase());
        switch (newState) {
            case READY_FOR_MISSION:
                mGoOrMissionCompleteButton.setBackgroundResource(R.drawable.green_button);
                mGoOrMissionCompleteButton.setText("Go!");
                sendWheelSpeed(0, 0);
                break;
            case NEAR_BALL_SCRIPT:
                mGpsInfoTextView.setText("---"); // Clear GPS display (optional)
                mGuessXYTextView.setText("---"); // Clear guess display (optional)
                mScripts.initialStraight();
                mScripts.nearBallScript();

//                mViewFlipper.setDisplayedChild(2);

//                ViewFlipper localFlipper = (ViewFlipper)findViewById(R.id.my_view_flipper);
//                localFlipper.setDisplayedChild(2);
                break;
            case DRIVE_TOWARDS_FAR_BALL:
                // All actions handled in the loop function.
                break;
            case FAR_BALL_SCRIPT:
                // TODO: Run a far ball script that results in removing that ball and white (if present).
                mScripts.farBallScript();
                break;
            case DRIVE_TOWARDS_HOME:
                // All actions handled in the loop function.
                break;
            case WAITING_FOR_PICKUP:
                sendWheelSpeed(0, 0);
                break;
            case SEEKING_HOME:
                // Actions handled in the loop function.
                break;
        }
        mState = newState;
    }

    /** Updates the mission strategy variables. */
    private void updateMissionStrategyVariables() {
        mNearBallGpsY = -50.0; // Note, X value is a constant so no need to figure it out.
        mFarBallGpsY = 50.0; // Note, X value is a constant so no need to figure it out.
        mNearBallLocation = 1;
        mWhiteBallLocation = 0; // Assume there is no white ball present for now (update later).
        mFarBallLocation = 3;

        // Example of doing real planning.
        for (int i = 0; i < 3; i++) {
            BallColor currentLocationsBallColor = mLocationColors[i];
            if (currentLocationsBallColor == BallColor.WHITE) {
                mWhiteBallLocation = i + 1;
            }
// TODO: In your project you’ll add more code to calculate the values below correctly!
        }

        Log.d(TAG, "Near ball position: " + mNearBallLocation + " drop off at y = " + mNearBallGpsY);
        Log.d(TAG, "Far ball position: " + mFarBallLocation + " drop off at y = " + mFarBallGpsY);
        Log.d(TAG, "White ball position: " + mWhiteBallLocation);
    }

    /**
     * Adjust the PWM duty cycles based on the turn amount needed to point at the target heading.
     *
     * @param x GPS X value of the target.
     * @param y GPS Y value of the target. */
    private void seekTargetAt(double x, double y) {
        int leftDutyCycle = mLeftStraightPwmValue;
        int rightDutyCycle = mRightStraightPwmValue;
        double targetHeading = NavUtils.getTargetHeading(mGuessX, mGuessY, x, y);
        double leftTurnAmount = NavUtils.getLeftTurnHeadingDelta(mCurrentSensorHeading, targetHeading);
        double rightTurnAmount = NavUtils.getRightTurnHeadingDelta(mCurrentSensorHeading, targetHeading);
        if (leftTurnAmount < rightTurnAmount) {
            leftDutyCycle = mLeftStraightPwmValue - (int)(leftTurnAmount * SEEKING_DUTY_CYCLE_PER_ANGLE_OFF_MULTIPLIER);
            leftDutyCycle = Math.max(leftDutyCycle, LOWEST_DESIRABLE_SEEKING_DUTY_CYCLE);
        } else {
            rightDutyCycle = mRightStraightPwmValue - (int)(rightTurnAmount * SEEKING_DUTY_CYCLE_PER_ANGLE_OFF_MULTIPLIER);
            rightDutyCycle = Math.max(rightDutyCycle, LOWEST_DESIRABLE_SEEKING_DUTY_CYCLE);
        }
        sendWheelSpeed(leftDutyCycle, rightDutyCycle);
    }



    @Override
    protected void onCommandReceived(String receivedCommand) {
        super.onCommandReceived(receivedCommand);
        // TODO: Handle any commands from the Arduino, for example ball test results.

        if (receivedCommand.charAt(0) == '1') {
            setLocationToColor(1, decodeColor(receivedCommand));
            assignLocation1(decodeColor(receivedCommand));
            } else if (receivedCommand.charAt(0) == '2') {
            setLocationToColor(2, decodeColor(receivedCommand));
            assignLocation2(decodeColor(receivedCommand));
        } else if (receivedCommand.charAt(0) == '3') {
            setLocationToColor(3, decodeColor(receivedCommand));
            assignLocation3(decodeColor(receivedCommand));
        }
//
//        if (receivedCommand.equalsIgnoreCase("1R")) {
//            setLocationToColor(1, BallColor.RED);
//        } else if (receivedCommand.equalsIgnoreCase("2W")) {
//            setLocationToColor(2, BallColor.WHITE);
//        } else if (receivedCommand.equalsIgnoreCase("3B")) {
//            setLocationToColor(3, BallColor.BLUE);
//        }
    }

    public BallColor decodeColor(String receivedCommand) {
        if (receivedCommand.charAt(1) == 'R') {
            return BallColor.RED;
        } else if (receivedCommand.charAt(1) == 'G') {
            return BallColor.GREEN;
        } else if (receivedCommand.charAt(1) == 'B') {
            return BallColor.BLUE;
        } else if (receivedCommand.charAt(1) == 'K') {
            return BallColor.BLACK;
        } else if (receivedCommand.charAt(1) == 'Y') {
            return BallColor.YELLOW;
        } else if (receivedCommand.charAt(1) == 'N') {
            return BallColor.NONE;
        } else if (receivedCommand.charAt(1) == 'W') {
            return BallColor.WHITE;
        }
        return null;
    }

    public void assignLocation1(BallColor BallColor){
        if(BallColor.equals(BallColor.YELLOW) || BallColor.equals(BallColor.BLUE)){
            mNearBallLocation = 1;
        }
        else if(BallColor.equals(BallColor.RED) || BallColor.equals(BallColor.GREEN)){
            mFarBallLocation = 1;
        }
        else if(BallColor.equals(BallColor.BLACK)){
            mWhiteBallLocation = 0;
        }
        else if(BallColor.equals(BallColor.WHITE)){
            mWhiteBallLocation = 1;
        }
    }

    public void assignLocation2(BallColor BallColor){
        if(BallColor.equals(BallColor.YELLOW) || BallColor.equals(BallColor.BLUE)){
            mNearBallLocation = 2;
        }
        else if(BallColor.equals(BallColor.RED) || BallColor.equals(BallColor.GREEN)){
            mFarBallLocation = 2;
        }
        else if(BallColor.equals(BallColor.BLACK)){
            mWhiteBallLocation = 0;
        }
        else if(BallColor.equals(BallColor.WHITE)){
            mWhiteBallLocation = 2;
        }
    }

    public void assignLocation3(BallColor BallColor){
        if(BallColor.equals(BallColor.YELLOW) || BallColor.equals(BallColor.BLUE)){
            mNearBallLocation = 3;
        }
        else if(BallColor.equals(BallColor.RED) || BallColor.equals(BallColor.GREEN)){
            mFarBallLocation = 3;
        }
        else if(BallColor.equals(BallColor.BLACK)){
            mWhiteBallLocation = 0;
        }
        else if(BallColor.equals(BallColor.WHITE)){
            mWhiteBallLocation = 3;
        }
    }

    public void nearRemoval(){
        if (mNearBallLocation == 1){
            mScripts.removal1Script();
        }
        else if (mNearBallLocation == 2){
            mScripts.removal2Script();
        }
        else if(mNearBallLocation == 3){
            mScripts.removal3Script();
        }
    }

    public void homeRemoval(){
        if (mWhiteBallLocation  == 1){
            mScripts.removal1Script();
            }
        else if (mWhiteBallLocation  == 2){
            mScripts.removal2Script();
            }
        else if(mWhiteBallLocation == 3){
            mScripts.removal3Script();
            }
        else if (mWhiteBallLocation == 0){

        }
    }

    public void farRemoval(){
        if (mFarBallLocation == 1){
            mScripts.removal1Script();
        }
        else if (mFarBallLocation == 2){
            mScripts.removal2Script();
        }
        else if(mFarBallLocation == 3){
            mScripts.removal3Script();
        }
    }


    public void handleDrivingStraight(View view) {
        Toast.makeText(this, "handleDrivingStraight", Toast.LENGTH_SHORT).show();
        AlertDialog.Builder builder = new AlertDialog.Builder(GolfBallDeliveryActivity.this);
        builder.setTitle("Driving Straight Calibration");
        View dialoglayout = getLayoutInflater().inflate(R.layout.driving_straight_dialog, (ViewGroup) getCurrentFocus());
        builder.setView(dialoglayout);
        final NumberPicker rightDutyCyclePicker = (NumberPicker) dialoglayout.findViewById(R.id.right_pwm_number_picker);
        rightDutyCyclePicker.setMaxValue(255);
        rightDutyCyclePicker.setMinValue(0);
        rightDutyCyclePicker.setValue(mRightStraightPwmValue);
        rightDutyCyclePicker.setWrapSelectorWheel(false);
        final NumberPicker leftDutyCyclePicker = (NumberPicker) dialoglayout.findViewById(R.id.left_pwm_number_picker);
        leftDutyCyclePicker.setMaxValue(255);
        leftDutyCyclePicker.setMinValue(0);
        leftDutyCyclePicker.setValue(mLeftStraightPwmValue);
        leftDutyCyclePicker.setWrapSelectorWheel(false);
        Button doneButton = (Button) dialoglayout.findViewById(R.id.done_button);
        doneButton.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                mLeftStraightPwmValue = leftDutyCyclePicker.getValue();
                mRightStraightPwmValue = rightDutyCyclePicker.getValue();
                alert.dismiss();
            }
        });
        final Button testStraightButton = (Button) dialoglayout.findViewById(R.id.test_straight_button);
        testStraightButton.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                mLeftStraightPwmValue = leftDutyCyclePicker.getValue();
                mRightStraightPwmValue = rightDutyCyclePicker.getValue();
                mScripts.testStraightDriveScript();
            }
        });
        alert = builder.create();
        alert.show();
    }

    /**
     * Test GPS point when going to the Far ball (assumes Blue Team heading to red ball).
     */
    public void handleFakeGpsF0(View view) {
        onLocationChanged(165, 50, NO_HEADING, null); // Midfield
    }

    public void handleFakeGpsF1(View view) {
        onLocationChanged(209, 50, 0, null);  // Out of range so ignored.
    }

    public void handleFakeGpsF2(View view) {
        onLocationChanged(231, 50, 135, null);  // Within range (terrible heading)
    }

    public void handleFakeGpsF3(View view) {
        onLocationChanged(240, 41, 35, null);  // Within range
    }

    public void handleFakeGpsH0(View view) {
        onLocationChanged(165, 0, -180, null); // Midfield
    }

    public void handleFakeGpsH1(View view) {
        onLocationChanged(11, 0, -180, null);  // Out of range so ignored.
    }

    public void handleFakeGpsH2(View view) {
        onLocationChanged(9, 0, -170, null);  // Within range
    }

    public void handleFakeGpsH3(View view) {
        onLocationChanged(0, -9, -170, null);  // Within range
    }

    public void handleSetOrigin(View view) {
        mFieldGps.setCurrentLocationAsOrigin();    }

    public void handleSetXAxis(View view) {
        mFieldGps.setCurrentLocationAsLocationOnXAxis();
    }

    public void handleZeroHeading(View view) {
        mFieldOrientation.setCurrentFieldHeading(0);
    }

    public void handleGoOrMissionComplete(View view) {
        if (mState == State.READY_FOR_MISSION) {
            mMatchStartTime = System.currentTimeMillis();
            updateMissionStrategyVariables();
            mGoOrMissionCompleteButton.setBackgroundResource(R.drawable.red_button);
            mGoOrMissionCompleteButton.setText("Mission Complete!");
            setState(State.NEAR_BALL_SCRIPT);
        } else{
            setState(State.READY_FOR_MISSION);
        }
    }

    public  void handleGoOrStop(View view){
        if (mState == State.READY_FOR_MISSION) {
            mMatchStartTime = System.currentTimeMillis();
            updateMissionStrategyVariables();
            mGoOrMissionCompleteButton.setBackgroundResource(R.drawable.red_button);
            mGoOrMissionCompleteButton.setText("Stop");
            setState(State.NEAR_BALL_SCRIPT);
        } else {
            setState(State.READY_FOR_MISSION);
        }
    }
}
