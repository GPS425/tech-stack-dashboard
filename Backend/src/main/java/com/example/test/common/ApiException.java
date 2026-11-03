package com.example.test.common;

public class ApiException extends RuntimeException {

    private final int status;
    private final String code;

    public ApiException(int status, String code, String message){
        super(message);
        this.code = code;
        this.status = status;
    }

    public int getStatus(){
        return status;
    }

    public String getCode(){
        return code;
    }

}
