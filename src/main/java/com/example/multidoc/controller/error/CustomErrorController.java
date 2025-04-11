package com.example.multidoc.controller.error;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.web.servlet.error.ErrorController;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.RequestMapping;

import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Date;

@Controller
public class CustomErrorController implements ErrorController {

    private static final Logger logger = LoggerFactory.getLogger(CustomErrorController.class);

    @RequestMapping("/error")
    public String handleError(HttpServletRequest request, Model model) {
        // 获取错误状态码
        Object status = request.getAttribute(RequestDispatcher.ERROR_STATUS_CODE);
        HttpStatus httpStatus = HttpStatus.INTERNAL_SERVER_ERROR; // 默认为500错误
        
        if (status != null) {
            int statusCode = Integer.parseInt(status.toString());
            httpStatus = HttpStatus.valueOf(statusCode);
        }
        
        // 获取错误信息
        Object errorMessage = request.getAttribute(RequestDispatcher.ERROR_MESSAGE);
        if (errorMessage != null && !errorMessage.toString().isEmpty()) {
            model.addAttribute("error", errorMessage.toString());
        } else {
            model.addAttribute("error", "发生了错误 - " + httpStatus.getReasonPhrase());
        }
        
        // 如果是5xx错误，记录详细异常信息
        if (httpStatus.is5xxServerError()) {
            Throwable throwable = (Throwable) request.getAttribute(RequestDispatcher.ERROR_EXCEPTION);
            if (throwable != null) {
                logger.error("服务器错误", throwable);
                model.addAttribute("trace", getStackTrace(throwable));
            }
        }
        
        // 添加错误时间戳
        model.addAttribute("timestamp", new Date());
        
        // 添加请求路径
        Object path = request.getAttribute(RequestDispatcher.ERROR_REQUEST_URI);
        if (path != null) {
            model.addAttribute("path", path.toString());
        }
        
        // 添加HTTP状态码
        model.addAttribute("status", httpStatus.value());
        model.addAttribute("statusName", httpStatus.getReasonPhrase());
        
        return "error";
    }
    
    /**
     * 获取异常堆栈信息
     */
    private String getStackTrace(Throwable throwable) {
        StringBuilder stringBuilder = new StringBuilder();
        stringBuilder.append(throwable.toString()).append("\n\n");
        
        for (StackTraceElement element : throwable.getStackTrace()) {
            stringBuilder.append("\tat ").append(element).append("\n");
        }
        
        // 添加嵌套异常的堆栈信息
        Throwable cause = throwable.getCause();
        if (cause != null) {
            stringBuilder.append("\n原因: ").append(getStackTrace(cause));
        }
        
        return stringBuilder.toString();
    }
} 