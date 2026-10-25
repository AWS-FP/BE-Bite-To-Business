package com.se203730.mvc.generateuibe.common.api;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Khung JSON phản hồi thành công dùng chung: status là mã HTTP, message là thông báo, data là dữ liệu trả về.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ResponseObject<T> {

    // Mã HTTP tương ứng, ví dụ 200 thành công hoặc 201 đã tạo dữ liệu.
    private int status;
    private String message;
    // T cho phép dùng cùng một khung response với UserResponse, danh sách, hoặc null.
    private T data;
}
