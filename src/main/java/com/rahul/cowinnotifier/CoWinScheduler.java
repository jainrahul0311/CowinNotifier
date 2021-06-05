package com.rahul.cowinnotifier;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

@Configuration
@EnableScheduling
public class CoWinScheduler {

    @Autowired
    public CoWinDao coWinDao;

    @Value("${coWinGetUrl}")
    public static String coWinUrl;

    @Value("${coWinPinCode}")
    public static String pinCode;

    @Scheduled(fixedDelayString = "${coWinFixedDelay}")
    public void scheduledCoinVaccineAvailability(){
        coWinDao.getForDate();
    }

    @Scheduled(fixedDelayString = "3600000")
    public void doHealthCheck(){
        coWinDao.sendSlackTextResponse("This is just a Health check. Server is Up and Running!!",false);
    }

}
