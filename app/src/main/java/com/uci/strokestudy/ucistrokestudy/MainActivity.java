package com.uci.strokestudy.ucistrokestudy;

import android.app.ActionBar;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.DownloadManager;
import android.content.Context;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Debug;
import android.os.Handler;
import android.text.InputType;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import com.loopj.android.http.*;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;

import cz.msebera.android.httpclient.Header;

public class MainActivity extends Activity {
    private String siteNo = "1";
    SQLiteDatabase assessmentDatabase;
    private String userID;
    private String answers;
    private int score;
    private int currentQ = 0;

    private String[][] survey = new String[30][6];


    /**
     * Override of Android onBackPressed
     * <p/>
     * Set so the back button does not close the assessment by accident.
     */
    @Override
    public void onBackPressed() {
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        SharedPreferences prefFile = this.getPreferences(Context.MODE_PRIVATE);
        siteNo = prefFile.getString("siteNo","1");
        populateSurvey();

        //The local SQLite Database used to store assessments that failed to get sent to server
        //If the database does not exist yet (first run) it will create one
        assessmentDatabase = openOrCreateDatabase("assessments", MODE_PRIVATE, null);
        assessmentDatabase.execSQL("CREATE TABLE IF NOT EXISTS assessments(id INTEGER PRIMARY KEY,siteNo VARCHAR,answers VARCHAR,score VARCHAR,userID VARCHAR);");

        setContentView(R.layout.activity_main);
        checkLocal();
        ((Button)findViewById(R.id.siteLabel)).setText("Site " + siteNo);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if (id == R.id.action_settings) {
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    @Override
    protected void onResume() {
        super.onResume();
    }


    public void loadUser(View view){
        //Reset all variables
        answers = "";
        score = 0;
        currentQ = 0;

        //Gets the UserID text and sets it to userID
        EditText userID_EditText = (EditText)findViewById(R.id.userID);
        userID = userID_EditText.getText().toString();

        //Sets the userID text and start1 button invisible and shows start2 button (Press Start When Ready)
        if(!userID.equals("")) {
            userID_EditText.setVisibility(View.GONE);
            view.setVisibility(View.GONE);

            findViewById(R.id.start2).setVisibility(View.VISIBLE);
        }
        else{
            Toast.makeText(MainActivity.this,"Please enter a user id",Toast.LENGTH_SHORT).show();
        }
    }

    public void setSite(View view){
        final EditText txtNo = new EditText(this);
        final Button label = (Button)view;
        txtNo.setHint(siteNo);
        txtNo.setInputType(InputType.TYPE_CLASS_NUMBER);

        new AlertDialog.Builder(this)
                .setTitle("Site #")
                .setView(txtNo)
                .setPositiveButton("Set", new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int whichButton) {
                        siteNo = txtNo.getText().toString();
                        label.setText("Site " + siteNo);
                    }
                })
                .setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int whichButton) {
                    }
                })
                .show();


    }

    public void startSurvey(View view){
        SharedPreferences prefFile = this.getPreferences(Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = prefFile.edit();
        editor.putString("siteNo", siteNo);
        editor.apply();

        setContentView(R.layout.layout_survey);
        loadQuestion();
    }

    public void submitAnswer(View view){
        activateButtons(false);
        int ansVal = 0;
        switch(view.getId()){
            case R.id.survey_answer1:
                ansVal = 1;
                break;
            case R.id.survey_answer2:
                ansVal = 2;
                break;
            case R.id.survey_answer3:
                ansVal = 3;
                break;
            case R.id.survey_answer4:
                ansVal = 4;
                break;
            default:
                break;
        }

        answers += "[" + currentQ + "]1234" + ansVal;
        if(Integer.toString(ansVal).equals(survey[currentQ][5])){ score += 1; }

        ++currentQ;
        if(currentQ <= 29){
            answers += ",";
            loadQuestion();
            activateButtons(true);
        }
        else{
            setContentView(R.layout.layout_complete);
        }
    }

    private void activateButtons(boolean activate){
        if(activate){
            Handler handler = new Handler();
            handler.postDelayed(new Runnable() {
                public void run() {
                    ((Button) findViewById(R.id.survey_answer1)).setEnabled(true);
                    ((Button) findViewById(R.id.survey_answer2)).setEnabled(true);
                    ((Button) findViewById(R.id.survey_answer3)).setEnabled(true);
                    ((Button) findViewById(R.id.survey_answer4)).setEnabled(true);
                }
            }, 250);
        }
        else{
            ((Button)findViewById(R.id.survey_answer1)).setEnabled(false);
            ((Button)findViewById(R.id.survey_answer2)).setEnabled(false);
            ((Button)findViewById(R.id.survey_answer3)).setEnabled(false);
            ((Button)findViewById(R.id.survey_answer4)).setEnabled(false);
        }
    }

    public void save(View view){
        view.setVisibility(View.GONE);

        RequestParams param = new RequestParams();
        param.put("txtSite", siteNo);
        param.put("txtAnswers", answers);
        param.put("txtScore", Integer.toString(score));
        param.put("userID", userID);
        param.put("txtKey","7bf8e7e7d12eab31d14c5db848bde73c");

        AsyncHttpClient client = new AsyncHttpClient();
        client.setEnableRedirects(true);
        client.post("http://www1.icts.uci.edu/telerehab/ed/updateed2.aspx", param, new AsyncHttpResponseHandler() {

            @Override
            public void onSuccess(int statusCode, Header[] headers, byte[] response) {
                // called when response HTTP status is "200 OK"
                String sResponse = new String(response);
                if(sResponse.equals("ok")){
                    setContentView(R.layout.activity_main);
                    checkLocal();
                    ((Button)findViewById(R.id.siteLabel)).setText("Site " + siteNo);
                }
                else{
                    Toast.makeText(MainActivity.this, "Saved Locally", Toast.LENGTH_SHORT).show();
                    assessmentDatabase.execSQL("INSERT INTO assessments VALUES(null,'" + siteNo + "','" +  answers + "','" + Integer.toString(score) + "','" + userID + "');");

                    setContentView(R.layout.activity_main);
                    checkLocal();
                    ((Button)findViewById(R.id.siteLabel)).setText("Site " + siteNo);
                }

            }

            @Override
            public void onFailure(int statusCode, Header[] headers, byte[] errorResponse, Throwable e) {
                // called when response HTTP status is "4XX" (eg. 401, 403, 404)
                //((Button) MainActivity.this.postDataButton).setVisibility(View.VISIBLE);
                Toast.makeText(MainActivity.this, "Saved Locally", Toast.LENGTH_SHORT).show();
                assessmentDatabase.execSQL("INSERT INTO assessments VALUES(null,'" + siteNo + "','" +  answers + "','" + Integer.toString(score) + "','" + userID + "');");

                setContentView(R.layout.activity_main);
                checkLocal();
                ((Button)findViewById(R.id.siteLabel)).setText("Site " + siteNo);
            }
        });
    }

    /**
     * saveLocal
     * <p/>
     * Called from the saveLocal button, uses saveSurveyLocal to save all local assessments.
     *
     * @param view
     */
    public void saveLocal(View view) {
        view.setVisibility(View.GONE);
        Toast.makeText(MainActivity.this, "Saving...", Toast.LENGTH_LONG).show();
        Cursor resultSet = assessmentDatabase.rawQuery("Select * from assessments", null);
        resultSet.moveToFirst();
        saveSurveyLocal(resultSet.getString(0), resultSet.getString(1), resultSet.getString(2), resultSet.getString(3), resultSet.getString(4));
        while (resultSet.moveToNext()) {
            saveSurveyLocal(resultSet.getString(0), resultSet.getString(1), resultSet.getString(2), resultSet.getString(3), resultSet.getString(4));
        }

        resultSet.close();
    }

    private void saveSurveyLocal(final String id, String siteNo, String answers, String score, String userID){
        findViewById(R.id.saveLocal).setVisibility(View.GONE);

        RequestParams param = new RequestParams();
        param.put("txtSite", siteNo);
        param.put("txtAnswers", answers);
        param.put("txtScore", score);
        param.put("userID", userID);
        param.put("txtKey","7bf8e7e7d12eab31d14c5db848bde73c");

        AsyncHttpClient client = new AsyncHttpClient();
        client.setEnableRedirects(true);
        client.post("http://www1.icts.uci.edu/telerehab/ed/updateed2.aspx", param, new AsyncHttpResponseHandler() {

            @Override
            public void onSuccess(int statusCode, Header[] headers, byte[] response) {
                // called when response HTTP status is "200 OK
                String sResponse = new String(response);

                if(sResponse.equals("ok")){
                    assessmentDatabase.delete("assessments", "id=" + id, null);
                    Cursor resultSet = assessmentDatabase.rawQuery("Select * from assessments", null);
                    if (resultSet.getCount() == 0) {
                        Toast.makeText(MainActivity.this, "Assessments saved successfully!", Toast.LENGTH_SHORT).show();
                    }
                }
                else{
                    findViewById(R.id.saveLocal).setVisibility(View.VISIBLE);
                    Toast.makeText(MainActivity.this, "Some assessments failed to save, try again later.", Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onFailure(int statusCode, Header[] headers, byte[] errorResponse, Throwable e) {
                // called when response HTTP status is "4XX" (eg. 401, 403, 404)
                //((Button) MainActivity.this.postDataButton).setVisibility(View.VISIBLE);

                findViewById(R.id.saveLocal).setVisibility(View.VISIBLE);
                Toast.makeText(MainActivity.this, "Some assessments failed to save, try again later.", Toast.LENGTH_SHORT).show();
            }
        });
    }

    /**
     * checkLocal
     * <p/>
     * Checks to see if there are any local assessments, if there are none hide the saveLocal button
     */
    private void checkLocal() {
        Cursor resultSet = assessmentDatabase.rawQuery("Select * from assessments", null);
        if (resultSet.getCount() == 0) {
            findViewById(R.id.saveLocal).setVisibility(View.GONE);
        }
    }

    private void populateSurvey(){
        //populate questions/answers
        survey[0][0] = "Which of the following is true about dietary fats:";
        survey[0][1] = "Fish have mostly unhealthy dietary fats";
        survey[0][2] = "Trans fats, found in many baked goods, reduce stroke risk";
        survey[0][3] = "Saturated fats, found in meat and dairy products, lower cholesterol";
        survey[0][4] = "Using unhydrogenated vegetable oils such as olive oil is healthy";
        survey[0][5] = "4";
        survey[1][0] = "Of the 795,000 strokes that occur each year in the U.S., approximately__________ occur in those who have already had at least one previous stroke";
        survey[1][1] = "20,000";
        survey[1][2] = "100,000";
        survey[1][3] = "500,000";
        survey[1][4] = "185,000";
        survey[1][5] = "4";
        survey[2][0] = "Most strokes are caused by:";
        survey[2][1] = "blockage of a blood vessel that brings blood to the brain";
        survey[2][2] = "bleeding into the brain";
        survey[2][3] = "a ruptured aneurysm";
        survey[2][4] = "a blood clot in the leg";
        survey[2][5] = "1";
        survey[3][0] = "Which of the following increases risk of stroke:";
        survey[3][1] = "use of newer Sulfa antibiotics";
        survey[3][2] = "high cholesterol";
        survey[3][3] = "low body mass index";
        survey[3][4] = "reading for more than 2 hours per day";
        survey[3][5] = "2";
        survey[4][0] = "Risk factors for stroke that you can change include:";
        survey[4][1] = "older age";
        survey[4][2] = "having history of a previous stroke";
        survey[4][3] = "being overweight";
        survey[4][4] = "having a family history of stroke";
        survey[4][5] = "3";
        survey[5][0] = "Common warning signs of stroke include all of the following except:";
        survey[5][1] = "sudden shortness of breath";
        survey[5][2] = "sudden weakness on one side, such as in the arm";
        survey[5][3] = "sudden difficulty speaking or understanding others";
        survey[5][4] = "sudden drooping of the face";
        survey[5][5] = "1";
        survey[6][0] = "If you suspect that you or someone else is having a stroke, you should:";
        survey[6][1] = "take an aspirin";
        survey[6][2] = "call your doctor";
        survey[6][3] = "drive them to the emergency room";
        survey[6][4] = "call 9-1-1";
        survey[6][5] = "4";
        survey[7][0] = "Getting a quick and accurate diagnosis of stroke:";
        survey[7][1] = "is important because a person might then receive a \"clot-busting\" drug";
        survey[7][2] = "rarely requires a CT scan";
        survey[7][3] = "often can be done by describing symptoms over the phone to a doctor";
        survey[7][4] = "requires that a blood test be done immediately";
        survey[7][5] = "1";
        survey[8][0] = "One of the common symptoms of stroke is:";
        survey[8][1] = "sudden diarrhea";
        survey[8][2] = "numbness in both hands";
        survey[8][3] = "problems with speaking";
        survey[8][4] = "hearing loss";
        survey[8][5] = "3";
        survey[9][0] = "Stroke can cause a person to:";
        survey[9][1] = "lose the need for human kindness";
        survey[9][2] = "have an improved ability to recognize faces";
        survey[9][3] = "have reduced physical activity";
        survey[9][4] = "develop an increased sensitivity to allergy medications";
        survey[9][5] = "3";
        survey[10][0] = "Stroke can cause:";
        survey[10][1] = "increased blood oxygen levels";
        survey[10][2] = "lack of response to Vitamin C";
        survey[10][3] = "improved memory";
        survey[10][4] = "emotional changes";
        survey[10][5] = "4";
        survey[11][0] = "After one has had a stroke, the risk of falling generally:";
        survey[11][1] = "decreases";
        survey[11][2] = "is about the same as prior to the stroke, particularly if one is over 70 years old";
        survey[11][3] = "can be reduced by placing throw rugs around the house for traction";
        survey[11][4] = "increases";
        survey[11][5] = "4";
        survey[12][0] = "Depression after stroke:";
        survey[12][1] = "can show up as low energy, apathy, or lack of joy";
        survey[12][2] = "is uncommon";
        survey[12][3] = "is usually difficult to treat";
        survey[12][4] = "is limited to highly creative people";
        survey[12][5] = "1";
        survey[13][0] = "Which of the following is not a useful strategy to reduce high blood pressure:";
        survey[13][1] = "follow an exercise program approved by your doctor";
        survey[13][2] = "take extra blood pressure pills at the first sign of stress";
        survey[13][3] = "stop smoking";
        survey[13][4] = "eat a healthy diet including limited salt intake";
        survey[13][5] = "2";
        survey[14][0] = "Lower blood cholesterol levels reduces risk of stroke, and this can be done by:";
        survey[14][1] = "avoiding most fruits and vegetables";
        survey[14][2] = "taking cholesterol-lowering medications after a fatty meal";
        survey[14][3] = "reducing the amount of trans fats in their diet";
        survey[14][4] = "carefully reducing levels of daily exercise";
        survey[14][5] = "3";
        survey[15][0] = "Which of the following would be considered good blood pressure in most people?";
        survey[15][1] = "161/92";
        survey[15][2] = "143/82";
        survey[15][3] = "128/84";
        survey[15][4] = "two of the answers are correct";
        survey[15][5] = "3";
        survey[16][0] = "If your blood pressure is too high even after taking your medicine, you should:";
        survey[16][1] = "exercise until it is no longer too high";
        survey[16][2] = "go to bed and check again when you wake up";
        survey[16][3] = "call your doctor or go to the emergency room depending on how high it is";
        survey[16][4] = "take extra medicine every 2 hours until it is no longer too high";
        survey[16][5] = "3";
        survey[17][0] = "To reduce blood cholesterol levels, a person should aim to have the percentage of calories that comes from saturated fat be no higher than:";
        survey[17][1] = "6%";
        survey[17][2] = "12%";
        survey[17][3] = "20-25%, depending on age";
        survey[17][4] = "40%";
        survey[17][5] = "1";
        survey[18][0] = "How many total servings of fruits and vegetables should a person eat each day?";
        survey[18][1] = "1 or 2";
        survey[18][2] = "2 or 3";
        survey[18][3] = "at least 12";
        survey[18][4] = "at least 4-5";
        survey[18][5] = "4";
        survey[19][0] = "A person should consume less than 2,400 milligrams of sodium per day. That is the equivalent of approximately";
        survey[19][1] = "1 teaspoon of table salt per day";
        survey[19][2] = "1 tablespoon of table salt per day";
        survey[19][3] = "4 teaspoons of table salt per day";
        survey[19][4] = "6.5 tablespoons of table salt per day";
        survey[19][5] = "1";
        survey[20][0] = "Once one has had a stroke, the risk of having another stroke is:";
        survey[20][1] = "a topic to be avoided, to prevent stress";
        survey[20][2] = "about the same as a person who never had a stroke";
        survey[20][3] = "reduced compared to a person who never had a stroke";
        survey[20][4] = "increased compared to a person who never had a stroke";
        survey[20][5] = "4";
        survey[21][0] = "Regarding dietary salt, which of these statements is true:";
        survey[21][1] = "Eating less salt in the diet increases blood pressure";
        survey[21][2] = "Dietary salt mainly comes from processed or packaged foods";
        survey[21][3] = "Most Americans do not consume enough salt each day";
        survey[21][4] = "Using herbs and onions automatically increases salt intake";
        survey[21][5] = "2";
        survey[22][0] = "Regular exercise is recommended for a person who had a stroke:";
        survey[22][1] = "unless they are overweight";
        survey[22][2] = "except during winter";
        survey[22][3] = "except for people with diabetes";
        survey[22][4] = "unless they have chest pain and a rapid heart rate at rest";
        survey[22][5] = "4";
        survey[23][0] = "What should a person who had a stroke know before starting an exercise program?";
        survey[23][1] = "build up to 30 minutes of activity per day, at least 5 days per week";
        survey[23][2] = "build up to 2.5 hours per day, 7 days per week";
        survey[23][3] = "begin a program focused on regular jogging";
        survey[23][4] = "joining a gym is the critical first step";
        survey[23][5] = "1";
        survey[24][0] = "Hypertension:";
        survey[24][1] = "is the result of a person having a stroke";
        survey[24][2] = "causes symptoms in most people, such as a headache";
        survey[24][3] = "in general simply means that a person has too much stress in their life";
        survey[24][4] = "is a condition where a person's blood pressure is too high";
        survey[24][5] = "4";
        survey[25][0] = "All of the following reduce risk of a second stroke except:";
        survey[25][1] = "managing blood pressure if you have hypertension";
        survey[25][2] = "taking antibiotics at the first sign of stroke symptoms";
        survey[25][3] = "a program of regular exercise approved by a person's doctor";
        survey[25][4] = "managing cholesterol levels if you have high cholesterol";
        survey[25][5] = "2";
        survey[26][0] = "Which is not true about losing weight:";
        survey[26][1] = "keeping a food diary that lists what you eat helps with losing weight";
        survey[26][2] = "an overweight person must lose at least 25% of their weight for better health";
        survey[26][3] = "losing weight depends on what you eat and how much you eat";
        survey[26][4] = "losing weight depends on how much you exercise";
        survey[26][5] = "2";
        survey[27][0] = "One pound of body weight equals 3,500 calories. To lose one pound per week after stroke, a person could";
        survey[27][1] = "eat an additional 500 calories per day of vegetables";
        survey[27][2] = "take vitamin C each day";
        survey[27][3] = "burn 500 more calories/day with exercise or eat 500 less calories/day";
        survey[27][4] = "it is impossible to lose weight after stroke";
        survey[27][5] = "3";
        survey[28][0] = "Which of the following is a tip for eating a healthier diet:";
        survey[28][1] = "limit intake of fruits and vegetables";
        survey[28][2] = "fried food is a safe choice if limited to snacks between meals";
        survey[28][3] = "eating butter helps prevent stroke and heart attacks";
        survey[28][4] = "have at least two meatless meals each week";
        survey[28][5] = "4";
        survey[29][0] = "When a person has diabetes:";
        survey[29][1] = "the liver and lungs are affected, but not blood vessels";
        survey[29][2] = "they should carefully limit their exercise";
        survey[29][3] = "the risk of stroke is increased";
        survey[29][4] = "better control of blood sugar levels increases the risk of complications";
        survey[29][5] = "3";
    }

    private void loadQuestion(){
        ((TextView)findViewById(R.id.survey_question)).setText(Integer.toString(currentQ + 1) + ". " + survey[currentQ][0]);
        ((TextView)findViewById(R.id.survey_answer1)).setText(survey[currentQ][1]);
        ((TextView)findViewById(R.id.survey_answer2)).setText(survey[currentQ][2]);
        ((TextView)findViewById(R.id.survey_answer3)).setText(survey[currentQ][3]);
        ((TextView)findViewById(R.id.survey_answer4)).setText(survey[currentQ][4]);
    }

}
