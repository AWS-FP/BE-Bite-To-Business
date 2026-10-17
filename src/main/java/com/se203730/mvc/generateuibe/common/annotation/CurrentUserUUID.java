package com.se203730.mvc.generateuibe.common.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Đánh dấu tham số UUID trong controller để resolver lấy ID từ JWT đã được Spring Security xác minh.
 */
@Target(ElementType.PARAMETER)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface CurrentUserUUID {

    // false cho phép nhận null khi chưa đăng nhập; không tự mở quyền truy cập endpoint.
    boolean required() default true;
}
