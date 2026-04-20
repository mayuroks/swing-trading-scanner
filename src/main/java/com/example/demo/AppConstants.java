package com.example.demo;

public class AppConstants {
    // TEST MODE: paste comma-separated instrument tokens here. Empty = production mode (load from DB)
    public static final String TEST_INSTRUMENTS_LIST = "138811652,138057988,136486148,128010756,136001796,136439300,128973828,131184900,136000772,129638660";
    // public static final String TEST_INSTRUMENTS_LIST = "";

    // Set true to bypass weekend check for local testing
    public static final boolean SKIP_WEEKEND_CHECK = true;

    private AppConstants() {}
}
