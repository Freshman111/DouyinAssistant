package com.sgffsg.douyin.assist.common;

import com.didi.drouter.api.DRouter;

/**
 * @author: lmk
 * @date: 2026/1/2
 * @desc:
 */
public class AppRuntime {

    public static <T> T getService(Class<T> serviceClass, Object... parameters){
        return DRouter.build(serviceClass).getService(parameters);
    }
}
