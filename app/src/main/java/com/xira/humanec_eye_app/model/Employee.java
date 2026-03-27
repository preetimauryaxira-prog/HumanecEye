package com.xira.humanec_eye_app.model;

import com.google.gson.annotations.SerializedName;
import java.util.Date;

public class Employee {

    @SerializedName("ROWNUMBER")
    private Integer rowNumber;

    @SerializedName("TOTALROW")
    private Integer totalRow;

    @SerializedName("CODE")
    private String code;

    @SerializedName("REF_CODE")
    private String refCode;

    @SerializedName("TITLE")
    private String title;

    @SerializedName("EMP_NAME")
    private String empName;

    @SerializedName("BRANCH")
    private Integer branch;

    @SerializedName("DEPT")
    private Integer dept;

    @SerializedName("DESG")
    private Integer desg;

    @SerializedName("SHIFT")
    private Integer shift;

    @SerializedName("EMP_STATUS")
    private String empStatus;

    @SerializedName("EMP_TYPE")
    private String empType;

    @SerializedName("EMP_RM")
    private String empRm;

    @SerializedName("EMP_RM2")
    private String empRm2;

    @SerializedName("DOJ")
    private String doj;

    @SerializedName("MOBILE")
    private String mobile;

    @SerializedName("DOE")
    private Object doe; // Can be Date or null

    @SerializedName("ATTN_ID")
    private String attnId;

    @SerializedName("DEPT_NAME")
    private String deptName;

    @SerializedName("DESG_NAME")
    private String desgName;

    @SerializedName("IMG_ATTN")
    private String imgAttn;

    @SerializedName("IMG")
    private String img; // Can be a URL or null

    @SerializedName("RM_NAME")
    private Object rmName;

    @SerializedName("RM2_NAME")
    private Object rm2Name;

    // Getters and Setters
    public Integer getRowNumber() { return rowNumber; }
    public void setRowNumber(Integer rowNumber) { this.rowNumber = rowNumber; }

    public Integer getTotalRow() { return totalRow; }
    public void setTotalRow(Integer totalRow) { this.totalRow = totalRow; }

    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }

    public String getRefCode() { return refCode; }
    public void setRefCode(String refCode) { this.refCode = refCode; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getEmpName() { return empName; }
    public void setEmpName(String empName) { this.empName = empName; }

    public Integer getBranch() { return branch; }
    public void setBranch(Integer branch) { this.branch = branch; }

    public Integer getDept() { return dept; }
    public void setDept(Integer dept) { this.dept = dept; }

    public Integer getDesg() { return desg; }
    public void setDesg(Integer desg) { this.desg = desg; }

    public Integer getShift() { return shift; }
    public void setShift(Integer shift) { this.shift = shift; }

    public String getEmpStatus() { return empStatus; }
    public void setEmpStatus(String empStatus) { this.empStatus = empStatus; }

    public String getEmpType() { return empType; }
    public void setEmpType(String empType) { this.empType = empType; }

    public String getEmpRm() { return empRm; }
    public void setEmpRm(String empRm) { this.empRm = empRm; }

    public String getEmpRm2() { return empRm2; }
    public void setEmpRm2(String empRm2) { this.empRm2 = empRm2; }

    public String getDoj() { return doj; }
    public void setDoj(String doj) { this.doj = doj; }

    public String getMobile() { return mobile; }
    public void setMobile(String mobile) { this.mobile = mobile; }

    public Object getDoe() { return doe; }
    public void setDoe(Object doe) { this.doe = doe; }

    public String getAttnId() { return attnId; }
    public void setAttnId(String attnId) { this.attnId = attnId; }

    public String getDeptName() { return deptName; }
    public void setDeptName(String deptName) { this.deptName = deptName; }

    public String getDesgName() { return desgName; }
    public void setDesgName(String desgName) { this.desgName = desgName; }

    public String getImgAttn() { return imgAttn; }
    public void setImgAttn(String imgAttn) { this.imgAttn = imgAttn; }

    public String getImg() { return img; }
    public void setImg(String img) { this.img = img; }

    public Object getRmName() { return rmName; }
    public void setRmName(Object rmName) { this.rmName = rmName; }

    public Object getRm2Name() { return rm2Name; }
    public void setRm2Name(Object rm2Name) { this.rm2Name = rm2Name; }
}
