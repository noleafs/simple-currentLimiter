package com.lx.aop;

import com.google.common.collect.ImmutableList;
import com.lx.annotation.Limit;
import com.lx.emun.LimitType;
import org.apache.commons.lang3.StringUtils;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import javax.servlet.http.HttpServletRequest;
import java.io.Serializable;
import java.lang.reflect.Method;

/**
 * 限流切面
 * @author ty
 * @create 2021-12-15 14:14
 */
@Aspect
@Component
public class LimitInterceptor {

    private static final Logger log = LoggerFactory.getLogger(LimitInterceptor.class);

    private static final String UNKNOWN = "unknown";

    @Autowired
    private RedisTemplate<String, Serializable> redisTemplate;


    /**
     * 环绕切面 针对带有 limit注解 且 修饰符是 public的方法
     * @param pjp
     * @return
     */
    @Around("execution(public * *(..)) && @annotation(com.lx.annotation.Limit))")
    public Object interceptor(ProceedingJoinPoint pjp) {
        MethodSignature signature = (MethodSignature) pjp.getSignature();
        Method method = signature.getMethod();
        // 获取方法上的注解
        Limit limitAnnotation = method.getAnnotation(Limit.class);
        // 限流的类型
        LimitType limitType = limitAnnotation.limitType();
        // 是ip 还是自定义key
        String name = limitAnnotation.name();

        String key;
        // 多少时间内
        int limitPeriod = limitAnnotation.period();
        // 可以访问的次数
        int limitCount = limitAnnotation.count();

        /**
         * 根据限流类型获取不同的key ,如果不传我们会以方法名作为key
         */
        switch (limitType) {
            case IP:
                key = getIpAddress();
                break;
            case CUSTOMER:
                key = limitAnnotation.key();
                break;
            default:
                key = StringUtils.upperCase(method.getName());
        }

        ImmutableList<String> keys = ImmutableList.of(StringUtils.join(limitAnnotation.prefix(), key));

        try {
            // lua脚本
            String luaScript = buildLuaScript();
            // 用于加载lua脚本
            RedisScript<Number> redisScript = new DefaultRedisScript<>(luaScript, Number.class);

            Number count = redisTemplate.execute(redisScript, keys, limitCount, limitPeriod);
            log.info("Access try count is {} for name={} and key = {}", count, name, key);
            if (count != null && count.intValue() <= limitCount) {
                System.out.println("执行前");
                Object proceed = pjp.proceed();
                // TODO 走到这里代表请求执行完成了，其实可以进行对次数进行-- ， 保存指定时间内，只有指定的请求在工作(针对自定义key情况下)
//                int i = (int) redisTemplate.opsForValue().get(key);
//                redisTemplate.opsForValue().set(key, --i);
                System.out.println("执行后");
                return proceed;
            } else {
                throw new RuntimeException("You have been dragged into the blacklis");
            }
        } catch (Throwable e) {
            if (e instanceof RuntimeException) {
                throw new RuntimeException(e.getLocalizedMessage());
            }
            throw new RuntimeException("server exception");
        }
    }


    /**
     * @description 编写 redis Lua 限流脚本
     * @date 2021年12月15日14:19:45
     */
    public String buildLuaScript() {
        StringBuilder lua = new StringBuilder();
        lua.append("local c");
        lua.append("\nc = redis.call('get',KEYS[1])");
        // 调用超过最大值，则直接返回
        lua.append("\nif c and tonumber(c) > tonumber(ARGV[1]) then");
        lua.append("\nreturn c;");
        lua.append("\nend");
        // 执行计算器自加
        lua.append("\nc = redis.call('incr',KEYS[1])");
        lua.append("\nif tonumber(c) == 1 then");
        // 从第一次调用开始限流，设置对应键值的过期
        lua.append("\nredis.call('expire',KEYS[1],ARGV[2])");
        lua.append("\nend");
        lua.append("\nreturn c;");
        return lua.toString();
    }


    /**
     * @description 获取id地址
     * @date 2020/4/8 13:24
     */
    public String getIpAddress() {
        HttpServletRequest request = ((ServletRequestAttributes) RequestContextHolder.getRequestAttributes()).getRequest();
        String ip = request.getHeader("x-forwarded-for");
        if (ip == null || ip.length() == 0 || UNKNOWN.equalsIgnoreCase(ip)) {
            ip = request.getHeader("Proxy-Client-IP");
        }
        if (ip == null || ip.length() == 0 || UNKNOWN.equalsIgnoreCase(ip)) {
            ip = request.getHeader("WL-Proxy-Client-IP");
        }
        if (ip == null || ip.length() == 0 || UNKNOWN.equalsIgnoreCase(ip)) {
            ip = request.getRemoteAddr();
        }
        return ip;
    }

}
