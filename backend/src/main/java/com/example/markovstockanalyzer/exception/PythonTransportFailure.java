package com.example.markovstockanalyzer.exception;

import java.net.ConnectException;
import java.net.NoRouteToHostException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.net.http.HttpConnectTimeoutException;
import java.net.http.HttpTimeoutException;
import java.util.Set;

/** Keeps the frozen 503 connection-failure / 504 response-timeout distinction. */
public final class PythonTransportFailure {
    private static final Set<String> CONNECT_METHODS = Set.of(
            "connect", "finishConnect", "timedFinishConnect", "pollConnect", "pollConnectNow");
    private PythonTransportFailure() {}

    public static boolean isConnectionFailure(Throwable error) {
        for (Throwable cause = error; cause != null; cause = cause.getCause()) {
            if (cause instanceof ConnectException || cause instanceof NoRouteToHostException
                    || cause instanceof UnknownHostException || cause instanceof HttpConnectTimeoutException) return true;
            if (cause instanceof SocketTimeoutException) {
                // HttpURLConnection uses SocketTimeoutException for both phases. Its JDK
                // socket stack identifies connect without depending on localized messages.
                for (StackTraceElement frame : cause.getStackTrace()) {
                    if ((frame.getClassName().startsWith("java.net.") || frame.getClassName().startsWith("sun.nio.ch."))
                            && CONNECT_METHODS.contains(frame.getMethodName())) return true;
                }
            }
        }
        return false;
    }

    public static boolean isResponseTimeout(Throwable error) {
        if (isConnectionFailure(error)) return false;
        for (Throwable cause = error; cause != null; cause = cause.getCause()) {
            if (cause instanceof SocketTimeoutException || cause instanceof HttpTimeoutException) return true;
        }
        return false;
    }
}
