package id.ac.ub.abl.payment.configuration;

import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.MDC;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

public final class RequestIdContext {

    public static final String HEADER_NAME = "x-request-id";
    public static final String MDC_KEY = "requestId";

    private RequestIdContext() {}

    public static String get() {
        RequestAttributes attributes = RequestContextHolder.getRequestAttributes();
        if (attributes instanceof ServletRequestAttributes servletAttributes) {
            HttpServletRequest request = servletAttributes.getRequest();
            String header = request.getHeader(HEADER_NAME);
            if (header != null && !header.isBlank()) {
                return header;
            }
        }
        String mdc = MDC.get(MDC_KEY);
        if (mdc != null && !mdc.isBlank()) {
            return mdc;
        }
        return null;
    }
}
