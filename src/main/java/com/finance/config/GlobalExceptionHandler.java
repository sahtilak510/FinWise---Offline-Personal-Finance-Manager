package com.finance.config;

import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.boot.web.servlet.error.ErrorController;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.RequestMapping;

@Controller
public class GlobalExceptionHandler implements ErrorController {

    @RequestMapping("/error")
    public String handleError(HttpServletRequest request, Model model) {
        Object status = request.getAttribute(RequestDispatcher.ERROR_STATUS_CODE);
        Object error = request.getAttribute(RequestDispatcher.ERROR_EXCEPTION);
        Object path = request.getAttribute(RequestDispatcher.ERROR_REQUEST_URI);

        int statusCode = 500;
        String errorTitle = "Internal Server Error";
        String errorMessage = "An unexpected error occurred. Please try again later.";
        String errorDescription = "Our team has been notified. If this persists, please contact support.";

        if (status != null) {
            statusCode = Integer.parseInt(status.toString());
            switch (statusCode) {
                case 404:
                    errorTitle = "Page Not Found";
                    errorMessage = "The page you're looking for doesn't exist or has been moved.";
                    errorDescription = "Please check the URL or return to the dashboard.";
                    break;
                case 403:
                    errorTitle = "Access Denied";
                    errorMessage = "You don't have permission to access this resource.";
                    errorDescription = "Please log in with appropriate credentials.";
                    break;
                case 401:
                    errorTitle = "Unauthorized";
                    errorMessage = "Your session has expired or you're not logged in.";
                    errorDescription = "Please sign in to continue.";
                    break;
                case 500:
                default:
                    errorTitle = "Internal Server Error";
                    errorMessage = "Something went wrong on our end.";
                    errorDescription = "We've logged the error. Please try again in a moment.";
                    break;
            }
        }

        model.addAttribute("statusCode", statusCode);
        model.addAttribute("errorTitle", errorTitle);
        model.addAttribute("errorMessage", errorMessage);
        model.addAttribute("errorDescription", errorDescription);
        model.addAttribute("requestPath", path != null ? path.toString() : "/");
        model.addAttribute("exception", error);

        return "error/error";
    }
}