package blog.devrandom.http;

public interface Http {
    interface Status {
        String OK = " 200 OK";
        String NOT_FOUND = " 404 Not Found";
        String NOT_IMPLEMENTED = " 501 Not Implemented";
    }

    interface Method {
        String GET = "GET";
    }

    interface Header {
        String CONNECTION = "Connection: ";
        String CONTENT_LENGTH = "Content-Length: ";
        String CONTENT_TYPE = "Content-Type: ";
        String DATE = "Date: ";
        String UA = "user-agent";
        String SERVER = "Server: ";
    }
}
