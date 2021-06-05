package com.rahul.cowinnotifier;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.json.JSONArray;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestTemplate;

import java.text.SimpleDateFormat;
import java.util.Date;

@Component
public class CoWinDao {

    public static final Logger logger = LogManager.getLogger(CoWinDao.class);

    public static final String KEY_CENTERS = "centers";
    public static final String KEY_SESSIONS = "sessions";
    public static final String KEY_ADDRESS = "address";
    public static final String KEY_VACCINE = "vaccine";
    public static final String KEY_DATE = "date";
    public static final String KEY_DOSE_PREFIX = "available_capacity_dose";
    public static final String KEY_AGE = "min_age_limit";

    @Value("${coWinGetUrl}")
    public String coWinGetURL;

    @Value("${coWinAge}")
    public String age;

    @Value("${coWinVaccineName}")
    public String vaccineName;

    @Value("${coWinDoseNo}")
    public String doseNo;

    @Value("${slackHook}")
    public String hook;

    public void getForDate(){
        JSONArray result = new JSONArray();

        JSONObject coWinResponse = sendHttpRequest();
        String error = coWinResponse.optString("error");
        if(error.length()!=0){
            sendSlackTextResponse(error,true);
        }else {
            result = parseCoWinResponse(coWinResponse);
            sendSlackResponse(result);
        }
    }

    public void sendSlackTextResponse(String message,boolean isError) {
        String header = isError ? "Oops!!!" : "Info :information_source:";

        String slackMessage = "{\n" +
                "\t\"blocks\": [\n" +
                "\t\t{\n" +
                "\t\t\t\"type\": \"header\",\n" +
                "\t\t\t\"text\": {\n" +
                "\t\t\t\t\"type\": \"plain_text\",\n" +
                "\t\t\t\t\"text\": \""+header+"\",\n" +
                "\t\t\t\t\"emoji\": true\n" +
                "\t\t\t}\n" +
                "\t\t},\n" +
                "\t\t{\n" +
                "\t\t\t\"type\": \"section\",\n" +
                "\t\t\t\"text\": {\n" +
                "\t\t\t\t\"type\": \"plain_text\",\n" +
                "\t\t\t\t\"text\": \""+message+"\",\n" +
                "\t\t\t\t\"emoji\": true\n" +
                "\t\t\t}\n" +
                "\t\t}\n" +
                "\t]\n" +
                "}";

        HttpEntity<String> entity = new HttpEntity<String>(slackMessage, null);
        ResponseEntity<String> exchange = new RestTemplate().exchange(hook, HttpMethod.POST, entity, String.class);

        if(exchange.getStatusCodeValue()!=200){
            logger.error("Slack is not able to send Message!!");
        }

    }

    private JSONObject sendHttpRequest(){
        String url = coWinGetURL + getCurrentDate();
        logger.info("CoWin Get URL : " + url);


        try {
            ResponseEntity<String> exchange = new RestTemplate().exchange(url, HttpMethod.GET, null, String.class);
            int statusCodeValue = exchange.getStatusCodeValue();

            if (statusCodeValue == 200) {
                return new JSONObject(exchange.getBody());
            } else {
                return new JSONObject().put("error", "Response Code from CoWin API is " + statusCodeValue);
            }
        }catch (HttpStatusCodeException exception){
            return new JSONObject().put("error", "Response Code from CoWin API is " + exception.getStatusCode());
        }
    }

    private String getCurrentDate(){
        SimpleDateFormat simpleDateFormat = new SimpleDateFormat("dd-MM-yyyy");
        return simpleDateFormat.format(new Date());
    }

    private JSONArray parseCoWinResponse(JSONObject data){

        JSONArray response = new JSONArray();

        JSONArray centers = data.optJSONArray(KEY_CENTERS);
        for (int i = 0; i < centers.length(); i++) {

            JSONObject center = centers.optJSONObject(i);
            String centerAddress = center.optString(KEY_ADDRESS);

            JSONArray sessions = center.optJSONArray(KEY_SESSIONS);

            for (int j = 0; j < sessions.length(); j++) {
                JSONObject session = sessions.optJSONObject(j);

                String sessionAge = session.optString(KEY_AGE);
                String sessionVaccine = session.optString(KEY_VACCINE);
                long sessionDose = session.optLong(KEY_DOSE_PREFIX + doseNo);
                String date = session.optString(KEY_DATE);
                if(sessionAge.equals(age) && sessionVaccine.equals(vaccineName) && sessionDose > 0){
                    response.put(
                            new JSONObject()
                            .put(KEY_AGE,age)
                            .put(KEY_VACCINE,vaccineName)
                            .put(KEY_DOSE_PREFIX,sessionDose)
                            .put(KEY_DATE,date)
                            .put(KEY_ADDRESS,centerAddress)
                    );
                }
            }
        }

        return response;
    }

    private void sendSlackResponse(JSONArray response){

        for (int i = 0; i < response.length(); i++) {
            JSONObject resp = response.optJSONObject(i);
            String message = "{\n" +
                    "\t\"blocks\": [\n" +
                    "\t\t{\n" +
                    "\t\t\t\"type\": \"header\",\n" +
                    "\t\t\t\"text\": {\n" +
                    "\t\t\t\t\"type\": \"plain_text\",\n" +
                    "\t\t\t\t\"text\": \"Hey !! :raising_hand: " + resp.optString(KEY_VACCINE)+" is Available ["+ resp.optString(KEY_DATE)+"]\",\n" +
                    "\t\t\t\t\"emoji\": true\n" +
                    "\t\t\t}\n" +
                    "\t\t},\n" +
                    "\t\t{\n" +
                    "\t\t\t\"type\": \"section\",\n" +
                    "\t\t\t\"text\": {\n" +
                    "\t\t\t\t\"type\": \"plain_text\",\n" +
                    "\t\t\t\t\"text\": \"Hurry up there are only "+resp.optString(KEY_DOSE_PREFIX)+" Dose of " + resp.optString(KEY_VACCINE) +" is available at "+ resp.optString(KEY_ADDRESS)+".\",\n" +
                    "\t\t\t\t\"emoji\": true\n" +
                    "\t\t\t}\n" +
                    "\t\t}\n" +
                    "\t]\n" +
                    "}";

            HttpEntity<String> entity = new HttpEntity<String>(message, null);
            ResponseEntity<String> exchange = new RestTemplate().exchange(hook, HttpMethod.POST, entity, String.class);

            if(exchange.getStatusCodeValue()!=200){
                logger.error("Slack is not able to send Message!!");
            }
        }

    }

}
