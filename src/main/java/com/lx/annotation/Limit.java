package com.lx.annotation;

import com.lx.emun.LimitType;

import java.lang.annotation.*;

/**
 * 可以作用于方法上
 * @description 自定义限流注解
 * @author ty
 * @create 2021-12-15 14:04
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface Limit {

    String name() default "";

    String key() default "";

    /**
     * key的前缀
     * @return
     */
    String prefix() default "";

    /**
     * 给定的时间范围 单位(秒)
     * @return
     */
    int period();

    /**
     * 一定时间内最多访问次数
     * @return
     */
    int count();

    /**
     * 限流的类型 自定义key 或者 请求ip
     * @return
     */
    LimitType limitType() default LimitType.CUSTOMER;

}
