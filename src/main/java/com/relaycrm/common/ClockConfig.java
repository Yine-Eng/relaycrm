package com.relaycrm.common;

import java.time.Clock;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * The application's clock, used wherever a date is calculated from "now"
 * (task due dates). Tests replace it with a fixed clock so they can check
 * exact dates.
 */
@Configuration
public class ClockConfig {

    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }
}
