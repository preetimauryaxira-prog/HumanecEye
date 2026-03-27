package com.xira.humanec_eye_app.model;

public class User {
    private String accessToken;
    private boolean isSuperAdmin;
    private String orgId;
    private String name;
    private String phoneNumber;

    private  String tokenExpiration;

    private String userName;

    private String empCode;

    private  boolean isAdmin;

    private boolean isHrHead;


    public String getAccessToken() { return accessToken; }
    public boolean isSuperAdmin() { return isSuperAdmin; }

    public void setAccessToken(String accessToken) {
        this.accessToken = accessToken;
    }

    public void setSuperAdmin(boolean superAdmin) {
        isSuperAdmin = superAdmin;
    }

    public void setOrgId(String orgId) {
        this.orgId = orgId;
    }

    public void setName(String name) {
        this.name = name;
    }

    public void setPhoneNumber(String phoneNumber) {
        this.phoneNumber = phoneNumber;
    }

    public void setTokenExpiration(String tokenExpiration) {
        this.tokenExpiration = tokenExpiration;
    }

    public void setUserName(String userName) {
        this.userName = userName;
    }

    public void setEmpCode(String empCode) {
        this.empCode = empCode;
    }

    public void setAdmin(boolean admin) {
        isAdmin = admin;
    }

    public void setHrHead(boolean hrHead) {
        isHrHead = hrHead;
    }

    public String getTokenExpiration() {
        return tokenExpiration;
    }

    public String getUserName(String userName) {
        return this.userName;
    }

    public String getEmpCode() {
        return empCode;
    }

    public boolean isAdmin() {
        return isAdmin;
    }

    public boolean isHrHead() {
        return isHrHead;
    }

    public String getOrgId() { return orgId; }
    public String getName() { return name; }
    public String getPhoneNumber() { return phoneNumber; }
}