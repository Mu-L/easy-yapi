package com.itangcent.model;

import com.itangcent.model.IResult;

public class Result<T> implements IResult {

    private Integer code;//response code

    private String msg;//message

    private T data;//response data

    public Result() {
    }

    public Result(Integer code, String msg, T data) {
        this.code = code;
        this.msg = msg;
        this.data = data;
    }

    /**
     * create success result
     */
    public static <T> Result<T> success(T data) {
        return new Result<>(0, "success", data);
    }

    public static <T> Result<T> fail(Integer code) {
        return new Result<>(code, "error", null);
    }

    public static <T> Result<T> fail(Integer code, String msg) {
        return new Result<>(code, msg, null);
    }

    @Override
    public Integer getCode() {
        return code;
    }

    public void setCode(Integer code) {
        this.code = code;
    }

    @Override
    public String getMsg() {
        return msg;
    }

    public void setMsg(String msg) {
        this.msg = msg;
    }

    public T getData() {
        return data;
    }

    public void setData(T data) {
        this.data = data;
    }
}
