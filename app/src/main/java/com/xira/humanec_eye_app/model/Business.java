package com.xira.humanec_eye_app.model;

public class Business {
    private final String id;
    private final String OrgName;

    public Business(String orgName, String id) {
        this.OrgName = orgName;
        this.id = id;
    }


    public String getId() { return id; }
    public String getORGName() { return OrgName; }
}