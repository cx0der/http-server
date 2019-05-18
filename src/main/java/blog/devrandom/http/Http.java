package blog.devrandom.http;

public interface Http {
    interface Status {
        String OK = " 200 OK";
        String BAD_REQUEST = " 400 Bad Request";
        String NOT_FOUND = " 404 Not Found";
        String NOT_IMPLEMENTED = " 501 Not Implemented";
    }

    interface Method {
        String GET = "GET";
    }

    interface Header {
        String ACCEPT_ENCODING = "accept-encoding";
        String CONNECTION = "Connection: ";
        String CONTENT_ENCODING = "Content-Encoding: ";
        String CONTENT_LENGTH = "Content-Length: ";
        String CONTENT_TYPE = "Content-Type: ";
        String DATE = "Date: ";
        String METHOD = "method";
        String PROTOCOL = "protocol";
        String RESOURCE = "resource";
        String SERVER = "Server: ";
        String UA = "user-agent";
    }
}
