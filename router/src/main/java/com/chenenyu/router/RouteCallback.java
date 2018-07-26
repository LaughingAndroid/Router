package com.chenenyu.router;


import java.io.Serializable;

/**
 * <p>
 * Created by chenenyu on 2016/12/20.
 */
public interface RouteCallback extends Serializable {
    /**
     * 功能性回调
     *
     * @param req
     * @param objects
     */
    void call(RouteRequest req, Object... objects);
}
